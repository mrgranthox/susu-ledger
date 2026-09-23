const fs = require('node:fs');
const path = require('node:path');
const db = require('../src/config/database');

(async () => {
  const client = await db.pool.connect();
  try {
    await client.query('BEGIN');
    await client.query("SELECT pg_advisory_xact_lock(hashtext('susu-schema-migration'))");
    const existing = await client.query("SELECT to_regclass('public.groups') AS table_name");
    if (!existing.rows[0].table_name) {
      await client.query(fs.readFileSync(path.resolve(__dirname,'../../database/01_schema.sql'),'utf8'));
    }
    await client.query(fs.readFileSync(path.resolve(__dirname,'../../database/02_pairings.sql'),'utf8'));
    await client.query(fs.readFileSync(path.resolve(__dirname,'../../database/03_message_log_phone.sql'),'utf8'));
    await client.query(fs.readFileSync(path.resolve(__dirname,'../../database/04_bot_sessions.sql'),'utf8'));
    await client.query('COMMIT');
    console.log('Database schema is ready');
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally { client.release(); }
})().catch(error => {
  console.error('Migration failed:', error.code || error.message);
  process.exitCode = 1;
}).finally(() => db.pool.end());
