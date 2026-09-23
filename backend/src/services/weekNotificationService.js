const db=require('../config/database');
const whatsapp=require('./whatsappService');

async function dispatchWeekNotification(groupId) {
  const client=await db.pool.connect();
  try {
    await client.query('BEGIN');
    const row=(await client.query(`SELECT n.cycle_id,n.member_id,c.number,c.amount_due,c.due_date,g.name,i.phone,i.id AS identity_id,
      EXISTS(SELECT 1 FROM message_log l WHERE l.identity_id=i.id AND l.direction='IN' AND l.created_at>clock_timestamp()-interval '23 hours') AS can_reply
      FROM week_notifications n JOIN cycles c ON c.id=n.cycle_id JOIN groups g ON g.id=c.group_id
      JOIN members m ON m.id=n.member_id JOIN identities i ON i.id=m.identity_id
      WHERE c.group_id=$1 AND n.state='pending' AND i.dpc_consent_granted=TRUE AND m.state='active'
      AND EXISTS(SELECT 1 FROM bot_pairings b WHERE b.group_id=c.group_id AND b.status='PAIRED')
      ORDER BY n.attempts,c.number FOR UPDATE OF n SKIP LOCKED LIMIT 1`,[groupId])).rows[0];
    if(row) {
      let accepted=false;
      try {
        if(row.can_reply) await whatsapp.sendWhatsAppTextMessage(row.phone,
          `${row.name}: Week ${row.number} is open. Contribution: GHS ${Number(row.amount_due).toFixed(2)}. Due: ${String(row.due_date).slice(0,10)}. Reply PAID for this week. To settle an earlier week, reply PAID WEEK followed by its number.`,row.identity_id);
        else await whatsapp.sendWhatsAppTemplate(row.phone,'susu_week_opened','en',
          [row.name,String(row.number),Number(row.amount_due).toFixed(2),String(row.due_date).slice(0,10)].map(text=>({type:'text',text})),row.identity_id);
        accepted=true;
      } catch (_) { /* Preserve the queue on provider rejection. */ }
      await client.query("UPDATE week_notifications SET state=$3::varchar,attempts=attempts+1,accepted_at=CASE WHEN $3::varchar='sent' THEN clock_timestamp() ELSE NULL END WHERE cycle_id=$1 AND member_id=$2",[row.cycle_id,row.member_id,accepted?'sent':'pending']);
    }
    await client.query('COMMIT');
  } catch(error) { await client.query('ROLLBACK'); throw error; }
  finally {client.release();}
}
module.exports={dispatchWeekNotification};
