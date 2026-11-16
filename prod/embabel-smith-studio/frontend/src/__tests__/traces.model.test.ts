import { describe, expect, it } from 'vitest'

import type { TraceInfo } from '../api/types'
import {
  failureReason,
  formatDuration,
  hasSteps,
  runStatusLabel,
  stepStatusColor,
} from '../traces/model'

function trace(partial: Partial<TraceInfo>): TraceInfo {
  return {
    runId: 'r1',
    timestamp: '2026-01-01T00:00:00Z',
    sourceUri: null,
    sourceRef: null,
    resolvedAt: null,
    pack: 'alpha',
    goal: 'g',
    input: null,
    planner: null,
    trustTier: null,
    steps: [],
    success: true,
    error: null,
    outputExcerpt: null,
    ...partial,
  }
}

describe('traces/model', () => {
  it('labels a run ok or failed', () => {
    expect(runStatusLabel(trace({ success: true }))).toBe('ok')
    expect(runStatusLabel(trace({ success: false }))).toBe('failed')
  })

  it('returns the failure reason only for failed runs', () => {
    expect(failureReason(trace({ success: true }))).toBeNull()
    expect(failureReason(trace({ success: false, error: 'boom' }))).toBe('boom')
    expect(failureReason(trace({ success: false, error: null }))).toBe('run failed')
  })

  it('colors step statuses', () => {
    expect(stepStatusColor('completed')).toBe('positive')
    expect(stepStatusColor('failed')).toBe('negative')
    expect(stepStatusColor('pending')).toBe('grey')
  })

  it('formats durations under and over a second', () => {
    expect(formatDuration(120)).toBe('120ms')
    expect(formatDuration(1500)).toBe('1.50s')
  })

  it('reports whether a run has steps', () => {
    expect(hasSteps(trace({}))).toBe(false)
    expect(
      hasSteps(trace({ steps: [{ name: 's', status: 'completed', durationMs: 1, outputExcerpt: null }] })),
    ).toBe(true)
  })
})
