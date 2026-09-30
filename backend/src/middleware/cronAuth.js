const { requirePhoneAuth } = require('./auth');

function cronAuth(req, res, next) {
  const cronSecret = process.env.CRON_SECRET;
  const headerSecret = req.get('x-cron-secret');
  if (cronSecret && headerSecret === cronSecret) {
    req.isScheduler = true;
    return next();
  }

  requirePhoneAuth(req, res, next);
}

module.exports = { cronAuth };
