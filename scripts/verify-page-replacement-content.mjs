#!/usr/bin/env node
// Independent deterministic traces for textbook content; not a production judge.
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const contentRoot = new URL("../content/operating-systems/wangdao-2025/", import.meta.url);
const loadJson = async (relative) => JSON.parse(await readFile(new URL(relative, contentRoot), "utf8"));
const data = await loadJson("verification/page-replacement.cases.json");

function run(testCase) {
  const { algorithm, frames: capacity, sequence, initialState } = testCase;
  const pages = initialState ? [...initialState.pages] : [];
  const accessed = initialState ? [...initialState.accessBits] : [];
  const dirty = initialState ? [...initialState.dirtyBits] : [];
  let hand = initialState?.hand || 0;
  const lastUsed = new Map();
  let faults = 0, replacements = 0;
  const evictions = [];
  for (let step = 0; step < sequence.length; step++) {
    const request = typeof sequence[step] === "number" ? { page: sequence[step], write: false } : sequence[step];
    const { page, write } = request;
    let index = pages.indexOf(page);
    if (index >= 0) {
      accessed[index] = 1;
      if (write) dirty[index] = 1;
    } else {
      faults++;
      if (pages.length < capacity) {
        index = pages.length;
        pages.push(page); accessed.push(1); dirty.push(write ? 1 : 0);
        if (["CLOCK", "CLOCK_ENHANCED"].includes(algorithm)) hand = pages.length % capacity;
      } else {
        replacements++;
        if (algorithm === "FIFO") {
          index = hand; hand = (hand + 1) % capacity;
        } else if (algorithm === "LRU") {
          index = pages.reduce((best, item, current) => lastUsed.get(item) < lastUsed.get(pages[best]) ? current : best, 0);
        } else if (algorithm === "OPT") {
          const nextUse = (resident) => {
            const next = sequence.findIndex((item, position) => position > step && (typeof item === "number" ? item : item.page) === resident);
            return next < 0 ? Infinity : next;
          };
          index = pages.reduce((best, item, current) => nextUse(item) > nextUse(pages[best]) ? current : best, 0);
        } else if (algorithm === "CLOCK") {
          while (accessed[hand] === 1) { accessed[hand] = 0; hand = (hand + 1) % capacity; }
          index = hand; hand = (hand + 1) % capacity;
        } else if (algorithm === "CLOCK_ENHANCED") {
          index = -1;
          while (index < 0) {
            for (let offset = 0; offset < capacity; offset++) {
              const candidate = (hand + offset) % capacity;
              if (accessed[candidate] === 0 && dirty[candidate] === 0) { index = candidate; break; }
            }
            if (index >= 0) break;
            for (let offset = 0; offset < capacity; offset++) {
              const candidate = (hand + offset) % capacity;
              if (accessed[candidate] === 0 && dirty[candidate] === 1) { index = candidate; break; }
              accessed[candidate] = 0;
            }
          }
          hand = (index + 1) % capacity;
        } else throw new Error(`Unsupported algorithm: ${algorithm}`);
        evictions.push([step + 1, pages[index]]);
        pages[index] = page; accessed[index] = 1; dirty[index] = write ? 1 : 0;
      }
    }
    lastUsed.set(page, step);
  }
  return { faults, replacements, evictions, lastEvicted: evictions.at(-1)?.[1], finalPages: pages, accessBits: accessed, dirtyBits: dirty, hand };
}

for (const testCase of data.cases) {
  const actual = run(testCase);
  for (const [key, expected] of Object.entries(testCase.expected)) {
    assert.deepEqual(actual[key], expected, `${testCase.caseCode}: ${key}`);
  }
  console.log(`${testCase.caseCode}: 缺页 ${actual.faults} / 换出 ${actual.replacements}，通过`);
}

// Q50 can also be solved without assuming an initial empty memory: use four
// distinct pages in reverse history. This cross-check avoids copying a trace bug.
const recentDistinct = [...new Set([...data.question50History].reverse())].slice(0, 4);
assert.deepEqual(recentDistinct, [5, 4, 8, 2]);
const questionPackage = await loadJson("questions/a-memory-page-replacement-001.json");
const question50 = questionPackage.questions.find((item) => item.questionCode === "q.wd25.s3_2.050.adapt1");
assert.equal(question50.answer, "A");
assert.equal(question50.options.find((item) => item.key === question50.answer).value, String(recentDistinct.at(-1)));
assert.ok(question50.content.includes(data.question50History.join(",")), "第50题题面序列需与核验数据一致");
console.log(`全部 ${data.cases.length} 个算法案例及第50题倒序独立核对通过。此脚本没有验证线上算法服务。`);
