# Production Acceptance Record

Last checked: 2026-09-23. This is not a full launch sign-off.

## Deployed Environment

- Project: `susu-ledger-c3daa`; region: `africa-south1`.
- Cloud Run: `susu-backend-00014-hey`, 100% production traffic.
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

- `cd backend && npm test`: 11 integration tests passed against isolated PostgreSQL.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest --tests com.example.GroupStorageTest --tests com.example.ExampleRobolectricTest --tests com.example.ExampleUnitTest --max-workers=2 --console=plain`: build successful, 9 tests passed.
- Production launch checker passed database readiness, anonymous sync rejection,
  unsigned webhook rejection, webhook challenge, current signing secret, Meta
  access token, and Meta app secret checks.
- Debug APK installed on the connected Samsung SM-A075F and opened to onboarding.
- Integration tests cover atomic group/payment persistence, ownership checks,
  idempotency, rollback, ledger entries, phone-bound expiring pairing codes,
  claim rejection, receipt retry, and signed webhook retries/deduplication.

## Outstanding Gates

- Actual handset SMS receipt, authenticated production group sync, and WhatsApp
  pairing are awaiting interactive acceptance. Installation is not proof of these flows.
- Meta templates `susu_friday_reminder`, `susu_payment_receipt`, and
  `susu_sunday_summary` are PENDING. The launch checker correctly fails these
  three gates; receipt failures remain queued rather than undoing payments.
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
