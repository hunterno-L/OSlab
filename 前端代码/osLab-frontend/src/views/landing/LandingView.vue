<template>
  <div class="landing">
    <header class="landing-header">
      <a class="landing-brand" href="/welcome" @click.prevent="goWelcome">
        <img
          src="@/assets/oj-logo.svg"
          alt=""
          width="31"
          height="31"
          class="landing-brand-mark"
        />
        <span class="landing-brand-name">OSLab</span>
      </a>
      <nav class="landing-nav" aria-label="平台介绍">
        <a href="#mainline">学习主线</a>
        <a href="#graph">知识图谱</a>
        <a href="#practice">三种练习</a>
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
          <h1 id="landing-hero-title">沿着教材，把操作系统学明白</h1>
          <p>
            OSLab
            用教材式学习单元组织五个章节，用知识图谱和学习画像记录你真正掌握了什么。
          </p>
          <div class="landing-hero-actions">
            <button
              type="button"
              class="landing-primary"
              data-auth-trigger
              @click="onCta"
            >
              {{ loggedIn ? "进入学习" : "登录开始学习" }}
            </button>
            <a class="landing-secondary" href="#mainline">查看学习方式</a>
          </div>
        </div>
        <PagingTheater
          class="landing-hero-theater"
          mode="ambient"
          label="页面置换 · LRU"
        />
      </section>

      <section id="mainline" class="landing-section">
        <div class="landing-section-copy">
          <h2>不是一张知识列表，而是一条学习主线</h2>
          <p>
            五个章节按教材顺序编排，每章拆成有目标的学习单元。第一次学习从首个已发布单元开始；回来时接着上次的位置；遇到薄弱点，系统插入补充单元，完成后回到主线。
          </p>
        </div>
        <MainlineDemo />
      </section>

      <section id="graph" class="landing-section is-reversed">
        <div class="landing-section-copy">
          <h2>同一张图谱，看见自己的掌握</h2>
          <p>
            公共图谱保存概念、算法、机制与实现及其关系；每个学生在此基础上叠加概念与实践证据、最近变化和下一步建议。没有证据的节点显示为未学习，不会被误判为薄弱。
          </p>
        </div>
        <GraphProfileDemo />
      </section>

      <section id="practice" class="landing-section">
        <div class="landing-section-copy">
          <h2>理解之后，随时练</h2>
          <p>
            题目练习、算法模拟和编程实验都能独立进入，也能被学习单元引用。演示覆盖
            CPU 调度、内存分配、页面置换和磁盘调度。
          </p>
        </div>
        <PracticeTabsDemo />
      </section>

      <section id="evidence" class="landing-section">
        <div class="landing-section-copy">
          <h2>进度不是打勾，是证据</h2>
          <p>
            活动进度、单元进度、达标情况和节点掌握分开记录；一次真实作答只计一次证据，历史变化可以回放。
          </p>
        </div>
        <EvidenceLedger />
      </section>
    </div>

    <footer class="landing-footer">
      <span>OSLab 操作系统学习平台</span>
      <nav aria-label="页脚">
        <a href="/welcome" @click.prevent="goWelcome">平台简介</a>
        <a :href="loggedIn ? '/graph' : '/user/login'">课程入口</a>
        <a :href="loggedIn ? '/resources' : '/user/login'">学习资源</a>
        <a href="/user/login">登录</a>
      </nav>
      <span>© 2026 OSLab</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
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

const router = useRouter();
const store = useStore();

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
</script>

<style scoped>
.landing {
  --landing-max: 1200px;
  min-height: 100vh;
  background: var(--oslab-canvas);
  color: var(--oslab-ink);
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
}

.landing-nav a:hover {
  color: var(--oslab-primary);
}

.landing-nav a:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
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
.landing-primary:focus-visible,
.landing-secondary:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
}

.landing-body {
  width: min(var(--landing-max), calc(100% - 96px));
  margin: 0 auto;
}

.landing-hero {
  padding: 72px 0 96px;
}

.landing-hero-copy {
  max-width: 780px;
  margin: 0 auto;
  text-align: center;
}

.landing-hero h1 {
  margin: 0;
  font-family: var(--oslab-font-display);
  font-size: clamp(44px, 5.2vw, 72px);
  font-weight: 700;
  line-height: 1.08;
  background: linear-gradient(
    transparent 62%,
    var(--oslab-highlight) 62%,
    var(--oslab-highlight) 94%,
    transparent 94%
  );
}

.landing-hero p {
  max-width: 640px;
  margin: 24px auto 0;
  color: var(--oslab-ink-soft);
  font-size: 16px;
  line-height: 1.8;
}

.landing-hero-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 32px;
}

.landing-primary {
  min-height: 44px;
  padding: 0 22px;
  border: 0;
  border-radius: var(--oslab-radius-control);
  background: var(--oslab-primary);
  color: var(--oslab-on-primary);
  font: inherit;
  font-size: 15px;
  cursor: pointer;
}

.landing-secondary {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 0 18px;
  border: 1px solid var(--oslab-line-strong);
  border-radius: var(--oslab-radius-control);
  color: var(--oslab-ink);
  font-size: 15px;
  text-decoration: none;
}

.landing-hero-theater {
  margin-top: 56px;
}

.landing-section {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  gap: 64px;
  align-items: start;
  padding: 96px 0;
  border-top: 1px solid var(--oslab-line);
  scroll-margin-top: 24px;
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
  font-size: clamp(30px, 3vw, 40px);
  line-height: 1.2;
}

.landing-section-copy p {
  margin: 20px 0 0;
  color: var(--oslab-ink-soft);
  font-size: 16px;
  line-height: 1.8;
}

.landing-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: min(var(--landing-max), calc(100% - 96px));
  margin: 0 auto;
  padding: 28px 0 36px;
  border-top: 1px solid var(--oslab-line);
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

@media (max-width: 768px) {
  .landing-header,
  .landing-body,
  .landing-footer {
    width: calc(100% - 40px);
  }

  .landing-hero {
    padding: 40px 0 64px;
  }

  .landing-hero-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .landing-primary,
  .landing-secondary {
    justify-content: center;
  }

  .landing-hero-theater {
    margin-top: 32px;
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

  .landing-section {
    grid-template-columns: 1fr;
    gap: 32px;
    padding: 64px 0;
  }

  .landing-section.is-reversed .landing-section-copy,
  .landing-section.is-reversed > :not(.landing-section-copy) {
    order: initial;
  }
}
</style>
