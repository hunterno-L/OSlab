// Verify the comment migration on session-created databases, then apply the tested bytes locally.
import {readFileSync,writeFileSync,readdirSync} from 'node:fs';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const temp=join(process.env.TEMP,'oslab-terra-acceptance'),configPath=join(temp,'verification-config.json');
const cfg=JSON.parse(readFileSync(configPath,'utf8'));
const local=process.argv.includes('--apply-local');
const fromBase=process.argv.includes('--from-v14-base');
assert(!(local&&fromBase));
const sqlBytes=readFileSync('数据库文件/oslab_upgrade_v14_experiment_comments.sql');
const hash=createHash('sha256').update(sqlBytes).digest('hex');
if(local)assert.equal(cfg.commentMigrationVerified,hash,'verify these exact SQL bytes on test databases first');
const targets=local?['oslab_db']:[cfg.freshDatabase,cfg.upgradeDatabase];
const root='content/operating-systems/wangdao-2025/experiments';
const es=readdirSync(root).filter(b=>b.startsWith('A-')).flatMap(b=>JSON.parse(readFileSync(join(root,b,'experiment-maintenance.json'),'utf8')).experiments.map(e=>({...e,dir:join(root,b)})));
const ids=es.map(e=>{assert.match(e.questionId,/^\d+$/);return e.questionId;}).join(',');
const hex=s=>`CONVERT(X'${Buffer.from(s,'utf8').toString('hex')}' USING utf8mb4)`;
for(const database of targets){
 assert(database==='oslab_db'&&local||/^oslab_terra_(fresh|upgrade)_\d+$/.test(database));
 function mysql(sql,ok=true){const r=spawnSync('mysql',['--user=root','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','--database',database],{input:sql,encoding:'utf8',maxBuffer:64*1024*1024});if((r.status===0)!==ok){writeFileSync(join(temp,'comment-update-error.log'),r.stderr);throw Error(`mysql unexpected exit ${r.status}; diagnostics in TEMP`);}return r.stdout.trim().split(/\r?\n/).filter(Boolean);}
 function rows(table,where='1=1',exclude=[]){const cols=mysql(`SHOW COLUMNS FROM ${table}`).map(s=>s.split('\t')[0]).filter(c=>!['createTime','updateTime',...exclude].includes(c));return mysql(`SELECT JSON_OBJECT(${cols.flatMap(c=>["'"+c+"'",`CAST(\`${c}\` AS CHAR CHARACTER SET utf8mb4)`]).join(',')}) FROM ${table} WHERE ${where}`).map(JSON.parse);}
 if(fromBase){
  assert(!local&&/^oslab_terra_(fresh|upgrade)_\d+$/.test(database));
  mysql(`START TRANSACTION; CREATE TEMPORARY TABLE _comment_base AS SELECT questionId,MIN(revision) revision FROM experiment_revision WHERE questionId IN (${ids}) AND reviewStatus='APPLIED' GROUP BY questionId;
UPDATE question q JOIN _comment_base b ON q.id=b.questionId JOIN experiment_revision r ON r.questionId=b.questionId AND r.revision=b.revision SET q.originalCode=CASE WHEN JSON_TYPE(JSON_EXTRACT(r.afterJson,'$.originalCode'))='NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.afterJson,'$.originalCode')) END,q.answer=CASE WHEN JSON_TYPE(JSON_EXTRACT(r.afterJson,'$.answer'))='NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.afterJson,'$.answer')) END;
DELETE r FROM experiment_revision r JOIN _comment_base b ON r.questionId=b.questionId WHERE r.revision>b.revision; DROP TEMPORARY TABLE _comment_base; COMMIT;`);
  console.log(`Prepared ${database} at original v14 code/revision baseline (session fixture only).`);
 }
 const digest=(table,where,exclude)=>createHash('sha256').update(rows(table,where,exclude).map(JSON.stringify).sort().join('\n')).digest('hex');
 const preserved=['course_resource','experiment_node','learning_unit','learning_unit_activity','learning_unit_node','course_content_revision','user','question_submit','learning_attempt','learning_event'];
 const before=preserved.map(t=>digest(t));const questionBefore=digest('question','1=1',['originalCode','answer']);
 if(local)writeFileSync(join(temp,'comment-update-before.json'),JSON.stringify({database,hash,question:rows('question',`id IN (${ids})`),revisions:rows('experiment_revision',`questionId IN (${ids})`)}));
 mysql(sqlBytes);
 const first=[digest('question'),digest('experiment_revision')];mysql(sqlBytes);assert.deepEqual([digest('question'),digest('experiment_revision')],first);
 assert.deepEqual(preserved.map(t=>digest(t)),before);assert.equal(digest('question','1=1',['originalCode','answer']),questionBefore);
 for(const e of es){const [row]=rows('question',`id=${e.questionId}`);for(const c of e.contentChanges.filter(c=>['originalCode','answer'].includes(c.field)))assert.equal(row[c.field],readFileSync(join(e.dir,...c.file.split('/')),'utf8'));assert(!/北航|Beihang|BUAA/.test(row.originalCode));}
 if(!local){const [old]=rows('question',`id=${es[0].questionId}`);mysql(`UPDATE question SET originalCode='comment acceptance conflict' WHERE id=${es[0].questionId}`);const sentinel=[digest('question'),digest('experiment_revision')];mysql(sqlBytes,false);assert.deepEqual([digest('question'),digest('experiment_revision')],sentinel);mysql(`UPDATE question SET originalCode=${hex(old.originalCode)} WHERE id=${es[0].questionId}`);assert.deepEqual([digest('question'),digest('experiment_revision')],first);}
 console.log(`PASS ${database}: 41 code fields match cleaned files; repeat unchanged; all ${preserved.length} other tables and other question fields unchanged${local?'':'; conflict rejected'}`);
}
cfg[local?'commentMigrationApplied':'commentMigrationVerified']=hash;writeFileSync(configPath,JSON.stringify(cfg,null,2));
console.log(`PASS comment update ${local?'local application':'isolated verification'} SHA-256 ${hash}`);
