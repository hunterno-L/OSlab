<template>
  <!--  <div
    id="code-editor"
    ref="codeEditorRef"
    style="min-height: 400px; height: 60vh"
  />-->
  <div class="code-editor-container">
    <div id="code-editor" ref="codeEditorRef" class="code-editor" />
  </div>
  <!--  <a-button @click="fillValue">填充值</a-button>-->
</template>

<script setup lang="ts">
import * as monaco from "monaco-editor";
import { onMounted, ref, toRaw, withDefaults, defineProps, watch } from "vue";

/**
 * 定义组件属性类型
 */
interface Props {
  value: string;
  language?: string;
  handleChange: (v: string) => void;
}

/**
 * 给组件指定初始值
 */
const props = withDefaults(defineProps<Props>(), {
  value: () => "",
  language: () => "java",
  handleChange: (v: string) => {
    console.log(v);
  },
});

const codeEditorRef = ref();
const codeEditor = ref();

// const fillValue = () => {
//   if (!codeEditor.value) {
//     return;
//   }
//   // 改变值
//   toRaw(codeEditor.value).setValue("新的值");
// };

watch(
  () => props.language,
  () => {
    if (codeEditor.value) {
      monaco.editor.setModelLanguage(
        toRaw(codeEditor.value).getModel(),
        props.language
      );
    }
  }
);
// 新增：监听语言变化
watch(
  () => props.language,
  () => {
    if (codeEditor.value) {
      monaco.editor.setModelLanguage(
        toRaw(codeEditor.value).getModel(),
        props.language
      );
    }
  }
);

// 新增：监听 value 变化
watch(
  () => props.value,
  (newValue) => {
    if (codeEditor.value) {
      const editor = toRaw(codeEditor.value);
      if (newValue !== editor.getValue()) {
        editor.setValue(newValue);
      }
    }
  },
  { immediate: true } // 关键：确保初始值生效
);
onMounted(() => {
  if (!codeEditorRef.value) {
    return;
  }
  // Hover on each property to see its docs!
  codeEditor.value = monaco.editor.create(codeEditorRef.value, {
    value: props.value,
    language: props.language,
    automaticLayout: true,
    colorDecorators: true,
    minimap: {
      enabled: false,
    },
    readOnly: false,
    theme: "vs-dark",
    // lineNumbers: "off",
    // roundedSelection: false,
    // scrollBeyondLastLine: false,
  });

  // 编辑 监听内容变化
  codeEditor.value.onDidChangeModelContent(() => {
    props.handleChange(toRaw(codeEditor.value).getValue());
  });
});
</script>

<style scoped>
/* 新增样式 */
.code-editor-container {
  height: 500px; /* 固定高度 */
  border: 1px solid #434343; /* 可选边框 */
  border-radius: 4px;
}

.code-editor {
  height: 100%; /* 关键：让编辑器撑满容器 */
  width: 100%;
  overflow: hidden; /* 防止滚动条溢出 */
}
</style>
