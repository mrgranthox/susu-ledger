const { Pool } = require('pg');
require('dotenv').config();

const connectionString = process.env.DATABASE_URL;

function resolvePoolConfig() {
  const cloudSqlConnectionName = process.env.CLOUD_SQL_CONNECTION_NAME || process.env.INSTANCE_CONNECTION_NAME;
  const host = process.env.DB_HOST || process.env.PGHOST || (cloudSqlConnectionName ? `/cloudsql/${cloudSqlConnectionName}` : null);
  const database = process.env.DB_NAME || process.env.PGDATABASE;
  const user = process.env.DB_USER || process.env.PGUSER;
  const password = process.env.DB_PASSWORD || process.env.PGPASSWORD;
  const port = Number(process.env.DB_PORT || process.env.PGPORT || 5432);

  if (host && database && user) {
    return {
      host,
      database,
      user,
      password,
      port,
      ssl: false,
    };
  }

  return {
    connectionString: connectionString || 'postgresql://postgres:secret@localhost:5432/susu_ledger',
    ssl: process.env.NODE_ENV === 'production' && !connectionString?.includes('localhost')
      ? { rejectUnauthorized: false }
      : false,
  };
}

const pool = new Pool({
  ...resolvePoolConfig(),
  max: 20,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 5000,
});

pool.on('error', (err) => {
  console.error('[DB] Unexpected error on idle PostgreSQL client:', err);
});

const dbInterface = {
  query: (text, params) => pool.query(text, params),
  pool,
};

module.exports = {
  ...dbInterface,
  db: dbInterface,
};
