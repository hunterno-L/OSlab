<template>
  <div class="accordion">
    <div v-for="(panel, index) in panels" :key="index" class="accordion-item">
      <div
        class="accordion-header"
        @click="togglePanel(index)"
        :class="{ 'is-open': activeIndex === index }"
      >
        {{ panel.title }}
      </div>
      <div v-show="activeIndex === index" class="accordion-body">
        <slot :name="panel.slotName">{{ panel.content }}</slot>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "Accordion-collapse",
  props: {
    panels: {
      type: Array,
      required: true,
    },
  },
  data() {
    return {
      activeIndex: null, // 默认没有展开面板
    };
  },
  methods: {
    togglePanel(index) {
      this.activeIndex = this.activeIndex === index ? null : index; // 切换当前面板
    },
  },
};
</script>

<style scoped>
.accordion {
  border: 1px solid #ccc;
  border-radius: 4px;
  width: 100%;
}

.accordion-item {
  border-top: 1px solid #ccc;
}

.accordion-header {
  padding: 10px;
  background-color: #f0f0f0;
  cursor: pointer;
  font-weight: bold;
}

.accordion-header.is-open {
  background-color: #e0e0e0;
}

.accordion-body {
  padding: 10px;
  background-color: #fafafa;
  border-top: 1px solid #eee;
}
</style>
