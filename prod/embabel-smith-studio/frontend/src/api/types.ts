/**
 * The TypeScript side of the TypeScript ↔ backend DTO contract for the studio (change 06).
 *
 * Each interface mirrors a `com.embabel.smith.studio.rest.dto` record field-for-field, so a backend
 * field rename is caught by `vue-tsc --noEmit` (and the client tests) rather than at runtime.
 */

import type { ComposePack } from '../compose/model'

// ---- Global status ---------------------------------------------------------
export interface StatusInfo {
  configured: boolean
  model: string | null
  error: string | null
}

// ---- Sources / packs -------------------------------------------------------
export interface SourceInfo {
  uri: string
  kind: 'folder' | 'git'
}
export interface PackInfo {
  name: string
  contentHash: string
  lifecycleState: string | null
  baselinePassRate: number | null
  drift: boolean
  updatedAt: string | null
}

// ---- Security --------------------------------------------------------------
export interface ScanFinding {
  severity: string
  category: string
  message: string
  evidence: string
}
export interface ScanInfo {
  specName: string
  findings: ScanFinding[]
  verdict: string
  recommendedTier: string | null
  summary: string
}

// ---- Lifecycle / publish ---------------------------------------------------
export interface LifecycleInfo {
  contentHash: string
  baselinePassRate: number
  state: string
  updatedAt: string
}
export interface PublishInfo {
  location: string
  pushed: boolean
}

// ---- Run (endpoint-gated) --------------------------------------------------
export interface RunRequest {
  source: string
  goal: string
  input: string
  planner?: string
  trustTier?: string
}
export interface RunResponse {
  success: boolean
  output: string | null
  error: string | null
  errorCode: string | null
  runId: string | null
}

// ---- Build (endpoint-gated) ------------------------------------------------
export interface BuildRequest {
  description: string
  outputSource: string
  maxRefine?: number
  overwrite?: boolean
  push?: boolean
}
export interface BuildResponse {
  status: 'BUILT' | 'BLOCKED'
  stage: string
  packName: string | null
  score: number | null
  issues: string[]
  scan: ScanInfo | null
  lifecycle: LifecycleInfo | null
  published: PublishInfo | null
  errorCode: string | null
  error: string | null
}

// ---- Compose (offline) -----------------------------------------------------
export interface ComposeRequest {
  source: string
  pack: ComposePack
  overwrite?: boolean
  push?: boolean
}
export interface ComposeResponse {
  ok: boolean
  packName: string | null
  goal: string | null
  published: PublishInfo | null
  lifecycle: LifecycleInfo | null
  error: string | null
}

// ---- Generate (endpoint-gated) ---------------------------------------------
export type GenerateTechnique = 'single-shot' | 'embabel'

export interface GenerateRequest {
  blueprint?: string
  path?: string
  technique?: GenerateTechnique
}
export interface GenerateResponse {
  pack: ComposePack | null
  issues: string[] | null
  error: string | null
  errorCode: string | null
}

// ---- Simulate (endpoint-gated) ---------------------------------------------
export interface SimulateStep {
  name: string
  status: string
  durationMs: number
  outputExcerpt: string | null
}
export interface SimulateRequest {
  pack: ComposePack
  input?: string
}
export interface SimulateResponse {
  success: boolean
  output: string | null
  error: string | null
  errorCode: string | null
  steps: SimulateStep[]
}

// ---- Discover / ingest -----------------------------------------------------
export interface DiscoverRequest {
  path?: string
  url?: string
}
export interface DiscoverResponse {
  sourceType: string | null
  location: string | null
  docs: Record<string, string> | null
  error: string | null
}

// ---- Extract (endpoint-gated) ----------------------------------------------
export interface ExtractRequest {
  path?: string
  url?: string
}
export interface ExtractResponse {
  description: string | null
  error: string | null
  errorCode: string | null
}

// ---- Durable store ---------------------------------------------------------
export interface StoredPackInfo {
  name: string
  goal: string | null
  steps: number
  savedAt: string | null
}
export interface StoredPackResponse {
  pack: ComposePack | null
  error: string | null
}
export interface SavePackRequest {
  pack: ComposePack
}
export interface SavePackResponse {
  ok: boolean
  name: string | null
  error: string | null
}
export interface DiscoveryRecord {
  timestamp: string
  sourceType: string
  location: string
  docCount: number
}

/** The pack database endpoint as persisted in the storage settings. */
export interface StorageDbSettings {
  url: string | null
  user: string | null
  password: string | null
}
/** The trace backend selection; `type` is jsonl / postgres-same / postgres-separate. */
export interface StorageTraceSettings {
  type: string
  url: string | null
  user: string | null
  password: string | null
}
export interface StorageSettings {
  packDb: StorageDbSettings
  trace: StorageTraceSettings
}
/** The Storage pane's save form (mirrors the backend PUT body). */
export interface StorageSettingsSaveRequest {
  url: string
  user: string
  password: string
  traceType: string
  traceUrl?: string
  traceUser?: string
  tracePassword?: string
}
export interface StorageSettingsSaveResponse {
  ok: boolean
  settings: StorageSettings | null
  error: string | null
}

// ---- Pack import -----------------------------------------------------------
export interface ImportPackRequest {
  path: string
}
export interface ImportPackResponse {
  ok: boolean
  name: string | null
  error: string | null
}

// ---- Traces ----------------------------------------------------------------
export interface StepInfo {
  name: string
  status: string
  durationMs: number
  outputExcerpt: string | null
}
export interface TraceInfo {
  runId: string
  timestamp: string
  sourceUri: string | null
  sourceRef: string | null
  resolvedAt: string | null
  pack: string | null
  goal: string | null
  input: string | null
  planner: string | null
  trustTier: string | null
  steps: StepInfo[]
  success: boolean
  error: string | null
  outputExcerpt: string | null
}

// ---- Improvement loop ------------------------------------------------------
export interface CandidateInfo {
  id: string
  spec: string
  score: number
  baseline: number
  diffSummary: string
  scan: ScanInfo | null
  evidence: TraceInfo[]
  proposedState: string
}
export interface LoopStatus {
  running: boolean
  autoPublish: boolean
}
export interface AutoPublishRequest {
  autoPublish: boolean
}
export interface IterationInfo {
  ran: boolean
  candidates: number
  publishedSpecs: string[]
  queuedSpecs: string[]
  autoPublish: boolean
  errorCode: string | null
  error: string | null
}
export interface ApproveResponse {
  ok: boolean
  location: string | null
  error: string | null
}
export interface RejectResponse {
  ok: boolean
  error: string | null
}

// ---- Eval (endpoint-gated) -------------------------------------------------
export interface EvalTaskDto {
  name: string
  input: string
  expected: string
}
export interface EvalRequest {
  source: string
  spec: string
  tasks?: EvalTaskDto[]
}
export interface EvalResponse {
  error: string | null
  errorCode: string | null
  spec: string | null
  score: number | null
  refinementCount: number
  failingTasks: string[]
  passed: boolean
  revisedPrompt: string | null
}

// ---- Security / Trust (offline) --------------------------------------------
export interface SecurityRequest {
  source: string
  spec: string
}
export interface SecurityResponse {
  error: string | null
  scan: ScanInfo | null
}
export interface TrustRequest {
  source: string
  spec: string
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

/**
 * True when a response is the structured "no model endpoint configured" error — the endpoint-gated
 * panes (Run, Build, Eval) render a dedicated banner for it rather than a generic failure.
 */
export function isModelNotConfigured(response: {
  errorCode?: string | null
} | null | undefined): boolean {
  return response?.errorCode === 'MODEL_NOT_CONFIGURED'
}
