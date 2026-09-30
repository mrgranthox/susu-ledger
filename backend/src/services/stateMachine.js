const db = require('../config/database');
const { consumePairing } = require('./pairingService');
const { pushToTreasurer } = require('./fcmService');
const whatsapp = require('./whatsappService');
const sendWhatsAppInteractiveMessage = (...args) => whatsapp.sendWhatsAppInteractiveMessage(...args);
const sendWhatsAppTextMessage = (...args) => whatsapp.sendWhatsAppTextMessage(...args);
const logMessage = (...args) => whatsapp.logMessage(...args);

function normalizePhone(rawPhone) {
  const cleanPhone = String(rawPhone || '').replace(/[^0-9]/g, '');
  const formattedPhone = cleanPhone.startsWith('233')
    ? `+${cleanPhone}`
    : `+233${cleanPhone.replace(/^0/, '')}`;

  return { cleanPhone, formattedPhone };
}

function parsePairingCommand(text) {
  const value = String(text || '').trim().toUpperCase();
  const prefixed = /^PAIR(?:\s*:\s*|\s+)(.*)$/.exec(value);
  if (prefixed) return prefixed[1].trim();
  if (value === 'PAIR') return '';
  return /^[A-Z0-9]{3}-[A-Z0-9]{3}$/.test(value) ? value : null;
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
    `SELECT DISTINCT ON (g.id)
       COALESCE(m.id::text, 'officer-' || g.id::text) AS member_id,
       COALESCE(m.alias, i.display_name, 'Officer') AS alias,
       g.id AS group_id,
       g.name AS group_name,
       g.amount AS group_amount,
       g.currency,
       g.schedule,
       g.treasurer_id,
       g.officer_id,
       g.state AS group_state,
       CASE
         WHEN g.treasurer_id = $1 THEN 'Treasurer'
         WHEN g.officer_id = $1 THEN 'Second Officer'
         ELSE 'Member'
       END AS user_role,
       c.id AS cycle_id,
       c.number AS cycle_number,
       c.amount_due
     FROM groups g
     LEFT JOIN members m ON m.group_id = g.id AND m.identity_id = $1 AND m.state = 'active'
     LEFT JOIN identities i ON i.id = $1
     LEFT JOIN LATERAL (
       SELECT *
       FROM cycles
       WHERE group_id = g.id AND state = 'open'
       ORDER BY number DESC
       LIMIT 1
     ) c ON TRUE
     WHERE (m.id IS NOT NULL OR g.treasurer_id = $1 OR g.officer_id = $1)
       AND g.state != 'deleted'
     ORDER BY g.id, g.created_at ASC`,
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

async function broadcastWelcomeToGroupMembers(groupId) {
  try {
    const groupRes = await db.query(
      `SELECT g.id, g.name AS group_name, g.amount, g.schedule, i.display_name AS treasurer_name, i.phone AS treasurer_phone
       FROM groups g
       JOIN identities i ON i.id = g.treasurer_id
       WHERE g.id = $1`,
      [groupId]
    );
    const group = groupRes.rows[0];
    if (!group) return 0;

    const membersRes = await db.query(
      `SELECT m.id AS member_id, m.alias, i.id AS identity_id, i.phone, i.display_name
       FROM members m
       JOIN identities i ON i.id = m.identity_id
       WHERE m.group_id = $1 AND m.state = 'active'
         AND NOT EXISTS (
           SELECT 1 FROM message_log l
           WHERE l.identity_id = i.id AND l.direction = 'OUT'
             AND l.body LIKE '%Welcome to%'
             AND l.created_at > clock_timestamp() - interval '24 hours'
         )`,
      [groupId]
    );

    console.log(`[WhatsApp Broadcast] Delivering welcome messages to ${membersRes.rows.length} members of group "${group.group_name}"`);
    let sentCount = 0;

    for (const member of membersRes.rows) {
      if (!member.phone) continue;
      const memberName = member.alias || member.display_name || 'Member';
      const welcomeText =
        `👋 *Welcome to ${group.group_name}!*\n\n` +
        `Hello ${memberName},\n\n` +
        `You were added to the Susu group *${group.group_name}* by your treasurer *${group.treasurer_name || 'Treasurer'}* (${group.treasurer_phone}).\n\n` +
        `I am the official *SusuLedger WhatsApp Assistant*. You can use me to track your weekly contributions, check your verified savings balance, and record payment claims directly on WhatsApp!\n\n` +
        `📌 *Group Details*:\n` +
        `• Contribution: GHS ${Number(group.amount || 0).toFixed(2)} (${group.schedule || 'weekly'})\n` +
        `• Treasurer: ${group.treasurer_name || 'Treasurer'} (${group.treasurer_phone})\n\n` +
        `📲 *Quick Commands*:\n` +
        `• Reply *PAID* after sending your contribution\n` +
        `• Reply *BALANCE* to inspect your savings record\n` +
        `• Reply *PROGRESS* to view weekly cycle status\n` +
        `• Reply *GROUPS* to switch groups\n` +
        `• Reply *MENU* anytime for assistance\n\n` +
        `👉 Reply *OK* or *CONFIRM* to confirm your membership, or *REJECT* to decline.`;

      try {
        await sendWhatsAppTextMessage(member.phone, welcomeText, member.identity_id);
        await logMessage({
          identityId: member.identity_id,
          phone: member.phone,
          direction: 'OUT',
          body: welcomeText,
          metaStatus: 'sent',
        });
        sentCount++;
      } catch (sendErr) {
        console.error(`[WhatsApp Broadcast Error] Failed to send welcome to ${member.phone}:`, sendErr.message);
      }
    }
    return sentCount;
  } catch (err) {
    console.error('[WhatsApp Broadcast Error]', err.message);
    return 0;
  }
}

async function promptForGroup(fromPhone, identityId, memberships) {
  const lines = memberships.map((m, index) => {
    const roleBadge = m.user_role ? ` [${m.user_role}]` : '';
    const amount = Number(m.amount_due || m.group_amount || 0).toFixed(2);
    return `${index + 1}. *${m.group_name}*${roleBadge} • GHS ${amount} (${m.schedule || 'weekly'})`;
  });

  await saveBotSession(identityId, {
    currentState: 'WAITING_GROUP_SELECTION',
    contextData: { groupIds: memberships.map((m) => m.group_id) },
  });

  await sendWhatsAppTextMessage(
    fromPhone,
    `📂 *Your Susu Groups* (${memberships.length} available):\n\n${lines.join('\n')}\n\nReply with the group number (e.g. *1* or *2*) to set your active group.`,
    identityId
  );
}

async function promptForAmount(fromPhone, identityId, membership) {
  const paid = membership.cycle_id ? (await db.query("SELECT COALESCE(SUM(amount_paid),0) AS total FROM payments WHERE cycle_id=$1 AND member_id=$2 AND status='confirmed'",[membership.cycle_id,membership.member_id])).rows[0].total : 0;
  const amountDue = Math.max(0, Number(membership.amount_due || membership.group_amount || 0) - Number(paid));
  if (membership.cycle_id && amountDue === 0) {
    await sendWhatsAppTextMessage(fromPhone, `Week ${membership.cycle_number} is already fully paid. Reply PAID WEEK followed by the week number to choose another week.`, identityId);
    return;
  }
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

  try {
    await pushToTreasurer(membership.treasurer_id, {
      title: `New Claim — ${membership.group_name}`,
      body: `${memberName} claims GHS ${Number(amount).toFixed(2)} for Week ${membership.cycle_number || 1}`,
      data: { type: 'CLAIM_SUBMITTED', groupId: membership.group_id },
    });
  } catch (pushErr) {
    console.warn('[Treasurer FCM Push Error]', pushErr.message);
  }
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
  const cleanText = String(messageBody || buttonPayload || '').trim().toUpperCase();

  await logMessage({
    identityId,
    phone: formattedPhone || cleanPhone || fromPhone,
    direction: 'IN',
    body: messageBody || buttonPayload || '',
    metaStatus: 'received',
  });

  const rawCode = parsePairingCommand(cleanText);
  if (rawCode !== null) {
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
          `✅ *SusuLedger Group Paired Successfully!*\n\nPairing Code: ${rawCode}\nPhone: ${formattedPhone}\nYour group is now live on the WhatsApp Bot Engine.\n\nBroadcasting welcome messages and instructions to all registered members...`,
          identityId
        );
        await broadcastWelcomeToGroupMembers(pairSession.group_id);
        return;
      }

      // Provide specific, actionable feedback based on pairing state
      const codeRecord = (await db.query(
        "SELECT bp.*, g.name AS group_name FROM bot_pairings bp LEFT JOIN groups g ON g.id = bp.group_id WHERE bp.code = $1",
        [rawCode]
      )).rows[0];

      if (!codeRecord) {
        await sendWhatsAppTextMessage(
          fromPhone,
          `Invalid pairing code "${rawCode}".\n\nPlease check the code displayed in your SusuLedger app under Settings & More > Connect WhatsApp and try again (e.g. PAIR: ${rawCode}).`,
          identityId
        );
        return;
      }

      if (codeRecord.status === 'PAIRED') {
        await sendWhatsAppTextMessage(
          fromPhone,
          `This SusuLedger group is already connected!\n\nGroup: ${codeRecord.group_name || 'Susu Group'}\nStatus: CONNECTED & ACTIVE\n\nYour WhatsApp bot is currently active. Reply MENU or HELP to start recording payments and viewing balances.`,
          identityId
        );
        return;
      }

      if (new Date(codeRecord.expires_at) <= new Date()) {
        await sendWhatsAppTextMessage(
          fromPhone,
          `This pairing code (${rawCode}) has expired (codes are valid for 15 minutes).\n\nPlease open SusuLedger, go to Settings & More > Connect WhatsApp to generate a new code, and send it here.`,
          identityId
        );
        return;
      }

      if (codeRecord.phone !== formattedPhone) {
        await sendWhatsAppTextMessage(
          fromPhone,
          `Phone Number Mismatch:\nThis pairing code was created for registered number ${codeRecord.phone}, but you are messaging from ${formattedPhone}.\n\nPlease send the code from the registered phone number in SusuLedger, or re-generate a code with this number.`,
          identityId
        );
        return;
      }

      await sendWhatsAppTextMessage(
        fromPhone,
        `Unable to pair with code ${rawCode}. Please generate a fresh pairing code in your SusuLedger app and try again.`,
        identityId
      );
      return;
    }
    await sendWhatsAppTextMessage(fromPhone, 'Send the pairing code shown in SusuLedger, for example PAIR:ABC-DEF, from the same number you verified by SMS.', identityId);
    return;
  }

  if (cleanText === 'DISCONNECT') {
    const result=await db.query("UPDATE bot_pairings SET status='DISCONNECTED' WHERE status='PAIRED' AND group_id IN (SELECT id FROM groups WHERE treasurer_id=$1)",[identityId]);
    await sendWhatsAppTextMessage(fromPhone,result.rowCount ? 'Your group bot is disconnected. Reconnect from SusuLedger to resume member commands.' : 'Only the verified treasurer can disconnect the group bot.',identityId);
    return;
  }
  const allMemberships = await getActiveMemberships(identityId);
  const connected=await db.query("SELECT DISTINCT group_id FROM bot_pairings WHERE status='PAIRED'");
  const memberships = allMemberships.filter(m=>connected.rows.some(p=>p.group_id===m.group_id));
  const session = await getBotSession(identityId);

  if (cleanText === 'OK' || cleanText === 'CONFIRM') {
    await db.query('UPDATE identities SET dpc_consent_granted=true, dpc_consent_timestamp=clock_timestamp() WHERE id=$1', [identityId]);
    const groupName = memberships[0]?.group_name || allMemberships[0]?.group_name || 'your Susu group';
    await sendWhatsAppTextMessage(
      fromPhone,
      `✅ *Membership Confirmed!*\n\nThank you for confirming your membership in *${groupName}*.\n\nYou will receive automated alerts when weekly contributions are due. Reply *MENU* anytime to check balances or submit payment claims.`,
      identityId
    );
    return;
  }

  if (cleanText === 'REJECT' || cleanText === 'DECLINE') {
    await db.query('UPDATE identities SET dpc_consent_granted=false, dpc_consent_timestamp=clock_timestamp() WHERE id=$1', [identityId]);
    const groupName = memberships[0]?.group_name || allMemberships[0]?.group_name || 'the Susu group';
    await sendWhatsAppTextMessage(
      fromPhone,
      `You have declined participation in *${groupName}*.\n\nPlease contact your treasurer if this was done in error. You will not receive contribution notifications.`,
      identityId
    );
    const targetTreasurerId = memberships[0]?.treasurer_id || allMemberships[0]?.treasurer_id;
    if (targetTreasurerId) {
      const treasurerRes = await db.query('SELECT phone, display_name FROM identities WHERE id = $1', [targetTreasurerId]);
      if (treasurerRes.rows[0]?.phone) {
        await sendWhatsAppTextMessage(
          treasurerRes.rows[0].phone,
          `⚠️ *MEMBER NOTICE*: ${identity.display_name || fromPhone} has declined participation in ${groupName} on WhatsApp.`,
          identityId
        );
      }
    }
    return;
  }

  if (cleanText === 'STOP') {
    await db.query('UPDATE identities SET dpc_consent_granted=false,dpc_consent_timestamp=clock_timestamp() WHERE id=$1',[identityId]);
    await sendWhatsAppTextMessage(fromPhone,'Group reminders stopped. Reply START to subscribe again.',identityId);
    return;
  }

  if (cleanText === 'START') {
    await db.query('UPDATE identities SET dpc_consent_granted=true,dpc_consent_timestamp=clock_timestamp() WHERE id=$1',[identityId]);
    await sendWhatsAppTextMessage(fromPhone,'Group reminders enabled. Reply STOP to unsubscribe.',identityId);
    return;
  }

  // Group Switching / Accounts Command
  const isSwitchCmd = cleanText === 'SWITCH' || cleanText === 'GROUPS' || cleanText === 'ACCOUNTS';
  const switchTargetMatch = /^SWITCH\s+(\d+)$/.exec(cleanText);

  if (isSwitchCmd) {
    if (memberships.length <= 1) {
      const onlyGroup = memberships[0];
      await sendWhatsAppTextMessage(
        fromPhone,
        `📂 You belong to 1 active group: *${onlyGroup ? onlyGroup.group_name : 'No active group'}* (${onlyGroup?.user_role || 'Member'}).\n\nReply *MENU* to view commands.`,
        identityId
      );
      return;
    }
    await promptForGroup(fromPhone, identityId, memberships);
    return;
  }

  if (switchTargetMatch) {
    const idx = Number(switchTargetMatch[1]) - 1;
    if (memberships[idx]) {
      const chosen = memberships[idx];
      await saveBotSession(identityId, {
        currentState: 'IDLE',
        selectedGroupId: chosen.group_id,
        contextData: { group_id: chosen.group_id, group_name: chosen.group_name },
      });
      await sendWhatsAppTextMessage(
        fromPhone,
        `✅ Active group switched to *${chosen.group_name}* (${chosen.user_role || 'Member'})!\n\n` +
          `• Reply *BALANCE* for your verified savings\n` +
          `• Reply *PAID* to record a contribution\n` +
          `• Reply *PROGRESS* for cycle status\n` +
          `• Reply *GROUPS* to switch groups`,
        identityId
      );
      return;
    }
  }

  if (session?.current_state === 'WAITING_GROUP_SELECTION' && /^[1-9]$/.test(cleanText)) {
    const idx = Number(cleanText) - 1;
    if (memberships[idx]) {
      const chosen = memberships[idx];
      await saveBotSession(identityId, {
        currentState: 'IDLE',
        selectedGroupId: chosen.group_id,
        contextData: { group_id: chosen.group_id, group_name: chosen.group_name },
      });
      await sendWhatsAppTextMessage(
        fromPhone,
        `✅ Active group set to *${chosen.group_name}* (${chosen.user_role || 'Member'})!\n\n` +
          `Commands for *${chosen.group_name}*:\n` +
          `• Reply *BALANCE* for your verified savings\n` +
          `• Reply *PAID* to submit a payment claim\n` +
          `• Reply *DUE* for contribution details\n` +
          `• Reply *PROGRESS* for cycle status\n` +
          `• Reply *GROUPS* to switch groups`,
        identityId
      );
      return;
    }
  }

  const selectedMembership = findMembershipSelection(cleanText, buttonPayload, memberships, session);

  if (cleanText === 'HELP' || cleanText === 'MENU' || cleanText === 'COMMANDS' || !cleanText) {
    const activeGroupName = selectedMembership?.group_name || memberships[0]?.group_name || 'Susu Group';
    const activeRole = selectedMembership?.user_role || memberships[0]?.user_role || 'Member';
    const groupCountNote = memberships.length > 1 ? `\n📌 Active: *${activeGroupName}* (${activeRole} • 1 of ${memberships.length} groups)\n` : `\n📌 Group: *${activeGroupName}*\n`;

    await sendWhatsAppTextMessage(
      fromPhone,
      `*SusuLedger Assistant*${groupCountNote}\n` +
        `• *PAID* - Submit a contribution claim\n` +
        `• *PAID WEEK 1* - Settle an earlier week\n` +
        `• *BALANCE* - Check your verified savings\n` +
        `• *PROGRESS* - View current cycle status\n` +
        `• *DUE* - Contribution deadline details\n` +
        (memberships.length > 1 ? `• *GROUPS* - Switch between your ${memberships.length} groups\n` : '') +
        `• *START / STOP* - Manage reminders\n` +
        `• *PAIR:CODE* - Pair your mobile app instance`,
      identityId
    );
    return;
  }

  if (memberships.length === 0) {
    await sendWhatsAppTextMessage(
      fromPhone,
      allMemberships.length ? 'Your group WhatsApp bot is disconnected. Ask your treasurer to reconnect it in SusuLedger, then retry. No payment claim has been recorded.' : 'This WhatsApp number is not attached to an active group. Ask your treasurer to add your number in SusuLedger.',
      identityId
    );
    return;
  }

  const weekRequest = /^PAID\s+WEEK\s+(\d+)$/.exec(cleanText);
  const isClaimStart = cleanText === 'PAID' || Boolean(weekRequest) || buttonPayload === 'CLAIM_PAID';
  const isAmountConfirmation = buttonPayload?.startsWith('CONFIRM_AMOUNT_') || cleanText.startsWith('CONFIRM_AMOUNT_');
  const isCustomClaim = buttonPayload === 'CLAIM_CUSTOM';
  const hasMomoEvidence = cleanText.startsWith('MOMO:') || cleanText.includes('MOMO');
  if (selectedMembership && (weekRequest || (!isClaimStart && session?.context_data?.cycleId))) {
    const target = weekRequest
      ? await db.query('SELECT * FROM cycles WHERE group_id=$1 AND number=$2',[selectedMembership.group_id,Number(weekRequest[1])])
      : await db.query('SELECT * FROM cycles WHERE group_id=$1 AND id=$2',[selectedMembership.group_id,session.context_data.cycleId]);
    if (!target.rows.length) {
      await sendWhatsAppTextMessage(fromPhone, 'That week was not found. Reply PAID for the current week or PAID WEEK followed by an existing week number.',identityId);
      return;
    }
    Object.assign(selectedMembership,{cycle_id:target.rows[0].id,cycle_number:target.rows[0].number,amount_due:target.rows[0].amount_due});
  }
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
    const lines = [];
    for (const m of memberships) {
      if (m.user_role === 'Treasurer' || m.user_role === 'Second Officer') {
        const poolRes = await db.query(
          "SELECT COALESCE(SUM(amount_paid), 0) AS total FROM payments WHERE group_id = $1 AND status = 'confirmed'",
          [m.group_id]
        );
        const total = Number(poolRes.rows[0]?.total || 0).toFixed(2);
        lines.push(`🏛️ *${m.group_name}* [${m.user_role}]: GHS ${total} collected pool`);
      } else {
        const memberRes = await db.query(
          "SELECT COALESCE(SUM(p.amount_paid), 0) AS total FROM payments p WHERE p.member_id = $1 AND p.status = 'confirmed'",
          [m.member_id]
        );
        const total = Number(memberRes.rows[0]?.total || 0).toFixed(2);
        lines.push(`👤 *${m.group_name}*: GHS ${total} confirmed savings`);
      }
    }
    await sendWhatsAppTextMessage(
      fromPhone,
      `📊 *SusuLedger Savings Statement*\n\n${lines.join('\n')}\n\nReply *GROUPS* to switch active group.`,
      identityId
    );
    return;
  }

  if (cleanText === 'DUE') {
    const treasurer=(await db.query('SELECT display_name,phone FROM identities WHERE id=$1',[selectedMembership.treasurer_id])).rows[0];
    await sendWhatsAppTextMessage(fromPhone,`${selectedMembership.group_name}: GHS ${Number(selectedMembership.amount_due || selectedMembership.group_amount).toFixed(2)} due.\nContact your treasurer: ${treasurer?.display_name || 'Treasurer'} (${treasurer?.phone || 'In app'}).\nReply PAID after contributing.`,identityId);
    return;
  }

  if (cleanText === 'PROGRESS') {
    const lines = [];
    for (const membership of memberships) {
      const total=await db.query("SELECT COALESCE(SUM(amount_paid),0) AS total FROM payments WHERE cycle_id=$1 AND status='confirmed'",[membership.cycle_id]);
      lines.push(`${membership.group_name}: Week ${membership.cycle_number || 1}, GHS ${Number(total.rows[0]?.total || 0).toFixed(2)} collected`);
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

async function handleMediaMessage(fromPhone, mediaType, mediaId) {
  const { cleanPhone, formattedPhone } = normalizePhone(fromPhone);
  const identity = await resolveIdentity(fromPhone, formattedPhone);
  const session = await getBotSession(identity.id);

  await logMessage({
    identityId: identity.id,
    phone: formattedPhone || cleanPhone || fromPhone,
    direction: 'IN',
    body: `[${(mediaType || 'MEDIA').toUpperCase()}: ${mediaId || 'received'}]`,
    metaStatus: 'received',
  });

  if (session?.current_state === 'WAITING_CLAIM_AMOUNT' || session?.current_state === 'WAITING_CUSTOM_AMOUNT') {
    const ctxData = session.context_data || {};
    await saveBotSession(identity.id, {
      ...session,
      currentState: session.current_state,
      selectedGroupId: session.selected_group_id,
      contextData: { ...ctxData, evidenceMediaId: mediaId },
    });
    await sendWhatsAppTextMessage(
      fromPhone,
      `📎 Received your ${mediaType}! Your attachment has been recorded as payment evidence.\n\nPlease reply *PAID* or send the amount (e.g. *GHS 50*) to complete your claim, or add your MoMo reference: *MOMO:MP12345*`,
      identity.id
    );
  } else {
    await sendWhatsAppTextMessage(
      fromPhone,
      `Thanks for sending this ${mediaType}! If you are submitting a payment claim, please reply *PAID* with your contribution details or MoMo reference.\n\nReply *MENU* for all commands.`,
      identity.id
    );
  }
}

module.exports = {
  handleIncomingWhatsAppMessage,
  handleMediaMessage,
  broadcastWelcomeToGroupMembers,
  getActiveMemberships,
  parseAmount,
  parsePairingCommand,
};
