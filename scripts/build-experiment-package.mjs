#!/usr/bin/env node
import {readFileSync,writeFileSync,readdirSync,existsSync} from 'node:fs';
import {join,resolve} from 'node:path';
import {spawnSync} from 'node:child_process';
const base='content/operating-systems/wangdao-2025/experiments';
const names={'ch1':'A-第一章-实验判题-001','ch2':'A-第二章-实验判题-001','ch3':'A-第三章-实验判题-001','ch4':'A-第四章-实验判题-001'};
const counts={ch1:5,ch2:8,ch3:13,ch4:15};
const concepts={'1917131319528452098':'make.dependency-order','1930154079598989313':'ld.section-address'};
const json=p=>JSON.parse(readFileSync(p,'utf8').replace(/^\uFEFF/,''));
function fail(s){throw Error(s)}
function main(){
 const arg=process.argv.slice(2);if(arg.includes('--help')){console.log('用法: node scripts/build-experiment-package.mjs <ch1|ch2|ch3|ch4|--all>\n只读输入、运行一致性检查并生成对应批次 experiment-maintenance.json。');return}
 if(arg.length!==1||(!names[arg[0]]&&arg[0]!=='--all'))fail('需要指定 ch1、ch2、ch3、ch4 或 --all');
 const inv=json('artifacts/team-handoff/2026-10-17/A-开工包/reference/experiments/inventory.json');const originals=new Map(inv.experiments.map(x=>[x.questionId,x]));
 const titles=new Map(readFileSync('artifacts/experiment-judge/current-content.tsv','utf8').split(/\r?\n/).map(s=>s.split('\t')).filter(a=>a.length>=2).map(a=>[a[0],a[1]]));
 const sections=new Map();for(const line of readFileSync('artifacts/experiment-judge/units.txt','utf8').split(/\r?\n/)){const m=/^\|\s*(unit\.[^\s|]+)\s*\|[^|]*\|\s*(sec\.[^\s|]+)\s*\|/.exec(line);if(m)sections.set(m[1],m[2]);}
 const selected=arg[0]==='--all'?Object.keys(names):arg;const builds=[];
 for(const ch of selected){const dir=join(base,names[ch]);const ef=join(dir,'experiment-files');const ids=readdirSync(ef,{withFileTypes:true}).filter(x=>x.isDirectory()).map(x=>x.name).sort();if(ids.length!==counts[ch])fail(`${ch}: 目录数量 ${ids.length} != ${counts[ch]}`);
 const check=spawnSync(process.execPath,['scripts/check-content-consistency.mjs',...ids.map(id=>join(ef,id))],{encoding:'utf8'});if(check.status!==0)fail(`${ch}: 内容一致性检查失败\n${check.stdout||''}\n${check.stderr||''}`);
 const experiments=[];for(const id of ids){const d=join(ef,id);const n=json(join(d,'nodes.json')),u=json(join(d,'units.json'));const old=originals.get(id);if(!old||!titles.has(id)||old.title!==titles.get(id)||n.questionId!==id||u.questionId!==id)fail(`${id}: 来源身份或标题不一致`);if(!Array.isArray(n.nodeMappings)||!n.nodeMappings.length||!Array.isArray(u.unitMappings)||!u.unitMappings.length)fail(`${id}: 缺少映射`);
 const sectionCode=sections.get(u.unitMappings[0].unitCode);if(!sectionCode)fail(`${id}: 主单元缺少 sectionCode`);for(const m of u.unitMappings)if(!sections.has(m.unitCode))fail(`${id}: 单元不存在 ${m.unitCode}`);
 const local=existsSync(join(d,'judge-mode.json'));const tier=local?'B':concepts[id]?'C':'A';if(local&&json(join(d,'judge-mode.json')).judgeMode!=='LOCAL_BUILD')fail(`${id}: 本地模式无效`);const caseFile=join(d,'judge-cases.json');if(local&&existsSync(caseFile))fail(`${id}: 本地实验不应有判题用例`);if(!local&&!existsSync(caseFile))fail(`${id}: 缺少判题用例`);if(u.unitMappings.some(m=>m.completionRule!==(local?'SUBMIT':'PASS')))fail(`${id}: 完成规则与判题方式不符`);
 const list=[['content','content.md'],['originalCode','starter.c']];if(!local)list.push(['answer','reference.c'],['judgeCase','judge-cases.json'],['judgeConfig','judge-config.json']);
 const changes=list.map(([field,file])=>{const f=join(d,file);if(!existsSync(f)||!readFileSync(f,'utf8').trim())fail(`${id}: 缺少 ${file}`);return {field,file:`experiment-files/${id}/${file}`,reason:local?'提供原实验说明及本地实验材料':'更新实验说明及宿主判题材料'};});
 if(!local){const cases=json(caseFile),cfg=json(join(d,'judge-config.json'));if(!Array.isArray(cases)||!cases.length||cases.some(c=>typeof c.input!=='string'||typeof c.output!=='string'||!c.output.trim())||!(cfg.timeLimit>0&&cfg.memoryLimit>0))fail(`${id}: 判题数据无效`);}
 const nodeMappings=n.nodeMappings.map(m=>{if(!['PRIMARY','RELATED'].includes(m.role))fail(`${id}: 本地节点角色无效`);return {...m,role:m.role==='RELATED'?'SECONDARY':'PRIMARY'};});
 const e={questionId:id,resourceCode:old.resourceCode,title:titles.get(id),sectionCode,contentChanges:changes,nodeMappings,unitMappings:u.unitMappings,judgeMode:local?'LOCAL_BUILD':tier==='C'?'CONCEPT_HARNESS':'FUNCTIONAL_HARNESS',...(tier==='C'?{conceptTarget:concepts[id]}:{}),verification:{fileReview:'内容一致性脚本检查通过',runtime:local?'本地构建实验；未在平台自动判分':'判题参考输出来自已有 judge-cases.json；运行核验另行确认',openItems:'尚未进行在线预览、导入与部署验证'},nodeProposals:[],unitProposals:[]};experiments.push(e);
 }
 builds.push({dir,ch,pack:{schemaVersion:'oslab-experiment-maintenance/1.0',batchCode:`batch.a.experiments.${ch}.judge.001`,contributor:'A',experiments}});
 }
 for(const b of builds){writeFileSync(join(b.dir,'experiment-maintenance.json'),JSON.stringify(b.pack,null,2)+'\n');console.log(`${b.ch}: ${b.pack.experiments.length} 项 → ${join(b.dir,'experiment-maintenance.json')}`)}
}
try{main()}catch(e){console.error('组包失败:',e.message);process.exitCode=1}
