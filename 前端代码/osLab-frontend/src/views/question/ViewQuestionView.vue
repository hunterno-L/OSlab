<template>
  <div id="viewQuestionView">
    <a-row :gutter="[24, 24]">
      <a-col :md="12" :xs="24">
        <a-tabs default-active-key="question">
          <a-tab-pane key="question" title="题目">
            <a-card v-if="question" :title="question.title">
              <!--              <a-descriptions
                title="判题条件"
                :column="{ xs: 1, md: 2, lg: 3 }"
              >
                <a-descriptions-item label="时间限制">
                  {{ question.judgeConfig.timeLimit ?? 0 }}
                </a-descriptions-item>
                <a-descriptions-item label="内存限制">
                  {{ question.judgeConfig.memoryLimit ?? 0 }}
                </a-descriptions-item>
                <a-descriptions-item label="堆栈限制">
                  {{ question.judgeConfig.stackLimit ?? 0 }}
                </a-descriptions-item>
              </a-descriptions>-->
              <MdViewer :value="question.content || ''" />
              <template #extra>
                <a-space wrap>
                  <p>关联知识点：</p>
                  <a-tag
                    v-for="(tag, index) of question.tags"
                    :key="index"
                    color="blue"
                    >{{ tag }}
                  </a-tag>
                </a-space>
              </template>
            </a-card>
          </a-tab-pane>
          <a-tab-pane key="knowledgeGraph" title="知识图谱">
            <a-card v-if="question" :title="question.title">
              <KnowledgeGraph
                :nodes="nodes"
                :links="links"
                :categories="categories"
              />
              <template #extra>
                <a-button type="primary" @click="goToKnowledgeGraphPage">
                  查看完整知识图谱
                </a-button>
              </template>
            </a-card>
          </a-tab-pane>
          <!--          <a-tab-pane key="comment" title="评论" disabled> 评论区</a-tab-pane>
          <a-tab-pane key="answer" title="答案"> 暂时无法查看答案</a-tab-pane>-->
          <a-tab-pane key="chat" title="问答助手" class="chat-tab">
            <ChatWindow />
          </a-tab-pane>
        </a-tabs>
      </a-col>
      <a-col :md="12" :xs="24">
        <a-form :model="form" layout="inline">
          <a-form-item
            field="language"
            label="编程语言"
            style="min-width: 240px"
          >
            <a-select
              v-model="form.language"
              :style="{ width: '320px' }"
              placeholder="选择编程语言"
            >
              <a-option value="c">C</a-option>
              <a-option value="cpp">C++</a-option>
              <a-option value="java">Java</a-option>
              <a-option value="go">Go</a-option>
            </a-select>
          </a-form-item>
        </a-form>
        <a-tabs>
          <a-tab-pane key="operate" title="工作台"></a-tab-pane>
        </a-tabs>
        <CodeEditor
          :value="form.code as string"
          :language="form.language"
          :handle-change="changeCode"
        />
        <a-divider />
        <a-button
          type="primary"
          style="
            min-width: 200px;
            display: block;
            margin: 0 auto;
            background-color: white;
            color: #1677ff;
            border: 1px solid #1677ff;
          "
          @click="doSubmit"
        >
          评测
        </a-button>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watchEffect, withDefaults, defineProps } from "vue";
import {
  CategoryDTO,
  type GraphDataDTO,
  KnowledgeGraphControllerService,
  LinkDTO,
  NodeDTO,
  Question,
  QuestionControllerService,
  QuestionSubmitAddRequest,
  QuestionVO,
} from "../../../generated";
import message from "@arco-design/web-vue/es/message";
import CodeEditor from "@/components/CodeEditor.vue";
import MdViewer from "@/components/MdViewer.vue";
import KnowledgeGraph from "@/components/KnowledgeGraph.vue";
import ChatWindow from "@/components/ChatWindow.vue";
import { useRouter } from "vue-router";

interface Props {
  id: string;
}

const props = withDefaults(defineProps<Props>(), {
  id: () => "",
});

const question = ref<QuestionVO>();
const router = useRouter();

const loadData = async () => {
  const res = await QuestionControllerService.getQuestionVoByIdUsingGet(
    props.id as any
  );
  if (res.code === 0) {
    question.value = res.data;
    // 将标题设置到代码编辑器的内容中
    if (res.data?.title) {
      form.value.code = res.data.originalCode; // 关键修改点
    }
  } else {
    message.error("加载失败，" + res.message);
  }
  if (question.value?.tags) {
    const graph =
      await KnowledgeGraphControllerService.getKnowledgeGraphDataByListUsingPost(
        question.value.tags
      );
    if (graph.code == 0) {
      console.log(graph.data);
      if (graph.data != undefined) {
        nodes.value = graph.data.nodes || [];
        links.value = graph.data.links || [];
        categories.value = graph.data.categories || [];
      }
    }
  } else {
    console.error("Tags are undefined");
  }
};

const form = ref<QuestionSubmitAddRequest>({
  language: "c",
  code: "",
});

/**
 * 提交代码
 */
const doSubmit = async () => {
  if (!question.value?.id) {
    return;
  }
  const res = await QuestionControllerService.doQuestionSubmitUsingPost({
    ...form.value,
    questionId: question.value.id,
  });
  if (res.code === 0) {
    message.success("提交成功");
    await router.push({
      path: `/question_submit`,
    });
  } else {
    message.error("提交失败," + res.message);
  }
};
const goToKnowledgeGraphPage = () => {
  router.push({
    path: "/",
    query: {
      // 可以传递当前题目的标签作为参数
      tags: question.value?.tags?.join(","),
    },
  });
};

/**
 * 页面加载时，请求数据
 */
onMounted(() => {
  loadData();
});

const changeCode = (value: string) => {
  form.value.code = value;
};
const nodes = ref<NodeDTO[]>([]);
const links = ref<LinkDTO[]>([]);
const categories = ref<CategoryDTO[]>([]);
/*// 模拟外部传递的数据
const nodes = ref([
  { id: "1", name: "操作系统", category: "子概念" },
  { id: "2", name: "进程管理", category: "子概念" },
  { id: "3", name: "内存管理", category: "子概念" },
]);

const links = ref([
  { source: "1", target: "2", value: "关系1" },
  { source: "1", target: "3", value: "关系2" },
]);
const categories = ref([{ name: "核心概念" }, { name: "子概念" }]);*/
</script>

<style>
#viewQuestionView {
  max-width: 90%;
  margin: 0 auto;
}

#viewQuestionView .arco-space-horizontal .arco-space-item {
  margin-bottom: 0 !important;
}
.chat-tab {
  display: flex;
  justify-content: center; /* 水平居中 */
  align-items: center; /* 垂直居中 */
  height: 100vh; /* 设置高度为视口高度 */
}
</style>
