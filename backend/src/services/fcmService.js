const { getApps, initializeApp, cert } = require('firebase-admin/app');
const { getMessaging } = require('firebase-admin/messaging');
const db = require('../config/database');

function ensureFirebase() {
  if (getApps().length) return true;
  try {
    if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
      const sa = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);
      initializeApp({ credential: cert(sa) });
      return true;
    }
    initializeApp();
    return true;
  } catch (err) {
    console.warn('[FCM] Firebase initialization warning:', err.message);
    return false;
  }
}

async function pushToTreasurer(treasurerIdentityId, { title, body, data = {} }) {
  if (!ensureFirebase()) return false;
  try {
    const res = await db.query(
      'SELECT fcm_token FROM identities WHERE id = $1 AND fcm_token IS NOT NULL',
      [treasurerIdentityId]
    );
    const token = res.rows[0]?.fcm_token;
    if (!token) return false;

    await getMessaging().send({
      token,
      notification: { title, body },
      data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
      android: { priority: 'high' }
    });
    return true;
  } catch (err) {
    console.warn('[FCM Push Warning]', err.message);
    return false;
  }
}

module.exports = { pushToTreasurer };
