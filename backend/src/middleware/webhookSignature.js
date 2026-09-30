const { createHmac, timingSafeEqual } = require('node:crypto');

function signatureMiddleware({ secretName, header, algorithm, prefix = '' }) {
  return (req, res, next) => {
    const secret = process.env[secretName];
    if (!secret) return res.status(503).json({ error: 'Webhook verification is not configured' });
    if (req.rawBody === undefined) {
      console.error('[webhookSignature] rawBody is undefined — body-parser verify hook missing');
      return res.status(500).json({ error: 'Server misconfiguration: rawBody unavailable' });
    }
    const supplied = req.get(header) || '';
    const expected = prefix + createHmac(algorithm,secret).update(req.rawBody).digest('hex');
    if (Buffer.byteLength(supplied) !== Buffer.byteLength(expected) || !timingSafeEqual(Buffer.from(supplied),Buffer.from(expected))) {
      return res.status(401).json({ error: 'Invalid webhook signature' });
    }
    next();
  };
}

module.exports = { signatureMiddleware };
