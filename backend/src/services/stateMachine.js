const db = require('../config/database');
const { consumePairing } = require('./pairingService');
const {
  sendWhatsAppInteractiveMessage,
  sendWhatsAppTextMessage,
  logMessage,
} = require('./whatsappService');

function normalizePhone(rawPhone) {
  const cleanPhone = String(rawPhone || '').replace(/[^0-9]/g, '');
  const formattedPhone = cleanPhone.startsWith('233')
    ? `+${cleanPhone}`
    : `+233${cleanPhone.replace(/^0/, '')}`;

  return { cleanPhone, formattedPhone };
}

async function resolveIdentity(fromPhone, formattedPhone) {
  const cleanPhone = String(fromPhone || '').replace(/[^0-9]/g, '');
  const identityRes = await db.query(
    'SELECT * FROM identities WHERE phone = $1 OR phone = $2 OR phone = $3',
    [fromPhone, cleanPhone, formattedPhone]
  );

  if (identityRes.rows[0]) {
    return identityRes.rows[0];
  }

  console.log(`[WhatsApp Bot] New or unregistered number: ${fromPhone}. Creating guest identity.`);
  const newIdentity = await db.query(
    'INSERT INTO identities (phone, display_name) VALUES ($1, $2) RETURNING *',
    [formattedPhone, `Member (${formattedPhone.slice(-4)})`]
  );
  return newIdentity.rows[0];
}

async function getActiveMemberships(identityId) {
  const result = await db.query(
    `SELECT
       m.id AS member_id,
       m.alias,
       g.id AS group_id,
       g.name AS group_name,
       g.amount AS group_amount,
       g.currency,
       g.schedule,
       g.treasurer_id,
       c.id AS cycle_id,
       c.number AS cycle_number,
       c.amount_due
     FROM members m
     JOIN groups g ON g.id = m.group_id
     LEFT JOIN LATERAL (
       SELECT *
       FROM cycles
       WHERE group_id = g.id AND state = 'open'
       ORDER BY number DESC
       LIMIT 1
     ) c ON TRUE
     WHERE m.identity_id = $1
       AND m.state = 'active'
       AND g.state = 'active'
     ORDER BY g.created_at ASC`,
    [identityId]
  );

  return result.rows;
}

async function getBotSession(identityId) {
  const result = await db.query('SELECT * FROM bot_sessions WHERE identity_id = $1', [identityId]);
  return result.rows[0] || null;
}

async function saveBotSession(identityId, patch) {
  const currentState = patch.currentState || 'IDLE';
  const selectedGroupId = patch.selectedGroupId || null;
  const pairingCode = patch.pairingCode || null;
  const pairingExpiresAt = patch.pairingExpiresAt || null;
  const contextData = patch.contextData || {};

  await db.query(
    `INSERT INTO bot_sessions (
       identity_id, current_state, selected_group_id, pairing_code,
       pairing_expires_at, context_data, updated_at
     )
     VALUES ($1, $2, $3, $4, $5, $6::jsonb, CURRENT_TIMESTAMP)
     ON CONFLICT (identity_id)
     DO UPDATE SET
       current_state = EXCLUDED.current_state,
       selected_group_id = COALESCE(EXCLUDED.selected_group_id, bot_sessions.selected_group_id),
       pairing_code = EXCLUDED.pairing_code,
       pairing_expires_at = EXCLUDED.pairing_expires_at,
       context_data = EXCLUDED.context_data,
       updated_at = CURRENT_TIMESTAMP`,
    [identityId, currentState, selectedGroupId, pairingCode, pairingExpiresAt, JSON.stringify(contextData)]
  );
}

function parseAmount(textBody, fallbackAmount) {
  const cleanText = String(textBody || '').trim().toUpperCase();
  const match = cleanText.match(/^(?:(?:GHS|AMOUNT)[:\s]*)?([0-9]+(?:\.[0-9]{1,2})?)(?:\s|$)/);
  const amount = match ? Number(match[1]) : Number(fallbackAmount);
  return Number.isFinite(amount) && amount > 0 && amount <= 9999999999.99 ? amount : NaN;
}

function parseMomoReference(textBody) {
  const cleanText = String(textBody || '').trim().toUpperCase();
  const match = cleanText.match(/MOMO[:\s-]*([A-Z0-9-]+)/);
  return match?.[1] || `WA-${Date.now().toString(36).toUpperCase()}`;
}

function findMembershipSelection(cleanText, buttonPayload, memberships, session) {
  if (buttonPayload?.startsWith('GROUP_')) {
    const groupId = buttonPayload.replace('GROUP_', '');
    return memberships.find((m) => m.group_id === groupId || m.group_id.startsWith(groupId));
  }

  if (cleanText.startsWith('SELECT_GROUP:') || cleanText.startsWith('GROUP_')) {
    const groupId = cleanText.replace('SELECT_GROUP:', '').replace('GROUP_', '').trim();
    return memberships.find((m) => m.group_id === groupId || m.group_id.startsWith(groupId));
  }

  if (/^[1-9]$/.test(cleanText)) {
    return memberships[Number(cleanText) - 1];
  }

  if (session?.current_state === 'WAITING_GROUP_SELECTION') return null;

  if (session?.selected_group_id) {
    const sessionMembership = memberships.find((m) => m.group_id === session.selected_group_id);
    if (sessionMembership) return sessionMembership;
  }

  return memberships[0];
}

async function promptForGroup(fromPhone, identityId, memberships) {
  const lines = memberships.map((m, index) => {
    const amount = Number(m.amount_due || m.group_amount || 0).toFixed(2);
    return `${index + 1}. ${m.group_name} - Week ${m.cycle_number || 1}, GHS ${amount}`;
  });

  await saveBotSession(identityId, {
    currentState: 'WAITING_GROUP_SELECTION',
    contextData: { groupIds: memberships.map((m) => m.group_id) },
  });

  await sendWhatsAppTextMessage(
    fromPhone,
    `You belong to multiple active Susu groups. Which group is this payment for?\n\n${lines.join('\n')}\n\nReply with the group number.`,
    identityId
  );
}

async function promptForAmount(fromPhone, identityId, membership) {
  const amountDue = Number(membership.amount_due || membership.group_amount || 0);
  await saveBotSession(identityId, {
    currentState: 'WAITING_CLAIM_AMOUNT',
    selectedGroupId: membership.group_id,
    contextData: {
      memberId: membership.member_id,
      cycleId: membership.cycle_id,
      amountDue,
    },
  });

  if (!membership.cycle_id) {
    await sendWhatsAppTextMessage(
      fromPhone,
      `I found ${membership.group_name}, but there is no open contribution cycle yet. Please ask your treasurer to open a cycle in the app.`,
      identityId
    );
    return;
  }

  await sendWhatsAppInteractiveMessage(
    fromPhone,
    {
      text: `Confirm your payment claim for ${membership.group_name} (Week ${membership.cycle_number || 1}):`,
      buttons: [
        { id: `CONFIRM_AMOUNT_${amountDue.toFixed(2)}`, title: `GHS ${amountDue.toFixed(2)}` },
        { id: 'CLAIM_CUSTOM', title: 'Other Amount' },
      ],
    },
    identityId
  );
}

async function upsertClaim({ cycleId, memberId, amount, evidenceMoMoId }) {
  const client = await db.pool.connect();
  try {
    await client.query('BEGIN');

    const existing = await client.query(
      `SELECT id
       FROM claims
       WHERE cycle_id = $1 AND member_id = $2 AND state = 'pending'
       ORDER BY created_at DESC
       LIMIT 1
       FOR UPDATE`,
      [cycleId, memberId]
    );

    let claim;
    if (existing.rows[0]) {
      const updated = await client.query(
        `UPDATE claims
         SET claimed_amount = $1,
             evidence_momo_id = COALESCE($2, evidence_momo_id),
             created_at = CURRENT_TIMESTAMP
         WHERE id = $3
         RETURNING *`,
        [amount, evidenceMoMoId, existing.rows[0].id]
      );
      claim = updated.rows[0];
    } else {
      const inserted = await client.query(
        `INSERT INTO claims (cycle_id, member_id, claimed_amount, evidence_momo_id, state)
         VALUES ($1, $2, $3, $4, 'pending')
         RETURNING *`,
        [cycleId, memberId, amount, evidenceMoMoId]
      );
      claim = inserted.rows[0];
    }

    await client.query('COMMIT');
    return claim;
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

async function notifyTreasurer(membership, amount, evidenceMoMoId, identityId) {
  if (!membership.treasurer_id) return;

  const treasurerRes = await db.query('SELECT phone, display_name FROM identities WHERE id = $1', [
    membership.treasurer_id,
  ]);
  const treasurer = treasurerRes.rows[0];
  if (!treasurer?.phone) return;

  const memberName = membership.alias || 'A member';
  await sendWhatsAppTextMessage(
    treasurer.phone,
    `MEMBER CLAIM ALERT\n\n${memberName} claims GHS ${Number(amount).toFixed(2)} for ${membership.group_name} (Week ${membership.cycle_number || 1}).\nReference: ${evidenceMoMoId || 'Not provided'}\n\nOpen SusuLedger to confirm or reject the claim.`,
    identityId
  );
}

async function recordPaymentClaim(fromPhone, identity, membership, amount, evidenceMoMoId) {
  if (!membership?.cycle_id || !membership?.member_id) {
    await sendWhatsAppTextMessage(
      fromPhone,
      'I could not find an open Susu cycle for your account. Please ask your treasurer to check your group setup.',
      identity.id
    );
    return;
  }

  const claim = await upsertClaim({
    cycleId: membership.cycle_id,
    memberId: membership.member_id,
    amount,
    evidenceMoMoId,
  });

  await saveBotSession(identity.id, {
    currentState: 'CLAIM_PENDING',
    selectedGroupId: membership.group_id,
    contextData: { claimId: claim.id, cycleId: membership.cycle_id, memberId: membership.member_id },
  });

  try {
    await notifyTreasurer(membership, amount, evidenceMoMoId, identity.id);
  } catch (err) {
    console.error('[Treasurer Claim Notification Error]', err.message);
  }

  await sendWhatsAppTextMessage(
    fromPhone,
    `Payment claim recorded.\n\nAmount: GHS ${Number(amount).toFixed(2)}\nGroup: ${membership.group_name}\nWeek: ${membership.cycle_number || 1}\nReference: ${evidenceMoMoId || 'Not provided'}\nStatus: Pending treasurer confirmation.`,
    identity.id
  );
}

async function handleIncomingWhatsAppMessage(fromPhone, messageBody, buttonPayload) {

  const { cleanPhone, formattedPhone } = normalizePhone(fromPhone);
  const identity = await resolveIdentity(fromPhone, formattedPhone);
  const identityId = identity.id;
  const cleanText = String(messageBody || '').trim().toUpperCase();

  await logMessage({
    identityId,
    phone: formattedPhone || cleanPhone || fromPhone,
    direction: 'IN',
    body: messageBody || buttonPayload || '',
    metaStatus: 'received',
  });

  if (cleanText.startsWith('PAIR:') || cleanText.startsWith('PAIR ')) {
    const rawCode = cleanText.replace('PAIR:', '').replace('PAIR', '').trim();
    if (rawCode) {
      const pairSession = await consumePairing(rawCode, formattedPhone);
      if (pairSession) {
        pairSession.status = 'PAIRED';
        pairSession.pairedPhone = formattedPhone;
        pairSession.pairedAt = new Date().toISOString();
        await saveBotSession(identityId, {
          currentState: 'PAIRED',
          selectedGroupId: pairSession.group_id,
          pairingCode: rawCode,
          contextData: pairSession,
        });

        await sendWhatsAppTextMessage(
          fromPhone,
          `SusuLedger instance paired successfully.\n\nPairing Code: ${rawCode}\nPhone: ${formattedPhone}\nYour app is now connected to the Cloud Run WhatsApp Bot Engine.`,
          identityId
        );
        return;
      }

      await sendWhatsAppTextMessage(
        fromPhone,
        `Pairing code expired or not found.\nCode ${rawCode} is invalid or past its 15-minute validity window. Please generate a new code in the mobile app.`,
        identityId
      );
      return;
    }
  }

  const memberships = await getActiveMemberships(identityId);
  const session = await getBotSession(identityId);

  if (cleanText === 'STOP' || cleanText === 'START' || cleanText === 'OK') {
    const subscribed = cleanText !== 'STOP';
    await db.query('UPDATE identities SET dpc_consent_granted=$1,dpc_consent_timestamp=clock_timestamp() WHERE id=$2',[subscribed,identityId]);
    await sendWhatsAppTextMessage(fromPhone,subscribed ? 'Group reminders enabled. Reply STOP to unsubscribe.' : 'Group reminders stopped. Reply START to subscribe again.',identityId);
    return;
  }

  if (cleanText === 'HELP' || cleanText === 'MENU' || cleanText === 'COMMANDS' || !cleanText) {
    await sendWhatsAppTextMessage(
      fromPhone,
      `SusuLedger WhatsApp Assistant:\n\n` +
        `Reply PAID to submit a contribution claim\n` +
        `Reply BALANCE to check your group balance\n` +
        `Reply PROGRESS to view current cycle status\n` +
        `Reply DUE for contribution details\n` +
        `Reply SWITCH to select a group\n` +
        `Reply START or STOP to manage reminders\n` +
        `Reply PAIR:CODE to pair your mobile app instance`,
      identityId
    );
    return;
  }

  if (memberships.length === 0) {
    await sendWhatsAppTextMessage(
      fromPhone,
      `Welcome to SusuLedger. Your phone (${formattedPhone}) is known to the bot, but it is not attached to an active group yet. Ask your treasurer to add this number in the SusuLedger app.`,
      identityId
    );
    return;
  }

  const isClaimStart = cleanText === 'PAID' || buttonPayload === 'CLAIM_PAID';
  if (cleanText === 'SWITCH') {
    await promptForGroup(fromPhone,identityId,memberships);
    return;
  }
  const isAmountConfirmation = buttonPayload?.startsWith('CONFIRM_AMOUNT_') || cleanText.startsWith('CONFIRM_AMOUNT_');
  const isCustomClaim = buttonPayload === 'CLAIM_CUSTOM';
  const hasMomoEvidence = cleanText.startsWith('MOMO:') || cleanText.includes('MOMO');
  const selectedMembership = findMembershipSelection(cleanText, buttonPayload, memberships, session);
  if (!selectedMembership && session?.current_state === 'WAITING_GROUP_SELECTION') {
    await promptForGroup(fromPhone, identityId, memberships);
    return;
  }
  const selectedByText = selectedMembership && (session?.current_state === 'WAITING_GROUP_SELECTION' || /^[1-9]$/.test(cleanText));

  if (isClaimStart) {
    if (memberships.length > 1 && !session?.selected_group_id) {
      await promptForGroup(fromPhone, identityId, memberships);
      return;
    }

    await promptForAmount(fromPhone, identityId, selectedMembership);
    return;
  }

  if (selectedByText && session?.current_state === 'WAITING_GROUP_SELECTION') {
    await promptForAmount(fromPhone, identityId, selectedMembership);
    return;
  }

  if (isCustomClaim) {
    await saveBotSession(identityId, {
      currentState: 'WAITING_CUSTOM_AMOUNT',
      selectedGroupId: selectedMembership.group_id,
      contextData: { memberId: selectedMembership.member_id, cycleId: selectedMembership.cycle_id },
    });
    await sendWhatsAppTextMessage(
      fromPhone,
      'Reply with the amount and optional MoMo reference, for example: GHS 35 MOMO:MP12345',
      identityId
    );
    return;
  }

  if (isAmountConfirmation || hasMomoEvidence || session?.current_state === 'WAITING_CUSTOM_AMOUNT') {
    const rawAmount = (buttonPayload || cleanText).replace('CONFIRM_AMOUNT_', '');
    const fallbackAmount = session?.current_state === 'WAITING_CUSTOM_AMOUNT' ? NaN : selectedMembership.amount_due || selectedMembership.group_amount || 0;
    const amount = parseAmount(rawAmount, fallbackAmount);
    if (!Number.isFinite(amount)) {
      await sendWhatsAppTextMessage(fromPhone, 'Enter a positive amount, for example GHS 35.50.', identityId);
      return;
    }
    const evidenceMoMoId = hasMomoEvidence ? parseMomoReference(cleanText) : null;
    await recordPaymentClaim(fromPhone, identity, selectedMembership, amount, evidenceMoMoId);
    return;
  }

  if (cleanText === 'BALANCE') {
    const totals = await db.query(`SELECT m.group_id,COALESCE(SUM(p.amount_paid),0) AS total
      FROM members m LEFT JOIN payments p ON p.member_id=m.id AND p.status='confirmed'
      WHERE m.identity_id=$1 GROUP BY m.group_id`,[identityId]);
    const lines=memberships.map(m=>`${m.group_name}: GHS ${Number(totals.rows.find(t=>t.group_id===m.group_id)?.total || 0).toFixed(2)} confirmed`);
    await sendWhatsAppTextMessage(
      fromPhone,
      `SusuLedger contribution statement\n\n${lines.join('\n')}`,
      identityId
    );
    return;
  }

  if (cleanText === 'DUE') {
    const treasurer=(await db.query('SELECT display_name,phone FROM identities WHERE id=$1',[selectedMembership.treasurer_id])).rows[0];
    await sendWhatsAppTextMessage(fromPhone,`${selectedMembership.group_name}: GHS ${Number(selectedMembership.amount_due || selectedMembership.group_amount).toFixed(2)} due.\nContact your treasurer: ${treasurer.display_name} (${treasurer.phone}).\nReply PAID after contributing.`,identityId);
    return;
  }

  if (cleanText === 'PROGRESS') {
    const lines = [];
    for (const membership of memberships) {
      const total=await db.query("SELECT COALESCE(SUM(amount_paid),0) AS total FROM payments WHERE cycle_id=$1 AND status='confirmed'",[membership.cycle_id]);
      lines.push(`${membership.group_name}: Week ${membership.cycle_number || 1}, GHS ${Number(total.rows[0].total).toFixed(2)} collected`);
    }

    await sendWhatsAppTextMessage(fromPhone, `SusuLedger group progress\n\n${lines.join('\n')}`, identityId);
    return;
  }

  await sendWhatsAppTextMessage(
    fromPhone,
    `Hello! Reply PAID to log a contribution or HELP for available commands.`,
    identityId
  );
}

module.exports = {
  handleIncomingWhatsAppMessage,
  parseAmount,
};
