import { describe, expect, it } from 'vitest'

import type { CandidateInfo, LoopStatus, TraceInfo } from '../api/types'
import { evidenceCount, lifecycleColor, loopStatusLabel, scoreDelta } from '../improve/model'

function trace(runId: string): TraceInfo {
  return {
    runId,
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
    success: false,
    error: 'boom',
    outputExcerpt: null,
  }
}

function candidate(partial: Partial<CandidateInfo>): CandidateInfo {
  return {
    id: 'c1',
    skill: 'alpha',
    score: 0.9,
    baseline: 0.7,
    diffSummary: 'tightened prompt',
    scan: null,
    evidence: [],
    proposedState: 'ACTIVE',
    ...partial,
  }
}

describe('improve/model', () => {
  it('maps lifecycle states to badge colors', () => {
    expect(lifecycleColor('ACTIVE')).toBe('positive')
    expect(lifecycleColor('DEGRADED')).toBe('warning')
    expect(lifecycleColor('RETIRED')).toBe('negative')
    expect(lifecycleColor('INCUBATING')).toBe('grey')
    expect(lifecycleColor(null)).toBe('grey')
  })

  it('computes the score delta over the baseline', () => {
    expect(scoreDelta(candidate({ score: 0.9, baseline: 0.7 }))).toBeCloseTo(0.2)
    expect(scoreDelta(candidate({ score: 0.5, baseline: 0.7 }))).toBeCloseTo(-0.2)
  })

  it('counts triggering traces', () => {
    expect(evidenceCount(candidate({}))).toBe('0 triggering trace(s)')
    expect(evidenceCount(candidate({ evidence: [trace('a'), trace('b')] }))).toBe(
      '2 triggering trace(s)',
    )
  })

  it('labels the loop schedule', () => {
    expect(loopStatusLabel({ running: true, autoPublish: false })).toBe('running')
    expect(loopStatusLabel({ running: false, autoPublish: true })).toBe('stopped')
  })
})
