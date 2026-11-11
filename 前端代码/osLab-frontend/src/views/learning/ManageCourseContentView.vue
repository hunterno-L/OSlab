<template>
  <div class="manage-course">
    <header>
      <h1>课程内容管理</h1>
      <p>接收成员资源包，先校验、导入草稿，再审核与发布。</p>
    </header>
    <div class="controls">
      <a-select
        v-model="courseCode"
        aria-label="选择课程"
        placeholder="选择课程"
        ><a-option
          v-for="course in availableCourses"
          :key="course.courseCode"
          :value="course.courseCode"
          >{{ course.title }} · {{ course.courseCode }}</a-option
        ></a-select
      ><a-button @click="loadCatalog">刷新资源清单</a-button>
    </div>
    <section v-if="catalog" class="panel">
      <h2>已登记教学块</h2>
      <details>
        <summary>共 {{ catalog.chunks.length }} 个教学块</summary>
        <div class="controls">
          <a-button
            v-for="chunk in catalog.chunks"
            :key="chunk.chunkCode"
            size="small"
            @click="chooseReview(chunk.chunkCode)"
            >{{ chunk.title }} · {{ chunk.reviewStatus }}</a-button
          >
        </div>
      </details>
      <pre v-if="reviewDetail">{{ reviewDetail }}</pre>
    </section>
    <a-tabs>
      <a-tab-pane key="registry" title="来源词表与资源"
        ><CourseRegistryEditor
          v-if="catalog"
          :catalog="catalog"
          :course-code="courseCode"
          @changed="loadCatalog"
        />
        <section v-if="catalog?.units?.length" class="panel">
          <h2>单元草稿列表</h2>
          <div class="controls">
            <a-button
              v-for="entry in catalog.units"
              :key="entry.unitCode"
              @click="chooseUnit(entry.unitCode)"
              >{{ entry.title }} ·
              {{ entry.status || entry.reviewStatus }}</a-button
            >
          </div>
          <a-textarea
            v-if="unitJson"
            v-model="unitJson"
            aria-label="单元草稿 JSON"
            :auto-size="{ minRows: 10, maxRows: 26 }"
          />
          <p v-if="unitStatus">当前状态 {{ unitStatus }}</p>
          <div class="controls">
            <a-button :disabled="!unitJson" @click="saveUnit">保存草稿</a-button
            ><a-button
              :disabled="!unitJson"
              @click="review('units', unitCode, 'SUBMIT')"
              >提交审核</a-button
            ><a-button
              :disabled="!unitJson"
              @click="review('units', unitCode, 'APPROVE')"
              >审核通过</a-button
            ><a-button
              :disabled="unitStatus !== 'APPROVED'"
              @click="publishUnit"
              >发布单元</a-button
            >
          </div>
        </section></a-tab-pane
      >
      <a-tab-pane key="import" title="资源包导入">
        <section class="panel">
          <h2>选择 JSON 与相对附件</h2>
          <p>
            选择交回文件夹可同时读取 JSON、assets/ 与
            experiment-files/；也可分别选择文件。文件仅在点击预览时上传。
          </p>
          <div class="controls">
            <label class="file-label"
              >选择资源包文件夹<input
                type="file"
                webkitdirectory
                multiple
                @change="selectFiles" /></label
            ><label class="file-label"
              >选择 JSON / 附件<input
                type="file"
                multiple
                @change="selectFiles" /></label
            ><a-button v-if="files.length" @click="clearFiles">清空</a-button>
          </div>
          <p v-if="files.length">已选择 {{ files.length }} 个文件</p>
          <a-select
            v-if="jsonFiles.length"
            v-model="selectedJson"
            placeholder="选择本次导入的 JSON"
            @change="invalidatePreview"
            ><a-option
              v-for="file in jsonFiles"
              :key="relativePath(file)"
              :value="relativePath(file)"
              >{{ relativePath(file) }}</a-option
            ></a-select
          >
          <p v-if="fileError" role="alert" class="error">{{ fileError }}</p>
          <div class="controls">
            <a-button
              type="primary"
              :loading="busy"
              :disabled="!selectedJson"
              @click="preview"
              >校验并预览</a-button
            ><a-button
              :disabled="!report?.valid || !prepared || committed"
              :loading="busy"
              @click="commit"
              >确认导入草稿</a-button
            >
          </div>
          <p v-if="committed" role="status">
            已导入为草稿。请在下方审核资源并检查单元，发布后学生才可访问。
          </p>
        </section>
        <section v-if="report" class="panel">
          <h2>{{ committed ? "导入结果" : "校验报告" }}</h2>
          <p>
            {{ report.valid ? "校验通过" : "校验未通过" }} · 新增
            {{ report.created || 0 }} · 更新 {{ report.updated || 0 }} · 未变
            {{ report.unchanged || 0 }}
          </p>
          <ul v-if="report.errors?.length" class="error">
            <li v-for="(item, index) in report.errors" :key="index">
              <code>{{ item.path }}</code
              >：{{ item.message }}
            </li>
          </ul>
          <table v-if="report.items?.length">
            <thead>
              <tr>
                <th>资源类型</th>
                <th>稳定代码</th>
                <th>处理</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in report.items" :key="index">
                <td>{{ item.type }}</td>
                <td>{{ item.code }}</td>
                <td>{{ item.action }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </a-tab-pane>
      <a-tab-pane key="unit" title="目录与单元编排">
        <section class="panel">
          <h2>教材目录</h2>
          <p>目录只负责结构与顺序；保存不会发布教学正文。</p>
          <div class="controls">
            <a-input
              v-model="courseCode"
              aria-label="课程代码"
              :input-attrs="{ 'aria-label': '课程代码' }"
              placeholder="courseCode"
            /><a-button :loading="busy" @click="loadOutline">读取目录</a-button
            ><a-button
              :disabled="!outlineJson"
              :loading="busy"
              @click="saveOutline"
              >保存目录</a-button
            >
          </div>
          <a-textarea
            v-if="outlineJson"
            v-model="outlineJson"
            :auto-size="{ minRows: 10, maxRows: 25 }"
            aria-label="课程目录 JSON"
          />
        </section>
        <section class="panel">
          <h2>学习单元</h2>
          <div class="controls">
            <a-input
              v-model="unitCode"
              aria-label="单元代码"
              :input-attrs="{ 'aria-label': '单元代码' }"
              placeholder="unitCode"
            /><a-button :loading="busy" @click="loadUnit">读取草稿</a-button
            ><a-button :loading="busy" @click="createUnit">新建单元</a-button
            ><a-button :disabled="!unitJson" :loading="busy" @click="saveUnit"
              >保存单元草稿</a-button
            >
          </div>
          <p v-if="unitStatus">当前状态：{{ unitStatus }}</p>
          <p>
            编辑目标、节点映射、活动顺序与完成条件。修改已发布单元时，学生继续使用当前发布版本。
          </p>
          <LearningUnitForm
            v-if="catalog"
            :model-value="unitJson"
            :nodes="catalog.nodes || []"
            @update:model-value="updateUnitJson"
          />
          <a-collapse style="margin-top: 10px">
            <a-collapse-item
              header="高级 JSON（可直接编辑完整单元定义）"
              key="json"
            >
              <a-textarea
                v-model="unitJson"
                :auto-size="{ minRows: 14, maxRows: 35 }"
                aria-label="学习单元编排 JSON"
              />
            </a-collapse-item>
          </a-collapse>
          <div class="controls">
            <a-button
              :disabled="!unitCode"
              :loading="busy"
              @click="review('units', unitCode, 'SUBMIT')"
              >提交审核</a-button
            ><a-button
              :disabled="!unitCode"
              :loading="busy"
              @click="review('units', unitCode, 'APPROVE')"
              >审核通过</a-button
            ><a-button
              type="primary"
              :disabled="unitStatus !== 'APPROVED'"
              :loading="busy"
              @click="publishUnit"
              >发布单元</a-button
            >
          </div>
        </section>
      </a-tab-pane>
      <a-tab-pane key="relations" title="知识节点与关系">
        <section v-if="catalog" class="panel">
          <h2>知识节点与关系</h2>
          <p>
            基础信息复用现有节点接口；关系写入 MySQL
            稳定登记表并进入图谱同步流程。 入向关系只读，并链接到起点节点维护。
          </p>
          <NodeRelationsEditor :nodes="catalog.nodes || []" />
        </section>
      </a-tab-pane>
      <a-tab-pane key="review" title="资源审核">
        <section class="panel">
          <h2>内容资源审核</h2>
          <p>
            按资源稳定代码读取、审核并发布。服务端会核对审核权限、状态与引用内容。
          </p>
          <div class="controls">
            <a-select v-model="reviewType"
              ><a-option value="chunks">教学块</a-option
              ><a-option value="units">学习单元</a-option
              ><a-option value="assessments">题目</a-option></a-select
            ><a-input
              v-model="reviewCode"
              aria-label="资源代码"
              :input-attrs="{ 'aria-label': '资源代码' }"
              placeholder="chunkCode / unitCode / questionCode"
            /><a-button
              :loading="busy"
              :disabled="!reviewCode"
              @click="readReview"
              >查看资源</a-button
            >
          </div>
          <a-textarea
            v-model="reviewReason"
            aria-label="审核说明"
            placeholder="审核说明或退回原因"
          />
          <div class="controls">
            <a-button
              v-for="action in reviewActions"
              :key="action.value"
              :disabled="!reviewCode"
              :loading="busy"
              @click="review(reviewType, reviewCode, action.value)"
              >{{ action.label }}</a-button
            >
          </div>
          <pre v-if="reviewDetail">{{ reviewDetail }}</pre>
        </section>
        <section class="panel">
          <h2>编程实验修订与映射</h2>
          <p>
            沿用原实验
            ID。导入与编辑先保存修订草稿，审核后明确应用；新资源完成运行核验后再启用。
          </p>
          <div class="controls">
            <a-input
              v-model="experimentId"
              aria-label="实验 ID"
              :input-attrs="{ 'aria-label': '实验 ID' }"
              placeholder="questionId（保留完整 ID）"
            /><a-button :loading="busy" @click="loadMappings">读取映射</a-button
            ><a-button
              :loading="busy"
              :disabled="!mappingJson"
              @click="saveMappings"
              >保存映射草稿</a-button
            >
          </div>
          <a-textarea
            v-model="mappingJson"
            :auto-size="{ minRows: 8, maxRows: 24 }"
            aria-label="实验映射 JSON"
          />
          <p v-if="experimentStatus">修订状态：{{ experimentStatus }}</p>
          <div class="controls">
            <a-button
              :disabled="!experimentId"
              :loading="busy"
              @click="reviewExperiment('SUBMIT')"
              >提交实验修订</a-button
            >
            <a-button
              :disabled="experimentStatus !== 'SUBMITTED'"
              :loading="busy"
              @click="reviewExperiment('APPROVE')"
              >审核通过</a-button
            >
            <a-button
              type="primary"
              :disabled="experimentStatus !== 'APPROVED'"
              :loading="busy"
              @click="reviewExperiment('APPLY')"
              >将修订应用到原实验</a-button
            >
          </div>
        </section>
        <section class="panel">
          <h2>确认旧节点身份</h2>
          <p>
            将新知识代码绑定到现有图谱节点，保留该节点及其学习历史。两个不同领域的同名算法需要分别确认。
          </p>
          <div class="controls">
            <a-input
              v-model="legacyCode"
              aria-label="旧节点代码"
              :input-attrs="{ 'aria-label': '旧节点代码' }"
              placeholder="已导入的 nodeCode"
            />
            <a-input
              v-model="legacyName"
              aria-label="旧节点名称"
              :input-attrs="{ 'aria-label': '旧节点名称' }"
              placeholder="旧图谱中的准确节点名称"
            />
            <a-button
              :disabled="!legacyCode || !legacyName"
              :loading="busy"
              @click="bindLegacy"
              >确认身份并同步图谱</a-button
            >
          </div>
        </section>
      </a-tab-pane>
    </a-tabs>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, onMounted } from "vue";
import { selectPackageAttachments } from "@/utils/memberPackageFiles";
import CourseRegistryEditor from "@/components/learning/CourseRegistryEditor.vue";
import LearningUnitForm from "@/views/tasks/components/LearningUnitForm.vue";
import NodeRelationsEditor from "@/views/tasks/components/NodeRelationsEditor.vue";
import {
  courseRequest,
  ImportReport,
  PackageUpload,
  listCourses,
  CourseSummary,
} from "@/api/course";
const experimentStatus = ref(""),
  legacyCode = ref(""),
  legacyName = ref("");
const bindLegacy = () =>
  perform(async () => {
    await courseRequest(
      `teaching/nodes/${encodeURIComponent(legacyCode.value)}/legacy-identity`,
      "put",
      { legacyNodeName: legacyName.value }
    );
    notice.value = "历史身份已确认，图谱同步状态可在词表管理中查看。";
  });
const reviewExperiment = (action: string) =>
  perform(async () => {
    const result = await courseRequest<Record<string, unknown>>(
      `teaching/experiments/${experimentId.value}/review`,
      "post",
      {
        action,
        confirmed: action === "APPLY",
        reason: reviewReason.value || undefined,
      }
    );
    mappingJson.value = JSON.stringify(result, null, 2);
    experimentStatus.value = String(result.status || "");
    notice.value =
      action === "APPLY"
        ? "修订已应用，相关单元仍需审核发布；新实验资源请完成运行核验后启用。"
        : "实验修订状态已更新";
  });
const files = ref<File[]>([]),
  selectedJson = ref(""),
  report = ref<ImportReport | null>(null),
  prepared = ref<PackageUpload | null>(null),
  busy = ref(false),
  committed = ref(false),
  fileError = ref(""),
  error = ref(""),
  notice = ref("");
const courseCode = ref(""),
  unitCode = ref(""),
  outlineJson = ref(""),
  unitJson = ref(""),
  unitStatus = ref("");
const reviewType = ref("chunks"),
  reviewCode = ref(""),
  reviewReason = ref(""),
  reviewDetail = ref("");
const experimentId = ref(""),
  mappingJson = ref("");
const availableCourses = ref<CourseSummary[]>([]);
const catalog = ref<{
  chunks: { chunkCode: string; title: string; reviewStatus: string }[];
  units?: {
    unitCode: string;
    title: string;
    status?: string;
    reviewStatus?: string;
  }[];
  nodes?: { nodeCode: string; name: string }[];
  [key: string]: unknown;
} | null>(null);
const loadCatalog = () =>
  perform(async () => {
    availableCourses.value = await listCourses();
    if (!courseCode.value)
      courseCode.value = availableCourses.value[0]?.courseCode || "";
    catalog.value = await courseRequest("teaching/content/catalog");
  });
function chooseReview(code: string) {
  reviewType.value = "chunks";
  reviewCode.value = code;
  readReview();
}
function chooseUnit(code: string) {
  unitCode.value = code;
  loadUnit();
}
onMounted(loadCatalog);
const reviewActions = [
  { value: "SUBMIT", label: "提交审核" },
  { value: "APPROVE", label: "审核通过" },
  { value: "REJECT", label: "退回修改" },
  { value: "PUBLISH", label: "发布资源" },
];
const relativePath = (file: File) => file.webkitRelativePath || file.name;
const jsonFiles = computed(() =>
  files.value.filter((file) => file.name.toLowerCase().endsWith(".json"))
);
function invalidatePreview() {
  report.value = null;
  prepared.value = null;
  committed.value = false;
  fileError.value = "";
}
function selectFiles(event: Event) {
  const input = event.target as HTMLInputElement;
  const selected = Array.from(input.files || []);
  const merged = new Map(files.value.map((file) => [relativePath(file), file]));
  selected.forEach((file) => merged.set(relativePath(file), file));
  files.value = Array.from(merged.values());
  if (
    !selectedJson.value ||
    !files.value.some((file) => relativePath(file) === selectedJson.value)
  )
    selectedJson.value = jsonFiles.value[0]
      ? relativePath(jsonFiles.value[0])
      : "";
  invalidatePreview();
  input.value = "";
}
function clearFiles() {
  files.value = [];
  selectedJson.value = "";
  invalidatePreview();
}
async function perform(operation: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await operation();
  } catch (e) {
    // 保留业务/附件错误文案；axios 的技术错误（Request failed…）换成可读提示
    const message = e instanceof Error ? e.message : "";
    error.value =
      message && !message.startsWith("Request failed")
        ? message
        : "操作未完成，请检查网络后重试";
  } finally {
    busy.value = false;
  }
}
function readBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(",")[1]);
    reader.onerror = () => reject(new Error(`无法读取附件 ${file.name}`));
    reader.readAsDataURL(file);
  });
}
async function buildUpload(): Promise<PackageUpload> {
  const main = files.value.find(
    (file) => relativePath(file) === selectedJson.value
  );
  if (!main) throw new Error("请选择资源包 JSON");
  const pkg = JSON.parse(await main.text());
  if (!pkg || typeof pkg !== "object" || Array.isArray(pkg))
    throw new Error("资源包必须为 JSON 对象");
  const attachments = selectPackageAttachments(
    relativePath(main),
    pkg,
    files.value
  );
  if (
    attachments.reduce((sum, entry) => sum + entry.file.size, 0) >
    20 * 1024 * 1024
  )
    throw new Error("本次附件超过 20 MB，请拆分资源包");
  const assets = await Promise.all(
    attachments.map(async ({ file, path }) => ({
      path,
      contentBase64: await readBase64(file),
      contentType: file.type || "application/octet-stream",
    }))
  );
  return { package: pkg, assets };
}
function importEndpoint() {
  const pkg = prepared.value?.package,
    type = String(pkg?.packageType || "").toUpperCase();
  return type === "QUESTIONS"
    ? "teaching/assessments/import"
    : type === "EXPERIMENT_MAINTENANCE" ||
      String(pkg?.schemaVersion || "").startsWith(
        "oslab-experiment-maintenance/"
      )
    ? "teaching/experiments/import"
    : "teaching/content/import";
}
const preview = () =>
  perform(async () => {
    invalidatePreview();
    prepared.value = await buildUpload();
    report.value = await courseRequest<ImportReport>(
      `${importEndpoint()}/preview`,
      "post",
      prepared.value
    );
  });
const commit = () =>
  perform(async () => {
    if (!prepared.value || !report.value?.valid || committed.value) return;
    report.value = await courseRequest<ImportReport>(
      `${importEndpoint()}/commit`,
      "post",
      prepared.value
    );
    committed.value = report.value.valid;
  });
const loadOutline = () =>
  perform(async () => {
    if (!courseCode.value.trim()) throw new Error("请输入课程代码");
    const data = await courseRequest<Record<string, unknown>>(
      `teaching/courses/${encodeURIComponent(courseCode.value)}/outline`
    );
    const flatten = (
      sections: Record<string, unknown>[]
    ): Record<string, unknown>[] =>
      sections.flatMap((section) => {
        const { children, ...fields } = section;
        delete fields.units;
        return [
          fields,
          ...flatten((children || []) as Record<string, unknown>[]),
        ];
      });
    outlineJson.value = JSON.stringify(
      {
        ...data,
        sections: flatten(data.sections as Record<string, unknown>[]),
      },
      null,
      2
    );
  });
const saveOutline = () =>
  perform(async () => {
    await courseRequest(
      `teaching/courses/${encodeURIComponent(courseCode.value)}/outline`,
      "put",
      JSON.parse(outlineJson.value)
    );
    notice.value = "目录已保存";
  });
async function refreshUnit() {
  const data = await courseRequest<Record<string, unknown>>(
    `teaching/units/${encodeURIComponent(unitCode.value)}`
  );
  unitJson.value = JSON.stringify(data, null, 2);
  unitStatus.value = String(data.status || "DRAFT");
}
const loadUnit = () =>
  perform(async () => {
    if (!unitCode.value.trim()) throw new Error("请输入单元代码");
    await refreshUnit();
  });
function createUnit() {
  const code = `unit.${Date.now().toString(36)}`;
  unitCode.value = code;
  unitStatus.value = "DRAFT";
  unitJson.value = JSON.stringify(
    {
      unitCode: code,
      title: "新建学习单元",
      courseCode: courseCode.value,
      sectionCode: "",
      sortOrder: 0,
      estimatedMinutes: 0,
      objectives: [],
      nodeMappings: [],
      activities: [],
      questionMappings: [],
    },
    null,
    2
  );
  notice.value = "已生成单元草稿，请完善小节、目标与活动后保存。";
}
function updateUnitJson(value: string) {
  unitJson.value = value;
  try {
    const parsed = JSON.parse(value);
    if (parsed.unitCode) unitCode.value = String(parsed.unitCode);
  } catch {
    // 表单编辑期间 JSON 短暂不可解析时保留原 unitCode
  }
}
const saveUnit = () =>
  perform(async () => {
    const data = JSON.parse(unitJson.value);
    await courseRequest(
      `teaching/units/${encodeURIComponent(unitCode.value)}`,
      "put",
      data
    );
    await refreshUnit();
    notice.value = "单元已保存为草稿";
  });
const review = (type: string, code: string, action: string) =>
  perform(async () => {
    await courseRequest(
      `teaching/${
        type === "assessments" ? "assessments" : `content/${type}`
      }/${encodeURIComponent(code)}/review`,
      "post",
      { action, reason: reviewReason.value || undefined }
    );
    notice.value = "审核状态已更新";
    if (type === "units" && code === unitCode.value && unitJson.value)
      await refreshUnit();
  });
const publishUnit = () =>
  perform(async () => {
    await courseRequest(
      `teaching/units/${encodeURIComponent(unitCode.value)}/publish`,
      "post",
      {}
    );
    await refreshUnit();
    notice.value = "单元已发布";
  });
const readReview = () =>
  perform(async () => {
    const data = await courseRequest(
      `teaching/${
        reviewType.value === "units"
          ? "units"
          : reviewType.value === "assessments"
          ? "assessments"
          : "content/chunks"
      }/${encodeURIComponent(reviewCode.value)}`
    );
    reviewDetail.value = JSON.stringify(data, null, 2);
  });
const loadMappings = () =>
  perform(async () => {
    if (!/^\d+$/.test(experimentId.value)) throw new Error("请输入完整实验 ID");
    const data = await courseRequest<Record<string, unknown>>(
      `teaching/experiments/${experimentId.value}/mappings`
    );
    mappingJson.value = JSON.stringify(data, null, 2);
    experimentStatus.value = String(data.status || "");
  });
const saveMappings = () =>
  perform(async () => {
    if (!/^\d+$/.test(experimentId.value)) throw new Error("请输入完整实验 ID");
    const data = await courseRequest<Record<string, unknown>>(
      `teaching/experiments/${experimentId.value}/mappings`,
      "put",
      JSON.parse(mappingJson.value)
    );
    mappingJson.value = JSON.stringify(data, null, 2);
    experimentStatus.value = String(data.status || "");
    notice.value = "实验映射修订已保存，审核应用后生成相关单元编排草稿";
  });
</script>
<style scoped>
.manage-course {
  max-width: 1120px;
  margin: auto;
  padding: 16px 0 40px;
}
h1 {
  font-size: 24px;
}
h2 {
  font-size: 18px;
}
p {
  line-height: 1.7;
  color: #4e5969;
}
.panel {
  background: #fff;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  padding: 24px;
  margin: 18px 0;
}
.controls {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin: 18px 0;
}
.controls .arco-input-wrapper {
  max-width: 390px;
}
.controls .arco-select {
  max-width: 210px;
}
.file-label {
  padding: 8px 14px;
  background: #f2f3f5;
  border-radius: 4px;
  cursor: pointer;
}
.file-label input {
  display: block;
  max-width: 300px;
  margin-top: 8px;
}
.error {
  color: #f53f3f;
}
table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
}
td,
th {
  padding: 12px;
  border-bottom: 1px solid #e5e6eb;
  overflow-wrap: anywhere;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: #f7f8fa;
  padding: 16px;
  font-size: 12px;
}
</style>
