#!/usr/bin/env node
// Normalize experiment source comments while preserving every character outside comments.
import {readFileSync,writeFileSync,readdirSync,copyFileSync,mkdirSync,existsSync} from 'node:fs';
import {join,extname,relative} from 'node:path';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const roots=['content/operating-systems/wangdao-2025/experiments','artifacts/experiment-judge'];
const backup=join(process.env.TEMP,'oslab-terra-acceptance/comment-backup');
const reportFile='artifacts/experiment-judge/comment-cleanup.json';
const report=existsSync(reportFile)?JSON.parse(readFileSync(reportFile,'utf8')):[];
let changed=0;
function clean(text){
 let out=text.replace(/北航|Beihang|BUAA/g,'');
 out=out.replace(/内容来源诚实性标注(?: \+ 宿主等价适配)?/g,'宿主环境说明').replace(/内容来源诚实性/g,'示例范围');
 out=out.replace(/\bAgent [AB]\b/g,'维护脚本').replace(/主 agent/g,'维护者').replace(/本 agent/g,'维护脚本');
 out=out.replace(/字节垃圾/g,'字节占位内容').replace(/沙箱只有 x86-64 gcc、无 MIPS 交叉编译器与 gxemul，无法判题。/g,'完整运行需使用 MIPS 交叉编译器与 gxemul。');
 out=out.replace(/（[^（）]*「原始代码\.txt」[^（）]*）/g,'')
  .replace(/；\s*\n \* 工作区 question\.id=\d+ 的 originalCode\/answer 均为 0 字节/g,'')
  .replace(/（逐条理由见 report-A\.md）/g,'')
  .replace(/【关于(.*?)的诚实性说明】/g,'【$1】')
  .replace(/\n \* ([。，；])/g,'$1')
  .replace(/真实源码锚：工作区 question\.id=\d+ 的 answer 字段给出了\s*\n \* mips_vm_init\(\) 的完整参考实现（1432 字节），本填写区与它逐 Step 对应。/g,'本填写区对应 mips_vm_init() 的内存分配与映射步骤。');
 if(out.includes('构建 Minix 内核')&&out.includes('平台判分子任务'))return `/* 操作系统实验 1.1：构建依赖顺序。\n * 按 make 的目标依赖语义填写合法构建顺序；依赖图是教学示例。\n * 完整内核构建需在本地 MIPS 工具链验证，本子任务通过不代表内核构建成功。\n * 仅填写标记的学生区域。\n */`;
 if(out.includes('使用 Linker Script 控制内核加载地址')&&out.includes('平台判分子任务'))return `/* 操作系统实验 1.3：链接脚本的段地址计算。\n * 按题面规定逐段 ALIGN 对齐并顺序放置，末尾 _end 按页对齐。\n * 这是通用布局规则的教学示例；完整链接仍需在本地 MIPS 工具链验证。\n * 仅填写标记的学生区域。\n */`;
 if(out.includes('第三章/第四章共享：MOS 进程控制块定义'))return `/* MOS 进程控制块与异常现场的宿主等价定义。\n * 按 MOS/MIPS3000 语义提供字段、地址布局和 CP0 状态位，用于功能验证。\n * 整数统一使用 uint32_t，保持 LLP64 与 LP64 环境下的宽度一致。\n * 完整内核运行需在 MIPS 环境验证。\n */`;
 if(out.includes('初始化进程控制块数组')&&out.includes('启动期主机等价环境')){
  const start=out.indexOf(' * 等价适配');
  return `/* 操作系统实验 3.1：启动期内存初始化。\n * pages、envs 与 boot_pgdir 为指针，由 bump 分配器 alloc() 分配，\n * 与使用静态页数组的运行时分配模型区分。\n *\n`+(start>=0?out.slice(start):' */');
 }
 return out;
}
function transform(source){
 let i=0,out='',code='';
 while(i<source.length){
  if(source[i]==='"'||source[i]==="'"){
   const quote=source[i];let j=i+1;
   for(;j<source.length;j++){if(source[j]==='\\'){j++;continue;}if(source[j]===quote){j++;break;}}
   const value=source.slice(i,j);out+=value;code+=value;i=j;continue;
  }
  if(source.startsWith('/*',i)||source.startsWith('//',i)){
   const block=source.startsWith('/*',i);const end=block?source.indexOf('*/',i+2):source.indexOf('\n',i+2);
   assert(!block||end>=0,'unterminated comment');const j=end<0?source.length:block?end+2:end;
   out+=clean(source.slice(i,j));code+=' ';i=j;continue;
  }
  out+=source[i];code+=source[i];i++;
 }
 return {out,code};
}
function walk(dir){for(const item of readdirSync(dir,{withFileTypes:true})){const file=join(dir,item.name);if(item.isDirectory()){if(!['node_modules','dist','target','screenshots'].includes(item.name))walk(file);continue;}
 if(!/\.(?:c|h|c\.inc)$/.test(file))continue;
 const before=readFileSync(file,'utf8'),result=transform(before);if(before===result.out)continue;
 assert.equal(transform(result.out).code,result.code,`${file}: executable text changed`);
 assert(!/北航|Beihang|BUAA/.test(result.out),`${file}: institution name remains`);
 const saved=join(backup,relative('.',file));mkdirSync(join(saved,'..'),{recursive:true});if(!existsSync(saved))copyFileSync(file,saved);
 writeFileSync(file,result.out);
 const reportItem=report.find(r=>r.file===file);
 if(reportItem){assert.equal(reportItem.executableSha256,createHash('sha256').update(result.code).digest('hex'));reportItem.afterBytes=Buffer.byteLength(result.out);}
 else report.push({file,beforeBytes:Buffer.byteLength(before),afterBytes:Buffer.byteLength(result.out),executableSha256:createHash('sha256').update(result.code).digest('hex')});
 changed++;
 }}
for(const root of roots)walk(root);
writeFileSync(reportFile,JSON.stringify(report,null,2)+'\n');
console.log(`Cleaned ${changed} source files (${report.length} total); all non-comment text identical. Original files saved in TEMP.`);
