// Run with: node tests/course-catalog-multi-course.js
// 多课程目录契约：侧栏必须列出全部 publishedUnitCount>0 的课程，
// 每门课独立加载 outline、显示自己的单元数与徽章，并支持跨课程定位。
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");
const { parse } = require("@vue/compiler-sfc");
const ts = require("typescript");
const vue = require("vue");

const root = path.join(__dirname, "..");
const read = (file) => fs.readFileSync(path.join(root, file), "utf8");

function transpile(source) {
  return ts.transpileModule(source, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.CommonJS,
      esModuleInterop: true,
    },
  }).outputText;
}

function evaluate(source, requireDependency, macros = {}) {
  const module = { exports: {} };
  new Function(
    "require",
    "module",
    "exports",
    "defineProps",
    "defineEmits",
    source
  )(
    requireDependency,
    module,
    module.exports,
    macros.defineProps,
    macros.defineEmits
  );
  return module.exports;
}

const dbCourse = {
  courseCode: "course.database-systems",
  title: "数据库系统",
  description: "关系模型与 SQL",
  status: "PUBLISHED",
  publishedUnitCount: 1,
  completedUnitCount: 1,
  progressPercent: 100,
};
const osCourse = {
  courseCode: "course.operating-systems",
  title: "操作系统",
  description: "进程、内存与文件系统",
  status: "PUBLISHED",
  publishedUnitCount: 104,
  completedUnitCount: 12,
  progressPercent: 11,
};
const draftCourse = {
  courseCode: "course.draft",
  title: "未发布课程",
  status: "DRAFT",
  publishedUnitCount: 0,
  completedUnitCount: 0,
  progressPercent: 0,
};

const dbOutline = {
  courseCode: dbCourse.courseCode,
  title: dbCourse.title,
  progress: {
    status: "IN_PROGRESS",
    goalStatus: "IN_PROGRESS",
    requiredCount: 1,
    completedCount: 1,
    percent: 100,
  },
  sections: [
    {
      sectionCode: "sec.db",
      title: "关系数据库",
      textbookNumber: "第 1 章",
      children: [
        {
          sectionCode: "sec.db.fd",
          parentSectionCode: "sec.db",
          title: "函数依赖",
          units: [
            {
              unitCode: "unit.db.fd",
              courseCode: dbCourse.courseCode,
              sectionCode: "sec.db.fd",
              title: "函数依赖与候选键",
              estimatedMinutes: 20,
            },
          ],
        },
      ],
    },
  ],
};

function makeOsOutline() {
  const sections = [];
  let unitIndex = 0;
  for (let chapter = 1; chapter <= 5; chapter += 1) {
    const chapterCode = `sec.os.ch${chapter}`;
    const children = [];
    const childCount = chapter === 1 ? 10 : 6;
    for (let child = 1; child <= childCount; child += 1) {
      const childCode = `${chapterCode}.s${child}`;
      const units = [];
      const unitCount = Math.min(
        Math.ceil((104 - unitIndex) / (childCount - child + 1)),
        6
      );
      for (let unit = 1; unit <= unitCount && unitIndex < 104; unit += 1) {
        unitIndex += 1;
        units.push({
          unitCode: `unit.os.${unitIndex}`,
          courseCode: osCourse.courseCode,
          sectionCode: childCode,
          title: `操作系统单元 ${unitIndex}`,
          sortOrder: unitIndex,
        });
      }
      children.push({
        sectionCode: childCode,
        parentSectionCode: chapterCode,
        title: `小节 ${chapter}.${child}`,
        units,
      });
    }
    sections.push({
      sectionCode: chapterCode,
      title: `第 ${chapter} 章`,
      textbookNumber: `${chapter}`,
      children,
    });
  }
  assert.equal(unitIndex, 104, "fixture must expose exactly 104 OS units");
  return {
    courseCode: osCourse.courseCode,
    title: osCourse.title,
    progress: {
      status: "IN_PROGRESS",
      goalStatus: "IN_PROGRESS",
      requiredCount: 104,
      completedCount: 12,
      percent: 11,
    },
    sections,
  };
}

const osOutline = makeOsOutline();

function collectSections(sections, result = []) {
  for (const section of sections) {
    result.push(section);
    if (section.children) collectSections(section.children, result);
  }
  return result;
}

async function assertCatalogLoadsEveryCourse() {
  const outlineCalls = [];
  const api = {
    listCourses: async () => [dbCourse, osCourse, draftCourse],
    getCourseOutline: async (code) => {
      outlineCalls.push(code);
      if (code === osCourse.courseCode) return osOutline;
      if (code === dbCourse.courseCode) return dbOutline;
      throw new Error(`unexpected outline request: ${code}`);
    },
    getCourseResume: async (code) => ({
      unitCode: code === osCourse.courseCode ? "unit.os.1" : "unit.db.fd",
      activityCode: "activity.1",
      reason: "RESUME",
    }),
  };

  const catalogModule = evaluate(
    transpile(read("src/composables/useCourseCatalog.ts")),
    (name) => {
      if (name === "vue") return vue;
      if (name === "@/api/course") return api;
      if (name === "@/composables/useCurrentCourse")
        return evaluate(
          transpile(read("src/composables/useCurrentCourse.ts")),
          (dep) => {
            if (dep === "vue") return vue;
            if (dep === "@/api/course") return api;
            throw new Error(`unexpected dependency: ${dep}`);
          }
        );
      throw new Error(`unexpected dependency: ${name}`);
    }
  );
  const catalog = catalogModule.useCourseCatalog();
  await catalog.load(dbCourse.courseCode);

  assert.equal(catalog.courseCode.value, dbCourse.courseCode);
  assert.equal(catalog.courseTitle.value, dbCourse.title);
  assert.deepEqual(
    outlineCalls.slice().sort(),
    [dbCourse.courseCode, osCourse.courseCode].sort(),
    "侧栏切换课程不得让其他已发布课程从目录消失"
  );
  assert.deepEqual(
    catalog.publishedCourses.value.map((item) => item.courseCode),
    [dbCourse.courseCode, osCourse.courseCode],
    "publishedUnitCount=0 的课程不得计入侧栏"
  );
  assert.equal(
    catalog.outlines.value[osCourse.courseCode].title,
    osCourse.title
  );
  assert.equal(catalog.courseGroups.value.length, 2);

  const dbGroup = catalog.courseGroups.value.find(
    (group) => group.courseCode === dbCourse.courseCode
  );
  const osGroup = catalog.courseGroups.value.find(
    (group) => group.courseCode === osCourse.courseCode
  );
  assert.equal(dbGroup.publishedUnitCount, 1);
  assert.equal(dbGroup.sections[0].sectionCode, "sec.db");
  assert.equal(osGroup.publishedUnitCount, 104);
  assert.equal(osGroup.sections.length, 5);
  assert.equal(
    collectSections(osGroup.sections).length,
    5 + 34,
    "操作系统目录必须保留完整的章/节层级"
  );

  // 跨课程选择：章节/单元代码反查所属课程后再切换
  assert.equal(catalog.courseCodeOfSection("sec.db.fd"), dbCourse.courseCode);
  assert.equal(
    catalog.courseCodeOfSection("sec.os.ch5.s6"),
    osCourse.courseCode
  );
  assert.equal(catalog.courseCodeOfUnit("unit.db.fd"), dbCourse.courseCode);
  assert.equal(catalog.courseCodeOfUnit("unit.os.104"), osCourse.courseCode);
  assert.equal(catalog.courseCodeOfUnit("unit.missing"), "");
}

function assertSidebarRendersAllGroups() {
  const source = read("src/components/learning/CourseCatalogSidebar.vue");
  const descriptor = parse(source).descriptor;
  assert.ok(descriptor.template, "sidebar must keep its template");
  const template = descriptor.template.content;
  const script = descriptor.scriptSetup.content;

  assert.ok(
    template.includes('v-for="group in catalogGroups"'),
    "侧栏必须遍历全部课程分组，而不是只渲染当前 outline"
  );
  assert.ok(
    template.includes("{{ activeMark }}"),
    "顶部课程徽章必须随当前课程变化"
  );
  assert.ok(
    template.includes(':data-course="courseSlug(group.courseCode)"'),
    "每个课程分组都必须使用自己的课程标识"
  );
  assert.ok(
    template.includes("markOf(group.courseCode)"),
    "分组徽章不得写死操作系统图标"
  );
  assert.ok(
    template.includes("{{ group.publishedUnitCount }} 个单元"),
    "每门课程必须显示自己的已发布单元数"
  );
  assert.ok(
    template.includes("{{ catalogGroups.length }} 门课程"),
    "“全部课程”计数必须显示课程数"
  );
  assert.ok(
    !/<div\s+class="course-logo"\s*>\s*OS\s*<\/div>/.test(template),
    "课程徽章不得再写死 OS"
  );

  const allSections = collectSections(osOutline.sections);
  const props = vue.reactive({
    courseCode: dbCourse.courseCode,
    courseTitle: dbCourse.title,
    sections: dbOutline.sections,
    courses: [dbCourse, osCourse, draftCourse],
    outlines: {
      [dbCourse.courseCode]: dbOutline,
      [osCourse.courseCode]: osOutline,
    },
    selectedSectionCode: "",
    selectedUnitCode: "",
    expandedSections: allSections.map((section) => section.sectionCode),
    loading: false,
    error: "",
    progress: dbOutline.progress,
  });
  const emitted = [];
  const compiled = transpile(
    script +
      "\nexport { catalogGroups, activeGroup, activeMark, courseSlug, markOf, publishedUnitCount, pickCourse };"
  );
  const sidebar = evaluate(
    compiled,
    (name) => {
      if (name === "vue") return vue;
      if (name === "@/api/course") return {};
      throw new Error(`unexpected dependency: ${name}`);
    },
    {
      defineProps: () => props,
      defineEmits: () => (event, payload) => emitted.push({ event, payload }),
    }
  );

  const groups = sidebar.catalogGroups.value;
  assert.equal(groups.length, 2, "未发布课程不得出现在侧栏");
  assert.deepEqual(
    groups.map((group) => group.courseCode),
    [dbCourse.courseCode, osCourse.courseCode]
  );
  assert.deepEqual(
    groups.map((group) => group.publishedUnitCount),
    [1, 104]
  );
  const osGroup = groups.find(
    (group) => group.courseCode === osCourse.courseCode
  );
  assert.equal(
    osGroup.rows.filter((row) => row.kind === "unit").length,
    104,
    "展开后操作系统树必须包含全部 104 个单元"
  );
  assert.equal(
    osGroup.rows.filter((row) => row.kind === "section").length,
    39,
    "展开后必须显示 5 章 + 34 节"
  );

  assert.equal(sidebar.activeMark.value, "DB");
  assert.equal(sidebar.markOf(osCourse.courseCode), "OS");
  assert.equal(sidebar.markOf("course.unknown-course"), "UC");

  sidebar.pickCourse(osCourse.courseCode);
  assert.deepEqual(emitted, [
    { event: "select-course", payload: osCourse.courseCode },
  ]);

  props.courseCode = osCourse.courseCode;
  assert.equal(sidebar.activeMark.value, "OS");
  assert.equal(sidebar.activeGroup.value.courseCode, osCourse.courseCode);
}

function assertViewsUseMultiCourseCatalog() {
  const views = [
    "src/views/knowledgeGraph/KnowledgeGraphView.vue",
    "src/views/learning/CourseUnitView.vue",
    "src/views/learning/LearningQuestionsView.vue",
  ];
  for (const file of views) {
    const source = read(file);
    assert.ok(
      source.includes(':courses="catalogCourses"'),
      `${file} 必须传入全部课程`
    );
    assert.ok(
      source.includes(':outlines="catalogOutlines"'),
      `${file} 必须传入全部 outline`
    );
    assert.ok(
      source.includes("courseCodeOfSection"),
      `${file} 必须按章节反查所属课程`
    );
    assert.ok(
      source.includes("courseCodeOfUnit"),
      `${file} 必须按单元反查所属课程`
    );
  }
  const graph = read(views[0]);
  assert.ok(
    graph.includes('@select-course="handleCatalogCourse"'),
    "图谱页课程头必须处理跨课程切换"
  );
  assert.ok(
    graph.includes("syncAiCourse(targetCourse)"),
    "图谱页切换课程后 AI 会话作用域必须跟随"
  );
  const unit = read(views[1]);
  assert.ok(
    unit.includes('@select-course="goGraphCourse"'),
    "单元页课程头必须保留原入口行为"
  );
  assert.ok(
    unit.includes("courseCodeOfUnit(unitCode)"),
    "单元页跨课程单元切换必须解析目标课程"
  );
}

async function run() {
  await assertCatalogLoadsEveryCourse();
  assertSidebarRendersAllGroups();
  assertViewsUseMultiCourseCatalog();
  console.log(
    "Multi-course catalog contracts passed: both published courses load independently, sidebar renders per-course groups with 104 OS units, dynamic OS/DB marks, course+unit counts, and cross-course scope resolution."
  );
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
