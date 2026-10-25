<template>
  <div class="experiments-catalog">
    <a-alert v-if="nodeFilter" class="context-alert">
      当前节点：<b>{{ nodeFilter }}</b>
      <a-button type="text" size="small" @click="clearNodeFilter"
        >清除节点筛选</a-button
      >
    </a-alert>

    <div class="experiment-grid">
      <aside class="chapter-nav">
        <div class="chapter-nav-title">章节目录</div>
        <a-spin :loading="chaptersLoading" style="width: 100%">
          <button
            class="chapter-item"
            :class="{ active: !selectedChapterId }"
            type="button"
            @click="selectChapter(null)"
          >
            <span>全部实验</span>
            <span class="chapter-count">{{ allTotalLabel }}</span>
          </button>
          <button
            v-for="chapter in chapters"
            :key="chapter.id"
            class="chapter-item"
            :class="{ active: selectedChapterId === chapter.id }"
            type="button"
            @click="selectChapter(chapter.id)"
          >
            <span>{{ chapter.setName || "未命名章节" }}</span>
            <span class="chapter-count">{{ chapterCount(chapter) }}</span>
          </button>
          <a-empty
            v-if="!chaptersLoading && !chapters.length"
            description="暂无章节"
          />
        </a-spin>
      </aside>

      <section class="experiment-main">
        <div class="experiment-section">
          <div class="section-heading">
            <div>
              <h2>{{ selectedChapter || "编程实验" }}</h2>
              <span v-if="selectedChapter" class="side-hint">本章实验列表</span>
            </div>
            <div class="intro-metric">
              <strong>{{ total }}</strong>
              <span>{{ selectedChapter ? "本章实验" : "当前结果" }}</span>
            </div>
          </div>
          <a-form
            :model="searchParams"
            layout="inline"
            class="experiment-filter"
          >
            <a-form-item field="title" label="名称">
              <a-input
                v-model="searchParams.title"
                allow-clear
                placeholder="输入实验名称"
              />
            </a-form-item>
            <a-form-item field="tags" label="节点">
              <a-input-tag
                v-model="searchParams.tags"
                allow-clear
                placeholder="输入节点名称"
              />
            </a-form-item>
            <a-form-item>
              <a-space>
                <a-button
                  type="primary"
                  :loading="experimentsLoading"
                  @click="doSearch"
                  ><template #icon><icon-search /></template>查询</a-button
                >
                <a-tooltip content="重置筛选">
                  <a-button aria-label="重置筛选" @click="clearSearch">
                    <template #icon><icon-refresh /></template>
                  </a-button>
                </a-tooltip>
              </a-space>
            </a-form-item>
          </a-form>
          <a-table
            row-key="id"
            size="small"
            :columns="columns"
            :data="dataList"
            :loading="experimentsLoading"
            :pagination="false"
            :scroll="{ x: 760 }"
          >
            <template #tags="{ record }">
              <a-space v-if="record.tags?.length" wrap size="mini">
                <a-tag v-for="tag in record.tags" :key="tag" color="gray">{{
                  tag
                }}</a-tag>
              </a-space>
              <span v-else class="side-hint">未标注</span>
            </template>
            <template #acceptedRate="{ record }">
              <span :class="['rate', rateClass(record)]">{{
                acceptedRate(record)
              }}</span>
            </template>
            <template #createTime="{ record }">
              {{ formatDate(record.createTime) }}
            </template>
            <template #action="{ record }">
              <a-button type="text" size="small" @click="toQuestionPage(record)"
                >进入实验</a-button
              >
            </template>
          </a-table>
          <div class="table-footer">
            <span class="side-hint">共 {{ total }} 个实验</span>
            <a-pagination
              v-if="!selectedChapterId"
              v-model:current="searchParams.current"
              :page-size="searchParams.pageSize"
              :total="total"
              show-total
              @change="onPageChange"
            />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { Message } from "@arco-design/web-vue";
import { useRoute, useRouter } from "vue-router";
import { formatDate } from "@/utils/dateTime";
import {
  QuestionControllerService,
  type QuestionQueryRequest,
  type QuestionSets,
  type QuestionVO,
} from "../../../generated";

const route = useRoute();
const router = useRouter();
const chapters = ref<QuestionSets[]>([]);
const chaptersLoading = ref(false);
const experimentsLoading = ref(false);
const selectedChapterId = ref<number | null>(null);
const dataList = ref<QuestionVO[]>([]);
const total = ref(0);
const allExperimentsTotal = ref<number | null>(null);
const nodeFilter = ref("");
const chapterQuestions = reactive<Record<number, QuestionVO[]>>({});
const chapterLoading = reactive<Record<number, boolean>>({});
const chapterRequests = new Map<number, Promise<QuestionVO[] | null>>();
let loadSequence = 0;

const searchParams = ref<QuestionQueryRequest>({
  title: "",
  tags: [],
  pageSize: 8,
  current: 1,
});

const columns = [
  { title: "实验名称", dataIndex: "title", width: 270 },
  { title: "关联节点", slotName: "tags", minWidth: 220 },
  { title: "通过率", slotName: "acceptedRate", width: 110 },
  { title: "更新日期", slotName: "createTime", width: 120 },
  { title: "操作", slotName: "action", width: 100 },
];

const selectedChapter = computed(
  () =>
    chapters.value.find((chapter) => chapter.id === selectedChapterId.value)
      ?.setName || ""
);

const allTotalLabel = computed(() => {
  return allExperimentsTotal.value === null ? "—" : allExperimentsTotal.value;
});

const chapterCount = (chapter: QuestionSets) => {
  if (chapter.id === undefined) return "—";
  if (chapterLoading[chapter.id]) return "…";
  return chapterQuestions[chapter.id]
    ? chapterQuestions[chapter.id].length
    : "—";
};

const routeValue = (value: unknown) =>
  Array.isArray(value)
    ? String(value[0] || "")
    : typeof value === "string"
    ? value
    : "";

const loadChapterQuestions = (
  chapter: QuestionSets
): Promise<QuestionVO[] | null> => {
  const id = chapter.id;
  if (id === undefined) return Promise.resolve(null);
  if (chapterQuestions[id] !== undefined)
    return Promise.resolve(chapterQuestions[id]);
  const pending = chapterRequests.get(id);
  if (pending) return pending;
  chapterLoading[id] = true;
  const request = (async () => {
    try {
      const res = await QuestionControllerService.getQuestionBySetsUsingGet(id);
      if (res.code === 0) {
        const rows = res.data || [];
        chapterQuestions[id] = rows;
        return rows;
      }
      Message.error(`加载章节实验失败：${res.message || "未知错误"}`);
    } catch (error) {
      Message.error("加载章节实验失败，请稍后重试");
    } finally {
      chapterLoading[id] = false;
      chapterRequests.delete(id);
    }
    return null;
  })();
  chapterRequests.set(id, request);
  return request;
};

const loadChapters = async () => {
  chaptersLoading.value = true;
  try {
    const res = await QuestionControllerService.getQuestionSetsUsingGet();
    if (res.code === 0) {
      chapters.value = res.data || [];
    } else {
      Message.error(`加载章节失败：${res.message || "未知错误"}`);
    }
  } catch (error) {
    Message.error("加载章节失败，请稍后重试");
  } finally {
    chaptersLoading.value = false;
  }
};

const filteredChapterRows = (rows: QuestionVO[]) => {
  const keyword = (searchParams.value.title || "").trim().toLowerCase();
  const tags = (searchParams.value.tags || [])
    .map((tag) => tag.trim())
    .filter(Boolean);
  return rows.filter((question) => {
    const titleMatches =
      !keyword || (question.title || "").toLowerCase().includes(keyword);
    const questionTags = question.tags || [];
    const tagsMatch = tags.every((tag) => questionTags.includes(tag));
    const nodeMatches =
      !nodeFilter.value || questionTags.includes(nodeFilter.value);
    return titleMatches && tagsMatch && nodeMatches;
  });
};

const loadData = async () => {
  const sequence = ++loadSequence;
  experimentsLoading.value = true;
  try {
    if (selectedChapterId.value !== null) {
      const chapter = chapters.value.find(
        (item) => item.id === selectedChapterId.value
      );
      if (!chapter) return;
      const rows = await loadChapterQuestions(chapter);
      if (sequence !== loadSequence) return;
      if (!rows) {
        dataList.value = [];
        total.value = 0;
        return;
      }
      dataList.value = filteredChapterRows(rows);
      total.value = dataList.value.length;
      return;
    }
    const tags = Array.from(
      new Set(
        [...(searchParams.value.tags || []), nodeFilter.value].filter(Boolean)
      )
    );
    const request: QuestionQueryRequest = {
      ...searchParams.value,
      tags,
    };
    const res = await QuestionControllerService.listQuestionVoByPageUsingPost(
      request
    );
    if (sequence !== loadSequence) return;
    if (res.code === 0) {
      dataList.value = res.data?.records || [];
      total.value = res.data?.total || 0;
      if (!request.title && !tags.length)
        allExperimentsTotal.value = total.value;
    } else {
      Message.error(`加载实验失败：${res.message || "未知错误"}`);
    }
  } catch (error) {
    if (sequence === loadSequence) Message.error("加载实验失败，请稍后重试");
  } finally {
    if (sequence === loadSequence) experimentsLoading.value = false;
  }
};

const syncRouteQuery = () => {
  nodeFilter.value = routeValue(route.query.nodeName);
  searchParams.value.title = routeValue(route.query.title);
  searchParams.value.tags = routeValue(route.query.tags)
    .split(",")
    .map((tag) => tag.trim())
    .filter(Boolean);
  const requestedPage = Number(routeValue(route.query.current));
  searchParams.value.current =
    Number.isFinite(requestedPage) && requestedPage > 0 ? requestedPage : 1;
  const requestedChapter = routeValue(route.query.chapter);
  const matched = chapters.value.find(
    (chapter) => chapter.setName === requestedChapter
  );
  selectedChapterId.value = matched?.id ?? null;
  void loadData();
};

const replaceQuery = (patch: Record<string, string | undefined>) => {
  const next: Record<string, string> = {};
  for (const [key, value] of Object.entries({ ...route.query, ...patch })) {
    const text = Array.isArray(value)
      ? value.join(",")
      : typeof value === "string"
      ? value
      : "";
    if (text) next[key] = text;
  }
  const changed = JSON.stringify(next) !== JSON.stringify(route.query);
  if (changed) void router.replace({ query: next });
  else void loadData();
};

const selectChapter = (id: number | null) => {
  const chapter = chapters.value.find((item) => item.id === id);
  router.replace({
    query: {
      ...route.query,
      chapter: chapter?.setName || undefined,
    },
  });
};

const clearNodeFilter = () => {
  const nextQuery = { ...route.query };
  delete nextQuery.nodeName;
  router.replace({ query: nextQuery });
};

const doSearch = () => {
  searchParams.value.current = 1;
  replaceQuery({
    title: searchParams.value.title.trim() || undefined,
    tags: searchParams.value.tags.length
      ? searchParams.value.tags.join(",")
      : undefined,
    current: undefined,
  });
};

const clearSearch = () => {
  searchParams.value = {
    ...searchParams.value,
    title: "",
    tags: [],
    current: 1,
  };
  replaceQuery({ title: undefined, tags: undefined, current: undefined });
};

const onPageChange = (page: number) => {
  searchParams.value.current = page;
  replaceQuery({ current: page > 1 ? String(page) : undefined });
};

const toQuestionPage = (question: QuestionVO) => {
  if (question.id === undefined || question.id === null) {
    Message.error("实验编号缺失，无法进入工作区");
    return;
  }
  const query: Record<string, string> = {
    source: "experiments",
    returnTo: route.fullPath,
  };
  if (nodeFilter.value) query.nodeName = nodeFilter.value;
  if (selectedChapter.value) query.chapter = selectedChapter.value;
  router.push({ path: `/experiments/code/${question.id}`, query });
};

const acceptedRate = (question: QuestionVO) => {
  const submits = question.submitNum || 0;
  if (!submits) return "暂无数据";
  return `${Math.round(((question.acceptedNum || 0) / submits) * 100)}%`;
};

const rateClass = (question: QuestionVO) => {
  const submits = question.submitNum || 0;
  if (!submits) return "is-empty";
  const rate = Math.round(((question.acceptedNum || 0) / submits) * 100);
  if (rate >= 95) return "is-full";
  if (rate >= 60) return "is-mid";
  return "is-low";
};

watch(
  () => route.query,
  () => syncRouteQuery(),
  { deep: true }
);

onMounted(async () => {
  await loadChapters();
  syncRouteQuery();
});
</script>

<style scoped>
.experiments-catalog {
  max-width: none;
  margin: 0;
  padding: 4px 0 28px;
}
.intro-metric {
  display: flex;
  align-items: baseline;
  gap: 6px;
  color: var(--oslab-text-secondary, #86909c);
  font-size: 12px;
}
.intro-metric strong {
  color: var(--oslab-text-title, #1d2129);
  font-size: 20px;
  line-height: 1;
}
.experiment-grid {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.chapter-nav {
  background: var(--oslab-panel, #fff);
  border: 1px solid var(--oslab-border, #e5e6eb);
  border-radius: var(--oslab-radius-lg, 8px);
}
.chapter-nav {
  position: sticky;
  top: 12px;
  padding: 12px;
}
.chapter-nav-title {
  padding: 2px 8px 10px;
  color: var(--oslab-text-title, #1d2129);
  font-size: 14px;
  font-weight: 600;
}
.chapter-item {
  display: flex;
  justify-content: space-between;
  width: 100%;
  margin: 2px 0;
  padding: 9px 8px;
  border: 0;
  border-radius: var(--oslab-radius-md, 6px);
  background: transparent;
  color: var(--oslab-text-body, #4e5969);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.chapter-item:hover,
.chapter-item.active {
  background: #f2f7ff;
  color: var(--oslab-primary, #2447d8);
}
.chapter-count {
  color: var(--oslab-text-secondary, #86909c);
  font-size: 12px;
}
.experiment-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.experiment-section {
  padding: 16px 18px;
  background: var(--oslab-panel, #fff);
  border-bottom: 1px solid var(--oslab-border, #e5e6eb);
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.section-heading h2 {
  margin: 0 0 4px;
  color: var(--oslab-text-title, #1d2129);
  font-size: 15px;
}
.experiment-filter {
  margin-bottom: 14px;
}
.experiment-filter :deep(.arco-form-item) {
  margin-bottom: 0;
}
.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding-top: 12px;
}
.experiment-section :deep(.arco-table-th) {
  background: var(--oslab-panel-sub, #f7f8fa);
  color: var(--oslab-text-secondary, #86909c);
  font-size: 12px;
}
.experiment-section :deep(.arco-table-td) {
  color: var(--oslab-text-body, #4e5969);
  font-size: 13px;
}
.side-hint {
  color: var(--oslab-text-secondary, #86909c);
  font-size: 12px;
  line-height: 1.6;
}
/* C+B 学习图册 */
.experiment-grid {
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 12px;
}
.chapter-nav {
  padding: 12px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.chapter-item {
  min-height: 36px;
  padding: 0 10px;
  border-radius: var(--oslab-radius-control);
  color: var(--oslab-ink-soft);
}
.chapter-item.active {
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink);
  font-weight: 600;
}
.experiment-section {
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.section-heading h2 {
  font-family: var(--oslab-font-display);
  font-size: 24px;
}
.table-footer {
  border-top: 1px solid var(--oslab-line-soft);
}
.experiment-section :deep(.arco-table-td) {
  padding: 6px 14px;
  white-space: nowrap;
}
.experiment-section :deep(.arco-table-th) {
  padding: 7px 14px;
}
.experiment-section :deep(.arco-table-td:first-child) {
  white-space: normal;
}
.rate {
  display: inline-block;
  padding: 1px 7px;
  border-radius: var(--oslab-radius-pill);
  font-family: var(--oslab-font-mono);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
}
.rate.is-full {
  background: var(--oslab-mastered-soft);
  color: var(--oslab-mastered-text);
}
.rate.is-mid {
  background: var(--oslab-review-soft);
  color: var(--oslab-review-text);
}
.rate.is-low {
  background: var(--oslab-weak-soft);
  color: var(--oslab-weak-text);
}
.rate.is-empty {
  color: var(--oslab-muted-strong);
}

/* 与壳层同断点：<=1024 直接单列，避免 881–1024 的两栏挤压 */
@media (max-width: 1024px) {
  .experiment-grid {
    grid-template-columns: 1fr;
  }
  .chapter-nav {
    position: static;
  }
}
@media (max-width: 640px) {
  .section-heading,
  .table-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
