<template>
  <Viewer :value="value" :plugins="plugins" :rehype-plugins="rehypePlugins" />
</template>

<script setup lang="ts">
import gfm from "@bytemd/plugin-gfm";
import highlight from "@bytemd/plugin-highlight";
import { Viewer } from "@bytemd/vue-next";
import rehypeSanitize from "rehype-sanitize";
import { withDefaults, defineProps } from "vue";

/**
 * 定义组件属性类型
 */
interface Props {
  value: string;
}

const plugins = [
  gfm(),
  highlight(),
  // Add more plugins here
];

/**
 * 统一消毒：过滤题目/帖子内容中的脚本与危险 HTML，防止存储型 XSS。
 */
const rehypePlugins = [rehypeSanitize];

/**
 * 给组件指定初始值
 */
const props = withDefaults(defineProps<Props>(), {
  value: () => "",
});
</script>

<style></style>
