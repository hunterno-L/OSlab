import axios from "axios";

export interface AiSource {
  sourceId: string;
  title: string;
  sectionCode?: string;
  snippet: string;
}

export interface AiTool {
  tool: string;
  args: Record<string, unknown>;
  resultCount: number;
}

export interface AiAnswer {
  sessionId: string;
  mode: "MODEL" | "DEGRADED";
  answer: string;
  sources: AiSource[];
  tools: AiTool[];
  evidence: Record<string, number>;
}

export interface AiTask {
  title: string;
  reason: string;
  type: string;
  link: string;
  evidenceCount: number;
}

export interface AiConfig {
  configured: boolean;
  baseUrl: string;
  model: string;
  embeddingModel?: string;
  apiKeyMasked: string;
}

const http = axios.create({ baseURL: "/api", withCredentials: true, timeout: 120000 });

export const aiApi = {
  startSession: (body: Record<string, unknown>) =>
    http.post("/ai/session/start", body).then((r) => r.data.data),
  ask: (body: Record<string, unknown>) =>
    http.post("/ai/ask", body).then((r) => r.data.data as AiAnswer),
  plan: (body: Record<string, unknown>) =>
    http.post("/ai/plan", body).then((r) => r.data.data),
  getConfig: () => http.get("/ai/config").then((r) => r.data.data as AiConfig),
  saveConfig: (body: Record<string, unknown>) =>
    http.put("/ai/config", body).then((r) => r.data.data as AiConfig),
  testConfig: () =>
    http.post("/ai/config/test").then((r) => r.data.data as Record<string, unknown>),
  diagnose: (nodeCode: string) =>
    http.get("/ai/diagnose", { params: { nodeCode } }).then((r) => r.data.data),
  help: (body: Record<string, unknown>) =>
    http.post("/ai/help", body).then((r) => r.data.data),
};
