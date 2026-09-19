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

// Health Check for Cloud Run
app.get('/healthz', (req, res) => {
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
