const axios = require('axios');
const db = require('../config/database');
require('dotenv').config();

const PAYSTACK_SECRET_KEY = process.env.PAYSTACK_SECRET_KEY;
const PAYSTACK_BASE_URL = 'https://api.paystack.co';

async function initializeMoMoSubscription({ email, phone, groupId, network, amount = 4000 }) { // 4000 pesewas = GHS 40
  if (!PAYSTACK_SECRET_KEY) {
    console.log(`[Paystack Sim] Initialized MoMo subscription for ${phone} (${network}): GHS ${amount / 100}`);
    return {
      status: true,
      data: {
        authorization_url: 'https://checkout.paystack.com/simulated',
        reference: `PAY-SIM-${Date.now()}`,
      },
    };
  }

  try {
    const response = await axios.post(
      `${PAYSTACK_BASE_URL}/transaction/initialize`,
      {
        email: email || `${phone.replace(/\D/g, '')}@susuledger.com`,
        amount: amount, // amount in pesewas
        currency: 'GHS',
        channels: ['mobile_money'],
        metadata: {
          group_id: groupId,
          phone: phone,
          network: network, // MTN, VOD (Telecel), AIR (AT)
        },
      },
      {
        headers: {
          Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`,
          'Content-Type': 'application/json',
        },
      }
    );
    return response.data;
  } catch (error) {
    console.error('[Paystack Init Error]', error.response?.data || error.message);
    throw error;
  }
}

async function handlePaystackWebhook(event) {
  const eventType = event.event;
  const data = event.data;

  console.log(`[Paystack Webhook] Received event: ${eventType}`);

  switch (eventType) {
    case 'charge.success': {
      const groupId = data.metadata?.group_id;
      if (groupId) {
        // Record active subscription
        await db.query(
          `INSERT INTO subscriptions (
             group_id, paystack_customer_code, amount, currency, status,
             current_period_start, current_period_end
           )
           VALUES ($1, $2, $3, 'GHS', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '30 days')
           ON CONFLICT (id) DO UPDATE
           SET status = 'active', current_period_end = CURRENT_TIMESTAMP + INTERVAL '30 days'`,
          [groupId, data.customer?.customer_code, (data.amount / 100)]
        );

        await db.query(
          `INSERT INTO audit_log (group_id, action, payload)
           VALUES ($1, 'SUBSCRIPTION_RENEWED', $2)`,
          [groupId, JSON.stringify({ reference: data.reference, amount: data.amount / 100 })]
        );
      }
      break;
    }

    case 'invoice.payment_failed': {
      const customerCode = data.customer?.customer_code;
      // Mark as past_due (14 days grace period). Never delete group data.
      await db.query(
        `UPDATE subscriptions 
         SET status = 'past_due' 
         WHERE paystack_customer_code = $1`,
        [customerCode]
      );
      break;
    }

    default:
      break;
  }
}

module.exports = {
  initializeMoMoSubscription,
  handlePaystackWebhook,
};
