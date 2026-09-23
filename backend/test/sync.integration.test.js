const { test, before, after, mock } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { randomUUID, createHmac } = require('node:crypto');
const db = require('../src/config/database');
const { syncGroup, cloudId, money } = require('../src/services/syncService');
const { registerPairing, consumePairing } = require('../src/services/pairingService');
const { verifyLedgerChain } = require('../src/services/cryptoEngine');

before(async () => {
  // Require an explicitly isolated database; never run schema creation against production.
  assert.equal(process.env.SUSU_TEST_DATABASE, 'isolated');
  await db.query(readFileSync('../database/01_schema.sql', 'utf8'));
  await db.query(readFileSync('../database/02_pairings.sql', 'utf8'));
});
after(() => db.pool.end());

let counter = 0;
function fixture() {
  const treasurer = { id:randomUUID(),phone:`+23324000${String(++counter).padStart(4,'0')}`,displayName:'Test Treasurer' };
  const group = {id:randomUUID(),name:'Integration test',amount:50,currency:'GHS',schedule:'weekly',treasurerId:treasurer.id,state:'active'};
  const members = [{id:randomUUID(),groupId:group.id,identityId:randomUUID(),phone:`+23325000${String(counter).padStart(4,'0')}`,alias:'Test Member',state:'active',joinedCycle:1}];
  const cycles = [{id:randomUUID(),groupId:group.id,number:1,amountDue:50,dueDate:'2026-09-25',state:'open'}];
  const payments = [{id:randomUUID(),cycleId:cycles[0].id,memberId:members[0].id,amountPaid:25,method:'MOMO',idempotencyKey:randomUUID()}];
  return {treasurer,group,members,cycles,payments};
}

test('group, members, cycle and payment persist atomically; retry does not duplicate ledger', async () => {
  const payload = fixture();
  const responses = await Promise.all([syncGroup(payload,payload.treasurer.phone),syncGroup(payload,payload.treasurer.phone)]);
  assert.deepEqual(responses[0].acknowledgedPaymentIds,[payload.payments[0].id]);
  const payments = await db.query('SELECT * FROM payments WHERE cycle_id=$1',[payload.cycles[0].id]);
  assert.equal(payments.rowCount,1);
  assert.equal(verifyLedgerChain(payments.rows).isChainValid,true);
  const entries = await db.query('SELECT entry_type,amount::float FROM ledger_entries WHERE payment_id=$1 ORDER BY entry_type',[payload.payments[0].id]);
  assert.deepEqual(entries.rows,[{entry_type:'credit',amount:25},{entry_type:'debit',amount:25}]);
});

test('invalid payment rolls back the entire group upload', async () => {
  const payload=fixture(); payload.payments[0].amountPaid=-5;
  await assert.rejects(syncGroup(payload,payload.treasurer.phone),/Amount/);
  assert.equal((await db.query('SELECT id FROM groups WHERE id=$1',[payload.group.id])).rowCount,0);
});

test('owner checks reject another phone and foreign member references', async () => {
  const first=fixture(),second=fixture();
  await syncGroup(first,first.treasurer.phone);
  await assert.rejects(syncGroup(first,second.treasurer.phone),/verified phone/);
  second.payments[0].memberId=first.members[0].id;
  await assert.rejects(syncGroup(second,second.treasurer.phone),/group mismatch/);
});

test('retry with a changed amount is rejected without modifying stored payment', async () => {
  const payload=fixture(); await syncGroup(payload,payload.treasurer.phone);
  payload.payments[0].amountPaid=30;
  await assert.rejects(syncGroup(payload,payload.treasurer.phone),/retry differs/);
  assert.equal(Number((await db.query('SELECT amount_paid FROM payments WHERE id=$1',[payload.payments[0].id])).rows[0].amount_paid),25);
});

test('multiple contributions produce a valid group chain and resolve pending claims', async () => {
  const payload=fixture(); payload.payments=[];
  await syncGroup(payload,payload.treasurer.phone);
  await db.query('INSERT INTO claims(cycle_id,member_id,claimed_amount) VALUES($1,$2,20)',[payload.cycles[0].id,payload.members[0].id]);
  for(let index=0;index<3;index++) payload.payments.push({id:randomUUID(),cycleId:payload.cycles[0].id,memberId:payload.members[0].id,amountPaid:10,method:'CASH',idempotencyKey:randomUUID()});
  await syncGroup(payload,payload.treasurer.phone);
  const rows=(await db.query('SELECT * FROM payments WHERE cycle_id=$1 ORDER BY confirmed_at,id',[payload.cycles[0].id])).rows;
  assert.equal(verifyLedgerChain(rows).isChainValid,true);
  assert.equal((await db.query('SELECT state FROM claims WHERE cycle_id=$1',[payload.cycles[0].id])).rows[0].state,'confirmed');
});

test('pairing is phone-bound, expires, and is consumed once across concurrent requests', async () => {
  const payload=fixture(); await syncGroup(payload,payload.treasurer.phone);
  await registerPairing('ABC-DEF',payload.group.id,payload.treasurer.phone);
  assert.equal(await consumePairing('ABC-DEF',payload.members[0].phone),undefined);
  assert.equal((await db.query("SELECT status FROM bot_pairings WHERE code='ABC-DEF'")).rows[0].status,'PENDING_WHATSAPP_CONFIRMATION');
  const attempts=await Promise.all([consumePairing('ABC-DEF',payload.treasurer.phone),consumePairing('ABC-DEF',payload.treasurer.phone)]);
  assert.equal(attempts.filter(Boolean).length,1);
  await registerPairing('EXPIRE',payload.group.id,payload.treasurer.phone);
  await db.query("UPDATE bot_pairings SET expires_at=clock_timestamp()-interval '1 second' WHERE code='EXPIRE'");
  assert.equal(await consumePairing('EXPIRE',payload.treasurer.phone),undefined);
});

test('legacy local IDs map deterministically; invalid amounts are rejected', () => {
  assert.equal(cloudId('hello'),'5d41402a-bc4b-3a76-b971-9d911017c592');
  for(const value of [NaN,Infinity,0,-1,0.001,'50']) assert.throws(()=>money(value));
});

test('claim rejection is owner-bound, idempotent and cannot reject a confirmed claim', async () => {
  const { rejectClaim } = require('../src/services/claimService');
  const payload=fixture(); payload.payments=[];
  await syncGroup(payload,payload.treasurer.phone);
  const claim=(await db.query('INSERT INTO claims(cycle_id,member_id,claimed_amount) VALUES($1,$2,20) RETURNING id',[payload.cycles[0].id,payload.members[0].id])).rows[0];
  await assert.rejects(rejectClaim(payload.group.id,claim.id,payload.members[0].phone,'Unverified'),/not found/);
  await rejectClaim(payload.group.id,claim.id,payload.treasurer.phone,'Unverified');
  await rejectClaim(payload.group.id,claim.id,payload.treasurer.phone,'Unverified');
  assert.equal((await db.query("SELECT id FROM audit_log WHERE group_id=$1 AND action='CLAIM_REJECTED'",[payload.group.id])).rowCount,1);
  await db.query("UPDATE claims SET state='confirmed' WHERE id=$1",[claim.id]);
  await assert.rejects(rejectClaim(payload.group.id,claim.id,payload.treasurer.phone,'Unverified'),/already resolved/);
});

test('custom amount parsing does not interpret a MoMo reference or invalid text as money', () => {
  const { parseAmount }=require('../src/services/stateMachine');
  assert.equal(parseAmount('GHS 35.50 MOMO:123456',NaN),35.5);
  for(const input of ['MOMO:123456','hello','-10','0','10.123']) assert.equal(Number.isNaN(parseAmount(input,NaN)),true);
});

test('provider failures retain a queued receipt without undoing or duplicating payment', async () => {
  const payload=fixture();
  payload.receiptPaymentIds=[payload.payments[0].id];
  await syncGroup(payload,payload.treasurer.phone);
  const whatsapp=require('../src/services/whatsappService');
  let attempts=0;
  const sender=mock.method(whatsapp,'sendWhatsAppTemplate',async()=>{
    attempts++;
    if(attempts===1) throw new Error('provider unavailable');
    return {messages:[{id:'test-only'}]};
  });
  try {
    const { dispatchReceipt }=require('../src/services/receiptService');
    assert.equal(await dispatchReceipt(payload.payments[0].id),false);
    assert.equal((await db.query('SELECT state FROM payment_receipts WHERE payment_id=$1',[payload.payments[0].id])).rows[0].state,'pending');
    assert.equal(await dispatchReceipt(payload.payments[0].id),true);
    assert.equal(await dispatchReceipt(payload.payments[0].id),true);
    assert.equal(attempts,2);
    assert.equal((await db.query('SELECT id FROM payments WHERE cycle_id=$1',[payload.cycles[0].id])).rowCount,1);
  } finally { sender.mock.restore(); }
});

test('HTTP auth fails closed; signed webhook processes batches once and retries failures', async () => {
  const stateMachine = require('../src/services/stateMachine');
  const received = [];
  const handler = mock.method(stateMachine,'handleIncomingWhatsAppMessage', async (sender,body) => {
    if (body === 'FAIL') throw new Error('storage unavailable');
    received.push(body);
  });
  process.env.META_APP_SECRET = 'integration-only-secret';
  const app = require('../src/server');
  const server = app.listen(0,'127.0.0.1');
  await new Promise(resolve => server.once('listening',resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const send = async (body, signed = true) => {
    const text=JSON.stringify(body);
    return fetch(`${base}/webhooks/whatsapp`,{method:'POST',headers:{'content-type':'application/json',...(signed ? {'x-hub-signature-256':`sha256=${createHmac('sha256',process.env.META_APP_SECRET).update(text).digest('hex')}`} : {})},body:text});
  };
  try {
    assert.equal((await fetch(`${base}/api/app/sync`,{method:'POST',headers:{'content-type':'application/json'},body:'{}'})).status,401);
    assert.equal((await fetch(`${base}/api/app/groups/${randomUUID()}`)).status,401);
    const payload={object:'whatsapp_business_account',entry:[{changes:[{value:{messages:[{id:'message-1',from:'233240000000',type:'text',text:{body:'PAID'}},{id:'message-2',from:'233240000000',type:'text',text:{body:'BALANCE'}}]}}]}]};
    assert.equal((await send(payload,false)).status,401);
    assert.equal((await send(payload)).status,200);
    assert.equal((await send(payload)).status,200);
    assert.deepEqual(received,['PAID','BALANCE']);
    payload.entry[0].changes[0].value.messages=[{id:'message-fail',from:'233240000000',type:'text',text:{body:'FAIL'}}];
    assert.equal((await send(payload)).status,503);
    assert.equal((await db.query("SELECT id FROM webhook_events WHERE id='message-fail'")).rowCount,0);
  } finally {
    handler.mock.restore();
    await new Promise(resolve => server.close(resolve));
  }
});
