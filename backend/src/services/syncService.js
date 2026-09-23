const crypto = require('node:crypto');
const db = require('../config/database');
const { generatePaymentHash, GENESIS_HASH } = require('./cryptoEngine');

function cloudId(value) {
  if (typeof value !== 'string' || !value || value.length > 200) throw new Error('Invalid record ID');
  if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) return value.toLowerCase();
  // Matches Java UUID.nameUUIDFromBytes for legacy offline record IDs.
  const bytes = crypto.createHash('md5').update(value).digest();
  bytes[6] = (bytes[6] & 15) | 48;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = bytes.toString('hex');
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}

function phone(value) {
  const digits = String(value || '').replace(/\D/g, '');
  const normalized = digits.length === 9 ? `233${digits}` : digits.startsWith('0') ? `233${digits.slice(1)}` : digits;
  if (!/^233[235]\d{8}$/.test(normalized)) throw new Error('A valid Ghana phone number is required');
  return `+${normalized}`;
}

function money(value) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0 || value > 9999999999.99 || Math.abs(value * 100 - Math.round(value * 100)) > 0.0001) {
    throw new Error('Amount must be positive with at most two decimal places');
  }
  return value;
}

async function syncGroup(payload, authenticatedPhone) {
  const { group, treasurer, members = [], cycles = [], payments = [], receiptPaymentIds = [] } = payload;
  if (!Array.isArray(receiptPaymentIds) || receiptPaymentIds.length > 10000 || receiptPaymentIds.some(id=>!payments.some(p=>p.id===id))) throw new Error('Invalid receipt request');
  if (!group || !treasurer || phone(treasurer.phone) !== phone(authenticatedPhone)) {
    throw Object.assign(new Error('Group must belong to your verified phone'), { status: 403 });
  }
  if (!Array.isArray(members) || !Array.isArray(cycles) || !Array.isArray(payments) || members.length > 1000 || cycles.length > 1000 || payments.length > 10000) throw new Error('Invalid sync batch');
  if (typeof group.name !== 'string' || !group.name.trim() || group.name.length > 100) throw new Error('Invalid group name');
  money(group.amount);
  const groupId = cloudId(group.id);
  const client = await db.pool.connect();
  try {
    await client.query('BEGIN');
    await client.query('SELECT pg_advisory_xact_lock(hashtext($1))', [groupId]);
    const identity = (await client.query(
      `INSERT INTO identities(id, phone, display_name) VALUES ($1,$2,$3)
       ON CONFLICT(phone) DO UPDATE SET display_name=EXCLUDED.display_name RETURNING id`,
      [cloudId(treasurer.id), phone(treasurer.phone), treasurer.displayName]
    )).rows[0];
    const existing = (await client.query('SELECT treasurer_id FROM groups WHERE id=$1', [groupId])).rows[0];
    if (existing && existing.treasurer_id !== identity.id) throw Object.assign(new Error('Group access denied'), { status: 403 });
    await client.query(
      `INSERT INTO groups(id,name,amount,currency,schedule,treasurer_id,state) VALUES($1,$2,$3,$4,$5,$6,$7)
       ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name,amount=EXCLUDED.amount,schedule=EXCLUDED.schedule,state=EXCLUDED.state`,
      [groupId,group.name,group.amount,group.currency || 'GHS',group.schedule,identity.id,group.state]
    );
    for (const member of members) {
      if (cloudId(member.groupId) !== groupId) throw new Error('Member group mismatch');
      const mid = cloudId(member.id);
      const owner = (await client.query('SELECT group_id FROM members WHERE id=$1', [mid])).rows[0];
      if (owner && owner.group_id !== groupId) throw new Error('Member access denied');
      const person = (await client.query(
        `INSERT INTO identities(id,phone,display_name) VALUES($1,$2,$3)
         ON CONFLICT(phone) DO UPDATE SET phone=EXCLUDED.phone RETURNING id`,
        [cloudId(member.identityId),phone(member.phone),member.alias]
      )).rows[0];
      await client.query(
        `INSERT INTO members(id,group_id,identity_id,alias,state,joined_cycle,exited_cycle) VALUES($1,$2,$3,$4,$5,$6,$7)
         ON CONFLICT(id) DO UPDATE SET alias=EXCLUDED.alias,state=EXCLUDED.state,exited_cycle=EXCLUDED.exited_cycle`,
        [mid,groupId,person.id,member.alias,member.state,member.joinedCycle,member.exitedCycle]
      );
    }
    for (const cycle of cycles) {
      if (cloudId(cycle.groupId) !== groupId) throw new Error('Cycle group mismatch');
      money(cycle.amountDue);
      if (!/^\d{4}-\d{2}-\d{2}$/.test(cycle.dueDate)) throw new Error('Cycle due date must be YYYY-MM-DD');
      const owner = (await client.query('SELECT group_id FROM cycles WHERE id=$1', [cloudId(cycle.id)])).rows[0];
      if (owner && owner.group_id !== groupId) throw new Error('Cycle access denied');
      await client.query(
        `INSERT INTO cycles(id,group_id,number,amount_due,due_date,state,closed_at,closed_by) VALUES($1,$2,$3,$4,$5,$6,$7,$8)
         ON CONFLICT(id) DO UPDATE SET amount_due=EXCLUDED.amount_due,due_date=EXCLUDED.due_date,state=EXCLUDED.state,closed_at=EXCLUDED.closed_at,closed_by=EXCLUDED.closed_by`,
        [cloudId(cycle.id),groupId,cycle.number,cycle.amountDue,cycle.dueDate,cycle.state,cycle.closedAt ? new Date(cycle.closedAt) : null,cycle.closedAt ? identity.id : null]
      );
      if (!owner && cycle.number > 1 && cycle.state === 'open') {
        await client.query(`INSERT INTO week_notifications(cycle_id,member_id)
          SELECT $1,m.id FROM members m JOIN identities i ON i.id=m.identity_id
          WHERE m.group_id=$2 AND m.state='active' AND i.dpc_consent_granted=TRUE
          ON CONFLICT DO NOTHING`,[cloudId(cycle.id),groupId]);
      }
    }
    const acknowledged = [];
    for (const payment of payments) {
      money(payment.amountPaid);
      if (typeof payment.idempotencyKey !== 'string' || !payment.idempotencyKey) throw new Error('Payment idempotency key required');
      const pid = cloudId(payment.id), cid = cloudId(payment.cycleId), mid = cloudId(payment.memberId);
      const scope = (await client.query('SELECT c.id FROM cycles c JOIN members m ON m.group_id=c.group_id WHERE c.id=$1 AND m.id=$2 AND c.group_id=$3', [cid,mid,groupId])).rows[0];
      if (!scope) throw new Error('Payment group mismatch');
      const prior = (await client.query('SELECT * FROM payments WHERE idempotency_key=$1 OR id=$2', [payment.idempotencyKey,pid])).rows;
      if (prior.length) {
        if (prior.length !== 1 || prior[0].id !== pid || prior[0].idempotency_key !== payment.idempotencyKey || prior[0].cycle_id !== cid || prior[0].member_id !== mid || Number(prior[0].amount_paid) !== payment.amountPaid || prior[0].method !== payment.method) throw Object.assign(new Error('Payment retry differs from stored record'), { status: 409 });
      } else {
        const previous = (await client.query('SELECT p.current_hash FROM payments p JOIN cycles c ON c.id=p.cycle_id WHERE c.group_id=$1 ORDER BY p.confirmed_at DESC,p.id DESC LIMIT 1', [groupId])).rows[0];
        const prevHash = previous?.current_hash || GENESIS_HASH;
        const hash = generatePaymentHash({id:pid,cycleId:cid,memberId:mid,amountPaid:payment.amountPaid,idempotencyKey:payment.idempotencyKey,prevHash});
        // Strictly increasing server timestamps establish a stable per-group chain order.
        await client.query(`INSERT INTO payments(id,cycle_id,member_id,amount_paid,method,confirmed_by,idempotency_key,source,prev_hash,current_hash,confirmed_at)
          VALUES($1,$2,$3,$4,$5,$6,$7,'app',$8,$9,GREATEST(clock_timestamp(),COALESCE((SELECT MAX(confirmed_at)+interval '1 microsecond' FROM payments),clock_timestamp())))`,
          [pid,cid,mid,payment.amountPaid,payment.method,identity.id,payment.idempotencyKey,prevHash,hash]);
        for (const type of ['asset','equity']) {
          const accountId = cloudId(`${groupId}:${type}:${type === 'equity' ? mid : ''}`);
          await client.query('INSERT INTO accounts(id,group_id,name,type,member_id) VALUES($1,$2,$3,$4,$5) ON CONFLICT(id) DO NOTHING', [accountId,groupId,type === 'asset' ? 'Cash/MoMo Asset' : 'Member Equity',type,type === 'equity' ? mid : null]);
          await client.query('INSERT INTO ledger_entries(payment_id,account_id,entry_type,amount) VALUES($1,$2,$3,$4)', [pid,accountId,type === 'asset' ? 'debit' : 'credit',payment.amountPaid]);
        }
        await client.query("UPDATE claims SET state='confirmed',resolved_by=$1,resolved_at=clock_timestamp() WHERE cycle_id=$2 AND member_id=$3 AND state='pending'", [identity.id,cid,mid]);
      }
      acknowledged.push(payment.id);
      if (receiptPaymentIds.includes(payment.id)) await client.query('INSERT INTO payment_receipts(payment_id) VALUES($1) ON CONFLICT DO NOTHING',[pid]);
    }
    await client.query('COMMIT');
    return { groupId, acknowledgedPaymentIds: acknowledged };
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

module.exports = { cloudId, phone, money, syncGroup };
