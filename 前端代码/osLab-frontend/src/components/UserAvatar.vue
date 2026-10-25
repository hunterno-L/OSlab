<template>
  <span
    class="oslab-user-avatar"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      fontSize: `${fallbackFontSize}px`,
    }"
    aria-hidden="true"
  >
    <img
      v-if="hasImage"
      class="oslab-user-avatar-image"
      :src="avatarSrc"
      :width="size"
      :height="size"
      alt=""
      @error="handleImageError"
      @load="handleImageLoad"
    />
    <template v-else>{{ fallbackChar }}</template>
  </span>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import {
  buildAvatarFailureKey,
  isAvatarFailureMarked,
  markAvatarFailed,
  markAvatarLoaded,
  resolveAvatarFallbackChar,
} from "@/utils/userPresentation";

// eslint-disable-next-line no-undef
const props = withDefaults(
  // eslint-disable-next-line no-undef
  defineProps<{
    /** 真实用户 ID：参与失败态键，切换账号时重置回退状态。 */
    userId?: number | string;
    /** 昵称：无头像或图片失败时取其首个字素回退。 */
    userName?: string;
    /** 头像地址：为空或加载失败时回退首字。 */
    userAvatar?: string;
    /** 直径（px）：顶栏 36、个人资料摘要 52，用户管理摘要按所在布局设置。 */
    size?: number;
  }>(),
  {
    userId: undefined,
    userName: "",
    userAvatar: "",
    size: 36,
  }
);

// 失败态与 userId + userAvatar 绑定：换地址或换账号自动重置。
const failureKey = computed(() =>
  buildAvatarFailureKey(props.userId, props.userAvatar)
);
const failed = ref(isAvatarFailureMarked(failureKey.value));

watch(failureKey, (key) => {
  failed.value = isAvatarFailureMarked(key);
});

const avatarSrc = computed(() =>
  typeof props.userAvatar === "string" ? props.userAvatar.trim() : ""
);

const hasImage = computed(() => avatarSrc.value !== "" && !failed.value);

const fallbackChar = computed(() => resolveAvatarFallbackChar(props.userName));

const fallbackFontSize = computed(() =>
  Math.max(12, Math.round(props.size * 0.4))
);

const handleImageError = () => {
  markAvatarFailed(failureKey.value);
  failed.value = true;
};

const handleImageLoad = () => {
  markAvatarLoaded(failureKey.value);
  failed.value = false;
};
</script>

<style scoped>
.oslab-user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 50%;
  background: var(--oslab-primary, #2447d8);
  color: #ffffff;
  font-weight: 600;
  line-height: 1;
  user-select: none;
}

.oslab-user-avatar-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
