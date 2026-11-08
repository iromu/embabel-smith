/**
 * Pure logic for the Traces pane (change 06): status labels, failure reasons, step status colors,
 * and duration formatting. No DOM, no Vue — trivially unit-testable.
 */

import type { TraceInfo } from '../api/types'

/** A short status label for a run. */
export function runStatusLabel(trace: TraceInfo): string {
  return trace.success ? 'ok' : 'failed'
}

/** The failure reason for a run, or null when it succeeded. */
export function failureReason(trace: TraceInfo): string | null {
  return trace.success ? null : trace.error ?? 'run failed'
}

/** A Quasar color for a step's status. */
export function stepStatusColor(status: string): string {
  return status === 'completed' ? 'positive' : status === 'failed' ? 'negative' : 'grey'
}

/** Human-readable duration (ms, switching to seconds above a second). */
export function formatDuration(ms: number): string {
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(2)}s`
}

/** Whether a run has any step records (drives the drill-down affordance). */
export function hasSteps(trace: TraceInfo): boolean {
  return trace.steps.length > 0
}
