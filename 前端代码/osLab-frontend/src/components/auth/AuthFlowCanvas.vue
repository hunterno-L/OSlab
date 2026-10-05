<template>
  <canvas
    ref="canvasRef"
    class="oslab-flow-canvas"
    role="img"
    aria-label="知识路径：虚拟内存、页表、缺页中断、LRU；蓝色信号沿关系流动"
  ></canvas>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";

// eslint-disable-next-line no-undef
const props = defineProps<{ paused?: boolean }>();

const canvasRef = ref<HTMLCanvasElement | null>(null);
let context: CanvasRenderingContext2D | null = null;
const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

// 时间轴状态：phase 为 8.4s 循环内的相位（0-1）
let phase = 0.34;
let last = 0;
let width = 800;
let height = 320;
// 指针视差：目标值与当前值分离，缓动系数约 0.06
let pointerX = 0;
let pointerY = 0;
let currentX = 0;
let currentY = 0;
let inViewport = true;
let formFocused = false;
let rafId = 0;
let resizeObserver: ResizeObserver | null = null;
let intersectionObserver: IntersectionObserver | null = null;

// 知识路径锚点与节点（与设计稿 auth-flow.html 保持一致）
const ANCHORS: Array<[number, number]> = [
  [-100, 235],
  [74, 195],
  [270, 98],
  [485, 196],
  [683, 100],
  [788, 234],
  [1010, 257],
];
const NODES = [
  { x: 74, y: 195, label: "虚拟内存" },
  { x: 270, y: 98, label: "页表" },
  { x: 485, y: 196, label: "缺页中断" },
  { x: 683, y: 100, label: "LRU" },
];

function cubic(
  ax: number,
  ay: number,
  bx: number,
  by: number,
  cx: number,
  cy: number,
  dx: number,
  dy: number,
  t: number
): [number, number] {
  const m = 1 - t;
  return [
    m * m * m * ax + 3 * m * m * t * bx + 3 * m * t * t * cx + t * t * t * dx,
    m * m * m * ay + 3 * m * m * t * by + 3 * m * t * t * cy + t * t * t * dy,
  ];
}

/**
 * 生成一条贝塞尔带状路径：控制点不带波纹，端点带 1.5px 细微漂移
 */
function paths(offset: number): Array<[number, number]> {
  const points: Array<[number, number]> = [];
  for (let i = 0; i < ANCHORS.length - 1; i++) {
    const ax = ANCHORS[i][0];
    const ay = ANCHORS[i][1] + offset;
    const dx = ANCHORS[i + 1][0];
    const dy = ANCHORS[i + 1][1] + offset;
    const wave = Math.sin(phase * Math.PI * 2 + i * 0.65) * 1.5;
    const ayWave = ay + wave;
    const dyWave = dy + wave;
    const bx = ax + (dx - ax) * 0.4;
    const cx = dx - (dx - ax) * 0.4;
    for (let n = 0; n < 55; n++) {
      points.push(cubic(ax, ayWave, bx, ay, cx, dy, dx, dyWave, n / 54));
    }
  }
  return points;
}

function stroke(points: Array<[number, number]>, color: string, lineWidth = 1) {
  const ctx = context;
  if (!ctx) return;
  ctx.beginPath();
  points.forEach((p, i) =>
    i ? ctx.lineTo(p[0], p[1]) : ctx.moveTo(p[0], p[1])
  );
  ctx.strokeStyle = color;
  ctx.lineWidth = lineWidth;
  ctx.stroke();
}

function draw() {
  const ctx = context;
  if (!ctx || !canvasRef.value) return;
  const dpi = Math.min(window.devicePixelRatio || 1, 2);
  ctx.setTransform(dpi, 0, 0, dpi, 0, 0);
  ctx.clearRect(0, 0, width, height);
  const scale = width / 900;
  ctx.translate(currentX, Math.max(0, (height - 310 * scale) / 2) + currentY);
  ctx.scale(scale, scale);
  // 25 条细线组成弯曲带状路径
  for (let i = -12; i <= 12; i++) {
    stroke(
      paths(i * 2.6),
      i % 4 === 0 ? "#cbd2d8" : "#e1e5e8",
      i % 4 === 0 ? 0.9 : 0.65
    );
  }
  [-4, 4].forEach((i) => stroke(paths(i * 2.6), "#cdd9f1", 0.9));
  // 两段蓝色短光流沿路径持续推进，形成局部高亮
  [paths(0), paths(10.4)].forEach((path, j) => {
    const head = (phase + 0.14 + j * 0.37) % 1;
    for (let k = 0; k < 19; k++) {
      const fraction = head - k * 0.0028;
      if (fraction < 0) continue;
      const index = Math.min(
        path.length - 2,
        Math.floor(fraction * (path.length - 1))
      );
      stroke(
        [path[index], path[index + 1]],
        `rgba(32,89,201,${Math.pow(1 - k / 19, 1.2)})`,
        j ? 1.3 : 2.5
      );
    }
  });
  // 节点：光流接近时细环扩展约 6px，内点短暂变蓝
  NODES.forEach((node, i) => {
    const raw = (phase + 0.14 - (0.15 + i * 0.175) + 1) % 1;
    const pulse = Math.max(0, 1 - Math.min(raw, 1 - raw) / 0.09);
    ctx.beginPath();
    ctx.arc(node.x, node.y, 12 + pulse * 6, 0, Math.PI * 2);
    ctx.fillStyle = "#fafaf8";
    ctx.fill();
    ctx.strokeStyle = "#ccd4de";
    ctx.lineWidth = 0.8;
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(node.x, node.y, 4.5, 0, Math.PI * 2);
    ctx.fillStyle = pulse > 0.25 ? "#2059c9" : "#171a1d";
    ctx.fill();
    const isLru = node.label === "LRU";
    ctx.font =
      (isLru ? "600 " : "") +
      Math.max(12, 14 * scale) / scale +
      'px "PingFang SC","Microsoft YaHei",sans-serif';
    ctx.fillStyle = isLru ? "#2059c9" : "#525962";
    ctx.textAlign = "center";
    ctx.fillText(node.label, node.x, node.y + 40);
  });
  // 一个先修分支与一个应用分支，保持课程含义
  if (width > 490) {
    const branches = [
      {
        pts: [
          [270, 98],
          [324, 95],
          [320, 260],
          [398, 266],
        ] as Array<[number, number]>,
        label: "地址转换",
      },
      {
        pts: [
          [683, 100],
          [792, 100],
          [705, 27],
          [782, 27],
        ] as Array<[number, number]>,
        label: "页面置换实验",
      },
    ];
    branches.forEach((branch) => {
      const [a, b, c, d] = branch.pts;
      const branchPoints: Array<[number, number]> = [];
      for (let n = 0; n < 70; n++) {
        branchPoints.push(
          cubic(a[0], a[1], b[0], b[1], c[0], c[1], d[0], d[1], n / 69)
        );
      }
      stroke(branchPoints, "#cfd7de", 0.8);
      ctx.beginPath();
      ctx.arc(d[0], d[1], 3.5, 0, Math.PI * 2);
      ctx.fillStyle = "#4f8875";
      ctx.fill();
      ctx.textAlign = "left";
      ctx.font = 12 / scale + 'px "PingFang SC","Microsoft YaHei",sans-serif';
      ctx.fillStyle = "#777f86";
      ctx.fillText(branch.label, d[0] + 16, d[1] + 4);
    });
  }
}

function tick(now: number) {
  const delta = Math.min(now - last || 16, 50);
  last = now;
  if (inViewport && !document.hidden) {
    // 任一表单字段获得焦点后，流速降至空闲状态的 35%
    if (!props.paused && !reducedMotion.matches) {
      phase = (phase + (delta / 8400) * (formFocused ? 0.35 : 1)) % 1;
    }
    currentX += (pointerX - currentX) * 0.06;
    currentY += (pointerY - currentY) * 0.06;
    draw();
  }
  rafId = requestAnimationFrame(tick);
}

/**
 * 成功提交后让知识流向前推进一小段
 */
function advance(amount: number) {
  phase = (phase + amount) % 1;
}

// eslint-disable-next-line no-undef
defineExpose({ advance });

function onPointerMove(event: PointerEvent) {
  if (event.pointerType === "touch" || reducedMotion.matches) return;
  const canvas = canvasRef.value;
  if (!canvas) return;
  const box = canvas.getBoundingClientRect();
  // 最大约 5px 横向、3px 纵向位移
  pointerX = ((event.clientX - box.left) / box.width - 0.5) * 10;
  pointerY = ((event.clientY - box.top) / box.height - 0.5) * 6;
}

function onPointerLeave() {
  pointerX = 0;
  pointerY = 0;
}

function isFormField(el: Element | null): boolean {
  return (
    !!el &&
    (el.tagName === "INPUT" ||
      el.tagName === "TEXTAREA" ||
      el.tagName === "SELECT")
  );
}

function onDocFocusIn(event: FocusEvent) {
  formFocused = isFormField(event.target as Element | null);
}

function onDocFocusOut() {
  formFocused = isFormField(document.activeElement);
}

onMounted(() => {
  const canvas = canvasRef.value;
  if (!canvas) return;
  context = canvas.getContext("2d");
  resizeObserver = new ResizeObserver(() => {
    const rect = canvas.getBoundingClientRect();
    width = rect.width;
    height = rect.height;
    const dpi = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(width * dpi);
    canvas.height = Math.round(height * dpi);
    draw();
  });
  resizeObserver.observe(canvas);
  // 离开视口后暂停绘制
  intersectionObserver = new IntersectionObserver((entries) => {
    inViewport = entries[0].isIntersecting;
  });
  intersectionObserver.observe(canvas);
  canvas.addEventListener("pointermove", onPointerMove);
  canvas.addEventListener("pointerleave", onPointerLeave);
  document.addEventListener("focusin", onDocFocusIn);
  document.addEventListener("focusout", onDocFocusOut);
  rafId = requestAnimationFrame(tick);
});

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId);
  resizeObserver?.disconnect();
  intersectionObserver?.disconnect();
  const canvas = canvasRef.value;
  if (canvas) {
    canvas.removeEventListener("pointermove", onPointerMove);
    canvas.removeEventListener("pointerleave", onPointerLeave);
  }
  document.removeEventListener("focusin", onDocFocusIn);
  document.removeEventListener("focusout", onDocFocusOut);
});
</script>
