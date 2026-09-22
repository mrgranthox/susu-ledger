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
IMAGE_TAG="gcr.io/${PROJECT_ID}/${SERVICE_NAME}:$(git rev-parse --short HEAD 2>/dev/null || echo 'latest')"

# 2. WhatsApp Meta Cloud API Credentials (Injected via Environment or Secret Manager)
META_PHONE_NUMBER_ID="${META_WHATSAPP_PHONE_NUMBER_ID:-}"
META_ACCESS_TOKEN="${META_WHATSAPP_ACCESS_TOKEN:-}"
META_VERIFY_TOKEN="${META_WEBHOOK_VERIFY_TOKEN:-}"
META_WABA_ID="${META_WHATSAPP_BUSINESS_ACCOUNT_ID:-}"

# 3. Database & Application Secrets
DATABASE_URL="${DATABASE_URL:-}"
PAYSTACK_SECRET_KEY="${PAYSTACK_SECRET_KEY:-}"
PAYSTACK_PUBLIC_KEY="${PAYSTACK_PUBLIC_KEY:-}"
SECRET_SALT="${SECRET_SALT:-}"

echo "========================================================"
echo " Deploying SusuLedger Backend to Google Cloud Run"
echo " Project: ${PROJECT_ID} | Region: ${REGION}"
echo " Service: ${SERVICE_NAME}"
echo "========================================================"

# Step 1: Ensure active GCP Project
gcloud config set project "${PROJECT_ID}"

# Step 2: Build container image via Google Cloud Build
echo "[1/3] Building container image via Google Cloud Build..."
gcloud builds submit --tag "${IMAGE_TAG}" ./backend

# Step 3: Deploy service to Cloud Run
echo "[2/3] Deploying container to Google Cloud Run..."
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
echo " Deployment Complete!"
echo " Service URL:    ${SERVICE_URL}"
echo " WhatsApp Hook:  ${SERVICE_URL}/webhooks/whatsapp"
echo "========================================================"

