const db = require('../config/database');
const whatsapp = require('./whatsappService');

async function dispatchReceipt(paymentId) {
  const client=await db.pool.connect();
  try {
    await client.query('BEGIN');
    const row=(await client.query(`SELECT r.state,p.amount_paid,p.method,p.current_hash,c.number,g.name,
      i.phone,i.id AS identity_id,treasurer.display_name,
      EXISTS(SELECT 1 FROM message_log ml WHERE ml.identity_id=i.id AND ml.direction='IN'
        AND ml.created_at>clock_timestamp()-interval '23 hours') AS can_reply
      FROM payment_receipts r JOIN payments p ON p.id=r.payment_id
      JOIN cycles c ON c.id=p.cycle_id JOIN groups g ON g.id=c.group_id JOIN members m ON m.id=p.member_id
      JOIN identities i ON i.id=m.identity_id JOIN identities treasurer ON treasurer.id=g.treasurer_id
      WHERE r.payment_id=$1 FOR UPDATE OF r SKIP LOCKED`,[paymentId])).rows[0];
    if(!row){await client.query('COMMIT');return false;}
    if(row.state==='sent'){await client.query('COMMIT');return true;}
    try {
      if (row.can_reply) {
        await whatsapp.sendWhatsAppTextMessage(row.phone,
          `SusuLedger payment receipt\nAmount: GHS ${Number(row.amount_paid).toFixed(2)}\nGroup: ${row.name}\nWeek: ${row.number}\nMethod: ${row.method}\nConfirmed by: ${row.display_name}\nReference: ${row.current_hash.slice(0,16)}`,
          row.identity_id);
      } else await whatsapp.sendWhatsAppTemplate(row.phone,'susu_payment_receipt','en',[
        Number(row.amount_paid).toFixed(2),row.name,String(row.number),row.method,row.current_hash.slice(0,16),row.display_name
      ].map(text=>({type:'text',text})),row.identity_id);
      await client.query("UPDATE payment_receipts SET state='sent',attempts=attempts+1,accepted_at=clock_timestamp() WHERE payment_id=$1",[paymentId]);
      await client.query('COMMIT');
      return true;
    } catch (_) {
      await client.query('UPDATE payment_receipts SET attempts=attempts+1 WHERE payment_id=$1',[paymentId]);
      await client.query('COMMIT');
      return false;
    }
  } catch(error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {client.release();}
}

module.exports={dispatchReceipt};
