const { createHmac, timingSafeEqual } = require('node:crypto');

function signatureMiddleware({ secretName, header, algorithm, prefix = '' }) {
  return (req, res, next) => {
    const secret = process.env[secretName];
    if (!secret) return res.status(503).json({ error: 'Webhook verification is not configured' });
    const supplied = req.get(header) || '';
    const expected = prefix + createHmac(algorithm,secret).update(req.rawBody || Buffer.alloc(0)).digest('hex');
    if (Buffer.byteLength(supplied) !== Buffer.byteLength(expected) || !timingSafeEqual(Buffer.from(supplied),Buffer.from(expected))) {
      return res.status(401).json({ error: 'Invalid webhook signature' });
    }
    next();
  };
}

module.exports = { signatureMiddleware };
