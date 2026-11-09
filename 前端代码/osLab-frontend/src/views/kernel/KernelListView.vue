<template>
  <div class="kl-list">
    <aside class="kl-chapters">
      <div class="kl-list-title">章节目录</div>
      <div class="kl-chapter" :class="{ on: chapter === '全部' }" @click="chapter = '全部'">
        <span>全部实验</span><span class="kl-count">{{ list.length }}</span>
      </div>
      <div v-for="item in chapters" :key="item.name" class="kl-chapter"
           :class="{ on: chapter === item.name }" @click="chapter = item.name">
        <span>{{ item.name }}</span><span class="kl-count">{{ item.count }}</span>
      </div>
    </aside>

    <main class="kl-table-wrap">
      <div class="kl-table-head">
        <h2>实验列表</h2>
        <span class="kl-total">{{ filtered.length }} 条结果</span>
      </div>
      <div class="kl-filters">
        <a-input v-model="keyword" placeholder="输入实验名称" style="width: 220px" allow-clear />
        <a-button type="primary" @click="() => {}">查询</a-button>
      </div>

      <a-table :data="filtered" :pagination="false" row-key="code" class="kl-table">
        <template #columns>
          <a-table-column title="实验名称">
            <template #cell="{ record }">
              <router-link :to="`/kernel/lab/${record.code}`" class="kl-name">{{ record.title }}</router-link>
            </template>
          </a-table-column>
          <a-table-column title="关联节点">
            <template #cell="{ record }">
              <a-tag size="small">{{ record.chapter.split("·")[0].trim() }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="通过率">
            <template #cell="{ record }">
              <span v-if="record.rate === null || record.rate === undefined" class="kl-muted">暂无数据</span>
              <span v-else>{{ record.rate }}%（{{ record.passed }}/{{ record.attempts }}）</span>
            </template>
          </a-table-column>
          <a-table-column title="更新时间">
            <template #cell="{ record }">
              <span class="kl-muted">{{ record.lastTime || "—" }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作">
            <template #cell="{ record }">
              <router-link :to="`/kernel/lab/${record.code}`">进入实验</router-link>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import axios from "axios";

const list = ref<any[]>([]);
const chapter = ref("全部");
const keyword = ref("");

const chapters = computed(() => {
  const map = new Map<string, number>();
  list.value.forEach((item) => {
    const name = String(item.chapter || "").split("·")[0].trim();
    map.set(name, (map.get(name) || 0) + 1);
  });
  return Array.from(map.entries()).map(([name, count]) => ({ name, count }));
});

const filtered = computed(() =>
  list.value.filter((item) => {
    const name = String(item.chapter || "").split("·")[0].trim();
    const okChapter = chapter.value === "全部" || name === chapter.value;
    const okKeyword = !keyword.value || String(item.title).includes(keyword.value);
    return okChapter && okKeyword;
  })
);

onMounted(async () => {
  try {
    const resp = await axios.get("/api/kernel/stats");
    list.value = resp.data || [];
  } catch {
    list.value = [];
  }
});
</script>

<style scoped>
.kl-list { display: flex; gap: 20px; padding: 20px 24px 40px; background: #f7f6ef; min-height: calc(100vh - 140px); }
.kl-chapters { width: 220px; }
.kl-list-title { font-size: 13px; color: #8a8578; margin-bottom: 10px; }
.kl-chapter { display: flex; justify-content: space-between; padding: 10px 12px; border-radius: 8px; font-size: 13px; color: #4a473f; cursor: pointer; }
.kl-chapter:hover { background: #f1eee0; }
.kl-chapter.on { background: #fdf4d6; color: #6b5b17; font-weight: 600; }
.kl-count { color: #a09a8a; }
.kl-table-wrap { flex: 1; background: #fff; border: 1px solid #e8e4d4; border-radius: 14px; padding: 18px 20px 26px; }
.kl-table-head { display: flex; justify-content: space-between; align-items: baseline; }
.kl-table-head h2 { margin: 0; font-size: 20px; }
.kl-total { font-size: 12px; color: #8a8578; }
.kl-filters { display: flex; gap: 10px; margin: 14px 0; }
.kl-name { color: #3f5d7a; }
.kl-muted { color: #a09a8a; font-size: 12px; }
</style>
