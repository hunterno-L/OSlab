#!/usr/bin/env node
// Export reviewed comment changes as a separate migration after the original v14.
import {readFileSync,writeFileSync,readdirSync} from 'node:fs';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import assert from 'node:assert/strict';
const cfg=JSON.parse(readFileSync(join(process.env.TEMP,'oslab-terra-acceptance/verification-config.json'),'utf8'));
assert.match(cfg.database,/^oslab_terra_verify_\d+$/);
const root='content/operating-systems/wangdao-2025/experiments';
const entries=readdirSync(root).filter(b=>b.startsWith('A-')).flatMap(b=>JSON.parse(readFileSync(join(root,b,'experiment-maintenance.json'),'utf8')).experiments.map(e=>({...e,dir:join(root,b)})));
assert.equal(entries.length,41);
const ids=entries.map(e=>{assert.match(e.questionId,/^\d+$/);return e.questionId;}).join(',');
const fields=['id','questionId','resourceCode','revision','reviewStatus','requestJson','beforeJson','afterJson','contributor','batchCode','appliedTime','createTime'];
const query=`SELECT JSON_OBJECT(${fields.flatMap(f=>["'"+f+"'",`CAST(\`${f}\` AS CHAR CHARACTER SET utf8mb4)`]).join(',')}) FROM experiment_revision r WHERE questionId IN (${ids}) AND reviewStatus='APPLIED' ORDER BY questionId,revision`;
const r=spawnSync('mysql',['--user=root','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','--database',cfg.database],{input:query,encoding:'utf8',maxBuffer:64*1024*1024});assert.equal(r.status,0);
const rows=r.stdout.trim().split(/\r?\n/).map(JSON.parse);
const hex=s=>`CONVERT(X'${Buffer.from(String(s),'utf8').toString('hex')}' USING utf8mb4) COLLATE utf8mb4_unicode_ci`;
const val=x=>x===null?'NULL':hex(x);
const guard=q=>`INSERT INTO _comment_update_guard(ok) SELECT 0 WHERE EXISTS (${q});`;
const sql=['-- Reviewed experiment code comments. Apply after oslab_upgrade_v14_experiment_judge.sql.','-- Changes only question.originalCode/answer and appends reviewed APPLIED revisions.','-- MySQL batch mode without --force; an error stops before writes and rolls back on disconnect.','SET NAMES utf8mb4;','START TRANSACTION;','CREATE TEMPORARY TABLE _comment_update_guard(ok TINYINT NOT NULL CHECK (ok=1));',guard(`SELECT 1 WHERE (SELECT COUNT(*) FROM question WHERE id IN (${ids}) AND isDelete=0)<>41`)];
const updates=[];
let auditCount=0;
for(const e of entries){
 const history=rows.filter(r=>r.questionId===e.questionId);assert(history.length>=2);
 const baseline=JSON.parse(history[0].afterJson),revisions=history.slice(1),row=revisions.at(-1);
 const after=JSON.parse(row.afterJson),changes={};
 for(const c of e.contentChanges.filter(c=>['originalCode','answer'].includes(c.field))){const expected=readFileSync(join(e.dir,...c.file.split('/')),'utf8');assert.equal(after[c.field],expected);assert(!/北航|Beihang|BUAA|report-A\.md|原始代码\.txt|参考解\.txt/.test(expected));assert.notEqual(baseline[c.field],after[c.field]);changes[c.field]=expected;}
 assert(Object.hasOwn(changes,'originalCode'));
 for(const revision of revisions){const state=JSON.parse(revision.afterJson);for(const f of ['title','content','judgeCase','judgeConfig'])assert.deepEqual(state[f],baseline[f],`non-comment field changed: ${e.questionId}/${f}`);}
 const allowedStates=[baseline,...revisions.map(r=>JSON.parse(r.afterJson))];
 const states=Object.keys(changes).map(f=>`(${allowedStates.map(state=>`CAST(\`${f}\` AS BINARY) <=> CAST(${val(state[f])} AS BINARY)`).join(' OR ')})`).join(' AND ');
 sql.push(guard(`SELECT 1 FROM question WHERE id=${row.questionId} AND NOT (${states})`));
 sql.push(guard(`SELECT 1 FROM experiment_revision WHERE questionId=${row.questionId} AND revision>${row.revision}`));
 updates.push(`UPDATE question SET ${Object.entries(changes).map(([f,v])=>'`'+f+'`='+hex(v)).join(',')} WHERE id=${row.questionId} AND isDelete=0;`);
 for(const revision of revisions){
 const row=revision;
 sql.push(guard(`SELECT 1 FROM experiment_revision WHERE id=${row.id} AND (questionId<>${row.questionId} OR revision<>${row.revision})`));
 sql.push(guard(`SELECT 1 FROM experiment_revision WHERE questionId=${row.questionId} AND revision=${row.revision} AND (id<>${row.id} OR reviewStatus<>'APPLIED' OR NOT (requestJson <=> CAST(${hex(row.requestJson)} AS JSON)) OR NOT (JSON_EXTRACT(afterJson,'$.originalCode') <=> JSON_EXTRACT(CAST(${hex(row.afterJson)} AS JSON),'$.originalCode')) OR NOT (JSON_EXTRACT(afterJson,'$.answer') <=> JSON_EXTRACT(CAST(${hex(row.afterJson)} AS JSON),'$.answer')))`));
 const safe=s=>{const data=JSON.parse(s),out={id:row.questionId};for(const f of ['title','content','originalCode','answer','judgeCase','judgeConfig','tags'])if(Object.hasOwn(data,f))out[f]=data[f];return JSON.stringify(out);};
 const values=fields.map(f=>['id','questionId','revision'].includes(f)?row[f]:val(['beforeJson','afterJson'].includes(f)?safe(row[f]):row[f]));
 updates.push(`INSERT INTO experiment_revision (${fields.map(f=>'`'+f+'`').join(',')},userId,reviewerId,reviewReason) VALUES (${values.join(',')},0,0,NULL) ON DUPLICATE KEY UPDATE id=id;`);
 auditCount++;
 }
}
sql.push(...updates,'DROP TEMPORARY TABLE _comment_update_guard;','COMMIT;','');
writeFileSync('数据库文件/oslab_upgrade_v14_experiment_comments.sql',sql.join('\n'));
console.log(`Exported comment-only update for 41 experiments and ${auditCount} reviewed revisions; other tables are not written.`);
