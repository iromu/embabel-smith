/**
 * Pure logic for the Compose pane (change 07): the GOAP-flow graph rules and the skill-pack
 * assembly. No DOM, no Vue — trivially unit-testable.
 *
 * A flow is a set of action steps plus a single goal. Steps connect into a graph by type name: an
 * action's `outputTypeName` feeds any consumer whose `inputTypeNames` include that type, and the
 * goal is reached when some step produces its `outputTypeName`. GOAP plans over those joins at run
 * time; the drawn edges in the editor are a visual aid that must stay consistent with the type
 * names (enforced by {@link isValidConnection}).
 */

/** A step node in the flow graph: an action with typed inputs and a single typed output. */
export interface ComposeStep {
  id: string
  name: string
  description: string
  inputTypeNames: string[]
  outputTypeName: string
  prompt: string
  toolGroups: string[]
}

/** The single goal node: the type the run returns. */
export interface ComposeGoal {
  name: string
  description: string
  outputTypeName: string
}

/** The type names available at run start, before any step runs. */
export const INITIAL_TYPES = ['UserInput']

/**
 * Whether a connection between two handles is type-compatible: a step's output type must match the
 * consuming handle's input type. Handles carry their type in the id as `in:<Type>` / `out:<Type>`.
 */
export function isValidConnection(
  sourceHandleId: string | null,
  targetHandleId: string | null,
): boolean {
  if (sourceHandleId == null || targetHandleId == null) return false
  const sourceType = sourceHandleId.split(':').pop()
  const targetType = targetHandleId.split(':').pop()
  return sourceType != null && sourceType === targetType
}

/**
 * Validate the flow as a GOAP graph. The goal must be reachable (some step produces its output
 * type) and every step input must be fed (produced by some step, or an initial type). Returns the
 * list of problems — empty when the flow is sound.
 */
export function validateFlow(goal: ComposeGoal | null, steps: ComposeStep[]): string[] {
  const problems: string[] = []
  if (!goal) {
    problems.push('Add a goal: the type the run returns.')
    return problems
  }
  if (steps.length === 0) {
    problems.push('Add at least one action step.')
    return problems
  }
  const produced = new Set(steps.map((s) => s.outputTypeName))
  if (!produced.has(goal.outputTypeName)) {
    problems.push(`No step produces the goal's output type '${goal.outputTypeName}'.`)
  }
  for (const step of steps) {
    for (const input of step.inputTypeNames) {
      const fed = produced.has(input) || INITIAL_TYPES.includes(input)
      if (!fed) {
        problems.push(`Step '${step.name}' needs input '${input}', which no step produces.`)
      }
    }
  }
  return problems
}

// ---- Skill-pack assembly ---------------------------------------------------

export interface ComposePackManifest {
  name: string
  version: string
}
export interface ComposeGoalSpec {
  stepType: 'goal'
  name: string
  description: string
  outputTypeName: string
  export: boolean
}
export interface ComposeActionSpec {
  stepType: 'action'
  name: string
  description: string
  inputTypeNames: string[]
  outputTypeName: string
  prompt: string
  toolGroups?: string[]
}
export interface ComposeTypeDef {
  name: string
  description: string
}
export interface ComposePack {
  manifest: ComposePackManifest
  goal: ComposeGoalSpec
  steps: ComposeActionSpec[]
  types: ComposeTypeDef[]
}

/**
 * Assemble the skill-pack the flow exports: the exported goal, the action steps, and a minimal
 * definition for each intermediate type — every produced type except the initial types, which the
 * runtime already knows. The pack name is the goal's name.
 */
export function assemblePack(goal: ComposeGoal, steps: ComposeStep[]): ComposePack {
  const types = new Map<string, string>()
  for (const step of steps) {
    if (!INITIAL_TYPES.includes(step.outputTypeName)) {
      types.set(step.outputTypeName, `Produced by step '${step.name}'.`)
    }
  }
  return {
    manifest: { name: goal.name, version: '1.0.0' },
    goal: {
      stepType: 'goal',
      name: goal.name,
      description: goal.description,
      outputTypeName: goal.outputTypeName,
      export: true,
    },
    steps: steps.map((s) => ({
      stepType: 'action' as const,
      name: s.name,
      description: s.description,
      inputTypeNames: s.inputTypeNames,
      outputTypeName: s.outputTypeName,
      prompt: s.prompt,
      toolGroups: s.toolGroups.length > 0 ? s.toolGroups : undefined,
    })),
    types: [...types.entries()].map(([name, description]) => ({ name, description })),
  }
}
