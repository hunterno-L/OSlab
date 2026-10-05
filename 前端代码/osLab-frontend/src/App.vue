<template>
  <div id="app">
    <router-view />
    <!-- 全局可拖动 AI 助手：singleton，Teleport 到 body，登录/注册页自动隐藏 -->
    <FloatingAiAssistant />
  </div>
</template>

<style>
#app {
}
</style>
<script setup lang="ts">
import { onMounted } from "vue";
import FloatingAiAssistant from "./components/assistant/FloatingAiAssistant.vue";

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
