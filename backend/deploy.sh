#!/usr/bin/env bash
# ==============================================================================
# SusuLedger Production Cloud Run & WhatsApp Bot Deployment Script
# Secure Mode: Reads secrets from your local environment or GCP Secret Manager.
# ==============================================================================
set -euo pipefail

# 1. GCP Configuration Parameters
PROJECT_ID="${GCP_PROJECT_ID:-susu-ledger-c3daa}"
REGION="${GCP_REGION:-africa-south1}"
SERVICE_NAME="${CLOUD_RUN_SERVICE_NAME:-susu-backend}"
IMAGE_TAG="gcr.io/${PROJECT_ID}/${SERVICE_NAME}:$(date -u +%Y%m%d%H%M%S)"

# 2. WhatsApp Meta Cloud API Credentials
META_PHONE_NUMBER_ID="${META_WHATSAPP_PHONE_NUMBER_ID:-}"
META_WABA_ID="${META_WHATSAPP_BUSINESS_ACCOUNT_ID:-}"

# 3. Database & Application Configuration
DB_HOST="${DB_HOST:-}"
DB_NAME="${DB_NAME:-susu_ledger_db}"
DB_USER="${DB_USER:-postgres}"
DB_PORT="${DB_PORT:-5432}"
REDIS_URL="${REDIS_URL:-}"
REDIS_HOST="${REDIS_HOST:-}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_TLS="${REDIS_TLS:-false}"
CLOUDSQL_INSTANCE="${CLOUDSQL_INSTANCE:-susu-ledger-c3daa:africa-south1:susu-db-instance}"
CLOUDSQL_ARGS=()
if [[ -n "${CLOUDSQL_INSTANCE}" ]]; then
  CLOUDSQL_ARGS+=(--add-cloudsql-instances "${CLOUDSQL_INSTANCE}")
  DB_HOST="${DB_HOST:-/cloudsql/${CLOUDSQL_INSTANCE}}"
fi

# Secret Manager names. Override these if your GCP secrets use different names.
DB_PASSWORD_SECRET="${DB_PASSWORD_SECRET:-DB_PASSWORD}"
META_ACCESS_TOKEN_SECRET="${META_ACCESS_TOKEN_SECRET:-META_WHATSAPP_ACCESS_TOKEN}"
PAYSTACK_SECRET_KEY_SECRET="${PAYSTACK_SECRET_KEY_SECRET:-PAYSTACK_SECRET_KEY}"
PAYSTACK_PUBLIC_KEY_SECRET="${PAYSTACK_PUBLIC_KEY_SECRET:-PAYSTACK_PUBLIC_KEY}"
SECRET_SALT_SECRET="${SECRET_SALT_SECRET:-SECRET_SALT}"
REDIS_PASSWORD_SECRET="${REDIS_PASSWORD_SECRET:-}"

SECRET_ARGS=(
  "DB_PASSWORD=${DB_PASSWORD_SECRET}:latest"
  "META_WHATSAPP_ACCESS_TOKEN=${META_ACCESS_TOKEN_SECRET}:latest"
  "PAYSTACK_SECRET_KEY=${PAYSTACK_SECRET_KEY_SECRET}:latest"
  "PAYSTACK_PUBLIC_KEY=${PAYSTACK_PUBLIC_KEY_SECRET}:latest"
  "SECRET_SALT=${SECRET_SALT_SECRET}:latest"
  "META_WEBHOOK_VERIFY_TOKEN=META_WEBHOOK_VERIFY_TOKEN:latest"
  "META_APP_SECRET=META_APP_SECRET:latest"
)
if [[ -n "${REDIS_PASSWORD_SECRET}" ]]; then
  SECRET_ARGS+=("REDIS_PASSWORD=${REDIS_PASSWORD_SECRET}:latest")
fi
SECRET_BINDINGS=$(IFS=,; echo "${SECRET_ARGS[*]}")
TRAFFIC_ARGS=()
if [[ "${NO_TRAFFIC:-false}" == "true" ]]; then
  TRAFFIC_ARGS+=(--no-traffic --tag candidate)
fi
[[ -n "$META_PHONE_NUMBER_ID" ]] || { echo "META_WHATSAPP_PHONE_NUMBER_ID is required" >&2; exit 1; }

echo "========================================================"
echo " Deploying SusuLedger Backend to Google Cloud Run"
echo " Project: ${PROJECT_ID} | Region: ${REGION}"
echo " Service: ${SERVICE_NAME}"
echo "========================================================"

# Step 1: Ensure active GCP Project
for binding in "${SECRET_ARGS[@]}"; do
  secret_name="${binding#*=}"
  gcloud secrets versions describe latest --secret="${secret_name%:*}" --project="${PROJECT_ID}" >/dev/null
done

# Step 2: Build container image via Google Cloud Build
echo "[1/3] Building container image via Google Cloud Build..."
gcloud builds submit --project "${PROJECT_ID}" --tag "${IMAGE_TAG}" ./backend

# Step 3: Deploy service to Cloud Run
echo "[2/3] Deploying container to Google Cloud Run..."
gcloud run deploy "${SERVICE_NAME}" \
  --image "${IMAGE_TAG}" \
  --platform managed \
  --region "${REGION}" \
  --project "${PROJECT_ID}" \
  --max-instances 3 \
  --concurrency 8 \
  --allow-unauthenticated \
  --port 8080 \
  --set-env-vars \
NODE_ENV="production",\
GOOGLE_CLOUD_PROJECT="${PROJECT_ID}",\
META_WHATSAPP_PHONE_NUMBER_ID="${META_PHONE_NUMBER_ID}",\
META_WHATSAPP_BUSINESS_ACCOUNT_ID="${META_WABA_ID}",\
DB_HOST="${DB_HOST}",\
DB_NAME="${DB_NAME}",\
DB_USER="${DB_USER}",\
DB_PORT="${DB_PORT}",\
CLOUD_SQL_CONNECTION_NAME="${CLOUDSQL_INSTANCE}",\
REDIS_URL="${REDIS_URL}",\
REDIS_HOST="${REDIS_HOST}",\
REDIS_PORT="${REDIS_PORT}",\
REDIS_TLS="${REDIS_TLS}" \
  --set-secrets "${SECRET_BINDINGS}" \
  "${CLOUDSQL_ARGS[@]}" \
  "${TRAFFIC_ARGS[@]}"

# Step 4: Retrieve public service URL
SERVICE_URL=$(gcloud run services describe "${SERVICE_NAME}" --platform managed --region "${REGION}" --project "${PROJECT_ID}" --format="value(status.url)")

echo "========================================================"
echo " Deployment Complete!"
echo " Service URL:    ${SERVICE_URL}"
echo " WhatsApp Hook:  ${SERVICE_URL}/webhooks/whatsapp"
echo "========================================================"
