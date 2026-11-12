/**
 * The TypeScript side of the TypeScript ↔ backend DTO contract for the unified builder (POC 22).
 *
 * Each interface mirrors a `com.embabel.smith.unified.dto` record field-for-field, so a backend
 * field rename is caught by `vue-tsc --noEmit` (and the client tests) rather than at runtime. The
 * canvas-specific types (`SpecInfo`, `Edge`) live in `canvas/model.ts` and are re-exported here so
 * the whole contract is importable from one place.
 */

import type { SpecInfo, Edge } from '../canvas/model'

export type { SpecInfo, Edge }

// ---- Compose (POC 14) ------------------------------------------------------
export interface CanvasRequest {
  nodes: string[]
  edges: Edge[]
}
export interface CanvasResponse {
  valid: boolean
  issues: string[]
  chain: string[]
}
export interface ComposeRequest {
  name: string
  nodes: string[]
  edges: Edge[]
}
export interface GoalInfo {
  name: string
  outputType: string
  export: boolean
}
export interface ComposeResponse {
  path: string | null
  goal: GoalInfo
  steps: string[]
  types: string[]
  issues: string[]
}

// ---- Retrieve (POC 12/18) --------------------------------------------------
export interface RetrieveRequest {
  query: string
  k: number
}
export interface RetrieveResult {
  name: string
  description: string
  score: number
  outputType: string
  state: string
}
export interface RetrieveResponse {
  scorer: string
  results: RetrieveResult[]
}

// ---- Security (POC 21) -----------------------------------------------------
export interface SecurityRequest {
  specName: string
}
export interface SecurityFinding {
  severity: string
  category: string
  message: string
  evidence: string
}
export interface SecurityResponse {
  error: string | null
  specName: string | null
  verdict: string | null
  recommendedTier: string | null
  summary: string | null
  findings: SecurityFinding[]
}

// ---- Trust (POC 10) --------------------------------------------------------
export interface TrustRequest {
  specName: string
  tier: string
}
export interface TrustResponse {
  error: string | null
  specName: string | null
  tier: string | null
  allowed: boolean
  disallowedGroups: string[]
  declaredToolGroups: string[]
}

// ---- Scale (POC 12/17/19/20) -----------------------------------------------
export interface ScalePoint {
  librarySize: number
  precisionAt1: number
}
export interface ScaleResponse {
  scorer: string
  coreSize: number
  coreP1: number
  fullSize: number
  fullP1: number
  curve: ScalePoint[]
}

// ---- Run (POC 1, endpoint-gated) --------------------------------------------
export interface RunRequest {
  specName: string
  input: string
}
export interface RunResponse {
  error: string | null
  output: string | null
}

// ---- Author (POC 2, endpoint-gated) ----------------------------------------
export interface ActionInfo {
  name: string
  inputTypes: string[]
  outputType: string
}
export interface AuthorDraft {
  goalName: string | null
  goalOutputType: string | null
  goalExport: boolean
  actions: ActionInfo[]
  qualityVerdict: string
  degenerate: boolean
  qualityReason: string | null
  issues: string[]
}
export interface CompileRequest {
  description: string
  agentName?: string
}
export interface CompileResponse {
  error: string | null
  draft: AuthorDraft | null
}

// ---- Eval (POC 9, endpoint-gated) ------------------------------------------
export interface EvalTask {
  input: string
  acceptanceCriteria: string
  mustContain: string
}
export interface EvalRequest {
  specName: string
  tasks?: EvalTask[]
}
export interface EvalResponse {
  error: string | null
  specName: string | null
  stepName: string | null
  beforeScore: number
  afterScore: number
  refined: boolean
  improved: boolean
  revisedPrompt: string | null
  failingInputs: string[]
}

// ---- Lifecycle (POC 18, endpoint-gated) ------------------------------------
export interface CurateRequest {
  specName: string
  tasks?: EvalTask[]
}
export interface CurateResponse {
  error: string | null
  specName: string | null
  before: string | null
  after: string | null
  contentDrift: boolean
  baselineScore: number
  currentScore: number
  verdict: string | null
  surfaced: boolean
}

// ---- Remote (POC 13/16) ----------------------------------------------------
export interface RemoteRequest {
  input: string
}
export interface RemoteResponse {
  running: boolean
  error: string | null
  action: string | null
  result: string | null
  mcpUrl: string | null
}
