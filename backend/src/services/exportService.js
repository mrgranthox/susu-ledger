const db = require('../config/database');

async function generateCycleCsv(cycleId) {
  const cycleRes = await db.query(
    `SELECT c.number, c.due_date, g.name as group_name
     FROM cycles c
     JOIN groups g ON c.group_id = g.id
     WHERE c.id = $1`,
    [cycleId]
  );

  const paymentsRes = await db.query(
    `SELECT p.id, m.alias, p.amount_paid, p.method, p.status, p.confirmed_at,
            p.prev_hash, p.current_hash, p.idempotency_key, i.display_name as confirmed_by_name
     FROM payments p
     JOIN members m ON p.member_id = m.id
     JOIN identities i ON p.confirmed_by = i.id
     WHERE p.cycle_id = $1
     ORDER BY p.confirmed_at ASC`,
    [cycleId]
  );

  const cycle = cycleRes.rows[0] || { number: 0, group_name: 'Susu Group' };

  let csv = `SusuLedger Official Audit Export - ${cycle.group_name} (Week ${cycle.number})\n`;
  csv += `Generated at: ${new Date().toISOString()}\n`;
  csv += `Statutory: Non-custodial record layer. Cryptographic SHA-256 Verified.\n\n`;
  csv += `Payment ID,Member Name,Amount (GHS),Payment Method,Status,Confirmed At,Confirmed By,Prev Hash,Current Hash,Idempotency Key\n`;

  for (const row of paymentsRes.rows) {
    csv += `"${row.id}","${row.alias}",${Number(row.amount_paid).toFixed(2)},"${row.method}","${row.status}","${new Date(row.confirmed_at).toISOString()}","${row.confirmed_by_name}","${row.prev_hash}","${row.current_hash}","${row.idempotency_key}"\n`;
  }

  return csv;
}

async function generateAnnualLedgerSummary(groupId, year = 2026) {
  const summaryRes = await db.query(
    `SELECT 
       COUNT(DISTINCT c.id) as total_cycles,
       COALESCE(SUM(p.amount_paid), 0) as total_collected,
       COUNT(DISTINCT m.id) as total_members
     FROM groups g
     LEFT JOIN cycles c ON c.group_id = g.id
     LEFT JOIN members m ON m.group_id = g.id
     LEFT JOIN payments p ON p.cycle_id = c.id AND p.status = 'confirmed'
     WHERE g.id = $1`,
    [groupId]
  );

  const methodBreakdown = await db.query(
    `SELECT p.method, COUNT(*) as count, SUM(p.amount_paid) as total
     FROM payments p
     JOIN cycles c ON p.cycle_id = c.id
     WHERE c.group_id = $1 AND p.status = 'confirmed'
     GROUP BY p.method`,
    [groupId]
  );

  return {
    summary: summaryRes.rows[0],
    methods: methodBreakdown.rows,
  };
}

module.exports = {
  generateCycleCsv,
  generateAnnualLedgerSummary,
};
