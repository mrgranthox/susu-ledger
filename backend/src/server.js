const express = require('express');
const cors = require('cors');
require('dotenv').config();

const appRoutes = require('./routes/appRoutes');
const webhookRoutes = require('./routes/webhookRoutes');
const cronRoutes = require('./routes/cronRoutes');

const app = express();
const PORT = process.env.PORT || 8080;

app.use(cors());
app.use(express.json());

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
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
        .card { background: #1e293b; padding: 2.5rem; border-radius: 1rem; border: 1px solid #334155; text-align: center; max-width: 480px; box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3); }
        h1 { color: #10b981; margin-top: 0; font-size: 1.8rem; }
        p { color: #94a3b8; line-height: 1.6; }
        .status { display: inline-block; background: rgba(16, 185, 129, 0.1); color: #10b981; border: 1px solid #10b981; padding: 0.4rem 1rem; border-radius: 9999px; font-weight: 600; font-size: 0.875rem; margin-top: 1rem; }
      </style>
    </head>
    <body>
      <div class="card">
        <h1>SusuLedger Financial Solutions</h1>
        <p>Enterprise digital record-keeping information layer and double-entry ledger engine for Susu savings groups.</p>
        <div class="status">✓ API Service Online & Healthy</div>
      </div>
    </body>
    </html>
  `);
});

// Health Checks for Cloud Run & Monitoring
app.get(['/health', '/healthz'], (req, res) => {
  res.status(200).json({ status: 'ok', service: 'SusuLedger Cloud Run API', timestamp: new Date().toISOString() });
});

// Mount Routes
app.use('/api/app', appRoutes);
app.use('/webhooks', webhookRoutes);
app.use('/api/cron', cronRoutes);

// Global Error Handler
app.use((err, req, res, next) => {
  console.error('[Server Error]', err.stack);
  res.status(500).json({ error: 'Internal server error', message: err.message });
});

app.listen(PORT, () => {
  console.log(`🚀 SusuLedger Backend running on port ${PORT}`);
  console.log(`👉 Webhook endpoint: /webhooks/whatsapp`);
  console.log(`👉 App REST API: /api/app/*`);
  console.log(`👉 Cloud Scheduler: /api/cron/*`);
});

module.exports = app;
