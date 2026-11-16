import { describe, expect, it } from 'vitest'

import { assemblePack, isValidConnection, validateFlow, type ComposeStep } from '../compose/model'

function step(overrides: Partial<ComposeStep> = {}): ComposeStep {
  return {
    id: 's1',
    name: 'explain',
    description: 'explain the input',
    inputTypeNames: ['UserInput'],
    outputTypeName: 'Result',
    prompt: 'explain {{userInput.content}}',
    toolGroups: [],
    ...overrides,
  }
}

describe('isValidConnection', () => {
  it('allows a connection when the handle types match', () => {
    expect(isValidConnection('out:Result', 'in:Result')).toBe(true)
  })

  it('rejects a connection when the handle types differ', () => {
    expect(isValidConnection('out:Result', 'in:Draft')).toBe(false)
  })

  it('rejects a connection with a missing handle', () => {
    expect(isValidConnection(null, 'in:Result')).toBe(false)
    expect(isValidConnection('out:Result', null)).toBe(false)
  })
})

describe('validateFlow', () => {
  it('requires a goal', () => {
    expect(validateFlow(null, [step()])).toEqual(['Add a goal: the type the run returns.'])
  })

  it('requires at least one step', () => {
    expect(validateFlow({ name: 'g', description: '', outputTypeName: 'Result' }, [])).toEqual([
      "Add at least one action step.",
    ])
  })

  it('accepts a reachable flow whose inputs are fed', () => {
    const steps = [
      step({ outputTypeName: 'Draft' }),
      step({ id: 's2', name: 'polish', inputTypeNames: ['Draft'], outputTypeName: 'Result' }),
    ]
    expect(validateFlow({ name: 'g', description: '', outputTypeName: 'Result' }, steps)).toEqual([])
  })

  it('flags an unreachable goal', () => {
    const problems = validateFlow(
      { name: 'g', description: '', outputTypeName: 'Wanted' },
      [step()],
    )
    expect(problems).toContain("No step produces the goal's output type 'Wanted'.")
  })

  it('flags an unfed step input', () => {
    const problems = validateFlow(
      { name: 'g', description: '', outputTypeName: 'Result' },
      [step({ inputTypeNames: ['UserInput', 'Ghost'] })],
    )
    expect(problems).toContain("Step 'explain' needs input 'Ghost', which no step produces.")
  })
})

describe('assemblePack', () => {
  it('names the pack after the goal and exports it', () => {
    const pack = assemblePack(
      { name: 'explainer', description: 'explain', outputTypeName: 'Result' },
      [step()],
    )
    expect(pack.manifest.name).toBe('explainer')
    expect(pack.goal.export).toBe(true)
    expect(pack.goal.stepType).toBe('goal')
  })

  it('maps steps to action specs', () => {
    const pack = assemblePack(
      { name: 'explainer', description: 'explain', outputTypeName: 'Result' },
      [step()],
    )
    expect(pack.steps).toEqual([
      {
        stepType: 'action',
        name: 'explain',
        description: 'explain the input',
        inputTypeNames: ['UserInput'],
        outputTypeName: 'Result',
        prompt: 'explain {{userInput.content}}',
      },
    ])
  })

  it('defines each intermediate type and omits the initial type', () => {
    const steps = [
      step({ outputTypeName: 'Draft' }),
      step({ id: 's2', name: 'polish', inputTypeNames: ['Draft'], outputTypeName: 'Result' }),
    ]
    const pack = assemblePack(
      { name: 'explainer', description: 'explain', outputTypeName: 'Result' },
      steps,
    )
    expect(pack.types.map((t) => t.name).sort()).toEqual(['Draft', 'Result'])
  })
})
