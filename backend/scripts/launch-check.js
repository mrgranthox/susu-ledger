const { execFileSync } = require('node:child_process');
const { createHmac } = require('node:crypto');
require('node:dns').setDefaultResultOrder('ipv4first');
const project=process.env.GCP_PROJECT_ID || 'susu-ledger-c3daa';
const region=process.env.GCP_REGION || 'africa-south1';
const serviceName=process.env.CLOUD_RUN_SERVICE_NAME || 'susu-backend';
let failures=0;
function check(condition,label) { console.log(`${condition ? 'PASS' : 'FAIL'} ${label}`); if(!condition) failures++; }
function gcloud(args) { return execFileSync('gcloud',[...args,`--project=${project}`],{encoding:'utf8',timeout:60000,stdio:['ignore','pipe','pipe']}); }
function secret(name) { return gcloud(['secrets','versions','access','latest',`--secret=${name}`]).trim(); }
async function request(url,options={}) {
  for(let attempt=0;attempt<2;attempt++) {
    try { return await fetch(url,{...options,signal:AbortSignal.timeout(25000)}); }
    catch(error) { if(attempt===1) throw new Error(`Request timed out or failed: ${new URL(url).origin}${new URL(url).pathname}`); }
  }
}

(async()=>{
  const service=JSON.parse(gcloud(['run','services','describe',serviceName,`--region=${region}`,'--format=json']));
  const candidate=process.argv.includes('--candidate');
  const target=candidate ? service.status.traffic.find(item=>item.tag==='candidate') : service.status.traffic.find(item=>item.percent===100);
  if(!target) throw new Error('Expected target revision is not configured');
  const revision=JSON.parse(gcloud(['run','revisions','describe',target.revisionName,`--region=${region}`,'--format=json']));
  const env=revision.spec.containers[0].env || [];
  const sensitive=['DATABASE_URL','DB_PASSWORD','META_WHATSAPP_ACCESS_TOKEN','WHATSAPP_TOKEN','META_APP_SECRET','META_WEBHOOK_VERIFY_TOKEN','PAYSTACK_SECRET_KEY','SECRET_SALT'];
  check(!env.some(item=>sensitive.includes(item.name) && item.value),'target revision uses secret references');
  const base=candidate ? target.url : service.status.url;
  console.log(`Checking ${target.revisionName}: ${base}`);
  const statusResponse=await request(`${base}/api/app/status`);
  const status=await statusResponse.json();
  check(statusResponse.ok && status.database?.status==='healthy','Cloud SQL schema is reachable');
  const denied=await request(`${base}/api/app/sync`,{method:'POST',headers:{'content-type':'application/json'},body:'{}'});
  check(denied.status===401,'anonymous sync is denied');
  const unsigned=await request(`${base}/webhooks/whatsapp`,{method:'POST',headers:{'content-type':'application/json'},body:'{}'});
  check(unsigned.status===401,'unsigned webhook is denied');
  const appSecret=secret('META_APP_SECRET');
  const verifyToken=secret('META_WEBHOOK_VERIFY_TOKEN');
  const token=secret('META_WHATSAPP_ACCESS_TOKEN');
  const challengeUrl=new URL(`${base}/webhooks/whatsapp`);
  challengeUrl.search=new URLSearchParams({'hub.mode':'subscribe','hub.verify_token':verifyToken,'hub.challenge':'launchcheck'}).toString();
  const verification=await request(challengeUrl);
  check(verification.ok && await verification.text()==='launchcheck','webhook challenge succeeds');
  const body=JSON.stringify({object:'whatsapp_business_account',entry:[]});
  const signed=await request(`${base}/webhooks/whatsapp`,{method:'POST',headers:{'content-type':'application/json','x-hub-signature-256':`sha256=${createHmac('sha256',appSecret).update(body).digest('hex')}`},body});
  check(signed.ok,'webhook uses the current signing secret');
  const debug=await request(`https://graph.facebook.com/v23.0/debug_token?input_token=${encodeURIComponent(token)}`,{headers:{Authorization:`Bearer ${token}`}});
  const metadata=await debug.json();
  check(debug.ok && metadata.data?.is_valid,'Meta access token is valid');
  if(metadata.data?.app_id){
    const validSecret=await request(`https://graph.facebook.com/v23.0/debug_token?input_token=${encodeURIComponent(token)}`,{headers:{Authorization:`Bearer ${metadata.data.app_id}|${appSecret}`}});
    check(validSecret.ok,'Meta App Secret is valid');
  }
  const waba=env.find(item=>item.name==='META_WHATSAPP_BUSINESS_ACCOUNT_ID')?.value;
  if(waba){
    const response=await request(`https://graph.facebook.com/v23.0/${waba}/message_templates?fields=name,status&limit=100`,{headers:{Authorization:`Bearer ${token}`}});
    const templates=await response.json();
    for(const name of ['susu_friday_reminder','susu_payment_receipt','susu_sunday_summary']) {
      const state=templates.data?.find(item=>item.name===name)?.status || 'MISSING';
      check(state==='APPROVED',`${name}: ${state}`);
    }
  }else check(false,'Meta business account configured');
  console.log(`${failures} failed check(s). Real-device SMS and WhatsApp delivery still require acceptance testing.`);
  process.exitCode=failures ? 1 : 0;
})().catch(error=>{ console.error('FAIL launch check could not finish:',error.message.startsWith('Request timed out') ? error.message : 'check GCP access and connectivity'); process.exitCode=1; });
