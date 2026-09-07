<template>
  <div id="addQuestionView">
    <a-card>
      <div class="form-container">
        <a-form :model="form" label-align="left" class="centered-form">
          <a-form-item field="title" label="标题">
            <a-input
              v-model="form.title"
              placeholder="请输入标题"
              class="form-item-width"
            />
          </a-form-item>
          <a-form-item field="content" label="题目内容">
            <MdEditor
              :value="form.content"
              :handle-change="onContentChange"
              class="form-item-width"
            />
          </a-form-item>
          <a-form-item field="tags" label="关联知识点">
            <a-input-tag
              v-model="form.tags"
              placeholder="请选择关联知识点"
              allow-clear
              class="form-item-width"
            />
          </a-form-item>
          <a-form-item>
            <a-collapse :default-active-key="[1]" accordion>
              <a-collapse-item header="从全部知识点中选择" key="1">
                <div>
                  <a-checkbox-group v-model="form.tags">
                    <a-checkbox v-for="tag in tags" :key="tag" :value="tag">
                      {{ tag }}</a-checkbox
                    >
                  </a-checkbox-group>
                </div>
              </a-collapse-item>
            </a-collapse>
          </a-form-item>
          <a-form-item field="setId" label="题目集">
            <a-select
              v-model="form.setId"
              placeholder="请选择题目集"
              allow-clear
              :loading="setsLoading"
              class="form-item-width"
              style="width: 80%"
            >
              <a-option
                v-for="set in questionSets"
                :key="set.id"
                :value="set.id"
                :label="set.setName"
              >
                {{ set.setName }}
              </a-option>
            </a-select>
          </a-form-item>
          <a-form-item field="originalCode" label="初始框架代码">
            <div class="code-editor-wrapper">
              <CodeEditor
                :value="form.originalCode"
                :language="form.language"
                :handle-change="onOriginalCodeChange"
              />
            </div>
          </a-form-item>
          <a-form-item field="answer" label="答案">
            <div class="answer-editor">
              <CodeEditor
                :value="form.answer"
                :language="form.language"
                :handle-change="onAnswerChange"
              />
            </div>
            <!--        <MdEditor :value="form.answer" :handle-change="onAnswerChange" />-->
          </a-form-item>
          <!--      <a-form-item label="判题配置" :content-flex="false" :merge-props="false">
          <a-space direction="vertical" style="min-width: 480px">
            <a-form-item field="judgeConfig.timeLimit" label="时间限制">
              <a-input-number
                v-model="form.judgeConfig.timeLimit"
                placeholder="请输入时间限制"
                mode="button"
                min="0"
                size="large"
              />
            </a-form-item>
            <a-form-item field="judgeConfig.memoryLimit" label="内存限制">
              <a-input-number
                v-model="form.judgeConfig.memoryLimit"
                placeholder="请输入内存限制"
                mode="button"
                min="0"
                size="large"
              />
            </a-form-item>
            <a-form-item field="judgeConfig.stackLimit" label="堆栈限制">
              <a-input-number
                v-model="form.judgeConfig.stackLimit"
                placeholder="请输入堆栈限制"
                mode="button"
                min="0"
                size="large"
              />
            </a-form-item>
          </a-space>
        </a-form-item>-->
          <!--      <a-form-item
          label="测试用例配置"
          :content-flex="false"
          :merge-props="false"
        >
          <a-form-item
            v-for="(judgeCaseItem, index) of form.judgeCase"
            :key="index"
            no-style
          >
            <a-space direction="vertical" style="min-width: 640px">
              <a-form-item
                :field="`form.judgeCase[${index}].input`"
                :label="`输入用例-${index}`"
                :key="index"
              >
                <a-input
                  v-model="judgeCaseItem.input"
                  placeholder="请输入测试输入用例"
                />
              </a-form-item>
              <a-form-item
                :field="`form.judgeCase[${index}].output`"
                :label="`输出用例-${index}`"
                :key="index"
              >
                <a-input
                  v-model="judgeCaseItem.output"
                  placeholder="请输入测试输出用例"
                />
              </a-form-item>
              <a-button status="danger" @click="handleDelete(index)">
                删除
              </a-button>
            </a-space>
          </a-form-item>
          <div style="margin-top: 32px">
            <a-button @click="handleAdd" type="outline" status="success"
              >新增测试用例
            </a-button>
          </div>
        </a-form-item>-->
          <div style="margin-top: 16px" />
        </a-form>
      </div>
      <div class="button-contain">
        <a-button type="primary" style="min-width: 200px" @click="doSubmit"
          >提交
        </a-button>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import MdEditor from "@/components/MdEditor.vue";
import {
  KnowledgeGraphControllerService,
  QuestionControllerService,
  QuestionSets,
} from "../../../generated";
import message from "@arco-design/web-vue/es/message";
import { useRoute, useRouter } from "vue-router";
import CodeEditor from "@/components/CodeEditor.vue";
import { debounce } from "@arco-design/web-vue/es/_utils/debounce";

const route = useRoute();
// 如果页面地址包含 update，视为更新页面
const updatePage = route.path.includes("update");
const router = useRouter();

let form = ref({
  setId: undefined,
  title: "",
  tags: [] as string[],
  answer: "",
  content: "",
  judgeConfig: {
    memoryLimit: 1000,
    stackLimit: 1000,
    timeLimit: 1000,
  },
  judgeCase: [
    {
      input: "",
      output: "",
    },
  ],
  originalCode: "",
  language: "c",
});

// Add these new refs
const questionSets = ref<QuestionSets[]>([]);
const setsLoading = ref(false);

const isOpen = ref<boolean>(false); // 控制面板是否展开

const toggle = () => {
  isOpen.value = !isOpen.value; // 切换面板状态
};

const tags = ref<string[]>([]);
const fetchTags = async () => {
  const res =
    await KnowledgeGraphControllerService.getAllKnowledgeGraphNameUsingGet();
  tags.value = res.data || [];
};

// 新增事件处理函数
const onOriginalCodeChange = (value: string) => {
  form.value.originalCode = value;
};

/**
 * 获取题目集列表
 */
const loadQuestionSets = async () => {
  setsLoading.value = true;
  try {
    const res = await QuestionControllerService.getQuestionSetsUsingGet();
    if (res.code === 0) {
      questionSets.value = res.data || [];
    } else {
      message.error("获取题目集失败: " + res.message);
    }
  } catch (e) {
    message.error("获取题目集失败");
  } finally {
    setsLoading.value = false;
  }
};

/**
 * 根据题目 id 获取老的数据
 */
const loadData = async () => {
  const id = route.query.id;
  if (!id) {
    return;
  }
  const res = await QuestionControllerService.getQuestionVoByIdUsingGet(
    id as any
  );
  if (res.code === 0) {
    form.value = res.data as any;
    // json 转 js 对象
    /*if (!form.value.judgeCase) {
      form.value.judgeCase = [
        {
          input: "",
          output: "",
        },
      ];
    } else {
      form.value.judgeCase = JSON.parse(form.value.judgeCase as any);
    }
    if (!form.value.judgeConfig) {
      form.value.judgeConfig = {
        memoryLimit: 1000,
        stackLimit: 1000,
        timeLimit: 1000,
      };
    } else {
      form.value.judgeConfig = JSON.parse(form.value.judgeConfig as any);
    }*/
    /*if (!form.value.tags) {
      form.value.tags = [];
    } else {
      form.value.tags = JSON.parse(form.value.tags as any);
    }*/
  } else {
    message.error("加载失败，" + res.message);
  }
};

onMounted(() => {
  loadQuestionSets();
  loadData();
  fetchTags();
});
const doSubmit = async () => {
  console.log(form.value);
  // 区分更新还是创建
  if (updatePage) {
    const res = await QuestionControllerService.updateQuestionUsingPost(
      form.value
    );
    if (res.code === 0) {
      message.success("更新成功");
      await router.push({
        path: `/manage/question/`,
      });
    } else {
      message.error("更新失败，" + res.message);
    }
  } else {
    const res = await QuestionControllerService.addQuestionUsingPost(
      form.value
    );
    if (res.code === 0) {
      message.success("创建成功");
      await router.push({
        path: `/manage/question/`,
      });
    } else {
      message.error("创建失败，" + res.message);
    }
  }
};

/**
 * 新增判题用例
 */
const handleAdd = () => {
  form.value.judgeCase.push({
    input: "",
    output: "",
  });
};

/**
 * 删除判题用例
 */
const handleDelete = (index: number) => {
  form.value.judgeCase.splice(index, 1);
};

const onContentChange = (value: string) => {
  form.value.content = value;
  // 匹配 value 中包含的 tags
  /*// 将匹配到的标签赋值给 form 的 tags 属性
  form.value.tags = tags.value.filter((tag) => value.includes(tag));*/
  updateTagsDebounced(value);
};
const updateTagsDebounced = debounce((val: string) => {
  form.value.tags = tags.value.filter((tag) => val.includes(tag));
}, 2000); // 500ms 不输入就认为“输入完毕”

const onAnswerChange = (value: string) => {
  form.value.answer = value;
};
</script>

<style scoped>
#addQuestionView {
  max-width: 90%;
  margin: 0 auto;
}
/* 父组件中定义高度 */
.code-editor-wrapper {
  height: 500px;
  width: 80%;
}
.answer-editor {
  height: 500px;
  width: 80%;
}
.collapsible-panel {
  border: 1px solid #ddd;
  border-radius: 5px;
  width: 80%;
  max-width: 80%;
}

.panel-header {
  display: flex;
  align-items: center; /* 垂直居中 */
  justify-content: flex-start; /* 水平起始对齐 */
  padding: 10px;
  cursor: pointer;
  text-align: center;
  background-color: #007bff; /* 蓝色背景 */
  color: white; /* 白色文字 */
}

.panel-body {
  padding: 10px;
  background-color: #f9f9f9;
  border: 1px solid #007bff;
}
.form-item-width {
  width: 80%; /* 或者设置为具体的宽度，如 300px */
}
.form-container {
  display: flex;
  justify-content: center; /* 水平居中 */
  align-items: center; /* 垂直居中 */
  height: 100%; /* 确保容器占满整个卡片高度 */
}
.centered-form {
  width: 80%; /* 设置表单的宽度 */
  margin: auto; /* 水平居中 */
}
.button-contain {
  display: flex;
  justify-content: center; /* 水平居中 */
  margin-top: 16px;
}
</style>
