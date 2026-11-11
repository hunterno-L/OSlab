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
  // 生产包不生成 source map：在 2GB Node 堆限制下显著降低打包峰值内存。
  productionSourceMap: false,
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
    // CodeEditor 只使用 c/cpp（同一语法贡献）、java、go；默认配置会打包全部
    // 语言及其 ts/json/css/html worker，显著放大产物（ts.worker 单文件 5.8MB）。
    config.plugin("monaco").use(
      new MonacoWebpackPlugin({
        languages: ["cpp", "java", "go"],
      })
    );
  },
});
