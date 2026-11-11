#!/usr/bin/env node
// Adapted from origin/zixiaoranran-patch-2:scripts/import-experiment-package.mjs.
import {readFile,readdir,realpath} from 'node:fs/promises';
import path from 'node:path';
const help=`用法: node scripts/import-experiment-package.mjs <包目录|父目录> [--validate-only|--preview|--commit] [--apply] [--publish --runtime-verified]
默认 preview；--validate-only 仅离线验证。--commit 暂存修订；--apply 需同时指定 --commit，按当前状态审核应用。
--publish 需同时指定 --apply 与 --runtime-verified，仅启用已完成宿主核验的功能/概念判题资源。
LOCAL_BUILD 资源及依赖其发布的单元会被阻止，需单独完成本地核验和发布。
环境变量: OSLAB_BASE_URL (含 /api), OSLAB_TEACHER_USER, OSLAB_TEACHER_PASS。账号须有管理员权限。
退出码: 0 成功；1 文件、服务或流程失败；2 参数或环境错误。`;
const args=process.argv.slice(2);if(args.includes('--help')){console.log(help);process.exit(0)}
const flags=new Set(args.filter(a=>a.startsWith('--'))), targets=args.filter(a=>!a.startsWith('--'));const allowed=new Set(['--validate-only','--preview','--commit','--apply','--publish','--runtime-verified']);
function usage(s){console.error(s+'\n'+help);process.exit(2)}
if(targets.length!==1||[...flags].some(f=>!allowed.has(f))||args.filter(x=>x.startsWith('--')).length!==flags.size)usage('参数无效');
if(['--validate-only','--preview','--commit'].filter(f=>flags.has(f)).length>1||flags.has('--apply')&&!flags.has('--commit')||flags.has('--publish')&&(!flags.has('--apply')||!flags.has('--runtime-verified'))||flags.has('--runtime-verified')&&!flags.has('--publish'))usage('模式组合无效');
const offline=flags.has('--validate-only'),commit=flags.has('--commit'),apply=flags.has('--apply'),publish=flags.has('--publish');
const validPath=p=>typeof p==='string'&&p.startsWith('experiment-files/')&&!p.includes('..')&&!/[\\:?#]/.test(p)&&!p.includes('//')&&p.length<=300;
async function packages(dir){let st=await readdir(dir,{withFileTypes:true});if(st.some(x=>x.name==='experiment-maintenance.json'&&x.isFile()))return [dir];const result=[];for(const x of st.filter(x=>x.isDirectory()).sort((a,b)=>a.name.localeCompare(b.name))){const child=path.join(dir,x.name);if((await readdir(child)).includes('experiment-maintenance.json'))result.push(child)}return result}
async function load(dir){const pack=JSON.parse((await readFile(path.join(dir,'experiment-maintenance.json'),'utf8')).replace(/^\uFEFF/,''));if(pack.schemaVersion!=='oslab-experiment-maintenance/1.0'||!pack.batchCode||!pack.contributor||!Array.isArray(pack.experiments)||!pack.experiments.length)throw Error('包结构无效');const root=await realpath(dir);const seen=new Set(),ids=new Set(),codes=new Set(),files=[];let total=0;
 for(const e of pack.experiments){if(typeof e.questionId!=='string'||! /^[1-9][0-9]*$/.test(e.questionId)||ids.has(e.questionId)||typeof e.resourceCode!=='string'||! /^[a-z][a-z0-9._-]{1,127}$/.test(e.resourceCode)||codes.has(e.resourceCode)||!e.title||!Array.isArray(e.contentChanges)||!Array.isArray(e.nodeMappings)||!Array.isArray(e.unitMappings))throw Error('实验项无效或重复');ids.add(e.questionId);codes.add(e.resourceCode);const fields=new Set();for(const c of e.contentChanges){if(!['content','originalCode','answer','judgeCase','judgeConfig','tags'].includes(c.field)||fields.has(c.field)||!c.reason||!validPath(c.file))throw Error(`${e.questionId}: 修订字段/路径无效`);fields.add(c.field);if(seen.has(c.file))throw Error('附件路径重复引用');seen.add(c.file);const full=await realpath(path.join(root,...c.file.split('/')));if(!full.startsWith(root+path.sep))throw Error('附件越出包目录');const b=await readFile(full);total+=b.length;if(b.length>4*1024*1024||total>16*1024*1024)throw Error('附件超限');const t=new TextDecoder('utf-8',{fatal:true}).decode(b).replace(/^\uFEFF/,'');if(!t.trim())throw Error('附件为空');if(c.field==='judgeCase'){const j=JSON.parse(t);if(!Array.isArray(j)||!j.length||j.some(x=>typeof x.input!=='string'||typeof x.output!=='string'))throw Error('judgeCase 无效')}if(c.field==='judgeConfig'){const j=JSON.parse(t);if(!(j.timeLimit>0&&j.memoryLimit>0))throw Error('judgeConfig 无效')}files.push({path:c.file,contentBase64:b.toString('base64')});}
 if(!['LOCAL_BUILD','CONCEPT_HARNESS','FUNCTIONAL_HARNESS'].includes(e.judgeMode))throw Error(`${e.questionId}: 未知判题方式`);
 if(e.judgeMode==='LOCAL_BUILD'&&(['judgeCase','judgeConfig','answer'].some(f=>fields.has(f))||e.unitMappings.some(m=>m.completionRule!=='SUBMIT')))throw Error(`${e.questionId}: LOCAL_BUILD 契约错误`);
 if(e.judgeMode==='CONCEPT_HARNESS'&&!e.conceptTarget)throw Error(`${e.questionId}: 缺少 conceptTarget`);
 }return {pack,files};}
let base,cookie;async function request(method,endpoint,body){let res;try{res=await fetch(base+endpoint,{method,headers:{'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},body:body===undefined?undefined:JSON.stringify(body),redirect:'error',signal:AbortSignal.timeout(30000)});}catch{throw Error(`请求失败 ${method} ${endpoint}`)}let data;try{data=await res.json()}catch{throw Error(`HTTP ${res.status} 响应不是 JSON`)}if(!res.ok||data.code!==0)throw Error(`请求失败 ${method} ${endpoint}: HTTP ${res.status}, code ${data.code}`);return {res,data:data.data}}
async function login(){const r=await request('POST','/user/login',{userAccount:process.env.OSLAB_TEACHER_USER,userPassword:process.env.OSLAB_TEACHER_PASS});cookie=r.res.headers.getSetCookie?.().map(x=>x.split(';')[0]).join('; ')||r.res.headers.get('set-cookie')?.split(';')[0];if(!cookie)throw Error('登录响应无会话 Cookie')}
async function main(){const dirs=await packages(path.resolve(targets[0]));if(!dirs.length)throw Error('没有找到实验维护包');const payloads=[];for(const dir of dirs)payloads.push(await load(dir));const globalIds=new Set(),globalCodes=new Set();for(const {pack} of payloads)for(const e of pack.experiments){if(globalIds.has(e.questionId)||globalCodes.has(e.resourceCode))throw Error('多个批次含重复实验或资源');globalIds.add(e.questionId);globalCodes.add(e.resourceCode)}for(const {pack,files} of payloads)console.log(`${pack.batchCode}: ${pack.experiments.length} 项 / ${files.length} 附件，离线校验通过`);if(offline)return;
 if(!process.env.OSLAB_BASE_URL||!process.env.OSLAB_TEACHER_USER||!process.env.OSLAB_TEACHER_PASS)usage('缺少 OSLAB_BASE_URL、OSLAB_TEACHER_USER 或 OSLAB_TEACHER_PASS');let u;try{u=new URL(process.env.OSLAB_BASE_URL)}catch{usage('OSLAB_BASE_URL 无效')}if(!['http:','https:'].includes(u.protocol)||u.username||u.password||u.search||u.hash)usage('OSLAB_BASE_URL 格式无效');base=u.toString().replace(/\/$/,'');await login();
 for(const payload of payloads){const {pack}=payload;const requestPayload={package:pack,files:payload.files};const p=(await request('POST','/teaching/experiments/import/preview',requestPayload)).data;if(!p?.valid){console.error('预览失败:',JSON.stringify(p?.errors||[]));throw Error('preview 校验未通过')}console.log(`${pack.batchCode}: preview valid，updated=${p.updated} unchanged=${p.unchanged}`);for(const item of p.items)console.log(`  ${item.code}: ${item.action}`);if(!commit)continue;const updated=new Map(p.items.map(x=>[x.code,x.action]));const result=(await request('POST','/teaching/experiments/import/commit',requestPayload)).data;console.log(`${pack.batchCode}: commit updated=${result.updated} unchanged=${result.unchanged}`);if(!apply)continue;
 for(const e of pack.experiments){let current=(await request('GET',`/teaching/experiments/${e.questionId}/mappings`)).data;let status=current.status;if(status==='APPLIED'){if(updated.get(e.resourceCode)!=='UNCHANGED')throw Error(`${e.questionId}: 状态已变化，停止以防跳过新修订`);console.log(`${e.questionId}: UNCHANGED/APPLIED`);continue}for(const [from,action] of [[['DRAFT','REJECTED'],'SUBMIT'],[['SUBMITTED'],'APPROVE'],[['APPROVED'],'APPLY']])if(from.includes(status)){current=(await request('POST',`/teaching/experiments/${e.questionId}/review`,{action,...(action==='APPLY'?{confirmed:true,reason:'实验内容包导入'}:{})})).data;status=current.status}if(status!=='APPLIED')throw Error(`${e.questionId}: 无法继续审核状态 ${status}`);console.log(`${e.questionId}: APPLIED revision=${current.revision}`)}

 }
 const allExperiments=payloads.flatMap(p=>p.pack.experiments);
 if(publish){
  const local=allExperiments.filter(e=>e.judgeMode==='LOCAL_BUILD');
  const automatic=allExperiments.filter(e=>e.judgeMode!=='LOCAL_BUILD');
  const blockedUnits=new Set(local.flatMap(e=>e.unitMappings.map(m=>m.unitCode)));
  for(const e of local)console.error(`${e.questionId} ${e.resourceCode}: LOCAL_BUILD 未发布；须单独完成本地构建核验并经明确确认后发布`);
  for(const e of automatic){const result=(await request('PUT',`/teaching/content/resources/${encodeURIComponent(e.resourceCode)}`,{status:'PUBLISHED',runtimeVerified:true})).data;if(result?.status!=='PUBLISHED')throw Error(`${e.resourceCode}: 发布接口未确认 PUBLISHED`);console.log(`${e.resourceCode}: 资源发布接口确认 PUBLISHED（宿主核验）`)}
  const units=[...new Set(automatic.flatMap(e=>e.unitMappings.map(m=>m.unitCode)))];
  for(const unit of units){
   if(blockedUnits.has(unit)){console.error(`${unit}: 包含待核验 LOCAL_BUILD 资源，单元发布已阻止`);continue}
   const encoded=encodeURIComponent(unit);let state=(await request('GET',`/teaching/units/${encoded}`)).data.status;
   if(state==='PUBLISHED'){console.log(`${unit}: 接口显示已发布`);continue}
   for(const [from,action] of [[['DRAFT','REJECTED'],'SUBMIT'],[['SUBMITTED'],'APPROVE']])if(from.includes(state)){state=(await request('POST',`/teaching/content/units/${encoded}/review`,{action})).data.status}
   if(state!=='APPROVED')throw Error(`${unit}: 无法发布状态 ${state}`);
   const published=(await request('POST',`/teaching/units/${encoded}/publish`,{})).data;
   if(published?.status!=='PUBLISHED')throw Error(`${unit}: 发布接口未确认 PUBLISHED`);
   console.log(`${unit}: 单元发布接口确认 PUBLISHED`);
  }
  for(const unit of blockedUnits)if(!units.includes(unit))console.error(`${unit}: 包含待核验 LOCAL_BUILD 资源，单元发布已阻止`);
  if(local.length){console.error(`发布未完成：${local.length} 个 LOCAL_BUILD 实验须单独核验；${blockedUnits.size} 个关联单元暂缓发布`);process.exitCode=1}
 }
}
main().catch(e=>{console.error('失败:',e.message);process.exitCode=1});
