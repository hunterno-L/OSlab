// Session-specific application of the SQL already verified in migration-{fresh,upgrade}.log.
// Backup rows and a pristine unit baseline stay in TEMP / an isolated local backup database.
import {readFileSync,writeFileSync,readdirSync} from 'node:fs';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
assert.deepEqual(process.argv.slice(2),['--apply-local']);
const temp=join(process.env.TEMP,'oslab-terra-acceptance');
const configPath=join(temp,'verification-config.json');const cfg=JSON.parse(readFileSync(configPath,'utf8'));
assert.equal(cfg.source,'oslab_db');
const migration=readFileSync('数据库文件/oslab_upgrade_v14_experiment_judge.sql');
const hash=createHash('sha256').update(migration).digest('hex');
assert.equal(hash,'a3e9e145dc36c320e234da4efa63a01a46d6e96379468ab3bd598b055ce97af4','SQL differs from the tested artifact');
for(const mode of ['fresh','upgrade'])assert.match(readFileSync(`artifacts/experiment-judge/migration-${mode}.log`,'utf8'),new RegExp(`PASS ${mode} migration acceptance`));
function sql(text,db='oslab_db'){const r=spawnSync('mysql',['--user=root','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','--database',db],{input:text,encoding:'utf8',maxBuffer:64*1024*1024});if(r.status!==0){writeFileSync(join(temp,'local-apply-error.log'),r.stderr);throw Error('MySQL failed; diagnostics saved in TEMP');}return r.stdout.trim().split(/\r?\n/).filter(Boolean);}
const root='content/operating-systems/wangdao-2025/experiments';
const entries=readdirSync(root).filter(b=>b.startsWith('A-')).flatMap(b=>JSON.parse(readFileSync(join(root,b,'experiment-maintenance.json'),'utf8')).experiments);
const ids=entries.map(e=>e.questionId);assert.equal(ids.length,41);assert(ids.every(x=>/^[0-9]+$/.test(x)));
const unitCodes=[...new Set(entries.flatMap(e=>e.unitMappings.map(m=>m.unitCode)))];
const hex=s=>`CONVERT(X'${Buffer.from(s,'utf8').toString('hex')}' USING utf8mb4) COLLATE utf8mb4_unicode_ci`;
const idList=ids.join(','),units=unitCodes.map(hex).join(','),codes=entries.map(e=>hex(e.resourceCode)).join(',');
const scope=[['question',`id IN (${idList})`],['course_resource',`resourceCode IN (${codes})`],['experiment_node',`questionId IN (${idList})`],['experiment_revision',`questionId IN (${idList})`],['learning_unit',`unitCode IN (${units})`],['learning_unit_activity',`unitCode IN (${units})`],['course_content_revision',`entityType='units' AND entityCode IN (${units})`]];
function rows(table,where){const cols=sql(`SHOW COLUMNS FROM ${table}`).map(s=>s.split('\t')[0]);const obj=cols.flatMap(c=>[`'${c}'`,`CAST(\`${c}\` AS CHAR CHARACTER SET utf8mb4)`]).join(',');return sql(`SELECT JSON_OBJECT(${obj}) FROM ${table} WHERE ${where}`).map(JSON.parse);}
function digest(table,where='1=1'){const data=rows(table,where).map(r=>JSON.stringify(r)).sort();return createHash('sha256').update(data.join('\n')).digest('hex');}
const preserved=[['question',`id NOT IN (${idList})`],['learning_unit',`unitCode NOT IN (${units})`],['learning_unit_activity',"activityType<>'EXPERIMENT'"],['learning_unit_node','1=1']];
const before=preserved.map(([t,w])=>digest(t,w));
const untouched=['user','question_submit','learning_attempt','learning_event','ai_tutor_turn'].map(t=>[t,sql(`SELECT COUNT(*) FROM ${t}`)[0]]);
const backup=Object.fromEntries(scope.map(([t,w])=>[t,rows(t,w)]));
writeFileSync(join(temp,'local-v14-before.json'),JSON.stringify({database:'oslab_db',hash,at:new Date().toISOString(),rows:backup}));
const baseline=`oslab_terra_baseline_${Date.now()}`;assert.match(baseline,/^oslab_terra_baseline_\d+$/);
sql(`CREATE DATABASE ${baseline} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`);
for(const t of ['learning_unit','learning_unit_activity','learning_unit_node'])sql(`CREATE TABLE ${baseline}.${t} LIKE oslab_db.${t}; INSERT INTO ${baseline}.${t} SELECT * FROM oslab_db.${t} WHERE unitCode IN (${units});`);
cfg.baselineDatabase=baseline;writeFileSync(configPath,JSON.stringify(cfg,null,2));
console.log('Scoped backup saved in TEMP; untouched curriculum baseline saved in '+baseline);
sql(migration);
assert.deepEqual(preserved.map(([t,w])=>digest(t,w)),before);
assert.deepEqual(untouched.map(([t])=>[t,sql(`SELECT COUNT(*) FROM ${t}`)[0]]),untouched);
assert.equal(sql(`SELECT COUNT(*) FROM experiment_revision WHERE questionId IN (${idList}) AND reviewStatus='APPLIED'`)[0],'41');
assert.equal(sql(`SELECT COUNT(*) FROM experiment_node WHERE questionId IN (${idList})`)[0],'123');
assert.equal(sql(`SELECT COUNT(*) FROM learning_unit_activity WHERE activityType='EXPERIMENT' AND referenceCode IN (${codes}) AND isDelete=0`)[0],'77');
cfg.localAppliedAt=new Date().toISOString();cfg.migrationSha256=hash;writeFileSync(configPath,JSON.stringify(cfg,null,2));
console.log('Local oslab_db: 41 APPLIED revisions, 123 nodes, 77 experiment activities');
console.log('Other questions/units, non-experiment activities and all unit nodes unchanged; user/submission/attempt/event/AI counts unchanged');
console.log('PASS local v14 application; backup contains the original target rows. No duplicate experiments deleted.');
