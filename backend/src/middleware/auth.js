const { initializeApp, getApps } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');

async function requirePhoneAuth(req, res, next) {
  const match = /^Bearer (\S+)$/.exec(req.headers.authorization || '');
  if (!match) return res.status(401).json({ error: 'Phone sign-in required' });
  try {
    if (!getApps().length) initializeApp();
    req.auth = await getAuth().verifyIdToken(match[1]);
    if (!req.auth.phone_number) return res.status(403).json({ error: 'Verified phone required' });
    next();
  } catch (_) {
    res.status(401).json({ error: 'Session expired. Sign in again.' });
  }
}

module.exports = { requirePhoneAuth };
