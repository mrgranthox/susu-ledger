const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { generatePaymentHash, verifyLedgerChain } = require('../services/cryptoEngine');
const { initializeMoMoSubscription } = require('../services/paystackService');
const { generateCycleCsv, generateAnnualLedgerSummary } = require('../services/exportService');

// 1. Get Group Details
router.get('/groups/:id', async (req, res) => {
  try {
    const result = await db.query('SELECT * FROM groups WHERE id = $1', [req.params.id]);
    if (result.rows.length === 0) return res.status(404).json({ error: 'Group not found' });
    res.json(result.rows[0]);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 2. Get Members for a Group
router.get('/groups/:id/members', async (req, res) => {
  try {
    const result = await db.query(
      `SELECT m.*, i.phone, i.display_name
       FROM members m
       JOIN identities i ON m.identity_id = i.id
       WHERE m.group_id = $1
       ORDER BY m.alias ASC`,
      [req.params.id]
    );
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 3. Get Active Cycle & Payments
router.get('/groups/:id/cycles/active', async (req, res) => {
  try {
    const cycleRes = await db.query(
      'SELECT * FROM cycles WHERE group_id = $1 AND state = \'open\' ORDER BY number DESC LIMIT 1',
      [req.params.id]
    );
    if (cycleRes.rows.length === 0) return res.json(null);

    const cycle = cycleRes.rows[0];
    const paymentsRes = await db.query(
      `SELECT p.*, m.alias as member_name
       FROM payments p
       JOIN members m ON p.member_id = m.id
       WHERE p.cycle_id = $1
       ORDER BY p.confirmed_at DESC`,
      [cycle.id]
    );

    const claimsRes = await db.query(
      `SELECT c.*, m.alias as member_name, i.phone as member_phone
       FROM claims c
       JOIN members m ON c.member_id = m.id
       JOIN identities i ON m.identity_id = i.id
       WHERE c.cycle_id = $1 AND c.state = 'pending'`,
      [cycle.id]
    );

    res.json({
      cycle,
      payments: paymentsRes.rows,
      claims: claimsRes.rows,
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 4. Record Confirmed Payment (with SHA-256 Hash Chain)
router.post('/payments/confirm', async (req, res) => {
  const { cycleId, memberId, amountPaid, method, confirmedBy, idempotencyKey, source } = req.body;

  try {
    // 1. Fetch the last payment hash in the ledger
    const lastPaymentRes = await db.query(
      'SELECT current_hash FROM payments ORDER BY confirmed_at DESC LIMIT 1'
    );
    const prevHash = lastPaymentRes.rows[0]?.current_hash || '0000000000000000000000000000000000000000000000000000000000000000';

    // 2. Compute the current SHA-256 block hash
    const paymentId = require('crypto').randomUUID();
    const finalIdempotency = idempotencyKey || `idemp-${Date.now()}`;
    const currentHash = generatePaymentHash({
      id: paymentId,
      cycleId,
      memberId,
      amountPaid,
      idempotencyKey: finalIdempotency,
      prevHash,
    });

    // 3. Insert Payment
    const paymentRes = await db.query(
      `INSERT INTO payments (
         id, cycle_id, member_id, amount_paid, method, status, confirmed_by,
         idempotency_key, source, prev_hash, current_hash
       )
       VALUES ($1, $2, $3, $4, $5, 'confirmed', $6, $7, $8, $9, $10)
       RETURNING *`,
      [
        paymentId,
        cycleId,
        memberId,
        amountPaid,
        method || 'CASH',
        confirmedBy,
        finalIdempotency,
        source || 'app',
        prevHash,
        currentHash,
      ]
    );

    // 4. Update any pending claim from this member for this cycle
    await db.query(
      `UPDATE claims 
       SET state = 'confirmed', resolved_by = $1, resolved_at = CURRENT_TIMESTAMP
       WHERE cycle_id = $2 AND member_id = $3 AND state = 'pending'`,
      [confirmedBy, cycleId, memberId]
    );

    res.json(paymentRes.rows[0]);
  } catch (err) {
    console.error('[Payment Error]', err);
    res.status(500).json({ error: err.message });
  }
});

// 5. Verify Cryptographic Ledger Chain Integrity
router.get('/groups/:id/verify-chain', async (req, res) => {
  try {
    const paymentsRes = await db.query(
      `SELECT p.* FROM payments p
       JOIN cycles c ON p.cycle_id = c.id
       WHERE c.group_id = $1
       ORDER BY p.confirmed_at ASC`,
      [req.params.id]
    );

    const report = verifyLedgerChain(paymentsRes.rows);
    res.json(report);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 6. Paystack Subscription Init
router.post('/subscriptions/momo-init', async (req, res) => {
  const { phone, network, groupId, email } = req.body;
  try {
    const result = await initializeMoMoSubscription({
      phone,
      network,
      groupId,
      email,
      amount: 4000, // GHS 40.00
    });
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 7. CSV Export
router.get('/cycles/:id/export.csv', async (req, res) => {
  try {
    const csv = await generateCycleCsv(req.params.id);
    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', `attachment; filename="susu-cycle-${req.params.id}.csv"`);
    res.send(csv);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 8. Annual Summary
router.get('/groups/:id/summary/:year', async (req, res) => {
  try {
    const summary = await generateAnnualLedgerSummary(req.params.id, req.params.year);
    res.json(summary);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
