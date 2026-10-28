<template>
  <div v-if="loading" class="editor-loading"><a-spin /></div>
  <div v-else class="task-editor">
    <a-card class="editor-head">
      <div class="head-top">
        <a-button size="small" @click="router.push('/teaching/tasks')"
          >返回任务列表</a-button
        >
        <a-space>
          <a-tag v-if="published" color="green">已发布 · 内容冻结</a-tag>
          <a-tag v-else color="gray">{{ task ? "草稿" : "新建任务" }}</a-tag>
          <a-button v-if="task && !published" @click="openPreview"
            >学生视角预览</a-button
          >
          <a-button
            v-if="!published"
            :loading="saving"
            type="primary"
            @click="saveDraft"
          >
            保存草稿
          </a-button>
          <a-button
            v-if="!published"
            type="success"
            :loading="publishing"
            @click="publish"
          >
            发布任务
          </a-button>
          <a-button
            v-if="published"
            @click="router.push(`/teaching/tasks/${taskCode}/review`)"
          >
            查看批阅
          </a-button>
        </a-space>
      </div>
      <h1 class="page-title">{{ task?.title || "新建学习任务" }}</h1>
    </a-card>

    <a-alert v-if="published" type="info" class="frozen-alert">
      发布后题干、答案、测试用例、分值与必做规则已冻结。可延长截止时间、调整逾期开关或追加说明；
      需要修改内容时请复制为新任务。
    </a-alert>

    <!-- 基本信息 -->
    <a-card title="基本信息">
      <a-form :model="form" layout="vertical">
        <div class="grid-2">
          <a-form-item label="任务标题" required>
            <a-input
              v-model="form.title"
              :disabled="published"
              maxlength="200"
            />
          </a-form-item>
          <a-form-item label="截止时间">
            <a-date-picker
              v-model="form.dueAt"
              show-time
              value-format="YYYY-MM-DDTHH:mm:ss"
              :disabled="false"
              style="width: 100%"
            />
          </a-form-item>
        </div>
        <div class="grid-2">
          <a-form-item label="关联课程代码（可选）">
            <a-input
              v-model="form.courseCode"
              :disabled="published"
              placeholder="例如 os2026"
            />
          </a-form-item>
          <a-form-item label="关联学习单元代码（可选）">
            <a-input
              v-model="form.unitCode"
              :disabled="published"
              placeholder="例如 unit.os.intro"
            />
          </a-form-item>
        </div>
        <a-form-item label="任务说明">
          <a-textarea
            v-model="form.description"
            :disabled="published"
            :auto-size="{ minRows: 2 }"
          />
        </a-form-item>
        <a-form-item label="允许逾期提交">
          <a-switch v-model="form.allowLate" :disabled="false" />
        </a-form-item>
        <a-form-item
          v-if="published"
          label="追加说明（保存后追加到任务说明区）"
        >
          <a-textarea
            v-model="supplement"
            :auto-size="{ minRows: 2 }"
            placeholder="例如：截止时间延后一天"
          />
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 发布对象 -->
    <a-card title="发布对象">
      <a-space direction="vertical" style="width: 100%" size="medium">
        <div class="grid-2">
          <div>
            <div class="field-label">按班级选择</div>
            <a-select
              v-model="form.audience.classNames"
              multiple
              allow-search
              placeholder="选择一个或多个现有班级"
              style="width: 100%"
            >
              <a-option
                v-for="option in classes"
                :key="option.className"
                :value="option.className"
              >
                {{ option.className }}（{{ option.studentCount }}）
              </a-option>
            </a-select>
          </div>
          <div>
            <div class="field-label">按学生选择</div>
            <a-select
              v-model="form.audience.userIds"
              multiple
              allow-search
              placeholder="按姓名或学号选择具体学生"
              style="width: 100%"
            >
              <a-option
                v-for="student in studentOptions"
                :key="String(student.userId)"
                :value="String(student.userId)"
              >
                {{ student.name }} · {{ student.studentNumber }} ·
                {{ student.className }}
              </a-option>
            </a-select>
          </div>
        </div>
        <div class="audience-actions">
          <a-button
            size="small"
            :loading="audienceLoading"
            @click="loadAudiencePreview"
          >
            预览接收人数
          </a-button>
          <span v-if="audiencePreview" class="muted">
            有效接收 {{ audiencePreview.validCount }} 人
            <span v-if="audiencePreview.excludedCount">
              · 排除 {{ audiencePreview.excludedCount }} 条
            </span>
          </span>
        </div>
        <a-alert v-if="audiencePreview?.excludedCount" type="warning">
          <div
            v-for="(row, index) in audiencePreview.excluded"
            :key="index"
            class="excluded-row"
          >
            {{ row.name || row.studentNumber || "未绑定记录" }}：{{
              row.reason
            }}
          </div>
        </a-alert>
        <div v-if="published" class="audience-actions">
          <a-button size="small" @click="appendRecipients">
            追加接收学生
          </a-button>
          <span class="muted">新入班学生不会自动接收旧任务。</span>
        </div>
      </a-space>
    </a-card>

    <!-- 内容块 -->
    <a-card>
      <template #title>
        <div class="content-title">
          <span>任务内容</span>
          <a-space v-if="!published">
            <a-button size="small" @click="addItem('TEXT')">添加文字</a-button>
            <a-button size="small" @click="addItem('MATERIAL')"
              >添加资料</a-button
            >
            <a-button size="small" @click="addItem('QUESTION')"
              >添加题目</a-button
            >
            <a-button size="small" @click="addItem('EXPERIMENT')"
              >添加实验</a-button
            >
          </a-space>
        </div>
      </template>

      <a-empty
        v-if="!form.items.length"
        description="添加文字、资料、题目或实验"
      />

      <div
        v-for="(item, index) in form.items"
        :key="item.itemCode"
        class="item-card"
      >
        <div class="item-head">
          <a-space wrap>
            <span class="item-index">{{ index + 1 }}</span>
            <strong>{{ typeText(item.itemType) }}</strong>
            <a-tag size="small">{{ item.content.title || "未命名" }}</a-tag>
            <a-switch
              v-model="item.required"
              :disabled="published"
              size="small"
            />
            <span class="muted">必做</span>
            <a-switch
              v-model="item.scored"
              :disabled="published"
              size="small"
              @change="toggleScored(item)"
            />
            <span class="muted">计分</span>
            <a-input-number
              v-if="item.scored"
              v-model="item.maxScore"
              :min="0"
              :max="1000"
              :precision="1"
              :disabled="published"
              placeholder="分值"
              style="width: 110px"
            />
          </a-space>
          <a-space v-if="!published">
            <a-button
              size="mini"
              :disabled="index === 0"
              @click="moveItem(index, -1)"
              >上移</a-button
            >
            <a-button
              size="mini"
              :disabled="index === form.items.length - 1"
              @click="moveItem(index, 1)"
            >
              下移
            </a-button>
            <a-button size="mini" status="danger" @click="removeItem(index)"
              >删除</a-button
            >
          </a-space>
        </div>

        <template v-if="!published">
          <!-- 文字 -->
          <template v-if="item.itemType === 'TEXT'">
            <a-form layout="vertical">
              <a-form-item label="标题">
                <a-input v-model="item.content.title" />
              </a-form-item>
              <a-form-item label="正文（Markdown）">
                <a-textarea
                  v-model="item.content.contentMd"
                  :auto-size="{ minRows: 4 }"
                />
              </a-form-item>
              <a-space wrap>
                <a-switch v-model="item.content.requireText" size="small" />
                <span>要求文字答复</span>
                <a-switch v-model="item.content.requireFile" size="small" />
                <span>要求上传文件</span>
                <a-switch v-model="item.content.requireConfirm" size="small" />
                <span>要求阅读确认</span>
              </a-space>
              <a-form-item
                v-if="item.content.requireText"
                label="答复提示"
                style="margin-top: 10px"
              >
                <a-input v-model="item.content.textPrompt" />
              </a-form-item>
            </a-form>
          </template>

          <!-- 资料 -->
          <template v-else-if="item.itemType === 'MATERIAL'">
            <a-form layout="vertical">
              <a-form-item label="标题">
                <a-input v-model="item.content.title" />
              </a-form-item>
              <a-form-item label="阅读说明">
                <a-textarea
                  v-model="item.content.description"
                  :auto-size="{ minRows: 2 }"
                />
              </a-form-item>
              <div class="field-label">资料文件</div>
              <div class="material-files">
                <div
                  v-for="file in item.content.files || []"
                  :key="file.fileId"
                  class="material-file"
                >
                  <icon-file />
                  <a :href="file.downloadPath" target="_blank" rel="noopener">{{
                    file.originalName
                  }}</a>
                  <a-button
                    size="mini"
                    type="text"
                    status="danger"
                    @click="removeMaterialFile(item, file.fileId)"
                  >
                    移除
                  </a-button>
                </div>
                <a-upload
                  :custom-request="(option: any) => uploadMaterial(option, item)"
                  :show-file-list="false"
                  :disabled="!taskCode"
                  accept=".doc,.docx,.pdf,.txt,.md,.png,.jpg,.jpeg,.webp"
                >
                  <a-button size="small" :disabled="!taskCode"
                    >上传资料</a-button
                  >
                </a-upload>
                <span v-if="!taskCode" class="muted"
                  >先保存草稿后即可上传资料文件。</span
                >
              </div>
              <a-space wrap style="margin-top: 10px">
                <a-switch v-model="item.content.requireConfirm" size="small" />
                <span>要求阅读确认</span>
                <a-switch v-model="item.content.requireFile" size="small" />
                <span>要求提交作业文件</span>
              </a-space>
            </a-form>
          </template>

          <!-- 客观题 -->
          <template v-else-if="item.itemType === 'QUESTION'">
            <a-form layout="vertical">
              <a-space wrap>
                <a-radio-group
                  v-model="item.sourceMode"
                  type="button"
                  @change="resetQuestionSource(item)"
                >
                  <a-radio value="AUTHORED">自行编写</a-radio>
                  <a-radio value="REFERENCE">从资源库选择</a-radio>
                </a-radio-group>
                <a-button
                  v-if="item.sourceMode === 'REFERENCE'"
                  size="small"
                  @click="openResourcePicker('QUESTION', item)"
                >
                  选择题目
                </a-button>
                <span v-if="item.content.sourceQuestionCode" class="muted">
                  来源 {{ item.content.sourceQuestionCode }} · 修订
                  {{ item.content.sourceRevision }}
                </span>
              </a-space>
              <div class="grid-2" style="margin-top: 12px">
                <a-form-item label="标题" required>
                  <a-input v-model="item.content.title" />
                </a-form-item>
                <a-form-item label="题型" required>
                  <a-select
                    v-model="item.content.type"
                    @change="onQuestionTypeChange(item)"
                  >
                    <a-option value="JUDGE">判断题</a-option>
                    <a-option value="SINGLE">单选题</a-option>
                    <a-option value="MULTIPLE">多选题</a-option>
                    <a-option value="FILL_BLANK">填空题</a-option>
                  </a-select>
                </a-form-item>
              </div>
              <a-form-item label="题干（Markdown）" required>
                <a-textarea
                  v-model="item.content.content"
                  :auto-size="{ minRows: 3 }"
                />
              </a-form-item>
              <a-form-item
                v-if="
                  item.content.type === 'SINGLE' ||
                  item.content.type === 'MULTIPLE'
                "
                label="选项"
              >
                <div
                  v-for="(option, optionIndex) in item.content.options"
                  :key="optionIndex"
                  class="option-row"
                >
                  <span class="option-key">{{ optionKey(optionIndex) }}</span>
                  <a-input v-model="option.value" placeholder="选项内容" />
                  <a-button
                    size="mini"
                    :disabled="item.content.options.length <= 2"
                    @click="removeOption(item, optionIndex)"
                  >
                    移除
                  </a-button>
                </div>
                <a-button size="small" @click="addOption(item)"
                  >添加选项</a-button
                >
              </a-form-item>
              <a-form-item label="标准答案" required>
                <a-radio-group
                  v-if="item.content.type === 'JUDGE'"
                  v-model="item.content.answer"
                >
                  <a-radio value="true">正确</a-radio>
                  <a-radio value="false">错误</a-radio>
                </a-radio-group>
                <a-select
                  v-else-if="item.content.type === 'SINGLE'"
                  v-model="item.content.answer"
                >
                  <a-option
                    v-for="(option, optionIndex) in item.content.options"
                    :key="optionIndex"
                    :value="optionKey(optionIndex)"
                  >
                    {{ optionKey(optionIndex) }}. {{ option.value }}
                  </a-option>
                </a-select>
                <a-checkbox-group
                  v-else-if="item.content.type === 'MULTIPLE'"
                  v-model="item.content.answer"
                >
                  <a-checkbox
                    v-for="(option, optionIndex) in item.content.options"
                    :key="optionIndex"
                    :value="optionKey(optionIndex)"
                  >
                    {{ optionKey(optionIndex) }}
                  </a-checkbox>
                </a-checkbox-group>
                <a-input
                  v-else
                  v-model="item.content.answer"
                  placeholder="填空答案；多个可接受答案用 | 分隔"
                />
              </a-form-item>
              <div class="grid-2">
                <a-form-item label="解析">
                  <a-textarea
                    v-model="item.content.analysis"
                    :auto-size="{ minRows: 2 }"
                  />
                </a-form-item>
                <a-form-item label="常见误区">
                  <a-input v-model="item.content.misconception" />
                </a-form-item>
              </div>
              <div class="grid-2">
                <a-form-item label="所属小节代码（可选）">
                  <a-input v-model="item.content.sectionCode" />
                </a-form-item>
                <a-form-item label="难度（1-5）">
                  <a-input-number
                    v-model="item.content.difficulty"
                    :min="1"
                    :max="5"
                  />
                </a-form-item>
              </div>
              <NodeSelect v-model="item.content.nodeCodes" :nodes="nodes" />
            </a-form>
          </template>

          <!-- 编程实验 -->
          <template v-else-if="item.itemType === 'EXPERIMENT'">
            <a-form layout="vertical">
              <a-space wrap>
                <a-radio-group
                  v-model="item.sourceMode"
                  type="button"
                  @change="resetExperimentSource(item)"
                >
                  <a-radio value="AUTHORED">自行编写</a-radio>
                  <a-radio value="REFERENCE">从资源库选择</a-radio>
                </a-radio-group>
                <a-button
                  v-if="item.sourceMode === 'REFERENCE'"
                  size="small"
                  @click="openResourcePicker('EXPERIMENT', item)"
                >
                  选择实验
                </a-button>
                <span v-if="item.content.sourceQuestionId" class="muted">
                  来源实验 ID {{ item.content.sourceQuestionId }}
                </span>
              </a-space>
              <div class="grid-2" style="margin-top: 12px">
                <a-form-item label="标题" required>
                  <a-input v-model="item.content.title" />
                </a-form-item>
                <a-form-item label="评阅方式">
                  <a-radio-group v-model="item.content.manualReview">
                    <a-radio :value="false">自动判题</a-radio>
                    <a-radio :value="true">人工评阅</a-radio>
                  </a-radio-group>
                </a-form-item>
              </div>
              <a-form-item label="实验说明（Markdown）" required>
                <a-textarea
                  v-model="item.content.content"
                  :auto-size="{ minRows: 3 }"
                />
              </a-form-item>
              <a-form-item label="初始代码">
                <a-textarea
                  v-model="item.content.originalCode"
                  :auto-size="{ minRows: 4 }"
                  class="mono-textarea"
                />
              </a-form-item>
              <a-form-item label="参考解（可选）">
                <a-textarea
                  v-model="item.content.answer"
                  :auto-size="{ minRows: 3 }"
                  class="mono-textarea"
                />
              </a-form-item>
              <template v-if="item.content.manualReview">
                <a-space wrap>
                  <a-switch v-model="item.content.requireText" size="small" />
                  <span>要求填写说明</span>
                  <a-switch v-model="item.content.requireFile" size="small" />
                  <span>要求上传报告文件</span>
                </a-space>
              </template>
              <template v-else>
                <a-form-item label="支持语言">
                  <a-checkbox-group v-model="item.content.languages">
                    <a-checkbox value="java">Java</a-checkbox>
                    <a-checkbox value="cpp">C++</a-checkbox>
                    <a-checkbox value="c">C</a-checkbox>
                    <a-checkbox value="go">Go</a-checkbox>
                  </a-checkbox-group>
                </a-form-item>
                <div class="grid-3">
                  <a-form-item label="时间限制 (ms)">
                    <a-input-number
                      v-model="item.content.judgeConfig.timeLimit"
                      :min="1"
                    />
                  </a-form-item>
                  <a-form-item label="内存限制 (MB)">
                    <a-input-number
                      v-model="item.content.judgeConfig.memoryLimit"
                      :min="1"
                    />
                  </a-form-item>
                  <a-form-item label="堆栈限制">
                    <a-input-number
                      v-model="item.content.judgeConfig.stackLimit"
                      :min="1"
                    />
                  </a-form-item>
                </div>
                <a-form-item label="测试用例（隐藏于学生）">
                  <div
                    v-for="(testCase, caseIndex) in item.content.judgeCase"
                    :key="caseIndex"
                    class="case-row"
                  >
                    <a-input v-model="testCase.input" placeholder="输入" />
                    <a-input v-model="testCase.output" placeholder="期望输出" />
                    <a-button size="mini" @click="removeCase(item, caseIndex)"
                      >删除</a-button
                    >
                  </div>
                  <a-button size="small" @click="addCase(item)"
                    >添加测试用例</a-button
                  >
                </a-form-item>
                <a-form-item label="公开样例（可选）">
                  <div
                    v-for="(sample, sampleIndex) in item.content.samples"
                    :key="sampleIndex"
                    class="case-row"
                  >
                    <a-input v-model="sample.input" placeholder="输入" />
                    <a-input v-model="sample.output" placeholder="输出" />
                    <a-button
                      size="mini"
                      @click="removeSample(item, sampleIndex)"
                      >删除</a-button
                    >
                  </div>
                  <a-button size="small" @click="addSample(item)"
                    >添加样例</a-button
                  >
                </a-form-item>
                <a-switch v-model="item.content.requirePass" size="small" />
                <span class="muted">必做项必须通过测试用例</span>
              </template>
              <NodeSelect
                v-model="item.content.nodeCodes"
                :nodes="nodes"
                style="margin-top: 12px"
              />
            </a-form>
          </template>
        </template>

        <template v-else>
          <ReadonlyItem :content="item.content" :type="item.itemType" />
        </template>
      </div>
    </a-card>

    <!-- 资源选择器 -->
    <a-modal
      v-model:visible="resourcePicker.visible"
      :title="
        resourcePicker.type === 'QUESTION' ? '选择资源库题目' : '选择资源库实验'
      "
      :footer="false"
      :width="760"
    >
      <a-space style="margin-bottom: 10px">
        <a-input
          v-model="resourcePicker.keyword"
          placeholder="搜索标题"
          style="width: 260px"
          allow-clear
        />
        <a-button size="small" @click="loadResources">搜索</a-button>
      </a-space>
      <a-table
        :data="resourcePicker.records"
        :loading="resourcePicker.loading"
        :pagination="false"
        size="small"
      >
        <template #columns>
          <a-table-column title="标题" data-index="title" />
          <a-table-column
            v-if="resourcePicker.type === 'QUESTION'"
            title="题型"
            data-index="type"
            :width="100"
          />
          <a-table-column
            v-if="resourcePicker.type === 'QUESTION'"
            title="修订"
            data-index="revision"
            :width="80"
          />
          <a-table-column
            v-if="resourcePicker.type === 'EXPERIMENT'"
            title="资源代码"
            data-index="resourceCode"
            :width="180"
          />
          <a-table-column title="操作" :width="90">
            <template #cell="{ record }">
              <a-button
                size="mini"
                type="primary"
                @click="chooseResource(record)"
                >选择</a-button
              >
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-modal>

    <!-- 学生视角预览 -->
    <a-drawer
      v-model:visible="previewVisible"
      title="学生视角预览"
      :width="760"
      :footer="false"
    >
      <a-spin :loading="previewLoading" style="width: 100%">
        <template v-if="previewData">
          <h2>{{ previewData.title }}</h2>
          <p v-if="previewData.description" class="muted">
            {{ previewData.description }}
          </p>
          <a-alert type="info" class="preview-alert">
            有效接收
            {{ previewData.audience?.validCount || 0 }}
            人；答案、解析与隐藏测试用例不会出现在学生预览中。
          </a-alert>
          <div
            v-for="(entry, index) in previewData.items || []"
            :key="entry.itemCode"
            class="preview-item"
          >
            <div class="preview-title">
              <span class="item-index">{{ index + 1 }}</span>
              <strong>{{
                entry.content?.title || typeText(entry.itemType)
              }}</strong>
              <a-tag size="small">{{ typeText(entry.itemType) }}</a-tag>
              <a-tag v-if="entry.required" size="small" color="orange"
                >必做</a-tag
              >
            </div>
            <MdViewer
              v-if="
                entry.content?.contentMd ||
                entry.content?.content ||
                entry.content?.description
              "
              :value="
                entry.content.contentMd ||
                entry.content.content ||
                entry.content.description
              "
            />
            <div v-if="entry.content?.options?.length" class="preview-options">
              <div v-for="option in entry.content.options" :key="option.key">
                <strong>{{ option.key }}.</strong> {{ option.value }}
              </div>
            </div>
            <div v-if="entry.content?.files?.length" class="preview-options">
              <div v-for="file in entry.content.files" :key="file.fileId">
                资料：{{ file.originalName }}
              </div>
            </div>
          </div>
        </template>
      </a-spin>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { Message, Modal } from "@arco-design/web-vue";
import MdViewer from "@/components/MdViewer.vue";
import NodeSelect from "./components/TaskNodeSelect.vue";
import ReadonlyItem from "./components/TaskReadonlyItem.vue";
import {
  addTaskRecipients,
  createTeachingTask,
  getTaskExperimentResource,
  getTaskQuestionResource,
  getTeacherClasses,
  getTeachingCatalog,
  getTeachingTask,
  listStudents,
  listTaskExperiments,
  listTaskQuestions,
  previewTeachingTask,
  previewTaskAudience,
  publishTeachingTask,
  saveTeachingTask,
  updateTaskSettings,
  uploadTaskFile,
  type TaskDetail,
  type TaskItemContent,
} from "@/api/tasks";

interface ItemDraft {
  itemCode: string;
  itemType: "TEXT" | "MATERIAL" | "QUESTION" | "EXPERIMENT";
  sourceMode: string;
  required: boolean;
  scored: boolean;
  maxScore: number | undefined;
  content: any;
}

const route = useRoute();
const router = useRouter();
const taskCode = computed(() => String(route.params.taskCode || ""));
const task = ref<TaskDetail | null>(null);
const loading = ref(false);
const saving = ref(false);
const publishing = ref(false);
const published = computed(
  () =>
    task.value?.taskStatus === "PUBLISHED" ||
    task.value?.taskStatus === "CLOSED"
);
const supplement = ref("");
const classes = ref<any[]>([]);
const students = ref<any[]>([]);
const nodes = ref<any[]>([]);
const audiencePreview = ref<any>(null);
const audienceLoading = ref(false);

const form = reactive({
  title: "",
  description: "",
  courseCode: "",
  unitCode: "",
  dueAt: undefined as string | undefined,
  allowLate: false,
  audience: { classNames: [] as string[], userIds: [] as string[] },
  items: [] as ItemDraft[],
});

const dirty = ref(false);
let hydrating = false;

const markClean = () => {
  hydrating = true;
  dirty.value = false;
  nextTick(() => {
    hydrating = false;
  });
};

watch(
  form,
  () => {
    if (hydrating || loading.value || published.value) return;
    dirty.value = true;
  },
  { deep: true }
);

onBeforeRouteLeave(() => {
  if (!dirty.value || published.value) return true;
  return window.confirm("有未保存的修改，确定离开吗？");
});

const handleBeforeUnload = (event: BeforeUnloadEvent) => {
  if (!dirty.value || published.value) return;
  event.preventDefault();
  event.returnValue = "";
};

const studentOptions = computed(() => {
  if (!form.audience.classNames.length) return students.value;
  return students.value.filter((student) =>
    form.audience.classNames.includes(student.className)
  );
});

const newCode = () =>
  `item.${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;

const defaultContent = (type: string) => {
  switch (type) {
    case "TEXT":
      return {
        title: "",
        contentMd: "",
        requireText: false,
        requireFile: false,
        requireConfirm: false,
        textPrompt: "",
      };
    case "MATERIAL":
      return {
        title: "",
        description: "",
        files: [],
        requireConfirm: false,
        requireFile: false,
      };
    case "QUESTION":
      return {
        title: "",
        content: "",
        type: "SINGLE",
        options: [
          { key: "A", value: "" },
          { key: "B", value: "" },
        ],
        answer: "A",
        analysis: "",
        misconception: "",
        difficulty: 2,
        sectionCode: "",
        nodeCodes: [],
      };
    case "EXPERIMENT":
      return {
        title: "",
        content: "",
        originalCode: "",
        answer: "",
        manualReview: false,
        requirePass: true,
        requireText: true,
        requireFile: false,
        judgeCase: [{ input: "", output: "" }],
        judgeConfig: { timeLimit: 1000, memoryLimit: 256, stackLimit: 128 },
        languages: ["java"],
        samples: [],
        tags: [],
        nodeCodes: [],
      };
    default:
      return {};
  }
};

const addItem = (type: string) => {
  form.items.push({
    itemCode: newCode(),
    itemType: type as any,
    sourceMode: type === "QUESTION" || type === "EXPERIMENT" ? "AUTHORED" : "",
    required: true,
    scored: false,
    maxScore: undefined,
    content: defaultContent(type),
  });
};

const removeItem = (index: number) => {
  form.items.splice(index, 1);
};
const toggleScored = (item: ItemDraft) => {
  if (item.scored && (item.maxScore === undefined || item.maxScore === null)) {
    item.maxScore = 10;
  }
  if (!item.scored) item.maxScore = undefined;
};
const moveItem = (index: number, delta: number) => {
  const target = index + delta;
  if (target < 0 || target >= form.items.length) return;
  const [item] = form.items.splice(index, 1);
  form.items.splice(target, 0, item);
};

const typeText = (type?: string) =>
  ((
    {
      TEXT: "文字说明",
      MATERIAL: "资料文件",
      QUESTION: "客观题",
      EXPERIMENT: "编程实验",
    } as Record<string, string>
  )[type || ""] ||
  type ||
  "");
const optionKey = (index: number) => String.fromCharCode(65 + index);
const addOption = (item: ItemDraft) => {
  if (item.content.options.length >= 8) return;
  item.content.options.push({
    key: optionKey(item.content.options.length),
    value: "",
  });
};
const removeOption = (item: ItemDraft, index: number) => {
  item.content.options.splice(index, 1);
  item.content.options.forEach((option: any, optionIndex: number) => {
    option.key = optionKey(optionIndex);
  });
};
const onQuestionTypeChange = (item: ItemDraft) => {
  item.content.answer =
    item.content.type === "MULTIPLE"
      ? []
      : item.content.type === "JUDGE"
      ? "true"
      : "";
  if (item.content.type === "SINGLE" || item.content.type === "MULTIPLE") {
    if (!item.content.options?.length) {
      item.content.options = [
        { key: "A", value: "" },
        { key: "B", value: "" },
      ];
    }
  }
};
const addCase = (item: ItemDraft) =>
  item.content.judgeCase.push({ input: "", output: "" });
const removeCase = (item: ItemDraft, index: number) =>
  item.content.judgeCase.splice(index, 1);
const addSample = (item: ItemDraft) =>
  item.content.samples.push({ input: "", output: "" });
const removeSample = (item: ItemDraft, index: number) =>
  item.content.samples.splice(index, 1);

const resetQuestionSource = (item: ItemDraft) => {
  if (item.sourceMode === "AUTHORED") {
    delete item.content.sourceQuestionId;
    delete item.content.sourceQuestionCode;
    delete item.content.sourceRevision;
  }
};
const resetExperimentSource = (item: ItemDraft) => {
  if (item.sourceMode === "AUTHORED") {
    delete item.content.sourceQuestionId;
    delete item.content.sourceUpdatedAt;
  }
};

// 资源选择器
const resourcePicker = reactive({
  visible: false,
  type: "QUESTION" as "QUESTION" | "EXPERIMENT",
  keyword: "",
  loading: false,
  records: [] as any[],
  item: null as ItemDraft | null,
});
const openResourcePicker = (
  type: "QUESTION" | "EXPERIMENT",
  item: ItemDraft
) => {
  resourcePicker.type = type;
  resourcePicker.item = item;
  resourcePicker.visible = true;
  resourcePicker.keyword = "";
  loadResources();
};
const loadResources = async () => {
  resourcePicker.loading = true;
  try {
    const params = {
      keyword: resourcePicker.keyword,
      current: 1,
      pageSize: 20,
    };
    const res =
      resourcePicker.type === "QUESTION"
        ? await listTaskQuestions(params)
        : await listTaskExperiments(params);
    if (res.data?.code === 0)
      resourcePicker.records = res.data.data?.records || [];
  } finally {
    resourcePicker.loading = false;
  }
};
const chooseResource = async (record: any) => {
  if (!resourcePicker.item) return;
  const item = resourcePicker.item;
  try {
    if (resourcePicker.type === "QUESTION") {
      const res = await getTaskQuestionResource(record.id);
      if (res.data?.code === 0) {
        const content = res.data.data as TaskItemContent;
        item.sourceMode = "REFERENCE";
        item.content = {
          ...content,
          sourceMode: "REFERENCE",
          answer: normalizeAnswer(content.type, content.answer),
          options: content.options || [],
          nodeCodes: content.nodeCodes || [],
        };
      }
    } else {
      const res = await getTaskExperimentResource(record.id);
      if (res.data?.code === 0) {
        const content = res.data.data as TaskItemContent;
        item.sourceMode = "REFERENCE";
        item.content = {
          ...content,
          sourceMode: "REFERENCE",
          judgeCase: content.judgeCase || [],
          judgeConfig: content.judgeConfig || {
            timeLimit: 1000,
            memoryLimit: 256,
            stackLimit: 128,
          },
          languages: content.languages || ["java"],
          samples: content.samples || [],
          nodeCodes: content.nodeCodes || [],
        };
      }
    }
    resourcePicker.visible = false;
    Message.success("已载入资源内容，可继续复制后修改");
  } catch {
    Message.error("资源加载失败");
  }
};

const normalizeAnswer = (type: string | undefined, answer: any) => {
  if (answer === null || answer === undefined) return "";
  if (type === "MULTIPLE") {
    if (Array.isArray(answer)) return answer;
    try {
      const parsed = JSON.parse(String(answer));
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
  if (typeof answer === "string") return answer;
  return String(answer);
};

const uploadMaterial = async (option: any, item: ItemDraft) => {
  if (!taskCode.value) {
    Message.warning("请先保存草稿，再上传资料文件");
    option.onError?.();
    return;
  }
  try {
    const raw = option.fileItem?.file || option.file;
    const res = await uploadTaskFile(
      raw,
      taskCode.value,
      item.itemCode,
      "TEACHER_MATERIAL"
    );
    if (res.data?.code === 0) {
      item.content.files = item.content.files || [];
      item.content.files.push(res.data.data);
      option.onSuccess?.(res.data.data);
      Message.success("资料已上传，请保存草稿");
    } else {
      Message.error(res.data?.message || "上传失败");
      option.onError?.();
    }
  } catch {
    Message.error("上传失败");
    option.onError?.();
  }
};
const removeMaterialFile = (item: ItemDraft, fileId: string) => {
  item.content.files = (item.content.files || []).filter(
    (file: any) => String(file.fileId) !== String(fileId)
  );
};

// 加载
const loadOptions = async () => {
  try {
    const [classRes, studentRes, catalogRes] = await Promise.all([
      getTeacherClasses(),
      listStudents({ current: 1, pageSize: 200 }),
      getTeachingCatalog(),
    ]);
    if (classRes.data?.code === 0) classes.value = classRes.data.data || [];
    if (studentRes.data?.code === 0)
      students.value = studentRes.data.data?.records || [];
    if (catalogRes.data?.code === 0)
      nodes.value = catalogRes.data.data?.nodes || [];
  } catch {
    // 基础选项失败不阻塞表单
  }
};

const loadTask = async () => {
  if (!taskCode.value) {
    form.dueAt = undefined;
    return;
  }
  loading.value = true;
  try {
    const res = await getTeachingTask(taskCode.value);
    if (res.data?.code === 0) {
      task.value = res.data.data;
      const data = res.data.data;
      form.title = data.title;
      form.description = data.description || "";
      form.courseCode = data.courseCode || "";
      form.unitCode = data.unitCode || "";
      form.dueAt = data.dueAt || undefined;
      form.allowLate = Boolean(data.allowLate);
      form.audience.classNames = [...(data.audience?.classNames || [])];
      form.audience.userIds = (data.audience?.userIds || []).map((id: any) =>
        String(id)
      );
      form.items = (data.items || []).map((item: any) => ({
        itemCode: item.itemCode,
        itemType: item.itemType,
        sourceMode: item.sourceMode || item.content?.sourceMode || "",
        required: Boolean(item.required),
        scored: item.maxScore !== null && item.maxScore !== undefined,
        maxScore: item.maxScore === null ? undefined : Number(item.maxScore),
        content: normalizeLoadedContent(item.itemType, item.content || {}),
      }));
      markClean();
      if (route.path.endsWith("/preview")) openPreview();
    } else {
      Message.error(res.data?.message || "任务加载失败");
    }
  } finally {
    loading.value = false;
  }
};

const normalizeLoadedContent = (type: string, content: any) => {
  const copy = JSON.parse(JSON.stringify(content || {}));
  if (type === "QUESTION") {
    copy.answer = normalizeAnswer(copy.type, copy.answer);
    copy.options = copy.options || [];
    copy.nodeCodes = copy.nodeCodes || [];
  }
  if (type === "EXPERIMENT") {
    copy.judgeCase = copy.judgeCase || [];
    copy.judgeConfig = copy.judgeConfig || {
      timeLimit: 1000,
      memoryLimit: 256,
      stackLimit: 128,
    };
    copy.languages = copy.languages || ["java"];
    copy.samples = copy.samples || [];
    copy.nodeCodes = copy.nodeCodes || [];
  }
  if (type === "MATERIAL") {
    copy.files = copy.files || [];
  }
  return copy;
};

const buildPayload = () => {
  if (!form.title.trim()) {
    Message.warning("请填写任务标题");
    return null;
  }
  const items = form.items.map((item) => {
    let content = JSON.parse(JSON.stringify(item.content));
    if (
      item.itemType === "QUESTION" &&
      content.type === "FILL_BLANK" &&
      typeof content.answer === "string"
    ) {
      content.answer = content.answer
        .split("|")
        .map((value: string) => value.trim())
        .filter(Boolean);
    }
    if (item.itemType === "EXPERIMENT") {
      content.judgeCase = content.judgeCase || [];
      content.judgeConfig = content.judgeConfig || {};
      content.languages = content.languages || [];
      content.samples = content.samples || [];
    }
    if (item.itemType === "TEXT" || item.itemType === "MATERIAL") {
      delete content.sourceMode;
    } else {
      content.sourceMode = item.sourceMode;
    }
    return {
      itemCode: item.itemCode,
      itemType: item.itemType,
      sourceMode: item.sourceMode || null,
      required: item.required,
      maxScore: item.scored ? Number(item.maxScore ?? 10) : null,
      content,
    };
  });
  return {
    title: form.title.trim(),
    description: form.description,
    courseCode: form.courseCode || null,
    unitCode: form.unitCode || null,
    dueAt: form.dueAt || null,
    allowLate: form.allowLate,
    audience: {
      classNames: form.audience.classNames,
      userIds: form.audience.userIds,
    },
    rowVersion: task.value?.rowVersion ?? 0,
    items,
  };
};

const saveDraft = async (silent = false): Promise<boolean> => {
  const payload = buildPayload();
  if (!payload) return false;
  saving.value = true;
  try {
    if (!taskCode.value) {
      const res = await createTeachingTask(payload);
      if (res.data?.code === 0) {
        Message.success("草稿已创建");
        await router.replace(`/teaching/tasks/${res.data.data.taskCode}/edit`);
        await loadTask();
        return true;
      }
      Message.error(res.data?.message || "创建失败");
      return false;
    }
    if (published.value) {
      const res = await updateTaskSettings(taskCode.value, {
        dueAt: form.dueAt || null,
        allowLate: form.allowLate,
        supplement: supplement.value.trim() || null,
        rowVersion: task.value?.rowVersion,
      });
      if (res.data?.code === 0) {
        supplement.value = "";
        task.value = res.data.data;
        markClean();
        if (!silent) Message.success("设置已保存");
        return true;
      }
      Message.error(res.data?.message || "保存失败");
      return false;
    }
    const res = await saveTeachingTask(taskCode.value, payload);
    if (res.data?.code === 0) {
      task.value = res.data.data;
      markClean();
      if (!silent) Message.success("草稿已保存");
      return true;
    }
    Message.error(res.data?.message || "保存失败");
    return false;
  } catch {
    Message.error("保存失败");
    return false;
  } finally {
    saving.value = false;
  }
};

const publish = () => {
  Modal.confirm({
    title: "发布任务",
    content: "发布后将生成不可变执行副本与接收名单，学生即可接收。确认发布？",
    okText: "发布",
    onOk: async () => {
      publishing.value = true;
      try {
        const saved = await saveDraft(true);
        if (!saved) return;
        const res = await publishTeachingTask(
          taskCode.value,
          task.value?.rowVersion
        );
        if (res.data?.code === 0) {
          Message.success("任务已发布");
          task.value = res.data.data;
          markClean();
          await router.push(`/teaching/tasks/${taskCode.value}/review`);
        } else {
          Message.error(res.data?.message || "发布失败");
        }
      } finally {
        publishing.value = false;
      }
    },
  });
};

const loadAudiencePreview = async () => {
  if (!taskCode.value) {
    Message.warning("请先保存草稿后再预览接收人数");
    return;
  }
  audienceLoading.value = true;
  try {
    const res = await previewTaskAudience(taskCode.value, {
      classNames: form.audience.classNames,
      userIds: form.audience.userIds,
    });
    if (res.data?.code === 0) {
      audiencePreview.value = res.data.data?.audience;
      if (!published.value) await saveDraft(true);
    } else {
      Message.error(res.data?.message || "名单解析失败");
    }
  } finally {
    audienceLoading.value = false;
  }
};

const appendRecipients = async () => {
  if (!form.audience.classNames.length && !form.audience.userIds.length) {
    Message.warning("请先选择班级或学生");
    return;
  }
  const res = await addTaskRecipients(taskCode.value, {
    classNames: form.audience.classNames,
    userIds: form.audience.userIds,
  });
  if (res.data?.code === 0) {
    Message.success(
      `已追加 ${res.data.data.added} 人，已存在 ${res.data.data.already} 人`
    );
  } else {
    Message.error(res.data?.message || "追加失败");
  }
};

const previewVisible = ref(false);
const previewLoading = ref(false);
const previewData = ref<any>(null);
const openPreview = async () => {
  if (!taskCode.value) {
    Message.warning("请先保存草稿后再预览");
    return;
  }
  previewVisible.value = true;
  previewLoading.value = true;
  try {
    const res = await previewTeachingTask(taskCode.value);
    if (res.data?.code === 0) previewData.value = res.data.data;
    else Message.error(res.data?.message || "预览失败");
  } finally {
    previewLoading.value = false;
  }
};

onMounted(async () => {
  window.addEventListener("beforeunload", handleBeforeUnload);
  await loadOptions();
  if (!taskCode.value) {
    const requestedClass = Array.isArray(route.query.className)
      ? route.query.className[0]
      : route.query.className;
    if (typeof requestedClass === "string" && requestedClass.trim()) {
      form.audience.classNames = [requestedClass.trim()];
      markClean();
    }
  }
  await loadTask();
});

onBeforeUnmount(() => {
  window.removeEventListener("beforeunload", handleBeforeUnload);
});
</script>

<style scoped>
.task-editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.editor-loading {
  display: flex;
  justify-content: center;
  padding: 120px 0;
}
.editor-head {
  border-radius: var(--oslab-radius-lg, 8px);
}
.head-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.page-title {
  margin: 12px 0 0;
  font-size: 22px;
  font-weight: 600;
}
.frozen-alert {
  border-radius: 8px;
}
.grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.field-label {
  font-weight: 600;
  margin-bottom: 6px;
}
.content-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.item-card {
  border: 1px solid var(--oslab-line, rgba(0, 0, 0, 0.11));
  border-radius: var(--oslab-radius-lg, 8px);
  padding: 14px;
  margin-bottom: 12px;
}
.item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.item-index {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--oslab-primary-soft, #eaf0fd);
  color: var(--oslab-primary, #2447d8);
  font-size: 12px;
  font-weight: 600;
}
.option-row,
.case-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.option-key {
  width: 20px;
  font-weight: 600;
}
.mono-textarea :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
.material-files {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}
.material-file {
  display: flex;
  gap: 8px;
  align-items: center;
}
.material-file a {
  color: var(--oslab-primary, #2447d8);
}
.audience-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.excluded-row {
  font-size: 12px;
}
.muted {
  color: var(--oslab-muted-strong, #757069);
  font-size: 12px;
}
.preview-item {
  padding: 12px 0;
  border-top: 1px solid var(--oslab-line, rgba(0, 0, 0, 0.08));
}
.preview-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.preview-options {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.preview-alert {
  margin: 8px 0;
}
@media (max-width: 900px) {
  .grid-2,
  .grid-3 {
    grid-template-columns: 1fr;
  }
}
</style>
