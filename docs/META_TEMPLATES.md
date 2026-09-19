# SusuLedger WhatsApp Meta HSM Pre-Approved Templates

These templates must be submitted via the Meta Business Manager (WhatsApp Manager -> Message Templates) for the **Category: UTILITY**.
Approval turnaround is typically 1 to 24 hours.

---

### 1. Friday Collection Reminder
- **Template Name**: `susu_friday_reminder`
- **Category**: `UTILITY`
- **Language**: `en` (English)
- **Header**: None
- **Body Text**:
  ```text
  Hello {{1}}, this is a reminder that your contribution of GHS {{2}} for {{3}} (Week {{4}}) is due today Friday. Reply PAID once sent.
  ```
- **Sample Values**:
  - `{{1}}`: `Kofi Mensah`
  - `{{2}}`: `50.00`
  - `{{3}}`: `Nima Market Susu`
  - `{{4}}`: `12`
- **Buttons**: Quick Reply `I Have Paid` (Payload: `CLAIM_PAID`)

---

### 2. Payment Confirmation & Cryptographic Receipt
- **Template Name**: `susu_payment_receipt`
- **Category**: `UTILITY`
- **Language**: `en` (English)
- **Header**: `Text` -> `Payment Confirmed`
- **Body Text**:
  ```text
  Receipt confirmed! GHS {{1}} received for {{2}} (Week {{3}}) via {{4}}. Ledger Block Hash: {{5}}... Verified by {{6}}.
  ```
- **Sample Values**:
  - `{{1}}`: `50.00`
  - `{{2}}`: `Nima Market Susu`
  - `{{3}}`: `12`
  - `{{4}}`: `Mobile Money`
  - `{{5}}`: `e3b0c442`
  - `{{6}}`: `Ama Mensah (Treasurer)`
- **Footer**: `SusuLedger Non-Custodial Record Layer`

---

### 3. Sunday Group Summary Digest
- **Template Name**: `susu_sunday_summary`
- **Category**: `UTILITY`
- **Language**: `en` (English)
- **Header**: `Text` -> `Weekly Susu Digest`
- **Body Text**:
  ```text
  Weekly Susu Digest for {{1}} (Week {{2}}): Total collected GHS {{3}} across {{4}} members ({{5}}% target). Thank you for saving on time!
  ```
- **Sample Values**:
  - `{{1}}`: `Nima Market Susu`
  - `{{2}}`: `12`
  - `{{3}}`: `850.00`
  - `{{4}}`: `17/20`
  - `{{5}}`: `85`
- **Buttons**: Quick Reply `View My Balance` (Payload: `VIEW_BALANCE`)
