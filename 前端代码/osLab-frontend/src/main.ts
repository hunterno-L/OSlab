import { createApp } from "vue";
import App from "./App.vue";
import ArcoVue from "@arco-design/web-vue";
import "@arco-design/web-vue/dist/arco.css";
import router from "./router";
import store from "./store";
import "@/plugins/axios";
import "@/access";
import "bytemd/dist/index.css";
import ArcoVueIcon from "@arco-design/web-vue/es/icon";
import { OpenAPI } from "../generated";

// Generated endpoints already start with "/api". A relative base keeps every
// browser request on the current origin; the development server proxies /api.
OpenAPI.BASE = "";

createApp(App)
  .use(ArcoVue)
  .use(ArcoVueIcon)
  .use(store)
  .use(router)
  .mount("#app");
