<template>
  <div class="legacy-route">
    <a-spin :loading="true" />
    <span>正在打开实验中心</span>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

onMounted(() => {
  router.replace({ path: "/experiments", query: route.query });
});
</script>

<style scoped>
.legacy-route {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  min-height: 180px;
  color: var(--oslab-text-secondary, #86909c);
  font-size: 13px;
}
</style>
