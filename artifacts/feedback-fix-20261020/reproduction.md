# OSLab demand2 reproduction evidence (Task A, stage 0)

Date: 2026-10-20  
Branch: `feature/learning-loop-ui-rework`  
Baseline: HEAD `93ccf44` + the 145 uncommitted working-tree paths (snapshot in
`.superpowers/sdd/18-demand2人工反馈修正计划/baseline/`).  
Scope: F1 (node workbench entry) and F4 (graph selection/highlight/identity).
F2 (simulation player) is covered by Task C; F3 (graph visual redesign) is covered
by Task B. No F2/F3 changes are included in this artifact.

No browser automation was used (plan §2.4). Reproduction is source-level plus
deterministic Node/ECharts SSR tests. Manual screenshots remain for L.

## F1 — "进入节点工作台" does not open

### Pre-fix source evidence (baseline before Task A edits)

- Template, `src/views/knowledgeGraph/KnowledgeGraphView.vue:610-618` (line 615):
  `@click="enterNodeWorkspace"`.
- Handler, same file `:2101`:
  `const enterNodeWorkspace = (keyOrName?: string) => {`
  The truthy argument is looked up with
  `nodeByKey.value.get(keyOrName) || nodeByName.value.get(keyOrName)`.
- Routes, `src/router/routes.ts:133-141`: both `/knowledge/node/:nodeName` and
  `/knowledge/concept/:nodeCode` exist and point to `NodeLearningWorkspaceView`.
- The graph double-click path already called `enterNodeWorkspace(key)` with a
  string (`:1832-1835`), so only the right-side button was broken.

### Root cause

Vue binds a component event handler reference without arguments by passing the
event object. Arco `a-button` therefore invokes `enterNodeWorkspace(MouseEvent)`.
The function treats the truthy MouseEvent as a node key, `Map.get(event)` returns
`undefined`, and `if (!node) return;` exits silently. There is no error, no
navigation and no visible feedback.

### Expected result

Opening the workbench for the currently selected node uses:

- `/knowledge/concept/<nodeCode>` when the node has a stable `nodeCode`;
- `/knowledge/node/<nodeName>` only for legacy nodes without a stable code.

The route query must carry `nodeCode` (when available), `sectionCode`,
`unitCode` (when the selected unit maps the node) and `returnTo`.

### Deterministic reproduction added before the fix

Test: `workbench entry uses the current node and a stable nodeKey`.

Pre-fix failure (template contract):

```text
FAIL workbench entry uses the current node and a stable nodeKey
AssertionError: the detail CTA must call enterNodeWorkspace() without an event argument
```

The same test also drives the handler with no argument (expects the selected
node) and with a raw `{ type: "click" }` object (expects rejection, no
navigation). After the Task A fix all assertions pass.

## F4 — selection / highlight / identity corruption

### Confirmed source-level root causes

1. **Path/unit hint shares the selected visual.**
   Baseline `src/components/KnowledgeGraph.vue:321-326`:
   `const selected = (props.selectedId && toId(node.id) === ctx.selectedId) || node.highlight;`
   and `:352-356` applies the same 2px `PRIMARY` border and `fontWeight: 600`.
   `KnowledgeGraphView.vue:1372-1377` injects `highlight: true` for every
   `TEACHES` node of the selected unit. Result: many unit nodes look selected
   (blue border + bold) at once.

2. **Mastery text color is overwritten by selection/dimming.**
   Baseline `KnowledgeGraph.vue:280-288` sets the label color to `MUTED_STRONG`
   or `INK_SOFT`; `status` only controls the left bar/dot. A selected RED node is
   not red.

3. **Identity chain is fragmented.**
   Baseline derived node `id` is `toId(node.id ?? node.name)`
   (`KnowledgeGraph.vue:339`), so `nodeCode` / `stableId` are dropped from the
   ECharts data and from the click payload. The parent stores selection as
   `selectedNodeKey = nodeCode || name` (`KnowledgeGraphView.vue:994-1005`) but
   passes `selectedNode.id` as `selectedId`, and click handlers fall back to
   `node.name`. Graph data id, link endpoints, selection and route query do not
   share one rule. Duplicate display names cannot be distinguished by the
   payload; related-node entries carry only `name`.

4. **Relation entries do not carry a stable key.**
   Baseline `KnowledgeGraphView.vue:564-603` calls
   `selectAndFocus(item.name)`; `selectUnitNode(mapping.nodeCode)` skips the
   name-fallback resolution needed by legacy nodes (`:1950-1952`).

### Deterministically reproduced by tests (pre-fix failures)

Run: `node tests/knowledge-graph-render.js` from `前端代码/osLab-frontend`
before production edits.

```text
FAIL node A to B selection clears A and keeps one adjacency set
  selected label color actual '#37352f' expected '#a9695d'
FAIL unit path hint stays distinct from the selected outline
  path node border actual 'rgba(0, 0, 0, 0.15)' expected '#9fb0c9'
FAIL duplicate display names keep distinct nodeCode identity
  graph data ids actual ['1', '2'] expected ['os.a', 'os.b']
FAIL clicking node B routes to B with one selected detail
  related entry must carry a stable nodeKey
FAIL filtering out the selected node clears selection and detail
  selectedNodeId actual '1' expected 'os.a'
```

The A→B, path-hint, duplicate-identity and relation-entry failures are confirmed
functional/contract defects. The filter test additionally exposed the raw-id vs
nodeKey mismatch; the clearing watcher itself existed, but the selection prop was
not on the unified key.

### Risk item, not deterministically reproduced

`oslabGraphStyle` is registered with `update: "updateVisual"`
(`KnowledgeGraph.vue:44-60`). It merges item models but does not clear ECharts
hover/emphasis state. A hover→click→mouseout residue is therefore a credible
risk, but the SSR test `hover then click then mouseout leaves no emphasis residue`
passed against the unmodified component as well. It is recorded as a risk, not a
confirmed bug. Task A nevertheless inserts an explicit
`dispatchAction({ type: "downplay", seriesIndex: 0 })` before every non-rebuild
style refresh so the state is cleared by construction.

### Expected result

- Exactly one node has the blue selected outline; unit/path hints use a
  low-intensity thin edge and never bold text.
- Node-name color follows `RED`/`GREEN`/`YELLOW`/`GRAY` even while selected.
- A→B (including rapid clicks) leaves only B selected; adjacency follows B.
- Hover→click→mouseout leaves no emphasis residue.
- Duplicate `displayName` with different `nodeCode` opens the clicked node.
- Filtering out the selected node clears selection, detail and adjacency.
- Clicking blank canvas or a relation line emits no selection change.

## Commands used

```text
cd 前端代码/osLab-frontend
node tests/knowledge-graph-render.js
npx eslint src/components/KnowledgeGraph.vue src/views/knowledgeGraph/KnowledgeGraphView.vue tests/knowledge-graph-render.js
```

Stage-0 output is summarized above; the post-fix output is recorded in
`.superpowers/sdd/18-demand2人工反馈修正计划/task-A-report.md`.

## Manual checks still required (L)

- `/graph`: select a unit, confirm TEACHES nodes show only the thin path hint;
  click A then B and confirm only B is selected with its status text color.
- Open the right-side detail and click "进入节点工作台"; confirm the workbench
  URL is `/knowledge/concept/<nodeCode>` and the back link returns to the same
  course/section/unit/node context.
- `/teaching/knowledge` as admin: click nodes, change chapter, add relation,
  delete node and open content editor; confirm maintenance behavior is unchanged.
