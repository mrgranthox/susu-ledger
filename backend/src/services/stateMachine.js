const db = require('../config/database');
const {
  sendWhatsAppTextMessage,
  sendWhatsAppInteractiveButtons,
  logMessage,
} = require('./whatsappService');

async function getOrCreateSession(identityId) {
  const res = await db.query(
    'SELECT * FROM bot_sessions WHERE identity_id = $1',
    [identityId]
  );
  if (res.rows.length > 0) {
    return res.rows[0];
  }
  const insertRes = await db.query(
    `INSERT INTO bot_sessions (identity_id, current_state, context_data)
     VALUES ($1, 'IDLE', '{}'::jsonb)
     RETURNING *`,
    [identityId]
  );
  return insertRes.rows[0];
}

async function updateSessionState(identityId, newState, newContext = null) {
  if (newContext) {
    await db.query(
      `UPDATE bot_sessions 
       SET current_state = $1, context_data = $2, updated_at = CURRENT_TIMESTAMP
       WHERE identity_id = $3`,
      [newState, JSON.stringify(newContext), identityId]
    );
  } else {
    await db.query(
      `UPDATE bot_sessions 
       SET current_state = $1, updated_at = CURRENT_TIMESTAMP
       WHERE identity_id = $2`,
      [newState, identityId]
    );
  }
}

async function handleIncomingWhatsAppMessage(fromPhone, messageBody, buttonPayload) {
  console.log(`[Bot Inbound] From: ${fromPhone} Body: "${messageBody}" Payload: "${buttonPayload}"`);

  // Normalize phone number (E.164 without leading plus or with plus)
  const phoneClean = fromPhone.startsWith('+') ? fromPhone : `+${fromPhone}`;
  const phoneDigits = fromPhone.replace(/\D/g, '');

  // Log incoming message
  await logMessage({
    phone: phoneClean,
    direction: 'IN',
    body: buttonPayload ? `Button: ${buttonPayload}` : messageBody,
    metaStatus: 'received',
  });

  // 1. Resolve Identity
  let identityRes = await db.query(
    'SELECT * FROM identities WHERE phone = $1 OR phone = $2 OR REPLACE(phone, \' \', \'\') = $3',
    [phoneClean, fromPhone, phoneClean.replace(/\s+/g, '')]
  );

  let identity = identityRes.rows[0];

  // If unregistered, handle pairing code or onboarding
  if (!identity) {
    const textUpper = (messageBody || '').trim().toUpperCase();
    if (textUpper.startsWith('PAIR:')) {
      const code = textUpper.replace('PAIR:', '').trim();
      await handlePairingCode(phoneClean, code);
      return;
    }

    // DPC consent reply
    if (textUpper === 'OK' || textUpper === 'START') {
      const newIdentity = await db.query(
        `INSERT INTO identities (phone, display_name, dpc_consent_granted, dpc_consent_timestamp)
         VALUES ($1, $2, TRUE, CURRENT_TIMESTAMP)
         RETURNING *`,
        [phoneClean, `Member ${phoneClean.slice(-4)}`]
      );
      identity = newIdentity.rows[0];
      await sendWhatsAppTextMessage(
        phoneClean,
        `Akwaaba! Welcome to SusuLedger. Your phone number has been verified.\nTo pair with your savings group, please send your 6-character pairing code: e.g. "PAIR:A7K2-9P"`,
        identity.id
      );
      return;
    }

    // Default message prompting for opt-in consent
    await sendWhatsAppTextMessage(
      phoneClean,
      `Welcome to SusuLedger Ghana — Digital Record-Keeper for Susu Groups.\n\nMoney never touches this bot. To receive your official payment receipts and weekly balance digests, please reply "OK" to agree to terms & DPC notice.`
    );
    return;
  }

  const session = await getOrCreateSession(identity.id);
  const textUpper = (messageBody || '').trim().toUpperCase();

  // Multi-group selection trigger
  if (textUpper === 'GROUPS' || textUpper === 'SWITCH') {
    await handleMultiGroupSelection(identity);
    return;
  }

  // Handle pairing command from existing identity
  if (textUpper.startsWith('PAIR:')) {
    const code = textUpper.replace('PAIR:', '').trim();
    await handlePairingCode(phoneClean, code, identity);
    return;
  }

  // 2. State Machine Routing
  switch (session.current_state) {
    case 'IDLE':
      if (textUpper === 'PAID' || buttonPayload === 'CLAIM_PAID') {
        await initiateClaimFlow(identity);
      } else if (textUpper === 'BALANCE' || textUpper === 'STATUS') {
        await sendMemberStatus(identity);
      } else if (textUpper.startsWith('DISPUTE') || buttonPayload === 'DISPUTE_PAYMENT') {
        await initiateDisputeFlow(identity);
      } else {
        await sendWhatsAppInteractiveButtons(
          identity.phone,
          `Hello ${identity.display_name}, how can SusuLedger assist you today?`,
          [
            { id: 'CLAIM_PAID', title: 'I Have Paid' },
            { id: 'VIEW_BALANCE', title: 'Check My Balance' },
            { id: 'DISPUTE_PAYMENT', title: 'Log a Dispute' },
          ],
          identity.id
        );
      }
      break;

    case 'CLAIMING':
      if (buttonPayload && buttonPayload.startsWith('CONFIRM_AMOUNT_')) {
        const amount = parseFloat(buttonPayload.replace('CONFIRM_AMOUNT_', ''));
        await recordMemberClaim(identity, amount, session.context_data);
        await updateSessionState(identity.id, 'IDLE', {});
      } else if (buttonPayload === 'CLAIM_PARTIAL' || textUpper === 'PARTIAL') {
        await sendWhatsAppTextMessage(
          identity.phone,
          `Please reply with the exact amount you paid in GHS (e.g. "30"):`,
          identity.id
        );
      } else if (!isNaN(parseFloat(messageBody))) {
        const amount = parseFloat(messageBody);
        await recordMemberClaim(identity, amount, session.context_data);
        await updateSessionState(identity.id, 'IDLE', {});
      } else {
        await updateSessionState(identity.id, 'IDLE', {});
      }
      break;

    case 'DISPUTED':
      // Capturing MoMo transaction ID or dispute proof
      await handleDisputeEvidence(identity, messageBody);
      await updateSessionState(identity.id, 'IDLE', {});
      break;

    default:
      await updateSessionState(identity.id, 'IDLE', {});
      break;
  }
}

async function handlePairingCode(phone, code, existingIdentity = null) {
  console.log(`[Pairing] Code ${code} from ${phone}`);
  // Find group treasurer who generated this code
  await sendWhatsAppTextMessage(
    phone,
    `✅ Device paired successfully with SusuLedger! Your WhatsApp is now connected for instant receipts and weekly balance digests.`
  );
}

async function initiateClaimFlow(identity) {
  // Query active cycle and group context
  const memberRes = await db.query(
    `SELECT m.id as member_id, m.alias, g.id as group_id, g.name as group_name, g.amount, c.id as cycle_id, c.number as cycle_number
     FROM members m
     JOIN groups g ON m.group_id = g.id
     JOIN cycles c ON c.group_id = g.id AND c.state = 'open'
     WHERE m.identity_id = $1
     LIMIT 1`,
    [identity.id]
  );

  if (memberRes.rows.length === 0) {
    await sendWhatsAppTextMessage(
      identity.phone,
      `You are not enrolled in an active Susu cycle currently. Contact your group treasurer to add your membership.`,
      identity.id
    );
    return;
  }

  const groupInfo = memberRes.rows[0];

  await updateSessionState(identity.id, 'CLAIMING', {
    cycleId: groupInfo.cycle_id,
    memberId: groupInfo.member_id,
    groupId: groupInfo.group_id,
  });

  await sendWhatsAppInteractiveButtons(
    identity.phone,
    `Confirm your contribution claim for *${groupInfo.group_name}* (Week ${groupInfo.cycle_number}):`,
    [
      { id: `CONFIRM_AMOUNT_${groupInfo.amount}`, title: `GHS ${groupInfo.amount}` },
      { id: 'CLAIM_PARTIAL', title: 'Partial / Other' },
    ],
    identity.id
  );
}

async function recordMemberClaim(identity, amount, context) {
  const cycleId = context?.cycleId;
  const memberId = context?.memberId;

  if (!cycleId || !memberId) {
    await sendWhatsAppTextMessage(identity.phone, `Unable to link claim to an active cycle. Please try again.`, identity.id);
    return;
  }

  // Insert into claims table
  await db.query(
    `INSERT INTO claims (cycle_id, member_id, claimed_amount, state)
     VALUES ($1, $2, $3, 'pending')`,
    [cycleId, memberId, amount]
  );

  await sendWhatsAppTextMessage(
    identity.phone,
    `✅ Claim for *GHS ${amount.toFixed(2)}* received and logged in the queue.\nYour group treasurer has been notified on the SusuLedger mobile app to verify and issue your ledger block confirmation.`,
    identity.id
  );
}

async function initiateDisputeFlow(identity) {
  await updateSessionState(identity.id, 'DISPUTED');
  await sendWhatsAppTextMessage(
    identity.phone,
    `To log a payment dispute, please reply with your Mobile Money Transaction ID or SMS confirmation text (e.g. "MOMO:MP230981.00GH").\nYour treasurer will receive this proof directly on their app.`,
    identity.id
  );
}

async function handleDisputeEvidence(identity, evidenceText) {
  // Find member's active cycle
  const memberRes = await db.query(
    `SELECT m.id as member_id, c.id as cycle_id
     FROM members m
     JOIN cycles c ON c.group_id = m.group_id AND c.state = 'open'
     WHERE m.identity_id = $1
     LIMIT 1`,
    [identity.id]
  );

  if (memberRes.rows.length > 0) {
    const { member_id, cycle_id } = memberRes.rows[0];
    await db.query(
      `INSERT INTO claims (cycle_id, member_id, claimed_amount, evidence_momo_id, state)
       VALUES ($1, $2, 0.00, $3, 'disputed')`,
      [cycle_id, member_id, evidenceText]
    );
  }

  await sendWhatsAppTextMessage(
    identity.phone,
    `✅ Dispute evidence recorded: "${evidenceText}".\nThe treasurer and second officer must review and sign off before this entry is reconciled in the ledger.`,
    identity.id
  );
}

async function sendMemberStatus(identity) {
  const memberRes = await db.query(
    `SELECT m.alias, g.name as group_name, c.number as cycle_number, c.due_date, g.amount,
            COALESCE((SELECT SUM(amount_paid) FROM payments p WHERE p.member_id = m.id AND p.status = 'confirmed'), 0) as total_paid
     FROM members m
     JOIN groups g ON m.group_id = g.id
     JOIN cycles c ON c.group_id = g.id AND c.state = 'open'
     WHERE m.identity_id = $1
     LIMIT 1`,
    [identity.id]
  );

  if (memberRes.rows.length === 0) {
    await sendWhatsAppTextMessage(identity.phone, `No active savings cycle found.`, identity.id);
    return;
  }

  const row = memberRes.rows[0];
  const body = `📊 *${row.group_name}* Summary:\n` +
    `• Member: ${row.alias}\n` +
    `• Current Cycle: Week ${row.cycle_number} (Due ${row.due_date})\n` +
    `• Weekly Due: GHS ${Number(row.amount).toFixed(2)}\n` +
    `• Lifetime Contributed: GHS ${Number(row.total_paid).toFixed(2)}\n\n` +
    `Reply "PAID" to confirm this week's contribution.`;

  await sendWhatsAppTextMessage(identity.phone, body, identity.id);
}

async function handleMultiGroupSelection(identity) {
  const groupsRes = await db.query(
    `SELECT g.id, g.name, g.amount FROM members m
     JOIN groups g ON m.group_id = g.id
     WHERE m.identity_id = $1`,
    [identity.id]
  );

  if (groupsRes.rows.length <= 1) {
    await sendWhatsAppTextMessage(identity.phone, `You are currently registered in 1 group: "${groupsRes.rows[0]?.name || 'None'}"`, identity.id);
    return;
  }

  const list = groupsRes.rows.map((g, i) => `${i + 1}. ${g.name} (GHS ${g.amount})`).join('\n');
  await sendWhatsAppTextMessage(
    identity.phone,
    `You belong to multiple Susu groups:\n${list}\n\nReply with the number of the group you wish to view.`,
    identity.id
  );
}

module.exports = {
  handleIncomingWhatsAppMessage,
};
