import { nodeWorkbench, type NodeWorkbenchVO } from "@/api/learning";

const CACHE_TTL_MS = 30_000;
const CACHE_LIMIT = 24;

type WorkbenchCacheEntry = {
  data: NodeWorkbenchVO;
  expiresAt: number;
};

const cache = new Map<string, WorkbenchCacheEntry>();
const inFlight = new Map<string, Promise<NodeWorkbenchVO>>();
let revision = 0;

const keyOf = (userId: string | number, nodeName: string) =>
  JSON.stringify([String(userId), nodeName]);

export function invalidateNodeWorkbench() {
  revision += 1;
  cache.clear();
  inFlight.clear();
}

export function peekNodeWorkbench(
  userId: string | number,
  nodeName: string
): NodeWorkbenchVO | null {
  const key = keyOf(userId, nodeName);
  const entry = cache.get(key);
  if (!entry || entry.expiresAt <= Date.now()) {
    cache.delete(key);
    return null;
  }
  cache.delete(key);
  cache.set(key, entry);
  return entry.data;
}

export function loadNodeWorkbench(
  userId: string | number,
  nodeName: string,
  force = false
): Promise<NodeWorkbenchVO> {
  const key = keyOf(userId, nodeName);
  if (force) invalidateNodeWorkbench();
  const cached = peekNodeWorkbench(userId, nodeName);
  if (cached) return Promise.resolve(cached);
  const pending = inFlight.get(key);
  if (pending) return pending;

  const requestRevision = revision;
  const request = nodeWorkbench(nodeName).then((response) => {
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "节点工作台加载失败");
    }
    const data = response.data.data;
    if (requestRevision === revision) {
      cache.set(key, { data, expiresAt: Date.now() + CACHE_TTL_MS });
      while (cache.size > CACHE_LIMIT) {
        const oldest = cache.keys().next().value;
        if (oldest === undefined) break;
        cache.delete(oldest);
      }
    }
    return data;
  });
  inFlight.set(key, request);
  const removePending = () => {
    if (inFlight.get(key) === request) inFlight.delete(key);
  };
  request.then(removePending, removePending);
  return request;
}

if (typeof window !== "undefined") {
  window.addEventListener("oslab:learning-changed", invalidateNodeWorkbench);
}
