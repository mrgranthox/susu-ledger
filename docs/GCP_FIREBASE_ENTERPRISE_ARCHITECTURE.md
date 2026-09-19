# SusuLedger GCP & Firebase Enterprise Cloud Architecture

This document specifies the enterprise cloud architecture for **SusuLedger**, combining Google Cloud Platform (GCP), Firebase, Cloud Memorystore Redis, and automated caching pipelines.

---

## 1. High-Level Enterprise Topology

```
+-----------------------------------------------------------------------------------+
|                            MOBILE & BOT USER INTERFACES                           |
|  • Android Mobile App (Officers/Treasurers)   • WhatsApp Business API (Members)   |
+------------------------------------------+----------------------------------------+
                                           | HTTPS / Webhooks
                                           v
+-----------------------------------------------------------------------------------+
|                  API & GATEWAY LAYER (Google Cloud Run Containers)                |
|  • Express REST API (/api/app/*)              • Webhook Receiver (/webhooks/wa)   |
|  • HMAC Signature Validator                   • Rate-Limiter & Idempotency Filter |
+------------------------------------------+----------------------------------------+
                                           |
                    +----------------------+----------------------+
                    v                                             v
+---------------------------------------+     +---------------------------------------+
|  ENTERPRISE DATA & LEDGER (Cloud SQL)  |     |   CACHING LAYER (Cloud Memorystore)   |
|  • PostgreSQL 16 (PostGIS, pgcrypto)  | <-> |   • Cloud Memorystore for Redis v7.0  |
|  • SHA-256 Cryptographic Hash-Chain   |     |   • Read-Through / Write-Through      |
|  • Balanced Double-Entry Journal      |     |   • TTL Invalidation Engine           |
+---------------------------------------+     +---------------------------------------+
                    |                                             |
                    v                                             v
+---------------------------------------+     +---------------------------------------+
|   ASYNC JOBS & SCHEDULER (GCP Native) |     |    OBJECT STORAGE (Firebase Storage)   |
|  • Cloud Tasks (Notification Queue)   |     |    • Activity Logs, Audit Trail CSVs  |
|  • Cloud Scheduler (Friday/Sunday)    |     |    • PDF Payment Receipts & Proofs    |
+---------------------------------------+     +---------------------------------------+
```

---

## 2. GCP & Firebase Service Specification

### 1. Compute & API Hosting: Google Cloud Run
- **Instance Sizing**: 2 CPU / 4 GiB RAM per instance.
- **Autoscaling**: 2 min instances (zero cold starts for WhatsApp webhooks), 50 max instances.
- **Network Isolation**: Serverless VPC Access Connector (`susu-vpc-connector`) for private IP connectivity to Cloud SQL and Cloud Memorystore Redis.

### 2. Primary Database: Cloud SQL (PostgreSQL 16)
- **High Availability**: Regional replication across multiple zones (`europe-west2-a` and `europe-west2-b`).
- **Cryptographic Engine**: SHA-256 ledger triggers calculate previous hash link on insert (`generate_payment_hash`).
- **Backup Strategy**: Automated daily backups with 30-day point-in-time recovery (PITR).

### 3. High-Performance Caching: Cloud Memorystore for Redis
- **Engine Version**: Redis 7.0 (Standard Tier with failover).
- **In-Memory Cache Patterns**:
  - `group:stats:{groupId}`: Pre-computed group totals, completion rates, and outstanding balances (TTL: 300s).
  - `pairing:{pairingCode}`: 6-character monospace bot pairing codes (TTL: 900s).
  - `idempotency:{wamid}`: Meta WhatsApp message IDs to enforce exactly-once execution (TTL: 86400s).

### 4. Storage & File Delivery: Firebase Storage
- **Bucket**: `gs://susu-ledger-proofs-prod/`
- **Objects**: PDF activity reports, raw CSV data exports, dispute evidence uploads.

---

## 3. Automated Invalidation & Caching Strategy

```
  [ Confirmed Payment Event ]
              |
              v
   Update PostgreSQL Ledger
              |
              v
  Publish Invalidation Signal
              |
              v
 Redis Invalidation (`DEL group:stats:{groupId}`)
              |
              v
  Next Request Re-calculates and Populates Cache
```

1. **Read-Through**: API attempts Redis read first. On miss, queries PostgreSQL double-entry views, returns data, and populates Redis with 5-minute TTL.
2. **Event-Driven Write Invalidation**: Whenever a payment is confirmed via app or WhatsApp bot, `redis.del("group:stats:" + groupId)` is executed immediately.

---

## 4. Master Production Environment Variable Register

```env
# SERVER ENVIRONMENT
NODE_ENV=production
PORT=8080

# DATABASE (CLOUD SQL)
DATABASE_URL=postgresql://susu_user:PASSWORD@10.120.0.3:5432/susu_db
DB_POOL_MIN=10
DB_POOL_MAX=100

# REDIS MEMORYSTORE
REDIS_HOST=10.120.4.15
REDIS_PORT=6379
REDIS_PASSWORD=SECRET_REDIS_PASSWORD

# META WHATSAPP BUSINESS API
META_WHATSAPP_PHONE_NUMBER_ID=109384920485930
META_WHATSAPP_ACCESS_TOKEN=EAA...
META_WEBHOOK_VERIFY_TOKEN=susu_webhook_secret_token_2026
META_APP_SECRET=a1b2c3d4e5f6...

# PAYSTACK GHANA INTEGRATION
PAYSTACK_SECRET_KEY=sk_live_...
PAYSTACK_PUBLIC_KEY=pk_live_...
PAYSTACK_WEBHOOK_SECRET=paystack_wh_secret_2026

# SECURITY & AUTHENTICATION
JWT_SECRET=super_secret_256bit_jwt_signing_key
PIN_HASH_PEPPER=global_server_side_pin_pepper
FIREBASE_STORAGE_BUCKET=susu-ledger-proofs-prod.appspot.com
```
