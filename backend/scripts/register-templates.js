const { execFileSync } = require('node:child_process');
const project = process.env.GCP_PROJECT_ID || 'susu-ledger-c3daa';
const token = execFileSync('gcloud',['secrets','versions','access','latest','--secret=META_WHATSAPP_ACCESS_TOKEN',`--project=${project}`],{encoding:'utf8'});
const service = JSON.parse(execFileSync('gcloud',['run','services','describe','susu-backend','--region=africa-south1',`--project=${project}`,'--format=json'],{encoding:'utf8'}));
const waba = service.spec.template.spec.containers[0].env.find(item=>item.name==='META_WHATSAPP_BUSINESS_ACCOUNT_ID')?.value;
const templates = [
  {name:'susu_friday_reminder',text:'Hello {{1}}, your contribution of GHS {{2}} for {{3}} (Week {{4}}) is due. Reply PAID once sent.',examples:['Member','50.00','Savings Group','12']},
  {name:'susu_payment_receipt',text:'Your group treasurer has recorded your contribution of GHS {{1}} to your savings group {{2}}. This payment applies to contribution cycle {{3}} and was recorded using payment method {{4}}. The ledger reference for this record is {{5}}. Your confirming officer is {{6}}. Please keep this receipt for your records and contact your group treasurer if any of these details are incorrect. This receipt confirms a ledger entry, not a transfer of funds by SusuLedger.',examples:['50.00','Savings Group','12','Mobile Money','e3b0c442','Treasurer']},
  {name:'susu_sunday_summary',text:'Your group statement for {{1}} (Week {{2}}): GHS {{3}} collected, with {{4}} members fully paid ({{5}}%).',examples:['Savings Group','12','850.00','17/20','85']}
];
(async()=>{
  if (!waba) throw new Error('Business account ID is required');
  const url=`https://graph.facebook.com/v23.0/${waba}/message_templates`;
  const headers={Authorization:`Bearer ${token}`,'Content-Type':'application/json'};
  const existing=await fetch(`${url}?fields=name,status&limit=100`,{headers});
  if (!existing.ok) throw new Error('Unable to list Meta templates');
  const known=(await existing.json()).data;
  for(const template of templates){
    const found=known.find(item=>item.name===template.name);
    if(found){console.log(template.name,found.status);continue;}
    const response=await fetch(url,{method:'POST',headers,body:JSON.stringify({name:template.name,language:'en',category:'UTILITY',components:[{type:'BODY',text:template.text,example:{body_text:[template.examples]}}]})});
    const data=await response.json();
    console.log(template.name,JSON.stringify({httpStatus:response.status,status:data.status,errorCode:data.error?.code,errorTitle:data.error?.error_user_title,errorDetail:data.error?.error_user_msg}));
    if(!response.ok) process.exitCode=1;
  }
})().catch(error=>{console.error(error.message);process.exitCode=1});
