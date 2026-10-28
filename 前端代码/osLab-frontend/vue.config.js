const { defineConfig } = require("@vue/cli-service");
const MonacoWebpackPlugin = require("monaco-editor-webpack-plugin");

// webpack-dev-server 会把该函数 toString 后在浏览器端执行，
// 不能引用外部闭包变量（否则浏览器端 ReferenceError），忽略列表必须内联。
const shouldDisplayRuntimeError = (error) => {
  const ignoredRuntimeErrorMessages = [
    "ResizeObserver loop completed with undelivered notifications.",
    "ResizeObserver loop limit exceeded",
  ];
  const message = error?.message || error?.reason?.message || "";
  return !ignoredRuntimeErrorMessages.some((item) => message.includes(item));
};

module.exports = defineConfig({
  transpileDependencies: true,
  devServer: {
    setupMiddlewares(middlewares) {
      // Rebuilt original demos must be served from disk, ahead of webpack's
      // in-memory public copy, which can retain the previous hashed entry.
      middlewares.unshift({
        name: "legacy-simulation-assets",
        path: "/legacy-simulation",
        middleware: require("express").static(
          require("path").join(__dirname, "public", "legacy-simulation"),
          { etag: false, maxAge: 0 }
        ),
      });
      return middlewares;
    },
    proxy: {
      "/api": {
        target: process.env.VUE_APP_API_TARGET || "http://localhost:8121",
        changeOrigin: true,
      },
    },
    client: {
      overlay: {
        // eslint 警告不弹全屏遮罩，只对真实错误弹窗
        warnings: false,
        errors: true,
        runtimeErrors: shouldDisplayRuntimeError,
      },
    },
  },
  chainWebpack(config) {
    config.plugin("monaco").use(new MonacoWebpackPlugin());
  },
});
