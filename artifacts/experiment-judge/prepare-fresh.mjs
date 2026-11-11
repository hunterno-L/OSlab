// Fresh-install fixture. It creates a new, uniquely named database and never targets oslab_db.
import {readFileSync,writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import assert from 'node:assert/strict';
const configPath=join(process.env.TEMP,'oslab-terra-acceptance/verification-config.json');
const cfg=JSON.parse(readFileSync(configPath,'utf8'));
const database=`oslab_terra_fresh_${Date.now()}`;
assert.match(database,/^oslab_terra_fresh_\d+$/);
const seeds=['oslab_db.sql','oslab_upgrade_v3_learning.sql','oslab_upgrade_v4_flow.sql','oslab_upgrade_v5_teaching.sql','oslab_demo_seed_v3.sql','oslab_seed_v4_content.sql','oslab_upgrade_v6_courses.sql','oslab_upgrade_v7_identity.sql','oslab_upgrade_v8_teaching_tasks.sql','oslab_upgrade_v9_learning_activities.sql','oslab_upgrade_v10_classes.sql','oslab_upgrade_v11_ai_session.sql','oslab_upgrade_v12_ai_evidence.sql','oslab_upgrade_v13_learning_selftest.sql'];
for(const file of seeds){const text=readFileSync(join('数据库文件',file),'utf8');const code=text.replace(/'(?:\\.|''|[^'\\])*'|"(?:\\.|""|[^"\\])*"|`(?:``|[^`])*`|--[^\r\n]*|#[^\r\n]*|\/\*(?!\!)[\s\S]*?\*\//g,' ');assert(!/^\s*(?:USE\b|(?:CREATE|DROP)\s+DATABASE\b|SOURCE\b)/im.test(code),`${file}: database switching is forbidden`);}
function mysql(sql,db){const r=spawnSync('mysql',['--user=root','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4',...(db?['--database',db]:[])],{input:sql,encoding:'utf8',maxBuffer:16*1024*1024});if(r.status!==0){writeFileSync(join(process.env.TEMP,'oslab-terra-acceptance/fresh-error.log'),r.stderr);throw Error(`mysql failed; private diagnostics saved in TEMP (exit ${r.status})`);}return r.stdout;}
mysql(`CREATE DATABASE ${database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`);
cfg.freshDatabase=database;writeFileSync(configPath,JSON.stringify(cfg,null,2));
for(const file of seeds){mysql(readFileSync(join('数据库文件',file)),database);console.log(`Loaded ${file}`);}
console.log(mysql("SELECT 'questions',COUNT(*) FROM question; SELECT 'units',COUNT(*) FROM learning_unit; SELECT 'registry',COUNT(*) FROM knowledge_node_registry; SELECT 'experiment_nodes',COUNT(*) FROM experiment_node;",database));
console.log(`Fresh fixture ready: ${database}`);
