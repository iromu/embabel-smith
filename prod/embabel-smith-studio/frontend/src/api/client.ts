/**
 * The single `fetch` site for the studio (change 06). Every pane goes through this module, so the
 * request shaping and response parsing live in one place that the tests can mock `fetch` against
 * once. Each exported method is one backend endpoint, typed by the DTOs in {@link ./types}.
 */

import type {
  ApproveResponse,
  AutoPublishRequest,
  BuildRequest,
  BuildResponse,
  CandidateInfo,
  ComposeRequest,
  ComposeResponse,
  DiscoveryRecord,
  DiscoverRequest,
  DiscoverResponse,
  EvalRequest,
  EvalResponse,
  ExtractRequest,
  ExtractResponse,
  GenerateRequest,
  GenerateResponse,
  IterationInfo,
  LoopStatus,
  PackInfo,
  RejectResponse,
  RunRequest,
  RunResponse,
  SavePackRequest,
  SavePackResponse,
  SecurityRequest,
  SecurityResponse,
  SimulateRequest,
  SimulateResponse,
  SourceInfo,
  StatusInfo,
  StoredPackInfo,
  StoredPackResponse,
  TraceInfo,
  TrustRequest,
  TrustResponse,
} from './types'

/** A non-2xx response is a hard failure; the pane surfaces the thrown message. */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, init)
  if (!res.ok) {
    throw new Error(`${init?.method ?? 'GET'} ${path} failed with status ${res.status}`)
  }
  return (await res.json()) as T
}

function get<T>(path: string): Promise<T> {
  return request<T>(path)
}

function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

function put<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

// ---- Global status ---------------------------------------------------------
/** Whether a model endpoint is resolvable, and which default model it would use. */
export const getStatus = (): Promise<StatusInfo> => get<StatusInfo>('/api/status')

// ---- Sources / packs / goals (offline) -------------------------------------
export const listSources = (): Promise<SourceInfo[]> => get<SourceInfo[]>('/api/sources')
export const listPacks = (source: string): Promise<PackInfo[]> =>
  get<PackInfo[]>(`/api/packs?source=${encodeURIComponent(source)}`)
/** The goal names a source's packs export — the Run pane runs a goal, not a pack name. */
export const listGoals = (source: string): Promise<string[]> =>
  get<string[]>(`/api/goals?source=${encodeURIComponent(source)}`)

// ---- Run (endpoint-gated) ---------------------------------------------------
export const run = (req: RunRequest): Promise<RunResponse> => post<RunResponse>('/api/run', req)

// ---- Build (endpoint-gated) -------------------------------------------------
export const build = (req: BuildRequest): Promise<BuildResponse> =>
  post<BuildResponse>('/api/build', req)

// ---- Compose (offline) ------------------------------------------------------
export const compose = (req: ComposeRequest): Promise<ComposeResponse> =>
  post<ComposeResponse>('/api/compose', req)

// ---- Generate / Simulate / Discover (the author-a-process workspace) --------
export const generate = (req: GenerateRequest): Promise<GenerateResponse> =>
  post<GenerateResponse>('/api/generate', req)
export const simulate = (req: SimulateRequest): Promise<SimulateResponse> =>
  post<SimulateResponse>('/api/simulate', req)
export const discover = (req: DiscoverRequest): Promise<DiscoverResponse> =>
  post<DiscoverResponse>('/api/discover', req)
export const extract = (req: ExtractRequest): Promise<ExtractResponse> =>
  post<ExtractResponse>('/api/extract', req)

// ---- Durable store (offline) -----------------------------------------------
export const listStoredPacks = (): Promise<StoredPackInfo[]> =>
  get<StoredPackInfo[]>('/api/store/packs')
export const loadStoredPack = (name: string): Promise<StoredPackResponse> =>
  get<StoredPackResponse>(`/api/store/packs/${encodeURIComponent(name)}`)
export const savePack = (req: SavePackRequest): Promise<SavePackResponse> =>
  post<SavePackResponse>('/api/store/packs', req)
export const listDiscoveries = (): Promise<DiscoveryRecord[]> =>
  get<DiscoveryRecord[]>('/api/store/discoveries')

// ---- Eval (endpoint-gated) --------------------------------------------------
export const evaluate = (req: EvalRequest): Promise<EvalResponse> =>
  post<EvalResponse>('/api/eval', req)

// ---- Security / Trust (offline) ---------------------------------------------
export const scan = (req: SecurityRequest): Promise<SecurityResponse> =>
  post<SecurityResponse>('/api/security', req)
export const checkTrust = (req: TrustRequest): Promise<TrustResponse> =>
  post<TrustResponse>('/api/trust', req)

// ---- Traces (offline) -------------------------------------------------------
export const traces = (skill: string, limit = 100): Promise<TraceInfo[]> =>
  get<TraceInfo[]>(`/api/traces?skill=${encodeURIComponent(skill)}&limit=${limit}`)

// ---- Improvement loop -------------------------------------------------------
export const listCandidates = (): Promise<CandidateInfo[]> =>
  get<CandidateInfo[]>('/api/improve/candidates')
export const approveCandidate = (id: string): Promise<ApproveResponse> =>
  post<ApproveResponse>(`/api/improve/candidates/${encodeURIComponent(id)}/approve`)
export const rejectCandidate = (id: string): Promise<RejectResponse> =>
  post<RejectResponse>(`/api/improve/candidates/${encodeURIComponent(id)}/reject`)
export const triggerLoop = (): Promise<IterationInfo> => post<IterationInfo>('/api/loop/trigger')
export const startLoop = (): Promise<LoopStatus> => post<LoopStatus>('/api/loop/start')
export const stopLoop = (): Promise<LoopStatus> => post<LoopStatus>('/api/loop/stop')
export const loopStatus = (): Promise<LoopStatus> => get<LoopStatus>('/api/loop/status')
export const getAutoPublish = (): Promise<LoopStatus> => get<LoopStatus>('/api/loop/auto-publish')
export const setAutoPublish = (req: AutoPublishRequest): Promise<LoopStatus> =>
  put<LoopStatus>('/api/loop/auto-publish', req)
