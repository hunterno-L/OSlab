import { createApp } from "vue";
import App from "./App.vue";
import ArcoVue from "@arco-design/web-vue";
import "@arco-design/web-vue/dist/arco.css";
import router from "./router";
import store from "./store";
import "@/plugins/axios";
import "@/styles/learning-theme.css";
import "@/access";
import "bytemd/dist/index.css";
// 仅注册项目实际使用的 18 个图标：全量 ArcoVueIcon 会把数百个图标打进首屏 vendor。
import {
  IconArrowLeft,
  IconArrowRight,
  IconBook,
  IconCopy,
  IconDown,
  IconExport,
  IconFile,
  IconHistory,
  IconLaunch,
  IconPlus,
  IconQuestionCircle,
  IconRefresh,
  IconRobot,
  IconSearch,
  IconSend,
  IconSettings,
  IconUser,
  IconUserGroup,
} from "@arco-design/web-vue/es/icon";
import { OpenAPI } from "../generated";

// Generated endpoints already start with "/api". A relative base keeps every
// browser request on the current origin; the development server proxies /api.
OpenAPI.BASE = "";

const app = createApp(App);

// 全局错误处理：渲染期异常不再静默杀死渲染线程，统一暴露到 window.__verrs
// 便于诊断（Phase 2 计划 §13.4：统一的错误与重试状态）。
app.config.errorHandler = (err, _instance, info) => {
  const w = window as any;
  w.__verrs = w.__verrs || [];
  w.__verrs.push({
    msg: err instanceof Error ? `${err.message}\n${err.stack}` : String(err),
    info,
  });
  console.error("[vue]", info, err);
};

// 与 ArcoVueIcon 的注册名保持一致（PascalCase），模板里的 <icon-xxx> 仍可解析。
const arcoIcons = {
  IconArrowLeft,
  IconArrowRight,
  IconBook,
  IconCopy,
  IconDown,
  IconExport,
  IconFile,
  IconHistory,
  IconLaunch,
  IconPlus,
  IconQuestionCircle,
  IconRefresh,
  IconRobot,
  IconSearch,
  IconSend,
  IconSettings,
  IconUser,
  IconUserGroup,
};
Object.entries(arcoIcons).forEach(([name, icon]) => app.component(name, icon));

app.use(ArcoVue).use(store).use(router).mount("#app");
