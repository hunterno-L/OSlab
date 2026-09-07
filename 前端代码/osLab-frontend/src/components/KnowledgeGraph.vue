<template>
  <div class="knowledge-graph-container">
    <div ref="chart" class="knowledge-graph-chart"></div>
  </div>
</template>

<script setup>
import {
  ref,
  onMounted,
  onBeforeUnmount,
  defineProps,
  defineExpose,
  watch,
  reactive,
} from "vue";
import * as echarts from "echarts";
import { KnowledgeGraphControllerService } from "../../generated";
import { Message } from "@arco-design/web-vue";

const props = defineProps({
  nodes: { type: Array, required: true },
  links: { type: Array, required: true },
  categories: { type: Array, required: true },
});

const cloneGraphState = (source) => ({
  nodes: (source.nodes || []).map((node) => ({ ...node })),
  links: (source.links || []).map((link) => ({ ...link })),
  categories: (source.categories || []).map((category) => ({ ...category })),
});

// 创建响应式副本
const chartData = reactive(cloneGraphState(props));
const initialGraphState = ref(cloneGraphState(props));
const viewHistory = ref([]);

// eslint-disable-next-line no-undef
const emit = defineEmits(["node-click", "node-dblclick"]);

const chart = ref(null);
let myChart = null;
let clickTimer = null;
const CLICK_DELAY = 220;

// 初始化图表
const initChart = () => {
  myChart = echarts.init(chart.value);
  updateChart();
  myChart.on("click", handleChartClick);
  myChart.on("dblclick", handleNodeDblClick);
};

const applyGraphState = (state) => {
  chartData.nodes = state.nodes;
  chartData.links = state.links;
  chartData.categories = state.categories;
  updateChart();
};

const restoreGraph = () => {
  if (!viewHistory.value.length) {
    Message.info("当前已经是初始图谱");
  }
  viewHistory.value = [];
  currentZoom = 1;
  applyGraphState(cloneGraphState(initialGraphState.value));
};

const goBack = () => {
  const previousState = viewHistory.value.pop();
  if (!previousState) {
    Message.info("没有可回退的图谱");
    return;
  }
  currentZoom = 1;
  applyGraphState(previousState);
};

const handleChartClick = (params) => {
  if (params.dataType !== "node") {
    return;
  }
  if (clickTimer) {
    clearTimeout(clickTimer);
  }
  clickTimer = window.setTimeout(() => {
    clickTimer = null;
    handleNodeClick(params);
  }, CLICK_DELAY);
};

const handleNodeDblClick = (params) => {
  if (params.dataType !== "node") {
    return;
  }
  if (clickTimer) {
    clearTimeout(clickTimer);
    clickTimer = null;
  }
  emit("node-dblclick", params.data);
};

// 点击节点事件处理
const handleNodeClick = async (params) => {
  // 只处理点击的是节点（避免点击边连接线等）
  if (params.dataType === "node") {
    const nodeName = params.data.name;

    // 请求新的数据
    try {
      const newData =
        await KnowledgeGraphControllerService.getKnowledgeGraphDataUsingGet(
          nodeName
        );

      const nextState = cloneGraphState({
        nodes: newData.data?.nodes || [],
        links: newData.data?.links || [],
        categories: newData.data?.categories || [],
      });

      viewHistory.value.push(cloneGraphState(chartData));

      // 使用副本来更新数据
      applyGraphState(nextState);
    } catch (error) {
      Message.error("获取图谱数据失败");
      console.error("获取图表数据失败:", error);
    }
  }
  emit("node-click", params.data);
};

// 更新图表数据
const updateChart = () => {
  const option = {
    tooltip: {
      trigger: "item",
      confine: true, // 强制 tooltip 不超出图表区域
      extraCssText: `
      max-width: 300px;
      white-space: normal !important;
      word-wrap: break-word !important;
    `,
      formatter: (params) => {
        // 如果鼠标悬停在节点上
        if (params.dataType === "node") {
          const node = params.data;
          return `
            <div style="font-weight: bold; margin-bottom: 4px;">${
              node.name
            }</div>
            <div>${node.description || "暂无描述"}</div>
          `;
        }
        // 如果鼠标悬停在边上（连接线）
        else if (params.dataType === "edge") {
          const link = params.data;
          return `关系: ${link.label || link.value || "未知"}`;
        }
        return params.name;
      },
    },
    legend: [{ data: chartData.categories.map((c) => c.name) }],
    series: [
      {
        type: "graph",
        layout: "force",
        draggable: true, // 添加这行启用节点拖动
        roam: true, // 完全禁止缩放和平移
        data: chartData.nodes.map((node) => ({
          ...node,
          symbolSize: 55, // 设置所有节点的圆形大小为 20
        })),
        links: chartData.links,
        categories: chartData.categories,
        label: {
          show: true,
          fontSize: 12,
          scale: true, // 添加这个配置确保缩放生效
          scaleLimit: {
            min: 0.1,
            max: 10,
          },
        },
        force: { repulsion: 200, edgeLength: 200 },
        edgeLabel: { show: true, formatter: "{c}", scale: true },
        emphasis: { focus: "adjacency" },
        lineStyle: {
          normal: {
            width: 2,
            color: "", // 设置关系连线颜色为红色
            curvenes: 0.2,
            type: "solid",
          },
        },
        // 设置箭头指向
        edgeSymbol: ["none", "arrow"], // 第二个 "arrow" 表示目标节点显示箭头
        edgeSymbolSize: [0, 8], // 设置箭头大小，第二个值控制箭头的大小
      },
    ],
    toolbox: {
      feature: {
        myRestore: {
          show: true,
          title: "还原图谱",
          icon: "path://M512 128c-141.4 0-269.5 57.3-362.2 149.8L64 192v256h256l-90.5-90.5C302.7 284.7 401.9 240 512 240c150.2 0 279.7 83.1 347.7 205.5l97.6-54.2C870.8 236.2 702.8 128 512 128zM960 576H704l90.5 90.5C721.3 739.3 622.1 784 512 784c-150.2 0-279.7-83.1-347.7-205.5l-97.6 54.2C153.2 787.8 321.2 896 512 896c141.4 0 269.5-57.3 362.2-149.8L960 832V576z",
          onclick: restoreGraph,
        },
        myBack: {
          show: true,
          title: "回退",
          icon: "path://M640 192L256 512l384 320v-192h192V384H640V192z",
          onclick: goBack,
        },
        saveAsImage: {}, // 保存图片
        myZoomIn: {
          // 自定义放大按钮
          show: true,
          title: "放大",
          icon: "path://M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z", // 加号图标
          onclick: () => handleZoom(1.2),
        },
        myZoomOut: {
          // 自定义缩小按钮
          show: true,
          title: "缩小",
          icon: "path://M19 13H5v-2h14v2z", // 减号图标
          onclick: () => handleZoom(0.8),
        },
      },
    },
  };
  myChart.setOption(option);
};
let currentZoom = 1;

const handleZoom = (factor) => {
  currentZoom *= factor;
  const option = myChart.getOption();
  if (option.series && option.series[0]) {
    option.series[0].zoom = currentZoom;
    myChart.setOption(option);
  }
};
// 监听 props 变化，更新图表
watch(
  () => [props.nodes, props.links, props.categories],
  () => {
    if (myChart) {
      const nextState = cloneGraphState(props);
      initialGraphState.value = nextState;
      viewHistory.value = [];
      currentZoom = 1;
      applyGraphState(cloneGraphState(nextState));
    }
  },
  { deep: true } // 深度监听，确保数组内容变化也能触发
);

defineExpose({
  restoreGraph,
  goBack,
});

onMounted(() => {
  initChart();
});

onBeforeUnmount(() => {
  if (clickTimer) {
    clearTimeout(clickTimer);
    clickTimer = null;
  }
  if (myChart) {
    myChart.off("click", handleChartClick);
    myChart.off("dblclick", handleNodeDblClick);
    myChart.dispose();
    myChart = null;
  }
});
</script>

<style scoped>
.knowledge-graph-container {
  width: 100%;
  height: 100%;
}

.knowledge-graph-chart {
  width: 100%;
  height: 600px;
}
</style>
