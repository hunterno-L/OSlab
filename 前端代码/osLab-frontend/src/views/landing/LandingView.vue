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
            <a class="landing-feature-item is-active" href="#mainline">
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
const landingHeroUrl = `${process.env.BASE_URL}landing-hero.png`;
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
  background: var(--oslab-canvas);
  color: var(--oslab-ink);
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
  color: var(--oslab-ink-soft);
  text-decoration: none;
  transition: color var(--oslab-duration-fast) var(--oslab-ease);
}

.landing-nav a:hover {
  color: var(--oslab-primary);
}

.landing-nav a:focus-visible,
.landing-feature-item:focus-visible,
.landing-footer a:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 3px;
}

.landing-cta {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 0 18px;
  border: 1px solid var(--oslab-ink);
  border-radius: var(--oslab-radius-control);
  background: var(--oslab-ink);
  color: var(--oslab-on-primary);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}

.landing-cta svg {
  width: 16px;
  height: 16px;
}

.landing-cta:focus-visible,
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
  display: flex;
  align-items: center;
  width: 100vw;
  min-height: min(720px, calc(100vh - 80px));
  margin-left: calc(50% - 50vw);
  padding: clamp(72px, 10vw, 148px)
    max(48px, calc((100vw - var(--landing-max)) / 2)) clamp(88px, 11vw, 160px);
  background-color: #eef0e9;
  background-image: var(
    --landing-hero-image,
    linear-gradient(135deg, #eef0e9, #dce5eb)
  );
  background-position: center;
  background-size: cover;
  color: #20242b;
}

.landing-hero-copy {
  width: min(var(--landing-max), 100%);
  margin: 0 auto;
  animation: landing-hero-rise 900ms var(--oslab-ease) both;
}

.landing-hero h1 {
  max-width: 820px;
  margin: 0;
  font-family: var(--oslab-font-display);
  font-size: clamp(48px, 7.5vw, 112px);
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: 0;
  text-wrap: balance;
}

.landing-hero-lede {
  max-width: 500px;
  margin: 24px 0 0;
  color: rgba(32, 36, 43, 0.74);
  font-size: clamp(17px, 2vw, 22px);
  line-height: 1.65;
}

.landing-hero-actions {
  display: flex;
  justify-content: flex-start;
  gap: 12px;
  margin-top: 36px;
}

.landing-primary {
  min-height: 46px;
  padding: 0 22px;
  border: 0;
  border-radius: var(--oslab-radius-control);
  background: #f2d64b;
  color: #17212a;
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
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
  border-bottom: 1px solid var(--oslab-line);
}

.landing-feature-copy {
  max-width: 430px;
}

.landing-feature-copy h2 {
  margin: 0;
  color: var(--oslab-ink);
  font-family: var(--oslab-font-display);
  font-size: clamp(32px, 4vw, 50px);
  line-height: 1.18;
}

.landing-feature-copy > p {
  margin: 20px 0 0;
  color: var(--oslab-ink-soft);
  font-size: 16px;
  line-height: 1.8;
}

.landing-feature-list {
  display: grid;
  gap: 10px;
  margin-top: 32px;
}

.landing-feature-item {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  min-height: 68px;
  padding: 12px 14px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-control);
  color: var(--oslab-ink);
  text-decoration: none;
  transition: border-color var(--oslab-duration-fast) var(--oslab-ease),
    background-color var(--oslab-duration-fast) var(--oslab-ease),
    transform var(--oslab-duration-fast) var(--oslab-ease);
}

.landing-feature-item:hover,
.landing-feature-item.is-active {
  border-color: var(--oslab-primary);
  background: var(--oslab-highlight-soft);
  transform: translateX(4px);
}

.landing-feature-index {
  color: var(--oslab-muted-strong);
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
  color: var(--oslab-muted-strong);
  font-size: 12px;
}

.landing-feature-arrow {
  color: var(--oslab-primary);
  font-size: 20px;
}

.landing-feature-media {
  min-width: 0;
}

.landing-feature-media :deep(.paging-theater) {
  box-shadow: var(--oslab-shadow-card);
}

.landing-feature-caption {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 12px;
  color: var(--oslab-muted-strong);
  font-size: 13px;
}

.landing-feature-caption strong {
  color: var(--oslab-ink);
}

.landing-section {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  gap: 64px;
  align-items: start;
  padding: 104px 0;
  border-bottom: 1px solid var(--oslab-line);
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
  color: var(--oslab-ink);
  font-family: var(--oslab-font-display);
  font-size: clamp(30px, 3vw, 42px);
  line-height: 1.2;
}

.landing-section-copy p {
  margin: 20px 0 0;
  color: var(--oslab-ink-soft);
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
  color: var(--oslab-muted-strong);
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
  color: var(--oslab-primary);
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
    background-position: left center;
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

@media (prefers-reduced-motion: reduce) {
  .landing,
  .landing-reveal,
  .landing-reveal .landing-section-copy > *,
  .landing-reveal .landing-feature-copy > *,
  .landing-reveal .landing-feature-media > *,
  .landing-hero-copy,
  .landing-feature-item {
    animation: none !important;
    transition: none !important;
  }

  .landing-reveal {
    opacity: 1;
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
