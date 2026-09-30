const db = require('../config/database');
const { cloudId, phone } = require('./syncService');
const { sendWhatsAppTextMessage } = require('./whatsappService');

async function rejectClaim(groupId, claimId, verifiedPhone, reason) {
  if (typeof reason !== 'string' || !reason.trim() || reason.length > 500) throw new Error('A rejection reason is required');
  const client = await db.pool.connect();
  try {
    await client.query('BEGIN');
    const claim = (await client.query(`SELECT cl.state,g.treasurer_id,g.name as group_name,c.number as cycle_number,
      i_member.phone as member_phone,i_member.id as member_identity_id,m.alias as member_alias
      FROM claims cl
      JOIN cycles c ON c.id=cl.cycle_id JOIN groups g ON g.id=c.group_id JOIN identities i ON i.id=g.treasurer_id
      JOIN members m ON m.id=cl.member_id JOIN identities i_member ON i_member.id=m.identity_id
      WHERE cl.id=$1 AND g.id=$2 AND i.phone=$3 FOR UPDATE OF cl`,[cloudId(claimId),cloudId(groupId),phone(verifiedPhone)])).rows[0];
    if (!claim) throw Object.assign(new Error('Claim not found'),{status:404});
    if (claim.state !== 'pending' && claim.state !== 'rejected') throw Object.assign(new Error('Claim is already resolved'),{status:409});
    const wasPending = claim.state === 'pending';
    if (wasPending) {
      await client.query("UPDATE claims SET state='rejected',resolved_by=$1,resolved_at=clock_timestamp() WHERE id=$2",[claim.treasurer_id,cloudId(claimId)]);
      await client.query("INSERT INTO audit_log(actor_id,group_id,action,payload) VALUES($1,$2,'CLAIM_REJECTED',$3)",[claim.treasurer_id,cloudId(groupId),JSON.stringify({claimId,reason})]);
    }
    await client.query('COMMIT');

    if (wasPending && claim.member_phone) {
      try {
        const msg = `❌ *Claim Update — ${claim.group_name}*\n\n` +
          `Hi ${claim.member_alias || 'Member'}, your payment claim for Week ${claim.cycle_number || 1} was not accepted.\n\n` +
          `*Reason:* ${reason.trim()}\n\n` +
          `If this is an error, please contact your treasurer, or reply *PAID* with your correct MoMo reference to resubmit.`;
        await sendWhatsAppTextMessage(claim.member_phone, msg, claim.member_identity_id);
      } catch (wsErr) {
        console.warn('[Claim Rejection WhatsApp Notification Error]', wsErr.message);
      }
    }

    return {status:'rejected'};
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally { client.release(); }
}

module.exports = { rejectClaim };
