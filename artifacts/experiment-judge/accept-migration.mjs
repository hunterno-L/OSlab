// Runs migrations only against databases created for this acceptance session.
import {readFileSync,writeFileSync,readdirSync} from 'node:fs';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const configPath=join(process.env.TEMP,'oslab-terra-acceptance/verification-config.json');
const cfg=JSON.parse(readFileSync(configPath,'utf8'));
const fresh=process.argv.includes('--fresh');
const reuse=process.argv.includes('--reuse-upgrade');
const database=fresh?cfg.freshDatabase:reuse?cfg.upgradeDatabase:`oslab_terra_upgrade_${Date.now()}`;
assert.match(database,/^oslab_terra_(?:fresh|upgrade)_\d+$/);
function mysql(sql,db=database,expected=0){const r=spawnSync('mysql',['--user=root','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4',...(db?['--database',db]:[])],{input:sql,encoding:'utf8',maxBuffer:64*1024*1024});if((r.status===0)!==(expected===0)){writeFileSync(join(process.env.TEMP,'oslab-terra-acceptance/migration-error.log'),r.stderr);throw Error(`mysql exit ${r.status}; diagnostics in TEMP/migration-error.log`);}return r.stdout.trim().split(/\r?\n/).filter(Boolean);}
if(!fresh&&!reuse){
 mysql(`CREATE DATABASE ${database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`,null);
 const tables=mysql("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='oslab_db'",'oslab_db');
 for(const t of tables){assert.match(t,/^[a-zA-Z0-9_]+$/);mysql(`CREATE TABLE ${database}.\`${t}\` LIKE oslab_db.\`${t}\``);}
 const publicTables=['course_catalog','course_section','course_source','course_resource','learning_content_chunk','learning_content_chunk_node','learning_unit','learning_unit_activity','learning_unit_node','knowledge_node_registry','knowledge_node_relation','knowledge_node_legacy_identity','learning_question','learning_question_node'];
 for(const t of publicTables)mysql(`INSERT INTO ${database}.\`${t}\` SELECT * FROM oslab_db.\`${t}\``);
 mysql(`INSERT INTO ${database}.question SELECT * FROM oslab_db.question WHERE resourceScope='SHARED'`);
 for(const t of ['experiment_revision','course_content_revision'])mysql(`INSERT INTO ${database}.${t} SELECT * FROM oslab_db.${t}`);
 cfg.upgradeDatabase=database;writeFileSync(configPath,JSON.stringify({...JSON.parse(readFileSync(configPath,'utf8')),upgradeDatabase:database},null,2));
 console.log('Upgrade fixture cloned public baseline; no user/attempt/AI rows copied');
}
const digest=(table,where='1=1')=>{
 const cols=mysql(`SHOW COLUMNS FROM ${table}`).map(s=>s.split('\t')[0]).filter(c=>!['createTime','updateTime'].includes(c));
 const values=cols.flatMap(c=>[`'${c}'`,`CAST(\`${c}\` AS CHAR CHARACTER SET utf8mb4)`]).join(',');
 const lines=mysql(`SELECT JSON_OBJECT(${values}) FROM ${table} WHERE ${where}`).map(s=>JSON.stringify(JSON.parse(s))).sort();
 return createHash('sha256').update(lines.join('\n')).digest('hex');
};
const seed=readFileSync('数据库文件/oslab_seed_v14_experiment_prerequisites.sql');
const migration=readFileSync('数据库文件/oslab_upgrade_v14_experiment_judge.sql');
if(fresh){
 if(mysql('SELECT COUNT(*) FROM knowledge_node_registry')[0]==='0'){
  const [template]=mysql(`SELECT JSON_OBJECT('unitCode',unitCode,'title',title) FROM learning_unit WHERE unitCode IN (SELECT DISTINCT unitCode FROM ${cfg.database}.learning_unit_activity WHERE activityType='EXPERIMENT') LIMIT 1`).map(JSON.parse);
  assert(template,'fresh fixture needs an original v6 template');
  const hex=v=>`CONVERT(X'${Buffer.from(v,'utf8').toString('hex')}' USING utf8mb4)`;
  mysql(`UPDATE learning_unit SET title='edited empty template' WHERE unitCode=${hex(template.unitCode)}`);
  mysql(seed,database,1);assert.equal(mysql('SELECT COUNT(*) FROM knowledge_node_registry')[0],'0');
  assert.equal(mysql(`SELECT title FROM learning_unit WHERE unitCode=${hex(template.unitCode)}`)[0],'edited empty template');
  mysql(`UPDATE learning_unit SET title=${hex(template.title)} WHERE unitCode=${hex(template.unitCode)}`);
  const [q]=mysql("SELECT JSON_OBJECT('id',CAST(id AS CHAR),'questionCode',questionCode) FROM oslab_db.learning_question WHERE questionCode='q.wd25.s5_1.004'").map(JSON.parse);
  assert(q);const newId=(BigInt(q.id)+999n).toString();
  const cols=mysql('SHOW COLUMNS FROM learning_question').map(s=>s.split('\t')[0]);
  assert.equal(mysql(`INSERT INTO learning_question (${cols.map(c=>'`'+c+'`').join(',')}) SELECT ${cols.map(c=>c==='id'?newId:'`'+c+'`').join(',')} FROM oslab_db.learning_question WHERE id=${q.id}; SELECT ROW_COUNT();`)[0],'1');
  mysql(seed,database,1);assert.equal(mysql('SELECT COUNT(*) FROM knowledge_node_registry')[0],'0');
  mysql(`DELETE FROM learning_question WHERE id=${newId}`);
  console.log('Prerequisite guards reject an edited empty template and a questionCode with a different ID before inserting data');
 }
 mysql(seed);const first=digest('learning_unit');mysql(seed);assert.equal(digest('learning_unit'),first);console.log('Public prerequisite seed applied twice; unit state unchanged on repeat');
}
const activitiesBefore=digest('learning_unit_activity',"activityType<>'EXPERIMENT'");
const nodesBefore=digest('learning_unit_node');
const untouchedTables=['user','question_submit','learning_attempt','learning_event','ai_tutor_turn'];
const untouchedCounts=untouchedTables.map(t=>mysql(`SELECT COUNT(*) FROM ${t}`)[0]);
const tables=['question','course_resource','experiment_node','experiment_revision','learning_unit','learning_unit_activity','learning_unit_node','course_content_revision'];
mysql(migration);const first=tables.map(t=>digest(t));mysql(migration);assert.deepEqual(tables.map(t=>digest(t)),first);
assert.equal(digest('learning_unit_activity',"activityType<>'EXPERIMENT'"),activitiesBefore);assert.equal(digest('learning_unit_node'),nodesBefore);
assert.equal(mysql('SELECT COUNT(*) FROM experiment_node')[0],'123');
assert.equal(mysql("SELECT COUNT(*) FROM experiment_revision WHERE reviewStatus='APPLIED'")[0],'41');
assert.equal(mysql("SELECT COUNT(*) FROM learning_unit_activity WHERE activityType='EXPERIMENT' AND isDelete=0")[0],'77');
assert.equal(mysql("SELECT COUNT(*) FROM course_resource WHERE resourceType='EXPERIMENT' AND status='PUBLISHED' AND isDelete=0")[0],'36');
assert.deepEqual(untouchedTables.map(t=>mysql(`SELECT COUNT(*) FROM ${t}`)[0]),untouchedCounts);
if(!fresh)assert(untouchedCounts.every(n=>n==='0'));
const root='content/operating-systems/wangdao-2025/experiments';
const packs=readdirSync(root).filter(b=>b.startsWith('A-')).map(b=>({dir:join(root,b),pack:JSON.parse(readFileSync(join(root,b,'experiment-maintenance.json'),'utf8'))}));
for(const {dir,pack} of packs)for(const e of pack.experiments){const [row]=mysql(`SELECT JSON_OBJECT('content',content,'originalCode',originalCode,'answer',answer,'judgeCase',judgeCase,'judgeConfig',judgeConfig) FROM question WHERE id=${e.questionId}`).map(JSON.parse);for(const change of e.contentChanges)assert.equal(row[change.field],readFileSync(join(dir,...change.file.split('/')),'utf8').replace(/^\uFEFF/,''));}
console.log('v14 applied twice; all 8 table digests identical, 41 source files exact, 123 nodes/77 activities/36 published resources');
console.log('Non-experiment activities and unit nodes unchanged; no submissions/attempts/events/AI turns created');
if(fresh){mysql(seed);assert.deepEqual(tables.map(t=>digest(t)),first);console.log('Prerequisite seed repeated after v14; migrated tables unchanged');}
const e=packs[0].pack.experiments[0],unit=e.unitMappings[0].unitCode;
const [oldUnit]=mysql(`SELECT JSON_OBJECT('draftJson',CAST(draftJson AS CHAR)) FROM learning_unit WHERE unitCode='${unit}'`).map(JSON.parse);
const [oldQuestion]=mysql(`SELECT JSON_OBJECT('content',content) FROM question WHERE id=${e.questionId}`).map(JSON.parse);
const literal=v=>`CONVERT(X'${Buffer.from(v,'utf8').toString('hex')}' USING utf8mb4)`;
mysql(`UPDATE learning_unit SET draftJson=JSON_SET(draftJson,'$.title','acceptance conflict') WHERE unitCode='${unit}';`);
const unitConflictState=tables.map(t=>digest(t));
mysql(migration,database,1);
assert.deepEqual(tables.map(t=>digest(t)),unitConflictState);
mysql(`UPDATE learning_unit SET draftJson=${literal(oldUnit.draftJson)} WHERE unitCode='${unit}';`);
mysql(`UPDATE question SET content='acceptance sentinel' WHERE id=${e.questionId};`);
const questionConflictState=tables.map(t=>digest(t));
mysql(migration,database,1);assert.deepEqual(tables.map(t=>digest(t)),questionConflictState);
mysql(`UPDATE question SET content=${literal(oldQuestion.content)} WHERE id=${e.questionId};`);
assert.deepEqual(tables.map(t=>digest(t)),first);
console.log('Independent conflict tests reject an edited unit and edited question; all 8 tables unchanged after each rejection');
console.log(`PASS ${fresh?'fresh':'upgrade'} migration acceptance: ${database}`);
