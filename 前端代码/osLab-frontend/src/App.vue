<template>
  <div id="app">
    <!-- 公开页深蓝星图背景：只由 body[data-theme="public"] 显示 -->
    <div class="public-sky" aria-hidden="true">
      <div class="public-sky-drift"></div>
      <div class="public-sky-stars"></div>
    </div>
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

/* ---------- 公开页星图背景 ----------
   固定视口、inset 略大于视口以容纳漂移；z-index:-1 位于内容之下，
   pointer-events:none。三层：深墨蓝基底 + 钴蓝/青绿/琥珀辉光 + 细星点。
   不使用 background-position 动画、不使用大范围 filter blur。 */
.public-sky {
  display: none;
  position: fixed;
  inset: -4vmax;
  z-index: -1;
  overflow: hidden;
  pointer-events: none;
  background-color: var(--public-bg);
  background-image: radial-gradient(
      120% 92% at 50% -10%,
      rgba(4, 9, 20, 0) 38%,
      rgba(3, 7, 16, 0.62) 100%
    ),
    radial-gradient(54% 44% at 14% -4%, rgba(36, 71, 216, 0.2), transparent 68%),
    radial-gradient(
      46% 38% at 88% 0%,
      rgba(38, 128, 140, 0.14),
      transparent 70%
    ),
    radial-gradient(
      44% 36% at 60% 96%,
      rgba(215, 97, 74, 0.07),
      transparent 72%
    ),
    radial-gradient(
      34% 26% at 92% 62%,
      rgba(242, 214, 75, 0.05),
      transparent 74%
    ),
    linear-gradient(168deg, #081020 0%, var(--public-bg) 54%, #040913 100%);
}

body[data-theme="public"] .public-sky {
  display: block;
}

/* 慢漂移层：两层低饱和辉光，只做 transform 动画 */
.public-sky-drift {
  position: absolute;
  inset: -10%;
  background-image: radial-gradient(
      34% 28% at 26% 34%,
      rgba(96, 140, 255, 0.16),
      transparent 70%
    ),
    radial-gradient(
      30% 24% at 74% 66%,
      rgba(58, 164, 158, 0.12),
      transparent 72%
    );
  transform: translate3d(0, 0, 0);
}

/* 细星点：三档不同步长的点阵叠加，避免出现规则条纹；静态 */
.public-sky-stars {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(
      1.2px 1.2px at 31px 43px,
      rgba(233, 240, 255, 0.9),
      transparent 100%
    ),
    radial-gradient(
      1px 1px at 71px 137px,
      rgba(200, 216, 245, 0.6),
      transparent 100%
    ),
    radial-gradient(
      1.4px 1.4px at 113px 19px,
      rgba(242, 214, 75, 0.5),
      transparent 100%
    );
  background-size: 220px 220px, 340px 340px, 470px 470px;
  background-position: 0 0, 60px 120px, 200px 40px;
  opacity: 0.62;
}

/* 仅在桌面公开页开启漂移：移动端/触屏固定，reduced-motion 固定 */
@media (min-width: 768px) and (hover: hover) and (pointer: fine) {
  @media (prefers-reduced-motion: no-preference) {
    .public-sky-drift {
      animation: public-sky-drift 52s var(--oslab-ease) infinite alternate;
      will-change: transform;
    }
  }
}

@keyframes public-sky-drift {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(-2.4%, -1.8%, 0) scale(1.03);
  }
}
</style>

<script setup lang="ts">
import { defineAsyncComponent, watch } from "vue";
import { useRouter } from "vue-router";

// 这两个全局组件依赖 bytemd/katex 等较重模块，异步加载避免拖慢首屏
// 入口 vendor 的解析与执行；挂载后立即开始拉取，交互体验不变。
const FloatingAiAssistant = defineAsyncComponent(
  () => import("./components/assistant/FloatingAiAssistant.vue")
);
const AiMaterialDialog = defineAsyncComponent(
  () => import("./components/ai/AiMaterialDialog.vue")
);

// 主题壳：公开页（/welcome、/user/login、/user/register）用深蓝星图，
// 其余登录后页面用 Qoder 浅绿纸面。写在 body 上，CSS 只在页面壳层消费，
// 白卡/表单/表格继续使用纸面令牌，不随主题变浅。
const PUBLIC_PATHS = new Set(["/welcome", "/user/login", "/user/register"]);
const router = useRouter();

function applyTheme(path: string) {
  const theme = PUBLIC_PATHS.has(path) ? "public" : "workspace";
  document.body.dataset.theme = theme;
  // index.html 的极早脚本会用 html 上的标记避免首帧闪浅色；
  // 离开公开页时必须同步清掉，否则 html / body::before 会停留在深蓝。
  if (theme === "public") {
    document.documentElement.dataset.theme = "public";
  } else {
    delete document.documentElement.dataset.theme;
  }
  const themeColor = document.querySelector('meta[name="theme-color"]');
  if (themeColor) {
    themeColor.setAttribute(
      "content",
      theme === "public" ? "#070d1c" : "#e6e9d1"
    );
  }
}

// 首次渲染前同步写入，避免布局挂载时先闪一帧纸面色再切成深蓝。
// 初始导航尚未完成时 router.currentRoute 仍是 "/"，这里以地址栏为准，
// 与 index.html 的极早脚本保持一致；之后交给路由 watch。
applyTheme(window.location.pathname);
// flush:'pre' 保证在同一帧的 DOM 更新之前切换主题，路由跳转即时生效。
watch(() => router.currentRoute.value.path, applyTheme, { flush: "pre" });
</script>
