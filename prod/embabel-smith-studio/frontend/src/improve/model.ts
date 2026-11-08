/**
 * Pure logic for the Auto-improve pane (change 06): lifecycle badge colors, the score delta over the
 * baseline, the evidence count, and the loop status label. No DOM, no Vue — trivially unit-testable.
 */

import type { CandidateInfo, LoopStatus } from '../api/types'

/** A Quasar color for a lifecycle-state badge. */
export function lifecycleColor(state: string | null): string {
  switch (state) {
    case 'ACTIVE':
      return 'positive'
    case 'DEGRADED':
      return 'warning'
    case 'RETIRED':
      return 'negative'
    case 'INCUBATING':
      return 'grey'
    default:
      return 'grey'
  }
}

/** The candidate's score improvement over the baseline (positive = better). */
export function scoreDelta(candidate: CandidateInfo): number {
  return candidate.score - candidate.baseline
}

/** A short label for how many traces triggered the candidate. */
export function evidenceCount(candidate: CandidateInfo): string {
  return `${candidate.evidence.length} triggering trace(s)`
}

/** The loop schedule label for the status card. */
export function loopStatusLabel(status: LoopStatus): string {
  return status.running ? 'running' : 'stopped'
}
