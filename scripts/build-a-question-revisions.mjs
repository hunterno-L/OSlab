import fs from "fs";
import path from "path";

const root = path.resolve(".");
const sourcePath = path.join(
  root,
  "artifacts/content-import/20261019-draft-import/questions-original/questions.json"
);
const source = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
const byCode = new Map(source.questions.map((question) => [question.questionCode, question]));

const textbookBasis = (sectionCode, pageStart, pageEnd, locator) => ({
  sourceCode: "book.wangdao.os.2025",
  origin: "TEXTBOOK",
  sectionCode,
  pageStart,
  pageEnd,
  pageStatus: "VISIBLE",
  locator,
  sourceQuestionNumber: null,
});

const externalSource = (sourceCode, sectionCode, locator) => ({
  sourceCode,
  origin: "EXTERNAL_QUESTION",
  sectionCode,
  pageStart: null,
  pageEnd: null,
  pageStatus: "NOT_VISIBLE",
  locator,
  sourceQuestionNumber: null,
});

const overrides = {
  "q.wd25.s1_3.005": {
    content:
      "用户程序在用户态下通过访管（陷入）指令请求操作系统服务，所引起的中断属于（　）。",
    analysis:
      "访管指令（陷入指令）是一条非特权指令，用户程序主动执行它，使 CPU 从用户态进入内核态，因此属于陷入中断（访管中断）。特权指令只能在核心态执行；用户态试图执行特权指令不是“使用访管指令”，而会触发非法指令异常。本题按教材原题答案 D；题干改为明确的访管请求，避免把“执行特权指令”和“使用非特权访管指令”混为一谈。",
    misconception:
      "把“用户态执行特权指令”当作主动的访管请求；或反过来把非特权的访管指令当作特权指令。",
  },
  "q.wd25.s1_3.016": {
    options: [
      { key: "A", value: "只有特权指令" },
      { key: "B", value: "只有非特权指令" },
      { key: "C", value: "只有\"访管\"指令" },
      { key: "D", value: "指令系统中的全部指令" },
    ],
    analysis:
      "核心态下 CPU 可以执行指令系统中的全部指令，包括特权指令与访管指令；访管指令通常由用户程序在用户态发起，用于进入内核，但它在核心态并不是非法指令。同包第 06、34 题采用“内核可执行任何指令”的口径；原第 16 题的“除访管指令外”与这两题不一致，本次按统一口径修订选项 D 与解析，答案仍为 D。",
    misconception:
      "把“访管指令通常由用户态发起”误读为“核心态禁止执行访管指令”；或混淆教材第 16 题与其他题目的口径。",
  },
  "q.ext.s1_2.001": {
    options: [
      { key: "A", value: "用户独占全机，资源利用率低" },
      { key: "B", value: "主机等待人工操作，CPU 和内存利用率低" },
      { key: "C", value: "人工速度慢，但智能化程度高" },
      { key: "D", value: "脱机输入输出技术缓解了 CPU 等待慢速 I/O 的矛盾" },
    ],
    analysis:
      "A、B 与教材手工操作阶段的两个突出缺点一致。D 描述脱机输入输出通过外围机预先输入、减少 CPU 等待慢速 I/O，从而缓解 CPU 与 I/O 的矛盾；它没有提高硬件本身的物理速度，但也不再使用“提高 CPU 和 I/O 速度”这种错误表述。C 说人工操作“智能化程度高”与手工操作阶段依赖人工干预、效率低的事实相反，故 C 是错误说法。",
    misconception:
      "把脱机输入输出理解为提高硬件速度；或只看到“人工速度慢”就忽略“智能化程度高”这一明显错误。",
    sourceRef: externalSource(
      "external.shititong.os-mcq",
      "sec.overview.history",
      "试题通公开题库「操作系统选择题填空题」第 3 题（https://www.shititong.cn/cha-kan/shiti/0005ee43-68d2-1ff0-c0f9-29e3bb5dbe02.html）；原页标注答案 C。修订仅将 D 项改为可核对的技术表述，题干与其余选项保持原题，未提高硬件速度。"
    ),
    basisSourceRefs: [
      textbookBasis(
        "sec.overview.history",
        7,
        8,
        "教材 1.2.1 手工操作阶段与 1.2.2 单道批处理系统（印刷第 7—8 页）；用于核对人工操作缺点与脱机输入输出。"
      ),
    ],
  },
  "q.wd25.s1_2.009": {
    analysis:
      "优先级调度让重要任务优先获得处理机；非抢占式在进程运行期间不打断它，减少切换与状态保存开销，但若低优先级进程正在运行，新到高优先级任务必须等待其结束，因此它改善的是重要任务的相对响应，而不是严格的最短响应时间策略（抢占式优先级通常响应更快）。A 加大时间片会推迟轮转；B 静态页式管理与响应时间无直接关系；D 可重入代码也不是调度响应因素。答案仍为 C，解析明确该结论的适用边界。",
    misconception:
      "把非抢占式理解为“高优先级任务可以立即打断当前任务”；或忽略抢占式优先级在响应时间上通常更及时。",
  },
  "q.ext.s1_3.001": {
    content: "原语是一种内核中不可分割的公用程序，它的特点是（　）。",
    analysis:
      "原语位于操作系统底层、运行时间短、调用频繁，并且具有原子性：执行过程中不可被中断，只能一气呵成。它不等于“特殊的系统调用命令”；系统调用是用户程序请求内核服务的接口，原语是内核自身的原子操作。B 描述递归，C 普通子程序也可被外层调用，D 功能强不是原语的本质特征。",
    misconception:
      "把原语与系统调用混为一谈；或把递归、可被外层调用、功能强当作原语的本质特征。",
    sourceRef: externalSource(
      "external.offcn.2019.nx-electric.os13",
      "sec.overview.runtime",
      "中公教育「2019 宁夏电力公司校园二批招聘：计算机操作系统自测（13）」第 1 题（http://m.nx.offcn.com/html/2019/01/64488.html）；原页标注答案 A。修订将题干中的“特殊的系统调用命令”改为“内核中不可分割的公用程序”，使术语与原语定义一致。"
    ),
    basisSourceRefs: [
      textbookBasis(
        "sec.overview.runtime",
        16,
        16,
        "教材 1.3.1 第 3 点「原语」（印刷第 16 页）：底层、原子性、运行时间短、调用频繁。"
      ),
    ],
  },
  "q.ext.s1_3.003": {
    content: "按教材对内核四类内容的列举，内核不包括的子系统是（　）。",
    analysis:
      "教材把内核内容列为时钟管理、中断机制、原语、系统控制的数据结构及处理；其中最后一类明确包括进程管理、存储器管理、设备管理。A/B/C 分别对应设备、存储器、进程管理；D「硬件管理系统」不是教材列举的内核子系统名称。时钟、中断、设备驱动属于贴近硬件的内核模块，但不能据此把「硬件管理系统」当作一个独立子系统。答案仍为 D，题干已限定按教材列举口径。",
    misconception:
      "把贴近硬件的时钟、中断、设备驱动直接等同于一个名为“硬件管理系统”的子系统；或忽略题干限定的教材四类内容口径。",
    sourceRef: externalSource(
      "external.huatu.gongji.kernel-subsystems",
      "sec.overview.runtime",
      "华图在线公基题库「内核不包括的子系统是」（https://v.huatu.com/gktk/2eqlv.html）；原页标注参考答案 D。修订将题干限定为教材四类内容列举，以消除“硬件管理系统”广义理解造成的歧义，选项保持不变。"
    ),
    basisSourceRefs: [
      textbookBasis(
        "sec.overview.runtime",
        15,
        16,
        "教材 1.3.1「大多数操作系统的内核包括 4 方面内容」（印刷第 15—16 页）；用于核对内核四类内容及进程/存储/设备管理。"
      ),
    ],
  },
};

const questions = Object.entries(overrides).map(([questionCode, override]) => {
  const original = byCode.get(questionCode);
  if (!original) throw new Error(`未知 questionCode: ${questionCode}`);
  return {
    ...JSON.parse(JSON.stringify(original)),
    ...override,
  };
});

const output = {
  schemaVersion: "oslab-authoring/1.0",
  packageType: "QUESTIONS",
  batchCode: "batch.overview.questions.revisions.001",
  contributor: "OSLab agent（A 六题修订草案）",
  courseCode: "course.operating-systems",
  note:
    "本包只包含 A 待核的六道题，作为新修订另存，不覆盖原件、不改变历史题目 revision。" +
    "外部题已改用 EXTERNAL_QUESTION 来源并登记实际题源；教材知识依据另存 basisSourceRefs。" +
    "三道外部题来源授权标记为待 L 确认，确认前不进入正式单元映射，也不执行 preview/commit/publish。",
  questions,
};

const outDir = path.join(root, "artifacts/course-first-chapter/20261020");
fs.mkdirSync(outDir, { recursive: true });
const outFile = path.join(outDir, "a-six-question-revisions.draft.json");
fs.writeFileSync(outFile, `${JSON.stringify(output, null, 2)}\n`, "utf8");
console.log(`Wrote ${outFile}`);
console.log(`Questions: ${questions.length}`);
