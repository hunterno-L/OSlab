// Read-only database checks plus one rejected LOCAL_BUILD submission in the isolated test database.
import {readFileSync, readdirSync} from 'node:fs';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import assert from 'node:assert/strict';
const temp=join(process.env.TEMP,'oslab-terra-acceptance');
const cfg=JSON.parse(readFileSync(join(temp,'verification-config.json'),'utf8'));
assert.match(cfg.database,/^oslab_terra_verify_\d+$/);
function sql(s){const r=spawnSync('mysql',['--user=root','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','--database',cfg.database],{input:s,encoding:'utf8'});assert.equal(r.status,0,'database query failed');return r.stdout.trim().split(/\r?\n/).filter(Boolean);}
const before=readFileSync(join(temp,'activities-before.jsonl'),'utf8').trim().split(/\r?\n/).map(JSON.parse);
const after=sql("SELECT JSON_ARRAY(activityCode,unitCode,activityType,sortOrder,required,referenceKind,referenceCode,completionRule,configJson,isDelete) FROM learning_unit_activity WHERE activityType<>'EXPERIMENT' ORDER BY activityCode").map(JSON.parse);
assert.deepEqual(after,before);
console.log(`Non-experiment activity rows preserved: ${after.length}/${before.length}`);
assert.equal(sql("SELECT COUNT(*) FROM experiment_revision WHERE reviewStatus='APPLIED'")[0],'41');
assert.equal(sql('SELECT COUNT(*) FROM experiment_node')[0],'123');
assert.equal(sql("SELECT COUNT(*) FROM learning_unit_activity WHERE activityType='EXPERIMENT' AND isDelete=0")[0],'77');
console.log('APPLIED revisions=41, experiment nodes=123, experiment activities=77');
let cookie='';
const base=`http://localhost:${cfg.port}/api`;
async function api(url,body){const res=await fetch(base+url,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},...(body?{body:JSON.stringify(body)}:{})});assert.equal(res.status,200);const c=res.headers.getSetCookie?.();if(c?.length)cookie=c.map(s=>s.split(';')[0]).join('; ');return res.json();}
assert.equal((await api('/user/login',{userAccount:cfg.user,userPassword:cfg.password})).code,0);
const root='content/operating-systems/wangdao-2025/experiments';
const packs=readdirSync(root).filter(b=>b.startsWith('A-')).map(b=>JSON.parse(readFileSync(join(root,b,'experiment-maintenance.json'),'utf8')));
const local=packs.flatMap(p=>p.experiments).filter(e=>e.judgeMode==='LOCAL_BUILD');
assert.equal(local.length,5);
for(const e of local){const result=await api(`/question/get/vo?id=${e.questionId}`);assert.equal(result.code,0);assert.equal(result.data.judgeMode,'LOCAL_BUILD');for(const key of ['answer','judgeCase','metadataJson'])assert.equal(Object.hasOwn(result.data,key),false);}
for(const [id,mode] of [['1917131319528452098','CONCEPT_HARNESS'],['1908449571747745793','AUTO']]){const result=await api(`/question/get/vo?id=${id}`);assert.equal(result.code,0);assert.equal(result.data.judgeMode,mode);}
console.log('Detail mode responses verified for 5 local, 1 concept and 1 functional experiment; author answers not exposed');
const submittedBefore=sql('SELECT COUNT(*) FROM question_submit')[0];
const rejected=await api('/question/question_submit/do',{questionId:local[0].questionId,language:'c',code:'int main(void){return 0;}'});
assert.equal(rejected.code,40000);assert.match(rejected.message,/MIPS/);
assert.equal(sql('SELECT COUNT(*) FROM question_submit')[0],submittedBefore);
console.log('Real LOCAL_BUILD submission rejected with code 40000; no submission persisted');
console.log('PASS isolated API import acceptance');
