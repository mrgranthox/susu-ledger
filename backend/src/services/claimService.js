const db = require('../config/database');
const { cloudId, phone } = require('./syncService');

async function rejectClaim(groupId, claimId, verifiedPhone, reason) {
  if (typeof reason !== 'string' || !reason.trim() || reason.length > 500) throw new Error('A rejection reason is required');
  const client = await db.pool.connect();
  try {
    await client.query('BEGIN');
    const claim = (await client.query(`SELECT cl.state,g.treasurer_id FROM claims cl
      JOIN cycles c ON c.id=cl.cycle_id JOIN groups g ON g.id=c.group_id JOIN identities i ON i.id=g.treasurer_id
      WHERE cl.id=$1 AND g.id=$2 AND i.phone=$3 FOR UPDATE OF cl`,[cloudId(claimId),cloudId(groupId),phone(verifiedPhone)])).rows[0];
    if (!claim) throw Object.assign(new Error('Claim not found'),{status:404});
    if (claim.state !== 'pending' && claim.state !== 'rejected') throw Object.assign(new Error('Claim is already resolved'),{status:409});
    if (claim.state === 'pending') {
      await client.query("UPDATE claims SET state='rejected',resolved_by=$1,resolved_at=clock_timestamp() WHERE id=$2",[claim.treasurer_id,cloudId(claimId)]);
      await client.query("INSERT INTO audit_log(actor_id,group_id,action,payload) VALUES($1,$2,'CLAIM_REJECTED',$3)",[claim.treasurer_id,cloudId(groupId),JSON.stringify({claimId,reason})]);
    }
    await client.query('COMMIT');
    return {status:'rejected'};
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally { client.release(); }
}

module.exports = { rejectClaim };
