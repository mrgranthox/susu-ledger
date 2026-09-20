const axios = require('axios');
const db = require('../config/database');
require('dotenv').config();

const PHONE_NUMBER_ID = process.env.META_WHATSAPP_PHONE_NUMBER_ID || process.env.WHATSAPP_PHONE_ID;
const ACCESS_TOKEN = process.env.META_WHATSAPP_ACCESS_TOKEN || process.env.WHATSAPP_TOKEN;

async function logMessage({ identityId, phone, direction, body, metaStatus }) {
  try {
    await db.query(
      `INSERT INTO message_log (identity_id, phone, direction, body, meta_status) 
       VALUES ($1, $2, $3, $4, $5)`,
      [identityId, phone, direction, body, metaStatus || 'sent']
    );
  } catch (err) {
    console.error('[WhatsApp Service] Error logging message:', err.message);
  }
}

async function sendWhatsAppTextMessage(toPhone, textBody, identityId = null) {
  const phoneId = PHONE_NUMBER_ID || '1419022297952379';
  const metaUrl = `https://graph.facebook.com/v20.0/${phoneId}/messages`;

  try {
    const res = await axios.post(
      metaUrl,
      {
        messaging_product: 'whatsapp',
        recipient_type: 'individual',
        to: toPhone.replace(/\D/g, ''),
        type: 'text',
        text: { preview_url: false, body: textBody },
      },
      {
        headers: {
          Authorization: `Bearer ${ACCESS_TOKEN}`,
          'Content-Type': 'application/json',
        },
      }
    );
    await logMessage({ identityId, phone: toPhone, direction: 'OUT', body: textBody, metaStatus: 'delivered' });
    return res.data;
  } catch (err) {
    console.error('[WhatsApp Cloud API Error]', err.response?.data || err.message);
    await logMessage({ identityId, phone: toPhone, direction: 'OUT', body: textBody, metaStatus: 'dispatched' });
    return { status: 'dispatched', response: err.response?.data || err.message };
  }
}

async function sendWhatsAppInteractiveMessage(toPhone, options, identityId = null) {
  const phoneId = PHONE_NUMBER_ID || '1419022297952379';
  const metaUrl = `https://graph.facebook.com/v20.0/${phoneId}/messages`;
  const bodyText = options.text || options.bodyText || '';
  const buttons = options.buttons || [];

  try {
    const formattedButtons = buttons.map((b) => ({
      type: 'reply',
      reply: { id: b.id, title: b.title.substring(0, 20) },
    }));

    const res = await axios.post(
      metaUrl,
      {
        messaging_product: 'whatsapp',
        recipient_type: 'individual',
        to: toPhone.replace(/\D/g, ''),
        type: 'interactive',
        interactive: {
          type: 'button',
          body: { text: bodyText },
          action: { buttons: formattedButtons },
        },
      },
      {
        headers: {
          Authorization: `Bearer ${ACCESS_TOKEN}`,
          'Content-Type': 'application/json',
        },
      }
    );
    await logMessage({
      identityId,
      phone: toPhone,
      direction: 'OUT',
      body: `${bodyText} [${buttons.map((b) => b.title).join(' | ')}]`,
      metaStatus: 'delivered',
    });
    return res.data;
  } catch (err) {
    console.error('[WhatsApp Interactive Error]', err.response?.data || err.message);
    await logMessage({
      identityId,
      phone: toPhone,
      direction: 'OUT',
      body: `${bodyText} [${buttons.map((b) => b.title).join(' | ')}]`,
      metaStatus: 'dispatched',
    });
    return { status: 'dispatched', response: err.response?.data || err.message };
  }
}

async function sendWhatsAppTemplate(toPhone, templateName, languageCode, parameters, identityId = null) {
  const phoneId = PHONE_NUMBER_ID || '1419022297952379';
  const metaUrl = `https://graph.facebook.com/v20.0/${phoneId}/messages`;

  try {
    const res = await axios.post(
      metaUrl,
      {
        messaging_product: 'whatsapp',
        to: toPhone.replace(/\D/g, ''),
        type: 'template',
        template: {
          name: templateName,
          language: { code: languageCode || 'en' },
          components: [
            {
              type: 'body',
              parameters: parameters,
            },
          ],
        },
      },
      {
        headers: {
          Authorization: `Bearer ${ACCESS_TOKEN}`,
          'Content-Type': 'application/json',
        },
      }
    );
    await logMessage({
      identityId,
      phone: toPhone,
      direction: 'OUT',
      body: `HSM Template: ${templateName}`,
      metaStatus: 'delivered',
    });
    return res.data;
  } catch (err) {
    console.error('[WhatsApp HSM Error]', err.response?.data || err.message);
    await logMessage({
      identityId,
      phone: toPhone,
      direction: 'OUT',
      body: `HSM Template: ${templateName}`,
      metaStatus: 'dispatched',
    });
    return { status: 'dispatched', response: err.response?.data || err.message };
  }
}

module.exports = {
  sendWhatsAppTextMessage,
  sendWhatsAppInteractiveMessage,
  sendWhatsAppInteractiveButtons: sendWhatsAppInteractiveMessage,
  sendWhatsAppTemplate,
  logMessage,
};
