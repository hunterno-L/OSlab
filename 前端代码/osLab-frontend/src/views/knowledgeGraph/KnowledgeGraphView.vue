<template>
  <div class="knowledge-graph-container">
    <a-page-header
      title="知识图谱一览"
      sub-title="浏览和探索完整的知识网络"
      @back="() => router.go(-1)"
    >
      <template #extra>
        <a-space>
          <a-button @click="goBackGraph">回退</a-button>
          <a-button @click="restoreGraphView">还原图谱</a-button>
          <a-button type="primary" @click="refreshGraph">
            <template #icon><icon-refresh /></template>
            刷新图谱
          </a-button>
        </a-space>
      </template>
    </a-page-header>

    <a-row :gutter="16" class="content-row">
      <!-- 左侧筛选面板 -->
      <a-col :span="6">
        <a-card title="筛选条件" class="filter-card">
          <a-input-search
            v-model="searchText"
            placeholder="搜索知识点..."
            allow-clear
            @search="handleSearch"
            @clear="handleSearch"
          />

          <a-divider orientation="left">分类筛选</a-divider>
          <a-checkbox-group v-model="selectedCategories" direction="vertical">
            <a-checkbox
              v-for="category in availableCategories"
              :key="category"
              :value="category"
            >
              {{ category }}
            </a-checkbox>
          </a-checkbox-group>

          <!--          <a-divider orientation="left">节点类型</a-divider>
          <a-radio-group v-model="nodeTypeFilter" direction="vertical">
            <a-radio value="all">全部节点</a-radio>
            <a-radio value="core">核心概念</a-radio>
            <a-radio value="sub">子概念</a-radio>
          </a-radio-group>-->

          <a-divider />
          <a-button type="primary" block @click="applyFilters">
            应用筛选
          </a-button>
          <a-button block @click="resetFilters" style="margin-top: 8px">
            重置筛选
          </a-button>
        </a-card>

        <a-card title="统计信息" class="stats-card" style="margin-top: 16px">
          <a-descriptions :column="1" bordered>
            <a-descriptions-item label="总节点数">
              {{ stats.totalNodes }}
            </a-descriptions-item>
            <a-descriptions-item label="总关系数">
              {{ stats.totalLinks }}
            </a-descriptions-item>
            <a-descriptions-item label="分类数量">
              {{ stats.totalCategories }}
            </a-descriptions-item>
          </a-descriptions>
        </a-card>
        <a-card
          title="帮助"
          class="detail-card"
          style="margin-top: 16px"
          :bordered="false"
        >
          <p>点击图谱中的节点查看详细信息</p>
          <p>双击已配置节点可打开对应详情页</p>
          <p>使用左侧面板筛选节点</p>
          <p>拖动可以平移视图</p>
          <p>滚轮可以缩放视图</p>
        </a-card>
      </a-col>

      <!-- 主图谱区域 -->
      <a-col :span="12">
        <a-card class="graph-card">
          <KnowledgeGraph
            ref="graphRef"
            :nodes="filteredNodes"
            :links="filteredLinks"
            :categories="filteredCategories"
            @node-click="handleNodeClick"
            @node-dblclick="handleNodeDblClick"
          />
        </a-card>
      </a-col>

      <!-- 右侧详情面板 -->
      <a-col :span="6">
        <a-card
          v-if="selectedNode"
          title="节点详情"
          class="detail-card"
          :bordered="false"
        >
          <a-card v-if="isAdmin" title="管理图谱">
            <a-space wrap>
              <a-popconfirm
                content="删除节点及其关联关系后无法恢复，确定继续吗？"
                @ok="deleteSelectedNode"
              >
                <a-button type="primary" status="danger">删除此节点</a-button>
              </a-popconfirm>
              <a-button type="primary" @click="showAddNodeModal"
                >新建节点</a-button
              >
              <a-button @click="showRelationModal">添加关系</a-button>
              <a-button @click="showCypherModal">执行 Cypher 语句</a-button>
            </a-space>
          </a-card>
          <a-divider>详细信息</a-divider>
          <a-descriptions :column="1" bordered>
            <a-descriptions-item label="名称">
              {{ selectedNode.name }}
            </a-descriptions-item>
            <a-descriptions-item label="分类">
              {{ selectedNode.category }}
            </a-descriptions-item>
            <a-descriptions-item label="描述" v-if="selectedNode.description">
              {{ selectedNode.description }}
            </a-descriptions-item>
          </a-descriptions>

          <a-divider orientation="left">关联节点</a-divider>
          <a-tag
            v-for="node in relatedNodes"
            :key="node.name"
            color="blue"
            style="margin-bottom: 4px; margin-right: 8px; cursor: pointer"
            @click="focusNode(node.id || '')"
          >
            {{ node.name }}
          </a-tag>
          <a-divider orientation="left">关联实验</a-divider>
          <a-tag
            v-for="question in questions"
            :key="question.id"
            color="green"
            style="margin-bottom: 4px; margin-right: 8px; cursor: pointer"
            @click="goToQuestion(question)"
          >
            {{ question.title }}
          </a-tag>
        </a-card>

        <a-card title="最近浏览" class="history-card" style="margin-top: 16px">
          <a-timeline>
            <a-timeline-item
              v-for="item in browseHistory"
              :key="item.id"
              :color="item.id === selectedNode?.id ? 'blue' : 'gray'"
            >
              <a
                @click="focusNode(item.id)"
                style="cursor: pointer; transition: color 0.3s"
                class="hoverable-link"
                >{{ item.name }}</a
              >
              <br />
              <small>{{ formatTime(item.time) }}</small>
            </a-timeline-item>
          </a-timeline>
        </a-card>
      </a-col>
    </a-row>
  </div>
  <a-modal
    v-model:visible="addRelationVisible"
    title="添加关系"
    width="700px"
    :footer="false"
  >
    <a-form :model="relationForm" label-align="left">
      <a-form-item field="fromName" label="起始节点">
        <a-input
          v-model="relationForm.fromName"
          placeholder="请输入起始节点"
        ></a-input>
      </a-form-item>
      <a-form-item field="relationType" label="关系类型">
        <a-select v-model="relationForm.relationType" placeholder="请选择关系">
          <a-option value="相关">相关</a-option>
          <a-option value="包含">包含</a-option>
          <a-option value="依赖">依赖</a-option>
          <a-option value="实现">实现</a-option>
        </a-select>
      </a-form-item>
      <a-form-item field="toName" label="目标节点">
        <a-input
          v-model="relationForm.toName"
          placeholder="请输入目标节点"
        ></a-input>
      </a-form-item>
      <a-button type="primary" @click="addRelation">添加</a-button>
    </a-form>
  </a-modal>
  <a-modal
    v-model:visible="addNodeVisible"
    title="新建知识节点"
    :confirm-loading="nodeSubmitting"
    @ok="addNode"
  >
    <a-form :model="nodeForm" label-align="left">
      <a-form-item field="name" label="名称" required>
        <a-input v-model="nodeForm.name" placeholder="请输入节点名称" />
      </a-form-item>
      <a-form-item field="category" label="分类">
        <a-input v-model="nodeForm.category" placeholder="例如：进程管理" />
      </a-form-item>
      <a-form-item field="description" label="描述">
        <a-textarea
          v-model="nodeForm.description"
          placeholder="请输入节点说明"
        />
      </a-form-item>
    </a-form>
  </a-modal>
  <a-modal
    v-model:visible="cypherVisible"
    title="执行 Cypher 语句"
    :confirm-loading="cypherSubmitting"
    @ok="executeCypher"
  >
    <a-alert type="warning" :show-icon="true" style="margin-bottom: 16px">
      该操作会直接修改知识图谱，仅限管理员在确认语句无误后使用。
    </a-alert>
    <a-textarea
      v-model="cypherQuery"
      :auto-size="{ minRows: 6, maxRows: 12 }"
      placeholder="例如：MATCH (n {name: '示例节点'}) RETURN n"
    />
  </a-modal>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { useRouter } from "vue-router";

import KnowledgeGraph from "@/components/KnowledgeGraph.vue";
import { Message } from "@arco-design/web-vue";
import {
  CategoryDTO,
  KnowledgeGraphControllerService,
  LinkDTO,
  NodeDTO,
  QuestionControllerService,
  QuestionQueryRequest,
  QuestionVO,
} from "../../../generated";
import store from "@/store";
import ACCESS_ENUM from "@/access/accessEnum";

const router = useRouter();
const detailPageByNodeName: Record<string, string> = {
  系统调用: "系统调用概念详解.html",
  进程调度: "进程调度.html",
};

const buildStaticPagePath = (fileName: string) => {
  return `/${encodeURIComponent(fileName)}`;
};

// 图谱数据
const nodes = ref<NodeDTO[]>([]);
const links = ref<LinkDTO[]>([]);
const categories = ref<CategoryDTO[]>([]);
const graphRef = ref();

// 筛选状态
const searchText = ref("");
const selectedCategories = ref<string[]>([]);
const nodeTypeFilter = ref("all");

// 选中节点状态
const selectedNode = ref<NodeDTO | null>(null);
const browseHistory = ref<Array<{ id: string; name: string; time: Date }>>([]);

// 统计信息
const stats = computed(() => ({
  totalNodes: nodes.value.length,
  totalLinks: links.value.length,
  totalCategories: categories.value.length,
}));

// 可用分类
const availableCategories = computed(() => {
  return Array.from(new Set(categories.value.map((c) => c.name)));
});

// 相关链接
const relatedLinks = computed(() => {
  if (!selectedNode.value) return [];
  return links.value.filter(
    (link) =>
      link.source === selectedNode.value?.id ||
      link.target === selectedNode.value?.id
  );
});
const relatedNodes = computed(() => {
  if (!selectedNode.value) return [];
  const filteredLinks = links.value.filter(
    (link) =>
      link.source === selectedNode.value?.id ||
      link.target === selectedNode.value?.id
  );
  const relatedNodeIds = new Set(
    filteredLinks.map((link) => [link.source, link.target].flat()).flat()
  );
  return nodes.value.filter((node) => relatedNodeIds.has(node.id));
});

// 过滤后的数据
const filteredNodes = computed(() => {
  let result = nodes.value;

  // 按搜索文本过滤
  if (searchText.value) {
    result = result.filter((node) =>
      (node.name || "").toLowerCase().includes(searchText.value.toLowerCase())
    );
  }

  // 按分类过滤
  if (selectedCategories.value.length > 0) {
    result = result.filter((node) =>
      selectedCategories.value.includes(node.category || "")
    );
  }

  // 按节点类型过滤
  if (nodeTypeFilter.value !== "all") {
    result = result.filter((node) =>
      nodeTypeFilter.value === "core"
        ? node.category?.includes("核心")
        : node.category?.includes("子")
    );
  }

  return result;
});

const filteredLinks = computed(() => {
  const visibleNodeIds = new Set(filteredNodes.value.map((n) => n.id));
  return links.value.filter(
    (link) => visibleNodeIds.has(link.source) && visibleNodeIds.has(link.target)
  );
});

const filteredCategories = computed(() => {
  return categories.value;
});

// 加载图谱数据
const loadGraphData = async () => {
  try {
    const res =
      await KnowledgeGraphControllerService.getAllKnowledgeGraphDataUsingGet();
    if (res.code === 0 && res.data) {
      nodes.value = res.data.nodes || [];
      links.value = res.data.links || [];
      categories.value = res.data.categories || [];
    } else {
      Message.error("加载知识图谱失败: " + res.message);
    }
  } catch (error) {
    Message.error("加载知识图谱失败");
    console.error(error);
  }
};

// 节点点击处理
const handleNodeClick = (node: NodeDTO) => {
  selectedNode.value = node;
  // 清空 tags 数组
  searchParams.value.tags = [];
  searchParams.value.tags?.push(selectedNode.value.name || "");
  loadQuestionData();
  // 添加到浏览历史
  const existingIndex = browseHistory.value.findIndex(
    (item) => item.id === node.id
  );
  if (existingIndex >= 0) {
    browseHistory.value.splice(existingIndex, 1);
  }
  browseHistory.value.unshift({
    id: node.id || "",
    name: node.name || "default",
    time: new Date(),
  });

  // 保留最近10条记录
  if (browseHistory.value.length > 10) {
    browseHistory.value.pop();
  }
};

const handleNodeDblClick = (node: NodeDTO) => {
  const nodeName = node.name || "";
  const detailPageFileName = detailPageByNodeName[nodeName];
  if (!detailPageFileName) {
    Message.info(`节点“${nodeName || "未命名节点"}”暂未配置详情页`);
    return;
  }
  window.location.assign(buildStaticPagePath(detailPageFileName));
};

// 聚焦到特定节点
const focusNode = async (nodeId: string) => {
  const node = nodes.value.find((n) => n.id === nodeId);
  if (!node) {
    Message.warning("未找到该知识节点");
    return;
  }
  handleNodeClick(node);
  try {
    const newData =
      await KnowledgeGraphControllerService.getKnowledgeGraphDataUsingGet(
        node.name
      );
    if (newData.code === 0) {
      nodes.value = newData.data?.nodes || [];
      links.value = newData.data?.links || [];
      categories.value = newData.data?.categories || [];
    } else {
      Message.error(`加载关联图谱失败：${newData.message || "未知错误"}`);
    }
  } catch (error) {
    Message.error("加载关联图谱失败，请稍后重试");
  }
};

// 获取节点名称
const getNodeName = (nodeId: string) => {
  return nodes.value.find((n) => n.id === nodeId)?.name || nodeId;
};

// 搜索处理
const handleSearch = () => {
  // 计算属性会自动更新
};

// 应用筛选
const applyFilters = () => {
  // 计算属性会自动更新
};

// 重置筛选
const resetFilters = () => {
  searchText.value = "";
  selectedCategories.value = [];
  nodeTypeFilter.value = "all";
};

// 刷新图谱
const refreshGraph = async () => {
  await loadGraphData();
  Message.success("图谱已刷新");
};

const goBackGraph = () => {
  graphRef.value?.goBack?.();
};

const restoreGraphView = () => {
  graphRef.value?.restoreGraph?.();
};

// 格式化时间
const formatTime = (date: Date) => {
  return new Date(date).toLocaleTimeString();
};

onMounted(() => {
  loadGraphData();
});
//题目
const searchParams = ref<QuestionQueryRequest>({
  tags: [],
});
const questions = ref<QuestionVO[]>([]);
const loadQuestionData = async () => {
  try {
    const res = await QuestionControllerService.listQuestionVoUsingPost(
      searchParams.value
    );
    if (res.code === 0) {
      questions.value = res.data || [];
    } else {
      Message.error("加载关联题目失败：" + res.message);
    }
  } catch (error) {
    Message.error("加载关联题目失败，请稍后重试");
  }
};
const isAdmin = computed(() => {
  return store.state.user.loginUser?.userRole === ACCESS_ENUM.ADMIN;
});
//模态框
const addRelationVisible = ref(false);
const showRelationModal = () => {
  addRelationVisible.value = true;
};
const relationForm = ref({
  fromName: "",
  toName: "",
  relationType: "",
});
const addRelation = async () => {
  if (
    !relationForm.value.fromName.trim() ||
    !relationForm.value.toName.trim() ||
    !relationForm.value.relationType
  ) {
    Message.warning("请完整填写关系信息");
    return;
  }
  try {
    const res =
      await KnowledgeGraphControllerService.addKnowledgeGraphRelationUsingPost(
        relationForm.value
      );
    if (res.code === 0) {
      Message.success("关系添加成功");
      addRelationVisible.value = false;
      relationForm.value = { fromName: "", toName: "", relationType: "" };
      await loadGraphData();
    } else {
      Message.error(`关系添加失败：${res.message || "未知错误"}`);
    }
  } catch (error) {
    Message.error("关系添加失败，请稍后重试");
  }
};

const addNodeVisible = ref(false);
const nodeSubmitting = ref(false);
const nodeForm = ref<NodeDTO>({ name: "", category: "", description: "" });
const showAddNodeModal = () => {
  nodeForm.value = { name: "", category: "", description: "" };
  addNodeVisible.value = true;
};
const addNode = async () => {
  if (!nodeForm.value.name?.trim()) {
    Message.warning("请输入节点名称");
    return;
  }
  nodeSubmitting.value = true;
  try {
    const res =
      await KnowledgeGraphControllerService.addKnowledgeGraphNodeUsingPost({
        ...nodeForm.value,
        name: nodeForm.value.name.trim(),
        category: nodeForm.value.category?.trim(),
        description: nodeForm.value.description?.trim(),
      });
    if (res.code === 0) {
      Message.success("节点创建成功");
      addNodeVisible.value = false;
      await loadGraphData();
    } else {
      Message.error(`节点创建失败：${res.message || "未知错误"}`);
    }
  } catch (error) {
    Message.error("节点创建失败，请稍后重试");
  } finally {
    nodeSubmitting.value = false;
  }
};

const deleteSelectedNode = async () => {
  const nodeName = selectedNode.value?.name?.trim();
  if (!nodeName) {
    Message.warning("请先选择要删除的节点");
    return;
  }
  try {
    const res = await KnowledgeGraphControllerService.deleteByNameUsingPost({
      name: nodeName,
    });
    if (res.code === 0) {
      Message.success("节点已删除");
      selectedNode.value = null;
      questions.value = [];
      await loadGraphData();
    } else {
      Message.error(`删除节点失败：${res.message || "未知错误"}`);
    }
  } catch (error) {
    Message.error("删除节点失败，请稍后重试");
  }
};

const cypherVisible = ref(false);
const cypherSubmitting = ref(false);
const cypherQuery = ref("");
const showCypherModal = () => {
  cypherQuery.value = "";
  cypherVisible.value = true;
};
const executeCypher = async () => {
  const query = cypherQuery.value.trim();
  if (!query) {
    Message.warning("请输入 Cypher 语句");
    return;
  }
  cypherSubmitting.value = true;
  try {
    const res = await KnowledgeGraphControllerService.executeUsingPost({
      query,
    });
    if (res.code === 0) {
      Message.success("Cypher 语句执行成功");
      cypherVisible.value = false;
      await loadGraphData();
    } else {
      Message.error(`执行失败：${res.message || "未知错误"}`);
    }
  } catch (error) {
    Message.error("执行失败，请检查语句或稍后重试");
  } finally {
    cypherSubmitting.value = false;
  }
};

const goToQuestion = (question: QuestionVO) => {
  if (question.id === undefined || question.id === null) {
    Message.error("题目编号缺失，无法进入做题页面");
    return;
  }
  router.push({ path: `/view/question/${question.id}` });
};
</script>

<style scoped>
.knowledge-graph-container {
  display: flex;
  flex-direction: column;
}

.content-row {
  flex: 1;
  margin-top: 16px;
}

.filter-card,
.graph-card,
.detail-card,
.history-card,
.stats-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.graph-card {
  padding: 0 !important;
  overflow: hidden; /* 添加这行确保内容不会溢出 */
}

:deep(.graph-card .arco-card-body) {
  padding: 0;
  flex: 1;
  min-height: 600px;
}

.filter-card :deep(.arco-card-body),
.detail-card :deep(.arco-card-body),
.history-card :deep(.arco-card-body),
.stats-card :deep(.arco-card-body) {
  flex: 1;
  overflow-y: auto;
}

.filter-card :deep(.arco-divider) {
  margin: 12px 0;
}
.hoverable-link:hover {
  color: #1890ff; /* 悬停时变成蓝色 */
  text-decoration: underline; /* 添加下划线 */
}
</style>
