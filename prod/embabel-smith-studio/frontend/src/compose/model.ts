/**
 * Pure logic for the Compose pane (change 07): the GOAP-flow graph rules and the spec-pack
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

// ---- Agent-spec assembly ---------------------------------------------------

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
 * Assemble the spec-pack the flow exports: the exported goal, the action steps, and a minimal
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

// ---- Generated-pack -> graph -----------------------------------------------

/** A node in the rendered flow graph (the Vue Flow node, carrying a step or the goal). */
export interface FlowNode {
  id: string
  type: 'step' | 'goal'
  position: { x: number; y: number }
  data: ComposeStep | ComposeGoal
}

/** A type-checked edge between a producer's output handle and a consumer's input handle. */
export interface FlowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
}

// Layout metrics shared by {@link packToNodes} so callers (e.g. fit-to-view) know the bounds.
export const LAYOUT = {
  /** Horizontal gap between layers. */
  colGap: 240,
  /** Vertical gap between rows within a layer. */
  rowGap: 150,
  /** Approximate node height, used to center layers on the vertical midline. */
  nodeHeight: 56,
} as const

/**
 * Lay out a generated {@link ComposePack} as a left-to-right layered graph: each step's x is its
 * topological depth (the longest chain of producers before it) and its y its position within that
 * layer, so parallel steps (e.g. the analysts) fan out in a row and the goal sits at the far right.
 * Edges are derived from the type-name wiring, matching the editor's handle ids.
 */
export function packToNodes(pack: ComposePack): { nodes: FlowNode[]; edges: FlowEdge[] } {
  const steps = pack.steps
  const producerOf = new Map<string, number>()
  steps.forEach((s, i) => {
    if (s.outputTypeName && !producerOf.has(s.outputTypeName)) {
      producerOf.set(s.outputTypeName, i)
    }
  })

  const memo = new Map<number, number>()
  function depth(i: number): number {
    if (memo.has(i)) return memo.get(i) as number
    let max = 0
    for (const input of steps[i].inputTypeNames) {
      if (INITIAL_TYPES.includes(input)) continue
      const producer = producerOf.get(input)
      if (producer !== undefined && producer !== i) {
        max = Math.max(max, depth(producer) + 1)
      }
    }
    memo.set(i, max)
    return max
  }

  const byLayer = new Map<number, number[]>()
  steps.forEach((_, i) => {
    const d = depth(i)
    if (!byLayer.has(d)) byLayer.set(d, [])
    ;(byLayer.get(d) as number[]).push(i)
  })
  const layers = [...byLayer.entries()].sort((a, b) => a[0] - b[0])
  const maxLayer = layers.length > 0 ? layers[layers.length - 1][0] : 0

  // Center each layer's rows on a common vertical midline so parallel steps fan out symmetrically
  // (instead of stacking from the top) and the whole flow sits in the middle of the canvas.
  const widest = Math.max(1, ...layers.map(([, idx]) => idx.length))
  const midY = ((widest - 1) * LAYOUT.rowGap) / 2

  const nodes: FlowNode[] = []
  for (const [layer, indices] of layers) {
    const offset = ((indices.length - 1) * LAYOUT.rowGap) / 2
    indices.forEach((i, row) => {
      const s = steps[i]
      nodes.push({
        id: 'step-' + i,
        type: 'step',
        position: { x: layer * LAYOUT.colGap, y: midY - offset + row * LAYOUT.rowGap },
        data: {
          name: s.name,
          description: s.description,
          inputTypeNames: s.inputTypeNames,
          outputTypeName: s.outputTypeName,
          prompt: s.prompt,
          toolGroups: s.toolGroups ?? [],
        },
      })
    })
  }
  nodes.push({
    id: 'goal',
    type: 'goal',
    position: { x: (maxLayer + 1) * LAYOUT.colGap, y: midY },
    data: {
      name: pack.goal.name,
      description: pack.goal.description,
      outputTypeName: pack.goal.outputTypeName,
    },
  })

  const edges: FlowEdge[] = []
  steps.forEach((s, i) => {
    for (const input of s.inputTypeNames) {
      if (INITIAL_TYPES.includes(input)) continue
      const producer = producerOf.get(input)
      if (producer !== undefined && producer !== i) {
        edges.push({
          id: `step-${producer}-out-${input}-step-${i}-in`,
          source: 'step-' + producer,
          target: 'step-' + i,
          sourceHandle: 'out:' + input,
          targetHandle: 'in:' + input,
        })
      }
    }
  })
  const goalProducer = producerOf.get(pack.goal.outputTypeName)
  if (goalProducer !== undefined) {
    edges.push({
      id: `step-${goalProducer}-out-${pack.goal.outputTypeName}-goal-in`,
      source: 'step-' + goalProducer,
      target: 'goal',
      sourceHandle: 'out:' + pack.goal.outputTypeName,
      targetHandle: 'in:' + pack.goal.outputTypeName,
    })
  }
  return { nodes, edges }
}
