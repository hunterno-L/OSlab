<template>
  <div id="questionSubmitView">
    <a-form :model="filterParams" layout="inline">
      <a-form-item label="题目名称">
        <a-input
          v-model="filterParams.questionName"
          placeholder="请输入题目名称"
        />
      </a-form-item>
      <a-form-item label="提交者">
        <a-input
          v-model="filterParams.submitUser"
          placeholder="请输入提交者用户名"
        />
      </a-form-item>
      <a-form-item label="状态">
        <a-select v-model="filterParams.status" placeholder="请选择状态">
          <a-option value="0">等待中</a-option>
          <a-option value="1">判题中</a-option>
          <a-option value="2">判题完成</a-option>
          <a-option value="3">判题失败</a-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" @click="doSubmit">搜索</a-button>
      </a-form-item>
      <a-form-item>
        <a-button @click="resetForm">重置</a-button>
      </a-form-item>
    </a-form>
    <a-divider />
    <a-table
      :ref="tableRef"
      :columns="columns"
      :data="filterDataList"
      :pagination="{
        showTotal: true,
        pageSize: searchParams.pageSize,
        current: searchParams.current,
        total,
      }"
      @page-change="onPageChange"
    >
      <template #judgeInfo="{ record }">
        <a-tag v-if="record.judgeInfo.message === 'Accepted'" color="green">
          {{ record.judgeInfo.message }}
        </a-tag>
        <a-tag v-if="record.judgeInfo.message === 'Wrong Answer'" color="red">
          {{ record.judgeInfo.message }}
        </a-tag>
      </template>
      <template #createTime="{ record }">
        {{ moment(record.createTime).format("YYYY-MM-DD HH:mm:ss") }}
      </template>
      <template #JudgeStatus="{ record }">
        <a-tag :color="getStatusColor(record.status)">
          <template v-if="record.status === 0">
            <a-icon type="clock-circle" /> 等待中
          </template>
          <template v-else-if="record.status === 1">
            <LoadingOutlined /> 判题中
          </template>
          <template v-else-if="record.status === 2">
            <CheckCircleOutlined /> 判题完成
          </template>
          <template v-else-if="record.status === 3">
            <ExclamationCircleOutlined /> 判题失败
          </template>
        </a-tag>
      </template>
      <template #question="{ record }">
        {{ record.questionVO?.title }}
      </template>
      <template #userInfo="{ record }">
        {{ record.userVO?.userName }}
      </template>
      <!-- 新增操作列 -->
      <template #action="{ record }">
        <a-button @click="handleViewDetail(record.id)" type="primary"
          >查看详情</a-button
        >
      </template>
    </a-table>
    <!-- 详情模态框 -->
    <a-modal
      v-model:visible="visible"
      title="提交详情"
      width="700px"
      :footer="false"
    >
      <a-descriptions :column="1" bordered v-if="detail">
        <a-descriptions-item label="题目名称">{{
          detail.questionVO?.title
        }}</a-descriptions-item>
        <a-descriptions-item label="提交代码">
          <pre class="code-block">{{ detail.code }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="判题信息">
          {{ detail.judgeInfo?.message }}
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watchEffect } from "vue";
import {
  Page_QuestionSubmitVO_,
  Question,
  QuestionControllerService,
  QuestionSubmitQueryRequest,
  QuestionSubmitVO,
} from "../../../generated";
import message from "@arco-design/web-vue/es/message";
import { useRouter } from "vue-router";
import moment from "moment";
import {
  LoadingOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
} from "@ant-design/icons-vue";

const tableRef = ref();

// 新增响应式变量
const visible = ref(false);
const detail = ref<QuestionSubmitVO>();

const dataList = ref<QuestionSubmitVO[]>([]);
const filterDataList = ref<QuestionSubmitVO[]>([]);
const total = ref(0);
const searchParams = ref<QuestionSubmitQueryRequest>({
  questionId: undefined,
  language: undefined,
  pageSize: 10,
  current: 1,
});
const filterParams = ref({
  questionName: "",
  submitUser: "",
  status: undefined,
});

const loadData = async () => {
  const res = await QuestionControllerService.listQuestionSubmitByPageUsingPost(
    {
      ...searchParams.value,
      sortField: "createTime",
      sortOrder: "descend",
    }
  );
  if (res.code === 0) {
    dataList.value = res.data.records;
    total.value = res.data.total;
    filterDataList.value = res.data.records;
  } else {
    message.error("加载失败，" + res.message);
  }
};

/**
 * 监听 searchParams 变量，改变时触发页面的重新加载
 */
watchEffect(() => {
  loadData();
});

/**
 * 页面加载时，请求数据
 */
onMounted(() => {
  loadData();
});

const columns = [
  // {
  //   title: "提交号",
  //   dataIndex: "id",
  // },
  // {
  //   title: "编程语言",
  //   dataIndex: "language",
  // },
  {
    title: "题目",
    slotName: "question",
  },
  {
    title: "判题信息",
    slotName: "judgeInfo",
  },
  {
    title: "判题状态",
    // dataIndex: "status",
    slotName: "JudgeStatus",
  },
  {
    title: "提交者",
    slotName: "userInfo",
  },
  {
    title: "提交时间",
    slotName: "createTime",
  },
  {
    title: "操作",
    slotName: "action",
  },
];

const onPageChange = (page: number) => {
  searchParams.value = {
    ...searchParams.value,
    current: page,
  };
};

const router = useRouter();

/**
 * 跳转到做题页面
 * @param question
 */
const toQuestionPage = (question: Question) => {
  router.push({
    path: `/view/question/${question.id}`,
  });
};

/**
 * 确认搜索，重新加载数据
 */
const doSubmit = () => {
  console.log("状态是：", typeof filterParams.value.status);
  // 对 dataList 进行筛选
  filterDataList.value = dataList.value.filter((item) => {
    // 根据题目名称筛选
    if (
      filterParams.value.questionName &&
      (!item.questionVO ||
        !item.questionVO.title ||
        !item.questionVO.title
          .toLowerCase()
          .includes(filterParams.value.questionName.toLowerCase()))
    ) {
      return false;
    }
    // 根据提交者用户名筛选
    if (
      filterParams.value.submitUser &&
      (!item.userVO ||
        !item.userVO.userName ||
        !item.userVO.userName
          .toLowerCase()
          .includes(filterParams.value.submitUser.toLowerCase()))
    ) {
      return false;
    }
    // 根据状态筛选
    if (
      filterParams.value.status !== undefined &&
      item.status !== Number(filterParams.value.status)
    ) {
      return false;
    }
    return true;
  });
};
const resetForm = () => {
  filterParams.value = {
    questionName: "",
    submitUser: "",
    status: undefined,
  };
  filterDataList.value = dataList.value;
};
// 新增查看详情处理函数
const handleViewDetail = async (id: number) => {
  try {
    const res = await QuestionControllerService.getQuestionSubmitByIdUsingGet(
      id
    );
    if (res.code === 0) {
      detail.value = res.data;
      visible.value = true;
    } else {
      message.error("获取详情失败: " + res.message);
    }
  } catch (error) {
    message.error("请求失败，请重试");
  }
};
const getStatusColor = (status: number) => {
  switch (status) {
    case 0:
      return "orange"; // 等待中
    case 1:
      return "blue"; // 判题中
    case 2:
      return "green"; // 判题成功
    case 3:
      return "red"; // 判题失败
    default:
      return "default";
  }
};
</script>

<style scoped>
#questionSubmitView {
  max-width: 90%;
  margin: 0 auto;
}
/* 新增代码块样式 */
.code-block {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  max-height: 400px;
  white-space: pre-wrap;
}
</style>
