# Ghana Data Protection Commission (DPC) Compliance & Legal Shield

### 1. Statutory Registration & Legal Scope
Under the **Data Protection Act, 2012 (Act 843)** of the Republic of Ghana, **SusuLedger** operates as a registered Data Controller and Data Processor for rotating savings groups.

### 2. Statutory Non-Custodial Legal Shield
SusuLedger must prominently communicate its statutory non-custodial status across all mobile views, WhatsApp receipts, and PDF activity reports:
> **STATUTORY NOTICE**: *"SusuLedger is strictly an independent, non-custodial digital record-keeping information layer. SusuLedger does NOT hold, receive, transmit, custody, or convert funds. All financial exchanges occur strictly peer-to-peer between members and treasurers via authorized third-party Payment Service Providers (e.g. MTN MoMo, Telecel Cash, AT Money, or Cash). SusuLedger disclaims all liability for off-platform financial defaults."*

### 3. Member Consent & Opt-In (WhatsApp)
- **Explicit Consent**: Prior to receiving group alerts or transaction logging, members must send `OK` or enter the pairing code `PAIR:XXXX-XX`.
- **Timestamped Audit**: Consent timestamps are recorded in the `identities.dpc_consent_timestamp` column.
- **Unsubscribe**: Any member replying `STOP` or `EXIT` is immediately disengaged from automated reminders, with state transitioned to `exited`.

### 4. Data Retention & Integrity Guarantee
- In accordance with accounting standard practices, immutable double-entry ledger entries and SHA-256 blocks are preserved for 7 years to facilitate dispute resolution.
- **Lapsed Subscription Guarantee**: Even if a group treasurer's subscription lapses, group financial records are never purged or deleted; they remain permanently readable and exportable.
