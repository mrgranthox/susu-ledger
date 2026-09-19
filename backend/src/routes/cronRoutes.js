const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { sendWhatsAppTemplate } = require('../services/whatsappService');

// 1. Friday Morning 08:00 GMT Collection Reminder
router.get('/reminders/weekly', async (req, res) => {
  console.log('[Cron] Executing Friday Collection Reminder...');
  try {
    // Find all active open cycles
    const cyclesRes = await db.query(
      `SELECT c.id, c.number, c.due_date, g.name as group_name, g.amount, g.id as group_id
       FROM cycles c
       JOIN groups g ON c.group_id = g.id
       WHERE c.state = 'open'`
    );

    let sentCount = 0;

    for (const cycle of cyclesRes.rows) {
      // Find members who haven't paid yet for this cycle
      const pendingMembersRes = await db.query(
        `SELECT m.id, m.alias, i.phone, i.id as identity_id
         FROM members m
         JOIN identities i ON m.identity_id = i.id
         WHERE m.group_id = $1 AND m.state = 'active'
           AND m.id NOT IN (
             SELECT member_id FROM payments WHERE cycle_id = $2 AND status = 'confirmed'
           )`,
        [cycle.group_id, cycle.id]
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
          console.error(`[Reminder Fail -> ${member.alias}]`, mErr.message);
        }
      }
    }

    res.json({ status: 'success', sentCount });
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
      `SELECT c.id, c.number, g.name as group_name, g.amount, g.id as group_id
       FROM cycles c
       JOIN groups g ON c.group_id = g.id
       WHERE c.state = 'open'`
    );

    let digestCount = 0;

    for (const cycle of cyclesRes.rows) {
      const statsRes = await db.query(
        `SELECT 
           COUNT(DISTINCT m.id) as total_members,
           COUNT(DISTINCT p.member_id) as paid_members,
           COALESCE(SUM(p.amount_paid), 0) as total_collected
         FROM members m
         LEFT JOIN payments p ON p.member_id = m.id AND p.cycle_id = $1 AND p.status = 'confirmed'
         WHERE m.group_id = $2 AND m.state = 'active'`,
        [cycle.id, cycle.group_id]
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
         WHERE m.group_id = $1 AND m.state = 'active'`,
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
          console.error(`[Digest Fail -> ${member.alias}]`, mErr.message);
        }
      }
    }

    res.json({ status: 'success', digestCount });
  } catch (err) {
    console.error('[Cron Error]', err);
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
