# Production Acceptance Record

Last checked: 2026-09-23. This is not a full launch sign-off.

## Deployed Environment

- Project: `susu-ledger-c3daa`; region: `africa-south1`.
- Cloud Run: `susu-backend-00020-pew`, 100% production traffic, verified after resuming work.
- Android/Meta endpoint: `https://susu-backend-965064733382.africa-south1.run.app`.
- Cloud SQL schema includes persistent pairing, webhook deduplication, and receipt queue tables.
- Runtime credentials are Secret Manager references, not raw environment values.
- Firebase phone authentication allows Ghana; the local debug signing fingerprints are registered.
- Corrected the Android API key allowlist to include the installed debug APK's
  SHA-1 alongside the existing certificate. Verified the app uses that key and
  its Firebase project-config request changed from HTTP 403 to HTTP 200 with
  the matching Android package/certificate headers. SMS delivery still requires
  a handset retry; this configuration probe does not send an SMS.
- Subsequent handset logs showed Play Integrity rejecting the sideloaded APK,
  followed by failed reCAPTCHA verification. A browser-context configuration
  probe reproduced a 403 from the Android-only API-key restriction. The
  follow-up configuration removes that application restriction while retaining
  the existing API service allowlist, enabling the documented browser fallback.
  The launch checker now probes this path; it does not simulate CAPTCHA or SMS.

## Verified

- `cd backend && npm test`: 15 integration tests passed against isolated PostgreSQL.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest --tests com.example.GroupStorageTest --tests com.example.ExampleRobolectricTest --tests com.example.ExampleUnitTest --max-workers=1 --console=plain`: build successful, 10 tests passed on the latest UI-audit changes.
- Production launch checker passed database readiness, anonymous sync rejection,
  unsigned webhook rejection, webhook challenge, current signing secret, Meta
  access token, and Meta app secret checks.
- Latest debug APK installed on the connected Samsung SM-A075F and launched successfully. Bot-screen screenshot inspected without recording or reconfirming real payments.
- Integration tests cover atomic group/payment persistence, ownership checks,
  idempotency, rollback, ledger entries, phone-bound expiring pairing codes,
  claim rejection, receipt retry, and signed webhook retries/deduplication.

## UI And Claim Follow-Up

- Preserved the UI audit's style and bottom-sheet changes. Corrected onboarding
  pairing before group creation, returning-user Create navigation, and biometric
  sign-in requiring an additional PIN after a successful device prompt.
- Fixed the payment-sheet dismissal clearing its claim before authorization.
  The callback now retains the claim and target week; local payment, ledger,
  and claim updates commit together. Stale cloud pending rows cannot overwrite
  a locally resolved claim. Cloud terminal states clear older stuck pending rows.
- Foreground claim refresh runs every five seconds plus network time; dashboard
  Refresh performs synchronization. This is polling, not push delivery.
- Connection status comes from the selected group's server pairing records.
  Connected views hide the code and retain Reconnect; errors remain visible.
  Unpaired/disconnected groups receive a bot guard instead of recording claims.
- Late payments retain their original cycle: select the week in the payment
  sheet or send `PAID WEEK 1`. Closed-week payments are appended, not moved into
  the current week. New-week dues and dates are honored, and partial payments
  no longer mark a member fully paid in current-week statistics.
- Week announcements have a durable, consent-filtered cloud queue. Retries are
  driven by app sync/WorkManager; this is not an independent server scheduler.
- Regression tests cover claim double-confirmation prevention, late-cycle
  allocation, disconnected guards, text receipts in recent conversations,
  notification retry/idempotency, and legacy schema repairs.
- Successful device biometric sign-in and clearing the user's original stuck
  claim still require interactive confirmation. Do not re-confirm a payment
  merely to clear an old card.

## Outstanding Gates

Pairing follow-up: bare `ABC-DEF` messages now enter the pairing flow, alongside
`PAIR:ABC-DEF` and `PAIR ABC-DEF`; `PAIR` alone returns guidance. Production
silence was traced to PostgreSQL error 42P01: the legacy database lacked
`bot_sessions`. Migration `04_bot_sessions.sql` was applied without dropping
existing data. All 13 backend tests pass, including repeatable legacy-schema
repair and command parsing. Android builds successfully; Copy now includes
`PAIR:`. Pairing now persists the code and absolute expiry per officer and group
across process recreation. Reopening does not rotate codes, including expired
ones; only explicit generation replaces them. Concurrent registration is guarded,
and retries check server status before attempting to register the same code.
The outer tab scaffold consumes its insets so child headers do not apply the
status-bar gap a second time.

Handset follow-up: authenticated group upload and pairing registration reached
production successfully. Two newly registered codes were unexpired and pending;
WhatsApp sent them from a different number than the SMS-verified owner. Pairing
correctly rejected the mismatch, but the old response misleadingly blamed expiry.
The bot response now explains the same-number requirement without disclosing
the owner's phone. The mismatch test also verifies it does not consume the code.
Migration `03_message_log_phone.sql` repairs the legacy production message-log
table's missing phone column. A production insert was verified inside a rolled-back
transaction; all 11 backend regression tests passed again.

- The user confirmed successful WhatsApp pairing and claim submission. Each
  additional group requires its own pairing; membership alone cannot pair it.
- Meta `susu_friday_reminder` and `susu_sunday_summary` are APPROVED.
  `susu_payment_receipt` and `susu_week_opened` remain PENDING. Conversation replies
  are used for recent inbound chats; messages needing these unapproved templates
  remain queued. The launch checker still fails the receipt approval gate.
- Scheduled reminders need service-account authentication, job deduplication,
  and Scheduler provisioning. Current routes support authenticated manual triggers.
- Multi-device restore/merge and ledger correction synchronization are not
  validated or delivered by the upload-only group sync flow.
- Returning-user SMS reauthentication and PIN recovery require further review.
- Paystack flows and release signing/Play distribution have not passed acceptance.
- Provider acceptance is not handset delivery. A provider success followed by a
  database-commit failure can still cause a receipt retry; exactly-once external
  delivery is not guaranteed.
- Credentials previously pasted into chat should be rotated through their
  providers and Secret Manager before public launch. Do not put replacements in Git.

## Repeat Checks

Run `node backend/scripts/launch-check.js` for the active revision or add
`--candidate` for the tagged revision. Run database migrations with
`node backend/scripts/migrate.js` through a securely configured SQL connection
before deploying code that requires new tables. Do not recreate existing SQL
resources or apply destructive schema resets.
