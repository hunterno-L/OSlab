// Run with: node tests/user-identity.js
// 阶段 1 身份回归：昵称展示规则全分支、头像失败键、store 身份清空与迟到响应守卫。
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");
const ts = require("typescript");

const transpile = (relativePath) =>
  ts.transpileModule(
    fs.readFileSync(path.join(__dirname, relativePath), "utf8"),
    {
      compilerOptions: {
        target: ts.ScriptTarget.ES2020,
        module: ts.ModuleKind.CommonJS,
      },
    }
  ).outputText;

const loadModule = (compiled, requireShim) => {
  const module = { exports: {} };
  new Function("require", "module", "exports", compiled)(
    requireShim,
    module,
    module.exports
  );
  return module.exports;
};

// ---- 真实 accessEnum（保证 store 内角色值与生产一致）----
const accessEnumModule = loadModule(
  transpile("../src/access/accessEnum.ts"),
  () => {
    throw new Error("accessEnum.ts must not import runtime dependencies");
  }
);
const accessEnum = accessEnumModule.default;
assert.equal(accessEnum.NOT_LOGIN, "notLogin");

// ---- userPresentation：纯函数模块，不允许出现任何 require ----
const presentation = loadModule(
  transpile("../src/utils/userPresentation.ts"),
  (name) => {
    throw new Error(`userPresentation.ts must not import ${name}`);
  }
);

// ---- localStorage 桩（store 清空时需要移除用户隔离 ID）----
const localStorageData = new Map();
global.localStorage = {
  setItem: (key, value) => localStorageData.set(key, String(value)),
  getItem: (key) =>
    localStorageData.has(key) ? localStorageData.get(key) : null,
  removeItem: (key) => localStorageData.delete(key),
};

// ---- store：注入假的 generated 用户服务 ----
let identityResponses = [];
let identityRequests = 0;
const storeModule = loadModule(transpile("../src/store/user.ts"), (name) => {
  if (name === "@/access/accessEnum") {
    // store 是默认导入（编译后读 .default，是否走 __importDefault 取决于
    // interop 选项），这里同时给出 .default 与具名属性，两种形态都兼容。
    return { ...accessEnum, default: accessEnum };
  }
  if (name === "../../generated") {
    return {
      UserControllerService: {
        getLoginUserUsingGet: () => {
          identityRequests += 1;
          const respond = identityResponses.shift();
          if (!respond) {
            throw new Error("no scripted identity response");
          }
          return respond();
        },
      },
    };
  }
  throw new Error(`store/user.ts must not import ${name}`);
});

const storeDefinition = storeModule.default;
const buildState = () => storeDefinition.state();
const buildContext = (state) => ({
  state,
  commit: (type, payload) => storeDefinition.mutations[type](state, payload),
});

async function testPresentation() {
  const { resolveDisplayName, resolveAvatarFallbackChar } = presentation;

  // 昵称有值：去前后空白后显示真实昵称
  assert.equal(resolveDisplayName("  张三  "), "张三");
  assert.equal(resolveDisplayName("Alice"), "Alice");
  // 昵称空 / null / 纯空白：统一显示“用户”，不出现“未登录”
  assert.equal(resolveDisplayName(""), "用户");
  assert.equal(resolveDisplayName("   "), "用户");
  assert.equal(resolveDisplayName(null), "用户");
  assert.equal(resolveDisplayName(undefined), "用户");

  // 头像回退：昵称首个字素
  assert.equal(resolveAvatarFallbackChar("张三"), "张");
  assert.equal(resolveAvatarFallbackChar("  Bob "), "B");
  // 首字符是 emoji：不能显示半个字符
  assert.equal(resolveAvatarFallbackChar("😀小明"), "😀");
  // 昵称为空：回退“用”
  assert.equal(resolveAvatarFallbackChar(""), "用");
  assert.equal(resolveAvatarFallbackChar(null), "用");
  assert.equal(resolveAvatarFallbackChar("   "), "用");

  // ZWJ 组合 emoji：有 Intl.Segmenter 时按字素切分
  if (typeof Intl.Segmenter === "function") {
    assert.equal(resolveAvatarFallbackChar("🧑‍🚀领航员"), "🧑‍🚀");
  } else {
    assert.equal(resolveAvatarFallbackChar("🧑‍🚀领航员"), "🧑");
  }

  // 长昵称在规则内保持原值（省略交给样式层处理）
  const longName = "A".repeat(300);
  assert.equal(resolveDisplayName(longName), longName);
}

async function testAvatarFailureKey() {
  const {
    buildAvatarFailureKey,
    isAvatarFailureMarked,
    markAvatarFailed,
    markAvatarLoaded,
  } = presentation;

  const keyA1 = buildAvatarFailureKey(7, "https://cdn/a.png");
  assert.equal(keyA1, "7::https://cdn/a.png");
  assert.equal(
    buildAvatarFailureKey("7", "https://cdn/a.png"),
    keyA1,
    "string and numeric user id share the key"
  );

  markAvatarFailed(keyA1);
  assert.equal(isAvatarFailureMarked(keyA1), true);
  // 换账号或换地址都会改变键：A 的坏图片不影响 B 的正常头像
  assert.equal(
    isAvatarFailureMarked(buildAvatarFailureKey(8, "https://cdn/a.png")),
    false
  );
  assert.equal(
    isAvatarFailureMarked(buildAvatarFailureKey(7, "https://cdn/b.png")),
    false
  );
  // 地址修复后加载成功清除失败标记
  markAvatarLoaded(keyA1);
  assert.equal(isAvatarFailureMarked(keyA1), false);
}

async function testStoreIdentityLifecycle() {
  const { createEmptyLoginUser } = storeModule;

  // 初始身份是“未知”：userRole 为空，路由守卫会先请求服务端确认
  const state = buildState();
  assert.deepEqual(state.loginUser, createEmptyLoginUser());
  assert.equal(state.loginUser.userRole, "");

  const context = buildContext(state);

  // 1. 成功路径：整体替换为服务端返回的完整身份
  identityResponses.push(() => ({
    code: 0,
    data: {
      id: 7,
      userName: "张三",
      userAvatar: "https://cdn/a.png",
      userProfile: "你好",
      userRole: "user",
    },
  }));
  await storeDefinition.actions.getLoginUser(context);
  assert.equal(state.loginUser.id, 7);
  assert.equal(state.loginUser.userName, "张三");
  assert.equal(state.loginUser.userAvatar, "https://cdn/a.png");
  assert.equal(state.loginUser.userProfile, "你好");
  assert.equal(state.loginUser.userRole, "user");

  // 2. 服务端明确未登录（40100）：完整清空旧身份，不残留任何旧字段
  localStorage.setItem("userId", "7");
  identityResponses.push(() => ({ code: 40100, data: null }));
  await storeDefinition.actions.getLoginUser(context);
  assert.deepEqual(state.loginUser, createEmptyLoginUser(accessEnum.NOT_LOGIN));
  assert.equal(state.loginUser.userName, "");
  assert.equal(state.loginUser.userAvatar, "");
  assert.equal(state.loginUser.userProfile, "");
  assert.equal(state.loginUser.userRole, accessEnum.NOT_LOGIN);
  assert.equal(
    localStorage.getItem("userId"),
    null,
    "isolated user id cleared"
  );

  // 3. 临时网络失败：保留现有身份，不当作未登录
  storeDefinition.mutations.updateUser(state, {
    id: 42,
    userName: "账号甲",
    userAvatar: "https://cdn/x.png",
    userProfile: "",
    userRole: "user",
  });
  identityResponses.push(() => Promise.reject(new Error("offline")));
  await storeDefinition.actions.getLoginUser(context);
  assert.equal(state.loginUser.id, 42);
  assert.equal(state.loginUser.userName, "账号甲");
  assert.equal(state.loginUser.userRole, "user");

  // 4. 其他业务错误（如系统异常）：同样保留身份等待重试
  identityResponses.push(() => ({ code: 50000, message: "系统异常" }));
  await storeDefinition.actions.getLoginUser(context);
  assert.equal(state.loginUser.id, 42);

  // 5. 迟到响应守卫：旧请求不能覆盖新登录账号
  identityResponses.push(
    () =>
      new Promise((resolve) =>
        setTimeout(
          () =>
            resolve({
              code: 0,
              data: {
                id: 1,
                userName: "迟到的账号",
                userRole: "admin",
              },
            }),
          5
        )
      ),
    () => ({
      code: 0,
      data: { id: 2, userName: "新登录账号", userRole: "user" },
    })
  );
  const staleRequest = storeDefinition.actions.getLoginUser(context);
  const freshRequest = storeDefinition.actions.getLoginUser(context);
  await Promise.all([staleRequest, freshRequest]);
  assert.equal(state.loginUser.id, 2, "stale identity response is dropped");
  assert.equal(state.loginUser.userName, "新登录账号");

  // 6. 新响应先落地、旧响应后到：同样不得回滚
  identityResponses.push(
    () =>
      new Promise((resolve) => setTimeout(() => resolve({ code: 40100 }), 5)),
    () => ({
      code: 0,
      data: { id: 3, userName: "最新账号", userRole: "user" },
    })
  );
  const notLoginRequest = storeDefinition.actions.getLoginUser(context);
  const loginRequest = storeDefinition.actions.getLoginUser(context);
  await Promise.all([notLoginRequest, loginRequest]);
  assert.equal(state.loginUser.id, 3);
  assert.equal(state.loginUser.userName, "最新账号");
  assert.equal(
    identityRequests,
    8,
    "every scenario issues exactly one request"
  );
}

async function run() {
  await testPresentation();
  await testAvatarFailureKey();
  await testStoreIdentityLifecycle();
  console.log(
    "User identity: display-name fallbacks, avatar failure keys, full identity reset, transient-failure tolerance and stale-response guards passed."
  );
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
