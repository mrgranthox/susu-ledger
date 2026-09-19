# SusuLedger 30-Day Market Pilot Testing Guide

### Pilot Scope & Cohort Selection
- **Cohort Size**: 2 pilot groups
  - Group 1: Nima Market Yam & Tomato Traders Association (15–20 members, GHS 50/week)
  - Group 2: Adabraka Church Women's Fellowship Savings Circle (10–15 members, GHS 100/week)
- **Duration**: 4 consecutive collection cycles (30 days).

---

### Phase 1: Onboarding & Hardware Verification (Days 1–3)
1. Install SusuLedger APK on the treasurer's Android smartphone.
2. Complete the 6-step in-app onboarding:
   - Configure weekly contribution (GHS 50).
   - Enter member phone numbers with `+233`.
   - Issue the 6-character monospace pairing code (`A7K2-9P`) to the group WhatsApp broadcast.
3. Verify that members receive the welcome prompt and reply `OK` to log DPC compliance consent.

---

### Phase 2: First Collection Cycle (Days 4–10)
1. **Friday 08:00 GMT**: Verify that Google Cloud Scheduler triggers the `susu_friday_reminder` template to pending members.
2. **Payment Flow**:
   - Member transfers GHS 50 via MTN MoMo to treasurer.
   - Member sends "PAID" on WhatsApp.
   - Treasurer sees claim in "Needs Your Attention" on dashboard.
   - Treasurer taps "Confirm (✅)" and selects MoMo; prompt verifies Biometric / PIN.
   - Member receives WhatsApp receipt with SHA-256 block hash.

---

### Phase 3: Dispute & Reversal Testing (Days 11–18)
1. Intentionally simulate a disputed claim (e.g. member claims payment with incorrect MoMo reference).
2. Verify that the treasurer can inspect the claim in the "Disputed" tab and request MoMo transaction ID proof.
3. Test dual sign-off ledger correction requiring the second officer's PIN.

---

### Phase 4: Closure, Export & Audit Review (Days 19–30)
1. Close Cycle 12 on the app.
2. Export the official cycle CSV and share directly to the WhatsApp group.
3. Run the in-app SHA-256 Cryptographic Chain Verification tool: confirm 100% integrity validation.
4. Collect qualitative treasurer feedback on battery usage, sunlight readability, and time saved per collection day.
