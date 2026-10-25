<template>
  <figure
    ref="hostRef"
    class="paging-theater"
    :class="[`is-${mode}`, { 'is-compact': compact }]"
  >
    <div
      ref="stageRef"
      class="paging-theater-stage"
      role="img"
      :aria-label="stageLabel"
    >
      <canvas
        ref="canvasRef"
        class="paging-theater-canvas"
        aria-hidden="true"
      ></canvas>
    </div>

    <div class="paging-theater-legend" aria-hidden="true">
      <span
        v-for="beat in BEATS"
        :key="beat.id"
        :class="{ 'is-active': activeBeat === beat.id }"
      >
        <b>{{ beat.id }}</b
        >{{ beat.label }}
      </span>
    </div>

    <p class="sr-only" aria-live="polite">{{ liveText }}</p>

    <div v-if="showControls" class="paging-theater-controls">
      <button
        type="button"
        :aria-label="isPlaying ? '暂停演示' : '播放演示'"
        @click="togglePlay"
      >
        {{ isPlaying ? "暂停" : "播放" }}
      </button>
      <button type="button" aria-label="上一步" @click="step(-1)">
        上一步
      </button>
      <button type="button" aria-label="下一步" @click="step(1)">下一步</button>
      <button type="button" aria-label="重播演示" @click="replay">重播</button>
    </div>

    <ol v-if="reducedMotion" class="paging-theater-steps">
      <li v-for="access in scene.accesses" :key="access.index">
        第 {{ access.index + 1 }} 次请求第 {{ access.page }} 页：{{
          access.hit
            ? "TLB 命中"
            : `缺页${access.evicted ? `，淘汰第 ${access.evicted} 页` : ""}`
        }}
      </li>
    </ol>
  </figure>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { BEATS, buildPagingScene, phaseAt } from "./pagingScene";
import type { PagingAccess, PagingPhase, PagingScene } from "./pagingScene";

interface Box {
  x: number;
  y: number;
  w: number;
  h: number;
}

interface TheaterLayout {
  width: number;
  height: number;
  cpu: Box;
  tlb: Box;
  table: Box | null;
  frames: { x: number; y: number; w: number; h: number; gap: number };
  disk: Box;
  stamp: { x: number; y: number };
}

interface Palette {
  ink: string;
  panel: string;
  panelSoft: string;
  line: string;
  primary: string;
  highlight: string;
  highlightSoft: string;
  annotation: string;
  annotationSoft: string;
  muted: string;
}

// eslint-disable-next-line no-undef
const props = withDefaults(
  // eslint-disable-next-line no-undef
  defineProps<{
    mode?: "ambient" | "guided";
    playing?: boolean;
    showControls?: boolean;
    label?: string;
  }>(),
  {
    mode: "ambient",
    playing: true,
    showControls: true,
    label: "页面置换 · LRU",
  }
);

// eslint-disable-next-line no-undef
const emit = defineEmits<{ beat: [label: string] }>();

const WIDE: TheaterLayout = {
  width: 1000,
  height: 420,
  cpu: { x: 40, y: 40, w: 130, h: 90 },
  tlb: { x: 40, y: 160, w: 130, h: 34 },
  table: { x: 210, y: 40, w: 250, h: 300 },
  frames: { x: 520, y: 40, w: 140, h: 100, gap: 12 },
  disk: { x: 520, y: 330, w: 440, h: 60 },
  stamp: { x: 640, y: 250 },
};

const COMPACT: TheaterLayout = {
  width: 640,
  height: 420,
  cpu: { x: 24, y: 24, w: 120, h: 80 },
  tlb: { x: 24, y: 120, w: 120, h: 30 },
  table: null,
  frames: { x: 190, y: 24, w: 120, h: 90, gap: 10 },
  disk: { x: 190, y: 320, w: 360, h: 60 },
  stamp: { x: 340, y: 210 },
};

const FONT_UI = '"Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif';
const FONT_MONO = '"Cascadia Code", "JetBrains Mono", Consolas, monospace';

const scene: PagingScene = buildPagingScene();
const weight = ref(0);
const isPlaying = ref(props.playing);
const inView = ref(false);
const compact = ref(false);
const reducedMotion = ref(false);
const liveText = ref("");

const hostRef = ref<HTMLElement | null>(null);
const stageRef = ref<HTMLElement | null>(null);
const canvasRef = ref<HTMLCanvasElement | null>(null);

const palette: Palette = {
  ink: "",
  panel: "",
  panelSoft: "",
  line: "",
  primary: "",
  highlight: "",
  highlightSoft: "",
  annotation: "",
  annotationSoft: "",
  muted: "",
};

let context: CanvasRenderingContext2D | null = null;
let logicalWidth = WIDE.width;
let logicalHeight = WIDE.height;
let raf = 0;
let lastTime = 0;
let lastFrame = 0;
let lastAnnounce = 0;
let resizeObserver: ResizeObserver | null = null;
let intersectionObserver: IntersectionObserver | null = null;

const WEIGHT_MS = computed(() => (props.mode === "ambient" ? 560 : 460));
const frameInterval = computed(() =>
  props.mode === "ambient" ? 1000 / 24 : 1000 / 30
);

const current = computed(() => phaseAt(scene, weight.value));
const activeBeat = computed(() =>
  current.value.phase.beat === "HIT" ? 2 : current.value.phase.beat
);
const liveLabel = computed(() => {
  const { access, phase } = current.value;
  return `第 ${access.index + 1} 次访问第 ${access.page} 页：${phase.label}`;
});
const stageLabel = computed(
  () => `${props.label}：三个页框，访问序列 ${scene.sequence.join("、")}`
);

function canPlay() {
  return isPlaying.value && inView.value && !reducedMotion.value;
}

function refreshPalette() {
  const host = hostRef.value;
  if (!host) return;
  const styles = getComputedStyle(host);
  const read = (name: string) => styles.getPropertyValue(name).trim();
  palette.ink = read("--oslab-ink");
  palette.panel = read("--oslab-panel");
  palette.panelSoft = read("--oslab-panel-soft");
  palette.line = read("--oslab-line");
  palette.primary = read("--oslab-primary");
  palette.highlight = read("--oslab-highlight");
  palette.highlightSoft = read("--oslab-highlight-soft");
  palette.annotation = read("--oslab-annotation");
  palette.annotationSoft = read("--oslab-annotation-soft");
  palette.muted = read("--oslab-muted-strong");
}

function roundedRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  radius: number
) {
  const r = Math.min(radius, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.lineTo(x + w - r, y);
  ctx.quadraticCurveTo(x + w, y, x + w, y + r);
  ctx.lineTo(x + w, y + h - r);
  ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  ctx.lineTo(x + r, y + h);
  ctx.quadraticCurveTo(x, y + h, x, y + h - r);
  ctx.lineTo(x, y + r);
  ctx.quadraticCurveTo(x, y, x + r, y);
  ctx.closePath();
}

function drawCpu(
  ctx: CanvasRenderingContext2D,
  layout: TheaterLayout,
  access: PagingAccess,
  phase: PagingPhase
) {
  const box = layout.cpu;
  ctx.save();
  roundedRect(ctx, box.x, box.y, box.w, box.h, 10);
  ctx.fillStyle = palette.panel;
  ctx.fill();
  const active = phase.beat === 1 || phase.beat === 6;
  ctx.lineWidth = active ? 2 : 1;
  ctx.strokeStyle = active ? palette.primary : palette.line;
  ctx.stroke();

  ctx.textAlign = "left";
  ctx.textBaseline = "top";
  ctx.fillStyle = palette.muted;
  ctx.font = `600 11px ${FONT_UI}`;
  ctx.fillText("CPU", box.x + 12, box.y + 12);

  ctx.fillStyle = palette.ink;
  ctx.font = `600 ${compact.value ? 14 : 16}px ${FONT_MONO}`;
  ctx.fillText(`页 ${access.page}`, box.x + 12, box.y + 32);

  ctx.fillStyle = palette.muted;
  ctx.font = `12px ${FONT_UI}`;
  ctx.fillText(phase.label, box.x + 12, box.y + 58);
  ctx.restore();
}

function drawTlb(
  ctx: CanvasRenderingContext2D,
  layout: TheaterLayout,
  access: PagingAccess,
  phase: PagingPhase
) {
  const box = layout.tlb;
  const hit = access.hit || phase.beat === "HIT";
  ctx.save();
  roundedRect(ctx, box.x, box.y, box.w, box.h, 6);
  ctx.fillStyle = hit ? palette.highlightSoft : palette.panel;
  ctx.fill();
  ctx.lineWidth = hit || phase.beat === 2 ? 2 : 1;
  ctx.strokeStyle = hit
    ? palette.primary
    : phase.beat === 2
    ? palette.annotation
    : palette.line;
  ctx.stroke();

  ctx.textAlign = "left";
  ctx.textBaseline = "middle";
  ctx.fillStyle = palette.ink;
  ctx.font = `600 12px ${FONT_MONO}`;
  ctx.fillText("TLB", box.x + 10, box.y + box.h / 2);

  ctx.textAlign = "right";
  ctx.fillStyle = hit ? palette.primary : palette.muted;
  ctx.font = `12px ${FONT_UI}`;
  ctx.fillText(hit ? "命中" : "未命中", box.x + box.w - 10, box.y + box.h / 2);
  ctx.restore();
}

function drawPageTable(
  ctx: CanvasRenderingContext2D,
  layout: TheaterLayout,
  access: PagingAccess,
  phase: PagingPhase
) {
  const box = layout.table;
  if (!box) return;

  ctx.save();
  roundedRect(ctx, box.x, box.y, box.w, box.h, 10);
  ctx.fillStyle = palette.panelSoft;
  ctx.fill();
  ctx.lineWidth = 1;
  ctx.strokeStyle = palette.line;
  ctx.stroke();

  ctx.textAlign = "left";
  ctx.textBaseline = "top";
  ctx.fillStyle = palette.muted;
  ctx.font = `600 11px ${FONT_UI}`;
  ctx.fillText("页表", box.x + 12, box.y + 12);

  const headerY = box.y + 40;
  const pages = [1, 2, 3, 4, 5];
  const rowHeight = (box.h - 52) / pages.length;

  ctx.font = `11px ${FONT_MONO}`;
  ctx.fillStyle = palette.muted;
  ctx.fillText("页", box.x + 12, headerY);
  ctx.fillText("帧", box.x + 92, headerY);
  ctx.fillText("有效位", box.x + 160, headerY);

  pages.forEach((page, index) => {
    const rowY = headerY + 12 + index * rowHeight;
    if (page === access.page) {
      ctx.save();
      ctx.globalAlpha = 0.45;
      ctx.fillStyle = palette.highlight;
      ctx.fillRect(box.x + 6, rowY - 2, box.w - 12, rowHeight - 2);
      ctx.restore();
    }
    const frame = access.framesAfter.indexOf(page);
    const current = page === access.page;
    ctx.fillStyle = current ? palette.ink : palette.muted;
    ctx.fillText(String(page), box.x + 12, rowY);
    ctx.fillText(frame >= 0 ? String(frame) : "-", box.x + 92, rowY);
    ctx.fillText(frame >= 0 ? "1" : "0", box.x + 160, rowY);
  });

  if (phase.beat === 3) {
    ctx.strokeStyle = palette.primary;
    ctx.lineWidth = 2;
    const rowY = headerY + 12 + (access.page - 1) * rowHeight;
    ctx.beginPath();
    ctx.moveTo(box.x + 6, rowY + rowHeight / 2);
    ctx.lineTo(box.x + box.w - 6, rowY + rowHeight / 2);
    ctx.stroke();
  }
  ctx.restore();
}

function drawFrames(
  ctx: CanvasRenderingContext2D,
  layout: TheaterLayout,
  access: PagingAccess,
  phase: PagingPhase
) {
  const spec = layout.frames;
  const showAfter =
    phase.beat === 5 || phase.beat === 6 || phase.beat === "HIT";
  const display = showAfter ? access.framesAfter : access.framesBefore;

  ctx.save();
  ctx.textAlign = "left";
  ctx.textBaseline = "top";
  ctx.fillStyle = palette.muted;
  ctx.font = `600 11px ${FONT_UI}`;
  ctx.fillText("页框", spec.x, spec.y - 14);

  display.forEach((page, index) => {
    const x = spec.x + index * (spec.w + spec.gap);
    const y = spec.y;
    roundedRect(ctx, x, y, spec.w, spec.h, 8);
    ctx.fillStyle = palette.panel;
    ctx.fill();

    if (page === null) {
      ctx.setLineDash([4, 4]);
      ctx.lineWidth = 1;
      ctx.strokeStyle = palette.line;
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.textAlign = "center";
      ctx.fillStyle = palette.muted;
      ctx.font = `12px ${FONT_UI}`;
      ctx.fillText("空闲", x + spec.w / 2, y + spec.h / 2 - 6);
    } else {
      const isTarget =
        index === access.frameIndex && (phase.beat === 5 || phase.beat === 6);
      ctx.lineWidth = isTarget ? 2 : 1;
      ctx.strokeStyle = isTarget ? palette.primary : palette.line;
      ctx.stroke();

      ctx.textAlign = "center";
      ctx.fillStyle = palette.ink;
      ctx.font = `600 ${compact.value ? 22 : 28}px ${FONT_MONO}`;
      ctx.fillText(String(page), x + spec.w / 2, y + spec.h / 2 - 20);

      ctx.fillStyle = palette.muted;
      ctx.font = `11px ${FONT_MONO}`;
      ctx.fillText(
        page === access.page ? "当前访问" : "驻留",
        x + spec.w / 2,
        y + spec.h - 24
      );
    }
  });
  ctx.restore();
}

function drawDisk(
  ctx: CanvasRenderingContext2D,
  layout: TheaterLayout,
  access: PagingAccess,
  phase: PagingPhase
) {
  const box = layout.disk;
  ctx.save();
  roundedRect(ctx, box.x, box.y, box.w, box.h, 10);
  ctx.fillStyle = palette.panelSoft;
  ctx.fill();
  ctx.lineWidth = 1;
  ctx.strokeStyle = palette.line;
  ctx.stroke();

  ctx.textAlign = "left";
  ctx.textBaseline = "top";
  ctx.fillStyle = palette.muted;
  ctx.font = `600 11px ${FONT_UI}`;
  ctx.fillText("磁盘", box.x + 12, box.y + 10);

  const pages = [1, 2, 3, 4, 5];
  const cardWidth = compact.value ? 46 : 64;
  const gap = compact.value ? 6 : 8;
  const startX = box.x + (compact.value ? 60 : 72);

  pages.forEach((page, index) => {
    const x = startX + index * (cardWidth + gap);
    const isIncoming = phase.beat === 5 && page === access.page;
    const isEvicted = phase.beat === 5 && page === access.evicted;
    roundedRect(ctx, x, box.y + 12, cardWidth, box.h - 22, 6);
    ctx.fillStyle = isEvicted
      ? palette.annotationSoft
      : isIncoming
      ? palette.highlightSoft
      : palette.panel;
    ctx.fill();
    ctx.lineWidth = isIncoming || isEvicted ? 2 : 1;
    ctx.strokeStyle = isEvicted
      ? palette.annotation
      : isIncoming
      ? palette.primary
      : palette.line;
    ctx.stroke();

    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillStyle = palette.ink;
    ctx.font = `600 12px ${FONT_MONO}`;
    ctx.fillText(
      String(page),
      x + cardWidth / 2,
      box.y + 12 + (box.h - 22) / 2
    );
  });
  ctx.restore();
}

function drawPath(
  ctx: CanvasRenderingContext2D,
  points: [number, number][],
  color: string,
  width = 1.5,
  dash: number[] = []
) {
  if (points.length === 0) return;
  ctx.save();
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.setLineDash(dash);
  ctx.beginPath();
  points.forEach(([x, y], index) => {
    if (index === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.stroke();
  const [endX, endY] = points[points.length - 1];
  ctx.setLineDash([]);
  ctx.beginPath();
  ctx.moveTo(endX - 6, endY - 4);
  ctx.lineTo(endX, endY);
  ctx.lineTo(endX - 6, endY + 4);
  ctx.stroke();
  ctx.restore();
}

function drawAddressLine(
  ctx: CanvasRenderingContext2D,
  layout: TheaterLayout,
  access: PagingAccess,
  phase: PagingPhase
) {
  const cpuOut: [number, number] = [
    layout.cpu.x + layout.cpu.w,
    layout.cpu.y + layout.cpu.h / 2,
  ];
  const tlbIn: [number, number] = [
    layout.tlb.x,
    layout.tlb.y + layout.tlb.h / 2,
  ];
  const tlbOut: [number, number] = [
    layout.tlb.x + layout.tlb.w,
    layout.tlb.y + layout.tlb.h / 2,
  ];
  const targetFrame: [number, number] = [
    layout.frames.x + access.frameIndex * (layout.frames.w + layout.frames.gap),
    layout.frames.y + layout.frames.h / 2,
  ];

  const toTlb: [number, number][] = [cpuOut, [tlbIn[0], cpuOut[1]], tlbIn];

  if (phase.beat === 1) {
    drawPath(ctx, toTlb, palette.primary);
    return;
  }

  if (phase.beat === "HIT") {
    drawPath(
      ctx,
      [...toTlb, [tlbOut[0], targetFrame[1]], targetFrame],
      palette.primary,
      2
    );
    return;
  }

  if (phase.beat === 2) {
    drawPath(ctx, toTlb, palette.annotation, 2, [4, 4]);
    return;
  }

  if (phase.beat === 3) {
    if (layout.table) {
      const rowY =
        layout.table.y + 52 + (access.page - 1) * ((layout.table.h - 52) / 5);
      drawPath(
        ctx,
        [...toTlb, [layout.table.x, tlbIn[1]], [layout.table.x, rowY]],
        palette.primary,
        2
      );
    } else {
      drawPath(
        ctx,
        [...toTlb, [tlbOut[0], targetFrame[1]], targetFrame],
        palette.primary,
        2
      );
    }
    return;
  }

  if (phase.beat === 4) {
    const stamp: [number, number] = [layout.stamp.x - 72, layout.stamp.y];
    const start: [number, number] = layout.table
      ? [layout.table.x + layout.table.w, layout.stamp.y]
      : [tlbOut[0], layout.stamp.y];
    drawPath(ctx, [start, stamp], palette.annotation, 2, [5, 5]);
    return;
  }

  if (phase.beat === 5) {
    ctx.save();
    ctx.strokeStyle = palette.primary;
    ctx.lineWidth = 2;
    ctx.setLineDash([5, 4]);
    ctx.beginPath();
    const fromX = layout.table ? layout.table.x + layout.table.w : tlbOut[0];
    const fromY = layout.table
      ? layout.table.y + layout.table.h
      : layout.disk.y;
    ctx.moveTo(fromX, fromY);
    ctx.quadraticCurveTo(
      layout.disk.x + layout.disk.w / 2,
      layout.disk.y - 30,
      targetFrame[0] + layout.frames.w / 2,
      targetFrame[1] + layout.frames.h / 2
    );
    ctx.stroke();
    ctx.restore();
    return;
  }

  drawPath(
    ctx,
    [
      ...toTlb,
      ...(layout.table
        ? ([[layout.table.x, tlbIn[1]]] as [number, number][])
        : []),
      [targetFrame[0], targetFrame[1]],
    ],
    palette.primary,
    2
  );
}

function drawStamp(ctx: CanvasRenderingContext2D, layout: TheaterLayout) {
  const { x, y } = layout.stamp;
  ctx.save();
  ctx.translate(x, y);
  ctx.rotate((-4 * Math.PI) / 180);
  ctx.strokeStyle = palette.annotation;
  ctx.lineWidth = 2;
  roundedRect(ctx, -70, -20, 140, 40, 4);
  ctx.stroke();
  ctx.fillStyle = palette.annotation;
  ctx.font = `600 18px ${FONT_UI}`;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText("缺页中断", 0, 1);
  ctx.restore();
}

function draw() {
  const ctx = context;
  if (!ctx) return;
  const layout = compact.value ? COMPACT : WIDE;
  const { access, phase } = current.value;

  ctx.clearRect(0, 0, logicalWidth, logicalHeight);
  drawCpu(ctx, layout, access, phase);
  drawTlb(ctx, layout, access, phase);
  if (layout.table) drawPageTable(ctx, layout, access, phase);
  drawFrames(ctx, layout, access, phase);
  drawDisk(ctx, layout, access, phase);
  drawAddressLine(ctx, layout, access, phase);
  if (phase.beat === 4) drawStamp(ctx, layout);
}

function resize() {
  const stage = stageRef.value;
  const canvas = canvasRef.value;
  if (!stage || !canvas) return;

  const cssWidth = stage.clientWidth;
  if (cssWidth <= 0) return;

  compact.value = cssWidth < 640;
  const layout = compact.value ? COMPACT : WIDE;
  logicalWidth = layout.width;
  logicalHeight = layout.height;

  const cssHeight = Math.round((cssWidth * logicalHeight) / logicalWidth);
  const dpr = compact.value ? 1.5 : Math.min(window.devicePixelRatio || 1, 2);

  canvas.width = Math.round(cssWidth * dpr);
  canvas.height = Math.round(cssHeight * dpr);
  canvas.style.width = "100%";
  canvas.style.height = `${cssHeight}px`;

  context = canvas.getContext("2d");
  if (context) {
    const scaleX = canvas.width / logicalWidth;
    const scaleY = canvas.height / logicalHeight;
    context.setTransform(scaleX, 0, 0, scaleY, 0, 0);
  }
  draw();
}

function start() {
  if (raf || !canPlay()) return;
  lastTime = performance.now();
  lastFrame = 0;
  raf = requestAnimationFrame(tick);
}

function stop() {
  if (!raf) return;
  cancelAnimationFrame(raf);
  raf = 0;
}

function tick(now: number) {
  if (!canPlay()) {
    raf = 0;
    return;
  }
  raf = requestAnimationFrame(tick);
  if (now - lastFrame < frameInterval.value) return;

  const delta = Math.min(now - lastTime, 100);
  lastTime = now;
  lastFrame = now;
  weight.value += delta / WEIGHT_MS.value;

  if (weight.value >= scene.totalWeight) {
    if (props.mode === "ambient") {
      weight.value %= scene.totalWeight;
    } else {
      weight.value = scene.totalWeight - 0.001;
      isPlaying.value = false;
    }
  }

  draw();
  if (!canPlay()) stop();
}

function togglePlay() {
  isPlaying.value = !isPlaying.value;
}

function step(direction: 1 | -1) {
  isPlaying.value = false;
  const next = weight.value + direction;
  weight.value = Math.min(Math.max(next, 0), scene.totalWeight - 0.001);
  draw();
}

function replay() {
  weight.value = 0;
  isPlaying.value = !reducedMotion.value;
  draw();
}

function drawFinalFrame() {
  weight.value = scene.totalWeight - 0.001;
  draw();
}

function syncVisibility() {
  if (document.hidden) stop();
  else if (canPlay()) start();
}

function onWindowResize() {
  refreshPalette();
  resize();
}

watch([isPlaying, reducedMotion], () => {
  if (canPlay()) start();
  else stop();
});

watch(
  () => props.playing,
  (value) => {
    isPlaying.value = value;
  }
);

watch(liveLabel, (label) => {
  const now = performance.now();
  if (now - lastAnnounce < 800) return;
  lastAnnounce = now;
  liveText.value = label;
  emit("beat", label);
});

onMounted(() => {
  reducedMotion.value = window.matchMedia(
    "(prefers-reduced-motion: reduce)"
  ).matches;
  refreshPalette();

  if (typeof ResizeObserver !== "undefined") {
    resizeObserver = new ResizeObserver(() => {
      refreshPalette();
      resize();
    });
    if (hostRef.value) resizeObserver.observe(hostRef.value);
  } else {
    window.addEventListener("resize", onWindowResize);
  }

  if (typeof IntersectionObserver !== "undefined") {
    intersectionObserver = new IntersectionObserver(
      ([entry]) => {
        inView.value = entry.isIntersecting;
        if (canPlay()) start();
        else stop();
      },
      { threshold: 0.15 }
    );
    if (hostRef.value) intersectionObserver.observe(hostRef.value);
  } else {
    inView.value = true;
  }

  document.addEventListener("visibilitychange", syncVisibility);
  resize();
  liveText.value = liveLabel.value;

  if (reducedMotion.value) {
    isPlaying.value = false;
    drawFinalFrame();
  } else {
    syncVisibility();
  }
});

onBeforeUnmount(() => {
  stop();
  resizeObserver?.disconnect();
  intersectionObserver?.disconnect();
  window.removeEventListener("resize", onWindowResize);
  document.removeEventListener("visibilitychange", syncVisibility);
});
</script>

<style scoped>
.paging-theater {
  position: relative;
  margin: 0;
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}

.paging-theater-stage {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--oslab-panel-soft);
  border-radius: var(--oslab-radius-control);
}

.paging-theater-stage::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image: radial-gradient(var(--oslab-line) 1px, transparent 1px);
  background-size: 20px 20px;
  opacity: 0.35;
  pointer-events: none;
}

.paging-theater.is-compact .paging-theater-stage::before {
  background-size: 16px 16px;
}

.paging-theater-canvas {
  position: relative;
  z-index: 1;
  display: block;
  width: 100%;
  height: auto;
}

.paging-theater-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 12px;
  color: var(--oslab-muted-strong);
  font-size: 12px;
}

.paging-theater-legend b {
  margin-right: 4px;
  color: var(--oslab-primary);
  font-family: var(--oslab-font-mono);
  font-weight: 600;
}

.paging-theater-legend .is-active {
  color: var(--oslab-ink);
  background: var(--oslab-highlight-soft);
}

.paging-theater-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.paging-theater-controls button {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-control);
  background: var(--oslab-panel);
  color: var(--oslab-ink);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.paging-theater-controls button:hover {
  border-color: var(--oslab-primary);
}

.paging-theater-controls button:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
}

.paging-theater-steps {
  margin: 12px 0 0;
  padding-left: 20px;
  color: var(--oslab-muted-strong);
  font-size: 12px;
  line-height: 1.7;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 640px) {
  .paging-theater {
    padding: 10px;
  }

  .paging-theater-legend {
    gap: 6px 10px;
    font-size: 11px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .paging-theater *,
  .paging-theater *::before,
  .paging-theater *::after {
    transition: none !important;
    animation: none !important;
  }
}
</style>
