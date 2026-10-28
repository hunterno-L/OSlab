export interface CppToken {
  text: string;
  kind: string;
}

export interface CppSourceRow {
  number: number;
  text: string;
  tokens: CppToken[];
}

export interface CppSource {
  rows: CppSourceRow[];
  phases: Record<string, number[]>;
}

const keywords = new Set(
  "auto bool break case char class const constexpr continue default do double else enum explicit false float for if inline int long namespace nullptr private protected public return short signed sizeof static struct switch template this true typedef typename unsigned using void while".split(
    " "
  )
);

function tokenize(line: string): CppToken[] {
  const include = line.match(/^(\s*#\s*include\s*)(<[^>]+>)(.*)$/);
  if (include) {
    const tokens: CppToken[] = [
      { text: include[1], kind: "directive" },
      { text: include[2], kind: "header" },
    ];
    if (include[3]) tokens.push({ text: include[3], kind: "" });
    return tokens;
  }
  const pattern =
    /(\/\/.*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|#\s*\w+|\b\d+(?:\.\d+)?\b|\b\w+\b)/g;
  const tokens: CppToken[] = [];
  let offset = 0;
  for (const match of line.matchAll(pattern)) {
    const start = match.index || 0;
    if (start > offset)
      tokens.push({ text: line.slice(offset, start), kind: "" });
    const text = match[0];
    const kind = text.startsWith("//")
      ? "comment"
      : /^["']/.test(text)
      ? "string"
      : text.startsWith("#")
      ? "directive"
      : /^\d/.test(text)
      ? "number"
      : keywords.has(text)
      ? "keyword"
      : "";
    tokens.push({ text, kind });
    offset = start + text.length;
  }
  if (offset < line.length) tokens.push({ text: line.slice(offset), kind: "" });
  return tokens.length ? tokens : [{ text: " ", kind: "" }];
}

// Trace annotations are for linking real animation events. They are omitted
// from the teaching pane so it contains only the complete C++ program.
export function parseCppSource(source: string): CppSource {
  if (!/^\s*#include\s*[<"]/m.test(source) || !/\bmain\s*\(/.test(source))
    throw new Error("C++ 算法源码不完整");
  const rows: CppSourceRow[] = [];
  const phases: Record<string, number[]> = {};
  const pending = new Set<string>();
  let pendingComments: number[] = [];
  source.split(/\r?\n/).forEach((text) => {
    const marker = text.match(/^\s*\/\/\s*@trace:([a-z]+)\s*$/);
    if (marker) {
      pending.add(marker[1]);
      return;
    }
    const number = rows.length + 1;
    rows.push({ number, text, tokens: tokenize(text) });
    if (!text.trim()) {
      pendingComments = [];
      return;
    }
    // A marker also owns the explanatory comment lines directly below it, so
    // the pane highlights the teaching note together with the linked statement.
    if (/^\s*\/\//.test(text)) {
      if (pending.size) pendingComments.push(number);
      return;
    }
    if (pending.size) {
      pending.forEach((phase) => {
        (phases[phase] ||= []).push(...pendingComments, number);
      });
      pending.clear();
      pendingComments = [];
    }
  });
  return { rows, phases };
}
