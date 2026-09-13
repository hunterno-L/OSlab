import axios from "axios";
import Message from "@arco-design/web-vue/es/message";

axios.defaults.withCredentials = true;

// 未登录错误码（与后端 ErrorCode.NOT_LOGIN_ERROR 保持一致）
const NOT_LOGIN_CODE = 40100;

// Add a request interceptor
axios.interceptors.request.use(
  function (config) {
    // Do something before request is sent
    return config;
  },
  function (error) {
    // Do something with request error
    return Promise.reject(error);
  }
);

// Add a response interceptor
axios.interceptors.response.use(
  function (response) {
    const res = response.data;
    // 后端统一返回 BaseResponse 结构时按业务码处理。
    // 注意：业务错误不 reject，而是把响应原样返回——
    // generated/core/request.ts 与各 store 依赖响应体自行判断 code，
    // 拦截器 reject 会中断路由守卫/登录态初始化流程。
    if (res && typeof res === "object" && "code" in res) {
      if (res.code !== 0) {
        if (res.code === NOT_LOGIN_CODE) {
          // 未登录：提示并跳转登录页（保留回跳地址；登录/注册页自身不跳转，避免死循环）
          const path = window.location.pathname;
          if (!path.startsWith("/user/login") && !path.startsWith("/user/register")) {
            Message.warning("请先登录");
            const redirect = encodeURIComponent(
              window.location.pathname + window.location.search
            );
            window.location.href = `/user/login?redirect=${redirect}`;
          }
        } else {
          Message.error(res.message || "请求失败");
        }
      }
    }
    return response;
  },
  function (error) {
    if (error.response) {
      const status = error.response.status;
      if (status === 401) {
        // HTTP 401：会话过期或未认证
        const path = window.location.pathname;
        if (!path.startsWith("/user/login") && !path.startsWith("/user/register")) {
          Message.warning("登录已过期，请重新登录");
          window.location.href = "/user/login";
        }
      } else {
        Message.error(`请求失败（${status}）`);
      }
    } else {
      Message.error("网络异常，请稍后重试");
    }
    return Promise.reject(error);
  }
);
