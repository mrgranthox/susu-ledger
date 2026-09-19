# Paystack Ghana Mobile Money & Payouts Technical Integration Plan

This document details the enterprise integration plan for **Paystack Ghana** (MTN Mobile Money, Telecel Cash, AT Money) with **SusuLedger**.

---

## 1. Executive Summary & Flow Architecture

SusuLedger operates as a digital ledger layer for traditional Ghanaian Susu groups. Integrating Paystack enables:
1. **Direct Mobile Money Deposits (App & WhatsApp)**: Members receive an instant Mobile Money prompt (USSD prompt) on their phone to pay weekly contributions.
2. **Automated Payouts (Rotation Wins)**: Treasurers trigger weekly lump-sum payouts directly to the cycle winner's Mobile Money wallet.
3. **Webhook Reconciliation & SHA-256 Ledger Auto-posting**: Paystack webhooks automatically trigger double-entry journal creation and SHA-256 hash-chaining upon successful payment verification.

---

## 2. Technical API Sequence & Flows

### A. Mobile Money Charge (Deposit) Flow
```
 Member App / WA           Susu Backend              Paystack API           Telco (MTN/Telecel/AT)
       |                        |                         |                           |
       |-- 1. Pay GHS 50 ------>|                         |                           |
       |                        |-- 2. POST /charge ----->|                           |
       |                        |   (amount, phone, provider)                         |
       |                        |                         |-- 3. Push USSD Prompt --->|
       |                        |                         |                           |-- 4. User Enters PIN
       |<-- 5. Pending Status --|                         |                           |
       |                        |<-- 6. Webhook (charge.success) ---------------------|
       |                        |
       |                        |-- 7. Insert SHA-256 Payment into Ledger
       |                        |-- 8. Invalidate Redis Cache
       |<-- 9. Receipt Notification (WhatsApp / Push)
```

### B. Mobile Money Transfer (Payout) Flow
```
 Officer (App)            Susu Backend              Paystack API             Member MoMo Wallet
       |                        |                         |                           |
       |-- 1. Approve Payout -->|                         |                           |
       |    (Requires PIN)      |-- 2. Verify Officer PIN |                           |
       |                        |-- 3. POST /transfer/recipient                       |
       |                        |-- 4. POST /transfer --->|                           |
       |                        |                         |-- 5. Direct MoMo Credit ->|
       |                        |<-- 6. Webhook (transfer.success)                    |
       |                        |-- 7. Record Double-Entry Debit/Credit
```

---

## 3. Paystack API Endpoints & Request Payloads

### 1. Initialize Charge (Ghana MoMo Direct Debit)
- **Endpoint**: `POST https://api.paystack.co/charge`
- **Headers**:
  ```http
  Authorization: Bearer sk_live_...
  Content-Type: application/json
  ```
- **Payload**:
  ```json
  {
    "amount": 5000,
    "email": "member_233240001234@susuledger.com",
    "currency": "GHS",
    "mobile_money": {
      "phone": "0240001234",
      "provider": "mtn"
    },
    "metadata": {
      "group_id": "group-uuid-123",
      "cycle_id": "cycle-uuid-456",
      "member_id": "member-uuid-789",
      "idempotency_key": "pay_20260919_233240001234_w12"
    }
  }
  ```

### 2. Verify Transaction
- **Endpoint**: `GET https://api.paystack.co/transaction/verify/:reference`

### 3. Create Transfer Recipient (Payout Setup)
- **Endpoint**: `POST https://api.paystack.co/transferrecipient`
- **Payload**:
  ```json
  {
    "type": "mobile_money",
    "name": "Ama Serwaa",
    "account_number": "0240001234",
    "bank_code": "MTN",
    "currency": "GHS"
  }
  ```

### 4. Initiate Payout
- **Endpoint**: `POST https://api.paystack.co/transfer`
- **Payload**:
  ```json
  {
    "source": "balance",
    "amount": 100000,
    "recipient": "RCP_1234567890",
    "reason": "Nima Market Susu Week 12 Rotation Payout"
  }
  ```

---

## 4. Webhook Security & Signature Verification

Paystack delivers async notifications via `POST /webhooks/paystack`.

### Signature Verification Algorithm (Node.js/Express)
```javascript
const crypto = require('crypto');

function verifyPaystackSignature(req, res, next) {
  const hash = crypto
    .createHmac('sha512', process.env.PAYSTACK_WEBHOOK_SECRET)
    .update(JSON.stringify(req.body))
    .digest('hex');

  if (hash === req.headers['x-paystack-signature']) {
    return next();
  } else {
    return res.status(401).send('Invalid Signature');
  }
}
```

---

## 5. Security & Idempotency Checklist

1. **HMAC Signature Check**: Webhook handler rejects any request where `x-paystack-signature` does not match HMAC-SHA512.
2. **Idempotency Locking**: Key format `paystack:event:{event_id}` stored in Redis (`SET EX 86400 NX`). Duplicate webhook events are discarded with HTTP 200.
3. **PIN & Dual Sign-off**: Disbursing group payouts via Paystack requires 4-digit security PIN verification and dual officer approval.
4. **Failure Fallbacks**: If direct Mobile Money charge fails (e.g. insufficient funds or USSD timeout), members fall back to manual MoMo agent/cash logging with officer confirmation.
