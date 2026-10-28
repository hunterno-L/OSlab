// Standalone C++ source and behavior checks. Run: node tests/simulation-cpp.js
// The full reference implementations live beside this test; the teaching pane
// ships the compact C++11 versions from public/algorithm-code.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const sourceRoot = path.resolve(__dirname, "algorithm-code");
const displayRoot = path.resolve(__dirname, "../public/algorithm-code");
const compiler =
  process.env.CXX ||
  (process.platform === "win32" && fs.existsSync("D:/mingw64/bin/g++.exe")
    ? "D:/mingw64/bin/g++.exe"
    : "g++");
const tempRoot = path.resolve(os.tmpdir());
const buildRoot = fs.mkdtempSync(path.join(tempRoot, "oslab-cpp-"));
// Captured by executing the corresponding original teaching algorithm methods.
// CPU: dispatch slices; allocation: original partition IDs; page: final frames;
// disk: requested-track visit order (SCAN decreases; C-SCAN increases and wraps).
const originalFixtures = {
  "cpu.fcfs": {
    slices: [
      ["A", 1, 6],
      ["C", 6, 12],
      ["B", 12, 15],
      ["D", 15, 21],
    ],
  },
  "cpu.sjf": {
    slices: [
      ["A", 1, 9],
      ["B", 9, 11],
      ["D", 11, 14],
      ["C", 14, 24],
    ],
  },
  "cpu.hrrn": {
    slices: [
      ["A", 1, 9],
      ["B", 9, 11],
      ["D", 11, 14],
      ["C", 14, 24],
    ],
  },
  "cpu.priority_nonpreemptive": {
    slices: [
      ["A", 1, 3],
      ["C", 3, 6],
      ["B", 6, 8],
      ["D", 10, 14],
    ],
  },
  "cpu.priority_preemptive": {
    slices: [
      ["A", 0, 2],
      ["B", 2, 7],
      ["A", 7, 8],
      ["C", 8, 18],
      ["D", 18, 22],
      ["A", 22, 25],
    ],
  },
  "cpu.rr": {
    slices: [
      ["A", 0, 2],
      ["B", 2, 4],
      ["C", 4, 6],
      ["D", 6, 8],
      ["A", 8, 10],
      ["B", 10, 12],
      ["C", 12, 14],
      ["D", 14, 16],
      ["A", 16, 18],
      ["B", 18, 20],
      ["C", 20, 22],
      ["D", 22, 24],
      ["A", 24, 26],
      ["B", 26, 28],
      ["C", 28, 29],
      ["D", 29, 31],
      ["A", 31, 33],
      ["D", 33, 35],
      ["D", 35, 37],
      ["D", 37, 38],
    ],
  },
  "cpu.mlfq": {
    slices: [
      ["B", 0, 1, 1],
      ["A", 1, 2, 1],
      ["C", 2, 3, 1],
      ["D", 3, 4, 1],
      ["B", 4, 6, 2],
      ["A", 6, 8, 2],
      ["C", 8, 10, 2],
      ["D", 10, 12, 2],
      ["B", 12, 16, 3],
      ["A", 16, 20, 3],
      ["C", 20, 23, 3],
      ["D", 23, 25, 3],
      ["B", 25, 26, 3],
      ["A", 26, 30, 3],
      ["A", 30, 32, 3],
    ],
  },
  "allocation.first_fit": {
    allocations: [
      ["work0", 0, 50, 40],
      ["work1", 2, 70, 0],
      ["work2", 1, 50, 0],
      ["work3", 4, 100, 10],
    ],
    failures: 0,
  },
  "allocation.best_fit": {
    allocations: [
      ["work0", 1, 50, 0],
      ["work1", 0, 90, 0],
      ["work2", 2, 40, 30],
      ["work3", 4, 100, 10],
      ["work4", 2, 15, 15],
    ],
    failures: 0,
  },
  "allocation.worst_fit": {
    allocations: [
      ["work0", 0, 50, 40],
      ["work1", 3, 70, 20],
      ["work2", 2, 50, 20],
    ],
    failures: 1,
  },
  "allocation.next_fit": {
    allocations: [
      ["work0", 2, 70, 0],
      ["work1", 5, 90, 0],
      ["work2", 6, 5, 0],
      ["work3", 0, 5, 45],
    ],
    failures: 0,
  },
  "page.fifo": { faults: 9, replacements: 6, frames: [5, 2, 1] },
  "page.lru": { faults: 10, replacements: 7, frames: [2, 1, 5] },
  "page.opt": { faults: 7, replacements: 4, frames: [1, 3, 5] },
  "disk.fcfs": { order: [95, 183, 37, 122, 14, 130, 65, 80], total: 628 },
  "disk.sstf": { order: [90, 100, 130, 140, 60, 20], total: 180 },
  "disk.scan": { order: [37, 14, 65, 80, 95, 122, 130, 183], total: 208 },
  "disk.cscan": { order: [65, 80, 95, 122, 130, 183, 14, 37], total: 322 },
};
const executables = new Map();
let executions = 0;

function execute(key, input = "", expectedStatus = 0) {
  const result = spawnSync(executables.get(key), [], {
    input,
    encoding: "utf8",
    timeout: 10000,
    windowsHide: true,
  });
  assert.ifError(result.error);
  assert.equal(result.status, expectedStatus, `${key}: ${result.stderr}`);
  executions += 1;
  return result.stdout;
}
function slices(output, withQueue = false) {
  return [...output.matchAll(/^RUN (\S+) (\d+) (\d+)(?: Q(\d+))?$/gm)].map(
    (match) => [
      match[1],
      +match[2],
      +match[3],
      ...(withQueue ? [+match[4]] : []),
    ]
  );
}
function allocations(output) {
  return [...output.matchAll(/^ALLOCATE (\S+) (\d+) (\d+) (\d+)$/gm)].map(
    (match) => [match[1], +match[2], +match[3], +match[4]]
  );
}
function diskOrder(output) {
  return [...output.matchAll(/^(?:SEEK|JUMP) (\d+) (\d+) /gm)].map(
    (match) => +match[2]
  );
}
function number(output, label) {
  const match = output.match(new RegExp(`^${label} (\\d+)$`, "m"));
  assert.ok(match, `missing ${label}: ${output}`);
  return +match[1];
}
function frameStates(output) {
  return output
    .trim()
    .split("\n")
    .filter((line) => line.includes(" FRAMES "))
    .map((line) =>
      line
        .split(" FRAMES ")[1]
        .trim()
        .split(" ")
        .map((value) => (value === "-" ? -1 : +value))
    );
}
function checkCpuConservation(output, jobs) {
  const observed = slices(output);
  const durations = new Map();
  let end = -1;
  for (const [name, start, finish] of observed) {
    assert.ok(
      start >= end && finish >= start,
      "CPU slices must not overlap or go backwards"
    );
    assert.ok(
      start >= jobs.find((job) => job.name === name).arrival,
      "ran before arrival"
    );
    durations.set(name, (durations.get(name) || 0) + finish - start);
    end = finish;
  }
  for (const job of jobs)
    assert.equal(durations.get(job.name), job.burst, job.name);
}

try {
  const keys = Object.keys(originalFixtures).sort();
  assert.deepEqual(
    fs
      .readdirSync(sourceRoot)
      .filter((name) => name.endsWith(".cpp"))
      .sort(),
    keys.map((key) => `${key}.cpp`)
  );
  const phases = new Set([
    "initialize",
    "ready",
    "select",
    "run",
    "idle",
    "complete",
    "preempt",
    "yield",
    "demote",
    "search",
    "allocate",
    "fail",
    "hit",
    "fault",
    "replace",
    "load",
    "seek",
    "boundary",
    "jump",
    "result",
  ]);
  for (const key of keys) {
    const source = path.join(sourceRoot, `${key}.cpp`);
    const text = fs.readFileSync(source, "utf8");
    const lines = text.split(/\r?\n/);
    const found = new Set();
    lines.forEach((line, index) => {
      const marker = line.match(/^\s*\/\/ @trace:(\w+)\s*$/);
      if (!marker) return;
      assert.ok(phases.has(marker[1]), `${key}: unknown trace phase`);
      found.add(marker[1]);
      const statement = lines
        .slice(index + 1)
        .find((next) => next.trim() && !next.trim().startsWith("//"));
      assert.ok(
        statement && !/^[{}]\s*$/.test(statement.trim()),
        `${key}: marker has no statement`
      );
    });
    assert.ok(
      found.has("initialize") && found.has("result"),
      `${key}: missing lifecycle marker`
    );
    const executable = path.join(
      buildRoot,
      `${key}${process.platform === "win32" ? ".exe" : ""}`
    );
    const compilation = spawnSync(
      compiler,
      [
        "-std=c++17",
        "-Wall",
        "-Wextra",
        "-Werror",
        "-pedantic",
        source,
        "-o",
        executable,
      ],
      { encoding: "utf8", timeout: 60000, windowsHide: true }
    );
    assert.ifError(compilation.error);
    assert.equal(compilation.status, 0, `${key}: ${compilation.stderr}`);
    executables.set(key, executable);
  }

  // The teaching pane ships compact C++11 programs. Keep them complete and
  // syntax-clean, but use the reference implementations above for fixtures.
  assert.deepEqual(
    fs
      .readdirSync(displayRoot)
      .filter((name) => name.endsWith(".cpp"))
      .sort(),
    keys.map((key) => `${key}.cpp`)
  );
  for (const key of keys) {
    const file = path.join(displayRoot, `${key}.cpp`);
    const text = fs.readFileSync(file, "utf8");
    assert.match(text, /\bint\s+main\s*\(/, `${key}: display needs main`);
    assert.match(
      text,
      /\/\/\s*@trace:initialize/,
      `${key}: display initialize marker`
    );
    assert.match(text, /\/\/\s*@trace:result/, `${key}: display result marker`);
    const lines = text.split(/\r?\n/);
    lines.forEach((line, index) => {
      const marker = line.match(/^\s*\/\/\s*@trace:(\w+)\s*$/);
      if (!marker) return;
      const statement = lines
        .slice(index + 1)
        .find((next) => next.trim() && !next.trim().startsWith("//"));
      assert.ok(
        statement && !/^[{}]\s*$/.test(statement.trim()),
        `${key}: display marker ${marker[1]} has no statement`
      );
    });
    const syntax = spawnSync(
      compiler,
      [
        "-std=c++11",
        "-fsyntax-only",
        "-Wall",
        "-Wextra",
        "-Werror",
        "-Wno-missing-field-initializers",
        "-pedantic",
        file,
      ],
      { encoding: "utf8", timeout: 60000, windowsHide: true }
    );
    assert.ifError(syntax.error);
    assert.equal(
      syntax.status,
      0,
      `${key}: display C++11 check\n${syntax.stderr}`
    );
  }

  for (const [key, fixture] of Object.entries(originalFixtures)) {
    const output = execute(key);
    if (key.startsWith("cpu."))
      assert.deepEqual(slices(output, key === "cpu.mlfq"), fixture.slices, key);
    else if (key.startsWith("allocation.")) {
      assert.deepEqual(allocations(output), fixture.allocations, key);
      assert.equal(number(output, "failed"), fixture.failures, key);
    } else if (key.startsWith("page.")) {
      assert.equal(number(output, "faults"), fixture.faults, key);
      assert.equal(number(output, "replacements"), fixture.replacements, key);
      assert.deepEqual(frameStates(output).at(-1), fixture.frames, key);
    } else {
      assert.deepEqual(diskOrder(output), fixture.order, key);
      assert.equal(number(output, "total_movement"), fixture.total, key);
    }
    execute(key, "not-a-number\n", 1);
    execute(key, "999999999999999999999999999999999999999", 1);
    assert.equal(
      execute(key, " \n\t"),
      output,
      `${key}: whitespace EOF defaults`
    );
  }

  // The original exchange sort can reverse equal-arrival entries after a swap.
  assert.deepEqual(slices(execute("cpu.fcfs", "3\nX 4 2\nY 4 1\nZ 1 1\n")), [
    ["Z", 1, 2],
    ["Y", 4, 5],
    ["X", 5, 7],
  ]);
  // SJF's first dispatch follows first arrival, even when a shorter job is already ready.
  assert.deepEqual(slices(execute("cpu.sjf", "3\nA 0 9\nB 0 1\nC 0 3\n")), [
    ["A", 0, 9],
    ["B", 9, 10],
    ["C", 10, 13],
  ]);
  // After an idle gap SJF also dispatches the first remaining arrival first.
  assert.deepEqual(
    slices(execute("cpu.sjf", "5\nP0 7 1\nP1 0 2\nP2 7 3\nP3 0 2\nP4 9 3\n")),
    [
      ["P1", 0, 2],
      ["P3", 2, 4],
      ["P2", 7, 10],
      ["P0", 10, 11],
      ["P4", 11, 14],
    ]
  );
  assert.deepEqual(slices(execute("cpu.hrrn", "3\nA 0 4\nB 1 3\nC 2 1\n")), [
    ["A", 0, 4],
    ["C", 4, 5],
    ["B", 5, 8],
  ]);
  assert.deepEqual(
    slices(
      execute("cpu.priority_nonpreemptive", "3\nA 0 3 1\nB 0 1 9\nC 0 2 5\n")
    ),
    [
      ["B", 0, 1],
      ["C", 1, 3],
      ["A", 3, 6],
    ]
  );
  // Equal priority never preempts; higher priority does.
  assert.deepEqual(
    slices(
      execute("cpu.priority_preemptive", "3\nA 0 6 1\nB 2 1 1\nC 3 1 2\n")
    ),
    [
      ["A", 0, 3],
      ["C", 3, 4],
      ["B", 4, 5],
      ["A", 5, 8],
    ]
  );
  // RR appends residual work behind every original row, including future arrivals.
  assert.deepEqual(slices(execute("cpu.rr", "2 2\nA 0 5\nB 10 1\n")), [
    ["A", 0, 2],
    ["B", 10, 11],
    ["A", 11, 13],
    ["A", 13, 14],
  ]);
  const preemptedMlfq = execute("cpu.mlfq", "2 2\n1 3\nA 0 8\nB 2 1\n");
  assert.deepEqual(slices(preemptedMlfq, true), [
    ["A", 0, 1, 1],
    ["A", 1, 2, 2],
    ["B", 2, 3, 1],
    ["A", 3, 6, 2],
    ["A", 6, 9, 2],
  ]);
  assert.match(preemptedMlfq, /^PREEMPT A Q2$/m);
  // Arrival gaps and a last-queue preemption must finish without indexing past a queue.
  for (const key of keys.filter((key) => key.startsWith("cpu."))) {
    const header =
      key === "cpu.rr" ? "3 2" : key === "cpu.mlfq" ? "3 3\n1 2 4" : "3";
    const priority = key.includes("priority_");
    const jobs = [
      { name: "A", arrival: 0, burst: 9 },
      { name: "B", arrival: 5, burst: 2 },
      { name: "C", arrival: 20, burst: 1 },
    ];
    const input =
      `${header}\n` +
      jobs
        .map(
          (job, index) =>
            `${job.name} ${job.arrival} ${job.burst}${
              priority ? ` ${index + 1}` : ""
            }`
        )
        .join("\n") +
      "\n";
    checkCpuConservation(execute(key, input), jobs);
  }

  for (const key of keys.filter((key) => key.startsWith("allocation."))) {
    const output = execute(key, "1 4\n100\nA 30\nB 20\nC 60\nD 50\n");
    assert.deepEqual(allocations(output), [
      ["A", 0, 30, 70],
      ["B", 0, 20, 50],
      ["D", 0, 50, 0],
    ]);
    assert.equal(number(output, "failed"), 1);
    assert.equal(number(output, "total_free"), 0);
  }
  assert.deepEqual(
    allocations(
      execute("allocation.next_fit", "2 3\n10 100\nA 30\nB 20\nC 5\n")
    ),
    [
      ["A", 1, 30, 70],
      ["B", 1, 20, 50],
      ["C", 1, 5, 45],
    ]
  );
  // Re-sorting after each allocation means equal sizes retain the current sorted order.
  assert.deepEqual(
    allocations(execute("allocation.best_fit", "3 2\n30 20 10\nA 10\nB 20\n")),
    [
      ["A", 2, 10, 0],
      ["B", 1, 20, 0],
    ]
  );

  // Independent small reference simulations cover hits, frame order and victim selection.
  const referencePages = (kind, capacity, references) => {
    const frames = Array(capacity).fill(-1);
    const last = Array(capacity).fill(-1);
    const states = [];
    let faults = 0,
      replacements = 0,
      cursor = 0;
    references.forEach((page, index) => {
      let slot = frames.indexOf(page);
      if (slot < 0) {
        faults += 1;
        slot = frames.indexOf(-1);
        if (slot < 0) {
          replacements += 1;
          if (kind === "fifo") {
            slot = cursor;
            cursor = (cursor + 1) % capacity;
          }
          if (kind === "lru") slot = last.indexOf(Math.min(...last));
          if (kind === "opt") {
            const next = frames.map((resident) => {
              const nextIndex = references.indexOf(resident, index + 1);
              return nextIndex < 0 ? references.length : nextIndex;
            });
            slot = next.indexOf(Math.max(...next));
          }
        }
        frames[slot] = page;
      }
      last[slot] = index;
      states.push([...frames]);
    });
    return { faults, replacements, states };
  };
  for (const kind of ["fifo", "lru", "opt"]) {
    for (const capacity of [1, 2, 4]) {
      const references = [0, 1, 0, 2, 3, 2, 1, 4, 0, 4, 1];
      const output = execute(
        `page.${kind}`,
        `${capacity} ${references.length}\n${references.join(" ")}\n`
      );
      const expected = referencePages(kind, capacity, references);
      assert.equal(number(output, "faults"), expected.faults);
      assert.equal(number(output, "replacements"), expected.replacements);
      assert.deepEqual(frameStates(output), expected.states);
    }
    const empty = execute(`page.${kind}`, "3 0\n");
    assert.equal(number(empty, "faults"), 0);
    assert.match(empty, /fault_rate 0\.00%/);
  }
  // OPT's no-future marker must remain greater than every index beyond 1000.
  const longReferences = [1, 2, 3, ...Array(1000).fill(3), 2];
  const longOpt = execute(
    "page.opt",
    `2 ${longReferences.length}\n${longReferences.join(" ")}\n`
  );
  assert.equal(number(longOpt, "faults"), 3);
  assert.deepEqual(frameStates(longOpt).at(-1), [3, 2]);

  // SSTF sorts tracks first, so a distance tie chooses the lower track.
  assert.deepEqual(diskOrder(execute("disk.sstf", "2 50\n60 40\n")), [40, 60]);
  // No forced physical endpoint: these totals distinguish LOOK/C-LOOK from textbook SCAN.
  const scan = execute("disk.scan", "4 50\n10 40 60 90\n");
  assert.deepEqual(diskOrder(scan), [40, 10, 60, 90]);
  assert.equal(number(scan, "total_movement"), 120);
  const cscan = execute("disk.cscan", "4 50\n10 40 60 90\n");
  assert.deepEqual(diskOrder(cscan), [60, 90, 10, 40]);
  assert.equal(number(cscan, "total_movement"), 150);
  for (const key of keys.filter((key) => key.startsWith("disk."))) {
    const duplicate = execute(key, "3 50\n50 50 70\n");
    assert.equal(diskOrder(duplicate).length, 3);
    assert.equal(number(duplicate, "total_movement"), 20);
    assert.equal(number(execute(key, "0 50\n"), "total_movement"), 0);
  }
  assert.deepEqual(diskOrder(execute("disk.scan", "2 5\n20 10\n")), [10, 20]);
  assert.deepEqual(
    diskOrder(execute("disk.cscan", "2 100\n20 10\n")),
    [10, 20]
  );

  console.log(
    `C++ checks passed: ${keys.length} reference programs (C++17) plus ${keys.length} teaching sources (C++11); ${executions} executions; original default fixtures and edge cases.`
  );
} finally {
  // Verify the resolved target belongs to the temp directory before recursive cleanup.
  assert.equal(path.dirname(path.resolve(buildRoot)), tempRoot);
  assert.ok(path.basename(buildRoot).startsWith("oslab-cpp-"));
  fs.rmSync(buildRoot, { recursive: true, force: true });
}
