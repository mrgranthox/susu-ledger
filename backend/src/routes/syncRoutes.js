const router = require('express').Router();
const { requirePhoneAuth } = require('../middleware/auth');
const { syncGroup, cloudId } = require('../services/syncService');
const { dispatchReceipt } = require('../services/receiptService');

router.post('/sync', requirePhoneAuth, async (req, res) => {
  try {
    const result = await syncGroup(req.body, req.auth.phone_number);
    const receiptIds = req.body.receiptPaymentIds || [];
    const acceptedReceiptIds=[];
    // Limit provider work so storage acknowledgement fits the mobile request timeout.
    for(const id of receiptIds.slice(0,1)) {
      if(await dispatchReceipt(cloudId(id))) acceptedReceiptIds.push(id);
    }
    res.json({...result,acceptedReceiptIds,pendingReceiptCount:receiptIds.length-acceptedReceiptIds.length});
  } catch (error) {
    console.error('[Sync]', error.code || error.message);
    res.status(error.status || (error.code ? 500 : 400)).json({ error: error.code ? 'Cloud storage failed. Please retry.' : error.message });
  }
});

module.exports = router;
