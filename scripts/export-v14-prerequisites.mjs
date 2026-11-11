#!/usr/bin/env node
// Export only the public curriculum dependency closure of the 18 experiment units.
// Reads the pre-import baseline; never writes to MySQL or reads user/attempt/AI tables.
import {readFileSync,writeFileSync,readdirSync} from 'node:fs';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import assert from 'node:assert/strict';
const db=process.env.OSLAB_BASELINE_DB||'oslab_db';
assert.match(db,/^[a-zA-Z0-9_]{1,64}$/);
const hex=x=>`CONVERT(X'${Buffer.from(String(x),'utf8').toString('hex')}' USING utf8mb4) COLLATE utf8mb4_unicode_ci`;
function query(sql){assert.match(sql,/^(?:SELECT|SHOW)\s/);const r=spawnSync('mysql',['--user=root','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','--database',db],{input:sql,encoding:'utf8',maxBuffer:32*1024*1024});assert.equal(r.status,0,`read-only MySQL query failed: ${r.stderr.slice(0,300)}`);return r.stdout.trim().split(/\r?\n/).filter(Boolean);}
const schemas=new Map();
function schema(table){if(!schemas.has(table)){const cols=query(`SHOW COLUMNS FROM ${table}`).map(s=>s.split('\t'));schemas.set(table,cols.filter(c=>!['createTime','updateTime'].includes(c[0])));}return schemas.get(table);}
function rows(table,where='1=1'){
 const cols=schema(table);assert(!cols.some(c=>/blob|binary/i.test(c[1])),'binary data needs explicit export');
 const fields=cols.flatMap(([c])=>[`'${c}'`,`CAST(\`${c}\` AS CHAR CHARACTER SET utf8mb4)`]).join(',');
 return query(`SELECT JSON_OBJECT(${fields}) FROM ${table} WHERE ${where}`).map(JSON.parse);
}
const list=a=>{assert(a.length>0);return a.map(hex).join(',');};
const json=x=>x===null||x===undefined?null:JSON.parse(x);
function noSecrets(v){if(!v||typeof v!=='object')return;for(const [key,val] of Object.entries(v)){assert(!/^(password|userPassword|apiKey|secret|token|email|phone|studentId|userId|authorId|reviewerId)$/i.test(key),`sensitive JSON key ${key}`);noSecrets(val);}}
const root='content/operating-systems/wangdao-2025/experiments';
const experiments=readdirSync(root).filter(x=>x.startsWith('A-')).flatMap(b=>JSON.parse(readFileSync(join(root,b,'experiment-maintenance.json'),'utf8')).experiments);
assert.equal(experiments.length,41);
const unitCodes=[...new Set(experiments.flatMap(e=>e.unitMappings.map(m=>m.unitCode)))].sort();assert.equal(unitCodes.length,18);
const units=rows('learning_unit',`unitCode IN (${list(unitCodes)}) AND isDelete=0`);assert.equal(units.length,18);
const activities=rows('learning_unit_activity',`unitCode IN (${list(unitCodes)})`);
assert(!activities.some(a=>a.activityType==='EXPERIMENT'),'baseline must precede this experiment import');
const unitNodes=rows('learning_unit_node',`unitCode IN (${list(unitCodes)})`);
const refs={CHUNK:new Set(),QUESTION:new Set(),RESOURCE:new Set()};
for(const u of units)for(const snap of [json(u.draftJson),json(u.publishedJson)])for(const a of snap?.activities||[]){assert(refs[a.reference?.kind],`unknown reference ${a.reference?.kind}`);refs[a.reference.kind].add(a.reference.code);}
const chunks=rows('learning_content_chunk',`chunkCode IN (${list([...refs.CHUNK])}) AND isDelete=0`);
const questions=rows('learning_question',`questionCode IN (${list([...refs.QUESTION])}) AND isDelete=0 AND resourceScope='SHARED'`);
assert.equal(chunks.length,refs.CHUNK.size);assert.equal(questions.length,refs.QUESTION.size);
assert(chunks.every(c=>c.reviewStatus==='PUBLISHED'&&c.publishedJson));assert(questions.every(q=>q.reviewStatus==='PUBLISHED'));
const resources=refs.RESOURCE.size?rows('course_resource',`resourceCode IN (${list([...refs.RESOURCE])}) AND isDelete=0`):[];
assert.equal(resources.length,refs.RESOURCE.size);
assert(resources.every(r=>r.resourceType!=='EXPERIMENT'),'experiment resources belong in v14');
const chunkNodes=rows('learning_content_chunk_node',`chunkCode IN (${list(chunks.map(c=>c.chunkCode))})`);
const questionNodes=rows('learning_question_node',`questionId IN (${questions.map(q=>{assert.match(q.id,/^\d+$/);return q.id;}).join(',')})`);
const nodeCodes=new Set([...unitNodes,...chunkNodes,...questionNodes].map(n=>n.nodeCode).filter(Boolean));
for(const e of experiments)for(const n of e.nodeMappings)nodeCodes.add(n.nodeCode);
function collectNodes(v){if(!v||typeof v!=='object')return;for(const [k,x] of Object.entries(v)){if(k==='nodeCode'&&typeof x==='string')nodeCodes.add(x);if(k==='nodeCodes'&&Array.isArray(x))x.forEach(c=>nodeCodes.add(c));collectNodes(x);}}
for(const row of [...units,...chunks])for(const key of ['draftJson','publishedJson'])if(row[key])collectNodes(json(row[key]));
const nodes=rows('knowledge_node_registry',`nodeCode IN (${list([...nodeCodes])}) AND isDelete=0`);assert.equal(nodes.length,nodeCodes.size);
const legacy=rows('knowledge_node_legacy_identity',`nodeCode IN (${list([...nodeCodes])})`);
const relations=rows('knowledge_node_relation',`fromNodeCode IN (${list([...nodeCodes])}) AND toNodeCode IN (${list([...nodeCodes])}) AND isDelete=0`);
const allSections=rows('course_section','isDelete=0');const sectionMap=new Map(allSections.map(s=>[s.sectionCode,s]));
const sectionCodes=new Set([...units,...chunks,...questions].map(r=>r.sectionCode).concat(nodes.map(n=>n.homeSectionCode)).filter(Boolean));
for(const code of sectionCodes){const row=sectionMap.get(code);assert(row,`missing section ${code}`);if(row.parentSectionCode)sectionCodes.add(row.parentSectionCode);}
const sections=[...sectionCodes].sort().map(code=>sectionMap.get(code));
const courses=rows('course_catalog',`courseCode IN (${list([...new Set(sections.map(s=>s.courseCode))])}) AND isDelete=0`);
const sourceCodes=new Set(sections.map(s=>s.sourceCode).filter(Boolean));
function collectSources(v){if(!v||typeof v!=='object')return;for(const [k,x] of Object.entries(v)){if(k==='sourceCode'&&typeof x==='string')sourceCodes.add(x);collectSources(x);}}
for(const row of [...units,...chunks,...nodes,...resources])for(const [key,v] of Object.entries(row))if(v&&/Json$/.test(key)){const data=json(v);noSecrets(data);collectSources(data);}
const sources=rows('course_source',`sourceCode IN (${list([...sourceCodes])}) AND isDelete=0`);
const data=[['course_catalog',courses,'courseCode'],['course_source',sources,'sourceCode'],['course_section',sections,'sectionCode'],['knowledge_node_registry',nodes,'nodeCode'],['knowledge_node_legacy_identity',legacy,'nodeCode'],['knowledge_node_relation',relations,null],['learning_content_chunk',chunks,'chunkCode'],['learning_content_chunk_node',chunkNodes,null],['learning_question',questions,'questionCode'],['learning_question_node',questionNodes,null],['course_resource',resources,'resourceCode'],['learning_unit',units,'unitCode'],['learning_unit_node',unitNodes,null],['learning_unit_activity',activities,'activityCode']];
const numeric=t=>/^(?:bigint|int|tinyint|smallint|mediumint|float|double|decimal)/i.test(t);
function literal(x,type){if(x===null||x===undefined)return 'NULL';if(numeric(type)){assert.match(x,/^-?\d+(?:\.\d+)?$/);return x;}return hex(x);}
const statements=[`-- Public prerequisites for the 18 v14 experiment units. Generated from ${db}.`,`-- No user, task, progress, attempt, review or AI data. Teaching ownership is system 0.`,`-- Inserts missing public rows only. Existing rows are preserved, except untouched v6 empty unit templates.`,`-- Apply before oslab_upgrade_v14_experiment_judge.sql in fresh installs. Do not use mysql --force.`,`SET NAMES utf8mb4;`,`START TRANSACTION;`,`CREATE TEMPORARY TABLE _v14_prerequisite_guard(ok TINYINT NOT NULL CHECK (ok=1));`];
const templates=[...readFileSync('数据库文件/oslab_upgrade_v6_courses.sql','utf8').matchAll(/INSERT IGNORE INTO learning_unit[^\n]*?VALUES\((\d+),'([^']+)','([^']+)','([^']+)','([^']+)',(\d+),'([^']+)',0,'L'\);/g)];
assert.equal(templates.length,10,'v6 template syntax changed; review the exporter');
const templateMatches=templates.filter(m=>unitCodes.includes(m[2])).map(m=>`(id=${m[1]} AND unitCode=${hex(m[2])} AND courseCode=${hex(m[3])} AND sectionCode=${hex(m[4])} AND title=${hex(m[5])} AND sortOrder=${m[6]} AND draftJson <=> CAST(${hex(m[7])} AS JSON))`);
assert(templateMatches.length>0);
statements.push(`CREATE TEMPORARY TABLE _v14_unit_templates AS SELECT unitCode FROM learning_unit WHERE revision=1 AND reviewStatus='DRAFT' AND publishedJson IS NULL AND estimatedMinutes=0 AND contributor='L' AND userId=0 AND isDelete=0 AND (${templateMatches.join(' OR ')});`);
statements.push(`INSERT INTO _v14_prerequisite_guard(ok) SELECT 0 WHERE EXISTS (SELECT 1 FROM learning_unit WHERE unitCode IN (${list(templates.filter(m=>unitCodes.includes(m[2])).map(m=>m[2]))}) AND revision=1 AND reviewStatus='DRAFT' AND publishedJson IS NULL AND NOT (${templateMatches.join(' OR ')}));`);
// Detect a public source ID colliding with a different stable identity before any insert.
for(const [table,items,key] of data)if(key&&schema(table).some(c=>c[0]==='id'))for(const row of items)statements.push(`INSERT INTO _v14_prerequisite_guard(ok) SELECT 0 WHERE EXISTS (SELECT 1 FROM ${table} WHERE id=${literal(row.id,'bigint')} AND NOT (\`${key}\` <=> ${hex(row[key])}));`);
for(const q of questions)statements.push(`INSERT INTO _v14_prerequisite_guard(ok) SELECT 0 WHERE EXISTS (SELECT 1 FROM learning_question WHERE questionCode=${hex(q.questionCode)} AND id<>${literal(q.id,'bigint')});`);
const composites={'knowledge_node_relation':['fromNodeCode','toNodeCode','relationType'],'learning_content_chunk_node':['chunkCode','nodeCode'],'learning_question_node':['questionId','nodeCode','nodeName'],'learning_unit_node':['unitCode','nodeCode']};
for(const [table,items] of data)if(composites[table])for(const row of items){const types=new Map(schema(table).map(([c,t])=>[c,t]));const same=composites[table].map(c=>`\`${c}\` <=> ${literal(row[c],types.get(c))}`).join(' AND ');statements.push(`INSERT INTO _v14_prerequisite_guard(ok) SELECT 0 WHERE EXISTS (SELECT 1 FROM ${table} WHERE id=${literal(row.id,'bigint')} AND NOT (${same}));`);}
for(const [table,items] of data){const cols=schema(table);for(const row of items){
 const names=cols.map(c=>'`'+c[0]+'`').join(',');
 const values=cols.map(([c,type])=>literal(/^(userId|authorId|reviewerId|updateUserId)$/.test(c)?'0':row[c],type)).join(',');
 const preserve=cols.some(c=>c[0]==='id')?'`id`=`id`':'`nodeCode`=`nodeCode`';
 // Bootstrap only the shipped, unedited v6 template. Publish state is assigned last.
 if(table==='learning_unit')statements.push(`SET @v14_template=(SELECT COUNT(*) FROM _v14_unit_templates WHERE unitCode=${hex(row.unitCode)});`);
 const template='@v14_template=1';
 const updates=table==='learning_unit'?cols.filter(c=>!['id','unitCode','reviewStatus','revision','publishedJson','draftJson'].includes(c[0])).map(([c])=>`\`${c}\`=IF(${template},VALUES(\`${c}\`),\`${c}\`)`).concat(['draftJson','publishedJson','revision','reviewStatus'].map(c=>`\`${c}\`=IF(${template},VALUES(\`${c}\`),\`${c}\`)`)).join(','):preserve;
 statements.push(`INSERT INTO ${table} (${names}) VALUES (${values}) ON DUPLICATE KEY UPDATE ${updates};`);
 }}
statements.push('DROP TEMPORARY TABLE _v14_unit_templates;','DROP TEMPORARY TABLE _v14_prerequisite_guard;','COMMIT;','');
writeFileSync('数据库文件/oslab_seed_v14_experiment_prerequisites.sql',statements.join('\n'));
for(const [table,items] of data)console.log(`${table}: ${items.length}`);
console.log('Exported prerequisite SQL; no database writes performed.');
