# SusuLedger Enterprise Production Deployment Guide (GCP Cloud Run, Cloud SQL, Cloud Memorystore & Firebase)

### Prerequisites & Provisioning Checklist
- Google Cloud Platform (GCP) Account with project `susuledger-prod`
- `gcloud` CLI authenticated with Project Owner / Security Admin roles
- Firebase Project linked to `susuledger-prod`
- Meta Business Manager WhatsApp Cloud API account
- Paystack Ghana Enterprise Developer Account

---

### Step 1: GCP Infrastructure & Security Foundation Setup

```bash
# 1. Enable Required GCP APIs
gcloud services enable \
    run.googleapis.com \
    sqladmin.googleapis.com \
    redis.googleapis.com \
    cloudtasks.googleapis.com \
    cloudscheduler.googleapis.com \
    secretmanager.googleapis.com \
    vpcaccess.googleapis.com \
    identitytoolkit.googleapis.com

# 2. Create Serverless VPC Access Connector for Private DB/Redis Routing
gcloud compute networks vpc-access connectors create susu-vpc-connector \
    --region=europe-west2 \
    --range=10.8.0.0/28
```

---

### Step 2: Cloud SQL PostgreSQL & Cloud Memorystore Redis Provisioning

```bash
# 1. Create Cloud SQL Instance (PostgreSQL 16) with High Availability
gcloud sql instances create susu-postgres-prod \
    --database-version=POSTGRES_16 \
    --cpu=4 --memory=16GB \
    --region=europe-west2 \
    --availability-type=REGIONAL \
    --storage-type=SSD --storage-size=100GB \
    --enable-point-in-time-recovery \
    --root-password="REPLACE_WITH_STRONG_PASSWORD"

# 2. Create Database and User
gcloud sql databases create susu_db --instance=susu-postgres-prod
gcloud sql users create susu_user --instance=susu-postgres-prod --password="USER_STRONG_PASSWORD"

# 3. Apply Ledger Schema and Cryptographic Triggers
gcloud sql connect susu-postgres-prod --user=susu_user --database=susu_db < database/01_schema.sql
gcloud sql connect susu-postgres-prod --user=susu_user --database=susu_db < database/02_triggers.sql

# 4. Provision Cloud Memorystore for Redis v7.0 (Caching Layer)
gcloud redis instances create susu-redis-prod \
    --size=2 --region=europe-west2 \
    --zone=europe-west2-a \
    --redis-version=redis_7_0 \
    --tier=STANDARD \
    --connect-mode=PRIVATE_SERVICE_ACCESS
```

---

### Step 3: Firebase Auth & Cloud Storage Setup

```bash
# 1. Initialize Firebase Storage Bucket for Receipts & Audit CSV Exports
gsutil mb -p susuledger-prod -c STANDARD -l europe-west2 gs://susu-ledger-proofs-prod/

# 2. Configure Storage CORS for Mobile App Direct Uploads
cat <<EOF > cors.json
[
  {
    "origin": ["*"],
    "method": ["GET", "PUT", "POST", "DELETE"],
    "responseHeader": ["Content-Type", "Authorization"],
    "maxAgeSeconds": 3600
  }
]
EOF
gsutil cors set cors.json gs://susu-ledger-proofs-prod/
```

---

### Step 4: Secret Manager & Environment Variable Provisioning

```bash
# Create Secrets in GCP Secret Manager
gcloud secrets create DATABASE_URL --data-file=- <<< "postgresql://susu_user:USER_STRONG_PASSWORD@/susu_db?host=/cloudsql/susuledger-prod:europe-west2:susu-postgres-prod"
gcloud secrets create REDIS_HOST --data-file=- <<< "10.120.4.15"
gcloud secrets create PAYSTACK_SECRET_KEY --data-file=- <<< "sk_live_1234567890abcdef"
gcloud secrets create PAYSTACK_PUBLIC_KEY --data-file=- <<< "pk_live_1234567890abcdef"
gcloud secrets create META_WHATSAPP_TOKEN --data-file=- <<< "EAA..."
gcloud secrets create META_APP_SECRET --data-file=- <<< "app_secret_123456"
```

---

### Step 5: Deploy Cloud Run Backend Container

```bash
# 1. Build and push container to Google Artifact Registry
gcloud builds submit --tag europe-west2-docker.pkg.dev/susuledger-prod/susu-repo/susu-backend:v1 ./backend

# 2. Deploy to Cloud Run with VPC Connector & Secret Manager Bindings
gcloud run deploy susu-backend \
    --image europe-west2-docker.pkg.dev/susuledger-prod/susu-repo/susu-backend:v1 \
    --platform managed \
    --region europe-west2 \
    --vpc-connector susu-vpc-connector \
    --allow-unauthenticated \
    --min-instances 2 \
    --max-instances 50 \
    --cpu 2 --memory 4Gi \
    --set-secrets="DATABASE_URL=DATABASE_URL:latest" \
    --set-secrets="REDIS_HOST=REDIS_HOST:latest" \
    --set-secrets="PAYSTACK_SECRET_KEY=PAYSTACK_SECRET_KEY:latest" \
    --set-secrets="PAYSTACK_PUBLIC_KEY=PAYSTACK_PUBLIC_KEY:latest" \
    --set-secrets="META_WHATSAPP_ACCESS_TOKEN=META_WHATSAPP_TOKEN:latest" \
    --set-secrets="META_APP_SECRET=META_APP_SECRET:latest" \
    --set-env-vars "NODE_ENV=production,REDIS_PORT=6379,META_WEBHOOK_VERIFY_TOKEN=susu_webhook_secret_token_2026" \
    --add-cloudsql-instances susuledger-prod:europe-west2:susu-postgres-prod
```

---

### Step 6: Configure Automated Cloud Scheduler Jobs

```bash
# 1. Friday 08:00 GMT Collection Reminder
gcloud scheduler jobs create http susu-friday-reminder \
    --schedule="0 8 * * 5" \
    --time-zone="Africa/Accra" \
    --uri="https://susu-backend-xxx.a.run.app/api/cron/reminders/weekly" \
    --http-method=GET

# 2. Sunday 18:00 GMT Summary Digest
gcloud scheduler jobs create http susu-sunday-summary \
    --schedule="0 18 * * 0" \
    --time-zone="Africa/Accra" \
    --uri="https://susu-backend-xxx.a.run.app/api/cron/summaries/weekly" \
    --http-method=GET
```

