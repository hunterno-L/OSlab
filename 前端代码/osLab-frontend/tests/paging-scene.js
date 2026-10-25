// Run with: node tests/paging-scene.js
const assert = require("assert").strict;

(async () => {
  const { buildPagingScene, phaseAt, BEATS } = await import(
    "../src/components/theater/pagingScene.ts"
  );

  const scene = buildPagingScene();
  assert.equal(scene.frames, 3, "默认 3 个页框");
  assert.deepEqual(scene.sequence, [1, 2, 3, 1, 4], "默认访问序列");
  assert.equal(scene.faults, 4, "1,2,3,1,4 应有 4 次缺页");
  assert.equal(scene.hits, 1, "第二次访问 1 应命中");
  assert.equal(scene.accesses.length, 5, "应有 5 次访问");

  assert.deepEqual(scene.accesses[0].framesAfter, [1, null, null]);
  assert.deepEqual(scene.accesses[4].framesAfter, [1, 4, 3]);
  assert.equal(scene.accesses[4].evicted, 2, "第 5 次访问应淘汰第 2 页");
  assert.equal(scene.accesses[3].hit, true);
  assert.ok(
    !scene.accesses[3].phases.some((phase) => phase.beat === 4),
    "命中访问不能出现缺页中断拍"
  );

  assert.equal(BEATS.length, 6, "六拍定义必须完整");

  const again = buildPagingScene();
  assert.deepEqual(again, scene, "相同输入必须得到完全相同的场景");

  const last = phaseAt(scene, scene.totalWeight - 1);
  assert.equal(last.access.index, 4, "末尾权重应落在最后一次访问");
  assert.equal(last.phase.beat, 6, "末尾拍应为恢复执行");

  console.log("paging scene passed");
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
