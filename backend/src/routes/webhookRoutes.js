const router = require('express').Router();
const db = require('../config/database');
const { signatureMiddleware } = require('../middleware/webhookSignature');
const { handleIncomingWhatsAppMessage, handleMediaMessage } = require('../services/stateMachine');
const { handlePaystackWebhook } = require('../services/paystackService');

router.get('/whatsapp', (req, res) => {
  const token = process.env.META_WEBHOOK_VERIFY_TOKEN;
  if (token && req.query['hub.mode'] === 'subscribe' && req.query['hub.verify_token'] === token) {
    return res.status(200).send(req.query['hub.challenge']);
  }
  res.sendStatus(403);
});

router.post('/whatsapp', signatureMiddleware({secretName:'META_APP_SECRET',header:'x-hub-signature-256',algorithm:'sha256',prefix:'sha256='}), async (req,res) => {
  if (req.body.object !== 'whatsapp_business_account') return res.sendStatus(400);
  try {
    for (const entry of req.body.entry || []) {
      for (const change of entry.changes || []) {
        for (const message of change.value?.messages || []) {
          if (!message.id || !message.from) continue;
          const client = await db.pool.connect();
          try {
            await client.query('BEGIN');
            // Serialize a sender's state machine across Cloud Run instances.
            await client.query('SELECT pg_advisory_xact_lock(hashtext($1))',[message.from]);
            const duplicate=await client.query('SELECT id FROM webhook_events WHERE id=$1',[message.id]);
            if (!duplicate.rowCount) {
              const reply=message.interactive?.button_reply || message.interactive?.list_reply;
              if (message.type === 'text' || reply) {
                await handleIncomingWhatsAppMessage(message.from,message.text?.body || reply?.title || '',reply?.id);
              } else if (['image', 'document', 'audio', 'video'].includes(message.type)) {
                await handleMediaMessage(message.from, message.type, message[message.type]?.id);
              }
              await client.query('INSERT INTO webhook_events(id) VALUES($1)',[message.id]);
            }
            await client.query('COMMIT');
          } catch(error) {
            await client.query('ROLLBACK');
            throw error;
          } finally { client.release(); }
        }
      }
    }
    res.status(200).send('EVENT_RECEIVED');
  } catch(error) {
    console.error('[WhatsApp webhook failed]',error.code || error.message);
    res.sendStatus(503);
  }
});

router.post('/paystack', signatureMiddleware({secretName:'PAYSTACK_SECRET_KEY',header:'x-paystack-signature',algorithm:'sha512'}), async(req,res) => {
  try {
    await handlePaystackWebhook(req.body);
    res.sendStatus(200);
  } catch(error) {
    console.error('[Paystack webhook failed]',error.code || error.message);
    res.sendStatus(503);
  }
});

module.exports=router;
