/**
 * The single `fetch` site for the unified builder (POC 22). Every pane goes through this module, so
 * the request shaping and response parsing live in one place that the tests can mock `fetch` against
 * once. Each exported method is one backend endpoint, typed by the DTOs in {@link ./types}.
 */

import type {
  CanvasRequest,
  CanvasResponse,
  ComposeRequest,
  ComposeResponse,
  CurateRequest,
  CurateResponse,
  EvalRequest,
  EvalResponse,
  RemoteRequest,
  RemoteResponse,
  RetrieveRequest,
  RetrieveResponse,
  RunRequest,
  RunResponse,
  CompileRequest,
  CompileResponse,
  ScaleResponse,
  SecurityRequest,
  SecurityResponse,
  SkillInfo,
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

function post<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

// ---- Compose (POC 14) ------------------------------------------------------
export const listSkills = (): Promise<SkillInfo[]> => get<SkillInfo[]>('/api/skills')
export const validateCanvas = (req: CanvasRequest): Promise<CanvasResponse> =>
  post<CanvasResponse>('/api/canvas', req)
export const compose = (req: ComposeRequest): Promise<ComposeResponse> =>
  post<ComposeResponse>('/api/compose', req)

// ---- Retrieve (POC 12/18) --------------------------------------------------
export const retrieve = (req: RetrieveRequest): Promise<RetrieveResponse> =>
  post<RetrieveResponse>('/api/retrieve', req)

// ---- Security (POC 21) -----------------------------------------------------
export const scan = (req: SecurityRequest): Promise<SecurityResponse> =>
  post<SecurityResponse>('/api/security', req)

// ---- Trust (POC 10) --------------------------------------------------------
export const checkTrust = (req: TrustRequest): Promise<TrustResponse> =>
  post<TrustResponse>('/api/trust', req)

// ---- Scale (POC 12/17/19/20) -----------------------------------------------
export const scale = (): Promise<ScaleResponse> => get<ScaleResponse>('/api/scale')

// ---- Run (POC 1, endpoint-gated) --------------------------------------------
export const run = (req: RunRequest): Promise<RunResponse> => post<RunResponse>('/api/run', req)

// ---- Author (POC 2, endpoint-gated) ----------------------------------------
export const compile = (req: CompileRequest): Promise<CompileResponse> =>
  post<CompileResponse>('/api/compile', req)

// ---- Eval (POC 9, endpoint-gated) ------------------------------------------
export const evaluate = (req: EvalRequest): Promise<EvalResponse> =>
  post<EvalResponse>('/api/eval', req)

// ---- Lifecycle (POC 18, endpoint-gated) ------------------------------------
export const curate = (req: CurateRequest): Promise<CurateResponse> =>
  post<CurateResponse>('/api/curate', req)

// ---- Remote (POC 13/16) ----------------------------------------------------
export const invokeRemote = (req: RemoteRequest): Promise<RemoteResponse> =>
  post<RemoteResponse>('/api/remote', req)
