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
        const trace = (line) => {
          const marker = t.expressionStatement(t.callExpression(
            t.memberExpression(t.identifier("window"), t.identifier("__oslabLegacyTrace")),
            [t.stringLiteral(file), t.numericLiteral(line), t.stringLiteral("execute")]
          ));
          marker.__oslabInjected = true;
          return marker;
        };

        // Call sites become thin observers. They call the same SpriteJS methods
        // with the same objects, keyframes, delays and callback closures.
        programPath.traverse({
          CallExpression: {
            exit(callPath) {
              const node = callPath.node;
              if (!node.loc || !t.isMemberExpression(node.callee) || node.callee.computed) return;
              const name = node.callee.property.name;
              if (name !== "animate" && name !== "setTimeout") return;
              const helper = name === "animate" ? "__oslabLegacyAnimate" : "__oslabLegacyTimeout";
              const args = [...node.arguments];
              if (args.length === 1) args.push(t.objectExpression([]));
              callPath.replaceWith(t.callExpression(
                t.memberExpression(t.identifier("window"), t.identifier(helper)),
                [node.callee.object, ...args, t.stringLiteral(file), t.numericLiteral(lineOf(node))]
              ));
              callPath.skip();
            },
          },
          Statement(statementPath) {
            const node = statementPath.node;
            if (!node.loc || node.__oslabInjected || !statementPath.getFunctionParent()) return;
            if (t.isBlockStatement(node) || t.isEmptyStatement(node)) return;
            if (!statementPath.inList) return;
            statementPath.insertBefore(trace(lineOf(node)));
          },
        });
      },
    },
  };
};
