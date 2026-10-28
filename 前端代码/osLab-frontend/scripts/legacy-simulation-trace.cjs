const ALGORITHM_FILES = new Set([
  "unit3/FCFS.vue", "unit3/SJF.vue", "unit3/HighResponse.vue",
  "unit3/HighPrio.vue", "unit3/HighPrio2.vue", "unit3/RR.vue",
  "unit3/FeedbackQueue.vue", "unit4/PartitionAllocation_FF.vue",
  "unit4/PartitionAllocation_BF.vue", "unit4/PartitionAllocation_WF.vue",
  "unit4/PartitionAllocation_NF.vue", "unit4/Replacement_FF.vue",
  "unit4/Replacement_LRU.vue", "unit4/Replacement_OPT.vue",
  "unit5/FCFS.vue", "unit5/SSTF.vue", "unit5/SCAN.vue", "unit5/CSCAN.vue",
]);

module.exports = function traceLegacyAlgorithm({ types: t }) {
  return {
    name: "trace-legacy-simulation-source",
    visitor: {
      Program(programPath, state) {
        const filename = state.file.opts.filename || "";
        const match = filename.replace(/\\/g, "/").match(/unit[345]\/[^/]+\.vue$/);
        if (!match || !ALGORITHM_FILES.has(match[0])) return;
        // vue-loader also sends generated template render functions through
        // Babel with the same filename. Only instrument the original script.
        if (!programPath.node.body.some((node) => t.isExportDefaultDeclaration(node))) return;
        const file = `src/views/${match[0]}`;
        const sfc = require("fs").readFileSync(filename, "utf8");
        const scriptTag = sfc.match(/<script\b[^>]*>/);
        const offset = scriptTag
          ? sfc.slice(0, scriptTag.index + scriptTag[0].length).split(/\r?\n/).length - 1
          : 0;
        const lineOf = (node) => node.loc.start.line + offset;
        const script = state.file.code;
        const source = (node) => script.slice(node.start, node.end);
        const property = (node, name) => t.isMemberExpression(node) && !node.computed && t.isIdentifier(node.property, { name });
        const contains = (node, predicate) => {
          let found = false;
          t.traverseFast(node, (child) => { if (predicate(child)) found = true; });
          return found;
        };
        const metadata = (phase) => t.objectExpression([
          t.objectProperty(t.identifier("phase"), typeof phase === "string" ? t.stringLiteral(phase) : phase),
        ]);
        const trace = (line, phase, kind = "execute") => {
          const marker = t.expressionStatement(t.callExpression(
            t.memberExpression(t.identifier("window"), t.identifier("__oslabLegacyTrace")),
            [t.stringLiteral(file), t.numericLiteral(line), t.stringLiteral(kind), metadata(phase)]
          ));
          marker.__oslabInjected = true;
          return marker;
        };
        const methodName = (path) => {
          const method = path.findParent((parent) => parent.isObjectMethod());
          return method && method.node.key.name;
        };
        const inCallback = (path) => Boolean(path.findParent((parent) =>
          parent.isFunction() && parent.parentPath.isCallExpression() &&
          parent.parent.arguments[0] === parent.node && property(parent.parent.callee, "setTimeout")
        ));
        // Inspect the branch the original program actually took, rather than
        // predicting allocation success from a second implementation.
        const branchOf = (path, pattern) => {
          for (let child = path, parent = path.parentPath; parent; child = parent, parent = parent.parentPath) {
            if (parent.isIfStatement() && pattern.test(source(parent.node.test))) return child.key;
          }
          return null;
        };
        const releasePhases = new WeakMap();
        const cpuReleasePhase = (path) => {
          if (match[0] === "unit3/RR.vue") return t.stringLiteral("yield");
          if (match[0] !== "unit3/FeedbackQueue.vue") return t.stringLiteral("preempt");
          // These are the original MLFQ segment's executed values. A shortened
          // slice means a higher queue preempted it; an exhausted slice either
          // moves to the next queue or rotates within the final queue.
          const parse = require("@babel/parser").parseExpression;
          const method = path.findParent((parent) => parent.isObjectMethod());
          if (!releasePhases.has(method.node)) {
            const id = method.scope.generateUidIdentifier("oslabReleasePhase");
            method.get("body").unshiftContainer("body", t.variableDeclaration("const", [t.variableDeclarator(id,
              parse(`this.pcbSortLine[i].pcb_truework < Math.min(this.lineForm.lineObjs[this.pcbSortLine[i].queueIndex].size, this.pcbSortLine[i].pcb_work)
                ? "preempt" : this.pcbSortLine[i].queueIndex != this.queueNum - 1 ? "demote" : "yield"`))]));
            releasePhases.set(method.node, id);
          }
          return t.cloneNode(releasePhases.get(method.node));
        };
        const tipPhase = (node, path) => {
          const words = [];
          t.traverseFast(node, (child) => { if (t.isStringLiteral(child)) words.push(child.value); });
          const text = words.join("");
          if (match[0].startsWith("unit3/")) {
            if (text.includes("到达") && text.includes("就绪队列")) return "ready";
            if (text.includes("上CPU")) return "run";
            if (text.includes("运行结束")) return "complete";
            if (text.includes("剥夺处理机")) return cpuReleasePhase(path);
          }
          if (match[0].includes("Replacement_")) {
            if (text.includes("命中")) return "hit";
            if (text.includes("置换")) return "replace";
            if (text.includes("加入到")) return "load";
            if (text.includes("缺页率")) return "result";
          }
          if (match[0].startsWith("unit5/")) {
            if (text.includes("返回到待访问列表")) return "jump";
            if (text.includes("磁头移动方向上没有访问请求")) return "boundary";
            if (text.includes("磁头移动")) return "seek";
            if (text.includes("总共移动") || text.includes("平均需要移动")) return "result";
          }
          return null;
        };
        const animationPhase = (path) => {
          const node = path.node;
          const frames = node.arguments[0];
          const method = methodName(path);
          if (!frames) return null;
          if (match[0].startsWith("unit3/")) {
            if (contains(frames, (child) => property(child, "COLOR_PCB_RUN")) &&
                contains(frames, (child) => property(child, "COLOR_PCB_READY"))) return "run";
            if (node.arguments[1] && source(node.arguments[1]).includes("time_wait1")) return "ready";
          }
          if (match[0].includes("PartitionAllocation_") && method === "onSubmit2" && t.isIdentifier(node.callee.object, { name: "pcb" })) {
            return branchOf(path, /\bj\s*={2,3}\s*patitionPos\b/) === "consequent" ? "allocate" : "search";
          }
          if (match[0].includes("Replacement_")) {
            if (contains(frames, (child) => t.isIdentifier(child, { name: "PAGE_NEW" }))) return "load";
            if (contains(frames, (child) => t.isIdentifier(child, { name: "PAGE_RIGHT" }))) return "hit";
            // The tick is a visual confirmation after the page was loaded.
            // The actual new/replace callback below emits fault before load.
            if (t.isIdentifier(node.callee.object, { name: "p2" })) return null;
            if (method === "replace") return t.isIdentifier(node.callee.object, { name: "p1" }) ? "replace" : "load";
            if (method === "drawPage" && t.isIdentifier(node.callee.object, { name: "p1" })) return "search";
          }
          if (match[0].startsWith("unit5/")) {
            if (method === "drawArrow0" && match[0] === "unit5/CSCAN.vue") return "jump";
            if (method === "drawArrow0" || method === "drawArrow1") return "seek";
            if (method === "startAnimate" && property(node.callee.object, "stepArrow") &&
                contains(frames, (child) => t.isObjectProperty(child) && t.isIdentifier(child.key, { name: "x" }))) {
              if (match[0] === "unit5/CSCAN.vue") {
                return require("@babel/parser").parseExpression('this.partitionSort_line[i].size > 0 ? "seek" : "jump"');
              }
              return "seek";
            }
          }
          return null;
        };

        // Call sites become thin observers. They call the same SpriteJS methods
        // with the same objects, keyframes, delays and callback closures.
        programPath.traverse({
          ObjectMethod(methodPath) {
            const name = methodPath.node.key.name;
            const entry = match[0].startsWith("unit3/") ? "submitForm"
              : match[0].includes("PartitionAllocation_") ? "onSubmit2" : "onSubmit1";
            if (name !== entry) return;
            const awaited = methodPath.get("body.body").filter((statement) =>
              statement.isExpressionStatement() && t.isAwaitExpression(statement.node.expression));
            if (awaited.length) {
              const runId = methodPath.scope.generateUidIdentifier("oslabRunId");
              const currentRun = () => t.memberExpression(
                t.memberExpression(t.identifier("window"), t.identifier("__oslabLegacyState")), t.identifier("runId"));
              methodPath.node.body.body.unshift(t.variableDeclaration("const", [t.variableDeclarator(runId, currentRun())]));
              for (const statement of awaited) {
                // Reset cancels this original async invocation. An active run
                // still executes precisely the original continuation.
                statement.insertAfter(t.ifStatement(t.binaryExpression("!==", t.cloneNode(runId), currentRun()), t.returnStatement()));
              }
            }
            methodPath.node.body.body.unshift(trace(lineOf(methodPath.node), "initialize"));
          },
          CallExpression: {
            exit(callPath) {
              const node = callPath.node;
              if (!node.loc || !t.isMemberExpression(node.callee) || node.callee.computed) return;
              const name = node.callee.property.name;
              if (name !== "animate" && name !== "setTimeout") return;
              const helper = name === "animate" ? "__oslabLegacyAnimate" : "__oslabLegacyTimeout";
              let phase = name === "animate" ? animationPhase(callPath)
                : match[0].includes("PartitionAllocation_") && branchOf(callPath, /\bresultType\s*={2,3}\s*0\b/) === "consequent" ? "fail" : null;
              if (name === "setTimeout" && match[0].includes("Replacement_") && node.arguments[0] &&
                  contains(node.arguments[0], (child) => t.isAssignmentExpression(child) && property(child.left, "tips_text") &&
                    ["load", "replace"].includes(tipPhase(child.right)))) phase = "fault";
              const args = [...node.arguments];
              if (args.length === 1) args.push(t.objectExpression([]));
              callPath.replaceWith(t.callExpression(
                t.memberExpression(t.identifier("window"), t.identifier(helper)),
                [node.callee.object, ...args, t.stringLiteral(file), t.numericLiteral(lineOf(node)), phase ? metadata(phase) : t.objectExpression([])]
              ));
              callPath.skip();
            },
          },
          ExpressionStatement: {
            exit(statementPath) {
              const node = statementPath.node;
              if (!node.loc || node.__oslabInjected || !inCallback(statementPath)) return;
              const assignment = node.expression;
              if (!t.isAssignmentExpression(assignment) || !property(assignment.left, "tips_text")) return;
              const phase = tipPhase(assignment.right, statementPath);
              if (!phase) return;
              const marker = trace(lineOf(node), phase, "callback");
              // Single-statement if/else bodies must stay inside their original
              // branch. Emit only after the original assignment succeeds.
              if (statementPath.inList) statementPath.insertAfter(marker);
              else { statementPath.replaceWith(t.blockStatement([node, marker])); statementPath.skip(); }
            },
          },
        });
      },
    },
  };
};
