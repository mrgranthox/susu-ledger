const express = require('express');
const router = express.Router();
const { handleIncomingWhatsAppMessage } = require('../services/stateMachine');
const { handlePaystackWebhook } = require('../services/paystackService');
require('dotenv').config();

const VERIFY_TOKENS = [
  process.env.META_WEBHOOK_VERIFY_TOKEN,
  process.env.WHATSAPP_VERIFY_TOKEN,
  'susu_webhook_token_2026',
  'susu_webhook_secret_token_2026'
].filter(Boolean);

// 1. Meta WhatsApp Webhook Verification (GET)
router.get('/whatsapp', (req, res) => {
  const mode = req.query['hub.mode'];
  const token = req.query['hub.verify_token'];
  const challenge = req.query['hub.challenge'];

  if (mode && token) {
    if (mode === 'subscribe' && VERIFY_TOKENS.includes(token)) {
      console.log('[Meta Webhook] Verified successfully with token:', token);
      return res.status(200).send(challenge);
    } else {
      console.warn('[Meta Webhook] Token mismatch. Received:', token, 'Expected one of:', VERIFY_TOKENS);
      return res.sendStatus(403);
    }
  } else {
    return res.sendStatus(400);
  }
});

// 2. Meta WhatsApp Incoming Events (POST)
router.post('/whatsapp', async (req, res) => {
  const body = req.body;

  if (body.object) {
    if (
      body.entry &&
      body.entry[0].changes &&
      body.entry[0].changes[0].value.messages &&
      body.entry[0].changes[0].value.messages[0]
    ) {
      const message = body.entry[0].changes[0].value.messages[0];
      const fromPhone = message.from;
      let textBody = '';
      let buttonPayload = null;

      if (message.type === 'text') {
        textBody = message.text.body;
      } else if (message.type === 'interactive') {
        if (message.interactive.type === 'button_reply') {
          buttonPayload = message.interactive.button_reply.id;
          textBody = message.interactive.button_reply.title;
        }
      }

      try {
        await handleIncomingWhatsAppMessage(fromPhone, textBody, buttonPayload);
      } catch (err) {
        console.error('[Webhook Processing Error]', err);
      }
    }
    res.status(200).send('EVENT_RECEIVED');
  } else {
    res.sendStatus(404);
  }
});

// 3. Paystack MoMo Webhook (POST)
router.post('/paystack', async (req, res) => {
  try {
    await handlePaystackWebhook(req.body);
    res.sendStatus(200);
  } catch (err) {
    console.error('[Paystack Webhook Error]', err);
    res.sendStatus(500);
  }
});

module.exports = router;
