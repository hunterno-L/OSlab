// Clone public curriculum plus existing public revision history, with system audit actors.
// The only copied user is this session's generated acceptance account from its old test DB.
import {readFileSync,writeFileSync,readdirSync} from 'node:fs';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import assert from 'node:assert/strict';
const configPath=join(process.env.TEMP,'oslab-terra-acceptance/verification-config.json');
const cfg=JSON.parse(readFileSync(configPath,'utf8'));assert.match(cfg.database,/^oslab_terra_verify_\d+$/);
const database=`oslab_terra_verify_${Date.now()}`;assert.match(database,/^oslab_terra_verify_\d+$/);
function sql(text,db='oslab_db'){const r=spawnSync('mysql',['--user=root','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','--database',db],{input:text,encoding:'utf8',maxBuffer:8*1024*1024});assert.equal(r.status,0,`public staging preparation failed: ${r.stderr.slice(0,250)}`);return r.stdout.trim().split(/\r?\n/).filter(Boolean);}
sql(`CREATE DATABASE ${database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`);
const tables=sql("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='oslab_db'");for(const t of tables){assert.match(t,/^[a-zA-Z0-9_]+$/);sql(`CREATE TABLE ${database}.\`${t}\` LIKE oslab_db.\`${t}\``);}
const publicTables=['course_catalog','course_section','course_source','course_resource','learning_content_chunk','learning_content_chunk_node','learning_unit','learning_unit_activity','learning_unit_node','knowledge_node_registry','knowledge_node_relation','knowledge_node_legacy_identity','learning_question','learning_question_node'];
for(const t of publicTables){const cols=sql(`SHOW COLUMNS FROM ${t}`).map(s=>s.split('\t')[0]);const fields=cols.map(c=>/^(userId|authorId|reviewerId|updateUserId)$/.test(c)?'0':'`'+c+'`').join(',');sql(`INSERT INTO ${database}.\`${t}\` SELECT ${fields} FROM oslab_db.\`${t}\``);}
sql(`INSERT INTO ${database}.question SELECT * FROM oslab_db.question WHERE resourceScope='SHARED'`);
const root='content/operating-systems/wangdao-2025/experiments';const es=readdirSync(root).filter(b=>b.startsWith('A-')).flatMap(b=>JSON.parse(readFileSync(join(root,b,'experiment-maintenance.json'),'utf8')).experiments);
const ids=es.map(e=>e.questionId);assert(ids.every(x=>/^\d+$/.test(x)));const units=[...new Set(es.flatMap(e=>e.unitMappings.map(m=>m.unitCode)))];assert(units.every(s=>/^[a-z0-9.-]+$/.test(s)));
function copyAudit(table,where){const cols=sql(`SHOW COLUMNS FROM ${table}`).map(s=>s.split('\t')[0]);const fields=cols.map(c=>/^(userId|authorId|reviewerId)$/.test(c)?'0':c==='reviewReason'?'NULL':['beforeJson','afterJson'].includes(c)?`JSON_REMOVE(${c},'$.userId','$.createTime','$.updateTime','$.isDelete')`:'`'+c+'`').join(',');sql(`INSERT INTO ${database}.${table} SELECT ${fields} FROM oslab_db.${table} WHERE ${where}`);}
copyAudit('experiment_revision',`questionId IN (${ids.join(',')})`);
copyAudit('course_content_revision',`entityType='units' AND entityCode IN (${units.map(u=>"'"+u+"'").join(',')})`);
const account=`CONVERT(X'${Buffer.from(cfg.user,'utf8').toString('hex')}' USING utf8mb4) COLLATE utf8mb4_unicode_ci`;
assert.equal(sql(`INSERT INTO ${database}.user SELECT * FROM ${cfg.database}.user WHERE userAccount=${account}; SELECT ROW_COUNT();`)[0],'1');
cfg.previousVerificationDatabase=cfg.database;cfg.database=database;writeFileSync(configPath,JSON.stringify(cfg,null,2));
console.log('Prepared '+database+' with public revision history; copied only the generated test account');
console.log('Existing target revisions: '+sql('SELECT COUNT(*) FROM experiment_revision',database)[0]);
