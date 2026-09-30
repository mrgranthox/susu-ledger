const db = require('../config/database');
const { cloudId, phone } = require('./syncService');

async function registerPairing(code, groupId, verifiedPhone) {
  if (typeof code !== 'string' || !/^(?:[A-Z0-9]{6,10}|[A-Z0-9]{3}-[A-Z0-9]{3})$/.test(code)) throw new Error('Invalid pairing code');
  const id = cloudId(groupId);
  const owner = await db.query('SELECT g.id FROM groups g JOIN identities i ON i.id=g.treasurer_id WHERE g.id=$1 AND i.phone=$2', [id,phone(verifiedPhone)]);
  if (!owner.rows.length) throw Object.assign(new Error('Sync your group before pairing'), { status: 403 });
  await db.query(
    "INSERT INTO bot_pairings(code,phone,group_id,status) VALUES($1,$2,$3,'PENDING_WHATSAPP_CONFIRMATION')",
    [code,phone(verifiedPhone),id]
  );
}

async function consumePairing(code, senderPhone) {
  const result = await db.query(
    `UPDATE bot_pairings SET status='PAIRED',paired_at=clock_timestamp()
     WHERE code=$1 AND phone=$2 AND expires_at>clock_timestamp() AND status='PENDING_WHATSAPP_CONFIRMATION'
     RETURNING group_id,phone,paired_at`, [code,phone(senderPhone)]
  );
  return result.rows[0];
}

module.exports = { registerPairing, consumePairing };
