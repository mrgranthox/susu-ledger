const crypto = require('crypto');

const GENESIS_HASH = '0000000000000000000000000000000000000000000000000000000000000000';

function sha256(input) {
  return crypto.createHash('sha256').update(input).digest('hex');
}

function generatePaymentHash({ id, cycleId, memberId, amountPaid, idempotencyKey, prevHash }) {
  const content = `${id}${cycleId}${memberId}${Number(amountPaid).toFixed(2)}${idempotencyKey}${prevHash}`;
  return sha256(content);
}

function hashPin(pin, salt) {
  return crypto.pbkdf2Sync(pin, salt, 10000, 64, 'sha512').toString('hex');
}

function verifyPin(pin, salt, storedHash) {
  const calculated = hashPin(pin, salt);
  return crypto.timingSafeEqual(Buffer.from(calculated), Buffer.from(storedHash));
}

function verifyLedgerChain(payments) {
  let isChainValid = true;
  let brokenIndex = null;
  let currentExpectedPrev = GENESIS_HASH;

  for (let i = 0; i < payments.length; i++) {
    const p = payments[i];
    if (p.prev_hash !== currentExpectedPrev) {
      isChainValid = false;
      brokenIndex = i;
      break;
    }

    const calculatedCurrent = generatePaymentHash({
      id: p.id,
      cycleId: p.cycle_id,
      memberId: p.member_id,
      amountPaid: p.amount_paid,
      idempotencyKey: p.idempotency_key,
      prevHash: p.prev_hash
    });

    if (p.current_hash !== calculatedCurrent) {
      isChainValid = false;
      brokenIndex = i;
      break;
    }

    currentExpectedPrev = p.current_hash;
  }

  return { isChainValid, brokenIndex, totalPayments: payments.length };
}

module.exports = {
  GENESIS_HASH,
  sha256,
  generatePaymentHash,
  hashPin,
  verifyPin,
  verifyLedgerChain,
};
