const { defineConfig } = require("@vue/cli-service");
const MonacoWebpackPlugin = require("monaco-editor-webpack-plugin");

const ignoredRuntimeErrorMessages = [
  "ResizeObserver loop completed with undelivered notifications.",
  "ResizeObserver loop limit exceeded",
];

const shouldDisplayRuntimeError = (error) => {
  const message = error?.message || error?.reason?.message || "";
  return !ignoredRuntimeErrorMessages.some((item) => message.includes(item));
};

module.exports = defineConfig({
  transpileDependencies: true,
  devServer: {
    proxy: {
      "/api": {
        target: process.env.VUE_APP_API_TARGET || "http://localhost:8121",
        changeOrigin: true,
      },
    },
    client: {
      overlay: {
        runtimeErrors: shouldDisplayRuntimeError,
      },
    },
  },
  chainWebpack(config) {
    config.plugin("monaco").use(new MonacoWebpackPlugin());
  },
});
