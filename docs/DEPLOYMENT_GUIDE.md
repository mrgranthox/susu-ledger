# SusuLedger Enterprise Production Deployment Guide (GCP Cloud Run, Cloud SQL, Cloud Memorystore & Firebase)

### Prerequisites & Provisioning Checklist
- Google Cloud Platform (GCP) Account with project `susu-ledger-c3daa`
- `gcloud` CLI authenticated with Project Owner / Security Admin roles
- Firebase Project linked to `susu-ledger-c3daa`
- Meta Business Manager WhatsApp Cloud API account
- Paystack Ghana Enterprise Developer Account

Production coordinates used by the Android app and Firebase config:

```bash
export GCP_PROJECT_ID=susu-ledger-c3daa
export GCP_REGION=africa-south1
export CLOUD_RUN_SERVICE_NAME=susu-backend
export CLOUDSQL_INSTANCE=susu-ledger-c3daa:africa-south1:susu-db-instance
```

The infrastructure creation commands below are examples for a new environment,
not instructions to recreate existing production resources. Redis is optional;
pairing codes and webhook deduplication use PostgreSQL. See
`docs/PRODUCTION_ACCEPTANCE.md` for verified deployment status and remaining gates.

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
    --region=africa-south1 \
    --range=10.8.0.0/28
```

---

### Step 2: Cloud SQL PostgreSQL & Cloud Memorystore Redis Provisioning

```bash
# 1. Create Cloud SQL Instance (PostgreSQL 16) with High Availability
gcloud sql instances create susu-db-instance \
    --database-version=POSTGRES_16 \
    --cpu=4 --memory=16GB \
    --region=africa-south1 \
    --availability-type=REGIONAL \
    --storage-type=SSD --storage-size=100GB \
    --enable-point-in-time-recovery \
    --root-password="REPLACE_WITH_STRONG_PASSWORD"

# 2. Create Database and User
gcloud sql databases create susu_ledger_db --instance=susu-db-instance
gcloud sql users create postgres --instance=susu-db-instance --password="USER_STRONG_PASSWORD"

# 3. Apply migrations through an authenticated Cloud SQL connection.
# Supply DB_HOST, DB_PORT, DB_NAME, DB_USER and DB_PASSWORD securely.
# This initializes a new database or adds missing tables to an existing one.
node backend/scripts/migrate.js

# 4. Provision Cloud Memorystore for Redis v7.0 (Caching Layer)
gcloud redis instances create susu-redis-prod \
    --size=2 --region=africa-south1 \
    --zone=africa-south1-a \
    --redis-version=redis_7_0 \
    --tier=STANDARD \
    --connect-mode=PRIVATE_SERVICE_ACCESS
```

---

### Step 3: Firebase Auth & Cloud Storage Setup

```bash
# 1. Initialize Firebase Storage Bucket for Receipts & Audit CSV Exports
gsutil mb -p susu-ledger-c3daa -c STANDARD -l africa-south1 gs://susu-ledger-proofs-prod/

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
# Enable Secret Manager before deploying. Do not store long-lived API tokens
# as raw Cloud Run environment variable values.
gcloud services enable secretmanager.googleapis.com --project susu-ledger-c3daa

# Create Secrets in GCP Secret Manager
gcloud secrets create DB_PASSWORD --data-file=- <<< "USER_STRONG_PASSWORD"
gcloud secrets create REDIS_HOST --data-file=- <<< "10.120.4.15"
gcloud secrets create PAYSTACK_SECRET_KEY --data-file=- <<< "sk_live_1234567890abcdef"
gcloud secrets create PAYSTACK_PUBLIC_KEY --data-file=- <<< "pk_live_1234567890abcdef"
gcloud secrets create META_WHATSAPP_ACCESS_TOKEN --data-file=- <<< "EAA..."
gcloud secrets create SECRET_SALT --data-file=- <<< "replace_with_256bit_random_value"
```

---

### Step 5: Check And Deploy Cloud Run Backend Container

```bash
# Run once before deploy to catch project, API, secret, and endpoint drift.
./backend/scripts/launch-check.sh

# Build and deploy the backend image to Cloud Run.
./backend/deploy.sh

# Run again after deploy; all checks should pass before launch.
./backend/scripts/launch-check.sh
```

The deploy script uses structured Cloud SQL variables (`DB_HOST`, `DB_NAME`,
`DB_USER`, `DB_PASSWORD`) instead of a single `DATABASE_URL`, so special
characters in the password cannot break URL parsing.

---

### Step 6: Messaging Acceptance Gates

The reminder and summary routes require the treasurer's Firebase phone ID token
and only operate on that treasurer's groups. Anonymous Cloud Scheduler requests
are rejected. Service-account OIDC authentication, scheduled-job deduplication,
and automated Scheduler provisioning are not implemented; do not configure
unauthenticated jobs or treat manual app triggers as scheduled delivery.

Meta must approve `susu_friday_reminder`, `susu_payment_receipt`, and
`susu_sunday_summary` before template delivery can pass acceptance. Failed
receipts remain queued for retry. Provider acceptance is not proof of delivery
to the handset.

For SMS acceptance, enable Firebase phone sign-in, allow Ghana in the SMS region
policy, and register SHA-1 and SHA-256 fingerprints for the actual signing key.
Also inspect the API key referenced by `app/google-services.json`: its Android
application restrictions must allow the package name and actual APK SHA-1.
Firebase app fingerprint registration does not necessarily update this separate
allowlist. Preserve existing signing certificates and API service restrictions.
An HTTP 403 reporting that the Android client is blocked can occur before SMS
dispatch when this allowlist does not match the installed APK.
Repeat these steps for the release/Play signing key; debug-key verification
does not validate a release build. Test SMS receipt, group creation, cloud sync,
and the phone-bound WhatsApp pairing code on a real device.
