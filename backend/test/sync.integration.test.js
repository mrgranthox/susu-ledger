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
  await db.query(readFileSync('../database/05_week_notifications.sql', 'utf8'));
  await db.query(readFileSync('../database/06_indexes.sql', 'utf8'));
  await db.query(readFileSync('../database/07_fcm_token.sql', 'utf8'));
});
after(() => db.pool.end());

test('legacy bot schema migrations repair missing tables and columns idempotently', async () => {
  const client=await db.pool.connect();
  try {
    await client.query('BEGIN');
    await client.query('DROP TABLE bot_sessions');
    await client.query('ALTER TABLE message_log DROP COLUMN phone');
    for(let attempt=0;attempt<2;attempt++) {
      await client.query(readFileSync('../database/03_message_log_phone.sql','utf8'));
      await client.query(readFileSync('../database/04_bot_sessions.sql','utf8'));
    }
    await client.query('SELECT identity_id,current_state,selected_group_id,pairing_code,pairing_expires_at,context_data,updated_at FROM bot_sessions');
    await client.query('SELECT phone FROM message_log');
  } finally {
    await client.query('ROLLBACK');
    client.release();
  }
});

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

test('account recovery returns an owner-scoped complete ledger that can be synced again', async () => {
  const { getAccountBackup } = require('../src/services/restoreService');
  const first=fixture(), other=fixture();
  await syncGroup(first,first.treasurer.phone);
  await syncGroup(other,other.treasurer.phone);
  const backup=await getAccountBackup(first.treasurer.phone);
  assert.equal(backup.version,1);
  assert.deepEqual(backup.groups.map(g=>g.id),[first.group.id]);
  assert.deepEqual(backup.payments.map(p=>p.id),[first.payments[0].id]);
  assert.equal(backup.entries.length,2);
  assert.equal(backup.cycles[0].dueDate,'2026-09-25');
  assert.equal(backup.identities.some(i=>i.phone===other.treasurer.phone),false);
  assert.equal(backup.payments[0].isSynced,true);
  assert.equal(typeof backup.payments[0].confirmedAt,'number');
  assert.equal(backup.identities.some(i=>'pinHash' in i),false);
  await syncGroup({group:backup.groups[0],treasurer:backup.identities.find(i=>i.id===backup.groups[0].treasurerId),
    members:backup.members,cycles:backup.cycles,payments:backup.payments},first.treasurer.phone);
  assert.equal((await getAccountBackup(first.treasurer.phone)).payments.length,1);
  assert.equal((await getAccountBackup('+233240009999')).groups.length,0);
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

test('pairing parser accepts copied codes and command variants without stealing other commands', () => {
  const { parsePairingCommand } = require('../src/services/stateMachine');
  for (const input of ['ABC-DEF', ' abc-def ', 'PAIR:ABC-DEF', 'pair abc-def', 'PAIR : ABC-DEF']) assert.equal(parsePairingCommand(input),'ABC-DEF');
  assert.equal(parsePairingCommand('PAIR'), '');
  assert.equal(parsePairingCommand('PAIR:'), '');
  for (const input of ['BALANCE','PAID','50','MOMO:12345','hello']) assert.equal(parsePairingCommand(input),null);
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

test('late-week claims keep their cycle and disconnected groups cannot submit claims', async () => {
  const payload=fixture(); payload.payments=[];
  await syncGroup(payload,payload.treasurer.phone);
  await registerPairing('LATE01',payload.group.id,payload.treasurer.phone);
  await consumePairing('LATE01',payload.treasurer.phone);
  payload.cycles[0].state='closed';
  payload.cycles.push({...payload.cycles[0],id:randomUUID(),number:2,state:'open'});
  await syncGroup(payload,payload.treasurer.phone);
  const whatsapp=require('../src/services/whatsappService');
  const replies=[];
  const text=mock.method(whatsapp,'sendWhatsAppTextMessage',async(to,body)=>replies.push(body));
  const interactive=mock.method(whatsapp,'sendWhatsAppInteractiveMessage',async(to,body)=>replies.push(body.text));
  try {
    const {handleIncomingWhatsAppMessage:handle}=require('../src/services/stateMachine');
    await handle(payload.members[0].phone,'PAID WEEK 1');
    assert.match(replies.at(-1),/Week 1/);
    await handle(payload.members[0].phone,'','CONFIRM_AMOUNT_50.00');
    const claim=(await db.query('SELECT cycle_id FROM claims WHERE member_id=$1',[payload.members[0].id])).rows[0];
    assert.equal(claim.cycle_id,payload.cycles[0].id);
    await handle(payload.treasurer.phone,'DISCONNECT');
    await handle(payload.members[0].phone,'PAID');
    assert.match(replies.at(-1),/disconnected/);
    assert.equal((await db.query('SELECT id FROM claims WHERE member_id=$1',[payload.members[0].id])).rowCount,1);
  } finally {text.mock.restore();interactive.mock.restore();}
});

test('recent member conversation uses a text receipt and week notices are idempotent', async () => {
  const payload=fixture(); payload.receiptPaymentIds=[payload.payments[0].id];
  await syncGroup(payload,payload.treasurer.phone);
  const identity=(await db.query('SELECT identity_id FROM members WHERE id=$1',[payload.members[0].id])).rows[0].identity_id;
  await db.query("UPDATE identities SET dpc_consent_granted=TRUE WHERE id=$1",[identity]);
  await db.query("INSERT INTO message_log(identity_id,phone,direction,body) VALUES($1,$2,'IN','PAID')",[identity,payload.members[0].phone]);
  await registerPairing('NOTICE',payload.group.id,payload.treasurer.phone); await consumePairing('NOTICE',payload.treasurer.phone);
  const whatsapp=require('../src/services/whatsappService'); const replies=[];
  const text=mock.method(whatsapp,'sendWhatsAppTextMessage',async(to,body)=>replies.push(body));
  try {
    assert.equal(await require('../src/services/receiptService').dispatchReceipt(payload.payments[0].id),true);
    assert.match(replies[0],/payment receipt/);
    payload.cycles[0].state='closed'; payload.cycles.push({...payload.cycles[0],id:randomUUID(),number:2,state:'open'});
    await syncGroup(payload,payload.treasurer.phone); await syncGroup(payload,payload.treasurer.phone);
    const dispatch=require('../src/services/weekNotificationService').dispatchWeekNotification;
    await dispatch(payload.group.id); await dispatch(payload.group.id);
    assert.equal(replies.length,2);
    assert.match(replies[1],/Week 2 is open/);
  } finally {text.mock.restore();}
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
    assert.equal((await fetch(`${base}/api/app/account/backup`)).status,401);
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

test('member uniqueness conflict on group_id and identity_id is updated without error', async () => {
  const payload = fixture();
  await syncGroup(payload, payload.treasurer.phone);
  payload.members = [{
    id: randomUUID(),
    groupId: payload.group.id,
    identityId: randomUUID(),
    phone: payload.members[0].phone,
    alias: 'Updated Member Alias',
    state: 'active',
    joinedCycle: 1
  }];
  const res = await syncGroup(payload, payload.treasurer.phone);
  assert.equal(res.groupId, payload.group.id);
  const updated = (await db.query('SELECT alias FROM members WHERE group_id=$1', [payload.group.id])).rows;
  assert.equal(updated.length, 1);
  assert.equal(updated[0].alias, 'Updated Member Alias');
});

test('multi-group memberships resolve both member and treasurer roles for single identity', async () => {
  const { getActiveMemberships, broadcastWelcomeToGroupMembers } = require('../src/services/stateMachine');
  const sharedIdentityPhone = `+23324000${String(++counter).padStart(4, '0')}`;
  const sharedIdentityId = randomUUID();

  // Create shared identity in DB
  await db.query('INSERT INTO identities(id, phone, display_name) VALUES($1, $2, $3)', [
    sharedIdentityId, sharedIdentityPhone, 'Shared Officer & Member'
  ]);

  // Group 1: User is Treasurer
  const g1 = fixture();
  g1.treasurer.id = sharedIdentityId;
  g1.treasurer.phone = sharedIdentityPhone;
  await syncGroup(g1, sharedIdentityPhone);

  // Group 2: User is Member
  const g2 = fixture();
  g2.members.push({
    id: randomUUID(),
    groupId: g2.group.id,
    identityId: sharedIdentityId,
    phone: sharedIdentityPhone,
    alias: 'Shared Member',
    state: 'active',
    joinedCycle: 1
  });
  await syncGroup(g2, g2.treasurer.phone);

  const memberships = await getActiveMemberships(sharedIdentityId);
  assert.equal(memberships.length, 2);
  const roles = memberships.map(m => m.user_role);
  assert.ok(roles.includes('Treasurer'));
  assert.ok(roles.includes('Member'));

  const whatsapp = require('../src/services/whatsappService');
  const textMock = mock.method(whatsapp, 'sendWhatsAppTextMessage', async () => ({ success: true }));
  try {
    const sentCount = await broadcastWelcomeToGroupMembers(g2.group.id);
    assert.ok(sentCount >= 1);
  } finally {
    textMock.mock.restore();
  }
});

test('media message logs attachment and sends helpful response', async () => {
  const { handleMediaMessage } = require('../src/services/stateMachine');
  const whatsapp = require('../src/services/whatsappService');
  const sentMessages = [];
  const textMock = mock.method(whatsapp, 'sendWhatsAppTextMessage', async (phone, text) => {
    sentMessages.push({ phone, text });
    return { success: true };
  });

  try {
    const memberPhone = '+233249990001';
    await handleMediaMessage(memberPhone, 'image', 'meta-media-img-123');
    assert.equal(sentMessages.length, 1);
    assert.match(sentMessages[0].text, /image/i);

    const logRes = await db.query(
      "SELECT body FROM message_log WHERE phone = $1 AND body LIKE '%meta-media-img-123%'",
      [memberPhone]
    );
    assert.equal(logRes.rowCount, 1);
  } finally {
    textMock.mock.restore();
  }
});

test('maintenance prune removes webhook_events older than 7 days', async () => {
  await db.query("INSERT INTO webhook_events(id, processed_at) VALUES('old-event-1', NOW() - INTERVAL '10 days') ON CONFLICT DO NOTHING");
  await db.query("INSERT INTO webhook_events(id, processed_at) VALUES('new-event-1', NOW()) ON CONFLICT DO NOTHING");

  const deleteRes = await db.query("DELETE FROM webhook_events WHERE processed_at < NOW() - INTERVAL '7 days'");
  assert.ok(deleteRes.rowCount >= 1);

  const checkOld = await db.query("SELECT id FROM webhook_events WHERE id = 'old-event-1'");
  assert.equal(checkOld.rowCount, 0);

  const checkNew = await db.query("SELECT id FROM webhook_events WHERE id = 'new-event-1'");
  assert.equal(checkNew.rowCount, 1);
});


