const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { sendWhatsAppTemplate } = require('../services/whatsappService');
const { requirePhoneAuth } = require('../middleware/auth');
router.use(requirePhoneAuth);

// 1. Friday Morning 08:00 GMT Collection Reminder
router.get('/reminders/weekly', async (req, res) => {
  console.log('[Cron] Executing Friday Collection Reminder...');
  try {
    // Find all active open cycles
    const cyclesRes = await db.query(
      `SELECT c.id, c.number, c.due_date, g.name as group_name, c.amount_due AS amount, g.id as group_id
       FROM cycles c
       JOIN groups g ON c.group_id = g.id
       JOIN identities owner ON owner.id=g.treasurer_id
       WHERE c.state = 'open' AND owner.phone=$1`, [req.auth.phone_number]
    );

    let sentCount = 0;
    let failedCount = 0;

    for (const cycle of cyclesRes.rows) {
      // Find members who haven't paid yet for this cycle
      const pendingMembersRes = await db.query(
        `SELECT m.id, m.alias, i.phone, i.id as identity_id
         FROM members m
         JOIN identities i ON m.identity_id = i.id
         WHERE m.group_id = $1 AND m.state = 'active' AND i.dpc_consent_granted=TRUE
           AND COALESCE((SELECT SUM(amount_paid) FROM payments WHERE member_id=m.id AND cycle_id=$2 AND status='confirmed'),0) < $3`,
        [cycle.group_id, cycle.id,cycle.amount]
      );

      for (const member of pendingMembersRes.rows) {
        try {
          await sendWhatsAppTemplate(
            member.phone,
            'susu_friday_reminder',
            'en',
            [
              { type: 'text', text: member.alias },
              { type: 'text', text: Number(cycle.amount).toFixed(2) },
              { type: 'text', text: cycle.group_name },
              { type: 'text', text: cycle.number.toString() },
            ],
            member.identity_id
          );
          sentCount++;
        } catch (mErr) {
          failedCount++;
          console.error(`[Reminder Fail -> ${member.alias}]`, mErr.message);
        }
      }
    }

    res.status(failedCount ? 502 : 200).json({ status: failedCount ? 'partial_failure' : 'success', sentCount, failedCount });
  } catch (err) {
    console.error('[Cron Error]', err);
    res.status(500).json({ error: err.message });
  }
});

// 2. Sunday Evening 18:00 GMT Group Summary Digest
router.get('/summaries/weekly', async (req, res) => {
  console.log('[Cron] Executing Sunday Weekly Digest...');
  try {
    const cyclesRes = await db.query(
      `SELECT c.id, c.number, g.name as group_name, c.amount_due AS amount, g.id as group_id
       FROM cycles c
       JOIN groups g ON c.group_id = g.id
       JOIN identities owner ON owner.id=g.treasurer_id
       WHERE c.state = 'open' AND owner.phone=$1`, [req.auth.phone_number]
    );

    let digestCount = 0;
    let failedCount = 0;

    for (const cycle of cyclesRes.rows) {
      const statsRes = await db.query(
        `SELECT 
           COUNT(*) as total_members,
           COUNT(*) FILTER (WHERE COALESCE(p.total,0) >= $3) as paid_members,
           COALESCE(SUM(p.total), 0) as total_collected
         FROM members m
         LEFT JOIN LATERAL (SELECT SUM(amount_paid) AS total FROM payments WHERE member_id=m.id AND cycle_id=$1 AND status='confirmed') p ON TRUE
         WHERE m.group_id = $2 AND m.state = 'active'`,
        [cycle.id, cycle.group_id,cycle.amount]
      );

      const stats = statsRes.rows[0];
      const targetPercent = stats.total_members > 0 
        ? Math.round((stats.paid_members / stats.total_members) * 100) 
        : 0;

      // Broadcast summary to active members
      const membersRes = await db.query(
        `SELECT i.phone, i.id as identity_id, m.alias
         FROM members m
         JOIN identities i ON m.identity_id = i.id
         WHERE m.group_id = $1 AND m.state = 'active' AND i.dpc_consent_granted=TRUE`,
        [cycle.group_id]
      );

      for (const member of membersRes.rows) {
        try {
          await sendWhatsAppTemplate(
            member.phone,
            'susu_sunday_summary',
            'en',
            [
              { type: 'text', text: cycle.group_name },
              { type: 'text', text: cycle.number.toString() },
              { type: 'text', text: Number(stats.total_collected).toFixed(2) },
              { type: 'text', text: `${stats.paid_members}/${stats.total_members}` },
              { type: 'text', text: targetPercent.toString() },
            ],
            member.identity_id
          );
          digestCount++;
        } catch (mErr) {
          failedCount++;
          console.error(`[Digest Fail -> ${member.alias}]`, mErr.message);
        }
      }
    }

    res.status(failedCount ? 502 : 200).json({ status: failedCount ? 'partial_failure' : 'success', digestCount, failedCount });
  } catch (err) {
    console.error('[Cron Error]', err);
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
