// Run with: node tests/current-course-behavior.js
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");
const ts = require("typescript");
const vue = require("vue");

const read = (file) =>
  fs.readFileSync(path.join(__dirname, "..", file), "utf8");
function load(file, dependencies) {
  const js = ts.transpileModule(read(file), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
    },
  }).outputText;
  const module = { exports: {} };
  new Function("require", "module", "exports", js)(
    (name) => {
      if (!(name in dependencies))
        throw new Error(`unexpected dependency ${name}`);
      return dependencies[name];
    },
    module,
    module.exports
  );
  return module.exports;
}
function deferred() {
  let resolve;
  const promise = new Promise((r) => {
    resolve = r;
  });
  return { promise, resolve };
}
const os = {
  courseCode: "course.operating-systems",
  title: "OS",
  publishedUnitCount: 1,
};
const db = {
  courseCode: "course.database-systems",
  title: "DB",
  publishedUnitCount: 1,
};
const draft = {
  courseCode: "course.draft",
  title: "Draft",
  publishedUnitCount: 0,
};
const storage = new Map();
global.sessionStorage = {
  getItem: (key) => (storage.has(key) ? storage.get(key) : null),
  setItem: (key, value) => storage.set(key, value),
  removeItem: (key) => storage.delete(key),
};

async function run() {
  const requests = [];
  const api = {
    listCourses: () => {
      const request = deferred();
      requests.push(request);
      return request.promise;
    },
  };
  const course = load("src/composables/useCurrentCourse.ts", {
    vue,
    "@/api/course": api,
  });
  assert.equal(
    course.selectAvailableCourse([db, os, draft]).courseCode,
    os.courseCode,
    "OS wins when no valid selection exists"
  );
  assert.equal(
    course.selectAvailableCourse([db, os], db.courseCode).courseCode,
    db.courseCode,
    "explicit available selection wins"
  );
  assert.equal(
    course.selectAvailableCourse([os], db.courseCode).courseCode,
    os.courseCode,
    "unavailable explicit selection cannot escape server scope"
  );
  assert.equal(
    course.selectAvailableCourse([draft]),
    null,
    "no published course yields empty scope"
  );
  assert.equal(course.currentCourseCode.value, "");
  const old = course.resolveCurrentCourse(os.courseCode);
  const fresh = course.resolveCurrentCourse(db.courseCode);
  requests[1].resolve([os, db]);
  await fresh;
  requests[0].resolve([os, db]);
  assert.equal(
    (await old).courseCode,
    db.courseCode,
    "older caller receives newer valid course instead of an empty result"
  );
  assert.equal(
    course.currentCourseCode.value,
    db.courseCode,
    "late list response cannot replace newer choice"
  );

  const stillMounted = course.resolveCurrentCourse(os.courseCode);
  const unmounted = course.resolveCurrentCourse(db.courseCode, () => false);
  requests[2].resolve([os, db]);
  requests[3].resolve([os, db]);
  assert.equal(await unmounted, null);
  assert.equal(
    (await stillMounted).courseCode,
    os.courseCode,
    "an unmounted newer caller does not suppress the active page choice"
  );

  const slow = deferred();
  const catalogApi = {
    listCourses: async () => [os, db],
    getCourseOutline: (code) =>
      code === db.courseCode
        ? slow.promise
        : Promise.resolve({ courseCode: code, title: "OS", sections: [] }),
    getCourseResume: async () => ({ reason: "RESUME", unitCode: "u" }),
  };
  const catalog = load("src/composables/useCourseCatalog.ts", {
    vue,
    "@/api/course": catalogApi,
    "@/composables/useCurrentCourse": course,
  }).useCourseCatalog();
  await catalog.load(os.courseCode);
  assert.equal(
    catalog.loading.value,
    false,
    "slow unrelated course does not block active course"
  );
  assert.equal(catalog.resume.value.unitCode, "u");
  assert.equal(catalog.outlineLoading.value[db.courseCode], true);
  slow.resolve({ courseCode: db.courseCode, title: "DB", sections: [] });
  await Promise.resolve();
  await Promise.resolve();
  assert.equal(
    catalog.outlines.value[db.courseCode].title,
    "DB",
    "other course appears when its request finishes"
  );
  console.log(
    "Current course selection, stale responses and progressive catalog: passed"
  );
}
run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
