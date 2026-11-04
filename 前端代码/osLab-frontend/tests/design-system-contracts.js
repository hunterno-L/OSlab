// Run with: node tests/design-system-contracts.js
const assert = require("assert").strict;
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const css = fs.readFileSync(
  path.join(root, "src/styles/learning-theme.css"),
  "utf8"
);

const normalize = (value) =>
  String(value).replace(/\s+/g, " ").trim().toLowerCase();
const tokens = new Map();
for (const match of css.matchAll(/--([a-z0-9-]+)\s*:\s*([^;]+);/gi)) {
  if (!tokens.has(match[1])) tokens.set(match[1], normalize(match[2]));
}

const expectToken = (name, value) => {
  assert.ok(tokens.has(name), `learning-theme.css must define --${name}`);
  assert.equal(
    tokens.get(name),
    normalize(value),
    `--${name} must match the C+B design token`
  );
};

expectToken("oslab-canvas", "#edf1f6");
expectToken("oslab-panel", "#ffffff");
expectToken("oslab-panel-soft", "#f4f7fb");
expectToken("oslab-panel-muted", "#e9eef5");
expectToken("oslab-ink", "#20242b");
expectToken("oslab-ink-soft", "#3a4150");
expectToken("oslab-muted-strong", "#525b66");
expectToken("oslab-line", "#dbe2ea");
expectToken("oslab-line-soft", "#e9eef4");
expectToken("oslab-line-strong", "#c3cedb");
expectToken("oslab-primary", "#2447d8");
expectToken("oslab-primary-hover", "#1a35a8");
expectToken("oslab-primary-active", "#142a87");
expectToken("oslab-primary-soft", "#e9edff");
expectToken("oslab-on-primary", "#ffffff");
expectToken("oslab-highlight", "#f2d64b");
expectToken("oslab-highlight-soft", "#fff7c2");
expectToken("oslab-highlight-strong", "#ffe98a");
expectToken("oslab-annotation", "#d7614a");
expectToken("oslab-annotation-soft", "#f7ddd6");
expectToken("oslab-mastered", "#3f8f6e");
expectToken("oslab-mastered-soft", "#e6f2ec");
expectToken("oslab-review", "#b8892e");
expectToken("oslab-review-soft", "#f6edd9");
expectToken("oslab-weak", "#b8543f");
expectToken("oslab-weak-soft", "#f6e3de");
expectToken("oslab-unseen", "#6b7168");
expectToken("oslab-unseen-soft", "#eceff4");
expectToken("oslab-graph-surface", "transparent");
expectToken("oslab-graph-node-border", "#c3cedb");
expectToken("oslab-graph-node-radius", "8px");
expectToken("oslab-graph-category-concept", "#e8eefc");
expectToken("oslab-graph-category-algorithm", "#f1e9fa");
expectToken("oslab-graph-category-function", "#e4f2ed");
expectToken("oslab-graph-category-default", "#ecf0f5");
expectToken("oslab-graph-node-fill-concept", "#f8faff");
expectToken("oslab-graph-node-fill-algorithm", "#fbf8ff");
expectToken("oslab-graph-node-fill-function", "#f6fbf9");
expectToken("oslab-graph-node-fill-default", "#fafbfd");
expectToken("oslab-graph-node-shadow", "rgba(32, 36, 43, 0.08)");
expectToken("oslab-graph-selection", "#2447d8");
expectToken("oslab-graph-selection-ring", "rgba(36, 71, 216, 0.18)");
expectToken("oslab-graph-path", "#8fa6c4");
expectToken("oslab-graph-path-strong", "#6b809f");
expectToken("oslab-graph-path-edge", "#3f66a5");
expectToken("oslab-graph-path-ring", "rgba(107, 128, 159, 0.28)");
expectToken("oslab-graph-review-ring", "rgba(184, 137, 46, 0.26)");
expectToken("oslab-graph-text-soft", "#66707c");
expectToken("oslab-graph-label-surface", "#ffffff");
expectToken("oslab-graph-highlight-soft", "#fff7c2");
expectToken("oslab-graph-relation-dependent", "#4f72b8");
expectToken("oslab-graph-relation-contain", "#8b96a6");
expectToken("oslab-graph-relation-related", "#9a948b");
expectToken("oslab-graph-relation-implements", "#b8892e");
expectToken("oslab-graph-relation-fallback", "#b0b6bf");
expectToken("arcoblue-6", "36, 71, 216");
expectToken("red-6", "184, 84, 63");
expectToken("green-6", "63, 143, 110");
expectToken("orange-6", "184, 137, 46");
expectToken(
  "oslab-font-display",
  '"LXGW WenKai", "Kaiti SC", "KaiTi", "STKaiti", "Noto Serif SC", serif'
);
expectToken("oslab-shadow-card", "0 8px 24px rgba(32, 36, 43, 0.06)");
expectToken("oslab-radius-container", "14px");

const headerSource = fs.readFileSync(
  path.join(root, "src/components/GlobalHeader.vue"),
  "utf8"
);
const layoutSource = fs.readFileSync(
  path.join(root, "src/layouts/LearningLayout.vue"),
  "utf8"
);
assert.ok(
  layoutSource.includes("page-eyebrow"),
  "LearningLayout must render the mono eyebrow"
);
assert.ok(
  layoutSource.includes("var(--oslab-highlight)"),
  "page title must use the highlighter token"
);
assert.ok(
  headerSource.includes("border-radius: var(--oslab-radius-control)"),
  "global navigation must use the control radius token"
);

const annotationFile = path.join(
  root,
  "src/components/learning/AnnotationNote.vue"
);
assert.ok(fs.existsSync(annotationFile), "AnnotationNote.vue must exist");
const annotation = fs.readFileSync(annotationFile, "utf8");
assert.ok(
  annotation.includes("var(--oslab-annotation)"),
  "AnnotationNote must use the annotation token"
);
assert.ok(
  css.includes(".oslab-highlight-text"),
  "learning-theme.css must expose the highlighter utility"
);
assert.ok(
  css.includes(".arco-table-th"),
  "learning-theme.css must skin the Arco table header"
);

expectToken("oslab-ease", "cubic-bezier(0.2, 0.7, 0.2, 1)");
expectToken("oslab-duration-fast", "160ms");
expectToken("oslab-duration", "220ms");
expectToken("oslab-duration-slow", "420ms");

console.log(
  "Design system contracts passed: C+B tokens, Arco bridge, annotation."
);
