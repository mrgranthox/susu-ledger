#!/usr/bin/env bash
# ==============================================================================
# SusuLedger Production Cloud Run & WhatsApp Bot Deployment Script
# ==============================================================================
set -euo pipefail

# 1. GCP Configuration Parameters (Adjust as needed)
PROJECT_ID="${GCP_PROJECT_ID:-susu-ledger-prod}"
REGION="${GCP_REGION:-europe-west2}"
SERVICE_NAME="susu-backend"
IMAGE_TAG="gcr.io/${PROJECT_ID}/${SERVICE_NAME}:$(git rev-parse --short HEAD 2>/dev/null || echo 'latest')"

# 2. WhatsApp Meta Cloud API Credentials
# Replace these with your actual Meta Developer credentials or export them in your shell environment
META_PHONE_NUMBER_ID="${META_WHATSAPP_PHONE_NUMBER_ID:-1419022297952379}"
META_ACCESS_TOKEN="${META_WHATSAPP_ACCESS_TOKEN:-EAAB...}"
META_VERIFY_TOKEN="${META_WEBHOOK_VERIFY_TOKEN:-susu_webhook_secret_token_2026}"
META_WABA_ID="${META_WHATSAPP_BUSINESS_ACCOUNT_ID:-123456789012345}"

# 3. Database & Secret Configuration
DATABASE_URL="${DATABASE_URL:-postgresql://susu_user:secret_password@/susu_db?host=/cloudsql/${PROJECT_ID}:${REGION}:susu-postgres}"
PAYSTACK_SECRET_KEY="${PAYSTACK_SECRET_KEY:-sk_live_...}"
PAYSTACK_PUBLIC_KEY="${PAYSTACK_PUBLIC_KEY:-pk_live_...}"
SECRET_SALT="${SECRET_SALT:-susu_production_hash_salt_ghana_2026}"

echo "========================================================"
echo " Deploying SusuLedger Backend & WhatsApp Bot Engine"
echo " Project: ${PROJECT_ID} | Region: ${REGION}"
echo " Image:   ${IMAGE_TAG}"
echo "========================================================"

# Step 1: Ensure active GCP Project
gcloud config set project "${PROJECT_ID}"

# Step 2: Build and push container to Google Container Registry / Artifact Registry
echo "[1/4] Building container image via Google Cloud Build..."
gcloud builds submit --tag "${IMAGE_TAG}" ./backend

# Step 3: Deploy service to Cloud Run with full WhatsApp Bot & Database environment variables
echo "[2/4] Deploying container to Google Cloud Run..."
gcloud run deploy "${SERVICE_NAME}" \
  --image "${IMAGE_TAG}" \
  --platform managed \
  --region "${REGION}" \
  --allow-unauthenticated \
  --port 8080 \
  --set-env-vars \
NODE_ENV="production",\
META_WHATSAPP_PHONE_NUMBER_ID="${META_PHONE_NUMBER_ID}",\
META_WHATSAPP_ACCESS_TOKEN="${META_ACCESS_TOKEN}",\
META_WEBHOOK_VERIFY_TOKEN="${META_VERIFY_TOKEN}",\
META_WHATSAPP_BUSINESS_ACCOUNT_ID="${META_WABA_ID}",\
DATABASE_URL="${DATABASE_URL}",\
PAYSTACK_SECRET_KEY="${PAYSTACK_SECRET_KEY}",\
PAYSTACK_PUBLIC_KEY="${PAYSTACK_PUBLIC_KEY}",\
SECRET_SALT="${SECRET_SALT}"

# Step 4: Retrieve public service URL
SERVICE_URL=$(gcloud run services describe "${SERVICE_NAME}" --platform managed --region "${REGION}" --format="value(status.url)")

echo "========================================================"
echo " Deployment Succeeded!"
echo " Service URL:    ${SERVICE_URL}"
echo " WhatsApp Hook:  ${SERVICE_URL}/webhooks/whatsapp"
echo " Verify Token:   ${META_VERIFY_TOKEN}"
echo "========================================================"
echo ""
echo "Next: Paste '${SERVICE_URL}/webhooks/whatsapp' into Meta Developer Console > WhatsApp > Configuration > Callback URL"
