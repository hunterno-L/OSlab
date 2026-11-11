#!/usr/bin/env node
// Offline v14 exporter. Queries source with one SELECT statement; never writes to MySQL.
import {readFileSync,writeFileSync,existsSync,readdirSync,renameSync,unlinkSync} from 'node:fs';
import {join,resolve,dirname} from 'node:path';
import {spawnSync} from 'node:child_process';
import {isDeepStrictEqual} from 'node:util';
const ROOT='content/operating-systems/wangdao-2025/experiments';
const BATCHES=['A-第一章-实验判题-001','A-第二章-实验判题-001','A-第三章-实验判题-001','A-第四章-实验判题-001'];
const FORBIDDEN=new Set(['1918931948987031554','1918932928252157953','1918933052214812674','1918934540198682625','1918938841746878466','1918945654303125506','1920028942673477633']);
const args=process.argv.slice(2);if(args.includes('--help')){console.log('用法: 设置 OSLAB_DB_NAME 和 MySQL 环境变量后运行 node scripts/export-v14-migration.mjs [--output 路径]\n只读 SELECT；校验全部已应用修订和源文件后才写 SQL。默认输出 数据库文件/oslab_upgrade_v14_experiment_judge.sql。');process.exit(0)}
function die(x){throw new Error(x)}
const db=process.env.OSLAB_DB_NAME||'';if(!/^[A-Za-z0-9_]{1,64}$/.test(db)){console.error('错误: OSLAB_DB_NAME 必须为 1–64 位字母、数字或下划线');process.exit(2)}
if(args.length && (args.length!==2||args[0]!=='--output'||!args[1]||args[1].startsWith('--'))){console.error('错误: 参数应为 --output 路径');process.exit(2)}
const baselineDb=process.env.OSLAB_BASELINE_DB||'oslab_db';if(!/^[A-Za-z0-9_]{1,64}$/.test(baselineDb)||baselineDb===db){console.error('错误: OSLAB_BASELINE_DB 须为不同于源库的合法数据库名');process.exit(2)}
const output=resolve(args[1]||'数据库文件/oslab_upgrade_v14_experiment_judge.sql');
const readJson=p=>JSON.parse(readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const hex=s=>`CONVERT(X'${Buffer.from(String(s),'utf8').toString('hex')}' USING utf8mb4) COLLATE utf8mb4_unicode_ci`;
const val=(x,num=false)=>x===null||x===undefined?'NULL':num?( /^[0-9]+(?:\.[0-9]+)?$/.test(String(x))?String(x):die('非法数值') ):hex(x);
const id=x=>{if(!/^[1-9][0-9]*$/.test(String(x)))die('非法 ID');return String(x)};
const js=x=>typeof x==='string'?JSON.parse(x):x;
function noSensitive(x){if(!x||typeof x!=='object')return;for(const [k,v] of Object.entries(x)){if(/^(password|userPassword|apiKey|secret|token|email|phone|studentId|userId|authorId|reviewerId)$/i.test(k))die(`快照包含敏感键 ${k}，拒绝导出`);noSensitive(v)}}
function runCheck(command){const r=spawnSync(process.execPath,command,{encoding:'utf8',maxBuffer:32*1024*1024});if(r.status!==0)die(`文件检查失败: ${command[0]}\n${r.stdout||''}\n${r.stderr||''}`)}
function obj(cols){return 'JSON_OBJECT('+cols.map(c=>`'${c}',CAST(${c} AS CHAR CHARACTER SET utf8mb4)`).join(',')+')'}
function q(table,cols,where,tag){return `SELECT '${tag}' AS kind, ${obj(cols)} AS payload FROM ${table} WHERE ${where}`}
function insert(table,cols,row,updates,system={}){const vs=cols.map(c=>system[c]!==undefined?system[c]:val(row[c],['id','questionId','revision','sortOrder','required','isDelete','weight','estimatedMinutes','userId','reviewerId','authorId'].includes(c)));return `INSERT INTO ${table} (${cols.map(c=>'`'+c+'`').join(',')}) VALUES (${vs.join(',')}) ON DUPLICATE KEY UPDATE ${(updates.length?updates.map(c=>'`'+c+'`=VALUES(`'+c+'`)').join(','):'`id`=`id`')};`}
function guard(query){return `INSERT INTO _v14_scope_guard(ok) SELECT 0 WHERE EXISTS (${query});`}
function main(){
 runCheck(['scripts/check-experiment-mappings.mjs']);runCheck(['scripts/check-content-consistency.mjs','--all']);runCheck(['scripts/import-experiment-package.mjs',ROOT,'--validate-only']);
 const entries=[], dirs=[];for(const b of BATCHES){const d=join(ROOT,b),p=readJson(join(d,'experiment-maintenance.json'));if(p.schemaVersion!=='oslab-experiment-maintenance/1.0')die('包协议错误');for(const e of p.experiments){const ref=join(d,'experiment-files',String(e.questionId));const diskNodes=readJson(join(ref,'nodes.json')),diskUnits=readJson(join(ref,'units.json'));if(diskNodes.questionId!==e.questionId||diskUnits.questionId!==e.questionId||!isDeepStrictEqual(diskNodes.nodeMappings.map(n=>({...n,role:n.role==='RELATED'?'SECONDARY':n.role})),e.nodeMappings)||!isDeepStrictEqual(diskUnits.unitMappings,e.unitMappings))die(`${e.questionId}: 包与本地映射文件不一致`);if(FORBIDDEN.has(e.questionId))die('含禁止实验');id(e.questionId);entries.push(e);dirs.push(d)}}
 if(entries.length!==41||new Set(entries.map(e=>e.questionId)).size!==41)die('实验范围必须恰为 41 项');const unitCodes=[...new Set(entries.flatMap(e=>e.unitMappings.map(u=>u.unitCode)))].sort();if(entries.reduce((n,e)=>n+e.nodeMappings.length,0)!==123||entries.reduce((n,e)=>n+e.unitMappings.length,0)!==77||unitCodes.length!==18)die('映射范围不匹配');
 const idlist=entries.map(e=>id(e.questionId)).join(',');const units=unitCodes.map(hex).join(',');const codes=entries.map(e=>hex(e.resourceCode)).join(',');
 const question=['id','title','content','originalCode','answer','judgeCase','judgeConfig'];
 const rev=['id','questionId','resourceCode','revision','reviewStatus','requestJson','beforeJson','afterJson','contributor','batchCode','appliedTime','createTime'];
 const resource=['id','resourceCode','resourceType','title','questionId','adapterKey','algorithmKey','status','metadataJson','isDelete'];
 const node=['id','questionId','nodeCode','nodeName','role','weight','reason'];
 const unit=['id','unitCode','revision','reviewStatus','draftJson','publishedJson'];
 const act=['id','activityCode','unitCode','activityType','sortOrder','required','referenceKind','referenceCode','completionRule','configJson','isDelete'];
 const unode=['id','unitCode','nodeCode','role'];
 const crev=['id','entityType','entityCode','revision','snapshotJson','createTime'];
 const queries=[q('question',question,`id IN (${idlist}) AND isDelete=0`,'q'),q('experiment_revision',rev,`questionId IN (${idlist}) AND revision=(SELECT MAX(r2.revision) FROM experiment_revision r2 WHERE r2.questionId=experiment_revision.questionId)`,'r'),q('course_resource',resource,`questionId IN (${idlist}) AND resourceType='EXPERIMENT' AND isDelete=0`,'c'),q('experiment_node',node,`questionId IN (${idlist})`,'n'),q('learning_unit',unit,`unitCode IN (${units}) AND isDelete=0`,'u'),q('learning_unit_activity',act,`unitCode IN (${units})`,'a'),q('learning_unit_node',unode,`unitCode IN (${units})`,'nunit'),q('course_content_revision',crev,`entityType='units' AND entityCode IN (${units}) AND revision=(SELECT revision FROM learning_unit WHERE unitCode=course_content_revision.entityCode AND isDelete=0)`,'cr'),q(`\`${baselineDb}\`.learning_unit`,unit,`unitCode IN (${units}) AND isDelete=0`,'bu'),q(`\`${baselineDb}\`.learning_unit_activity`,act,`unitCode IN (${units})`,'ba'),q(`\`${baselineDb}\`.learning_unit_node`,unode,`unitCode IN (${units})`,'bn')];
 const sql=queries.join(' UNION ALL ')+';';if(!/^SELECT\s/i.test(sql))die('查询不是 SELECT');const mysqlArgs=['--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','--database',db];if(process.env.MYSQL_USER)mysqlArgs.push('--user',process.env.MYSQL_USER);if(process.env.MYSQL_HOST)mysqlArgs.push('--host',process.env.MYSQL_HOST);if(process.env.MYSQL_TCP_PORT)mysqlArgs.push('--port',process.env.MYSQL_TCP_PORT);
 const result=spawnSync('mysql',mysqlArgs,{input:sql,encoding:'utf8',maxBuffer:128*1024*1024,env:process.env});if(result.error||result.status!==0)die(`MySQL SELECT 失败（退出 ${result.status??'无'}）；请检查连接及 SELECT 权限`);
 const data={q:[],r:[],c:[],n:[],u:[],a:[],nunit:[],cr:[],bu:[],ba:[],bn:[]};for(const line of result.stdout.split(/\r?\n/)){if(!line)continue;const k=line.indexOf('\t');if(k<0||!data[line.slice(0,k)])die('MySQL 响应格式错误');data[line.slice(0,k)].push(JSON.parse(line.slice(k+1)))}
 const by=(arr,key)=>new Map(arr.map(o=>[o[key],o]));const qm=by(data.q,'id'),rm=by(data.r,'questionId'),cm=by(data.c,'questionId'),um=by(data.u,'unitCode'),crm=by(data.cr,'entityCode'),bum=by(data.bu,'unitCode');if(bum.size!==18)die('基线库单元不全');if(qm.size!==41||rm.size!==41||cm.size!==41||um.size!==18||crm.size!==18||data.n.length!==123)die(`源库范围不完整: questions=${qm.size}, latest revisions=${rm.size}, resources=${cm.size}, nodes=${data.n.length}, units=${um.size}, unit revisions=${crm.size}`);
 for(let i=0;i<entries.length;i++){const e=entries[i],r=rm.get(e.questionId),questionRow=qm.get(e.questionId),c=cm.get(e.questionId);if(!r||r.reviewStatus!=='APPLIED'||!r.appliedTime)die(`${e.questionId}: 最新修订不是 APPLIED`);if(r.resourceCode!==e.resourceCode||c.resourceCode!==e.resourceCode||questionRow.title!==e.title||!isDeepStrictEqual(js(r.requestJson),e))die(`${e.questionId}: 修订、身份或标题与包不一致`);const meta=js(c.metadataJson);if(meta.judgeMode!==e.judgeMode||!isDeepStrictEqual(meta.unitMappings,e.unitMappings)||(e.conceptTarget&&meta.conceptTarget!==e.conceptTarget))die(`${e.questionId}: 资源元数据不一致`);if(!['DRAFT','PUBLISHED','OFFLINE'].includes(c.status))die('资源状态无效');
 const after=js(r.afterJson);for(const ch of e.contentChanges){const expected=readFileSync(join(dirs[i],...ch.file.split('/')),'utf8').replace(/^\uFEFF/,'');if(after[ch.field]!==expected||questionRow[ch.field]!==expected)die(`${e.questionId}: ${ch.field} 与文件/已应用状态不一致`)}
 if(e.nodeMappings.some(n=>!['PRIMARY','SECONDARY'].includes(n.role)))die(`${e.questionId}: 节点角色不符合实际导入 API`);const actual=data.n.filter(n=>n.questionId===e.questionId);if(actual.length!==e.nodeMappings.length)die(`${e.questionId}: 节点数量不一致`);for(const n of e.nodeMappings){const found=actual.find(x=>x.nodeCode===n.nodeCode);if(!found||found.role!==n.role||Number(found.weight)!==n.weight||found.reason!==n.reason)die(`${e.questionId}: 节点不一致 ${n.nodeCode}`)}
 for(const m of e.unitMappings){const draft=js(um.get(m.unitCode)?.draftJson||'{}');if(!Array.isArray(draft.activities)||!draft.activities.some(a=>a.activityType==='EXPERIMENT'&&a.reference?.kind==='RESOURCE'&&a.reference?.code===e.resourceCode&&a.completionRule===m.completionRule&&a.required===m.required))die(`${e.questionId}: 单元草稿活动缺失 ${m.unitCode}`);const found=data.a.filter(a=>a.unitCode===m.unitCode&&a.referenceCode===e.resourceCode&&a.referenceKind==='RESOURCE'&&a.activityType==='EXPERIMENT'&&a.isDelete==='0');if(found.length!==1||found[0].completionRule!==m.completionRule||Boolean(Number(found[0].required))!==m.required)die(`${e.questionId}: 单元活动不一致 ${m.unitCode}`)}
 }
 const targetResources=new Set(entries.map(e=>e.resourceCode));
 const isTargetActivity=a=>a.activityType==='EXPERIMENT'&&a.referenceKind==='RESOURCE'&&targetResources.has(a.referenceCode);
 const selectedActivities=data.a.filter(isTargetActivity);
 if(selectedActivities.length!==77)die(`源库本轮活动数 ${selectedActivities.length} != 77`);
 const semantic=(a,fields)=>fields.map(k=>a[k]??null);
 const sameRows=(a,b,fields)=>isDeepStrictEqual(a.map(x=>JSON.stringify(semantic(x,fields))).sort(),b.map(x=>JSON.stringify(semantic(x,fields))).sort());
 const activityFields=act.filter(x=>x!=='id');
 if(!sameRows(data.a.filter(a=>!isTargetActivity(a)),data.ba.filter(a=>!isTargetActivity(a)),activityFields))die('源库非本轮活动不同于基线');
 if(data.ba.some(isTargetActivity))die('基线库已含本轮目标实验活动，无法证明仅追加');
 if(!sameRows(data.nunit,data.bn,['unitCode','nodeCode','role']))die('源库单元节点不同于基线');
 for(const u of data.u){const b=bum.get(u.unitCode);const clean=x=>{const y=js(x);delete y.revision;y.activities=(y.activities||[]).filter(a=>!(a.activityType==='EXPERIMENT'&&a.reference?.kind==='RESOURCE'&&targetResources.has(a.reference.code)));return y};if(!isDeepStrictEqual(clean(u.draftJson),clean(b.draftJson)))die(`${u.unitCode}: 源草稿的非本轮内容不同于基线`)}
 data.a=selectedActivities;
 for(const u of data.u){const draft=js(u.draftJson);noSensitive(draft);if(u.publishedJson)noSensitive(js(u.publishedJson));if(u.reviewStatus==='PUBLISHED'&&!u.publishedJson)die(`${u.unitCode}: 状态为 PUBLISHED 但缺少 publishedJson`);const cr=data.cr.find(x=>x.entityCode===u.unitCode&&x.revision===u.revision);if(!cr)die(`${u.unitCode}: 单元当前修订缺失`);}
 for(const cr of data.cr)noSensitive(js(cr.snapshotJson));for(const e of entries){const c=cm.get(e.questionId);noSensitive(js(c.metadataJson));}for(const a of data.a)if(a.configJson)noSensitive(js(a.configJson));
 const lines=[`-- v14 experiment migration; generated ${new Date().toISOString()}; source ${db}`,'-- Generated by scripts/export-v14-migration.mjs. DO NOT EDIT. Requires v6 and v7 schemas.',`-- Requires the 18 existing baseline units, their unchanged non-target activities and unit nodes matching ${baselineDb}, and referenced registry nodes.`, '-- This migration is not a standalone fresh-install course seed.','-- Audit operator IDs are system 0; personal operator IDs, review reasons and non-teaching question snapshot fields removed.','-- Resource and unit draft/published states are copied as observed; no publication is implied.',
 '-- MySQL default batch mode without --force stops on errors; an uncommitted InnoDB transaction rolls back on disconnect. Do not use --force. DDL/other engines or an interrupted client with an open connection are outside this guarantee.',`-- Scope: 41 questions, 123 nodes, 77 mappings, 18 units. Published resources: ${data.c.filter(x=>x.status==='PUBLISHED').length}/41; published unit status: ${data.u.filter(x=>x.reviewStatus==='PUBLISHED').length}/18.`, 'SET NAMES utf8mb4;','START TRANSACTION;','CREATE TEMPORARY TABLE IF NOT EXISTS _v14_scope_guard (ok TINYINT NOT NULL CHECK (ok=1));'];
 lines.push(guard(`SELECT 1 WHERE (SELECT COUNT(*) FROM question WHERE id IN (${idlist}) AND isDelete=0)<>41`));
 for(const row of data.q){
  const revision=rm.get(row.id),before=js(revision.beforeJson),after=js(revision.afterJson),fields=question.slice(1);
  for(const field of fields){if(!Object.hasOwn(before,field)||!Object.hasOwn(after,field)||![before[field],after[field]].every(v=>v===null||typeof v==='string'))die(`${row.id}: ${field} 审计快照缺失或类型无效`);if(row[field]!==after[field])die(`${row.id}: ${field} 当前值与最新已应用快照不一致`)}
  const matches=snapshot=>'('+fields.map(field=>`CAST(\`${field}\` AS BINARY) <=> CAST(${val(snapshot[field])} AS BINARY)`).join(' AND ')+')';
  lines.push(guard(`SELECT 1 FROM question WHERE id=${id(row.id)} AND NOT (${matches(before)} OR ${matches(after)})`));
 }
 lines.push(guard(`SELECT 1 WHERE (SELECT COUNT(*) FROM learning_unit WHERE unitCode IN (${units}) AND isDelete=0)<>18`));
 const requiredRegistry=[...new Set([...data.n.map(n=>n.nodeCode),...data.bn.map(n=>n.nodeCode)])].sort();
 lines.push(guard(`SELECT 1 WHERE (SELECT COUNT(*) FROM knowledge_node_registry WHERE nodeCode IN (${requiredRegistry.map(hex).join(',')}) AND isDelete=0)<>${requiredRegistry.length}`));
 const jsonValue=x=>x===null?'NULL':`CAST(${hex(x)} AS JSON)`;
 for(const u of data.u){
  const b=bum.get(u.unitCode), key=hex(u.unitCode);
  const matches=row=>`(revision=${val(row.revision,true)} AND reviewStatus=${hex(row.reviewStatus)} AND draftJson <=> ${jsonValue(row.draftJson)} AND publishedJson <=> ${jsonValue(row.publishedJson)})`;
  lines.push(guard(`SELECT 1 FROM learning_unit WHERE unitCode=${key} AND (revision>${val(u.revision,true)} OR NOT (${matches(b)} OR ${matches(u)}))`));
  const excluded=`activityType='EXPERIMENT' AND referenceKind='RESOURCE' AND referenceCode IN (${codes})`;
  const expectedActs=data.a.filter(a=>a.unitCode===u.unitCode);
  const expectedCodes=expectedActs.map(a=>hex(a.activityCode)).join(',');
  if(expectedActs.length)lines.push(guard(`SELECT 1 FROM learning_unit_activity WHERE unitCode=${key} AND (${excluded}) AND activityCode NOT IN (${expectedCodes})`));
  for(const a of expectedActs)lines.push(guard(`SELECT 1 FROM learning_unit_activity WHERE activityCode=${hex(a.activityCode)} AND (unitCode<>${key} OR activityType<>${hex(a.activityType)} OR referenceKind<>${hex(a.referenceKind)} OR referenceCode<>${hex(a.referenceCode)})`));
  const baseActs=data.ba.filter(a=>a.unitCode===u.unitCode&&!isTargetActivity(a));
  lines.push(guard(`SELECT 1 WHERE (SELECT COUNT(*) FROM learning_unit_activity WHERE unitCode=${key} AND NOT (${excluded}))<>${baseActs.length}`));
  for(const a of baseActs){const where=activityFields.map(c=>`\`${c}\` <=> ${c==='configJson' ? jsonValue(a[c]) : val(a[c],['sortOrder','required','isDelete'].includes(c))}`).join(' AND ');lines.push(guard(`SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM learning_unit_activity WHERE ${where})`))}
  const baseNodes=data.bn.filter(n=>n.unitCode===u.unitCode);
  lines.push(guard(`SELECT 1 WHERE (SELECT COUNT(*) FROM learning_unit_node WHERE unitCode=${key})<>${baseNodes.length}`));
  for(const n of baseNodes)lines.push(guard(`SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM learning_unit_node WHERE unitCode=${key} AND nodeCode=${hex(n.nodeCode)} AND role=${hex(n.role)})`));
 }
 for(const row of data.cr)lines.push(guard(`SELECT 1 FROM course_content_revision WHERE entityType=${hex(row.entityType)} AND entityCode=${hex(row.entityCode)} AND revision=${val(row.revision,true)} AND NOT (snapshotJson <=> ${jsonValue(row.snapshotJson)})`));
 for(const row of data.r){const e=entries.find(x=>x.questionId===row.questionId),after=js(row.afterJson);const changed=e.contentChanges.map(c=>`NOT (JSON_UNQUOTE(JSON_EXTRACT(afterJson, ${hex('$.'+c.field)})) <=> ${val(after[c.field])})`);lines.push(guard(`SELECT 1 FROM experiment_revision WHERE questionId=${id(row.questionId)} AND revision=${val(row.revision,true)} AND (NOT (requestJson <=> ${jsonValue(row.requestJson)}) OR ${changed.join(' OR ')})`))}
 for(const row of data.c)lines.push(guard(`SELECT 1 FROM course_resource WHERE resourceCode=${hex(row.resourceCode)} AND (questionId<>${id(row.questionId)} OR resourceType<>${hex('EXPERIMENT')})`));
 for(const row of data.a)lines.push(guard(`SELECT 1 FROM learning_unit_activity WHERE activityCode=${hex(row.activityCode)} AND unitCode<>${hex(row.unitCode)}`));
 
 for(const row of data.r)lines.push(guard(`SELECT 1 FROM experiment_revision WHERE questionId=${id(row.questionId)} AND revision>${val(row.revision,true)}`));
 // Protect source IDs against primary-key conflicts before any upsert.
 for(const [table,rows,key] of [['course_resource',data.c,'resourceCode'],['experiment_node',data.n,'nodeCode'],['learning_unit_activity',data.a,'activityCode'],['course_content_revision',data.cr,'entityCode'],['experiment_revision',data.r,'questionId']])for(const row of rows){let mismatch=table==='experiment_node'?`questionId<>${id(row.questionId)} OR nodeCode<>${hex(row.nodeCode)}`:table==='course_content_revision'?`entityType<>${hex(row.entityType)} OR entityCode<>${hex(row.entityCode)} OR revision<>${val(row.revision,true)}`:table==='experiment_revision'?`questionId<>${id(row.questionId)} OR revision<>${val(row.revision,true)}`:`${key}<>${table==='course_resource'||table==='learning_unit_activity'?hex(row[key]):id(row[key])}`;lines.push(guard(`SELECT 1 FROM ${table} WHERE id=${id(row.id)} AND (${mismatch})`))}
 for(const row of data.q)lines.push(`UPDATE question SET ${question.slice(1).map(c=>'`'+c+'`='+val(row[c])).join(',')} WHERE id=${id(row.id)} AND isDelete=0;`);
 for(const row of data.c)lines.push(insert('course_resource',resource,row,resource.filter(x=>!['id','resourceCode'].includes(x))));
 lines.push(`DELETE FROM experiment_node WHERE questionId IN (${idlist});`);for(const row of data.n)lines.push(insert('experiment_node',node,row,['nodeName','role','weight','reason']));
 for(const row of data.u)lines.push(`UPDATE learning_unit SET revision=${val(row.revision,true)}, reviewStatus=${val(row.reviewStatus)}, draftJson=${val(row.draftJson)}, publishedJson=${val(row.publishedJson)} WHERE unitCode=${hex(row.unitCode)} AND isDelete=0;`);
 for(const row of data.a)lines.push(insert('learning_unit_activity',act,row,act.filter(x=>!['id','activityCode'].includes(x))));

 for(const row of data.cr)lines.push(insert('course_content_revision',[...crev,'authorId'],row,[],{authorId:'0'}));
 for(const row of data.r){for(const f of ['beforeJson','afterJson']){const o=js(row[f]);const safe={};for(const k of ['title','content','originalCode','answer','judgeCase','judgeConfig','tags'])if(Object.hasOwn(o,k))safe[k]=o[k];safe.id=row.questionId;row[f]=JSON.stringify(safe)}row.reviewReason=null;lines.push(insert('experiment_revision',[...rev,'userId','reviewerId','reviewReason'],row,[],{userId:'0',reviewerId:'0',reviewReason:'NULL'}))}
 lines.push('DROP TEMPORARY TABLE _v14_scope_guard;','COMMIT;','');
 const tmp=output+'.tmp-'+process.pid;try{writeFileSync(tmp,lines.join('\n'),{encoding:'utf8',flag:'wx'});renameSync(tmp,output)}catch(e){try{unlinkSync(tmp)}catch{}throw e}console.log(`已导出 ${output}；资源已发布 ${data.c.filter(x=>x.status==='PUBLISHED').length}/41，单元状态已发布 ${data.u.filter(x=>x.reviewStatus==='PUBLISHED').length}/18`);
}
try{main()}catch(e){console.error('导出失败:',e.message);process.exitCode=1}
