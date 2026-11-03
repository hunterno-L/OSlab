<template>
  <div id="app">
    <router-view />
    <!-- 全局可拖动 AI 助手：singleton，Teleport 到 body，登录/注册页自动隐藏 -->
    <FloatingAiAssistant />
    <!-- 个人资料段落阅读：由引用卡/下一步卡通过 window 事件打开 -->
    <AiMaterialDialog />
  </div>
</template>

<style>
#app {
}
</style>
<script setup lang="ts">
import { onMounted } from "vue";
import FloatingAiAssistant from "./components/assistant/FloatingAiAssistant.vue";
import AiMaterialDialog from "./components/ai/AiMaterialDialog.vue";

/**
 * 全局初始化函数，有全局单次调用的代码，都可以写到这里
 */
const doInit = () => {
  console.log("hello 欢迎来到我的项目");
};

onMounted(() => {
  doInit();
});
</script>
