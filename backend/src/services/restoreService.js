const db = require('../config/database');
const { phone } = require('./syncService');

const millis = value => value == null ? null : new Date(value).getTime();

async function getAccountBackup(verifiedPhone) {
  const client = await db.pool.connect();
  try {
    // One consistent snapshot, scoped exclusively by the authenticated phone.
    await client.query('BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY');
    const groups = (await client.query(`SELECT g.* FROM groups g JOIN identities i ON i.id=g.treasurer_id
      WHERE i.phone=$1 ORDER BY g.created_at,g.id`, [phone(verifiedPhone)])).rows;
    const ids = groups.map(g => g.id);
    const members = (await client.query(`SELECT m.*,i.phone FROM members m JOIN identities i ON i.id=m.identity_id
      WHERE m.group_id=ANY($1::uuid[])`, [ids])).rows;
    const cycles = (await client.query(`SELECT *,to_char(due_date,'YYYY-MM-DD') AS due_day
      FROM cycles WHERE group_id=ANY($1::uuid[]) ORDER BY number,id`, [ids])).rows;
    const payments = (await client.query(`SELECT p.*,m.alias FROM payments p JOIN cycles c ON c.id=p.cycle_id
      JOIN members m ON m.id=p.member_id WHERE c.group_id=ANY($1::uuid[]) ORDER BY p.confirmed_at,p.id`, [ids])).rows;
    const accounts = (await client.query('SELECT * FROM accounts WHERE group_id=ANY($1::uuid[])', [ids])).rows;
    const entries = (await client.query(`SELECT e.*,a.name FROM ledger_entries e JOIN accounts a ON a.id=e.account_id
      WHERE a.group_id=ANY($1::uuid[])`, [ids])).rows;
    const claims = (await client.query(`SELECT cl.*,m.alias,i.phone FROM claims cl JOIN cycles c ON c.id=cl.cycle_id
      JOIN members m ON m.id=cl.member_id JOIN identities i ON i.id=m.identity_id
      WHERE c.group_id=ANY($1::uuid[])`, [ids])).rows;
    const corrections = (await client.query(`SELECT l.* FROM ledger_corrections l JOIN payments p ON p.id=l.payment_id
      JOIN cycles c ON c.id=p.cycle_id WHERE c.group_id=ANY($1::uuid[])`, [ids])).rows;
    const audits = (await client.query('SELECT * FROM audit_log WHERE group_id=ANY($1::uuid[])', [ids])).rows;
    const identityIds = new Set([
      ...groups.flatMap(g => [g.treasurer_id,g.officer_id]), ...members.map(m => m.identity_id),
      ...payments.map(p => p.confirmed_by), ...claims.map(c => c.resolved_by),
      ...corrections.flatMap(c => [c.entered_by,c.approved_by]), ...audits.map(a => a.actor_id)
    ].filter(Boolean));
    const identities = (await client.query('SELECT id,phone,display_name,created_at FROM identities WHERE id=ANY($1::uuid[])', [[...identityIds]])).rows;
    const paired = (await client.query(
      "SELECT 1 FROM bot_pairings WHERE (group_id = ANY($1::uuid[]) OR phone = $2) AND status = 'PAIRED' LIMIT 1",
      [ids, phone(verifiedPhone)]
    )).rows;
    const isBotConnected = paired.length > 0;
    await client.query('COMMIT');
    return {
      version: 1,
      isBotConnected,
      identities: identities.map(i => ({id:i.id,phone:i.phone,displayName:i.display_name,createdAt:millis(i.created_at)})),
      groups: groups.map(g => ({id:g.id,name:g.name,amount:Number(g.amount),currency:g.currency,schedule:g.schedule,
        treasurerId:g.treasurer_id,officerId:g.officer_id,state:g.state,createdAt:millis(g.created_at)})),
      members: members.map(m => ({id:m.id,groupId:m.group_id,identityId:m.identity_id,alias:m.alias || '',phone:m.phone,
        state:m.state,joinedCycle:m.joined_cycle,exitedCycle:m.exited_cycle,initials:(m.alias || '').split(/\s+/).slice(0,2).map(x=>x[0] || '').join('')})),
      cycles: cycles.map(c => ({id:c.id,groupId:c.group_id,number:c.number,amountDue:Number(c.amount_due),dueDate:c.due_day,
        state:c.state,closedAt:millis(c.closed_at),closedBy:c.closed_by})),
      payments: payments.map(p => ({id:p.id,cycleId:p.cycle_id,memberId:p.member_id,amountPaid:Number(p.amount_paid),method:p.method,
        status:p.status,confirmedBy:p.confirmed_by,confirmedAt:millis(p.confirmed_at),idempotencyKey:p.idempotency_key,
        source:p.source,prevHash:p.prev_hash,currentHash:p.current_hash,memberName:p.alias || '',isSynced:true})),
      accounts: accounts.map(a => ({id:a.id,groupId:a.group_id,name:a.name,type:a.type,memberId:a.member_id})),
      entries: entries.map(e => ({id:e.id,paymentId:e.payment_id,accountId:e.account_id,accountName:e.name,
        entryType:e.entry_type,amount:Number(e.amount),createdAt:millis(e.created_at)})),
      claims: claims.map(c => ({id:c.id,cycleId:c.cycle_id,memberId:c.member_id,memberName:c.alias || '',memberPhone:c.phone,
        claimedAmount:Number(c.claimed_amount),evidenceMoMoId:c.evidence_momo_id,state:c.state,createdAt:millis(c.created_at),
        resolvedBy:c.resolved_by,resolvedAt:millis(c.resolved_at)})),
      corrections: corrections.map(c => ({id:c.id,paymentId:c.payment_id,action:c.action,reason:c.reason,
        enteredBy:c.entered_by,approvedBy:c.approved_by,createdAt:millis(c.created_at)})),
      audits: audits.map(a => ({id:a.id,actorId:a.actor_id,groupId:a.group_id,action:a.action,
        payload:a.payload == null ? null : JSON.stringify(a.payload),createdAt:millis(a.created_at)}))
    };
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally { client.release(); }
}

module.exports = { getAccountBackup };
