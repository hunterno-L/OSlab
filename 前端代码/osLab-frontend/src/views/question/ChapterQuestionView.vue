<template>
  <div id="chapterQuestionView">
    <a-tabs
      v-model:active-key="activeTab"
      type="card"
      :position="'left'"
      class="custom-tabs"
      @change="handleTabChange"
    >
      <a-tab-pane key="1">
        <template #title><icon-calendar /> 所有题目</template>
        <a-card>
          <a-form :model="searchParams" layout="inline">
            <a-form-item field="title" label="名称" style="min-width: 240px">
              <a-input
                v-model="searchParams.title"
                placeholder="请输入题目名称"
                allow-clear
              />
            </a-form-item>
            <a-form-item
              field="tags"
              label="关联知识点"
              style="min-width: 240px"
            >
              <a-input-tag
                v-model="searchParams.tags"
                placeholder="请输入关联知识点"
                allow-clear
              />
            </a-form-item>
            <a-form-item>
              <a-button
                type="primary"
                :loading="allQuestionsLoading"
                @click="doSubmit"
              >
                查询
              </a-button>
            </a-form-item>
          </a-form>

          <a-table
            :columns="allColumns"
            :data="dataList"
            :loading="allQuestionsLoading"
            :pagination="{
              showTotal: true,
              pageSize: searchParams.pageSize,
              current: searchParams.current,
              total,
            }"
            @page-change="onPageChange"
          >
            <template #tags="{ record }">
              <a-space wrap>
                <a-tag v-for="tag in record.tags || []" :key="tag" color="blue">
                  {{ tag }}
                </a-tag>
              </a-space>
            </template>
            <template #action="{ record }">
              <a-button type="primary" @click="goToQuestion(record)"
                >去做题</a-button
              >
            </template>
          </a-table>
        </a-card>
      </a-tab-pane>

      <a-tab-pane key="2">
        <template #title><icon-list /> 按章节浏览</template>
        <a-card>
          <a-alert type="info" :show-icon="true" style="margin-bottom: 16px">
            点击章节左侧的展开按钮，即可查看该章节中的题目。
          </a-alert>
          <a-table
            row-key="id"
            :columns="chapterColumns"
            :data="mainTableData"
            :loading="mainTableLoading"
            :pagination="false"
            :expandable="chapterExpandable"
            @expand="handleChapterExpand"
          >
            <template #expand-row="{ record }">
              <a-table
                :columns="innerColumns"
                :data="getChildrenData(record.id)"
                :loading="childLoading[record.id]"
                :pagination="false"
                row-key="id"
              >
                <template #tags="{ record: childRecord }">
                  <a-space wrap>
                    <a-tag
                      v-for="tag in childRecord.tags || []"
                      :key="tag"
                      color="blue"
                    >
                      {{ tag }}
                    </a-tag>
                  </a-space>
                </template>
                <template #action="{ record: childRecord }">
                  <a-button type="primary" @click="goToQuestion(childRecord)">
                    去做题
                  </a-button>
                </template>
              </a-table>
              <a-empty
                v-if="
                  !childLoading[record.id] &&
                  getChildrenData(record.id).length === 0
                "
                description="本章节暂未配置题目"
              />
            </template>
          </a-table>
        </a-card>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { Message } from "@arco-design/web-vue";
import { useRouter } from "vue-router";
import {
  QuestionControllerService,
  QuestionQueryRequest,
  QuestionSets,
  QuestionVO,
} from "../../../generated";

const router = useRouter();
const activeTab = ref("1");

const searchParams = ref<QuestionQueryRequest>({
  title: "",
  tags: [],
  pageSize: 8,
  current: 1,
});
const dataList = ref<QuestionVO[]>([]);
const total = ref(0);
const allQuestionsLoading = ref(false);

const allColumns = [
  { title: "题目名称", dataIndex: "title" },
  { title: "关联知识点", slotName: "tags" },
  { title: "操作", slotName: "action" },
];

const chapterColumns = [
  { title: "章节名称", dataIndex: "setName" },
  { title: "描述", dataIndex: "description" },
];

const innerColumns = [
  { title: "题目名称", dataIndex: "title" },
  { title: "关联知识点", slotName: "tags" },
  { title: "操作", slotName: "action" },
];

const mainTableData = ref<QuestionSets[]>([]);
const mainTableLoading = ref(false);
const chapterLoaded = ref(false);
const childrenData = reactive<Record<number, QuestionVO[]>>({});
const childLoading = reactive<Record<number, boolean>>({});
const expandedRowKeys = ref<Array<number | string>>([]);
const chapterExpandable = computed(() => ({
  expandedRowKeys: expandedRowKeys.value,
}));

const loadData = async () => {
  allQuestionsLoading.value = true;
  try {
    const res = await QuestionControllerService.listQuestionVoByPageUsingPost(
      searchParams.value
    );
    if (res.code === 0) {
      dataList.value = res.data?.records || [];
      total.value = res.data?.total || 0;
    } else {
      Message.error(`加载题目失败：${res.message || "未知错误"}`);
    }
  } catch (error) {
    Message.error("加载题目失败，请稍后重试");
  } finally {
    allQuestionsLoading.value = false;
  }
};

const fetchQuestionSets = async () => {
  mainTableLoading.value = true;
  try {
    const res = await QuestionControllerService.getQuestionSetsUsingGet();
    if (res.code === 0) {
      mainTableData.value = res.data || [];
      chapterLoaded.value = true;
    } else {
      Message.error(`加载章节失败：${res.message || "未知错误"}`);
    }
  } catch (error) {
    Message.error("加载章节失败，请稍后重试");
  } finally {
    mainTableLoading.value = false;
  }
};

const getChildrenData = (setId?: number) => {
  return setId === undefined ? [] : childrenData[setId] || [];
};

const handleChapterExpand = async (
  rowKey: number | string,
  record: QuestionSets
) => {
  if (record.id === undefined || record.id === null) {
    return;
  }

  const isExpanding = !expandedRowKeys.value.includes(rowKey);
  expandedRowKeys.value = isExpanding ? [rowKey] : [];
  if (!isExpanding || childrenData[record.id] !== undefined) {
    return;
  }

  childLoading[record.id] = true;
  try {
    const res = await QuestionControllerService.getQuestionBySetsUsingGet(
      record.id
    );
    if (res.code === 0) {
      childrenData[record.id] = res.data || [];
    } else {
      Message.error(`加载章节题目失败：${res.message || "未知错误"}`);
    }
  } catch (error) {
    Message.error("加载章节题目失败，请稍后重试");
  } finally {
    childLoading[record.id] = false;
  }
};

const handleTabChange = (key: string | number) => {
  if (key === "2" && !chapterLoaded.value && !mainTableLoading.value) {
    fetchQuestionSets();
  }
};

const doSubmit = () => {
  searchParams.value.current = 1;
  loadData();
};

const onPageChange = (page: number) => {
  searchParams.value.current = page;
  loadData();
};

const goToQuestion = (question: QuestionVO) => {
  if (question.id === undefined || question.id === null) {
    Message.error("题目编号缺失，无法进入做题页面");
    return;
  }
  router.push({ path: `/view/question/${question.id}` });
};

onMounted(loadData);
</script>

<style scoped>
#chapterQuestionView {
  max-width: 90%;
  margin: 0 auto;
}

.custom-tabs {
  background-color: #f0f0f0;
  border: 2px solid #1677ff;
  padding: 10px;
}
</style>
