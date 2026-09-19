# SusuLedger 🏦🔒

**SusuLedger** is an enterprise-grade, cryptographically tamper-proof digital record-keeping platform designed for Rotating Savings and Credit Associations (ROSCAs / Susu groups) across West Africa.

Built with a **"One Brain, Two Faces"** architecture, SusuLedger pairs a modern **Jetpack Compose Android Mobile App** for group officers with a **WhatsApp Chat Interface** for group members, backed by a **SHA-256 cryptographic hash-chain ledger** and double-entry accounting engine.

---

## 🌟 Key Capabilities

### 📱 1. Officer Mobile App (Android / Jetpack Compose)
- **App-First Clean Slate Onboarding**: Create Susu groups, set contribution amounts, configure weekly/monthly schedules, and register group members without pre-seeded mock data.
- **Hardware-Level Biometric & Lock Security**: Utilizes Android's native `BiometricPrompt` and System Credential PIN/Pattern locks to authorize sensitive operations (rejection of claims, cycle closes, and ledger exports).
- **Interactive Dashboard & Progress Tracking**: Real-time progress indicators, confirmed vs. pending funds, and quick-action approval lists.
- **Local-First Architecture**: Powered by Room Database as the single source of truth, enabling full offline operation with cloud background synchronization.

### 💬 2. Member WhatsApp State Machine
- **WhatsApp Bot Interface**: Members can check their balance, view cycle history, and submit payment claims directly inside WhatsApp using interactive reply buttons and Meta pre-approved templates.
- **6-Digit Pairing Engine**: Secure 15-minute expiring pairing code system that links member phone numbers to WhatsApp state machine sessions.

### 🔐 3. Cryptographic Immutability & Double-Entry Accounting
- **SHA-256 Hash Chaining**: Every payment transaction auto-computes a cryptographic SHA-256 hash derived from the preceding row's hash (`prev_hash`), creating an append-only, tamper-evident audit trail.
- **Balanced Double-Entry Engine**: Auto-generates balanced ledger entries debiting Cash/MoMo Asset accounts and crediting Member Equity accounts for strict financial accountability.

### 💳 4. Paystack Ghana & Mobile Money Integration
- **Multi-Rail Support**: Designed for MTN Mobile Money (MoMo), Telecel Cash, and AT Money collections and automated payouts via Paystack API.

---

## 🏗 System Architecture

```
+------------------------+             +------------------------+
|  Android App (Mobile)  |             |  WhatsApp (Members)    |
|  (Treasurer / Officers) |             |  (Chat Interface)      |
+-----------+------------+             +-----------+------------+
            | HTTPS / REST                          | Webhooks (HTTPS)
            v                                       v
+----------------------------------------------------------------+
|              API LAYER (Node.js / Express on Cloud Run)        |
|  /api/app/* (App REST Endpoints) | /webhooks/whatsapp (Bot API) |
+----------------------------------------------------------------+
                                   |
                                   v
+----------------------------------------------------------------+
|                        CORE SERVICES LAYER                     |
|  • Conversation Engine (State Machine)                         |
|  • Double-Entry Ledger & Cryptographic SHA-256 Engine          |
|  • WhatsApp Notification & Meta Template Service               |
|  • Auth & PIN Engine (Firebase Auth + Salted PIN Hashes)       |
|  • Report & CSV Generator Service                              |
+----------------------------------------------------------------+
            |                              |                   |
            v                              v                   v
+----------------------+        +--------------------+   +-------------------+
| Cloud SQL PostgreSQL |        | Firebase Storage   |   | Cloud Scheduler   |
| (Double-Entry Ledger)|        | (PDFs, Receipts)   |   | (Cron Jobs)       |
+----------------------+        +--------------------+   +-------------------+
```

---

## 📂 Repository Structure

```
SusuLedger/
├── app/
│   ├── src/main/java/com/example/
│   │   ├── MainActivity.kt                # Application Entry Point & Navigation Scaffold
│   │   ├── data/
│   │   │   ├── local/                     # Room Entities, DAOs, and Database Configuration
│   │   │   │   ├── SusuDao.kt             # Room Data Access Object
│   │   │   │   ├── SusuDatabase.kt        # Room Database Definition
│   │   │   │   └── Entities.kt            # Group, Member, Cycle, Payment, Ledger, Claim Entities
│   │   │   └── repository/
│   │   │       └── SusuRepository.kt      # Single Source of Truth Data Repository
│   │   ├── ui/
│   │   │   ├── SusuViewModel.kt           # ViewModel & Reactive State Flow
│   │   │   ├── components/                # Shared UI Components & Biometric Auth Dialog
│   │   │   ├── screens/                   # Compose Screen Composables
│   │   │   │   ├── DashboardScreen.kt     # Active Cycle Progress & Approval Center
│   │   │   │   ├── MembersScreen.kt       # Member Roster & Individual Balances
│   │   │   │   ├── HistoryScreen.kt       # Closed Cycles & Ledger Audit Summary
│   │   │   │   ├── MoreSettingsScreen.kt  # System Configuration & Export Sheet
│   │   │   │   ├── ConfirmPaymentSheet.kt # Payment Entry Modal
│   │   │   │   ├── OnboardingFlowScreen.kt# Clean Slate Setup Wizard
│   │   │   │   └── AppLockScreen.kt       # Hardware Biometric & PIN Security Gate
│   │   │   └── theme/                     # Material Design 3 Color Schemes & Typography
├── docs/
│   ├── DEPLOYMENT_GUIDE.md                # Production Cloud Run & GCP CLI Commands
│   ├── GCP_FIREBASE_ENTERPRISE_ARCHITECTURE.md # Cloud Topology & Infrastructure Spec
│   └── PAYSTACK_INTEGRATION_PLAN.md      # Paystack Mobile Money & Payout Flow
├── metadata.json                          # AI Studio Platform Metadata
└── build.gradle.kts                       # Android App Build Configuration
```

---

## 🛠 Tech Stack

- **UI Framework**: Jetpack Compose (Material Design 3)
- **Programming Language**: Kotlin 1.9+
- **Local Database**: Room DB (SQLite) with Coroutines Flow
- **State Management**: Android ViewModel & `StateFlow`
- **Security**: AndroidX `BiometricPrompt`, Android KeyStore, SHA-256 Hashing
- **Cloud Infrastructure**: GCP Cloud Run, Cloud SQL (PostgreSQL), Cloud Redis, Cloud Scheduler
- **Authentication & Storage**: Firebase Auth, Firebase Storage

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or JDK 17+
- Android SDK 34 (`compileSdk = 34`, `minSdk = 24`)

### Build & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/your-org/susu-ledger.git
   cd susu-ledger
   ```
2. Open the project in **Android Studio**.
3. Sync Gradle dependencies:
   ```bash
   ./gradlew assembleDebug
   ```
4. Run the app on an Android Device or Emulator (API 24+).

---

## 🛡 Security & Compliance

1. **Hardware PIN/Biometrics**: Sensitive actions prompt the OS-level `BiometricPrompt` or fallback to device hardware PIN/Pattern.
2. **Double-Entry Balance Constraint**: Every debit entry must strictly equal credit entry values before ledger committing.
3. **Cryptographic Chain Verification**: The `generate_payment_hash()` PostgreSQL trigger validates ledger state continuity before allowing app updates.

---

## 📄 License

This project is proprietary and confidential. All rights reserved.
