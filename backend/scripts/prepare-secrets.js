// Migrate existing Cloud Run credentials without printing their values.
const { execFileSync } = require('node:child_process');
const project = process.env.GCP_PROJECT_ID || 'susu-ledger-c3daa';
const run = (args, options = {}) => execFileSync('gcloud', [...args, `--project=${project}`], { encoding: 'utf8', ...options });
const service = JSON.parse(run(['run','services','describe','susu-backend','--region=africa-south1','--format=json']));
const env = Object.fromEntries(service.spec.template.spec.containers[0].env.filter(item => item.value).map(item => [item.name,item.value]));
const secrets = ['DB_PASSWORD','PAYSTACK_SECRET_KEY','PAYSTACK_PUBLIC_KEY','SECRET_SALT','META_WEBHOOK_VERIFY_TOKEN'];
if (!env.DB_PASSWORD && env.DATABASE_URL) {
  const credentials = env.DATABASE_URL.slice(env.DATABASE_URL.indexOf('://') + 3, env.DATABASE_URL.lastIndexOf('@'));
  env.DB_PASSWORD = credentials.slice(credentials.indexOf(':') + 1);
}
for (const name of secrets) {
  try {
    run(['secrets','describe',name], { stdio:'ignore' });
    console.log(`${name}: already exists`);
    continue;
  } catch (_) { /* Create only missing secrets; never overwrite a rotated value. */ }
  if (!env[name]) throw new Error(`${name} must be provided securely`);
  run(['secrets','create',name,'--replication-policy=automatic'], {stdio:'ignore'});
  run(['secrets','versions','add',name,'--data-file=-'], {input:env[name],stdio:['pipe','ignore','ignore']});
  console.log(`${name}: migrated`);
}
for (const name of [...secrets,'META_WHATSAPP_ACCESS_TOKEN','META_APP_SECRET']) {
  try {
    run(['secrets','describe',name], {stdio:'ignore'});
  } catch (_) { console.log(`${name}: not configured`); continue; }
  run(['secrets','add-iam-policy-binding',name,`--member=serviceAccount:${service.spec.template.spec.serviceAccountName}`,'--role=roles/secretmanager.secretAccessor','--quiet'], {stdio:'ignore'});
  console.log(`${name}: runtime access granted`);
}
