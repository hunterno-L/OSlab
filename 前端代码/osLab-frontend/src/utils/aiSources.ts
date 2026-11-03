import type { RouteLocationRaw } from "vue-router";
import type { AiSourceRef } from "@/api/ai";
import { materialIdFromBlockId, paragraphFromLocator } from "@/utils/aiActions";

/** 来源卡片标题：优先摘录首句，其次定位说明 */
export function aiSourceTitle(source: AiSourceRef): string {
  const excerpt = (source.excerpt || "").replace(/\s+/g, " ").trim();
  if (excerpt) return excerpt.slice(0, 46);
  return source.locator || source.sourceId || "资料片段";
}

export function aiSourceScopeLabel(source: AiSourceRef): string {
  return source.ownerScope === "PERSONAL" ? "我的资料" : "课程资料";
}

export function aiSourceRevisionLabel(source: AiSourceRef): string {
  return source.revision ? `修订 ${source.revision}` : "版本未知";
}

/** 从 blockId / sourceId / locator 解析个人资料 id */
export function aiSourceMaterialId(source: AiSourceRef): string {
  if (source.ownerScope !== "PERSONAL") return "";
  if (source.sourceId && source.sourceId.startsWith("material:")) {
    return source.sourceId.slice("material:".length);
  }
  const fromBlock = materialIdFromBlockId(source.blockId);
  if (fromBlock) return fromBlock;
  const match = /material:(\d+)/.exec(source.locator || "");
  return match ? match[1] : "";
}

/** 课程来源的 chunkCode（后端 sourceId 即 chunkCode） */
export function aiSourceChunkCode(source: AiSourceRef): string {
  if (source.ownerScope !== "COURSE") return "";
  if (source.sourceId && !source.sourceId.startsWith("material:")) {
    return source.sourceId;
  }
  const match = /::([^:]+)::p\d+$/.exec(source.blockId || "");
  return match ? match[1] : "";
}

/** 课程来源的 sectionCode（locator 形如 sec.x#chunk-y-p0） */
export function aiSourceSectionCode(source: AiSourceRef): string {
  const locator = source.locator || "";
  const hash = locator.indexOf("#");
  return hash > 0 ? locator.slice(0, hash) : "";
}

/** 来源所在页面的站内路由（课程资料定位到目录/章节；个人资料走对话框） */
export function aiSourceRoute(source: AiSourceRef): RouteLocationRaw | null {
  if (source.ownerScope === "PERSONAL") return null;
  if (!source.courseCode) return null;
  const query: Record<string, string> = { courseCode: source.courseCode };
  const section = aiSourceSectionCode(source);
  if (section) query.sectionCode = section;
  const chunk = aiSourceChunkCode(source);
  if (chunk) query.chunk = chunk;
  return { path: "/graph", query };
}

/**
 * 若当前 DOM 已有该资料块锚点（课程单元/节点工作台的 chunk 容器），
 * 直接滚动并短暂高亮；返回 true 表示已就地定位。
 */
export function highlightSourceInPage(source: AiSourceRef): boolean {
  if (typeof document === "undefined") return false;
  const chunk = aiSourceChunkCode(source);
  if (!chunk) return false;
  const candidates = [
    `#chunk-${chunk}`,
    `[data-chunk-code="${chunk}"]`,
    `#chunk-${chunk}-p${paragraphFromLocator(source.locator)}`,
  ];
  for (const selector of candidates) {
    let element: Element | null = null;
    try {
      element = document.querySelector(selector);
    } catch {
      element = null;
    }
    if (!element) continue;
    element.scrollIntoView({ behavior: "smooth", block: "center" });
    element.classList.add("ai-source-highlight");
    window.setTimeout(() => {
      element?.classList.remove("ai-source-highlight");
    }, 2600);
    return true;
  }
  return false;
}

/** 打开个人资料对话框（由全局 AiMaterialDialog 监听） */
export function openAiMaterialSource(source: AiSourceRef): boolean {
  const materialId = aiSourceMaterialId(source);
  if (!materialId) return false;
  if (typeof window === "undefined") return false;
  window.dispatchEvent(
    new CustomEvent("oslab:ai-open-material", {
      detail: {
        materialId,
        paragraph: paragraphFromLocator(source.locator),
        title: source.locator || "",
      },
    })
  );
  return true;
}

/** 打开任意个人资料段落（下一步 MATERIAL 任务也复用该入口） */
export function openAiMaterial(materialId: string, paragraph = 0): void {
  if (typeof window === "undefined" || !materialId) return;
  window.dispatchEvent(
    new CustomEvent("oslab:ai-open-material", {
      detail: { materialId, paragraph },
    })
  );
}
