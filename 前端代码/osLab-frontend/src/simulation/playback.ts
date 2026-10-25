import type { DemoInput, DemoResult, DemoStep } from "./types";

export type PlayState = "idle" | "playing" | "paused" | "finished" | "error";

/**
 * 纯播放状态机。这里不持有定时器，也不依赖 Vue：
 * - activeStep = 0 表示尚未展示任何步骤；
 * - activeStep = steps.length 表示最后一步已展示。
 */
export interface PlayerState {
  /** 本次运行输入的深拷贝；输入编辑不修改它 */
  runConfig: DemoInput | null;
  steps: DemoStep[];
  activeStep: number;
  playState: PlayState;
  speed: number;
  /** 输入已偏离本次运行配置；重新运行前不得继续播放或记录完成 */
  dirty: boolean;
  error: string;
}

export const PLAYBACK_SPEEDS = [0.5, 1, 2] as const;
export const DEFAULT_PLAYBACK_SPEED = 1;
export const MIN_PLAYBACK_SPEED = 0.1;
export const MAX_PLAYBACK_SPEED = 4;
export const SPEED_STEP = 0.1;
export const BASE_STEP_INTERVAL_MS = 900;

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

/** 连续速度：0.1–4.0，按 0.1 取整；非法值回到默认 1×。 */
export function normalizeSpeed(speed: number): number {
  const value = Number(speed);
  if (!Number.isFinite(value)) return DEFAULT_PLAYBACK_SPEED;
  const clamped = Math.min(
    MAX_PLAYBACK_SPEED,
    Math.max(MIN_PLAYBACK_SPEED, value)
  );
  return Math.round(clamped * 10) / 10;
}

export function createPlayerState(
  speed: number = DEFAULT_PLAYBACK_SPEED
): PlayerState {
  return {
    runConfig: null,
    steps: [],
    activeStep: 0,
    playState: "idle",
    speed: normalizeSpeed(speed),
    dirty: false,
    error: "",
  };
}

/** 载入一次已完成的本地真值运行；配置与步骤均深拷贝。 */
export function load(
  state: PlayerState,
  config: DemoInput,
  result: DemoResult
): PlayerState {
  const steps = clone(result.steps);
  const hasSteps = steps.length > 0;
  return {
    runConfig: clone(config),
    steps,
    activeStep: 0,
    playState: hasSteps ? "idle" : "error",
    speed: state.speed,
    dirty: false,
    error: hasSteps ? "" : "该输入没有产生可展示的步骤",
  };
}

function errorState(state: PlayerState, message: string): PlayerState {
  return { ...state, playState: "error", error: message };
}

export function play(state: PlayerState): PlayerState {
  if (!state.steps.length) return errorState(state, "没有可播放的步骤");
  if (state.dirty) return errorState(state, "配置已修改，请重新运行演示");
  if (state.activeStep >= state.steps.length) {
    return { ...state, activeStep: 0, playState: "playing", error: "" };
  }
  return { ...state, playState: "playing", error: "" };
}

export function pause(state: PlayerState): PlayerState {
  if (state.playState !== "playing") return state;
  return { ...state, playState: "paused", error: "" };
}

export function stepForward(state: PlayerState): PlayerState {
  if (!state.steps.length) return errorState(state, "没有可播放的步骤");
  if (state.dirty) return errorState(state, "配置已修改，请重新运行演示");
  if (state.activeStep >= state.steps.length) {
    return { ...state, playState: "finished", error: "" };
  }
  const activeStep = state.activeStep + 1;
  const finished = activeStep >= state.steps.length;
  return {
    ...state,
    activeStep,
    playState: finished
      ? "finished"
      : state.playState === "playing"
      ? "playing"
      : "paused",
    error: "",
  };
}

export function stepBack(state: PlayerState): PlayerState {
  if (state.dirty) return errorState(state, "配置已修改，请重新运行演示");
  if (!state.steps.length) return errorState(state, "没有可播放的步骤");
  const activeStep = Math.max(0, state.activeStep - 1);
  return {
    ...state,
    activeStep,
    playState: activeStep === 0 ? "idle" : "paused",
    error: "",
  };
}

/** 拖动进度条；边界钳制到 [0, steps.length]。 */
export function seek(state: PlayerState, step: number): PlayerState {
  if (state.dirty) return errorState(state, "配置已修改，请重新运行演示");
  if (!state.steps.length) return errorState(state, "没有可播放的步骤");
  const activeStep = Math.max(
    0,
    Math.min(Math.round(step), state.steps.length)
  );
  return {
    ...state,
    activeStep,
    playState:
      activeStep === 0
        ? "idle"
        : activeStep >= state.steps.length
        ? "finished"
        : "paused",
    error: "",
  };
}

/** 重置回本次运行的初始步骤；不清除 dirty（配置仍需重新运行）。 */
export function reset(state: PlayerState): PlayerState {
  const hasSteps = state.steps.length > 0;
  return {
    ...state,
    activeStep: 0,
    playState: hasSteps ? "idle" : "error",
    error: hasSteps ? "" : "没有可播放的步骤",
  };
}

export function setSpeed(state: PlayerState, speed: number): PlayerState {
  return { ...state, speed: normalizeSpeed(speed) };
}

/** 输入改变：停止继续播放，并使完成资格失效，直到重新运行。 */
export function markDirty(state: PlayerState): PlayerState {
  if (!state.runConfig) return state;
  return {
    ...state,
    dirty: true,
    playState: state.playState === "playing" ? "paused" : state.playState,
  };
}

export function finish(state: PlayerState): PlayerState {
  if (!state.steps.length) return errorState(state, "没有可播放的步骤");
  return {
    ...state,
    activeStep: state.steps.length,
    playState: "finished",
    error: "",
  };
}

export function isAtEnd(state: PlayerState): boolean {
  return state.steps.length > 0 && state.activeStep >= state.steps.length;
}

/** 课程完成资格：真实播放到末尾、输入未改动、且本次运行有步骤。 */
export function canComplete(state: PlayerState): boolean {
  return (
    state.playState === "finished" &&
    !state.dirty &&
    state.steps.length > 0 &&
    state.activeStep === state.steps.length
  );
}

export function stepIntervalMs(speed: number): number {
  return BASE_STEP_INTERVAL_MS / normalizeSpeed(speed);
}
