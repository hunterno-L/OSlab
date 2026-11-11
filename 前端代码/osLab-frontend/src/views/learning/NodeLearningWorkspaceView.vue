<template>
  <div class="node-workspace">
    <NodeCourseLinks
      :node-code="
        routeNodeCode ||
        (typeof route.query.nodeCode === 'string'
          ? route.query.nodeCode
          : '') ||
        workbench?.mastery?.nodeCode
      "
    />
    <!-- 顶部紧凑头部（一行区）：返回 / 节点名 / 状态 / 紧凑掌握度 / 唯一动作=刷新 -->
    <header class="node-header node-hero">
      <div class="node-heading">
        <div class="node-kicker">NODE / CONCEPT</div>
        <div class="node-title-row">
          <button type="button" class="back-link" @click="goBack">
            <IconArrowLeft />返回
          </button>
          <h1 class="node-title oslab-highlight-text">
            {{
              workbench?.mastery?.displayName ||
              workbench?.mastery?.name ||
              nodeName
            }}
          </h1>
          <span class="node-code">{{
            currentNodeCode || workbench?.mastery?.nodeCode
          }}</span>
          <span
            v-if="workbench"
            class="oslab-status-tag"
            :class="statusTagClass(workbench.mastery?.status)"
            >{{ statusText(workbench.mastery?.status) }}</span
          >
        </div>
        <p v-if="workbench" class="node-crumb">
          {{ workbench.mastery?.chapter || "未分类"
          }}<span v-if="workbench.mastery?.category">
            · {{ workbench.mastery.category }}</span
          >
        </p>
      </div>
      <div class="node-metrics">
        <template v-if="workbench">
          <template v-if="hasMasteryRecord">
            <span class="metric"
              >概念<strong>{{
                scoreText(
                  workbench.mastery?.conceptScore,
                  workbench.mastery?.conceptEvidenceCount
                )
              }}</strong></span
            >
            <span class="metric"
              >实践<strong>{{
                scoreText(
                  workbench.mastery?.practiceScore,
                  workbench.mastery?.practiceEvidenceCount
                )
              }}</strong></span
            >
            <span class="metric"
              >可信度<strong>{{
                percentText(workbench.mastery?.confidence)
              }}</strong></span
            >
            <span class="metric"
              >证据<strong>{{ evidenceCount }}</strong
              >条</span
            >
          </template>
          <span v-else class="metric metric-untested">尚未测评</span>
        </template>
        <a-tooltip content="刷新节点数据">
          <a-button
            size="small"
            aria-label="刷新节点数据"
            :loading="loading"
            @click="refreshAll"
            ><template #icon><IconRefresh /></template
          ></a-button>
        </a-tooltip>
      </div>
    </header>

    <!-- 副标题行：一句话定义 + 唯一推荐行动（仅一条、可点击） -->
    <div v-if="definitionLine || primaryRecommendation" class="node-subtitle">
      <p v-if="definitionLine" class="node-definition">
        <span class="node-definition-label">定义</span>
        {{ definitionLine }}
      </p>
      <button
        v-if="primaryRecommendation"
        type="button"
        class="recommend-action"
        @click="goPractice"
      >
        {{ primaryRecommendation.reason }}
        <span class="recommend-cta">去练习<IconArrowRight /></span>
      </button>
    </div>

    <!-- 加载失败：保留重试动作，不用空白掩盖失败 -->
    <div v-if="loadError" class="load-error" role="alert">
      <span class="load-error-text"
        ><strong>节点数据加载失败</strong>{{ loadError }}</span
      >
      <a-button size="small" type="primary" @click="refreshAll">重试</a-button>
    </div>

    <div class="workspace-grid">
      <div class="workspace-main">
        <nav class="text-tabs" role="tablist" aria-label="节点学习分区">
          <button
            v-for="tab in tabs"
            :id="`node-tab-${tab.key}`"
            :key="tab.key"
            :ref="(el) => setTabRef(tab.key, el)"
            type="button"
            role="tab"
            :aria-selected="activeTab === tab.key"
            :aria-controls="`node-panel-${tab.key}`"
            :tabindex="activeTab === tab.key ? 0 : -1"
            :class="['text-tab', { active: activeTab === tab.key }]"
            @click="setTab(tab.key)"
            @keydown="onTabKeydown($event, tab.key)"
          >
            {{ tab.title
            }}<span v-if="tab.count" class="tab-count">{{ tab.count }}</span>
          </button>
        </nav>

        <!-- 页签 1：知识内容 -->
        <section
          id="node-panel-learn"
          v-show="activeTab === 'learn'"
          class="tab-panel"
          role="tabpanel"
          aria-labelledby="node-tab-learn"
        >
          <div v-if="loading && !workbench" class="oslab-loading">
            <span class="oslab-spinner"></span><span>正在加载节点内容…</span>
          </div>
          <template v-else-if="workbench">
            <section class="content-section">
              <h2 class="oslab-section-title content-heading">概念摘要</h2>
              <template v-if="workbench.textbookChunks?.length"
                ><article
                  v-for="(chunk, chunkIndex) in workbench.textbookChunks"
                  :key="chunk.chunkCode"
                  :id="`chunk-${chunk.chunkCode}`"
                  class="content-section textbook-chunk"
                >
                  <div class="chunk-head">
                    <span class="chunk-index">{{
                      String(chunkIndex + 1).padStart(2, "0")
                    }}</span>
                    <h3>{{ chunk.title }}</h3>
                  </div>
                  <p class="figure-source">
                    {{ chunk.sourceSectionTitle }} · 版本
                    {{ chunk.revision || 1
                    }}<span v-if="chunk.sourceRef">
                      ·
                      {{
                        Object.values(chunk.sourceRef)
                          .filter((value) => typeof value !== "object")
                          .join(" · ")
                      }}</span
                    >
                  </p>
                  <MdViewer :value="chunk.contentMd" />
                  <AnnotationNote
                    v-if="
                      chunkIndex === 0 &&
                      (focusSummary || primaryRecommendation)
                    "
                    :index="3"
                    title="当前节点"
                  >
                    {{ focusSummary || primaryRecommendation?.reason }}
                  </AnnotationNote>
                </article></template
              >
              <MdViewer
                v-else-if="workbench.contentMd"
                :value="workbench.contentMd"
              />
              <p
                v-else-if="workbench.mastery?.description"
                class="concept-text"
              >
                {{ workbench.mastery.description }}
              </p>
              <div v-else class="oslab-empty">
                <span class="oslab-state-title">本节点暂无已发布内容</span>
                <span class="oslab-state-hint"
                  >共享学习素材由教师发布后显示；可先查看知识关联与阅读材料。</span
                >
              </div>
            </section>
            <section
              v-if="workbench.learningUnits?.length"
              class="content-section"
            >
              <h2 class="oslab-section-title">在课程中复习</h2>
              <p v-for="unit in workbench.learningUnits" :key="unit.unitCode">
                <router-link
                  :to="{
                    path: `/courses/${encodeURIComponent(
                      unit.courseCode
                    )}/units/${encodeURIComponent(unit.unitCode)}`,
                    query: { mode: 'REVIEW' },
                  }"
                  >{{ unit.title }}</router-link
                >
              </p>
            </section>

            <section
              v-if="workbench.misconceptions?.length"
              class="content-section"
            >
              <h2 class="oslab-section-title content-heading">常见误区</h2>
              <ul class="misconceptions">
                <li v-for="item in workbench.misconceptions" :key="item">
                  {{ item }}
                </li>
              </ul>
            </section>

            <section class="content-section">
              <h2 class="oslab-section-title content-heading">知识关联</h2>
              <div
                v-if="graphState === 'loading' && !hasRelations"
                class="relation-loading"
              >
                <span class="oslab-spinner"></span>
                <span class="side-hint">正在加载图谱关系…</span>
              </div>
              <template v-if="hasRelations">
                <div v-if="prerequisites.length" class="relation-row">
                  <span class="relation-label">前置知识</span>
                  <div class="relation-links">
                    <button
                      v-for="node in prerequisites"
                      :key="node.nodeCode || node.name"
                      type="button"
                      @click="switchNode(node.nodeCode || node.name)"
                    >
                      <i :class="['dot', dotClass(node.status)]"></i
                      >{{ node.displayName || displayNameOf(node.name) }}
                    </button>
                  </div>
                </div>
                <div v-if="successors.length" class="relation-row">
                  <span class="relation-label">后续知识</span>
                  <div class="relation-links">
                    <button
                      v-for="node in successors"
                      :key="node.nodeCode || node.name"
                      type="button"
                      @click="switchNode(node.nodeCode || node.name)"
                    >
                      <i :class="['dot', dotClass(node.status)]"></i
                      >{{ node.displayName || displayNameOf(node.name) }}
                    </button>
                  </div>
                </div>
                <div v-if="relatedNodes.length" class="relation-row">
                  <span class="relation-label">相关节点</span>
                  <div class="relation-links">
                    <button
                      v-for="item in relatedNodes"
                      :key="item.nodeCode || item.name"
                      type="button"
                      @click="switchNode(item.nodeCode || item.name)"
                    >
                      <i :class="['dot', dotClass(item.status)]"></i
                      >{{ displayNameOf(item.name)
                      }}<em
                        v-for="tag in item.tags"
                        :key="tag"
                        class="rel-tag"
                        >{{ tag }}</em
                      >
                    </button>
                  </div>
                </div>
                <p class="relation-footnote">
                  仅「前置知识」表示学习先后（依赖关系）；相关 / 包含 /
                  实现不产生学习顺序。
                </p>
              </template>
              <p
                v-else-if="graphState !== 'loading' && graphState !== 'error'"
                class="empty-copy"
              >
                暂无图谱关联节点
              </p>
              <div v-if="graphState === 'error'" class="relation-error">
                <span class="side-hint">图谱关系加载失败，相关节点未显示</span>
                <a-button size="mini" type="text" @click="loadGraph"
                  >重试</a-button
                >
              </div>
            </section>

            <section class="content-section content-section-last">
              <h2 class="oslab-section-title content-heading">阅读材料</h2>
              <!-- 静态图文详解已归入「知识详解」页签，这里只保留摘要入口，不重复渲染 -->
              <div v-if="nodeResources.length" class="material-entry">
                <div class="resource-row-main">
                  <strong
                    >本节点共 {{ nodeResources.length }} 篇图文详解</strong
                  >
                  <span class="side-hint">{{ nodeResourceTitles }}</span>
                </div>
                <a-button
                  size="small"
                  type="primary"
                  @click="setTab('resources')"
                  >到知识详解查看<IconArrowRight
                /></a-button>
              </div>
              <a
                v-for="resource in publishedResourceLinks"
                :key="resource.url"
                class="resource-row resource-link"
                :href="resource.url"
                target="_blank"
                rel="noopener noreferrer"
                ><div class="resource-row-main">
                  <strong>{{ resource.title }}</strong>
                </div>
                <IconLaunch
              /></a>
              <p
                v-if="!nodeResources.length && !publishedResourceLinks.length"
                class="empty-copy"
              >
                暂无阅读材料
              </p>
            </section>
          </template>
        </section>

        <!-- 页签 2：知识详解（复用静态 HTML 资源；iframe 懒加载，同一时刻仅一个） -->
        <section
          id="node-panel-resources"
          v-show="activeTab === 'resources'"
          class="tab-panel"
          role="tabpanel"
          aria-labelledby="node-tab-resources"
        >
          <template v-if="nodeResources.length">
            <div v-if="nodeResources.length > 1" class="resource-chips">
              <button
                v-for="resource in nodeResources"
                :key="resource.id"
                type="button"
                :aria-pressed="resource.id === activeResource?.id"
                :class="[
                  'resource-chip',
                  { active: resource.id === activeResource?.id },
                ]"
                @click="selectResource(resource.id)"
              >
                {{ resource.title }}
              </button>
            </div>
            <template v-if="activeResource">
              <div class="resource-detail-head">
                <strong>{{ activeResource.title }}</strong>
                <span v-if="activeResource.description" class="side-hint">{{
                  activeResource.description
                }}</span>
              </div>
              <!-- 懒加载：资源页签首次打开才挂载 iframe；之后切页签不销毁 -->
              <div v-if="resourceVisited" class="resource-frame">
                <iframe
                  v-show="resourceLoadState === 'loaded'"
                  :key="`${activeResource.id}-${resourceReloadKey}`"
                  :src="activeResource.url"
                  :title="activeResource.title"
                  class="resource-iframe"
                  @load="onResourceLoad"
                  @error="onResourceError"
                ></iframe>
                <div
                  v-if="resourceLoadState === 'loading'"
                  class="resource-frame-state"
                >
                  <span class="oslab-spinner"></span>
                  <span>正在加载图文详解…</span>
                </div>
                <div
                  v-if="resourceLoadState === 'error'"
                  class="resource-frame-state resource-failed"
                  role="alert"
                >
                  <span class="resource-failed-text"
                    ><strong>图文详解加载失败</strong
                    >可能是网络中断或页面缺失，可重试或在新窗口打开。</span
                  >
                  <div class="resource-failed-actions">
                    <a-button
                      size="small"
                      type="primary"
                      @click="reloadResource"
                      >重试</a-button
                    >
                    <a-button
                      size="small"
                      :href="activeResource.url"
                      target="_blank"
                      rel="noopener noreferrer"
                      >新窗口打开<IconLaunch
                    /></a-button>
                  </div>
                </div>
              </div>
            </template>
          </template>
          <div v-else class="oslab-empty">
            <span class="oslab-state-title">本节点暂无图文详解</span>
            <span class="oslab-state-hint"
              >图文详解在学习资源库中按节点关联发布。</span
            >
            <a-button size="small" type="primary" @click="openResourceOverview"
              >查看学习资源</a-button
            >
          </div>
        </section>

        <!-- 页签 3：题目练习（全宽答题卡，不再用右侧窄抽屉） -->
        <section
          id="node-panel-assess"
          v-show="activeTab === 'assess'"
          class="tab-panel"
          role="tabpanel"
          aria-labelledby="node-tab-assess"
        >
          <div v-if="loading && !workbench" class="oslab-loading">
            <span class="oslab-spinner"></span><span>正在加载题目…</span>
          </div>
          <template v-else-if="workbench">
            <div class="tab-toolbar">
              <span class="side-hint"
                >共 {{ questionStats.total }} 题 · 已作答
                {{ questionStats.attempted }} · 未作答
                {{ questionStats.unattempted }}</span
              >
              <a-button
                type="primary"
                size="small"
                :disabled="!nodeQuestions.length"
                @click="startAssessment"
                >开始连续测评<IconArrowRight
              /></a-button>
            </div>
            <template v-if="nodeQuestions.length">
              <ul class="question-list">
                <li
                  v-for="(question, index) in nodeQuestions"
                  :key="question.id"
                >
                  <button
                    type="button"
                    class="question-row"
                    :class="{ expanded: isActiveQuestion(question) }"
                    :aria-expanded="isActiveQuestion(question)"
                    @click="toggleQuestion(question)"
                  >
                    <span class="q-index oslab-mono">{{ index + 1 }}</span>
                    <span class="q-title">{{ question.title }}</span>
                    <span class="q-meta">
                      <span class="q-hint">{{
                        questionTypeName(question.type)
                      }}</span>
                      <span class="q-hint">{{
                        difficultyLabel(question.difficulty)
                      }}</span>
                      <span
                        class="oslab-status-tag"
                        :class="questionState(question).cls"
                        >{{ questionState(question).text }}</span
                      >
                    </span>
                    <IconDown
                      class="q-caret"
                      :class="{ open: isActiveQuestion(question) }"
                    />
                  </button>
                </li>
              </ul>
              <div v-if="activeQuestion" class="answer-panel">
                <QuestionAnswerCard
                  :key="activeQuestion.id"
                  :question="activeQuestion"
                  @answered="onAnswered"
                />
              </div>
            </template>
            <div v-else class="oslab-empty">
              <span class="oslab-state-title">本节点暂无已发布题目</span>
              <span class="oslab-state-hint"
                >教师发布题目后，可在此逐题练习或进入连续测评。</span
              >
            </div>
          </template>
        </section>

        <!-- 页签 4：实践（编程实验 / 算法演示） -->
        <section
          id="node-panel-lab"
          v-show="activeTab === 'lab'"
          class="tab-panel"
          role="tabpanel"
          aria-labelledby="node-tab-lab"
        >
          <div v-if="loading && !workbench" class="oslab-loading">
            <span class="oslab-spinner"></span><span>正在加载实践入口…</span>
          </div>
          <template v-else-if="workbench">
            <div
              v-for="experiment in workbench.experiments || []"
              :key="experiment.id"
              class="practice-row"
            >
              <span class="practice-kind">编程实验</span>
              <div class="resource-row-main">
                <strong>{{ experiment.title }}</strong>
                <span class="side-hint"
                  >最近结果：{{ experiment.lastResult || "未提交" }}</span
                >
              </div>
              <a-button size="small" @click="openExperiment(experiment.id)"
                >进入实验<IconArrowRight
              /></a-button>
            </div>
            <div v-if="workbench.simulationEntry" class="practice-row">
              <span class="practice-kind">算法演示</span>
              <div class="resource-row-main">
                <strong>{{ simulationTitle }}</strong>
                <span class="side-hint">运行演示不写判题/掌握事件</span>
              </div>
              <a-select
                v-if="simulationOptions.length > 1"
                v-model="selectedSimulationKey"
                size="small"
                style="width: 190px"
              >
                <a-option
                  v-for="option in simulationOptions"
                  :key="option.key"
                  :value="option.key"
                  >{{ option.label }}</a-option
                >
              </a-select>
              <a-button size="small" @click="openSimulator"
                >进入演示<IconArrowRight
              /></a-button>
            </div>
            <div
              v-if="
                !loading &&
                !workbench.experiments?.length &&
                !workbench.simulationEntry
              "
              class="oslab-empty"
            >
              <span class="oslab-state-title">本节点暂无关联实践</span>
              <span v-if="prerequisites.length" class="oslab-state-hint"
                >可先学习前置知识，再回来完成实践：</span
              >
              <span v-else class="oslab-state-hint"
                >教师关联编程实验或算法演示后将显示在这里。</span
              >
              <div
                v-if="prerequisites.length"
                class="relation-links prereq-links"
              >
                <button
                  v-for="node in prerequisites"
                  :key="node.nodeCode || node.name"
                  type="button"
                  @click="switchNode(node.nodeCode || node.name)"
                >
                  <i :class="['dot', dotClass(node.status)]"></i
                  >{{ node.displayName || displayNameOf(node.name) }}
                </button>
              </div>
            </div>
          </template>
        </section>
      </div>

      <!-- 右侧学习侧栏：真实证据、下一步与统一导师入口；不再维护页面内独立聊天 -->
      <aside class="tutor-panel" aria-label="图谱导师">
        <div class="tutor-kicker">NODE MENTOR</div>
        <div class="tutor-body">
          <div class="tutor-focus tutor-focus-note">
            <span class="tutor-focus-index">③</span>
            <div>
              <span class="context-label">当前学习焦点</span>
              <p>{{ focusSummary }}</p>
            </div>
          </div>
          <!-- 证据摘要卡：掌握状态 / 证据数 / 最近失败，全部真实数据 -->
          <div class="evidence-card tutor-evidence">
            <div class="evidence-line">
              <span class="evidence-label">掌握状态</span>
              <span class="evidence-value evidence-status">
                <span
                  class="oslab-status-tag"
                  :class="statusTagClass(workbench?.mastery?.status)"
                  >{{ statusText(workbench?.mastery?.status) }}</span
                >
                <template v-if="hasMasteryRecord"
                  >掌握度
                  {{ percentText(workbench?.mastery?.pKnown) }}</template
                >
              </span>
            </div>
            <div class="evidence-line">
              <span class="evidence-label">学习证据</span>
              <span class="evidence-value">{{
                hasMasteryRecord ? `${evidenceCount} 条` : "尚未测评"
              }}</span>
            </div>
            <template v-if="lastFailure">
              <div class="evidence-line">
                <span class="evidence-label">最近失败</span>
                <span class="evidence-value">
                  {{ failureStateText(lastFailure.failureState) || "失败记录"
                  }}<template v-if="lastFailure.errorType">
                    · {{ lastFailure.errorType }}</template
                  >
                </span>
              </div>
              <p
                v-if="lastFailure.recommendedAction || lastFailure.result"
                class="failure-hint"
              >
                <template v-if="lastFailure.result"
                  >结果：{{ resultText(lastFailure.result) }}</template
                ><template v-if="lastFailure.recommendedAction"
                  >。建议：{{ lastFailure.recommendedAction }}</template
                >
              </p>
            </template>
            <div v-else class="evidence-line">
              <span class="evidence-label">最近失败</span>
              <span class="evidence-value evidence-muted">暂无记录</span>
            </div>
          </div>

          <section class="tutor-ai-plan" aria-label="AI 薄弱原因与下一步">
            <div class="tutor-ai-head">
              <strong>薄弱原因与下一步</strong>
              <button type="button" @click="askUnifiedTutor">
                向统一导师提问
              </button>
            </div>
            <ul v-if="weaknessReasons.length" class="tutor-weak-list">
              <li v-for="reason in weaknessReasons" :key="reason">
                {{ reason }}
              </li>
            </ul>
            <p v-else class="side-hint">
              暂无足够证据；先完成一次作答或预测，避免直接判为薄弱。
            </p>
            <p
              v-if="workbench?.events?.length"
              class="tutor-evidence-ids oslab-mono"
            >
              证据：{{
                workbench.events
                  .slice(0, 3)
                  .map((event) => `#${event.id}`)
                  .join("、")
              }}
            </p>
            <AiNextActions
              :actions="nextActions"
              :course-code="aiPlanCourseCode"
              :loading="nextActionsLoading"
              :error="nextActionsError"
              :due-count="dueReviewCount"
              title="下一步"
              empty-text="完成一次作答或预测后会生成可执行任务。"
              compact
              :limit="2"
              @refresh="refreshNodePlan"
            />
          </section>

          <section class="tutor-launch" aria-label="向统一导师提问">
            <p class="side-hint">
              导师会带入当前节点、薄弱依据与证据；页面只提交上下文，不在此维护第二套会话。
            </p>
            <a-button type="primary" long @click="openNodeTutor('ASK')">
              向导师提问
            </a-button>
            <a-button long @click="openNodeTutor('EXPLAIN')">
              解释当前节点
            </a-button>
          </section>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import { useStore } from "vuex";
import { Message } from "@arco-design/web-vue";
import {
  IconArrowLeft,
  IconArrowRight,
  IconDown,
  IconLaunch,
  IconRefresh,
} from "@arco-design/web-vue/es/icon";
import MdViewer from "@/components/MdViewer.vue";
import NodeCourseLinks from "@/components/learning/NodeCourseLinks.vue";
import AnnotationNote from "@/components/learning/AnnotationNote.vue";
import { normalizedGraphRelation } from "@/utils/graphSemantics";
import { scoreText } from "@/utils/masteryDisplay";
import QuestionAnswerCard from "@/components/learning/QuestionAnswerCard.vue";
import { getResourcesForNode } from "@/data/learningResources";
import {
  ALGORITHM_BY_KEY,
  algorithmKeyFromLegacy,
  algorithmKeyFromNodeCode,
} from "@/simulation";
import {
  loadNodeWorkbench,
  peekNodeWorkbench,
} from "@/composables/useNodeWorkbench";
import {
  personalGraph,
  type GraphLinkDTO,
  type LearningQuestionVO,
  type MasteryNodeVO,
  type NodeWorkbenchVO,
} from "@/api/learning";
import {
  registerAiPageContext,
  unregisterAiPageContext,
} from "@/composables/useAiPageContext";
import AiNextActions from "@/components/ai/AiNextActions.vue";
import { useAiConversation } from "@/composables/useAiConversation";
import {
  currentCourseCode,
  resolveCurrentCourse,
} from "@/composables/useCurrentCourse";
import { openTutor } from "@/composables/useAiLauncher";
import type { TutorIntent } from "@/types/tutor";

const {
  nextActions,
  nextActionsLoading,
  nextActionsError,
  dueReviewCount,
  refreshNextActions,
} = useAiConversation();
const aiPlanCourseCode = computed(() => currentCourseCode.value);
const courseReady = ref(false);

// AI 助手页面上下文（log3 阶段 4）：挂载注册、卸载注销；升级为课程上下文
const aiPageContextProvider = () => ({
  pageName: "节点学习工作台",
  courseCode: currentCourseCode.value || undefined,
  unitCode: workbench.value?.learningUnits?.find(
    (unit) => unit.courseCode === currentCourseCode.value
  )?.unitCode,
  unitName: workbench.value?.learningUnits?.find(
    (unit) => unit.courseCode === currentCourseCode.value
  )?.title,
  nodeName: nodeName.value || undefined,
  nodeCode: currentNodeCode.value || undefined,
  nodeCodes: currentNodeCode.value ? [currentNodeCode.value] : [],
  eventIds: (workbench.value?.events || [])
    .slice(0, 3)
    .map((event) => String(event.id)),
  lastJudgeResult: workbench.value?.lastFailure?.result
    ? resultText(workbench.value.lastFailure.result)
    : undefined,
});
onMounted(() => registerAiPageContext(aiPageContextProvider));
onBeforeUnmount(() => unregisterAiPageContext(aiPageContextProvider));

const route = useRoute();
const router = useRouter();
const store = useStore();
// Vue Router already decodes params; decoding twice corrupts literal percent signs.
const routeNodeCode = computed(() =>
  typeof route.params.nodeCode === "string" ? route.params.nodeCode : ""
);
const routeNodeName = computed(() =>
  typeof route.params.nodeName === "string" ? route.params.nodeName : ""
);
// 稳定代码优先，旧名称路径仅作兼容；API 层据此区分 nodeCode / nodeName
const nodeName = computed(() => routeNodeCode.value || routeNodeName.value);
const userId = computed(() =>
  String(store.state.user?.loginUser?.id || "anonymous")
);
const nodeKey = computed(() => JSON.stringify([userId.value, nodeName.value]));
const loading = ref(false);
const loadError = ref("");
const workbench = ref<NodeWorkbenchVO | null>(null);
const activeTab = ref("learn");
const activeQuestionId = ref("");
let loadSequence = 0;
let disposed = false;

// ---- 图谱关系数据：个人图谱（结构 + 掌握状态）一次取回，前端按 source/target 过滤 ----
const graphNodes = ref<MasteryNodeVO[]>([]);
const graphLinks = ref<GraphLinkDTO[]>([]);
const graphState = ref<"idle" | "loading" | "ready" | "error">("idle");
const matchedGraphNode = computed(
  () =>
    graphNodes.value.find(
      (node) =>
        (node.nodeCode && node.nodeCode === nodeName.value) ||
        node.name === nodeName.value
    ) || null
);
/** 真实节点名（旧名称入口即参数本身；稳定代码入口经工作台/图谱解析） */
const currentNodeName = computed(
  () =>
    workbench.value?.mastery?.name ||
    matchedGraphNode.value?.name ||
    routeNodeName.value ||
    nodeName.value
);
const currentNodeCode = computed(
  () =>
    workbench.value?.mastery?.nodeCode ||
    matchedGraphNode.value?.nodeCode ||
    routeNodeCode.value ||
    ""
);

const load = async (force = false) => {
  const requestNode = nodeName.value;
  const requestUser = userId.value;
  const sequence = ++loadSequence;
  if (!requestNode) return;
  loading.value = true;
  loadError.value = "";
  if (force || graphState.value === "idle" || graphState.value === "error")
    void loadGraph();
  try {
    const data = await loadNodeWorkbench(requestUser, requestNode, force);
    if (
      disposed ||
      sequence !== loadSequence ||
      requestNode !== nodeName.value ||
      requestUser !== userId.value
    )
      return;
    workbench.value = data;
    if (courseReady.value && currentCourseCode.value) {
      void refreshNextActions({
        courseCode: currentCourseCode.value,
        limit: 5,
      });
    }
    await nextTick();
    scrollToTextbookChunk();
  } catch (error) {
    if (disposed || sequence !== loadSequence) return;
    loadError.value =
      error instanceof Error ? error.message : "节点加载失败，请稍后重试";
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
};

const loadGraph = async () => {
  const course = currentCourseCode.value;
  if (!course || !courseReady.value) return;
  const request = ++nodeGraphSequence;
  graphState.value = "loading";
  try {
    const response = await personalGraph(course);
    if (
      disposed ||
      request !== nodeGraphSequence ||
      course !== currentCourseCode.value
    )
      return;
    if (response.data.code === 0 && response.data.data) {
      graphNodes.value = response.data.data.nodes || [];
      graphLinks.value = response.data.data.links || [];
      graphState.value = "ready";
    } else {
      graphState.value = "error";
    }
  } catch {
    if (
      !disposed &&
      request === nodeGraphSequence &&
      course === currentCourseCode.value
    )
      graphState.value = "error";
  }
};

let nodeGraphSequence = 0;
let courseResolveSequence = 0;
watch(
  () => route.query.courseCode,
  async (requested) => {
    const sequence = ++courseResolveSequence;
    nodeGraphSequence += 1;
    courseReady.value = false;
    graphNodes.value = [];
    graphLinks.value = [];
    graphState.value = "idle";
    try {
      await resolveCurrentCourse(
        typeof requested === "string" ? requested : undefined,
        () => !disposed && sequence === courseResolveSequence
      );
      if (disposed || sequence !== courseResolveSequence) return;
      courseReady.value = true;
      void loadGraph();
      if (currentCourseCode.value && workbench.value)
        void refreshNextActions({
          courseCode: currentCourseCode.value,
          limit: 5,
        });
    } catch {
      if (!disposed && sequence === courseResolveSequence)
        graphState.value = "error";
    }
  },
  { immediate: true }
);

watch(currentCourseCode, (course) => {
  nodeGraphSequence += 1;
  graphNodes.value = [];
  graphLinks.value = [];
  graphState.value = "idle";
  if (course && courseReady.value && !disposed) {
    void loadGraph();
    if (workbench.value)
      void refreshNextActions({ courseCode: course, limit: 5 });
  }
});

// ---- 知识详解（tab=resources）：复用静态 HTML 资源，iframe 懒加载 ----
// 本块必须先于下方 watch(route.query.tab) 注册，否则直达 ?tab=resources 时
// activeTab 的变更不会被监听到，iframe 不会懒加载。
const nodeResources = computed(() =>
  getResourcesForNode(currentNodeName.value)
);
const activeResourceId = ref("");
const activeResource = computed(() => {
  const list = nodeResources.value;
  if (!list.length) return null;
  return (
    list.find((resource) => resource.id === activeResourceId.value) || list[0]
  );
});
const nodeResourceTitles = computed(() =>
  nodeResources.value.map((resource) => resource.title).join("、")
);
/** 资源页签首次打开才挂载 iframe；此后切页签用 v-show 保留加载状态 */
const resourceVisited = ref(false);
const resourceLoadState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const resourceReloadKey = ref(0);
let resourceLoadTimer = 0;
const clearResourceTimer = () => {
  if (resourceLoadTimer) {
    window.clearTimeout(resourceLoadTimer);
    resourceLoadTimer = 0;
  }
};
// iframe 的 error 事件跨浏览器不可靠，以 load 为准；超时仍未 load 视为失败
const markResourceLoading = () => {
  clearResourceTimer();
  resourceLoadState.value = "loading";
  resourceLoadTimer = window.setTimeout(() => {
    resourceLoadTimer = 0;
    if (resourceLoadState.value === "loading")
      resourceLoadState.value = "error";
  }, 15000);
};
watch(activeTab, (tab) => {
  if (tab !== "resources") return;
  resourceVisited.value = true;
  if (activeResource.value && resourceLoadState.value === "idle")
    markResourceLoading();
});
watch(
  () => activeResource.value?.url,
  (url) => {
    clearResourceTimer();
    if (!url) {
      resourceLoadState.value = "idle";
      return;
    }
    // 页签未打开时不启动超时计时，避免首开误报加载失败
    if (activeTab.value === "resources" && resourceVisited.value) {
      markResourceLoading();
    } else {
      resourceLoadState.value = "idle";
    }
  }
);
const selectResource = (id: string) => {
  if (activeResource.value?.id === id) return;
  activeResourceId.value = id;
};
const onResourceLoad = () => {
  clearResourceTimer();
  resourceLoadState.value = "loaded";
};
const onResourceError = () => {
  clearResourceTimer();
  resourceLoadState.value = "error";
};
const reloadResource = () => {
  resourceReloadKey.value += 1;
  markResourceLoading();
};
const openResourceOverview = () => router.push("/resources");

watch(
  nodeKey,
  () => {
    activeQuestionId.value = "";
    activeResourceId.value = "";
    workbench.value = peekNodeWorkbench(userId.value, nodeName.value);
    void load();
  },
  { immediate: true }
);
watch(
  () => route.query.tab,
  (tab) => {
    const value = Array.isArray(tab) ? String(tab[0] || "") : String(tab || "");
    // ?tab=resources 直达「知识详解」页签；未知或缺省 tab 回退默认页签
    activeTab.value = ["learn", "resources", "assess", "lab"].includes(value)
      ? value
      : "learn";
  },
  { immediate: true }
);

const setTab = (key: string) => {
  if (activeTab.value === key) return;
  activeTab.value = key;
  // replace：URL 与页签保持同步可分享，但不制造额外历史记录
  if (route.query.tab !== key)
    router.replace({ query: { ...route.query, tab: key } });
};

const tabRefs: Record<string, HTMLButtonElement | null> = {};
const setTabRef = (key: string, el: unknown) => {
  tabRefs[key] = (el as HTMLButtonElement | null) || null;
};

/** WAI-ARIA tabs：左右方向键、Home/End 切换并移动焦点。 */
const onTabKeydown = (event: KeyboardEvent, key: string) => {
  const keys = tabs.value.map((tab) => tab.key);
  const index = keys.indexOf(key);
  if (index < 0) return;
  let nextIndex = index;
  if (event.key === "ArrowRight") nextIndex = (index + 1) % keys.length;
  else if (event.key === "ArrowLeft")
    nextIndex = (index - 1 + keys.length) % keys.length;
  else if (event.key === "Home") nextIndex = 0;
  else if (event.key === "End") nextIndex = keys.length - 1;
  else return;
  event.preventDefault();
  const nextKey = keys[nextIndex];
  setTab(nextKey);
  nextTick(() => tabRefs[nextKey]?.focus());
};
function scrollToTextbookChunk() {
  if (!route.hash.startsWith("#chunk-")) return;
  const target = document.getElementById(
    decodeURIComponent(route.hash.slice(1))
  );
  target?.scrollIntoView({ block: "start" });
}
watch(
  () => route.hash,
  async () => {
    activeTab.value = "learn";
    await nextTick();
    scrollToTextbookChunk();
  }
);

const refreshAll = () => {
  void load(true);
};

const onAnswered = () => {
  void load(true);
};

const queryText = (value: unknown) =>
  Array.isArray(value) ? String(value[0] || "") : String(value || "");
const backTarget = computed(() => {
  const value = queryText(route.query.returnTo);
  if (value.startsWith("/") && !value.startsWith("//")) return value;
  return "/graph";
});
const goBack = () => {
  const resolved = router.resolve(backTarget.value);
  const query = { ...resolved.query };
  if (resolved.path.startsWith("/courses/")) {
    const activity = queryText(route.query.returnActivity);
    const mode = queryText(route.query.returnMode);
    if (activity) query.activity = activity;
    if (mode === "REVIEW" || mode === "MAINLINE") query.mode = mode;
  }
  router.push({ path: resolved.path, query, hash: resolved.hash });
};

const switchNode = (keyOrName: string) => {
  if (!keyOrName) return;
  const target = graphNodes.value.find(
    (node) => node.nodeCode === keyOrName || node.name === keyOrName
  );
  const targetCode =
    target?.nodeCode || (keyOrName.startsWith("os.") ? keyOrName : "");
  const targetName = target?.name || keyOrName;
  if (
    (targetCode && targetCode === currentNodeCode.value) ||
    (!targetCode && targetName === currentNodeName.value)
  )
    return;
  const query: Record<string, string> = {};
  if (currentCourseCode.value) query.courseCode = currentCourseCode.value;
  const returnTo = queryText(route.query.returnTo);
  const returnActivity = queryText(route.query.returnActivity);
  const returnMode = queryText(route.query.returnMode);
  if (returnTo) query.returnTo = returnTo;
  if (returnActivity) query.returnActivity = returnActivity;
  if (returnMode) query.returnMode = returnMode;
  if (activeTab.value !== "learn") query.tab = activeTab.value;
  router.push({
    path: targetCode
      ? `/knowledge/concept/${encodeURIComponent(targetCode)}`
      : `/knowledge/node/${encodeURIComponent(targetName)}`,
    query: Object.keys(query).length ? query : undefined,
  });
};

const startAssessment = () =>
  router.push({
    path: "/assessments",
    query: {
      ...(currentCourseCode.value
        ? { courseCode: currentCourseCode.value }
        : {}),
      nodeName: currentNodeName.value,
      ...(currentNodeCode.value ? { nodeCode: currentNodeCode.value } : {}),
      returnTo: route.fullPath,
    },
  });

const goPractice = () => setTab("assess");

const openExperiment = (id: number | string) =>
  router.push({
    path: `/experiments/code/${id}`,
    query: {
      ...(currentCourseCode.value
        ? { courseCode: currentCourseCode.value }
        : {}),
      nodeName: currentNodeName.value,
      ...(currentNodeCode.value ? { nodeCode: currentNodeCode.value } : {}),
      returnTo: route.fullPath,
    },
  });

const openSimulator = () => {
  const key =
    selectedSimulationKey.value || simulationOptions.value[0]?.key || "";
  if (!key) return;
  router.push({
    path: "/experiments/simulation",
    query: {
      algorithmKey: key,
      nodeName: currentNodeName.value,
      ...(currentNodeCode.value ? { nodeCode: currentNodeCode.value } : {}),
      returnTo: route.fullPath,
    },
  });
};

// ============ 展示数据（全部来自真实 VO，不造演示数据） ============

const questionStats = computed(
  () =>
    workbench.value?.questionStats || { total: 0, attempted: 0, unattempted: 0 }
);

/** 多节点共享题按 questionId 去重 */
const nodeQuestions = computed<LearningQuestionVO[]>(() => {
  const seen = new Set<string>();
  const list: LearningQuestionVO[] = [];
  for (const question of workbench.value?.questions || []) {
    const key = String(question.id);
    if (seen.has(key)) continue;
    seen.add(key);
    list.push(question);
  }
  return list;
});

const isActiveQuestion = (question: LearningQuestionVO) =>
  activeQuestionId.value === String(question.id);
const toggleQuestion = (question: LearningQuestionVO) => {
  activeQuestionId.value = isActiveQuestion(question)
    ? ""
    : String(question.id);
};
const activeQuestion = computed(() =>
  nodeQuestions.value.find(
    (question) => String(question.id) === activeQuestionId.value
  )
);

const evidenceCount = computed(() => {
  const mastery = workbench.value?.mastery;
  if (!mastery) return 0;
  return (
    (mastery.conceptEvidenceCount || 0) + (mastery.practiceEvidenceCount || 0)
  );
});
const hasMasteryRecord = computed(() => {
  const mastery = workbench.value?.mastery;
  if (!mastery) return false;
  return (
    evidenceCount.value > 0 ||
    mastery.pKnown != null ||
    mastery.confidence != null
  );
});

/** 一句话定义：节点描述优先，否则取已发布素材正文摘要 */
const clampSentence = (value: string) => {
  const first = (value.split(/[。！？!?]/)[0] || value).trim();
  return first.length > 80 ? `${first.slice(0, 80)}…` : first;
};
const contentSummary = computed(() => {
  const md = workbench.value?.contentMd;
  if (!md) return "";
  const text = md
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/!\[[^\]]*\]\([^)]*\)/g, " ")
    .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/[#>*`~_]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  return text ? clampSentence(text) : "";
});
const definitionLine = computed(() => {
  const description = workbench.value?.mastery?.description?.trim();
  return description ? clampSentence(description) : contentSummary.value;
});

/** 唯一推荐行动：仅一条，来自真实推荐数据 */
const primaryRecommendation = computed(
  () => workbench.value?.recommendations?.[0] || null
);

const prerequisites = computed(() => workbench.value?.prerequisites || []);
const successors = computed(() => workbench.value?.successors || []);

// ---- 相关节点：related / contain / implements（历史 implemente 兼容），不产生学习顺序 ----
const RELATED_TAG_TEXT: Record<string, string> = {
  contrasts: "对比",
  based: "依据",
  related: "相关",
  contain: "包含",
  implements: "实现",
};
const displayNameOf = (name: string) =>
  graphNodes.value.find((node) => node.name === name)?.displayName || name;
interface RelatedNodeItem {
  name: string;
  nodeCode: string;
  status: string;
  tags: string[];
}
const relatedNodes = computed<RelatedNodeItem[]>(() => {
  if (graphState.value !== "ready") return [];
  const selfName = currentNodeName.value;
  if (!selfName) return [];
  const idToName = new Map(
    graphNodes.value.map(
      (node) => [String(node.id), node.name] as [string, string]
    )
  );
  const infoByName = new Map(
    graphNodes.value.map((node) => [node.name, node] as const)
  );
  const related = new Map<string, RelatedNodeItem>();
  for (const link of graphLinks.value) {
    const raw = String(link.relationType ?? link.value ?? "")
      .trim()
      .toLowerCase();
    const type = normalizedGraphRelation(raw);
    const tag = RELATED_TAG_TEXT[type];
    if (!tag) continue;
    const sourceName = idToName.get(String(link.source));
    const targetName = idToName.get(String(link.target));
    if (!sourceName || !targetName || sourceName === targetName) continue;
    const other =
      sourceName === selfName
        ? targetName
        : targetName === selfName
        ? sourceName
        : "";
    if (!other) continue;
    const entry = related.get(other) || {
      name: other,
      nodeCode: infoByName.get(other)?.nodeCode || "",
      status: infoByName.get(other)?.status || "GRAY",
      tags: [],
    };
    if (!entry.tags.includes(tag)) entry.tags = [...entry.tags, tag];
    related.set(other, entry);
  }
  return Array.from(related.values()).sort((a, b) =>
    a.name.localeCompare(b.name, "zh")
  );
});

const hasRelations = computed(
  () =>
    prerequisites.value.length > 0 ||
    successors.value.length > 0 ||
    relatedNodes.value.length > 0
);

const selectedSimulationKey = ref("");
const simulationOptions = computed<Array<{ key: string; label: string }>>(
  () => {
    const raw = workbench.value?.simulationEntry?.algorithms || [];
    const seen = new Set<string>();
    const options: Array<{ key: string; label: string }> = [];
    for (const item of raw) {
      const key = ALGORITHM_BY_KEY.has(item)
        ? item
        : algorithmKeyFromLegacy(item);
      if (!key || seen.has(key)) continue;
      seen.add(key);
      const definition = ALGORITHM_BY_KEY.get(key);
      options.push({
        key,
        label: definition
          ? `${definition.shortTitle} · ${definition.title}`
          : item,
      });
    }
    if (!options.length) {
      const key =
        algorithmKeyFromNodeCode(currentNodeCode.value) ||
        algorithmKeyFromNodeCode(workbench.value?.mastery?.nodeCode || "");
      const definition = ALGORITHM_BY_KEY.get(key);
      if (definition) {
        options.push({
          key: definition.key,
          label: `${definition.shortTitle} · ${definition.title}`,
        });
      }
    }
    return options;
  }
);
watch(
  simulationOptions,
  (options) => {
    if (!options.some((option) => option.key === selectedSimulationKey.value)) {
      selectedSimulationKey.value = options[0]?.key || "";
    }
  },
  { immediate: true }
);
const simulationTitle = computed(
  () => ALGORITHM_BY_KEY.get(selectedSimulationKey.value)?.title || "算法演示"
);

const focusSummary = computed(() => {
  const failure = workbench.value?.lastFailure;
  if (failure?.failureState === "CURRENT")
    return failure.recommendedAction || "定位最近一次失误，重新验证关键步骤";
  const recommendation = workbench.value?.recommendations?.[0];
  if (recommendation) return recommendation.reason;
  if (workbench.value?.mastery?.status === "GREEN")
    return "通过不同题型验证理解，继续关联知识";
  return "梳理核心概念，完成一次关联练习";
});
const lastFailure = computed(() => workbench.value?.lastFailure || null);

/** 薄弱原因只展示服务端已有归因/推荐/误区，不由前端臆测 */
const weaknessReasons = computed(() => {
  const reasons: string[] = [];
  const failure = workbench.value?.lastFailure;
  if (failure?.attributionBasis?.length) {
    reasons.push(...failure.attributionBasis);
  }
  if (failure?.recommendedAction) reasons.push(failure.recommendedAction);
  const recommendation = workbench.value?.recommendations?.[0];
  if (recommendation?.reason) reasons.push(recommendation.reason);
  for (const misconception of workbench.value?.misconceptions || []) {
    reasons.push(`可能误区：${misconception}`);
  }
  return Array.from(new Set(reasons.filter(Boolean))).slice(0, 5);
});

async function refreshNodePlan() {
  if (!aiPlanCourseCode.value) return;
  await refreshNextActions({ courseCode: aiPlanCourseCode.value, limit: 5 });
}

function buildNodeTutorDraft(intent: TutorIntent) {
  const evidence = (workbench.value?.events || [])
    .slice(0, 3)
    .map((event) => `#${event.id}`)
    .join("、");
  if (intent === "EXPLAIN") {
    return `请解释节点「${currentNodeName.value}」${
      currentNodeCode.value ? `（${currentNodeCode.value}）` : ""
    } 的核心规则，并联系前置知识。`;
  }
  if (!weaknessReasons.value.length) {
    return `我在节点「${currentNodeName.value}」${
      currentNodeCode.value ? `（${currentNodeCode.value}）` : ""
    }。目前还没有足够证据判断薄弱点；请先解释这个节点的核心概念，再给出一个可以马上执行的下一步。${
      evidence ? `相关证据：${evidence}。` : ""
    }`;
  }
  return `我当前在节点「${currentNodeName.value}」${
    currentNodeCode.value ? `（${currentNodeCode.value}）` : ""
  }。已有的薄弱依据：${weaknessReasons.value.join("；")}。${
    evidence ? `相关证据：${evidence}。` : ""
  }请说明可能原因，再给出可以马上执行的下一步。`;
}

/** 统一入口：只带入草稿与上下文，打开面板不调用模型。 */
function openNodeTutor(intent: TutorIntent) {
  const courseCode = aiPlanCourseCode.value;
  if (!courseCode) {
    Message.warning("当前节点还没有关联课程，暂时无法打开导师。");
    return;
  }
  openTutor({
    scene: "NODE",
    intent,
    courseCode,
    scope: "PAGE",
    unitCode: workbench.value?.learningUnits?.[0]?.unitCode,
    nodeCodes: currentNodeCode.value ? [currentNodeCode.value] : [],
    eventIds: (workbench.value?.events || [])
      .slice(0, 3)
      .map((event) => String(event.id)),
    draft: buildNodeTutorDraft(intent),
    delivery: "DRAFT",
    sceneLabel: currentNodeName.value,
  });
}

function askUnifiedTutor() {
  openNodeTutor("ASK");
}

const safeResourceUrl = (url: string) => /^(https?:\/\/|\/(?!\/))/.test(url);
/** 教师发布的扩展阅读外链；静态图文详解统一由「知识详解」页签渲染，不在此重复 */
const publishedResourceLinks = computed(() => {
  const links: Array<{ title: string; url: string }> = [];
  try {
    const published: unknown = JSON.parse(
      workbench.value?.resourceLinks || "[]"
    );
    if (Array.isArray(published))
      published.forEach((item) => {
        if (
          item &&
          typeof item.url === "string" &&
          safeResourceUrl(item.url) &&
          !nodeResources.value.some((resource) => resource.url === item.url)
        )
          links.push({
            title: typeof item.title === "string" ? item.title : item.url,
            url: item.url,
          });
      });
  } catch {
    /* 无效发布链接不影响本地资源目录。 */
  }
  return links;
});

const practiceCount = computed(() => {
  const data = workbench.value;
  if (!data) return 0;
  return (data.experiments?.length || 0) + (data.simulationEntry ? 1 : 0);
});
const tabs = computed(() => [
  { key: "learn", title: "知识内容", count: 0 },
  { key: "resources", title: "知识详解", count: nodeResources.value.length },
  {
    key: "assess",
    title: "题目练习",
    count: workbench.value ? questionStats.value.total : 0,
  },
  { key: "lab", title: "实践", count: practiceCount.value },
]);

const percentText = (value: number | null | undefined) =>
  value == null ? "-" : `${Math.round(value * 100)}%`;

const LABELS: Record<string, string> = {
  GRAY: "未学习",
  RED: "薄弱",
  YELLOW: "待巩固",
  GREEN: "已掌握",
  PASS: "通过",
  FAIL: "未通过",
  SYSTEM_ERROR: "系统错误",
  BASELINE: "状态基线",
};
const statusText = (status?: string) =>
  LABELS[status || "GRAY"] || status || "未学习";
const resultText = (result?: string | null) =>
  result ? LABELS[result] || result : "";

const STATUS_TAG_CLASS: Record<string, string> = {
  GREEN: "oslab-status-tag--mastered",
  YELLOW: "oslab-status-tag--review",
  RED: "oslab-status-tag--weak",
  GRAY: "oslab-status-tag--unseen",
};
const statusTagClass = (status?: string) =>
  STATUS_TAG_CLASS[status || "GRAY"] || "oslab-status-tag--unseen";

const DOT_CLASS: Record<string, string> = {
  GREEN: "dot-mastered",
  YELLOW: "dot-review",
  RED: "dot-weak",
  GRAY: "dot-unseen",
};
const dotClass = (status?: string) =>
  DOT_CLASS[status || "GRAY"] || "dot-unseen";

const FAILURE_STATE_TEXT: Record<string, string> = {
  CURRENT: "当前失误",
  HISTORICAL: "历史失误",
  CORRECTED: "已纠正",
  NONE: "无失败",
};
const failureStateText = (state?: string | null) =>
  (state && FAILURE_STATE_TEXT[state]) || "";

const questionTypeName = (type: string) =>
  ((
    {
      JUDGE: "判断题",
      SINGLE: "单选题",
      MULTIPLE: "多选题",
      FILL_BLANK: "填空题",
      COMPREHENSIVE: "综合应用题",
    } as Record<string, string>
  )[type] || type);

const DIFFICULTY_LABELS: Record<number, string> = {
  1: "入门",
  2: "基础",
  3: "进阶",
  4: "挑战",
  5: "综合",
};
const difficultyLabel = (difficulty?: number) =>
  (difficulty && DIFFICULTY_LABELS[difficulty]) || "未标注";

const questionState = (question: LearningQuestionVO) => {
  if (question.hasAttempted)
    return question.latestResult === "CORRECT"
      ? { text: "已答对", cls: "oslab-status-tag--mastered" }
      : { text: "待复习", cls: "oslab-status-tag--review" };
  return { text: "未作答", cls: "oslab-status-tag--unseen" };
};

onBeforeUnmount(() => {
  disposed = true;
  loadSequence += 1;
  clearResourceTimer();
});
</script>

<style scoped>
.node-workspace {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ---- 顶部紧凑头部 ---- */
.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}
.node-heading {
  min-width: 0;
}
.node-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  padding: 4px 8px;
  margin-left: -8px;
  border-radius: var(--oslab-radius-md);
  font: inherit;
  font-size: var(--oslab-font-body);
  color: var(--oslab-muted-strong);
  cursor: pointer;
  transition: color var(--oslab-duration-fast) var(--oslab-ease),
    background var(--oslab-duration-fast) var(--oslab-ease);
}
.back-link:hover {
  color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
}
.node-title {
  margin: 0;
  font-size: var(--oslab-text-page);
  font-weight: 600;
  line-height: 1.3;
  color: var(--oslab-ink);
  overflow-wrap: anywhere;
}
.node-crumb {
  margin: 4px 0 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.node-metrics {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.metric {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  white-space: nowrap;
}
.metric strong {
  color: var(--oslab-ink);
  font-size: var(--oslab-font-body);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.metric-untested {
  color: var(--oslab-muted-strong);
}

/* ---- 副标题行 ---- */
.node-subtitle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.node-definition {
  margin: 0;
  flex: 1 1 320px;
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-font-body);
  line-height: var(--oslab-leading-body);
}
.recommend-action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel);
  padding: 6px 10px;
  font: inherit;
  font-size: var(--oslab-text-small);
  color: var(--oslab-ink-soft);
  cursor: pointer;
  text-align: left;
  transition: border-color var(--oslab-duration-fast) var(--oslab-ease);
}
.recommend-action:hover {
  border-color: var(--oslab-primary);
}
.recommend-cta {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--oslab-primary);
  font-weight: 600;
  white-space: nowrap;
}

/* ---- 加载失败横条 ---- */
.load-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 14px;
  border: 1px solid var(--oslab-weak);
  background: var(--oslab-weak-soft);
  border-radius: var(--oslab-radius-lg);
}
.load-error-text {
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.load-error-text strong {
  color: var(--oslab-weak-text);
  margin-right: 8px;
}

/* ---- 主区网格 ---- */
.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 336px;
  gap: 24px;
  align-items: start;
}
.workspace-main {
  min-width: 0;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  padding: 12px 20px 4px;
}

/* ---- 四页签 ---- */
.text-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--oslab-line);
}
.text-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  padding: 8px 12px 10px;
  font: inherit;
  font-size: var(--oslab-font-body);
  color: var(--oslab-ink-soft);
  cursor: pointer;
  transition: color var(--oslab-duration-fast) var(--oslab-ease);
}
.text-tab:hover {
  color: var(--oslab-primary);
}
.text-tab.active {
  color: var(--oslab-primary);
  font-weight: 600;
}
.text-tab.active::after {
  content: "";
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: -1px;
  height: 2px;
  border-radius: 1px;
  background: var(--oslab-primary);
}
.tab-count {
  font-size: var(--oslab-text-small);
  line-height: 18px;
  padding: 0 6px;
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel-soft);
  color: var(--oslab-muted-strong);
}
.text-tab.active .tab-count {
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
}
.tab-panel {
  padding: 16px 0 8px;
}

/* ---- 知识内容 ---- */
.content-section {
  padding: 4px 0 20px;
  border-bottom: 1px solid var(--oslab-line-soft);
}
.content-section-last {
  border-bottom: 0;
}
.content-heading {
  margin: 0 0 12px;
}
.concept-text {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.8;
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-font-body);
}
.misconceptions {
  margin: 0;
  padding-left: 18px;
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-font-body);
  line-height: 1.8;
}
.relation-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.relation-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 6px 0;
  font-size: var(--oslab-text-small);
}
.relation-label {
  flex: 0 0 64px;
  color: var(--oslab-muted-strong);
}
.relation-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}
.relation-links button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  padding: 2px 0;
  font: inherit;
  font-size: 13px;
  color: var(--oslab-primary);
  cursor: pointer;
  text-align: left;
  overflow-wrap: anywhere;
  transition: color var(--oslab-duration-fast) var(--oslab-ease);
}
.relation-links button:hover {
  color: var(--oslab-primary-hover);
}
.rel-tag {
  font-style: normal;
  font-size: 11px;
  line-height: 16px;
  padding: 0 6px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  color: var(--oslab-muted-strong);
}
.relation-footnote {
  margin: 10px 0 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.relation-error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
/* 状态圆点：颜色 + 状态标签文字双信号 */
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--oslab-unseen);
}
.dot-mastered {
  background: var(--oslab-mastered);
}
.dot-review {
  background: var(--oslab-review);
}
.dot-weak {
  background: var(--oslab-weak);
}
.dot-unseen {
  background: var(--oslab-unseen);
}
.resource-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--oslab-line-soft);
  min-width: 0;
}
.resource-row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.resource-row-main strong {
  font-size: var(--oslab-font-body);
  font-weight: 500;
  color: var(--oslab-ink);
  overflow-wrap: anywhere;
}
.resource-row .arco-btn {
  flex-shrink: 0;
}
.resource-link {
  text-decoration: none;
  color: var(--oslab-primary);
}
.resource-link:hover strong {
  color: var(--oslab-primary);
}
.resource-row:last-of-type {
  border-bottom: 0;
}
.empty-copy {
  margin: 0;
  font-size: 13px;
  color: var(--oslab-muted-strong);
}

/* ---- 知识详解 ---- */
.material-entry {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--oslab-line-soft);
  min-width: 0;
}
.material-entry .arco-btn {
  flex-shrink: 0;
}
.resource-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.resource-chip {
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel);
  padding: 4px 12px;
  font: inherit;
  font-size: var(--oslab-text-small);
  color: var(--oslab-ink-soft);
  cursor: pointer;
  transition: border-color var(--oslab-duration-fast) var(--oslab-ease),
    color var(--oslab-duration-fast) var(--oslab-ease),
    background var(--oslab-duration-fast) var(--oslab-ease);
}
.resource-chip:hover {
  border-color: var(--oslab-primary);
  color: var(--oslab-primary);
}
.resource-chip.active {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
  font-weight: 600;
}
.resource-detail-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}
.resource-detail-head strong {
  font-size: var(--oslab-font-body);
  font-weight: 600;
  color: var(--oslab-ink);
  overflow-wrap: anywhere;
}
.resource-detail-head .side-hint {
  overflow-wrap: anywhere;
}
.resource-frame {
  position: relative;
  height: clamp(360px, 62vh, 720px);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  background: var(--oslab-panel-soft);
  overflow: hidden;
}
.resource-iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  background: #fff;
}
.resource-frame-state {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px;
  text-align: center;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.resource-failed-text strong {
  display: block;
  margin-bottom: 4px;
  color: var(--oslab-weak-text);
}
.resource-failed-actions {
  display: flex;
  gap: 8px;
}

/* ---- 题目练习 ---- */
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}
.question-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.question-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  border: 0;
  background: transparent;
  border-radius: var(--oslab-radius-md);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background var(--oslab-duration-fast) var(--oslab-ease);
}
.question-row:hover {
  background: var(--oslab-panel-soft);
}
.question-row.expanded {
  background: var(--oslab-primary-soft);
}
.q-index {
  flex: 0 0 24px;
  text-align: center;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.q-title {
  flex: 1;
  min-width: 0;
  color: var(--oslab-ink);
  font-size: var(--oslab-font-body);
  font-weight: 500;
  overflow-wrap: anywhere;
}
.q-meta {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.q-hint {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  white-space: nowrap;
}
.q-caret {
  color: var(--oslab-muted-strong);
  flex-shrink: 0;
  transition: transform var(--oslab-duration-fast) var(--oslab-ease);
}
.q-caret.open {
  transform: rotate(180deg);
}
.answer-panel {
  margin-top: 16px;
}

/* ---- 实践 ---- */
.practice-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--oslab-line-soft);
  min-width: 0;
}
.practice-row:last-of-type {
  border-bottom: 0;
}
.practice-kind {
  flex-shrink: 0;
  font-size: var(--oslab-text-small);
  line-height: 20px;
  padding: 0 8px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel-soft);
  color: var(--oslab-ink-soft);
  white-space: nowrap;
}
.prereq-links {
  justify-content: center;
}

/* ---- 图谱导师面板 ---- */
.tutor-panel {
  position: sticky;
  top: 16px;
  align-self: start;
  min-width: 0;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  overflow: hidden;
}
.tutor-toggle {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border: 0;
  border-bottom: 1px solid var(--oslab-line);
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
/* C+B 学习图册 */
.node-header,
.evidence-card,
.content-section,
.answer-panel {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
}
.node-title {
  font-family: var(--oslab-font-display);
  font-size: 30px;
}
.node-definition,
.concept-text {
  color: var(--oslab-ink-soft);
  font-family: var(--oslab-font-display);
  font-size: 14px;
  line-height: 1.7;
}
.text-tab.active {
  color: var(--oslab-primary);
  border-bottom-color: var(--oslab-primary);
}
.metric {
  background: var(--oslab-panel-soft);
  border-radius: var(--oslab-radius-md);
}
.failure-hint {
  border-left: 2px solid var(--oslab-annotation);
  border-radius: 0 var(--oslab-radius-sm) var(--oslab-radius-sm) 0;
  background: var(--oslab-annotation-soft);
  font-family: var(--oslab-font-display);
}

@media (min-width: 1024px) {
  .tutor-toggle {
    cursor: default;
  }
}
.tutor-icon {
  width: 20px;
  height: 20px;
  color: var(--oslab-primary);
  flex-shrink: 0;
}
.tutor-toggle-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.tutor-toggle-text strong {
  color: var(--oslab-ink);
  font-size: var(--oslab-font-body);
  font-weight: 600;
}
.tutor-caret {
  color: var(--oslab-muted-strong);
  flex-shrink: 0;
  transition: transform var(--oslab-duration-fast) var(--oslab-ease);
}
.tutor-caret.open {
  transform: rotate(180deg);
}
.tutor-focus {
  margin: 12px 16px 0;
  padding: 10px 12px;
  background: var(--oslab-panel-soft);
  border-radius: var(--oslab-radius-md);
}
.context-label {
  display: block;
  color: var(--oslab-primary);
  font-size: 11px;
  font-weight: 600;
}
.tutor-focus p {
  margin: 6px 0 0;
  color: var(--oslab-ink-soft);
  font-size: 13px;
  line-height: 1.7;
}
.evidence-card {
  margin: 12px 16px 0;
  padding: 2px 12px;
  border: 1px solid var(--oslab-line-soft);
  border-radius: var(--oslab-radius-md);
}
.evidence-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 0;
  font-size: var(--oslab-text-small);
}
.evidence-line + .evidence-line {
  border-top: 1px solid var(--oslab-line-soft);
}
.evidence-label {
  flex-shrink: 0;
  color: var(--oslab-muted-strong);
}
.evidence-value {
  color: var(--oslab-ink-soft);
  text-align: right;
  overflow-wrap: anywhere;
}
.evidence-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.evidence-muted {
  color: var(--oslab-muted-strong);
}
.failure-hint {
  margin: 0;
  padding: 6px 0;
  border-top: 1px solid var(--oslab-line-soft);
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 1.6;
}
.tutor-messages {
  min-height: 160px;
  max-height: 380px;
  overflow-y: auto;
  padding: 12px 16px;
}
.tutor-welcome {
  color: var(--oslab-ink-soft);
  font-size: 13px;
  line-height: 1.8;
}
.tutor-welcome p {
  margin: 0 0 10px;
}
.tutor-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tutor-prompts button {
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel);
  padding: 4px 10px;
  font: inherit;
  font-size: var(--oslab-text-small);
  color: var(--oslab-primary);
  cursor: pointer;
  transition: border-color var(--oslab-duration-fast) var(--oslab-ease),
    background var(--oslab-duration-fast) var(--oslab-ease);
}
.tutor-prompts button:hover {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
}
.tutor-message {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--oslab-line-soft);
  font-size: 13px;
}
.tutor-message.user {
  border-left: 2px solid var(--oslab-line);
  padding-left: 10px;
}
.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--oslab-text-small);
}
.message-meta strong {
  color: var(--oslab-ink);
}
.mode-tag {
  font-size: 11px;
  line-height: 16px;
  padding: 0 6px;
  border-radius: var(--oslab-radius-pill);
}
.mode-tag.graphrag {
  color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
}
.mode-tag.fallback {
  color: var(--oslab-muted-strong);
  background: var(--oslab-panel-soft);
}
.tutor-message p {
  margin: 6px 0 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.8;
  color: var(--oslab-ink-soft);
}
.tutor-sources {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
  font-size: var(--oslab-text-small);
}
.tutor-sources button {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: 0;
  background: transparent;
  padding: 0;
  font: inherit;
  color: var(--oslab-primary);
  cursor: pointer;
  text-align: left;
  overflow-wrap: anywhere;
}
.tutor-sources button svg {
  flex-shrink: 0;
}
.tutor-pending {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.tutor-compose {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 16px;
  background: var(--oslab-panel-soft);
  border-top: 1px solid var(--oslab-line);
}
.tutor-compose .arco-textarea-wrapper {
  flex: 1;
  min-width: 0;
}
.tutor-compose .arco-btn {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
}
.tutor-trace {
  margin: 0 12px 12px;
}
.tutor-trace ol {
  margin: 0;
  padding-left: 16px;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 1.7;
}

/* 2026-10-24 节点工作台：hero、教材编号、导师焦点 */
.node-hero {
  padding: 14px 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.node-kicker {
  margin-bottom: 2px;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
}
.node-title {
  display: inline;
  padding: 0 4px;
  font-family: var(--oslab-font-display);
  font-size: 32px;
}
.node-code {
  padding: 2px 7px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 9.5px;
}
.node-definition {
  padding: 9px 11px;
  border-left: 2px solid var(--oslab-primary);
  border-radius: 0 var(--oslab-radius-md) var(--oslab-radius-md) 0;
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink-soft);
  font-size: 13px;
  line-height: 1.7;
}
.node-definition-label {
  margin-right: 6px;
  color: var(--oslab-primary);
  font-family: var(--oslab-font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}
.workspace-grid {
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
}
.workspace-main {
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
  padding: 14px 20px 6px;
}
.text-tab.active {
  border-radius: var(--oslab-radius-sm) var(--oslab-radius-sm) 0 0;
  background: var(--oslab-highlight-soft);
}
.text-tab.active::after {
  background: var(--oslab-primary);
}
.content-section {
  border-bottom-color: var(--oslab-line);
}
.textbook-chunk {
  padding-top: 10px;
}
.chunk-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.chunk-index {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--oslab-primary);
  color: #fff;
  font-family: var(--oslab-font-mono);
  font-size: 9px;
}
.chunk-head h3 {
  margin: 0;
  font-size: 15px;
}
.figure-source {
  margin: 0 0 8px;
  overflow-wrap: anywhere;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 8.5px;
  line-height: 1.6;
}
.workspace-main :deep(.annotation-note) {
  margin: 10px 0 12px;
}
.tutor-panel {
  padding: 14px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.tutor-kicker {
  margin-bottom: 3px;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 9.5px;
  letter-spacing: 0.14em;
}
.tutor-focus {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 10px;
  border: 1px solid var(--oslab-primary);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-highlight-soft);
}
.tutor-focus-index {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--oslab-primary);
  color: #fff;
  font-family: var(--oslab-font-mono);
  font-size: 9px;
}
.tutor-focus .context-label {
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}
.tutor-focus p {
  margin: 2px 0 0;
  color: var(--oslab-ink-soft);
  font-family: var(--oslab-font-display);
  font-size: 11px;
  line-height: 1.55;
}
.tutor-evidence {
  margin-top: 10px;
}
.tutor-ai-plan {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
  padding: 12px 0 0;
  border-top: 1px solid var(--oslab-line-soft);
}
.tutor-ai-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.tutor-ai-head strong {
  color: var(--oslab-ink);
  font-size: var(--oslab-text-small);
  letter-spacing: 0.02em;
}
.tutor-ai-head button {
  min-height: 32px;
  padding: 0;
  border: 0;
  border-bottom: 1px solid transparent;
  background: transparent;
  color: var(--oslab-primary);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}
.tutor-ai-head button:hover {
  border-bottom-color: currentColor;
}
.tutor-ai-head button:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
  border-radius: var(--oslab-radius-sm);
}
.tutor-weak-list {
  margin: 0;
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  color: var(--oslab-ink-soft);
  font-size: 11px;
  line-height: 1.55;
}
.tutor-evidence-ids {
  margin: 0;
  color: var(--oslab-muted-strong);
  font-size: 10px;
  overflow-wrap: anywhere;
}

/* ---- 响应式：<1024px 导师面板移到内容底部（可折叠） ---- */
@media (max-width: 1023px) {
  .workspace-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }
  .tutor-panel {
    position: static;
  }
  .tutor-messages {
    min-height: 120px;
    max-height: 260px;
  }
}
@media (max-width: 600px) {
  .node-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  .node-title {
    font-size: 22px;
  }
  .question-row {
    flex-wrap: wrap;
  }
  .q-meta {
    flex-wrap: wrap;
  }
  .practice-row {
    flex-wrap: wrap;
  }
}
</style>
