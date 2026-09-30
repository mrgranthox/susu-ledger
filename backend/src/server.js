const express = require('express');
const cors = require('cors');
require('dotenv').config();

const appRoutes = require('./routes/appRoutes');
const webhookRoutes = require('./routes/webhookRoutes');
const cronRoutes = require('./routes/cronRoutes');

const app = express();
const PORT = process.env.PORT || 8080;

app.use(cors());
app.use(express.json({ limit: '5mb', verify: (req, res, buffer) => { req.rawBody = buffer; } }));

// Root Landing Page (for Meta Business Verification & Web Crawlers)
app.get('/', (req, res) => {
  res.status(200).send(`
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>SusuLedger Financial Solutions</title>
      <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 1rem; }
        .card { background: #1e293b; padding: 2.5rem; border-radius: 1rem; border: 1px solid #334155; text-align: center; max-width: 540px; box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3); }
        h1 { color: #10b981; margin-top: 0; font-size: 1.8rem; }
        p { color: #94a3b8; line-height: 1.6; }
        .links { margin-top: 1.5rem; }
        .links a { color: #38bdf8; text-decoration: none; margin: 0 0.5rem; font-size: 0.9rem; }
        .links a:hover { text-decoration: underline; }
        .status { display: inline-block; background: rgba(16, 185, 129, 0.1); color: #10b981; border: 1px solid #10b981; padding: 0.4rem 1rem; border-radius: 9999px; font-weight: 600; font-size: 0.875rem; margin-top: 1rem; }
      </style>
    </head>
    <body>
      <div class="card">
        <h1>SusuLedger Financial Solutions</h1>
        <p>Enterprise digital record-keeping information layer and double-entry ledger engine for Susu savings groups in Ghana & West Africa.</p>
        <div class="status">✓ API Service Online & Healthy</div>
        <div class="links">
          <a href="/privacy">Privacy Policy</a> • 
          <a href="/terms">Terms of Service</a> • 
          <a href="/data-deletion">Data Deletion</a>
        </div>
      </div>
    </body>
    </html>
  `);
});

// Privacy Policy (Meta App Requirement)
app.get('/privacy', (req, res) => {
  res.status(200).send(`
    <!DOCTYPE html>
    <html>
    <head><title>SusuLedger - Privacy Policy</title><style>body{font-family:sans-serif;padding:2rem;max-width:800px;margin:0 auto;line-height:1.6;color:#1e293b;}</style></head>
    <body>
      <h1>Privacy Policy for SusuLedger</h1>
      <p>Last updated: September 2026</p>
      <p>SusuLedger ("we", "our") complies with the Ghana Data Protection Act 843 (DPC Act 843). We respect your privacy and process personal data solely to facilitate record-keeping for traditional Susu groups.</p>
      <h2>Information We Collect</h2>
      <p>We collect mobile phone numbers provided during authentication and transactional claims submitted via WhatsApp or the mobile app.</p>
      <h2>How We Use Data</h2>
      <p>Data is strictly used to maintain double-entry journal logs, cryptographic SHA-256 hash chaining, and issue payment receipts.</p>
      <h2>Contact DPO</h2>
      <p>For privacy inquiries, contact our Data Protection Officer at <strong>xbeeneski@gmail.com</strong>.</p>
    </body>
    </html>
  `);
});

// Terms of Service (Meta App Requirement)
app.get('/terms', (req, res) => {
  res.status(200).send(`
    <!DOCTYPE html>
    <html>
    <head><title>SusuLedger - Terms of Service</title><style>body{font-family:sans-serif;padding:2rem;max-width:800px;margin:0 auto;line-height:1.6;color:#1e293b;}</style></head>
    <body>
      <h1>Terms of Service</h1>
      <p>SusuLedger functions strictly as an independent digital record-keeping information layer and software tool.</p>
      <h2>Scope of Service</h2>
      <p>SusuLedger does not hold, custody, or route financial funds. All financial settlements occur directly between group members and officers.</p>
      <h2>User Responsibilities</h2>
      <p>Group officers are responsible for ensuring accuracy when confirming cash or mobile money claims.</p>
    </body>
    </html>
  `);
});

// User Data Deletion Instructions (Meta App Requirement)
app.get('/data-deletion', (req, res) => {
  res.status(200).send(`
    <!DOCTYPE html>
    <html>
    <head><title>SusuLedger - User Data Deletion</title><style>body{font-family:sans-serif;padding:2rem;max-width:800px;margin:0 auto;line-height:1.6;color:#1e293b;}</style></head>
    <body>
      <h1>User Data Deletion Instructions</h1>
      <p>In accordance with Ghana DPC Act 843 (Right to Erasure), users can request full deletion of their identity and associated transaction history.</p>
      <h2>How to Request Account Deletion:</h2>
      <ol>
        <li>Open the SusuLedger Mobile App -> <strong>More Settings</strong> -> <strong>Data & Privacy</strong>.</li>
        <li>Tap <strong>Purge Account Data</strong>.</li>
        <li>Alternatively, email <strong>xbeeneski@gmail.com</strong> with your registered phone number. Data will be erased within 48 hours.</li>
      </ol>
    </body>
    </html>
  `);
});

// Health Checks for Cloud Run & Monitoring
app.get(['/health', '/healthz'], (req, res) => {
  res.status(200).json({ status: 'ok', service: 'SusuLedger Cloud Run API', timestamp: new Date().toISOString() });
});

// Mount Routes
app.use('/api/app', require('./routes/syncRoutes'));
app.use('/api/app', appRoutes);
app.use('/webhooks', webhookRoutes);
app.use('/api/cron', cronRoutes);

// Global Error Handler
app.use((err, req, res, next) => {
  console.error('[Server Error]', err.stack);
  res.status(500).json({ error: 'Internal server error', message: err.message });
});

const { runMigrations } = require('./config/migrate');

if (require.main === module) {
  runMigrations().catch(err => console.warn('[Auto-Migrate Notice]', err.message));
  app.listen(PORT, () => {
    console.log(`🚀 SusuLedger Backend running on port ${PORT}`);
    console.log(`👉 Webhook endpoint: /webhooks/whatsapp`);
    console.log(`👉 App REST API: /api/app/*`);
    console.log(`👉 Cloud Scheduler: /api/cron/*`);
  });
}

module.exports = app;
