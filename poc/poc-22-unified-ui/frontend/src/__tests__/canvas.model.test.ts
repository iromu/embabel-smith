import { describe, expect, it } from 'vitest'
import { checkEdge, validateCanvas, type SkillInfo } from '@/canvas/model'

// A minimal two-skill chain, mirroring the repo's compose-extract -> compose-explain:
// extract reads UserInput and emits an intermediate topic; explain consumes it and emits the goal.
const extract: SkillInfo = {
  name: 'compose-extract',
  externalInputTypes: ['com.embabel.agent.domain.io.UserInput'],
  outputType: 'com.example.ExtractedTopic',
}
const explain: SkillInfo = {
  name: 'compose-explain',
  externalInputTypes: ['com.example.ExtractedTopic'],
  outputType: 'com.embabel.agent.spec.model.BackgroundMessage',
}
const skills = [extract, explain]

describe('checkEdge (the snap rule)', () => {
  it('accepts a type-compatible connection', () => {
    const check = checkEdge(extract, explain)
    expect(check.valid).toBe(true)
    expect(check.reason).toBeUndefined()
  })

  it('flags a type-incompatible connection with the reason', () => {
    const check = checkEdge(explain, extract)
    expect(check.valid).toBe(false)
    expect(check.reason).toMatch(/BackgroundMessage.*not one of compose-extract's inputs/)
  })
})

describe('validateCanvas (chain composition)', () => {
  it('accepts a valid single chain and returns the composed path', () => {
    const result = validateCanvas({
      nodes: ['compose-extract', 'compose-explain'],
      edges: [{ from: 'compose-extract', to: 'compose-explain' }],
      skills,
    })
    expect(result.valid).toBe(true)
    expect(result.issues).toEqual([])
    expect(result.chain).toEqual(['compose-extract', 'compose-explain'])
  })

  it('rejects disconnected nodes', () => {
    const result = validateCanvas({
      nodes: ['compose-extract', 'compose-explain'],
      edges: [],
      skills,
    })
    expect(result.valid).toBe(false)
    expect(result.issues.length).toBeGreaterThan(0)
    expect(result.chain).toEqual([])
  })

  it('rejects a type-incompatible edge, flagging the reason', () => {
    const result = validateCanvas({
      nodes: ['compose-extract', 'compose-explain'],
      edges: [{ from: 'compose-explain', to: 'compose-extract' }],
      skills,
    })
    expect(result.valid).toBe(false)
    expect(result.issues.some((i) => i.includes('type-incompatible'))).toBe(true)
  })

  it('rejects a cycle', () => {
    const result = validateCanvas({
      nodes: ['compose-extract', 'compose-explain'],
      edges: [
        { from: 'compose-extract', to: 'compose-explain' },
        { from: 'compose-explain', to: 'compose-extract' },
      ],
      skills,
    })
    expect(result.valid).toBe(false)
    expect(result.issues.length).toBeGreaterThan(0)
  })

  it('ignores edges that reference skills not on the canvas', () => {
    const result = validateCanvas({
      nodes: ['compose-extract'],
      edges: [{ from: 'compose-extract', to: 'ghost' }],
      skills,
    })
    // A single entry with no exit is already invalid, but the dangling edge must be reported too.
    expect(result.valid).toBe(false)
    expect(result.issues.some((i) => i.includes('references a missing skill'))).toBe(true)
  })
})
