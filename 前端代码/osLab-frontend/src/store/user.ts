// initial state
import { StoreOptions } from "vuex";
import ACCESS_ENUM from "@/access/accessEnum";
import { UserControllerService } from "../../generated";

/** 未登录业务码，与后端 ErrorCode.NOT_LOGIN_ERROR、plugins/axios.ts 一致。 */
const NOT_LOGIN_CODE = 40100;

/** 登录成功后写入的用户隔离 ID 存储键（UserLoginView 等处使用）。 */
const CURRENT_USER_ID_KEY = "userId";

/**
 * 构造完整空身份。
 * userRole 默认为空串表示“身份未知”：路由守卫看到空 userRole 会先
 * dispatch getLoginUser 向服务端确认，而不是直接判为未登录。
 * 传入 ACCESS_ENUM.NOT_LOGIN 则表示服务端明确的未登录态。
 */
export function createEmptyLoginUser(userRole = "") {
  return {
    id: undefined,
    userName: "",
    userAvatar: "",
    userProfile: "",
    userRole,
  };
}

/** 清理用户隔离用的当前 ID（隐私模式下 localStorage 可能不可用）。 */
function clearCurrentUserId() {
  try {
    if (typeof localStorage !== "undefined") {
      localStorage.removeItem(CURRENT_USER_ID_KEY);
    }
  } catch {
    // 无法访问存储时忽略，不影响身份重置
  }
}

// 请求序号：只有最新一次身份请求允许落地，旧响应不得覆盖新登录账号。
let latestIdentityRequestSeq = 0;

export default {
  namespaced: true,
  state: () => ({
    loginUser: createEmptyLoginUser(),
  }),
  actions: {
    async getLoginUser({ commit }) {
      const requestSeq = ++latestIdentityRequestSeq;
      let res;
      try {
        res = await UserControllerService.getLoginUserUsingGet();
      } catch {
        // 网络异常 / 服务不可达属于临时失败：保留现有身份等待重试，
        // 不把“没取到身份”当成“未登录”。
        return;
      }
      if (requestSeq !== latestIdentityRequestSeq) {
        // 迟到的旧响应：直接丢弃，防止覆盖后续登录或刷新结果。
        return;
      }
      if (res.code === 0 && res.data) {
        // 整体替换为服务端返回的完整身份，禁止在旧对象上展开覆盖。
        commit("updateUser", {
          id: res.data.id,
          userName: res.data.userName,
          userAvatar: res.data.userAvatar,
          userProfile: res.data.userProfile,
          userRole: res.data.userRole || ACCESS_ENUM.NOT_LOGIN,
        });
        return;
      }
      if (res.code === NOT_LOGIN_CODE) {
        // 服务端明确未登录 / 会话失效：完整清空旧身份并清理隔离 ID。
        commit("resetUser");
      }
      // 其余业务错误（如系统异常）：视为临时失败，保留现有身份。
    },
  },
  mutations: {
    /** 写入登录身份：统一裁剪为固定字段，避免旧字段残留。 */
    updateUser(state, payload) {
      state.loginUser = {
        id: payload?.id,
        userName: payload?.userName,
        userAvatar: payload?.userAvatar,
        userProfile: payload?.userProfile,
        userRole: payload?.userRole || ACCESS_ENUM.NOT_LOGIN,
      };
    },
    /** 登出成功与会话失效共用：完整重置身份并清理用户隔离 ID。 */
    resetUser(state) {
      state.loginUser = createEmptyLoginUser(ACCESS_ENUM.NOT_LOGIN);
      clearCurrentUserId();
    },
  },
} as StoreOptions<any>;
