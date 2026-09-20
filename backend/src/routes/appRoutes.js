const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { generatePaymentHash, verifyLedgerChain } = require('../services/cryptoEngine');
const { initializeMoMoSubscription } = require('../services/paystackService');
const { generateCycleCsv, generateAnnualLedgerSummary } = require('../services/exportService');
const { sendWhatsAppTextMessage } = require('../services/whatsappService');

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

// 2b. Add a Member to a Group (And trigger WhatsApp Welcome Bot Notification)
router.post('/groups/:id/members', async (req, res) => {
  const groupId = req.params.id;
  const { name, phone } = req.body;

  if (!phone || !name) {
    return res.status(400).json({ error: 'Name and phone number are required' });
  }

  try {
    // Standardize phone format
    const cleanPhone = phone.replace(/[^0-9]/g, '');
    const formattedPhone = cleanPhone.startsWith('233') ? `+${cleanPhone}` : `+233${cleanPhone.replace(/^0/, '')}`;

    // 1. Resolve or create identity
    let identityRes = await db.query('SELECT * FROM identities WHERE phone = $1 OR phone = $2', [phone, formattedPhone]);
    let identity = identityRes.rows[0];

    if (!identity) {
      const newIdent = await db.query(
        'INSERT INTO identities (phone, display_name) VALUES ($1, $2) RETURNING *',
        [formattedPhone, name]
      );
      identity = newIdent.rows[0];
    }

    // 2. Get group details
    const groupRes = await db.query('SELECT * FROM groups WHERE id = $1', [groupId]);
    const group = groupRes.rows[0] || { name: 'Susu Group', amount: '50.00', currency: 'GHS', schedule: 'weekly' };

    // 3. Get treasurer name
    let treasurerName = 'Your Group Treasurer';
    if (group.treasurer_id) {
      const tRes = await db.query('SELECT display_name FROM identities WHERE id = $1', [group.treasurer_id]);
      if (tRes.rows[0]) treasurerName = tRes.rows[0].display_name;
    }

    // 4. Insert member record
    const memberRes = await db.query(
      `INSERT INTO members (group_id, identity_id, alias, state, joined_cycle) 
       VALUES ($1, $2, $3, 'active', 1) 
       ON CONFLICT (group_id, identity_id) DO UPDATE SET alias = EXCLUDED.alias, state = 'active'
       RETURNING *`,
      [groupId, identity.id, name]
    );

    const newMember = memberRes.rows[0];

    // 5. SEND AUTOMATED WHATSAPP WELCOME NOTIFICATION TO THE NEW MEMBER
    const welcomeText = 
      `Hello ${name}! 👋\n\n` +
      `Welcome to *${group.name}* on *SusuLedger*!\n\n` +
      `Your group treasurer (${treasurerName}) has registered you in the group.\n\n` +
      `• Contribution Amount: *${group.currency || 'GHS'} ${group.amount}* (${group.schedule || 'weekly'})\n\n` +
      `I am your automated SusuLedger WhatsApp Bot assistant. You can reply anytime with:\n` +
      `• *PAID* - To submit a payment contribution claim\n` +
      `• *BALANCE* - To check your payment history\n` +
      `• *HELP* - To view all commands\n\n` +
      `Welcome aboard! 💚`;

    try {
      await sendWhatsAppTextMessage(formattedPhone, welcomeText, identity.id);
      console.log(`[Member Welcome WhatsApp Sent] To: ${formattedPhone} for group: ${group.name}`);
    } catch (wsErr) {
      console.error('[WhatsApp Welcome Error]', wsErr.message);
    }

    res.status(201).json({
      member: newMember,
      identity,
      message: 'Member added and WhatsApp welcome notification dispatched!'
    });

  } catch (err) {
    console.error('[Add Member Error]', err);
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
      payments: paymentsRes.rows,      claims: claimsRes.rows,
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
      cycleId,      memberId,
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

    // 5. Send Payment Confirmation Receipt via WhatsApp to Member
    try {
      const memberInfo = await db.query(
        `SELECT m.alias, i.phone FROM members m JOIN identities i ON m.identity_id = i.id WHERE m.id = $1`,
        [memberId]
      );
      if (memberInfo.rows[0]) {
        const { alias, phone } = memberInfo.rows[0];
        const receiptText = 
          `SusuLedger Official Receipt 🧾\n\n` +
          `Dear ${alias},\n` +
          `Your payment of *GHS ${amountPaid}* has been confirmed!\n\n` +
          `• Method: ${method || 'CASH'}\n` +
          `• Transaction ID: ${paymentId.slice(0, 8)}\n` +
          `• SHA-256 Ledger Hash: ${currentHash.slice(0, 16)}...\n\n` +
          `Thank you for contributing on time! 🟢`;
        await sendWhatsAppTextMessage(phone, receiptText);
      }
    } catch (rcptErr) {
      console.error('[Receipt Error]', rcptErr.message);
    }

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
