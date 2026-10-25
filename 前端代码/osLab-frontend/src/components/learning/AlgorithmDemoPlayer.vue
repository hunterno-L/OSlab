<template>
  <section class="demo-player">
    <header class="demo-head">
      <div>
        <p class="demo-eyebrow">{{ categoryLabel }}</p>
        <h2 class="demo-title">{{ definition.title }}</h2>
        <p class="demo-description">{{ definition.description }}</p>
      </div>
      <div class="demo-badges">
        <a-tag color="blue">运行演示</a-tag>
        <a-tag color="gray">不写判题/掌握事件</a-tag>
        <a-tag
          :color="
            definition.challengeMode === 'SERVER_VERIFIED_WHEN_ADAPTED'
              ? 'orange'
              : 'gray'
          "
          >{{
            definition.challengeMode === "SERVER_VERIFIED_WHEN_ADAPTED"
              ? "服务端判分待启用"
              : definition.challengeMode === "PROGRESS_ONLY"
              ? "仅演示 / 对比"
              : "边界验证后开放挑战"
          }}</a-tag
        >
      </div>
    </header>

    <a-alert type="info" class="demo-note">
      当前 18
      项均为运行演示：步骤与指标由可复算的纯函数生成。完成一次完整运行会写入普通学习活动记录，但不写判题结果或掌握证据；只有服务端执行并验证边界/并列规则后的算法才会开放“可判分挑战”。OPT
      维持不写测评掌握事件的边界。
    </a-alert>

    <div class="demo-workspace">
      <section class="stage-pane" aria-label="动画与步骤舞台">
        <div class="stage-card">
          <div class="card-head">
            <div>
              <h3>动画 / 步骤舞台</h3>
              <p class="stage-sub">
                {{ definition.shortTitle }} · 动画由当前步骤的真值状态与事件驱动
              </p>
            </div>
            <span class="step-count oslab-mono"
              >{{ player.activeStep }} / {{ player.steps.length }} 步</span
            >
          </div>

          <template v-if="hasRun">
            <div class="playback-toolbar">
              <a-button
                size="small"
                :disabled="!canStepBack"
                @click="onStepBack"
                >上一步</a-button
              >
              <a-button
                size="small"
                :disabled="!canStepForward"
                @click="onStepForward"
                >单步</a-button
              >
              <a-button
                size="small"
                :disabled="player.dirty"
                @click="isPlaying ? onPause() : onPlay()"
                >{{ isPlaying ? "暂停" : "自动播放" }}</a-button
              >
              <a-button size="small" @click="onReset">重置</a-button>
              <span
                class="speed-group"
                aria-label="播放速度"
                title="速度范围 0.1×–4.0×，步长 0.1×"
              >
                <a-button
                  size="mini"
                  aria-label="降低播放速度"
                  :disabled="!canDecreaseSpeed"
                  @click="adjustSpeed(-1)"
                  >−</a-button
                >
                <span class="speed-value oslab-mono">{{ speedText }}×</span>
                <a-button
                  size="mini"
                  aria-label="提高播放速度"
                  :disabled="!canIncreaseSpeed"
                  @click="adjustSpeed(1)"
                  >＋</a-button
                >
                <a-button
                  v-for="preset in PLAYBACK_SPEEDS"
                  :key="preset"
                  size="mini"
                  :type="player.speed === preset ? 'primary' : 'outline'"
                  @click="setSpeedPreset(preset)"
                  >{{ preset }}×</a-button
                >
              </span>
            </div>

            <a-slider
              class="playback-slider"
              :model-value="player.activeStep"
              :min="0"
              :max="player.steps.length"
              :step="1"
              :disabled="player.dirty"
              :format-tooltip="sliderTooltip"
              @change="onScrub"
            />

            <a-alert v-if="player.dirty" type="warning" class="dirty-alert">
              配置已修改，当前结果已失效：不能继续播放，也不能记录演示完成；请重新运行。
            </a-alert>

            <div class="stage-screen" :style="stageStyle">
              <!-- CPU 调度动画 -->
              <div
                v-if="definition.category === 'cpu'"
                class="visual-block cpu-visual"
                aria-label="CPU 调度动画"
              >
                <div class="visual-summary">
                  <span
                    >时间 <b class="oslab-mono">{{ cpuState.time }}</b></span
                  >
                  <span v-if="cpuState.process" class="running-now">
                    运行
                    <b :style="{ color: ganttColor(cpuState.process) }">{{
                      cpuState.process
                    }}</b>
                    <span class="oslab-mono"
                      >[{{ cpuState.start }},{{ cpuState.end }})</span
                    >
                  </span>
                  <span v-else class="muted">{{
                    currentStep ? "CPU 空闲" : "尚未开始"
                  }}</span>
                  <span
                    v-if="cpuState.level !== undefined"
                    class="level-badge oslab-mono"
                    >Q{{ cpuState.level }}</span
                  >
                  <a-tag v-if="cpuPreemption" color="orange" size="small"
                    >{{ cpuPreemptionLabel }} {{ cpuPreemption }}</a-tag
                  >
                  <span v-if="currentEvent" class="event-pill">{{
                    eventKindLabel(currentEvent.kind)
                  }}</span>
                </div>

                <div class="gantt-axis" aria-hidden="true">
                  <span
                    v-for="tick in cpuAxisTicks"
                    :key="tick.value"
                    class="axis-tick oslab-mono"
                    :style="{ left: tick.left + '%' }"
                    >{{ tick.value }}</span
                  >
                </div>
                <div class="gantt-track" aria-label="CPU 时间轴">
                  <div
                    v-for="(segment, index) in visibleTimeline"
                    :key="`${segment.start}-${segment.end}-${index}`"
                    class="gantt-segment"
                    :class="{
                      idle: segment.name === '空闲',
                      'is-new': index === visibleTimeline.length - 1,
                      'is-split': segment.queueLevel !== undefined,
                    }"
                    :style="{
                      width: ganttWidth(segment),
                      background:
                        segment.name === '空闲'
                          ? 'var(--oslab-panel)'
                          : ganttColor(segment.name),
                    }"
                    :title="`${segment.name} [${segment.start},${segment.end})${
                      segment.queueLevel !== undefined
                        ? ` · Q${segment.queueLevel}`
                        : ''
                    }`"
                  >
                    <strong>{{ segment.name }}</strong>
                    <small v-if="ganttShowLabel(segment)"
                      >{{ segment.start }}–{{ segment.end
                      }}<template v-if="segment.queueLevel !== undefined">
                        · Q{{ segment.queueLevel }}</template
                      ></small
                    >
                  </div>
                  <div v-if="!visibleTimeline.length" class="gantt-empty">
                    尚未展示执行段；单步或自动播放后按真实时间轴增长。
                  </div>
                </div>

                <div class="cpu-lanes">
                  <div class="lane">
                    <span class="lane-label">CPU</span>
                    <Transition name="thread-swap" mode="out-in">
                      <span
                        :key="`${cpuState.process || 'idle'}-${
                          player.activeStep
                        }`"
                        class="cpu-thread"
                        :class="{ idle: !cpuState.process }"
                        :style="{
                          '--thread-color': cpuState.process
                            ? ganttColor(cpuState.process)
                            : 'var(--oslab-line)',
                        }"
                        >{{
                          cpuState.process
                            ? `${cpuState.process} 运行中`
                            : currentStep
                            ? "空闲"
                            : "等待开始"
                        }}</span
                      >
                    </Transition>
                  </div>
                  <div class="lane">
                    <span class="lane-label">已完成</span>
                    <TransitionGroup name="chip" tag="div" class="chip-row">
                      <span
                        v-for="name in cpuCompleted"
                        :key="name"
                        class="cpu-chip done"
                        >{{ name }}</span
                      >
                    </TransitionGroup>
                    <span v-if="!cpuCompleted.length" class="muted">无</span>
                  </div>
                </div>

                <div class="ready-block">
                  <div
                    v-for="queue in cpuQueues"
                    :key="queue.key"
                    class="queue-line"
                  >
                    <span class="queue-label">{{ queue.label }}</span>
                    <TransitionGroup name="chip" tag="div" class="chip-row">
                      <span
                        v-for="name in queue.names"
                        :key="name"
                        class="cpu-chip ready"
                        :style="{
                          borderColor: ganttColor(name),
                          color: ganttColor(name),
                        }"
                        >{{ name }}</span
                      >
                    </TransitionGroup>
                    <span v-if="!queue.names.length" class="muted">空</span>
                  </div>
                </div>

                <p v-if="currentEvent" class="visual-caption">
                  {{ eventKindLabel(currentEvent.kind) }}
                  <template v-if="currentEvent.from || currentEvent.to">
                    ：{{ currentEvent.from || "—" }} →
                    {{ currentEvent.to || "—" }}
                  </template>
                  <template v-if="currentEvent.duration !== undefined">
                    · 时长 {{ currentEvent.duration }}</template
                  >
                </p>
              </div>

              <!-- 页面置换动画 -->
              <div
                v-else-if="definition.category === 'page'"
                class="visual-block page-visual"
                aria-label="页面置换动画"
              >
                <div class="visual-summary">
                  <span
                    >访问页
                    <b class="oslab-mono">{{
                      pageState.page === null ? "—" : pageState.page
                    }}</b></span
                  >
                  <Transition name="verdict" mode="out-in">
                    <span
                      :key="`${player.activeStep}-${pageState.fault}`"
                      class="verdict"
                      :class="pageState.fault ? 'fault' : 'hit'"
                      >{{
                        pageState.page === null
                          ? "等待访问"
                          : pageState.fault
                          ? "缺页"
                          : "命中"
                      }}</span
                    >
                  </Transition>
                  <span v-if="pageState.evicted !== null"
                    >淘汰
                    <b class="oslab-mono">{{ pageState.evicted }}</b></span
                  >
                  <span
                    >缺页 <b class="oslab-mono">{{ pageState.faults }}</b> ·
                    命中率
                    <b class="oslab-mono">{{ pageState.hitRate }}%</b></span
                  >
                </div>

                <div class="page-sequence" aria-label="访问序列">
                  <span
                    v-for="(visit, index) in pageVisits"
                    :key="index"
                    class="visit-chip oslab-mono"
                    :class="{
                      current: index === pageVisits.length - 1,
                      fault: visit.fault,
                    }"
                    :title="`第 ${index + 1} 次访问 ${visit.page}：${
                      visit.fault ? '缺页' : '命中'
                    }`"
                    >{{ visit.page }}</span
                  >
                  <span v-if="!pageVisits.length" class="muted"
                    >尚未访问；运行后按访问序列逐项推进。</span
                  >
                </div>

                <div class="page-stage">
                  <div class="frame-row" aria-label="物理帧位">
                    <div
                      v-for="slot in pageSlotCount"
                      :key="slot"
                      class="frame-slot"
                      :class="{
                        filled: pageState.frames[slot - 1] !== undefined,
                        active:
                          pageState.page !== null &&
                          pageState.frames[slot - 1] === pageState.page,
                        fault:
                          pageState.fault &&
                          pageState.page !== null &&
                          pageState.frames[slot - 1] === pageState.page,
                      }"
                    >
                      <span class="slot-index oslab-mono">F{{ slot }}</span>
                      <Transition name="page-swap" mode="out-in">
                        <span
                          :key="pageState.frames[slot - 1] ?? 'empty'"
                          class="frame-value oslab-mono"
                          >{{ pageState.frames[slot - 1] ?? "·" }}</span
                        >
                      </Transition>
                    </div>
                  </div>
                  <Transition name="evict">
                    <div
                      v-if="pageState.evicted !== null"
                      :key="`${player.activeStep}-${pageState.evicted}`"
                      class="evicted-chip"
                    >
                      淘汰页 {{ pageState.evicted }}
                    </div>
                  </Transition>
                </div>

                <div class="resident-row">
                  <span class="lane-label">驻留顺序</span>
                  <TransitionGroup name="chip" tag="div" class="chip-row">
                    <span
                      v-for="(resident, index) in pageState.frames"
                      :key="resident"
                      class="page-chip"
                      :class="{ current: resident === pageState.page }"
                      >{{ resident
                      }}<small v-if="index === 0">队首</small></span
                    >
                  </TransitionGroup>
                  <span v-if="!pageState.frames.length" class="muted">空</span>
                </div>

                <p class="visual-caption">
                  帧位按驻留顺序填充，不代表固定物理帧编号；LRU
                  命中时队列会重排。
                </p>
              </div>

              <!-- 连续内存分配动画 -->
              <div
                v-else-if="definition.category === 'allocation'"
                class="visual-block allocation-visual"
                aria-label="连续内存分配动画"
              >
                <div class="visual-summary">
                  <span v-if="allocationState.action"
                    >操作： <b>{{ allocationState.action }}</b></span
                  >
                  <span v-else class="muted">初始状态：整块空闲</span>
                  <a-tag
                    v-if="allocationState.success !== undefined"
                    :color="allocationState.success ? 'green' : 'red'"
                    >{{ allocationState.success ? "成功" : "失败" }}</a-tag
                  >
                  <span
                    >空闲
                    <b class="oslab-mono">{{ allocationState.freeTotal }}</b>
                    · 最大空闲
                    <b class="oslab-mono">{{
                      allocationState.largestFree
                    }}</b></span
                  >
                  <span v-if="isNextFit" class="event-pill"
                    >指针
                    <b class="oslab-mono">{{
                      allocationState.pointer
                    }}</b></span
                  >
                </div>

                <div class="memory-scale">
                  <div class="memory-bar" aria-label="内存分区">
                    <TransitionGroup name="block" tag="div" class="block-layer">
                      <div
                        v-for="block in allocationState.blocks"
                        :key="blockKey(block)"
                        class="memory-block"
                        :class="{
                          free: block.process === null,
                          'is-new': isAllocatedBlock(block),
                          'is-freed': isFreedBlock(block),
                        }"
                        :style="{
                          width: blockWidth(block),
                          background:
                            block.process === null
                              ? 'var(--oslab-panel)'
                              : blockColor(block.process),
                        }"
                        :title="`${block.process || '空闲'} [${block.start},${
                          block.start + block.size
                        }) · ${block.size}`"
                      >
                        <strong>{{ block.process || "空闲" }}</strong>
                        <small v-if="blockLabelVisible(block)"
                          >{{ block.start }}–{{ block.start + block.size }} ·
                          {{ block.size }}</small
                        >
                      </div>
                    </TransitionGroup>
                    <div
                      v-if="allocationState.allocated"
                      :key="`alloc-${player.activeStep}`"
                      class="range-outline allocated"
                      :style="rangeStyle(allocationState.allocated)"
                    ></div>
                    <div
                      v-if="allocationState.freed"
                      :key="`free-${player.activeStep}`"
                      class="range-outline freed"
                      :style="rangeStyle(allocationState.freed)"
                    ></div>
                    <div
                      v-if="isNextFit"
                      class="allocation-pointer"
                      :style="{
                        left: allocationPointerPercent + '%',
                      }"
                      :title="`Next Fit 指针 ${allocationState.pointer}`"
                    >
                      <span>指针</span>
                    </div>
                  </div>
                  <div class="address-axis" aria-hidden="true">
                    <span
                      v-for="tick in allocationTicks"
                      :key="tick"
                      class="axis-tick oslab-mono"
                      :style="{
                        left: (tick / allocationMemorySize) * 100 + '%',
                      }"
                      >{{ tick }}</span
                    >
                  </div>
                </div>

                <div
                  v-if="allocationState.operationType === 'ALLOC'"
                  class="alloc-search"
                >
                  <span class="lane-label"
                    >候选空闲区（{{ allocationState.candidates.length }}）</span
                  >
                  <TransitionGroup name="chip" tag="div" class="chip-row">
                    <span
                      v-for="candidate in allocationState.candidates"
                      :key="candidate.start"
                      class="free-chip"
                      :class="{
                        chosen:
                          allocationState.victim !== null &&
                          candidate.start === allocationState.victim.start,
                      }"
                      >[{{ candidate.start }},{{
                        candidate.start + candidate.size
                      }})</span
                    >
                  </TransitionGroup>
                  <span v-if="!allocationState.candidates.length" class="muted"
                    >没有容量足够的空闲区</span
                  >
                </div>
                <div
                  v-else-if="
                    allocationState.operationType === 'FREE' &&
                    allocationState.freed
                  "
                  class="alloc-search"
                >
                  <span class="lane-label">回收范围</span>
                  <span class="free-chip chosen"
                    >[{{ allocationState.freed.start }},{{
                      allocationState.freed.start + allocationState.freed.size
                    }})</span
                  >
                  <span class="muted">{{
                    allocationState.merged
                      ? "已与相邻空闲区合并"
                      : "无相邻空闲区可合并"
                  }}</span>
                </div>
                <p v-if="allocationState.message" class="visual-caption">
                  {{ allocationState.message }}
                </p>
                <p class="visual-caption">
                  分区宽度与地址成比例；分配时切分空闲区，回收时立即合并相邻空闲区。
                </p>
              </div>

              <!-- 磁盘调度动画 -->
              <div
                v-else
                class="visual-block disk-visual"
                aria-label="磁盘调度动画"
              >
                <div class="visual-summary">
                  <span
                    >磁头 <b class="oslab-mono">{{ diskState.head }}</b></span
                  >
                  <span v-if="diskState.request !== null"
                    >目标磁道
                    <b class="oslab-mono">{{ diskState.request }}</b></span
                  >
                  <span
                    >本次移动
                    <b class="oslab-mono">{{ diskState.movement }}</b> · 累计
                    <b class="oslab-mono">{{ diskState.totalSeek }}</b></span
                  >
                  <span class="direction-badge">{{
                    diskState.direction === "DOWN" ? "↓ 小磁道" : "↑ 大磁道"
                  }}</span>
                  <a-tag
                    v-if="diskState.actionType === 'BOUNDARY'"
                    color="orange"
                    >到达端点</a-tag
                  >
                  <a-tag v-if="diskState.actionType === 'JUMP'" color="blue"
                    >回跳（不计寻道）</a-tag
                  >
                </div>

                <div class="disk-track">
                  <div class="disk-axis" aria-hidden="true">
                    <span
                      v-for="tick in diskTicks"
                      :key="tick"
                      class="axis-tick oslab-mono"
                      :style="{ left: diskTrackPercent(tick) + '%' }"
                      >{{ tick }}</span
                    >
                  </div>
                  <div class="disk-rail">
                    <span
                      v-for="tick in diskTicks"
                      :key="`tick-${tick}`"
                      class="disk-tick"
                      :style="{ left: diskTrackPercent(tick) + '%' }"
                    ></span>
                    <span
                      v-for="marker in diskMarkers"
                      :key="marker.key"
                      class="disk-mark"
                      :class="{
                        served: marker.served,
                        current: marker.current,
                      }"
                      :style="{ left: diskTrackPercent(marker.track) + '%' }"
                      :title="`磁道 ${marker.track}${
                        marker.served ? ' · 已服务' : ' · 待服务'
                      }`"
                    ></span>
                    <span
                      v-if="diskTravel"
                      :key="`travel-${player.activeStep}`"
                      class="disk-travel"
                      :class="{
                        boundary: diskTravel.boundary,
                        jump: diskTravel.jump,
                        reverse: diskTravel.reverse,
                      }"
                      :style="diskTravel.style"
                    ></span>
                    <span
                      class="disk-head"
                      :class="{ jump: diskState.actionType === 'JUMP' }"
                      :style="{ left: diskHeadPercent + '%' }"
                    >
                      <span class="disk-head-label oslab-mono">{{
                        diskState.head
                      }}</span>
                    </span>
                  </div>
                </div>

                <div class="disk-order">
                  <span class="lane-label">服务顺序</span>
                  <TransitionGroup name="chip" tag="div" class="chip-row">
                    <span
                      v-for="(track, index) in diskState.order"
                      :key="index"
                      class="disk-chip"
                      >{{ track }}</span
                    >
                  </TransitionGroup>
                  <span v-if="!diskState.order.length" class="muted">尚无</span>
                </div>
                <div class="disk-remaining">
                  <span class="lane-label">未服务</span>
                  <span v-if="!diskState.remaining.length" class="muted"
                    >无</span
                  >
                  <span
                    v-for="(track, index) in diskState.remaining"
                    :key="index"
                    class="remaining-chip oslab-mono"
                    >{{ track }}</span
                  >
                </div>
                <p v-if="currentEvent" class="visual-caption">
                  {{ eventKindLabel(currentEvent.kind) }}：{{
                    currentEvent.from
                  }}
                  → {{ currentEvent.to }} · 移动
                  {{ diskState.movement }}
                </p>
              </div>
            </div>

            <p class="stage-honesty">
              动画只读取当前步骤的真值状态与事件；暂停、单步、重置或切换算法会立即停止推进，不会自行计算第二套结果。
            </p>

            <div class="step-list" aria-label="步骤记录">
              <button
                v-for="step in revealedSteps"
                :key="step.index"
                type="button"
                class="step-item"
                :class="[
                  'tone-' + (step.tone || 'info'),
                  { latest: step.index === player.activeStep },
                ]"
                @click="seekTo(step.index)"
              >
                <span class="step-index oslab-mono">{{ step.index }}</span>
                <span class="step-body"
                  ><strong>{{ step.title }}</strong
                  ><small>{{ step.detail }}</small></span
                >
              </button>
              <div v-if="!revealedSteps.length" class="step-empty">
                运行后点击“单步”或“自动播放”查看每一步。
              </div>
            </div>

            <div v-if="allStepsShown" class="metrics">
              <h4>结果指标</h4>
              <div class="metric-grid">
                <div
                  v-for="metric in result?.metrics || []"
                  :key="metric.label"
                  class="metric-card"
                >
                  <span>{{ metric.label }}</span>
                  <strong class="oslab-mono">{{ metric.value }}</strong>
                </div>
              </div>
            </div>
            <a-alert v-else type="info" class="metrics-hint">
              已展示 {{ player.activeStep }} / {{ player.steps.length }}
              步；全部步骤展示后显示结果指标，避免提前泄露后续答案。
            </a-alert>
          </template>

          <div v-else class="stage-empty">
            <span class="oslab-state-title">配置输入后运行演示</span>
            <span class="stage-default oslab-mono"
              >默认示例：{{ defaultInputSummary }}</span
            >
            <span class="oslab-state-hint"
              >运行只生成可复算的步骤，不写学习证据；逐步播放不会改变算法结果。</span
            >
          </div>
        </div>
      </section>

      <aside class="side-pane" aria-label="输入、过程说明与实现代码">
        <section class="demo-input-card">
          <div class="card-head">
            <h3>输入与规则</h3>
            <a-button size="small" @click="resetFromDefinition(false)"
              >恢复示例</a-button
            >
          </div>

          <a-alert v-if="initialInputError" type="warning" class="input-error">
            深链参数无效，已使用可编辑示例：{{ initialInputError }}
          </a-alert>

          <!-- CPU 输入 -->
          <template v-if="definition.category === 'cpu'">
            <div class="process-table" role="table" aria-label="进程参数">
              <div
                class="process-row process-row-head"
                :class="{ 'with-priority': definition.requiresPriority }"
                role="row"
              >
                <span>进程</span><span>到达</span><span>运行</span>
                <span v-if="definition.requiresPriority">优先级</span
                ><span></span>
              </div>
              <div
                v-for="(process, index) in cpuProcesses"
                :key="index"
                class="process-row"
                :class="{ 'with-priority': definition.requiresPriority }"
                role="row"
              >
                <a-input
                  v-model="process.name"
                  size="small"
                  aria-label="进程名"
                />
                <a-input-number
                  v-model="process.arrival"
                  size="small"
                  :min="0"
                  :max="999"
                  :precision="0"
                />
                <a-input-number
                  v-model="process.burst"
                  size="small"
                  :min="1"
                  :max="100"
                  :precision="0"
                />
                <a-input-number
                  v-if="definition.requiresPriority"
                  v-model="process.priority"
                  size="small"
                  :min="0"
                  :max="99"
                  :precision="0"
                />
                <a-button
                  size="mini"
                  status="danger"
                  :disabled="cpuProcesses.length <= 1"
                  @click="cpuProcesses.splice(index, 1)"
                  >删除</a-button
                >
              </div>
            </div>
            <div class="input-actions">
              <a-button size="small" @click="addProcess">添加进程</a-button>
              <span
                v-if="definition.usesQuantum && !isMlfq"
                class="inline-field"
              >
                <span>时间片</span>
                <a-input-number
                  v-model="quantum"
                  size="small"
                  :min="1"
                  :max="20"
                  :precision="0"
                />
              </span>
              <span v-if="definition.requiresPriority" class="input-hint"
                >数字越小优先级越高</span
              >
            </div>
            <div v-if="isMlfq" class="mlfq-config">
              <div class="input-line">
                <span class="input-label">队列数</span>
                <a-input-number
                  v-model="mlfqQueueCount"
                  size="small"
                  :min="2"
                  :max="6"
                  :precision="0"
                  @change="resizeMlfqQueues"
                />
                <span class="input-hint"
                  >默认时间片按 q / 2q / 4q 递增，可逐级修改</span
                >
              </div>
              <div class="mlfq-slices">
                <label
                  v-for="(slice, index) in mlfqSlices.slice(0, mlfqQueueCount)"
                  :key="index"
                  class="mlfq-slice"
                >
                  <span class="oslab-mono">Q{{ index }}</span>
                  <a-input-number
                    v-model="mlfqSlices[index]"
                    size="small"
                    :min="1"
                    :max="20"
                    :precision="0"
                  />
                </label>
              </div>
            </div>
          </template>

          <!-- 连续内存分配输入 -->
          <template v-else-if="definition.category === 'allocation'">
            <div class="partition-editor">
              <div class="input-line">
                <span class="input-label">初始空闲分区</span>
                <span class="input-hint"
                  >总大小 {{ partitionTotal }} · 最多 12 块</span
                >
                <a-button
                  size="mini"
                  :disabled="initialPartitions.length >= 12"
                  @click="addPartition"
                  >添加分区</a-button
                >
              </div>
              <div
                v-for="(size, index) in initialPartitions"
                :key="index"
                class="partition-row"
              >
                <span class="partition-label">分区 {{ index + 1 }}</span>
                <a-input-number
                  v-model="initialPartitions[index]"
                  size="small"
                  :min="1"
                  :max="4096"
                  :precision="0"
                />
                <a-button
                  size="mini"
                  status="danger"
                  :disabled="initialPartitions.length <= 1"
                  @click="removePartition(index)"
                  >删除</a-button
                >
              </div>
            </div>
            <a-textarea
              v-model="allocationText"
              class="operation-text"
              :auto-size="{ minRows: 5, maxRows: 10 }"
              placeholder="每行一条操作：P1 100 或 FREE P1"
            />
            <p class="input-hint">
              分区从地址 0 开始连续排列；每行格式：<code>进程名 大小</code
              >（分配）或 <code>FREE 进程名</code>（回收）；# 后为注释。
            </p>
          </template>

          <!-- 页面置换输入 -->
          <template v-else-if="definition.category === 'page'">
            <div class="input-line">
              <span class="input-label">物理块数</span>
              <a-input-number
                v-model="pageFrames"
                size="small"
                :min="1"
                :max="60"
                :precision="0"
              />
            </div>
            <div class="input-line">
              <span class="input-label">访问序列</span>
              <a-input
                v-model="pageSequenceText"
                size="small"
                placeholder="如 7,0,1,2,0,3"
              />
            </div>
            <p class="input-hint">
              物理块 1–60；页号 0–9999，最多 200 步；逗号、空格或中文逗号分隔。
            </p>
          </template>

          <!-- 磁盘调度输入 -->
          <template v-else>
            <div class="input-line">
              <span class="input-label">磁头位置</span>
              <a-input-number
                v-model="diskHead"
                size="small"
                :min="DISK_MIN_TRACK"
                :max="DISK_MAX_TRACK"
                :precision="0"
              />
              <span class="input-label">扫描方向</span>
              <a-select
                v-model="diskDirection"
                size="small"
                style="width: 96px"
              >
                <a-option value="UP">向大磁道</a-option>
                <a-option value="DOWN">向小磁道</a-option>
              </a-select>
            </div>
            <div class="input-line">
              <span class="input-label">请求磁道</span>
              <a-input
                v-model="diskRequestsText"
                size="small"
                placeholder="如 98,183,37,122"
              />
            </div>
            <p class="input-hint">
              磁道范围 {{ DISK_MIN_TRACK }}–{{ DISK_MAX_TRACK }}，最多 20
              个请求；FCFS/SSTF 不使用扫描方向。
            </p>
          </template>

          <a-alert v-if="inputError" type="error" class="input-error">
            {{ inputError }}
          </a-alert>

          <div class="run-actions">
            <a-button type="primary" :loading="false" @click="run">
              运行演示
            </a-button>
            <span v-if="player.dirty" class="dirty-hint"
              >输入已修改，当前结果仍绑定上次运行</span
            >
          </div>

          <div class="rules">
            <h4>规则与边界</h4>
            <ul>
              <li v-for="rule in definition.rules" :key="rule">{{ rule }}</li>
              <li v-for="note in result?.notes || []" :key="note">
                {{ note }}
              </li>
            </ul>
          </div>

          <details class="analysis-panel">
            <summary>算法解析：思想与特点</summary>
            <p v-for="line in analysisLines" :key="line">{{ line }}</p>
          </details>
        </section>

        <section class="explain-card">
          <div class="card-head">
            <h3>过程说明</h3>
            <span v-if="currentStep" class="step-badge oslab-mono"
              >第 {{ currentStep.index }} 步</span
            >
          </div>
          <template v-if="currentStep">
            <strong class="explain-title">{{ currentStep.title }}</strong>
            <p class="explain-detail">{{ currentStep.detail }}</p>
            <dl class="event-meta">
              <div v-if="currentStep.time !== undefined">
                <dt>时间</dt>
                <dd class="oslab-mono">{{ currentStep.time }}</dd>
              </div>
              <div v-if="currentStep.event">
                <dt>事件</dt>
                <dd class="oslab-mono">{{ currentStep.event.kind }}</dd>
              </div>
              <div v-if="currentStep.event && currentStep.event.objectId">
                <dt>对象</dt>
                <dd>{{ currentStep.event.objectId }}</dd>
              </div>
              <div
                v-if="
                  currentStep.event &&
                  (currentStep.event.from || currentStep.event.to)
                "
              >
                <dt>轨迹</dt>
                <dd class="oslab-mono">
                  {{ currentStep.event.from || "—" }} →
                  {{ currentStep.event.to || "—" }}
                </dd>
              </div>
              <div
                v-if="
                  currentStep.event && currentStep.event.duration !== undefined
                "
              >
                <dt>时长</dt>
                <dd class="oslab-mono">{{ currentStep.event.duration }}</dd>
              </div>
            </dl>
          </template>
          <p v-else class="muted">
            运行后逐步展示当前步骤的对象、事件与对应实现代码行；没有可靠映射时不会猜测高亮。
          </p>
        </section>

        <section class="code-card">
          <div class="card-head">
            <h3>算法实现</h3>
            <span
              class="mapping-state"
              :class="{ mapped: activeCodeLines.length > 0 }"
              >{{ mappingLabel }}</span
            >
          </div>
          <p v-if="codeMap" class="code-kind oslab-mono">
            {{ codeMap.sourceFile }} · {{ codeMap.symbol }}
          </p>
          <p class="code-kind">真实 TypeScript 实现摘录 · 步骤高亮对应源码行</p>
          <ol v-if="codeMap" ref="codeListRef" class="code-lines">
            <li
              v-for="(line, index) in codeMap.lines"
              :key="index"
              :class="{ active: activeCodeLines.includes(index + 1) }"
            >
              <span class="line-no oslab-mono">{{ index + 1 }}</span>
              <code>{{ line }}</code>
            </li>
          </ol>
          <p v-else class="muted">该算法暂未登记实现代码映射。</p>
          <p v-if="currentStep && !activeCodeLines.length" class="no-mapping">
            本步骤无实现代码行映射。
          </p>
        </section>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import {
  CATEGORY_LABELS,
  runDemo,
  validateDemoInput,
  type AlgorithmDefinition,
  type AllocationInput,
  type CpuInput,
  type CpuProcessInput,
  type DemoInput,
  type DemoResult,
  type DiskInput,
  type PageInput,
} from "@/simulation";
import {
  formatAllocationOperations,
  parseAllocationOperations,
} from "@/simulation/allocation";
import { parsePageSequence } from "@/simulation/page";
import {
  DISK_MAX_TRACK,
  DISK_MIN_TRACK,
  parseDiskRequests,
} from "@/simulation/disk";
import {
  annotateResult,
  getCodeMap,
  resolveActiveCodeLines,
} from "@/simulation/codeMaps";
import {
  canComplete,
  createPlayerState,
  load,
  markDirty,
  MAX_PLAYBACK_SPEED,
  MIN_PLAYBACK_SPEED,
  pause,
  play,
  PLAYBACK_SPEEDS,
  reset,
  seek,
  setSpeed,
  SPEED_STEP,
  stepBack,
  stepForward,
  stepIntervalMs,
  type PlayerState,
} from "@/simulation/playback";

interface CpuViewState {
  time: number;
  process: string | null;
  start: number;
  end: number;
  ready: string[];
  queues?: string[][];
  level?: number;
}
interface PageViewState {
  frames: number[];
  page: number | null;
  fault: boolean;
  evicted: number | null;
  faults: number;
  hitRate: number;
}
interface AllocationBlock {
  start: number;
  size: number;
  process: string | null;
}
interface AllocationRange {
  start: number;
  size: number;
}
interface AllocationViewState {
  blocks: AllocationBlock[];
  pointer: number;
  freeTotal: number;
  largestFree: number;
  action?: string;
  success?: boolean;
  message?: string;
  operationType?: "ALLOC" | "FREE" | null;
  process?: string;
  request?: { process: string; size: number } | null;
  candidates: AllocationRange[];
  victim: AllocationRange | null;
  allocated: (AllocationRange & { process: string }) | null;
  freed: (AllocationRange & { process: string }) | null;
  merged: boolean;
}
interface DiskViewState {
  from: number | null;
  head: number;
  target: number | null;
  request: number | null;
  movement: number;
  totalSeek: number;
  order: number[];
  remaining: number[];
  direction: "UP" | "DOWN";
  actionType?: string;
}
interface CpuAxisTick {
  value: number;
  left: number;
}
interface CpuQueueView {
  key: string;
  label: string;
  level: number | null;
  names: string[];
}
interface PageVisitView {
  page: number;
  fault: boolean;
}
interface DiskMarkerView {
  key: string;
  track: number;
  served: boolean;
  current: boolean;
}
interface DiskTravelView {
  style: Record<string, string>;
  boundary: boolean;
  jump: boolean;
  reverse: boolean;
}

// eslint-disable-next-line no-undef
const props = defineProps<{
  definition: AlgorithmDefinition;
  /** 课程活动或深链带入的输入；算法归类不匹配时回退到示例 */
  initialInput?: DemoInput;
  /** 深链参数存在但无效时的明确错误 */
  initialInputError?: string;
}>();
// eslint-disable-next-line no-undef
const emit = defineEmits<{
  (event: "progress", allRevealed: boolean): void;
  (
    event: "completed",
    payload: {
      algorithmKey: string;
      title: string;
      input: Record<string, unknown> | null;
      resultSummary: Record<string, unknown>;
    }
  ): void;
}>();

const categoryLabel = computed(
  () => CATEGORY_LABELS[props.definition.category]
);

const analysisLines = computed(() => {
  const configured = props.definition.analysis;
  if (Array.isArray(configured) && configured.length) return configured;
  return [props.definition.description, ...props.definition.rules];
});

const defaultInputSummary = computed(() => {
  const input = props.definition.defaultInput;
  if (props.definition.category === "cpu") {
    const cpu = input as CpuInput;
    const slices = cpu.mlfqQueues?.length
      ? ` · 队列时间片 ${cpu.mlfqQueues.join(" / ")}`
      : "";
    return `${cpu.processes.length} 个进程${slices}`;
  }
  if (props.definition.category === "allocation") {
    const allocation = input as AllocationInput;
    const partitions = allocation.initialPartitions?.length
      ? allocation.initialPartitions
      : [allocation.memorySize];
    return `${partitions.length} 块初始空闲分区 · ${allocation.operations.length} 条操作`;
  }
  if (props.definition.category === "page") {
    const page = input as PageInput;
    return `${page.frames} 个物理块 · ${page.sequence.length} 次访问`;
  }
  const disk = input as DiskInput;
  return `磁头 ${disk.head} · ${disk.requests.length} 个请求 · ${
    disk.direction === "DOWN" ? "向小磁道" : "向大磁道"
  }`;
});

// ---------- 输入状态 ----------
const cpuProcesses = ref<CpuProcessInput[]>([]);
const quantum = ref(2);
const mlfqQueueCount = ref(3);
const mlfqSlices = ref<number[]>([1, 2, 4]);
const initialPartitions = ref<number[]>([]);
const partitionTotal = computed(() =>
  initialPartitions.value.reduce((sum, size) => sum + (Number(size) || 0), 0)
);
const allocationText = ref("");
const pageFrames = ref(3);
const pageSequenceText = ref("");
const diskHead = ref(53);
const diskRequestsText = ref("");
const diskDirection = ref<"UP" | "DOWN">("UP");
const inputError = ref("");

// ---------- 播放状态机 ----------
const player = ref<PlayerState>(createPlayerState());
const result = ref<DemoResult | null>(null);
let timer: number | null = null;
// 每次“运行演示”生成一个新序号；同一运行反复播放到末尾只上报一次。
let runSequence = 0;
let reportedRun = 0;

const clearTimer = () => {
  if (timer !== null) {
    window.clearInterval(timer);
    timer = null;
  }
};

const scheduleTimer = () => {
  clearTimer();
  if (player.value.playState !== "playing") return;
  timer = window.setInterval(() => {
    player.value = stepForward(player.value);
    if (player.value.playState !== "playing") clearTimer();
  }, stepIntervalMs(player.value.speed));
};

const isMlfq = computed(() => props.definition.key === "cpu.mlfq");

const defaultMlfqSlices = (count: number, base: number) =>
  Array.from({ length: count }, (_, index) =>
    Math.min(20, Math.max(1, Math.round(base * 2 ** index)))
  );

const resizeMlfqQueues = (count: number | string) => {
  const next = Math.min(6, Math.max(2, Number(count) || 2));
  const current = mlfqSlices.value.slice(0, next);
  const base = current[0] || quantum.value || 1;
  mlfqSlices.value = [
    ...current,
    ...defaultMlfqSlices(next, base).slice(current.length),
  ];
  mlfqQueueCount.value = next;
};

const addPartition = () => {
  if (initialPartitions.value.length >= 12) return;
  initialPartitions.value.push(120);
};

const removePartition = (index: number) => {
  if (initialPartitions.value.length <= 1) return;
  initialPartitions.value.splice(index, 1);
};

const resetFromDefinition = (useInitialInput = true) => {
  clearTimer();
  result.value = null;
  player.value = createPlayerState(player.value.speed);
  inputError.value = "";
  const initial =
    useInitialInput && !props.initialInputError
      ? props.initialInput
      : undefined;
  const categoryMatches =
    initial &&
    ((props.definition.category === "cpu" && "processes" in initial) ||
      (props.definition.category === "allocation" && "operations" in initial) ||
      (props.definition.category === "page" && "frames" in initial) ||
      (props.definition.category === "disk" && "requests" in initial));
  const input = JSON.parse(
    JSON.stringify(categoryMatches ? initial : props.definition.defaultInput)
  ) as DemoInput;
  if (props.definition.category === "cpu") {
    const cpu = input as CpuInput;
    cpuProcesses.value = cpu.processes.map((process) => ({ ...process }));
    quantum.value = cpu.quantum ?? 2;
    if (props.definition.key === "cpu.mlfq") {
      const configured =
        Array.isArray(cpu.mlfqQueues) && cpu.mlfqQueues.length >= 2
          ? cpu.mlfqQueues.slice(0, 6)
          : defaultMlfqSlices(3, cpu.quantum ?? 1);
      mlfqQueueCount.value = configured.length;
      mlfqSlices.value = configured.slice();
      quantum.value = configured[0];
    }
  } else if (props.definition.category === "allocation") {
    const allocation = input as AllocationInput;
    const partitions =
      Array.isArray(allocation.initialPartitions) &&
      allocation.initialPartitions.length
        ? allocation.initialPartitions.slice()
        : [allocation.memorySize];
    initialPartitions.value = partitions;
    allocationText.value = formatAllocationOperations(allocation.operations);
  } else if (props.definition.category === "page") {
    const page = input as PageInput;
    pageFrames.value = page.frames;
    pageSequenceText.value = page.sequence.join(",");
  } else {
    const disk = input as DiskInput;
    diskHead.value = disk.head;
    diskRequestsText.value = disk.requests.join(",");
    diskDirection.value = disk.direction;
  }
};

const addProcess = () => {
  cpuProcesses.value.push({
    name: `P${cpuProcesses.value.length + 1}`,
    arrival: 0,
    burst: 1,
    priority: 1,
  });
};

const buildInput = (): DemoInput => {
  if (props.definition.category === "cpu") {
    const cpu: CpuInput = {
      processes: cpuProcesses.value.map((process) => ({
        name: process.name,
        arrival: Number(process.arrival),
        burst: Number(process.burst),
        priority: Number(process.priority ?? 0),
      })),
      quantum: Number(quantum.value),
    };
    if (isMlfq.value) {
      const slices = mlfqSlices.value
        .slice(0, mlfqQueueCount.value)
        .map((value) => Number(value));
      cpu.mlfqQueues = slices;
      cpu.quantum = slices[0];
    }
    return cpu;
  }
  if (props.definition.category === "allocation") {
    const partitions = initialPartitions.value.map((size) => Number(size));
    return {
      memorySize: partitions.reduce((sum, size) => sum + size, 0),
      initialPartitions: partitions,
      operations: parseAllocationOperations(allocationText.value),
    };
  }
  if (props.definition.category === "page") {
    return {
      frames: Number(pageFrames.value),
      sequence: parsePageSequence(pageSequenceText.value),
    };
  }
  return {
    head: Number(diskHead.value),
    requests: parseDiskRequests(diskRequestsText.value),
    direction: diskDirection.value,
  };
};

const safeBuildInput = (): DemoInput | null => {
  try {
    return buildInput();
  } catch {
    return null;
  }
};

const run = () => {
  inputError.value = "";
  let input: DemoInput;
  try {
    input = buildInput();
  } catch (error) {
    inputError.value =
      error instanceof Error ? error.message : "输入格式不正确";
    return;
  }
  const message = validateDemoInput(props.definition, input);
  if (message) {
    inputError.value = message;
    return;
  }
  try {
    const truth = runDemo(props.definition, input);
    if (!truth.steps.length) {
      inputError.value = "该输入没有产生可展示的步骤";
      return;
    }
    const demo = annotateResult(props.definition, input, truth);
    clearTimer();
    runSequence += 1;
    result.value = demo;
    player.value = load(player.value, input, demo);
  } catch (error) {
    inputError.value = error instanceof Error ? error.message : "运行失败";
  }
};

const isPlaying = computed(() => player.value.playState === "playing");
const hasRun = computed(
  () => player.value.steps.length > 0 && player.value.runConfig !== null
);
const canStepBack = computed(
  () => player.value.activeStep > 0 && !player.value.dirty
);
const canStepForward = computed(
  () =>
    player.value.steps.length > 0 &&
    player.value.activeStep < player.value.steps.length &&
    !player.value.dirty
);

const onPlay = () => {
  player.value = play(player.value);
  scheduleTimer();
};
const onPause = () => {
  clearTimer();
  player.value = pause(player.value);
};
const onStepForward = () => {
  clearTimer();
  player.value = pause(player.value);
  player.value = stepForward(player.value);
};
const onStepBack = () => {
  clearTimer();
  player.value = pause(player.value);
  player.value = stepBack(player.value);
};
const onReset = () => {
  clearTimer();
  player.value = reset(player.value);
};
const onScrub = (value: number | number[]) => {
  if (Array.isArray(value)) return;
  clearTimer();
  player.value = pause(player.value);
  player.value = seek(player.value, value);
};
const seekTo = (index: number) => {
  clearTimer();
  player.value = pause(player.value);
  player.value = seek(player.value, index);
};
const applySpeed = (value: number) => {
  player.value = setSpeed(player.value, value);
  if (player.value.playState === "playing") scheduleTimer();
};
const setSpeedPreset = (value: number) => applySpeed(value);
const adjustSpeed = (direction: number) => {
  const next = Math.min(
    MAX_PLAYBACK_SPEED,
    Math.max(
      MIN_PLAYBACK_SPEED,
      Math.round((player.value.speed + direction * SPEED_STEP) * 10) / 10
    )
  );
  applySpeed(next);
};
const canDecreaseSpeed = computed(
  () => player.value.speed > MIN_PLAYBACK_SPEED + 0.0001
);
const canIncreaseSpeed = computed(
  () => player.value.speed < MAX_PLAYBACK_SPEED - 0.0001
);
const speedText = computed(() => player.value.speed.toFixed(1));
const sliderTooltip = (value?: number) => `第 ${value ?? 0} 步`;

const currentStep = computed(() =>
  player.value.activeStep > 0
    ? player.value.steps[player.value.activeStep - 1] || null
    : null
);
const revealedSteps = computed(() =>
  player.value.steps.slice(0, player.value.activeStep)
);
const allStepsShown = computed(
  () =>
    player.value.steps.length > 0 &&
    player.value.activeStep >= player.value.steps.length
);
const canCompleteRun = computed(() => canComplete(player.value));
const currentState = computed<Record<string, unknown>>(
  () => currentStep.value?.state || result.value?.initialState || {}
);

const previousStep = computed(() =>
  player.value.activeStep > 1
    ? player.value.steps[player.value.activeStep - 2] || null
    : null
);
const currentEvent = computed(() => currentStep.value?.event);

const cpuState = computed<CpuViewState>(() => ({
  time: Number(currentState.value.time ?? 0),
  process: (currentState.value.process as string | null) ?? null,
  start: Number(currentState.value.start ?? 0),
  end: Number(currentState.value.end ?? 0),
  ready: (currentState.value.ready as string[]) || [],
  queues: currentState.value.queues as string[][] | undefined,
  level: currentState.value.level as number | undefined,
}));

const pageState = computed<PageViewState>(() => ({
  frames: (currentState.value.frames as number[]) || [],
  page: (currentState.value.page as number | null) ?? null,
  fault: Boolean(currentState.value.fault),
  evicted: (currentState.value.evicted as number | null) ?? null,
  faults: Number(currentState.value.faults ?? 0),
  hitRate: Number(currentState.value.hitRate ?? 0),
}));

const allocationState = computed<AllocationViewState>(() => ({
  blocks: (currentState.value.blocks as AllocationBlock[]) || [],
  pointer: Number(currentState.value.pointer ?? 0),
  freeTotal: Number(currentState.value.freeTotal ?? 0),
  largestFree: Number(currentState.value.largestFree ?? 0),
  action: currentState.value.action as string | undefined,
  success: currentState.value.success as boolean | undefined,
  message: currentState.value.message as string | undefined,
  operationType: currentState.value.operationType as
    | "ALLOC"
    | "FREE"
    | null
    | undefined,
  process: currentState.value.process as string | undefined,
  request: currentState.value.request as
    | { process: string; size: number }
    | null
    | undefined,
  candidates:
    (currentState.value.candidates as AllocationRange[] | undefined) || [],
  victim: (currentState.value.victim as AllocationRange | null) ?? null,
  allocated:
    (currentState.value.allocated as
      | (AllocationRange & { process: string })
      | null) ?? null,
  freed:
    (currentState.value.freed as
      | (AllocationRange & { process: string })
      | null) ?? null,
  merged: Boolean(currentState.value.merged),
}));

const diskState = computed<DiskViewState>(() => ({
  from:
    currentState.value.from === null || currentState.value.from === undefined
      ? null
      : Number(currentState.value.from),
  head: Number(currentState.value.head ?? 0),
  target:
    currentState.value.target === null ||
    currentState.value.target === undefined
      ? null
      : Number(currentState.value.target),
  request:
    currentState.value.request === null ||
    currentState.value.request === undefined
      ? null
      : Number(currentState.value.request),
  movement: Number(currentState.value.movement ?? 0),
  totalSeek: Number(currentState.value.totalSeek ?? 0),
  order: (currentState.value.order as number[]) || [],
  remaining: (currentState.value.remaining as number[]) || [],
  direction:
    (currentState.value.direction as "UP" | "DOWN" | undefined) === "DOWN"
      ? "DOWN"
      : "UP",
  actionType: currentState.value.actionType as string | undefined,
}));

const visibleTimeline = computed(() => {
  if (!result.value) return [];
  if (props.definition.category !== "cpu") return [];
  if (!player.value.activeStep) return [];
  const end = cpuState.value.end;
  return (result.value.timeline || []).filter((segment) => segment.end <= end);
});

const cpuAxisSpan = computed(() => {
  const last = visibleTimeline.value[visibleTimeline.value.length - 1];
  return Math.max(1, last ? last.end : 0);
});
const cpuAxisTicks = computed<CpuAxisTick[]>(() => {
  const span = cpuAxisSpan.value;
  const values = new Set<number>();
  for (const fraction of [0, 0.25, 0.5, 0.75, 1]) {
    values.add(Math.round(span * fraction));
  }
  return [...values]
    .sort((a, b) => a - b)
    .map((value) => ({ value, left: (value / span) * 100 }));
});
const ganttWidth = (segment: { start: number; end: number }) => {
  const duration = Math.max(0, segment.end - segment.start);
  return `${(duration / cpuAxisSpan.value) * 100}%`;
};
const ganttShowLabel = (segment: { start: number; end: number }) =>
  (segment.end - segment.start) / cpuAxisSpan.value >= 0.09;

const cpuQueues = computed<CpuQueueView[]>(() => {
  if (cpuState.value.queues) {
    return cpuState.value.queues.map((names, level) => ({
      key: `q${level}`,
      label: `Q${level}`,
      level,
      names: names.slice(),
    }));
  }
  return [
    {
      key: "ready",
      label: "就绪队列",
      level: null,
      names: cpuState.value.ready.slice(),
    },
  ];
});

const cpuCompletionEnd = computed(() => {
  const map = new Map<string, number>();
  for (const segment of result.value?.timeline || []) {
    if (segment.name === "空闲") continue;
    map.set(segment.name, Math.max(map.get(segment.name) ?? 0, segment.end));
  }
  return map;
});
const cpuCompleted = computed(() => {
  const end = cpuState.value.end;
  return [...cpuCompletionEnd.value.entries()]
    .filter(([, completedAt]) => completedAt <= end)
    .sort((a, b) => a[1] - b[1] || a[0].localeCompare(b[0]))
    .map(([name]) => name);
});

const cpuPreemption = computed(() => {
  if (props.definition.category !== "cpu") return null;
  if (
    !["cpu.priority_preemptive", "cpu.mlfq", "cpu.rr"].includes(
      props.definition.key
    )
  ) {
    return null;
  }
  const previous = previousStep.value?.state as CpuViewState | undefined;
  const previousProcess = previous?.process;
  if (!previousProcess) return null;
  if (cpuState.value.process === previousProcess) return null;
  if (Number(previous?.end ?? 0) !== cpuState.value.start) return null;
  const completedAt = cpuCompletionEnd.value.get(previousProcess);
  if (completedAt === undefined || completedAt <= cpuState.value.end) {
    return null;
  }
  return previousProcess;
});
const cpuPreemptionLabel = computed(() => {
  if (!cpuPreemption.value) return "";
  if (props.definition.key === "cpu.rr") return "时间片用尽";
  if (props.definition.key === "cpu.mlfq") return "让出 CPU";
  return "被抢占";
});

const pageVisits = computed<PageVisitView[]>(() =>
  player.value.steps.slice(0, player.value.activeStep).map((step) => {
    const state = step.state as {
      page?: number | null;
      fault?: boolean;
    };
    return {
      page: Number(state.page ?? 0),
      fault: Boolean(state.fault),
    };
  })
);

const pageSlotCount = computed(
  () => (player.value.runConfig as PageInput | null)?.frames ?? 0
);
const allocationMemorySize = computed(() =>
  Math.max(
    1,
    Number((player.value.runConfig as AllocationInput | null)?.memorySize ?? 1)
  )
);
const isNextFit = computed(
  () => props.definition.key === "allocation.next_fit"
);
const blockWidth = (block: AllocationBlock) =>
  `${Math.max(0, (block.size / allocationMemorySize.value) * 100)}%`;
const blockLabelVisible = (block: AllocationBlock) =>
  block.size / allocationMemorySize.value >= 0.1;
const blockKey = (block: AllocationBlock) =>
  `${block.start}-${block.process ?? "free"}`;
const isAllocatedBlock = (block: AllocationBlock) =>
  Boolean(
    allocationState.value.allocated &&
      allocationState.value.allocated.start === block.start &&
      allocationState.value.allocated.process === block.process
  );
const isFreedBlock = (block: AllocationBlock) => {
  const freed = allocationState.value.freed;
  if (!freed || block.process !== null) return false;
  return (
    block.start < freed.start + freed.size &&
    block.start + block.size > freed.start
  );
};
const rangeStyle = (range: { start: number; size: number }) => ({
  left: `${Math.max(0, (range.start / allocationMemorySize.value) * 100)}%`,
  width: `${Math.max(0, (range.size / allocationMemorySize.value) * 100)}%`,
});
const allocationPointerPercent = computed(() =>
  Math.min(
    100,
    Math.max(
      0,
      (allocationState.value.pointer / allocationMemorySize.value) * 100
    )
  )
);
const allocationTicks = computed(() => {
  const size = allocationMemorySize.value;
  const values = new Set<number>();
  for (const fraction of [0, 0.25, 0.5, 0.75, 1]) {
    values.add(Math.round(size * fraction));
  }
  return [...values].sort((a, b) => a - b);
});

const diskRunRequests = computed(
  () => (player.value.runConfig as DiskInput | null)?.requests ?? []
);
const diskTrackPercent = (track: number) =>
  Math.min(
    100,
    Math.max(
      0,
      ((Number(track) - DISK_MIN_TRACK) / (DISK_MAX_TRACK - DISK_MIN_TRACK)) *
        100
    )
  );
const diskHeadPercent = computed(() => diskTrackPercent(diskState.value.head));
const diskTicks = [1, 40, 80, 120, 160, 200];
const diskMarkers = computed<DiskMarkerView[]>(() => {
  const servedCounts = new Map<number, number>();
  for (const track of diskState.value.order) {
    servedCounts.set(track, (servedCounts.get(track) ?? 0) + 1);
  }
  const seen = new Map<number, number>();
  return diskRunRequests.value.map((track, index) => {
    const occurrence = seen.get(track) ?? 0;
    seen.set(track, occurrence + 1);
    return {
      key: `${index}-${track}`,
      track,
      served: occurrence < (servedCounts.get(track) ?? 0),
      current: diskState.value.request === track,
    };
  });
});
const diskTravel = computed<DiskTravelView | null>(() => {
  const from = diskState.value.from;
  const to = diskState.value.head;
  if (from === null || from === to) return null;
  const left = Math.min(from, to);
  const startPercent = diskTrackPercent(left);
  const endPercent = diskTrackPercent(Math.max(from, to));
  return {
    style: {
      left: `${startPercent}%`,
      width: `${Math.max(0, endPercent - startPercent)}%`,
    },
    boundary: diskState.value.actionType === "BOUNDARY",
    jump: diskState.value.actionType === "JUMP",
    reverse: to < from,
  };
});

const eventLabels: Record<string, string> = {
  "cpu.run": "进程上 CPU",
  "cpu.idle": "CPU 空闲",
  "cpu.complete": "进程完成",
  "cpu.preempt": "被抢占",
  "cpu.demote": "降级队列",
  "cpu.yield": "让出 CPU",
  "page.hit": "页面命中",
  "page.load": "页面装入",
  "page.replace": "换出并装入",
  "allocation.alloc": "分区分配",
  "allocation.alloc_fail": "分配失败",
  "allocation.free": "分区回收",
  "allocation.free_fail": "回收失败",
  "disk.seek": "磁头寻道",
  "disk.boundary": "到达端点",
  "disk.jump": "回跳",
};
const eventKindLabel = (kind: string) => eventLabels[kind] || kind;

// 动画只消费步骤状态/事件，用 CSS 过渡与关键帧呈现；不新增定时器、rAF 或第二套算法。
const animMs = computed(() =>
  Math.max(
    180,
    Math.min(600, Math.round(stepIntervalMs(player.value.speed) * 0.55))
  )
);
const stageStyle = computed(() => ({ "--demo-anim": `${animMs.value}ms` }));

// ---------- 代码联动 ----------
const codeMap = computed(() => getCodeMap(props.definition.key));
const activeCodeLines = computed(() => {
  const step = currentStep.value;
  if (!step) return [];
  if (step.activeCodeLines) return step.activeCodeLines;
  if (step.codeLines) return step.codeLines;
  return resolveActiveCodeLines(props.definition.key, step.event);
});
const mappingLabel = computed(() => {
  if (!currentStep.value) return "等待运行";
  return activeCodeLines.value.length
    ? `高亮第 ${activeCodeLines.value.join("、")} 行`
    : "本步骤无代码行映射";
});

// 代码面板滚到当前高亮行，避免只看到函数顶部而误以为高亮错位。
const codeListRef = ref<HTMLElement | null>(null);
const scrollActiveCodeLine = () => {
  const list = codeListRef.value;
  const card = list?.closest(".code-card") as HTMLElement | null;
  const active = list?.querySelector("li.active") as HTMLElement | null;
  if (!list || !card || !active) return;
  if (card.scrollHeight <= card.clientHeight + 4) return;
  const cardRect = card.getBoundingClientRect();
  const lineRect = active.getBoundingClientRect();
  if (
    lineRect.top < cardRect.top + 36 ||
    lineRect.bottom > cardRect.bottom - 16
  ) {
    card.scrollTop += lineRect.top - cardRect.top - 72;
  }
};
watch(activeCodeLines, async () => {
  await nextTick();
  scrollActiveCodeLine();
});

// 输入编辑后立即废止本次运行：停表、禁止继续播放与完成
const editorSignature = computed(() => {
  const input = safeBuildInput();
  return input ? JSON.stringify(input) : "__invalid__";
});
watch(editorSignature, () => {
  const state = player.value;
  if (!state.runConfig || state.dirty) return;
  const current = safeBuildInput();
  if (!current || JSON.stringify(current) !== JSON.stringify(state.runConfig)) {
    clearTimer();
    player.value = markDirty(state);
  }
});

watch(
  () => [
    props.definition.key,
    props.initialInput ? JSON.stringify(props.initialInput) : "",
    props.initialInputError || "",
  ],
  () => resetFromDefinition(),
  { immediate: true }
);

watch(canCompleteRun, (value) => {
  emit("progress", value);
  if (!value || reportedRun === runSequence) return;
  reportedRun = runSequence;
  const runInput = player.value.runConfig
    ? (JSON.parse(JSON.stringify(player.value.runConfig)) as Record<
        string,
        unknown
      >)
    : null;
  emit("completed", {
    algorithmKey: props.definition.key,
    title: props.definition.title,
    input: runInput,
    resultSummary: {
      stepCount: result.value?.steps.length ?? 0,
      metrics: result.value?.metrics ?? [],
    },
  });
});

const ganttColors = [
  "#5b8ff9",
  "#5ad8a6",
  "#f6bd16",
  "#e8684a",
  "#6dc8ec",
  "#9270ca",
];
const colorIndex = (name: string) => {
  let hash = 0;
  for (let index = 0; index < name.length; index += 1) {
    hash = (hash * 31 + name.charCodeAt(index)) | 0;
  }
  return Math.abs(hash) % ganttColors.length;
};
const ganttColor = (name: string) => ganttColors[colorIndex(name)];
const blockColor = (name: string) => ganttColors[colorIndex(name)];

onBeforeUnmount(clearTimer);
</script>

<style scoped>
.demo-player {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-4);
}
.demo-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--oslab-space-3);
}
.demo-eyebrow {
  margin: 0 0 2px;
  color: var(--oslab-primary);
  font-size: var(--oslab-text-small);
}
.demo-title {
  margin: 0;
  font-size: 22px;
  color: var(--oslab-ink);
}
.demo-description {
  margin: 6px 0 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.demo-badges {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.demo-note {
  margin: 0;
}
.demo-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  grid-template-areas:
    "input code"
    "stage code"
    "explain code";
  gap: var(--oslab-space-4);
  align-items: start;
}
.stage-pane {
  grid-area: stage;
  min-width: 0;
}
.demo-input-card {
  grid-area: input;
}
.explain-card {
  grid-area: explain;
}
.code-card {
  grid-area: code;
  position: sticky;
  top: 16px;
  max-height: calc(100vh - 150px);
  overflow: auto;
}
.code-card .code-lines {
  max-height: none;
}
.stage-card,
.demo-input-card,
.explain-card,
.code-card {
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  background: var(--oslab-panel);
  padding: var(--oslab-space-4);
}
.stage-card {
  min-height: 420px;
}
.side-pane {
  display: contents;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--oslab-space-2);
  margin-bottom: var(--oslab-space-3);
}
.card-head h3 {
  margin: 0;
  font-size: 16px;
  color: var(--oslab-ink);
}
.stage-sub {
  margin: 4px 0 0;
  color: var(--oslab-muted-strong);
  font-size: 12px;
}
.stage-empty {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 340px;
  justify-content: center;
  text-align: center;
}
.process-table {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.process-row {
  display: grid;
  grid-template-columns: minmax(48px, 1fr) 58px 58px auto;
  gap: 6px;
  align-items: center;
}
.process-row.with-priority {
  grid-template-columns: minmax(42px, 1fr) 54px 54px 54px auto;
}
.process-row-head {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.input-actions {
  display: flex;
  align-items: center;
  gap: var(--oslab-space-2);
  flex-wrap: wrap;
  margin-top: var(--oslab-space-3);
}
.inline-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.mlfq-config {
  margin-top: var(--oslab-space-3);
  padding: var(--oslab-space-2) var(--oslab-space-3);
  border: 1px solid var(--oslab-line-soft);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel-soft);
}
.mlfq-slices {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oslab-space-2);
  margin-top: var(--oslab-space-2);
}
.mlfq-slice {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.partition-editor {
  margin-bottom: var(--oslab-space-2);
}
.partition-row {
  display: flex;
  align-items: center;
  gap: var(--oslab-space-2);
  margin-bottom: 6px;
}
.partition-label {
  min-width: 64px;
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
}
.speed-group {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.speed-value {
  min-width: 42px;
  text-align: center;
  color: var(--oslab-ink-soft);
  font-size: 12px;
}
.analysis-panel {
  margin-top: var(--oslab-space-3);
  padding-top: var(--oslab-space-2);
  border-top: 1px solid var(--oslab-line-soft);
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.analysis-panel summary {
  cursor: pointer;
  color: var(--oslab-primary);
  font-weight: 500;
}
.analysis-panel p {
  margin: 6px 0 0;
  line-height: 1.7;
}
.stage-default {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.code-kind {
  margin: -4px 0 var(--oslab-space-2);
  color: var(--oslab-muted-strong);
  font-size: 11px;
}
.input-line {
  display: flex;
  align-items: center;
  gap: var(--oslab-space-2);
  flex-wrap: wrap;
  margin-bottom: var(--oslab-space-2);
}
.input-label {
  min-width: 56px;
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
}
.input-hint,
.muted {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.operation-text {
  margin-top: var(--oslab-space-2);
  font-family: var(--oslab-font-mono, monospace);
}
.input-error {
  margin-top: var(--oslab-space-2);
}
.run-actions {
  display: flex;
  align-items: center;
  gap: var(--oslab-space-2);
  flex-wrap: wrap;
  margin-top: var(--oslab-space-3);
}
.dirty-hint {
  color: var(--oslab-review-text);
  font-size: var(--oslab-text-small);
}
.rules {
  margin-top: var(--oslab-space-4);
  padding-top: var(--oslab-space-3);
  border-top: 1px solid var(--oslab-line-soft);
}
.rules h4 {
  margin: 0 0 6px;
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
}
.rules ul {
  margin: 0;
  padding-left: 18px;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 1.7;
}
.step-count {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.playback-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2);
}
.playback-slider {
  margin: var(--oslab-space-2) 0 var(--oslab-space-3);
}
.dirty-alert {
  margin-bottom: var(--oslab-space-3);
}
.stage-screen {
  --demo-anim: 480ms;
}
.visual-block {
  padding: var(--oslab-space-3);
  border: 1px solid var(--oslab-line-soft);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel-soft);
  min-width: 0;
  overflow: hidden;
}
.visual-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2) var(--oslab-space-3);
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
  margin-bottom: var(--oslab-space-3);
}
.running-now b {
  font-size: 15px;
}
.event-pill,
.level-badge,
.direction-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill, 999px);
  background: var(--oslab-panel);
  color: var(--oslab-muted-strong);
  font-size: 11px;
  white-space: nowrap;
}
.gantt-axis {
  position: relative;
  height: 16px;
  margin: 2px 0 0;
}
.axis-tick {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  color: var(--oslab-muted-strong);
  font-size: 10px;
  line-height: 16px;
  white-space: nowrap;
}
.axis-tick:first-child {
  transform: none;
}
.axis-tick:last-child {
  transform: translateX(-100%);
}
.gantt-track {
  display: flex;
  align-items: stretch;
  gap: 2px;
  min-height: 54px;
  overflow-x: auto;
  padding: 2px 0;
  border-top: 1px solid var(--oslab-line-soft);
  border-bottom: 1px solid var(--oslab-line-soft);
}
.gantt-segment {
  min-width: 3px;
  padding: 4px 5px;
  border-radius: 3px;
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  font-size: 11px;
  line-height: 1.3;
  overflow: hidden;
  transition: width var(--demo-anim) ease,
    background-color var(--demo-anim) ease;
}
.gantt-segment.idle {
  color: var(--oslab-muted-strong);
  border: 1px dashed var(--oslab-line);
}
.gantt-segment.is-new {
  animation: gantt-grow var(--demo-anim) ease-out;
  transform-origin: left center;
}
.gantt-segment small {
  opacity: 0.85;
  white-space: nowrap;
}
.gantt-segment strong {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
@keyframes gantt-grow {
  from {
    transform: scaleX(0.04);
    opacity: 0.2;
  }
  to {
    transform: scaleX(1);
    opacity: 1;
  }
}
.gantt-empty {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  align-self: center;
  padding: 0 8px;
}
.cpu-lanes {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}
.lane {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.lane-label {
  min-width: 64px;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  white-space: nowrap;
}
.chip-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}
.cpu-thread {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border: 1px solid var(--thread-color, var(--oslab-line));
  border-left-width: 4px;
  border-radius: var(--oslab-radius-pill, 999px);
  background: var(--oslab-panel);
  color: var(--oslab-ink);
  font-size: 12px;
}
.cpu-thread.idle {
  color: var(--oslab-muted-strong);
}
.cpu-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 9px;
  border-radius: var(--oslab-radius-pill, 999px);
  font-size: 12px;
  line-height: 1.5;
}
.cpu-chip.ready {
  border: 1px solid;
  background: var(--oslab-panel);
}
.cpu-chip.done {
  border: 1px dashed var(--oslab-line);
  color: var(--oslab-muted-strong);
  background: var(--oslab-panel);
}
.ready-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--oslab-line-soft);
}
.queue-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}
.queue-label {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  white-space: nowrap;
}
.visual-caption {
  margin: 8px 0 0;
  color: var(--oslab-muted-strong);
  font-size: 12px;
  line-height: 1.6;
}
.thread-swap-enter-active,
.thread-swap-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}
.thread-swap-enter-from,
.thread-swap-leave-to {
  opacity: 0;
  transform: translateY(5px);
}
.verdict {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: var(--oslab-radius-pill, 999px);
  font-size: 12px;
  font-weight: 600;
}
.verdict.hit {
  color: var(--oslab-mastered, #4f8875);
  background: rgba(79, 136, 117, 0.12);
}
.verdict.fault {
  color: var(--oslab-weak, #a9695d);
  background: rgba(169, 105, 93, 0.12);
}
.verdict-enter-active,
.verdict-leave-active {
  transition: opacity 160ms ease, transform 160ms ease;
}
.verdict-enter-from,
.verdict-leave-to {
  opacity: 0;
  transform: scale(0.8);
}
.page-sequence {
  display: flex;
  flex-wrap: nowrap;
  gap: 4px;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 4px;
  margin-bottom: var(--oslab-space-3);
}
.visit-chip {
  flex: 0 0 auto;
  min-width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
  border: 1px solid var(--oslab-line-soft);
  border-radius: 6px;
  background: var(--oslab-panel);
  color: var(--oslab-ink-soft);
  font-size: 11px;
}
.visit-chip.fault {
  border-color: var(--oslab-weak, #a9695d);
  color: var(--oslab-weak, #a9695d);
}
.visit-chip.current {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 1px;
  color: var(--oslab-primary);
}
.page-stage {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-3);
}
.frame-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  max-height: 250px;
  overflow: auto;
}
.frame-slot {
  position: relative;
  width: 64px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--oslab-line);
  border-radius: var(--oslab-radius-md);
  color: var(--oslab-muted-strong);
  font-weight: 600;
  background: var(--oslab-panel);
  transition: border-color var(--demo-anim) ease,
    background-color var(--demo-anim) ease;
}
.slot-index {
  position: absolute;
  top: 2px;
  left: 5px;
  font-size: 9px;
  color: var(--oslab-muted-strong);
}
.frame-value {
  font-size: 19px;
  line-height: 1;
}
.frame-slot.filled {
  border-style: solid;
  color: var(--oslab-ink);
}
.frame-slot.active {
  border-color: var(--oslab-primary);
  box-shadow: inset 0 0 0 1px var(--oslab-primary);
  color: var(--oslab-primary);
}
.frame-slot.fault {
  background: var(--oslab-review-soft, #f6eddf);
}
.page-swap-enter-active,
.page-swap-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}
.page-swap-enter-from {
  opacity: 0;
  transform: translateY(-10px) scale(0.8);
}
.page-swap-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.8);
}
.evicted-chip {
  padding: 6px 10px;
  border: 1px dashed var(--oslab-weak, #a9695d);
  border-radius: var(--oslab-radius-md);
  color: var(--oslab-weak, #a9695d);
  background: var(--oslab-panel);
  font-size: 12px;
}
.evict-enter-active,
.evict-leave-active {
  transition: opacity 220ms ease, transform 220ms ease;
}
.evict-enter-from,
.evict-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}
.resident-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: var(--oslab-space-3);
}
.page-chip {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  padding: 2px 9px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill, 999px);
  background: var(--oslab-panel);
  color: var(--oslab-ink-soft);
  font-size: 12px;
}
.page-chip small {
  color: var(--oslab-muted-strong);
  font-size: 9px;
}
.page-chip.current {
  border-color: var(--oslab-primary);
  color: var(--oslab-primary);
}
.memory-scale {
  position: relative;
  padding-bottom: 18px;
}
.memory-bar {
  position: relative;
  height: 64px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-md);
  overflow: hidden;
  background: var(--oslab-panel);
}
.block-layer {
  position: absolute;
  inset: 0;
  display: flex;
}
.memory-block {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-width: 2px;
  box-sizing: border-box;
  padding: 4px 3px;
  color: #fff;
  font-size: 11px;
  line-height: 1.4;
  border-right: 1px solid rgba(255, 255, 255, 0.35);
  overflow: hidden;
  transition: width var(--demo-anim) ease,
    background-color var(--demo-anim) ease;
}
.memory-block.free {
  color: var(--oslab-muted-strong);
  border-right: 1px dashed var(--oslab-line);
}
.memory-block small {
  opacity: 0.85;
  white-space: nowrap;
}
.memory-block.is-new {
  animation: block-split var(--demo-anim) ease-out;
}
.memory-block.is-freed {
  animation: block-merge var(--demo-anim) ease-out;
}
@keyframes block-split {
  from {
    opacity: 0.2;
    transform: scaleX(0.35);
  }
  to {
    opacity: 1;
    transform: scaleX(1);
  }
}
@keyframes block-merge {
  from {
    opacity: 0.25;
    transform: scaleY(0.4);
  }
  to {
    opacity: 1;
    transform: scaleY(1);
  }
}
.block-enter-active,
.block-leave-active,
.block-move {
  transition: opacity var(--demo-anim) ease, transform var(--demo-anim) ease;
}
.block-enter-from,
.block-leave-to {
  opacity: 0;
  transform: scaleX(0.5);
}
.range-outline {
  position: absolute;
  top: 0;
  bottom: 0;
  border: 2px solid var(--oslab-primary);
  box-sizing: border-box;
  pointer-events: none;
}
.range-outline.allocated {
  border-color: rgba(255, 255, 255, 0.9);
  animation: range-flash var(--demo-anim) ease-out;
}
.range-outline.freed {
  border-style: dashed;
  border-color: var(--oslab-weak, #a9695d);
}
@keyframes range-flash {
  from {
    opacity: 0.1;
    transform: scaleX(0.4);
  }
  to {
    opacity: 1;
    transform: scaleX(1);
  }
}
.allocation-pointer {
  position: absolute;
  top: -2px;
  bottom: -2px;
  width: 2px;
  background: var(--oslab-weak, #a9695d);
  transition: left var(--demo-anim) ease;
  pointer-events: none;
}
.allocation-pointer span {
  position: absolute;
  bottom: 2px;
  left: 50%;
  transform: translateX(-50%);
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--oslab-weak, #a9695d);
  color: #fff;
  font-size: 10px;
  white-space: nowrap;
}
.address-axis {
  position: relative;
  height: 16px;
  margin-top: 2px;
}
.alloc-search {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.free-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border: 1px dashed var(--oslab-line);
  border-radius: var(--oslab-radius-pill, 999px);
  background: var(--oslab-panel);
  color: var(--oslab-ink-soft);
  font-size: 11px;
}
.free-chip.chosen {
  border-style: solid;
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
}
.chip-move,
.chip-enter-active,
.chip-leave-active {
  transition: opacity 200ms ease, transform 200ms ease;
}
.chip-enter-from,
.chip-leave-to {
  opacity: 0;
  transform: translateY(-5px) scale(0.9);
}
.disk-track {
  padding: 20px 12px 0;
}
.disk-axis {
  position: relative;
  height: 16px;
}
.disk-rail {
  position: relative;
  height: 64px;
  margin-top: 4px;
}
.disk-rail::before {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  top: 32px;
  height: 2px;
  border-radius: 1px;
  background: var(--oslab-line);
}
.disk-tick {
  position: absolute;
  top: 27px;
  width: 1px;
  height: 12px;
  background: var(--oslab-line-soft);
}
.disk-mark {
  position: absolute;
  top: 24px;
  width: 8px;
  height: 16px;
  margin-left: -4px;
  border-radius: 2px;
  background: var(--oslab-muted-strong);
  opacity: 0.4;
  transition: background-color var(--demo-anim) ease,
    opacity var(--demo-anim) ease, transform var(--demo-anim) ease;
}
.disk-mark.served {
  background: var(--oslab-primary);
  opacity: 1;
}
.disk-mark.current {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 1px;
  transform: translateY(-2px);
}
.disk-travel {
  position: absolute;
  top: 31px;
  height: 4px;
  border-radius: 2px;
  background: var(--oslab-primary);
  opacity: 0.55;
  transform-origin: left center;
  animation: travel-draw var(--demo-anim) ease-out;
}
.disk-travel.reverse {
  transform-origin: right center;
}
.disk-travel.boundary {
  background: #d48806;
}
.disk-travel.jump {
  background: repeating-linear-gradient(
    90deg,
    var(--oslab-primary) 0,
    var(--oslab-primary) 6px,
    transparent 6px,
    transparent 12px
  );
}
@keyframes travel-draw {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}
.disk-head {
  position: absolute;
  top: 14px;
  width: 0;
  height: 0;
  margin-left: -6px;
  border-left: 6px solid transparent;
  border-right: 6px solid transparent;
  border-top: 12px solid var(--oslab-weak, #d4380d);
  transition: left var(--demo-anim) cubic-bezier(0.4, 0, 0.2, 1);
}
.disk-head.jump {
  border-top-color: var(--oslab-primary);
}
.disk-head-label {
  position: absolute;
  top: -16px;
  left: 50%;
  transform: translateX(-50%);
  padding: 0 3px;
  background: var(--oslab-panel-soft);
  color: var(--oslab-ink-soft);
  font-size: 10px;
  line-height: 1.4;
  white-space: nowrap;
}
.disk-order,
.disk-remaining {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: var(--oslab-space-2);
}
.disk-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border: 1px solid var(--oslab-primary);
  border-radius: var(--oslab-radius-pill, 999px);
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
  font-size: 12px;
}
.remaining-chip {
  display: inline-flex;
  align-items: center;
  padding: 1px 7px;
  border: 1px dashed var(--oslab-line);
  border-radius: var(--oslab-radius-pill, 999px);
  color: var(--oslab-muted-strong);
  font-size: 11px;
}
.stage-honesty {
  margin: var(--oslab-space-2) 0 0;
  color: var(--oslab-muted-strong);
  font-size: 12px;
  line-height: 1.6;
}
.step-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: var(--oslab-space-4);
  max-height: 280px;
  overflow-y: auto;
}
.step-item {
  display: flex;
  gap: 10px;
  text-align: left;
  padding: 8px 10px;
  border: 1px solid var(--oslab-line-soft);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel);
  cursor: pointer;
  font: inherit;
}
.step-item.latest {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
}
.step-index {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  min-width: 20px;
}
.step-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.step-body strong {
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
}
.step-body small {
  color: var(--oslab-muted-strong);
  font-size: 12px;
  line-height: 1.6;
}
.step-empty {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  padding: 12px 0;
}
.metrics {
  margin-top: var(--oslab-space-4);
}
.metrics h4 {
  margin: 0 0 8px;
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: var(--oslab-space-2);
}
.metric-card {
  border: 1px solid var(--oslab-line-soft);
  border-radius: var(--oslab-radius-md);
  padding: 8px 10px;
  background: var(--oslab-panel-soft);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.metric-card span {
  color: var(--oslab-muted-strong);
  font-size: 12px;
}
.metric-card strong {
  color: var(--oslab-ink);
  font-size: 16px;
}
.metrics-hint {
  margin-top: var(--oslab-space-4);
}
.step-badge {
  color: var(--oslab-primary);
  font-size: 12px;
}
.explain-title {
  display: block;
  color: var(--oslab-ink);
  font-size: 15px;
  margin-bottom: 6px;
}
.explain-detail {
  margin: 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 1.7;
}
.event-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oslab-space-2) var(--oslab-space-3);
  margin: var(--oslab-space-3) 0 0;
}
.event-meta div {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.event-meta dt {
  color: var(--oslab-muted-strong);
  font-size: 12px;
}
.event-meta dd {
  margin: 0;
  color: var(--oslab-ink-soft);
  font-size: 12px;
}
.mapping-state {
  color: var(--oslab-muted-strong);
  font-size: 12px;
}
.mapping-state.mapped {
  color: var(--oslab-primary);
}
.code-lines {
  list-style: none;
  margin: 0;
  padding: 6px 0;
  max-height: 420px;
  overflow: auto;
  border: 1px solid var(--oslab-line-soft);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel-soft);
}
.code-lines li {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 6px;
  padding: 3px 8px;
  border-left: 3px solid transparent;
}
.code-lines li.active {
  border-left-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
}
.line-no {
  color: var(--oslab-muted-strong);
  font-size: 11px;
  text-align: right;
}
.code-lines code {
  color: var(--oslab-ink-soft);
  font-family: var(--oslab-font-mono, monospace);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.no-mapping {
  margin: var(--oslab-space-2) 0 0;
  color: var(--oslab-review-text);
  font-size: 12px;
}
/* C+B 学习图册 */
.demo-player {
  gap: 16px;
}
.demo-head {
  padding: 16px;
  border-bottom: 1px solid var(--oslab-line-soft);
}
.demo-title {
  font-family: var(--oslab-font-display);
  font-size: 24px;
}
.demo-input-card,
.explain-card,
.code-card,
.stage-card {
  border-radius: var(--oslab-radius-lg);
  background: var(--oslab-panel-soft);
}
.demo-note {
  padding: 9px 11px;
  border-left: 2px solid var(--oslab-annotation);
  border-radius: 0 var(--oslab-radius-sm) var(--oslab-radius-sm) 0;
  background: var(--oslab-annotation-soft);
  color: var(--oslab-ink-soft);
  font-family: var(--oslab-font-display);
  font-size: 12px;
  line-height: 1.55;
}
.code-lines {
  border-radius: var(--oslab-radius-md);
}
.code-lines li.active {
  border-left-color: var(--oslab-primary);
  background: var(--oslab-highlight-soft);
}
.demo-workspace {
  gap: 16px;
}

@media (max-width: 1180px) {
  .demo-workspace {
    grid-template-columns: 1fr;
    grid-template-areas:
      "input"
      "stage"
      "explain"
      "code";
  }
  .code-card {
    position: static;
    max-height: 70vh;
    overflow: auto;
  }
}
@media (max-width: 560px) {
  .process-row,
  .process-row.with-priority {
    grid-template-columns: minmax(0, 1fr) 64px 64px auto;
  }
  .process-row.with-priority {
    grid-template-columns: minmax(0, 1fr) 58px 58px 58px auto;
  }
  .frame-slot {
    width: 54px;
    height: 46px;
  }
  .lane-label {
    min-width: 52px;
  }
  .disk-track {
    padding-left: 6px;
    padding-right: 6px;
  }
  .visual-summary {
    font-size: 12px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .gantt-segment,
  .gantt-segment.is-new,
  .cpu-thread,
  .frame-slot,
  .memory-block,
  .memory-block.is-new,
  .memory-block.is-freed,
  .allocation-pointer,
  .range-outline.allocated,
  .disk-head,
  .disk-travel,
  .disk-mark,
  .thread-swap-enter-active,
  .thread-swap-leave-active,
  .chip-move,
  .chip-enter-active,
  .chip-leave-active,
  .verdict-enter-active,
  .verdict-leave-active,
  .page-swap-enter-active,
  .page-swap-leave-active,
  .evict-enter-active,
  .evict-leave-active,
  .block-enter-active,
  .block-leave-active,
  .block-move {
    animation: none !important;
    transition: none !important;
  }
}
</style>
