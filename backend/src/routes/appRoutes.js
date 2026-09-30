const express = require('express');
const router = express.Router();
const db = require('../config/database');
const cacheService = require('../config/redis');
const { requirePhoneAuth } = require('../middleware/auth');
const { cloudId, phone: normalizePhone } = require('../services/syncService');
const { registerPairing } = require('../services/pairingService');
const { generatePaymentHash, verifyLedgerChain } = require('../services/cryptoEngine');
const { initializeMoMoSubscription } = require('../services/paystackService');
const { generateCycleCsv, generateAnnualLedgerSummary } = require('../services/exportService');
const { sendWhatsAppTextMessage, sendWhatsAppInteractiveMessage, sendWhatsAppTemplate } = require('../services/whatsappService');

// 0. Cloud Run & System Diagnostics Health Status Check
router.get('/status', async (req, res) => {
  let dbStatus = 'healthy';
  let dbLatency = 0;
  try {
    const t0 = Date.now();
    await db.query('SELECT 1 FROM bot_pairings LIMIT 0');
    dbLatency = Date.now() - t0;
  } catch (dbErr) {
    dbStatus = 'unavailable';
  }

  const redisPing = await cacheService.ping();
  const metaConfigured = Boolean(process.env.META_WHATSAPP_PHONE_NUMBER_ID || process.env.WHATSAPP_PHONE_ID);
  const tokenConfigured = Boolean(process.env.META_WHATSAPP_ACCESS_TOKEN || process.env.WHATSAPP_TOKEN);

  res.status(dbStatus === 'healthy' ? 200 : 503).json({
    status: dbStatus === 'healthy' ? 'ONLINE' : 'DEGRADED',
    service: 'SusuLedger Cloud Run Core Engine',
    timestamp: new Date().toISOString(),
    database: {
      engine: 'Cloud SQL PostgreSQL',
      status: dbStatus,
      latencyMs: dbLatency
    },
    cache: {
      engine: cacheService.isAvailable() ? 'Cloud Memorystore Redis' : 'In-Memory Cache Fallback',
      ping: redisPing
    },
    whatsappBot: {
      provider: 'Meta WhatsApp Cloud API v23.0',
      phoneIdConfigured: metaConfigured,
      tokenConfigured: tokenConfigured,
      signatureConfigured: Boolean(process.env.META_APP_SECRET),
      webhookPath: '/webhooks/whatsapp',
      autoPairingSupported: true
    },
    cryptoEngine: {
      algorithm: 'SHA-256 Hash Chaining',
      doubleEntryBalanced: null
    }
  });
});

router.use(requirePhoneAuth);

router.get('/account/backup', async (req, res, next) => {
  try {
    res.setHeader('Cache-Control', 'no-store');
    res.json(await require('../services/restoreService').getAccountBackup(req.auth.phone_number));
  } catch (error) { next(error); }
});

router.post('/fcm-token', async (req, res, next) => {
  try {
    const { token } = req.body;
    if (!token) return res.status(400).json({ error: 'FCM token required' });
    await db.query(
      'UPDATE identities SET fcm_token = $1 WHERE phone = $2',
      [token, normalizePhone(req.auth.phone_number)]
    );
    res.json({ success: true });
  } catch (error) { next(error); }
});

router.use(async (req, res, next) => {
  try {
    let groupId = req.path.match(/^\/groups\/([^/]+)/)?.[1] || req.body.groupId;
    const cycleId = req.path.match(/^\/cycles\/([^/]+)/)?.[1];
    if (cycleId) {
      groupId = (await db.query('SELECT group_id FROM cycles WHERE id=$1', [cloudId(cycleId)])).rows[0]?.group_id;
      if (!groupId) return res.status(404).json({ error: 'Cycle not found' });
    }
    if (groupId) {
      const owner = await db.query('SELECT g.id FROM groups g JOIN identities i ON i.id=g.treasurer_id WHERE g.id=$1 AND i.phone=$2', [cloudId(groupId),normalizePhone(req.auth.phone_number)]);
      if (!owner.rows.length) return res.status(403).json({ error: 'Group access denied' });
    }
    next();
  } catch (error) { next(error); }
});

router.get('/pair-bot/:code', async (req, res, next) => {
  try {
    const result = await db.query('SELECT status,expires_at FROM bot_pairings WHERE code=$1 AND phone=$2', [req.params.code,normalizePhone(req.auth.phone_number)]);
    if (!result.rows.length) return res.status(404).json({ error: 'Pairing not found' });
    const pair = result.rows[0];
    res.json({ status: pair.status === 'PAIRED' ? 'PAIRED' : new Date(pair.expires_at) <= new Date() ? 'EXPIRED' : pair.status });
  } catch (error) { next(error); }
});

router.get('/groups/:id/bot-connection', async (req,res,next) => {
  try {
    const gid = cloudId(req.params.id);
    const result = await db.query(
      `SELECT 1 FROM bot_pairings bp
       WHERE (bp.group_id = $1 OR bp.group_id IN (
         SELECT g.id FROM groups g WHERE g.treasurer_id = (SELECT treasurer_id FROM groups WHERE id = $1)
       )) AND bp.status = 'PAIRED'
       LIMIT 1`,
      [gid]
    );
    res.json({status:result.rowCount ? 'CONNECTED' : 'DISCONNECTED'});
  } catch(error) { next(error); }
});

// 0b. Register/Refresh Dynamic Bot Pairing Code
router.post('/groups/:id/claims/:claimId/reject', async (req,res) => {
  try {
    res.json(await require('../services/claimService').rejectClaim(req.params.id,req.params.claimId,req.auth.phone_number,req.body.reason));
  } catch (error) {
    res.status(error.status || 400).json({error:error.message});
  }
});

router.post('/pair-bot', async (req, res) => {
  const { code, phone, groupId } = req.body;
  if (!code) {
    return res.status(400).json({ error: 'Pairing code is required' });
  }

  const cleanCode = code.trim().toUpperCase();
  const pairData = {
    code: cleanCode,
    phone: phone || '+233000000000',
    groupId: groupId || null,
    createdAt: new Date().toISOString(),
    status: 'PENDING_WHATSAPP_CONFIRMATION'
  };

  try {
    await registerPairing(cleanCode, groupId, req.auth.phone_number);
  } catch (error) {
    return res.status(error.status || 400).json({ error: error.code === '23505' ? 'Generate a new pairing code' : error.message });
  }

  res.json({
    success: true,
    pairingCode: cleanCode,
    expiresInSeconds: 900,
    message: 'Pairing code registered with Cloud Run engine.'
  });
});

// 0b-2. Broadcast Welcome WhatsApp Messages to All Group Members
router.post('/groups/:id/broadcast-welcome', async (req, res, next) => {
  try {
    const gid = cloudId(req.params.id);
    const treasurer = (await db.query(
      'SELECT g.id FROM groups g JOIN identities i ON i.id = g.treasurer_id WHERE g.id = $1 AND i.phone = $2',
      [gid, normalizePhone(req.auth.phone_number)]
    )).rows[0];
    if (!treasurer) return res.status(403).json({ error: 'Only the verified treasurer can broadcast welcome messages.' });

    const { broadcastWelcomeToGroupMembers } = require('../services/stateMachine');
    const sentCount = await broadcastWelcomeToGroupMembers(gid);
    res.json({ success: true, count: sentCount, message: `Welcome messages delivered to ${sentCount} members.` });
  } catch (error) {
    next(error);
  }
});

// 0c. Send Live WhatsApp Message to Member Phone
router.post('/whatsapp/send-message', async (req, res) => {
  const { phone, message, identityId } = req.body;
  if (!phone || !message) {
    return res.status(400).json({ error: 'Phone and message body are required' });
  }

  try {
    const recipient = await db.query(`SELECT recipient.id FROM identities recipient
      JOIN members m ON m.identity_id=recipient.id JOIN groups g ON g.id=m.group_id
      JOIN identities owner ON owner.id=g.treasurer_id WHERE recipient.phone=$1 AND owner.phone=$2 LIMIT 1`,
      [normalizePhone(phone),normalizePhone(req.auth.phone_number)]);
    if (!recipient.rowCount) return res.status(403).json({ error: 'Recipient is not in your group' });
    const result = await sendWhatsAppTextMessage(phone, message, recipient.rows[0].id);
    res.json({
      success: true,
      phone,
      result,
      message: 'WhatsApp message dispatched to member.'
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

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
      `SELECT
         c.id,
         c.cycle_id,
         c.member_id,
         c.claimed_amount::float AS claimed_amount,
         c.evidence_momo_id,
         c.state,
         c.created_at,
         c.resolved_by,
         c.resolved_at,
         m.alias as member_name,
         i.phone as member_phone
       FROM claims c 
       JOIN members m ON c.member_id = m.id 
       JOIN identities i ON m.identity_id = i.id 
       WHERE m.group_id = $1`,
      [req.params.id]
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
  return res.status(409).json({ error: 'Use authenticated group sync to record balanced payments' });
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
