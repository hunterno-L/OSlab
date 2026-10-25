import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, mkdir, readFile, writeFile, rm } from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const workspace = fileURLToPath(new URL("../../", import.meta.url));
const checker = path.join(workspace, "scripts/check-member-package.mjs");
const content = path.join(workspace, "content/operating-systems/wangdao-2025");
const sourceRef = { sourceCode:"sample.oslab", origin:"SELF_AUTHORED_SAMPLE", sectionCode:"sec.process.cpu-algorithms", pageStart:null, pageEnd:null, locator:"test fixture", sourceQuestionNumber:null };

async function fixture(run) {
  const root = await mkdtemp(path.join(os.tmpdir(), "oslab-member-package-"));
  const save = async (name, value) => {
    const filename = path.join(root, name);
    await mkdir(path.dirname(filename), { recursive:true });
    await writeFile(filename, typeof value === "string" ? value : JSON.stringify(value));
  };
  try {
    await save("shared/overview-authoring.json", {nodes:[],units:[]});
    await save("shared/nodes.json", {nodes:[{nodeCode:"os.cpu.fcfs"}]});
    await save("shared/sections.json", {sections:[{sectionCode:"sec.process"},{sectionCode:"sec.process.cpu-algorithms"}]});
    await save("shared/sources.json", [{sourceCode:"sample.oslab"}]);
    await save("shared/demos.json", {resources:[{resourceCode:"demo.cpu.fcfs"}]});
    const execute = (args=[]) => spawnSync(process.execPath, [checker, root, ...args], {encoding:"utf8"});
    const sample = JSON.parse(await readFile(path.join(content, "samples/application-questions.sample.json"), "utf8"));
    await run({root,save,execute,sample});
  } finally { await rm(root, {recursive:true,force:true}); }
}

test("standalone comprehensive package accepts full rubric and absolute work directory", async () => fixture(async ({root,save,execute,sample}) => {
  await save("work/application-questions.json", sample);
  const result = execute(["--work-dir", path.join(root,"work")]);
  assert.equal(result.status,0,result.stderr);
  assert.match(result.stdout,/1综合题/);
}));

test("incorrect rubric totals are rejected", async () => fixture(async ({save,execute,sample}) => {
  sample.questions[0].parts[0].rubric[0].points=1;
  await save("work/application-questions.json",sample);
  const result=execute();
  assert.equal(result.status,1);
  assert.match(result.stderr,/评分点分值之和/);
}));

test("new J section, node and unit can be referenced by A before database import", async () => fixture(async ({save,execute,sample}) => {
  await save("j/knowledge.json",{packageType:"KNOWLEDGE",nodes:[{nodeCode:"os.process.new_concept"}]});
  await save("j/chapter-plan.json",{sections:[{sectionCode:"sec.process.new-topic"}],units:[{unitCode:"unit.process.new-topic"}]});
  const q=sample.questions[0];
  q.sectionCode="sec.process.new-topic";
  q.nodeMappings[0].nodeCode="os.process.new_concept";
  q.parts[0].nodeMappings[0].nodeCode="os.process.new_concept";
  for(const point of q.parts[0].rubric)point.nodeCodes=["os.process.new_concept"];
  q.usageSuggestions=[{unitCode:"unit.process.new-topic",usage:"PRACTICE"}];
  await save("work/ch02/application-questions.json",sample);
  const result=execute(["--work-dir","work/ch02","--with-j","j"]);
  assert.equal(result.status,0,result.stderr);
  const withoutJ=execute(["--work-dir","work/ch02"]);
  assert.equal(withoutJ.status,1);
  assert.match(withoutJ.stderr,/未知nodeCode|目录/);
}));

test("empty comprehensive-only package requires explicit template mode", async () => fixture(async ({save,execute,sample}) => {
  sample.questions=[];
  await save("work/application-questions.json",sample);
  assert.equal(execute().status,1);
  assert.equal(execute(["--allow-empty"]).status,0);
}));

test("chapter reading coverage and prerequisite cycles are checked", async () => fixture(async ({save,execute}) => {
  await save("work/knowledge.json",{
    schemaVersion:"oslab-authoring/1.0",packageType:"KNOWLEDGE",
    nodes:[{nodeCode:"os.cpu.fcfs",name:"FCFS",description:"FCFS scheduling",nodeType:"ALGORITHM",homeSectionCode:"sec.process.cpu-algorithms",sourceRefs:[sourceRef]}],
    chunks:[{chunkCode:"chunk.fcfs",title:"Rule",contentMd:"Rule",chunkType:"EXPLANATION",sectionCode:"sec.process.cpu-algorithms",nodeCodes:["os.cpu.fcfs"],sourceRef,assets:[]}],relations:[]
  });
  const unit={unitCode:"unit.fcfs",sectionCode:"sec.process.cpu-algorithms",title:"FCFS",objectives:["Calculate waiting time"],sortOrder:1,estimatedMinutes:10,prerequisiteUnitCodes:[],nodeCodes:["os.cpu.fcfs"],readingChunkCodes:["chunk.fcfs"],demoResourceCodes:["demo.cpu.fcfs"],practiceGuidance:"Calculate then compare"};
  const plan={schemaVersion:"oslab-chapter-plan/1.0",chapterCode:"sec.process",status:"PROPOSED",sections:[],units:[unit]};
  await save("work/chapter-plan.json",plan);
  assert.equal(execute().status,0);
  unit.readingChunkCodes=[];
  await save("work/chapter-plan.json",plan);
  assert.match(execute().stderr,/未安排在本章阅读顺序/);
  unit.readingChunkCodes=["chunk.fcfs"];
  unit.prerequisiteUnitCodes=["unit.second"];
  plan.units.push({...unit,unitCode:"unit.second",prerequisiteUnitCodes:["unit.fcfs"]});
  await save("work/chapter-plan.json",plan);
  assert.match(execute().stderr,/先修形成环/);
}));

async function experiment(save) {
  const entry={questionId:"12345",resourceCode:"exp.legacy.12345",title:"FCFS experiment",sectionCode:"sec.process.cpu-algorithms",nodeMappings:[{nodeCode:"os.cpu.fcfs",role:"PRIMARY",weight:1}],unitMappings:[],verification:{runtime:"NOT_RUN"},contentChanges:[{field:"originalCode",file:"experiment-files/12345/starter.c",reason:"Add starter"},{field:"judgeCase",file:"experiment-files/12345/judge-cases.json",reason:"Real cases"}]};
  const pack={schemaVersion:"oslab-experiment-maintenance/1.0",batchCode:"batch.a.experiments.process.001",contributor:"A",experiments:[entry]};
  await save("work/experiment-maintenance.json",pack);
  await save("work/experiment-files/12345/starter.c","int main(void) { return 0; }\n");
  await save("work/experiment-files/12345/judge-cases.json",[{input:"2\n",output:"3\n"}]);
  return {pack,entry};
}

test("real starter and judge files are counted; invalid database fields fail", async () => fixture(async ({save,execute}) => {
  const {pack,entry}=await experiment(save);
  const result=execute();
  assert.equal(result.status,0,result.stderr);
  assert.match(result.stdout,/1项含实际修订文件/);
  entry.contentChanges[0].field="initialCode";
  await save("work/experiment-maintenance.json",pack);
  assert.match(execute().stderr,/修订字段/);
}));

test("missing attachments and malformed judge cases cannot pass", async () => fixture(async ({save,execute}) => {
  const {pack,entry}=await experiment(save);
  await save("work/experiment-files/12345/judge-cases.json",[{input:12,output:3}]);
  assert.match(execute().stderr,/judgeCase需/);
  entry.contentChanges[0].file="experiment-files/12345/missing.c";
  await save("work/experiment-maintenance.json",pack);
  const result=execute();
  assert.equal(result.status,1);
  assert.match(result.stderr,/ENOENT|附件不存在/);
}));

test("missing experiment title, primary mappings and zero ids are rejected before import", async () => fixture(async ({save,execute}) => {
  const {pack,entry}=await experiment(save);
  entry.title="";entry.nodeMappings=[];entry.questionId="0";
  await save("work/experiment-maintenance.json",pack);
  const result=execute();
  assert.equal(result.status,1);
  assert.match(result.stderr,/缺少原实验标题/);
  assert.match(result.stderr,/缺少主考查节点/);
  assert.match(result.stderr,/非零数字字符串/);
}));

test("later chapters can reference prior J nodes and prerequisite units without waiting for import", async () => fixture(async ({save,execute}) => {
  await save("prior/knowledge.json",{packageType:"KNOWLEDGE",nodes:[{nodeCode:"os.process.shared_context"}]});
  await save("prior/chapter-plan.json",{sections:[],units:[{unitCode:"unit.process.prior"}]});
  await save("extra/knowledge.json",{packageType:"KNOWLEDGE",nodes:[{nodeCode:"os.process.extra_context"}]});
  await save("extra/chapter-plan.json",{sections:[],units:[{unitCode:"unit.process.extra"}]});
  await save("work/knowledge.json",{schemaVersion:"oslab-authoring/1.0",packageType:"KNOWLEDGE",nodes:[],chunks:[{chunkCode:"chunk.later",title:"Later topic",contentMd:"Uses prior context",sectionCode:"sec.process.cpu-algorithms",nodeCodes:["os.process.shared_context","os.process.extra_context"],sourceRef,assets:[]}],relations:[]});
  await save("work/chapter-plan.json",{schemaVersion:"oslab-chapter-plan/1.0",chapterCode:"sec.process",status:"PROPOSED",sections:[],units:[{unitCode:"unit.later",sectionCode:"sec.process.cpu-algorithms",title:"Later",objectives:["Use prior concepts"],sortOrder:1,estimatedMinutes:10,prerequisiteUnitCodes:["unit.process.prior","unit.process.extra"],nodeCodes:["os.process.shared_context"],readingChunkCodes:["chunk.later"],demoResourceCodes:[],practiceGuidance:"Apply prior knowledge"}]});
  const result=execute(["--with-j","prior","--with-j","extra","--allow-empty"]);
  assert.equal(result.status,0,result.stderr);
  const missing=execute(["--allow-empty"]);
  assert.equal(missing.status,1);
  assert.match(missing.stderr,/前置|先修单元|节点引用/);
}));
