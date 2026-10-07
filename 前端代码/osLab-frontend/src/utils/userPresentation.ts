/**
 * 用户身份展示规则（阶段 1：头像、昵称与身份显示统一）。
 *
 * 顶栏（GlobalHeader）、个人信息页（StudentProfileView）、用户管理摘要
 * （UserCenterView）三处的昵称与头像回退都必须经过这里，禁止各自实现。
 *
 * 本模块只包含纯函数与展示态注册表，不依赖 DOM 与组件库，
 * 便于 tests/user-identity.js 在 Node 裸测中直接验证。
 */

/** 已登录但昵称为空 / null / 纯空白时的统一显示名。 */
export const DEFAULT_DISPLAY_NAME = "用户";

/** 没有任何昵称时头像回退字符（“用户”的首字）。 */
export const DEFAULT_AVATAR_CHAR = "用";

/**
 * 统一显示名：去掉前后空白后有值显示原值；
 * 空白、null、undefined 一律回退“用户”，不显示“未登录”。
 */
export function resolveDisplayName(userName?: string | null): string {
  if (typeof userName !== "string") {
    return DEFAULT_DISPLAY_NAME;
  }
  const trimmed = userName.trim();
  return trimmed.length > 0 ? trimmed : DEFAULT_DISPLAY_NAME;
}

/** 按 Unicode 码点取首个字符（Segmenter 不可用时的兜底）。 */
function takeFirstCodePoint(value: string): string {
  return Array.from(value)[0] || "";
}

/**
 * 取字符串首个字素（grapheme）。
 * 优先使用 Intl.Segmenter，保证 emoji（含 ZWJ 组合、修饰符）不会显示半个字符。
 */
function takeFirstGrapheme(value: string): string {
  if (!value) {
    return "";
  }
  const segmenterCtor = (
    Intl as unknown as {
      Segmenter?: new (options?: {
        granularity?: "grapheme" | "word" | "sentence";
      }) => { segment: (input: string) => Iterable<{ segment: string }> };
    }
  ).Segmenter;
  if (typeof segmenterCtor === "function") {
    try {
      const segments = Array.from(
        new segmenterCtor({ granularity: "grapheme" }).segment(value)
      );
      if (segments.length > 0 && segments[0].segment) {
        return segments[0].segment;
      }
    } catch {
      // 个别引擎实现异常时退回码点截取
    }
  }
  return takeFirstCodePoint(value);
}

/**
 * 头像回退字符：统一显示名的首个字素。
 * 昵称为空时显示名是“用户”，其首字正是“用”，与规则表一致。
 */
export function resolveAvatarFallbackChar(userName?: string | null): string {
  const display = resolveDisplayName(userName);
  return takeFirstGrapheme(display) || DEFAULT_AVATAR_CHAR;
}

/**
 * 头像图片加载失败注册表：键必须绑定 真实用户 ID + 头像地址。
 * 更换头像地址或切换账号时键随之变化，失败态天然重置，
 * 避免 A 的坏图片让 B 的正常头像也进入回退。
 * 禁止改用昵称作为键。
 */
const failedAvatarKeys = new Set<string>();

/** 头像失败态的键：userId + userAvatar。 */
export function buildAvatarFailureKey(
  userId?: number | string | null,
  userAvatar?: string | null
): string {
  return `${userId ?? "anonymous"}::${userAvatar ?? ""}`;
}

/** 查询某键是否处于图片加载失败态。 */
export function isAvatarFailureMarked(key: string): boolean {
  return failedAvatarKeys.has(key);
}

/** 标记图片加载失败，进入首字回退。 */
export function markAvatarFailed(key: string): void {
  failedAvatarKeys.add(key);
}

/** 图片加载成功（或地址修复后重新加载成功）时清除失败标记。 */
export function markAvatarLoaded(key: string): void {
  failedAvatarKeys.delete(key);
}
