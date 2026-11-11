# Design System: ZhiCore 操作系统学习平台

## 1. Overview

**Creative North Star: 「深蓝星图 + Qoder 浅绿纸面」**

register: product。同一品牌语言分两套页面壳：公开页（`/welcome`、`/user/login`、`/user/register`）是深墨蓝星图，白卡像浮起的纸；登录后的工作区（`/home`、`/graph`、`/teaching/*` 等）是 Qoder 通用模式的浅绿纸面，服务表格、编辑器与长时间阅读。白卡、表单、表格内部始终使用纸面令牌，深底不会把卡内文字变浅。

明确拒绝：紫蓝泛 AI 渐变、玻璃拟态、霓虹赛博朋克、渐变文字、装饰性光斑/blob、斜纹背景、通用 AI SaaS 模板、游戏化弹跳动画、用颜色或动效伪装 AI 能力。

**Key Characteristics:** 深蓝页壳与浅绿纸面；钴蓝只做主动作与当前项；荧光笔只标“现在”；状态色只表达学习结果；全站仅保留 hero 星图漂移这一处签名动效。

## 2. Theme Architecture

`App.vue` 按路由把主题写到 `body[data-theme]`，首次渲染前同步执行，路由切换在同一帧生效，避免布局挂载闪烁：

- `public`：`/welcome`、`/user/login`、`/user/register`
- `workspace`：其余全部路由

`body[data-theme="public"]::before` 换成深墨蓝；`body[data-theme="public"] .public-sky` 显示星图；`workspace` 下星图 `display:none`，沿用 `body::before` 的浅绿纸面渐变。公开页令牌只被页壳、页头、hero、分节标题、页脚消费。

## 3. Colors

### Workspace（纸面，登录后）
- 画布 `#e6e9d1`；次级面板 `#f4f7fb`；嵌套/选中 `#e9eef5`；主面板 `#ffffff`。
- 边线 `#dbe2ea` / `#e9eef4` / `#c3cedb`。
- 文本 主 `#20242b`，次 `#3a4150`，辅助 `#5f6873`（画布 ≈4.6:1），强辅助 `#525b66`（≈5.5:1）。
- 固定背景 `--oslab-canvas-gradient`：`180deg` 浅绿线性渐变（`#eaebc8 → #e3e6ce 54% → #dde2d4`）叠顶部极淡纸光 `rgba(252,253,235,.5)`；静态、平滑，不铺点阵。

### Public（星图，公开页）
- 基底 `--public-bg` `#070d1c`，深端 `#040913`；线性渐变 `#081020 → #070d1c → #040913`。
- 辉光：钴蓝 `rgba(36,71,216,.20)`、青绿 `rgba(38,128,140,.14)`、珊瑚 `rgba(215,97,74,.07)`、琥珀 `rgba(242,214,75,.05)`，均为低饱和径向渐变，只铺在星图容器里。
- 文本：正文 `--public-ink` `#f3f6fc`（≈17.9:1），次级 `--public-ink-soft` `#c8d4e7`（≈13:1），辅助 `--public-muted` `#9dabc4`（≈8.4:1）。
- 边线 `rgba(158,180,214,.22)`；强调/箭头 `--public-accent` `#9db8ff`。
- 页头登录用深底幽灵按钮（浅边线 + 浅字），hero 主 CTA 保持荧光黄 `#f2d64b` + 墨字（≈10.8:1）。

### Brand（两套共用）
钴蓝 `#2447d8`（hover `#1a35a8`，active `#142a87`，soft `#e9edff`）；荧光黄 `#f2d64b`（soft `#fff7c2`，strong `#ffe98a`）；珊瑚 `#d7614a`；状态绿 `#3f8f6e`、琥珀 `#b8892e`、砖红 `#b8543f`、未学习 `#6b7168`。状态色不用于装饰；标签必须带文字。

## 4. Starfield

`App.vue` 的 `.public-sky`：`position:fixed; inset:-4vmax; z-index:-1; pointer-events:none; overflow:hidden`。三层：

1. 基底与辉光：深墨蓝线性渐变 + 4 个低饱和径向辉光 + 边缘压暗。
2. `.public-sky-drift`：两层更淡的钴蓝/青绿辉光，`inset:-10%`，慢漂移载体。
3. `.public-sky-stars`：三档不同步长的 `radial-gradient` 点阵（220/340/470px，含极少量琥珀点），静态，避免规则条纹与噪点滤镜。

Hero 保留 `landing-hero.jpg`（`/welcome` preload 不变），作为右侧光场：`::before` 铺图并 `opacity:.42`，左缘 `linear-gradient` 蒙版融进夜色；`::after` 压深蓝黑罩层（左侧 97% 不透明）并向星图底色渐隐，保证标题/导语 ≥4.5:1。移动端只改蒙版方向与罩层浓度，不改结构。

白纸卡在深底上靠 `--public-shadow-paper: 0 30px 70px rgba(2,6,18,.55)` 浮起；公开页演示卡去掉 1px 冷纸边线（深底上不可见），认证弹层仍是白卡 + 深蓝黑遮罩 `rgba(4,8,20,.74)`。

## 5. Motion

- 唯一签名动效：`.public-sky-drift` 52s `alternate` `translate3d(-2.4%,-1.8%,0) scale(1.03)`，只动 transform，不做 `background-position`、不做大范围 `filter: blur`。
- 仅 `min-width:768px` + `hover:hover` + `pointer:fine` + `prefers-reduced-motion:no-preference` 开启；移动端、触屏、reduced-motion 全部静态。
- 其余过渡沿用 160/220/420ms、`cubic-bezier(.2,.7,.2,1)`；hero 入场 `landing-hero-rise 900ms`，reduced-motion 下关闭；段落 reveal 只做一次、不逐节堆叠。

## 6. Typography

**Display:** LXGW WenKai（Kaiti SC / KaiTi / Noto Serif SC 回退），只给页面标题、落地页标题与批注。
**Body:** 系统 UI 栈；**Mono:** Cascadia Code / JetBrains Mono。

- Display 34px（工作区）/ `clamp(48px,7.5vw,96px)`（hero，上限 6rem），字距 ≥-0.04em。
- Headline `clamp(30px,3vw,42px)`；Title 18px/600；Body 14px/1.6（公开页正文 16px/1.8，≤46ch）；Label 11–12px 等宽。
- 深底正文行高加 0.05–0.1，补偿浅色字的视觉变轻。

## 7. Components

- **Buttons:** 控件圆角 9px；主按钮钴蓝（工作区）/荧光黄（hero）；页头公开页用幽灵按钮；按压 `translateY(1px)`。
- **Cards:** 白面板、14px 容器圆角；工作区 1px `#dbe2ea` 边线 + `0 8px 24px rgba(32,36,43,.06)`；公开页白卡无可见边线，只用深投影。
- **Inputs:** 认证 48px、工作区 32–36px，9px 圆角；焦点 2px 钴蓝环；占位符 `#525b66`（白底 6.9:1）。
- **Navigation:** 工作区白色索引卡顶栏，一级项 42px（触屏 44px），当前项浅钴蓝底 + 钴蓝字。
- **状态标签:** 999px 胶囊，soft 底 + 状态文字色，必须写明状态。

## 8. Focus & Accessibility

- 工作区：`2px` 钴蓝焦点环、`offset 1px`。
- 公开页页壳：`2px` `#9db8ff` 焦点环（hero 左侧底 ≈10:1，≥3:1），白卡内部仍用钴蓝。
- 触屏（`pointer:coarse`）页头/页脚链接与按钮 ≥44px；正文不 <14px。
- 对比度：公开页正文/辅助、工作区辅助文字、占位符均 ≥4.5:1（已用验收脚本计算）。
- 文本选区荧光黄 + 墨字；`color-scheme: light`，白卡内表单控件保持浅色。

## 9. Do's and Don'ts

### Do
- 用 `body[data-theme]` 切换两套页壳；白卡内始终使用 `--oslab-*` 纸面令牌。
- 公开页新增页面级元素时引用 `--public-*`，新增白卡时保持白底深字。
- 星图只做一次慢漂移；新增动效先写 reduced-motion 与移动端降级。
- 保持 `/welcome` preload、landing-hero.jpg、按需 Monaco/ECharts/图标与 nginx 缓存不回退。

### Don't
- 不使用紫蓝泛 AI 渐变、玻璃拟态、霓虹赛博、渐变文字、条纹背景、噪点滤镜。
- 不把 `--public-*` 用于白卡内部，也不在 workspace 下渲染星图。
- 不给卡片加彩色侧边条或 1px 边线 + ≥16px 模糊的零偏移光晕。
- 不使用 ≥32px 卡片圆角、每节 tiny uppercase eyebrow 或逐节滚动淡入。
