<template>
  <div id="manageQuestionView">
    <a-form :model="searchParams" layout="inline">
      <a-form-item label="题目名称">
        <a-input v-model="searchParams.title" placeholder="请输入题目名称" />
      </a-form-item>
      <a-form-item field="tags" label="关联知识点">
        <a-input-tag
          v-model="searchParams.tags"
          placeholder="请输入关联知识点"
          allow-clear
          class="form-item-width"
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" @click="loadData">搜索</a-button>
      </a-form-item>
      <a-form-item>
        <a-button @click="resetForm">重置</a-button>
      </a-form-item>
      <a-form-item>
        <a-button type="outline" @click="goAdd">新建实验</a-button>
      </a-form-item>
    </a-form>
    <a-divider></a-divider>
    <a-table
      :ref="tableRef"
      :columns="columns"
      :data="dataList"
      :pagination="{
        showTotal: true,
        pageSize: searchParams.pageSize,
        current: searchParams.current,
        total,
      }"
      @page-change="onPageChange"
    >
      <template #optional="{ record }">
        <a-space>
          <a-button type="primary" @click="doUpdate(record)"> 修改</a-button>
          <a-button status="danger" @click="doDelete(record)">删除</a-button>
        </a-space>
      </template>
      <template #tags="{ record }">
        <a-space wrap>
          <a-tag
            v-for="(tag, index) of JSON.parse(record.tags)"
            :key="index"
            color="blue"
            >{{ tag }}
          </a-tag>
        </a-space>
      </template>
      <template #judgeConfig="{ record }">
        <a-descriptions style="margin-top: 20px" :size="'mini'" :column="1">
          <a-descriptions-item label="Time Limit"
            >{{ JSON.parse(record.judgeConfig).timeLimit }} ms
          </a-descriptions-item>
          <a-descriptions-item label="Memory Limit"
            >{{ JSON.parse(record.judgeConfig).memoryLimit }} MB
          </a-descriptions-item>
          <a-descriptions-item label="Stack Limit"
            >{{ JSON.parse(record.judgeConfig).stackLimit }} MB
          </a-descriptions-item>
        </a-descriptions>
      </template>
      <template #createTime="{ record }">
        {{ moment(record.createTime).format("YYYY-MM-DD HH:mm:ss") }}
      </template>
      <template #judgeCase="{ record }">
        <a-descriptions style="margin-top: 20px" :size="'mini'" :column="1">
          <a-descriptions-item label="input"
            >{{ JSON.parse(record.judgeCase)[0].input }}
          </a-descriptions-item>
          <a-descriptions-item label="output"
            >{{ JSON.parse(record.judgeCase)[0].output }}
          </a-descriptions-item>
        </a-descriptions>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watchEffect } from "vue";
import {
  Page_Question_,
  Question,
  QuestionControllerService,
} from "../../../generated";
import message from "@arco-design/web-vue/es/message";
import * as querystring from "querystring";
import { useRouter } from "vue-router";
import moment from "moment/moment";

const show = ref(true);
const tableRef = ref();
const dataList = ref([]);
const total = ref(0);
const searchParams = ref({
  pageSize: 10,
  current: 1,
  title: "",
  tags: [],
});

const loadData = async () => {
  const res = await QuestionControllerService.listQuestionByPageUsingPost(
    searchParams.value
  );
  if (res.code === 0) {
    dataList.value = res.data.records;
    total.value = res.data.total;
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

// {id: "1", title: "A+ D", content: "新的题目内容", tags: "["二叉树"]", answer: "新的答案", submitNum: 0,…}

const columns = [
  /*{
    title: "id",
    dataIndex: "id",
  },*/
  {
    title: "题目名称",
    dataIndex: "title",
  },
  /*{
    title: "内容",
    dataIndex: "content",
    ellipsis: true,
    tooltip: true,
  },*/
  {
    title: "关联知识点",
    slotName: "tags",
  },
  /*{
    title: "参考答案",
    dataIndex: "answer",
    ellipsis: true,
    tooltip: true,
  },*/
  // {
  //   title: "提交数",
  //   dataIndex: "submitNum",
  // },
  // {
  //   title: "通过数",
  //   dataIndex: "acceptedNum",
  // },
  /*{
    title: "判题配置",
    slotName: "judgeConfig",
  },
  {
    title: "判题用例",
    slotName: "judgeCase",
  },*/
  /*{
    title: "创建用户id",
    dataIndex: "userId",
  },*/
  {
    title: "创建时间",
    slotName: "createTime",
  },
  {
    title: "操作",
    slotName: "optional",
  },
];
const onPageChange = (page: number) => {
  searchParams.value = {
    ...searchParams.value,
    current: page,
  };
};
const resetForm = () => {
  searchParams.value = {
    ...searchParams.value,
    title: "",
    tags: [],
  };
};
const doDelete = async (question: Question) => {
  const res = await QuestionControllerService.deleteQuestionUsingPost({
    id: question.id,
  });
  if (res.code === 0) {
    message.success("删除成功");
    loadData();
  } else {
    message.error("删除失败");
  }
};

const router = useRouter();

const doUpdate = (question: Question) => {
  // 指向 /teaching/experiments/update（AddQuestionView 更新模式）；
  // 旧 /update/question 路径在阶段 1 重构后未挂路由，曾导致“修改”按钮落空。
  router.push({
    path: "/teaching/experiments/update",
    query: {
      id: question.id,
    },
  });
};

const goAdd = () => {
  router.push({ path: "/teaching/experiments/add" });
};
</script>

<style scoped>
#manageQuestionView {
  max-width: 90%;
  margin: 0 auto;
}
</style>
