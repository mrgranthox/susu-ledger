const express = require('express');
const router = express.Router();
const db = require('../config/database');
const { sendWhatsAppTemplate, sendWhatsAppTextMessage } = require('../services/whatsappService');
const { cronAuth } = require('../middleware/cronAuth');
const { dispatchWeekNotification } = require('../services/weekNotificationService');

router.use(cronAuth);

async function getEligibleCycles(req) {
  if (req.auth?.phone_number) {
    return db.query(
      `SELECT c.id, c.number, c.due_date, g.name as group_name, c.amount_due AS amount, g.id as group_id
       FROM cycles c
       JOIN groups g ON c.group_id = g.id
       JOIN identities owner ON owner.id = g.treasurer_id
       WHERE c.state = 'open' AND owner.phone = $1`,
      [req.auth.phone_number]
    );
  }
  return db.query(
    `SELECT DISTINCT c.id, c.number, c.due_date, g.name as group_name, c.amount_due AS amount, g.id as group_id
     FROM cycles c
     JOIN groups g ON c.group_id = g.id
     JOIN bot_pairings bp ON bp.group_id = g.id AND bp.status = 'PAIRED'
     WHERE c.state = 'open'`
  );
}

// 1. Friday Morning 08:00 GMT Collection Reminder
router.get('/reminders/weekly', async (req, res) => {
  console.log('[Cron] Executing Friday Collection Reminder...');
  try {
    const cyclesRes = await getEligibleCycles(req);

    // Drain pending week notifications for all eligible groups
    const groupIds = [...new Set(cyclesRes.rows.map(r => r.group_id))];
    for (const gid of groupIds) {
      try {
        for (let i = 0; i < 5; i++) {
          await dispatchWeekNotification(gid);
        }
      } catch (wnErr) {
        console.warn(`[Cron WeekNotice Warn] Group ${gid}:`, wnErr.message);
      }
    }

    let sentCount = 0;
    let failedCount = 0;

    for (const cycle of cyclesRes.rows) {
      const pendingMembersRes = await db.query(
        `SELECT m.id, m.alias, i.phone, i.id as identity_id
         FROM members m
         JOIN identities i ON m.identity_id = i.id
         WHERE m.group_id = $1 AND m.state = 'active' AND i.dpc_consent_granted=TRUE
           AND COALESCE((SELECT SUM(amount_paid) FROM payments WHERE member_id=m.id AND cycle_id=$2 AND status='confirmed'),0) < $3`,
        [cycle.group_id, cycle.id, cycle.amount]
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

    res.status(failedCount && !sentCount ? 502 : 200).json({
      status: failedCount && !sentCount ? 'failed' : failedCount ? 'partial_failure' : 'success',
      sentCount,
      failedCount
    });
  } catch (err) {
    console.error('[Cron Error]', err);
    res.status(500).json({ error: err.message });
  }
});

// 2. Targeted Unpaid Nudges (Supports GET & POST)
async function handleUnpaidNudges(req, res) {
  console.log('[Cron] Executing Targeted Unpaid Nudges...');
  try {
    const cyclesRes = await getEligibleCycles(req);
    let sentCount = 0;
    let failedCount = 0;

    for (const cycle of cyclesRes.rows) {
      const pendingMembersRes = await db.query(
        `SELECT m.id, m.alias, i.phone, i.id as identity_id,
           EXISTS(SELECT 1 FROM message_log l WHERE l.identity_id=i.id AND l.direction='IN' AND l.created_at>clock_timestamp()-interval '23 hours') AS can_reply
         FROM members m
         JOIN identities i ON m.identity_id = i.id
         WHERE m.group_id = $1 AND m.state = 'active' AND i.dpc_consent_granted=TRUE
           AND COALESCE((SELECT SUM(amount_paid) FROM payments WHERE member_id=m.id AND cycle_id=$2 AND status='confirmed'),0) < $3`,
        [cycle.group_id, cycle.id, cycle.amount]
      );

      for (const member of pendingMembersRes.rows) {
        try {
          if (member.can_reply) {
            const nudgeText = `Hi ${member.alias}! 👋 Just a friendly reminder: your contribution of GHS ${Number(cycle.amount).toFixed(2)} for *${cycle.group_name}* (Week ${cycle.number}) is due.\n\nReply *PAID* after sending your contribution, or reply *DUE* for details.`;
            await sendWhatsAppTextMessage(member.phone, nudgeText, member.identity_id);
          } else {
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
          }
          sentCount++;
        } catch (mErr) {
          failedCount++;
          console.error(`[Nudge Fail -> ${member.alias}]`, mErr.message);
        }
      }
    }

    res.status(failedCount && !sentCount ? 502 : 200).json({
      status: failedCount && !sentCount ? 'failed' : failedCount ? 'partial_failure' : 'success',
      sentCount,
      failedCount
    });
  } catch (err) {
    console.error('[Nudge Error]', err);
    res.status(500).json({ error: err.message });
  }
}

router.get('/nudges/unpaid', handleUnpaidNudges);
router.post('/nudges/unpaid', handleUnpaidNudges);

// 3. Sunday Evening 18:00 GMT Group Summary Digest
router.get('/summaries/weekly', async (req, res) => {
  console.log('[Cron] Executing Sunday Weekly Digest...');
  try {
    const cyclesRes = await getEligibleCycles(req);

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
        [cycle.id, cycle.group_id, cycle.amount]
      );

      const stats = statsRes.rows[0];
      const targetPercent = stats.total_members > 0 
        ? Math.round((stats.paid_members / stats.total_members) * 100) 
        : 0;

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

    res.status(failedCount && !digestCount ? 502 : 200).json({
      status: failedCount && !digestCount ? 'failed' : failedCount ? 'partial_failure' : 'success',
      digestCount,
      failedCount
    });
  } catch (err) {
    console.error('[Cron Error]', err);
    res.status(500).json({ error: err.message });
  }
});

// 4. Daily Maintenance - Prune Webhook Events
router.get('/maintenance/prune-events', async (req, res) => {
  try {
    const result = await db.query(
      "DELETE FROM webhook_events WHERE processed_at < NOW() - INTERVAL '7 days'"
    );
    res.json({ status: 'ok', deleted: result.rowCount });
  } catch (err) {
    console.error('[Maintenance Error]', err);
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
