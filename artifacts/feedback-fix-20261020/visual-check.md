# Task B manual visual check (L)

Date: 2026-10-20
Environment: local frontend + backend, branch `feature/learning-loop-ui-rework`.
No browser automation is used; L takes screenshots and records pass/fail below.

## Style reference (what the screenshots should show)

| Dimension | Expected |
| --- | --- |
| Node shape | Sharp rectangle, fixed 160 x 56 px (or 160 x 44 without status data); names wrap to at most two lines, long names end with `…` |
| Category | Low-saturation cool tint only: 概念 `#e9eef7`, 算法 `#efecf5`, 功能 `#e9f0f2`, 未标注 `#f1f1ef`; no green/amber/red node fill |
| Mastery | Node-name text and the 10px line below it: 已掌握 `#4f8875`, 待巩固 `#b98239`, 薄弱 `#a9695d`, 未学习 `#8f9994` |
| Selection | Exactly one node: 2px `#2059c9` border + short 3px low-alpha outer ring; name text keeps its mastery color |
| Path hint | Unit TEACHES nodes / remediation path use a 1px `#9fb0c9` edge (no blue selected border); path edges use `#3f66a5` 2.2px short lines, no glow |
| Relations | Dependent blue solid arrow, contain slate dashed, related dotted, implements amber dashed; non-adjacent edges stay visible at 0.45 opacity |
| Legend | Four explanation groups (类别 tint, 掌握 text color, 选中 2px blue outline, 路径 thin edge/line), plus relation samples |

## 1. Desktop `/graph` at 1440 px

- [ ] Log in as a student with mastery data; open `/graph`.
- [ ] Nodes are sharp/fixed-size; short and long names do not change node width.
- [ ] A name longer than two lines ends with `…`; hovering shows the full name, and the right detail panel shows it in full.
- [ ] Category tints are visible but stay low-saturation; they are not green/amber/red and cannot be read as mastery.
- [ ] All four mastery states appear as text colors on names (use fixtures/accounts covering RED/GREEN/YELLOW/GRAY).
- [ ] The 10px status line under each name matches the name color.
- [ ] Click node A: only A gets the 2px blue border + short outer ring; A's name keeps its mastery color.
- [ ] Click node B: A returns to the 1px neutral border with no ring; B is the only selected node.
- [ ] Adjacent edges of B become slightly thicker; all other edges remain readable at low opacity.
- [ ] Select a unit with TEACHES mappings: mapped nodes show only the 1px path edge; they never look like a second selection.
- [ ] Toggle "只看学习路径": path edges are the distinct path blue, not a glow and not the selected-node border.
- [ ] Toggle "关系标签": only relation text appears/disappears; selection, path hints and edge emphasis do not change.
- [ ] Hover a node, click it, move the pointer out: no blue ring, bold text or emphasis residue remains on other nodes.
- [ ] Legend shows all four groups and matches the canvas: tint swatches, colored mastery words, 2px blue outline sample, 1px path sample and path line.
- [ ] The right 340px detail panel does not overlap the canvas; node labels and relation labels stay inside the canvas card.

## 2. Narrow viewport at 390 px

- [ ] Set the viewport to 390 x 844 (device toolbar) and open `/graph`.
- [ ] The page does not scroll horizontally; the detail card stacks below the graph card.
- [ ] The canvas can be panned/zoomed by wheel/pinch/drag; node text remains readable and nodes are not clipped by the card.
- [ ] Legend wraps into rows without clipping; all four explanation groups remain readable.
- [ ] Select a node; the 2px selection outline and mastery text color are still distinguishable at this width.

## 3. Zoom levels 0.2x / 1x / 2.5x / 4x

Use the mouse wheel over the canvas; press "重置视野" between checks.

- [ ] At 0.2x, node names and status lines remain screen-size and readable (nodes intentionally do not shrink; overlap at very low zoom is acceptable).
- [ ] At 1x, layout is stable; labels sit inside their nodes.
- [ ] At 2.5x and 4x, node text stays 12px screen-size, is not blurred and the canvas is not blank/white.
- [ ] With "关系标签" on, relation labels stay screen-size at every zoom and do not scale down with the graph.
- [ ] A selected node remains the only 2px-blue node at every zoom; path hints remain 1px.

## 4. Mastery fixtures (four colors)

- [ ] RED (薄弱) node: name is `#a9695d` before and after selection.
- [ ] GREEN (已掌握) node: name is `#4f8875` before and after selection.
- [ ] YELLOW (待巩固) node: name is `#b98239` before and after selection.
- [ ] GRAY (未学习) node: name is `#8f9994` before and after selection.
- [ ] The small status line reads 薄弱 / 已掌握 / 待巩固 / 未学习 and uses the same color.
- [ ] A node with `fillByStatus` (teacher dashboard projection) keeps the aggregate status fill, status border and status text; it is not mistaken for a category tint.

## 5. Selection vs path vs pulse

- [ ] Select a unit-mapped node: path hint (1px) is not replaced by the selected 2px blue border, and other mapped nodes keep the 1px hint.
- [ ] Select a node adjacent to a mapped node: exactly one selected outline is visible; the mapped node still uses the path hint.
- [ ] Trigger a status-change pulse (complete an activity that changes mastery) on a path-highlighted node: the pulse uses the path blue-gray token, not the selected primary blue.
- [ ] After 1.2s the pulse disappears and the node returns to its normal/path/selected style.

## 6. Admin `/teaching/knowledge`

- [ ] Open `/teaching/knowledge` as an admin with the shared graph.
- [ ] Node single click selects and shows detail; double click still enters the node workspace.
- [ ] "修改章节" still updates and re-renders the graph.
- [ ] "新建节点", "添加关系", "执行 Cypher", "删除此节点" and "编辑学习素材" still open and submit.
- [ ] After a chapter change or node deletion, the graph rebuilds, selection clears correctly and the legend stays consistent.
- [ ] Admin graph shows category tints and 1px neutral borders; no personal mastery text colors are fabricated.

## 7. Regression surfaces sharing the component

- [ ] Teacher dashboard class heatmap (`fillByStatus`): soft status fills, 1px status border, status text color; legend matches.
- [ ] Learning records replay graph (embedded, `chartHeight`): nodes and labels are not clipped at the small height.
- [ ] Question view embedded graph (if shown): selection and relation reading still work.
- [ ] No large gradients, neon halos, decorative nodes or a page of identical colored nodes.

## Screenshot list for L

1. `/graph` 1440 px, no selection.
2. `/graph` 1440 px, node A selected (show ring + status color + adjacent edges).
3. `/graph` 1440 px, unit path hint + one selected node.
4. `/graph` 390 px, graph + wrapped legend.
5. `/graph` at 0.2x, 1x, 2.5x and 4x (four crops of the same area).
6. Four mastery colors (RED/GREEN/YELLOW/GRAY), selected and unselected.
7. `/teaching/knowledge` admin detail with the maintenance collapse open.
8. Teacher dashboard `fillByStatus` graph and legend.

Record pass/fail and attach screenshot file names in the Task D acceptance note.
