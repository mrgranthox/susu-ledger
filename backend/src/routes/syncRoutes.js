const router = require('express').Router();
const { requirePhoneAuth } = require('../middleware/auth');
const { syncGroup, cloudId } = require('../services/syncService');
const { dispatchReceipt } = require('../services/receiptService');
const { dispatchWeekNotification } = require('../services/weekNotificationService');
const db = require('../config/database');

router.post('/sync', requirePhoneAuth, async (req, res) => {
  try {
    const result = await syncGroup(req.body, req.auth.phone_number);
    const receiptIds = req.body.receiptPaymentIds || [];
    const acceptedReceiptIds=[];
    const pendingReceipts = await db.query(
      `SELECT pr.payment_id FROM payment_receipts pr
       JOIN payments p ON p.id = pr.payment_id
       JOIN cycles c ON c.id = p.cycle_id
       WHERE c.group_id = $1 AND pr.state = 'pending'
       ORDER BY pr.attempts ASC LIMIT 3`,
      [cloudId(req.body.group.id)]
    );
    const toDispatch = [...new Set([...receiptIds.map(id => cloudId(id)), ...pendingReceipts.rows.map(r => r.payment_id)])];
    // Limit provider work so storage acknowledgement fits the mobile request timeout.
    await Promise.all(toDispatch.slice(0,3).map(async id => {
      if(await dispatchReceipt(id)) acceptedReceiptIds.push(id);
    }));
    await Promise.all(Array.from({length:3},()=>dispatchWeekNotification(cloudId(req.body.group.id))));
    const notices=await db.query("SELECT count(*)::int AS count FROM week_notifications n JOIN cycles c ON c.id=n.cycle_id WHERE c.group_id=$1 AND n.state='pending'",[cloudId(req.body.group.id)]);
    res.json({...result,acceptedReceiptIds,pendingReceiptCount:receiptIds.length-acceptedReceiptIds.length,pendingNotificationCount:notices.rows[0].count});
  } catch (error) {
    console.error('[Sync Error]', error);
    res.status(error.status || (error.code ? 500 : 400)).json({
      error: error.message || 'Cloud storage failed. Please retry.',
      code: error.code
    });
  }
});

module.exports = router;
