<template>
  <div ref="revealHost" class="landing">
    <header class="landing-header">
      <a class="landing-brand" href="/welcome" @click.prevent="goWelcome">
        <img
          :src="brandMarkUrl"
          alt=""
          width="31"
          height="31"
          class="landing-brand-mark"
        />
        <span class="landing-brand-name">ZhiCore</span>
      </a>
      <nav class="landing-nav" aria-label="平台介绍">
        <a href="#features">功能概览</a>
        <a href="#mainline">学习主线</a>
        <a href="#graph">知识图谱</a>
        <a href="#practice">练习与实验</a>
        <a href="#evidence">学习证据</a>
      </nav>
      <button
        type="button"
        class="landing-cta"
        data-auth-trigger
        @click="onCta"
      >
        <span>{{ ctaLabel }}</span>
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          aria-hidden="true"
        >
          <path d="M5 12h14" />
          <path d="m13 6 6 6-6 6" />
        </svg>
      </button>
    </header>

    <div class="landing-body">
      <section class="landing-hero" aria-labelledby="landing-hero-title">
        <div class="landing-hero-copy">
          <h1 id="landing-hero-title">Page&amp;Frame</h1>
          <p class="landing-hero-lede">章节、图谱、练习、实验，一处完成。</p>
          <div class="landing-hero-actions">
            <button
              type="button"
              class="landing-primary"
              data-auth-trigger
              @click="onCta"
            >
              {{ loggedIn ? "进入学习" : "登录开始学习" }}
            </button>
          </div>
        </div>
      </section>

      <section
        id="features"
        class="landing-feature landing-reveal"
        data-reveal
        aria-labelledby="features-title"
      >
        <div class="landing-feature-copy">
          <h2 id="features-title">从章节学习，到算法实践</h2>
          <p>
            把教材内容、知识关系和实践入口放在同一条学习路径上，先理解，再动手验证。
          </p>
          <nav class="landing-feature-list" aria-label="学习功能">
            <a class="landing-feature-item" href="#mainline">
              <span class="landing-feature-index">01</span>
              <span>
                <strong>章节学习</strong>
                <small>按教材顺序推进</small>
              </span>
              <span class="landing-feature-arrow" aria-hidden="true">→</span>
            </a>
            <a class="landing-feature-item" href="#graph">
              <span class="landing-feature-index">02</span>
              <span>
                <strong>知识图谱</strong>
                <small>查看概念与关系</small>
              </span>
              <span class="landing-feature-arrow" aria-hidden="true">→</span>
            </a>
            <a class="landing-feature-item" href="#practice">
              <span class="landing-feature-index">03</span>
              <span>
                <strong>题目与实验</strong>
                <small>从练习走向实现</small>
              </span>
              <span class="landing-feature-arrow" aria-hidden="true">→</span>
            </a>
            <a class="landing-feature-item" href="#evidence">
              <span class="landing-feature-index">04</span>
              <span>
                <strong>学习证据</strong>
                <small>记录每一次掌握</small>
              </span>
              <span class="landing-feature-arrow" aria-hidden="true">→</span>
            </a>
          </nav>
        </div>
        <div class="landing-feature-media">
          <PagingTheater mode="guided" label="页面置换算法演示" />
          <div class="landing-feature-caption">
            <strong>页面置换算法演示</strong>
            <span>播放或逐步查看访问序列与页框变化</span>
          </div>
        </div>
      </section>

      <section
        id="mainline"
        class="landing-section landing-reveal"
        data-reveal
        aria-labelledby="mainline-title"
      >
        <div class="landing-section-copy">
          <h2 id="mainline-title">按教材顺序，完成一个个学习单元</h2>
          <p>
            五个章节拆成有目标的学习单元。第一次学习从首个已发布单元开始，回来时接着上次的位置。
          </p>
        </div>
        <MainlineDemo />
      </section>

      <section
        id="graph"
        class="landing-section is-reversed landing-reveal"
        data-reveal
        aria-labelledby="graph-title"
      >
        <div class="landing-section-copy">
          <h2 id="graph-title">看见概念，也看见自己的掌握</h2>
          <p>
            图谱连接概念、算法、机制与实现；学习证据叠加在节点上，下一步从当前薄弱处继续。
          </p>
        </div>
        <GraphProfileDemo />
      </section>

      <section
        id="practice"
        class="landing-section landing-reveal"
        data-reveal
        aria-labelledby="practice-title"
      >
        <div class="landing-section-copy">
          <h2 id="practice-title">理解之后，立即动手验证</h2>
          <p>
            题目练习、算法演示和编程实验保持独立入口，也能被学习单元引用，形成从理解到实现的闭环。
          </p>
        </div>
        <PracticeTabsDemo />
      </section>

      <section
        id="evidence"
        class="landing-section landing-reveal"
        data-reveal
        aria-labelledby="evidence-title"
      >
        <div class="landing-section-copy">
          <h2 id="evidence-title">每一步都有可回看的证据</h2>
          <p>
            活动进度、单元进度、达标情况和节点掌握分开记录，让学习状态有依据，也让下一步更明确。
          </p>
        </div>
        <EvidenceLedger />
      </section>
    </div>

    <footer class="landing-footer">
      <span>ZhiCore · 操作系统学习平台</span>
      <nav aria-label="页脚">
        <a href="/welcome" @click.prevent="goWelcome">平台简介</a>
        <a :href="loggedIn ? '/graph' : '/user/login'">课程入口</a>
        <a :href="loggedIn ? '/resources' : '/user/login'">学习资源</a>
        <a href="/user/login">登录</a>
      </nav>
      <span>© 2026 ZhiCore</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useStore } from "vuex";
import PagingTheater from "@/components/theater/PagingTheater.vue";
import MainlineDemo from "@/components/landing/MainlineDemo.vue";
import GraphProfileDemo from "@/components/landing/GraphProfileDemo.vue";
import PracticeTabsDemo from "@/components/landing/PracticeTabsDemo.vue";
import EvidenceLedger from "@/components/landing/EvidenceLedger.vue";
import { ROLE_HOME } from "@/navigation/appNavigation";
import ACCESS_ENUM from "@/access/accessEnum";
import "@/styles/landing-demo.css";

const brandMarkUrl = `${process.env.BASE_URL}brand-mark.svg`;
// 原图 1536×1024 的 PNG（1.7MB）转成同尺寸 JPEG（≈127KB），视觉一致且首屏更快。
const landingHeroUrl = `${process.env.BASE_URL}landing-hero.jpg`;
const router = useRouter();
const store = useStore();
const revealHost = ref<HTMLElement | null>(null);
let revealObserver: IntersectionObserver | null = null;

const loggedIn = computed(() => {
  const role = store.state.user?.loginUser?.userRole;
  return Boolean(role && role !== ACCESS_ENUM.NOT_LOGIN);
});
const ctaLabel = computed(() => (loggedIn.value ? "进入学习" : "登录 / 注册"));

function onCta() {
  if (loggedIn.value) {
    const role = store.state.user.loginUser?.userRole;
    void router.push(ROLE_HOME[role] || "/graph");
    return;
  }
  void router.push("/user/login");
}

function goWelcome() {
  if (router.currentRoute.value.path !== "/welcome") {
    void router.push("/welcome");
    return;
  }
  const behavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches
    ? "auto"
    : "smooth";
  window.scrollTo({ top: 0, behavior });
}

onMounted(() => {
  const hero = revealHost.value?.querySelector<HTMLElement>(".landing-hero");
  if (hero)
    hero.style.setProperty("--landing-hero-image", `url("${landingHeroUrl}")`);

  const elements =
    revealHost.value?.querySelectorAll<HTMLElement>("[data-reveal]");
  if (!elements?.length) return;

  if (!("IntersectionObserver" in window)) {
    elements.forEach((element) => element.classList.add("is-visible"));
    return;
  }

  revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        entry.target.classList.toggle("is-visible", entry.isIntersecting);
      });
    },
    { threshold: 0.12, rootMargin: "0px 0px -8% 0px" }
  );
  elements.forEach((element) => revealObserver?.observe(element));
});

onBeforeUnmount(() => {
  revealObserver?.disconnect();
  revealObserver = null;
});
</script>

<style scoped>
.landing {
  --landing-max: 1200px;
  min-height: 100vh;
  overflow-x: clip;
  /* 底色来自 body::before / .public-sky 的固定星图层；长页面不重复铺背景 */
  background: transparent;
  color: var(--public-ink);
  scroll-behavior: smooth;
}

.landing-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: min(var(--landing-max), calc(100% - 96px));
  margin: 0 auto;
  padding: 24px 0;
}

.landing-brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: inherit;
  text-decoration: none;
}

.landing-brand-mark {
  display: block;
  width: 31px;
  height: 31px;
  border-radius: 9px;
  /* 深底下给深色 mark 一圈极细亮边，避免图形与星图糊在一起 */
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.18);
}

.landing-brand-name {
  font-family: var(--oslab-font-display);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.landing-nav {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  justify-content: center;
  gap: 24px;
  font-size: 14px;
}

.landing-nav a {
  color: var(--public-ink-soft);
  text-decoration: none;
  transition: color var(--oslab-duration-fast) var(--oslab-ease);
}

.landing-nav a:hover {
  color: var(--public-ink);
}

.landing-brand:focus-visible,
.landing-nav a:focus-visible,
.landing-feature-item:focus-visible,
.landing-footer a:focus-visible {
  outline: 2px solid var(--public-focus);
  outline-offset: 3px;
}

/* 页头右侧：深底幽灵按钮（浅边线 + 浅字），不与 hero 的荧光黄 CTA 抢层级 */
.landing-cta {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 0 18px;
  border: 1px solid rgba(196, 214, 244, 0.34);
  border-radius: var(--oslab-radius-control);
  background: var(--public-cta-bg);
  color: var(--public-ink);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  touch-action: manipulation;
  transition: background-color var(--oslab-duration-fast) var(--oslab-ease),
    border-color var(--oslab-duration-fast) var(--oslab-ease);
}

.landing-cta:hover {
  border-color: rgba(157, 184, 255, 0.62);
  background: rgba(255, 255, 255, 0.1);
}

.landing-cta:active {
  transform: translateY(1px);
}

.landing-cta svg {
  width: 16px;
  height: 16px;
}

.landing-cta:focus-visible {
  outline: 2px solid var(--public-focus);
  outline-offset: 2px;
}

/* 荧光黄 CTA 上亮蓝环对比不足，改用钴蓝 */
.landing-primary:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
}

.landing-body {
  width: min(var(--landing-max), calc(100% - 96px));
  margin: 0 auto;
}

.landing-hero {
  position: relative;
  isolation: isolate;
  display: flex;
  align-items: center;
  width: 100vw;
  min-height: min(720px, calc(100vh - 80px));
  margin-left: calc(50% - 50vw);
  padding: clamp(72px, 10vw, 148px)
    max(48px, calc((100vw - var(--landing-max)) / 2)) clamp(88px, 11vw, 160px);
  background-color: var(--public-bg);
  color: var(--public-ink);
}

/* 右侧光场：保留定制首屏图，压暗后只作为深蓝背景里的一块冷光，
   左缘用蒙版融进夜色，不做玻璃拟态。 */
.landing-hero::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background-image: var(
    --landing-hero-image,
    linear-gradient(135deg, #0d1830, #0a1224 60%, #070d1c)
  );
  background-position: 76% center;
  background-size: cover;
  opacity: 0.42;
  -webkit-mask-image: linear-gradient(
    100deg,
    transparent 12%,
    rgba(0, 0, 0, 0.6) 46%,
    #000 74%
  );
  mask-image: linear-gradient(
    100deg,
    transparent 12%,
    rgba(0, 0, 0, 0.6) 46%,
    #000 74%
  );
}

/* 左侧蓝黑罩层：保证标题/导语/按钮在任何图区上 ≥4.5:1，
   并向星图底色渐隐，避免出现生硬的图片下边界。 */
.landing-hero::after {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background-image: radial-gradient(
      1px 1px at 43px 61px,
      rgba(226, 236, 255, 0.5),
      transparent 100%
    ),
    radial-gradient(
      1.2px 1.2px at 151px 19px,
      rgba(226, 236, 255, 0.38),
      transparent 100%
    ),
    linear-gradient(
      96deg,
      rgba(5, 10, 22, 0.97) 0%,
      rgba(5, 10, 22, 0.94) 30%,
      rgba(6, 12, 26, 0.72) 52%,
      rgba(6, 12, 26, 0.4) 78%,
      rgba(6, 12, 26, 0.24) 100%
    ),
    linear-gradient(
      180deg,
      rgba(7, 13, 28, 0.7) 0%,
      rgba(7, 13, 28, 0) 24%,
      rgba(7, 13, 28, 0) 64%,
      var(--public-bg) 100%
    );
  background-size: 300px 300px, 380px 380px, auto, auto;
  background-position: 0 0, 90px 60px, 0 0, 0 0;
}

.landing-hero-copy {
  position: relative;
  z-index: 2;
  width: min(var(--landing-max), 100%);
  margin: 0 auto;
  animation: landing-hero-rise 900ms var(--oslab-ease) both;
}

.landing-hero h1 {
  max-width: 820px;
  margin: 0;
  color: var(--public-ink);
  font-family: var(--oslab-font-display);
  /* 上限 96px（6rem）：超过后首屏标题会压过学习入口 */
  font-size: clamp(48px, 7.5vw, 96px);
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: 0;
  text-wrap: balance;
}

.landing-hero-lede {
  max-width: 500px;
  margin: 24px 0 0;
  color: var(--public-ink-soft);
  font-size: clamp(17px, 2vw, 22px);
  line-height: 1.65;
}

.landing-hero-actions {
  display: flex;
  justify-content: flex-start;
  gap: 12px;
  margin-top: 36px;
}

/* hero 主 CTA：荧光黄 + 墨色字，是星图上唯一的暖色行动点 */
.landing-primary {
  min-height: 46px;
  padding: 0 22px;
  border: 0;
  border-radius: var(--oslab-radius-control);
  background: var(--oslab-highlight);
  color: var(--oslab-ink);
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  touch-action: manipulation;
  transition: background-color var(--oslab-duration-fast) var(--oslab-ease);
}

.landing-primary:hover {
  background: var(--oslab-highlight-strong);
}

.landing-primary:active {
  transform: translateY(1px);
}

.landing-feature,
.landing-section {
  scroll-margin-top: 24px;
}

.landing-feature {
  display: grid;
  grid-template-columns: minmax(260px, 0.8fr) minmax(0, 1.2fr);
  gap: clamp(36px, 6vw, 88px);
  align-items: center;
  padding: 104px 0 112px;
  border-bottom: 1px solid var(--public-line);
}

.landing-feature-copy {
  max-width: 430px;
}

.landing-feature-copy h2 {
  margin: 0;
  color: var(--public-ink);
  font-family: var(--oslab-font-display);
  font-size: clamp(32px, 4vw, 50px);
  line-height: 1.18;
}

.landing-feature-copy > p {
  margin: 20px 0 0;
  color: var(--public-ink-soft);
  font-size: 16px;
  line-height: 1.8;
}

.landing-feature-list {
  display: grid;
  gap: 10px;
  margin-top: 32px;
}

/* 功能索引是深底上的行，不是白卡：细线 + 透明底，hover 只提亮边线与背景 */
.landing-feature-item {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  min-height: 68px;
  padding: 12px 14px;
  border: 1px solid var(--public-line);
  border-radius: var(--oslab-radius-control);
  color: var(--public-ink);
  text-decoration: none;
  transition: border-color var(--oslab-duration-fast) var(--oslab-ease),
    background-color var(--oslab-duration-fast) var(--oslab-ease),
    transform var(--oslab-duration-fast) var(--oslab-ease);
}

.landing-feature-item:hover,
.landing-feature-item:focus-visible {
  border-color: rgba(242, 214, 75, 0.55);
  background: rgba(242, 214, 75, 0.08);
  transform: translateX(4px);
}

.landing-feature-index {
  color: var(--public-muted);
  font-family: var(--oslab-font-mono);
  font-size: 12px;
}

.landing-feature-item strong,
.landing-feature-item small {
  display: block;
}

.landing-feature-item strong {
  font-size: 15px;
}

.landing-feature-item small {
  margin-top: 4px;
  color: var(--public-muted);
  font-size: 12px;
}

.landing-feature-arrow {
  color: var(--public-accent);
  font-size: 20px;
}

.landing-feature-media {
  min-width: 0;
}

.landing-feature-media :deep(.paging-theater) {
  box-shadow: var(--public-shadow-paper);
  border-color: transparent;
}

.landing-feature-caption {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 12px;
  color: var(--public-muted);
  font-size: 13px;
}

.landing-feature-caption strong {
  color: var(--public-ink-soft);
}

.landing-section {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  gap: 64px;
  align-items: start;
  padding: 104px 0;
  border-bottom: 1px solid var(--public-line);
}

.landing-section.is-reversed .landing-section-copy {
  order: 2;
}

.landing-section.is-reversed > :not(.landing-section-copy) {
  order: 1;
}

.landing-section-copy {
  max-width: 46ch;
}

.landing-section-copy h2 {
  margin: 0;
  color: var(--public-ink);
  font-family: var(--oslab-font-display);
  font-size: clamp(30px, 3vw, 42px);
  line-height: 1.2;
}

.landing-section-copy p {
  margin: 20px 0 0;
  color: var(--public-ink-soft);
  font-size: 16px;
  line-height: 1.8;
}

.landing-reveal {
  opacity: 0;
  transform: translateY(28px);
  transition: opacity 700ms var(--oslab-ease), transform 700ms var(--oslab-ease);
}

.landing-reveal.is-visible {
  opacity: 1;
  transform: translateY(0);
}

.landing-reveal .landing-section-copy > *,
.landing-reveal .landing-feature-copy > *,
.landing-reveal .landing-feature-media > * {
  opacity: 0;
  transform: translateY(16px);
  transition: opacity 650ms var(--oslab-ease), transform 650ms var(--oslab-ease);
}

.landing-reveal.is-visible .landing-section-copy > *,
.landing-reveal.is-visible .landing-feature-copy > *,
.landing-reveal.is-visible .landing-feature-media > * {
  opacity: 1;
  transform: translateY(0);
}

.landing-reveal.is-visible .landing-section-copy > *:nth-child(2),
.landing-reveal.is-visible .landing-feature-copy > *:nth-child(2) {
  transition-delay: 80ms;
}

.landing-reveal.is-visible .landing-section-copy > *:nth-child(3),
.landing-reveal.is-visible .landing-feature-copy > *:nth-child(3) {
  transition-delay: 150ms;
}

.landing-reveal.is-visible .landing-feature-media > *:nth-child(2) {
  transition-delay: 130ms;
}

.landing-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: min(var(--landing-max), calc(100% - 96px));
  margin: 0 auto;
  padding: 28px 0 36px;
  color: var(--public-muted);
  font-size: 13px;
}

.landing-footer nav {
  display: flex;
  gap: 20px;
}

.landing-footer a {
  color: inherit;
  text-decoration: none;
}

.landing-footer a:hover {
  color: var(--public-ink);
}

@keyframes landing-hero-rise {
  from {
    opacity: 0;
    transform: translateY(24px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  .landing-header,
  .landing-body,
  .landing-footer {
    width: calc(100% - 40px);
  }

  .landing-header {
    padding: 16px 0;
  }

  .landing-hero {
    min-height: 620px;
    padding: 72px 20px 88px;
    background-color: var(--public-bg);
  }

  /* 移动端不改动图层，只压暗：图片收成顶部一块冷光，文字区统一深蓝 */
  .landing-hero::before {
    opacity: 0.55;
    background-position: 66% 12%;
    -webkit-mask-image: linear-gradient(
      180deg,
      rgba(0, 0, 0, 0.85) 0%,
      rgba(0, 0, 0, 0.45) 46%,
      transparent 86%
    );
    mask-image: linear-gradient(
      180deg,
      rgba(0, 0, 0, 0.85) 0%,
      rgba(0, 0, 0, 0.45) 46%,
      transparent 86%
    );
  }

  .landing-hero::after {
    background-image: linear-gradient(
      180deg,
      rgba(6, 11, 24, 0.6) 0%,
      rgba(6, 11, 24, 0.88) 44%,
      var(--public-bg) 100%
    );
    background-size: auto;
    background-position: 0 0;
  }

  .landing-hero-copy {
    width: 100%;
  }

  .landing-hero h1 {
    max-width: 520px;
  }

  .landing-hero-actions {
    flex-direction: column;
    align-items: stretch;
    max-width: 280px;
  }

  .landing-primary {
    justify-content: center;
  }

  .landing-feature,
  .landing-section {
    grid-template-columns: 1fr;
    gap: 32px;
    padding: 72px 0;
  }

  .landing-feature-copy {
    max-width: none;
  }

  .landing-feature-caption {
    flex-direction: column;
    gap: 4px;
  }

  .landing-section.is-reversed .landing-section-copy,
  .landing-section.is-reversed > :not(.landing-section-copy) {
    order: initial;
  }

  .landing-footer {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 900px) {
  .landing-nav {
    display: none;
  }
}

/* 触屏：页头/页脚链接与主按钮统一 44px 触控目标 */
@media (pointer: coarse) {
  .landing-cta,
  .landing-nav a,
  .landing-footer a {
    display: inline-flex;
    align-items: center;
    min-height: 44px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .landing,
  .landing-reveal,
  .landing-reveal .landing-section-copy > *,
  .landing-reveal .landing-feature-copy > *,
  .landing-reveal .landing-feature-media > *,
  .landing-hero-copy,
  .landing-feature-item,
  .landing-cta,
  .landing-primary {
    animation: none !important;
    transition: none !important;
  }

  .landing {
    scroll-behavior: auto;
  }

  .landing-reveal {
    opacity: 1;
    transform: none;
  }

  .landing-feature-item:hover,
  .landing-feature-item:focus-visible {
    transform: none;
  }

  .landing-reveal .landing-section-copy > *,
  .landing-reveal .landing-feature-copy > *,
  .landing-reveal .landing-feature-media > * {
    opacity: 1;
    transform: none;
  }
}
</style>
