/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

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

import { Graph, layout } from '@dagrejs/dagre'

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

// Layout metrics shared by {@link layoutFlow} so callers (e.g. fit-to-view) know the bounds.
export const LAYOUT = {
  /** Gap between ranks, measured along the flow (dagre's `ranksep`). */
  colGap: 240,
  /** Gap between siblings within a rank, across the flow (dagre's `nodesep`). */
  rowGap: 150,
  /** Approximate box a step renders at — the layout spaces around this, not around the text. */
  nodeWidth: 200,
  nodeHeight: 56,
} as const

/**
 * Places a flow with dagre's layered (Sugiyama) layout — the route Vue Flow's own layout example
 * takes: rank the nodes by their producer chain, stand dummy nodes in for every edge that spans a
 * rank so a long edge cannot drag a rank ordering out of true, order each rank to cut crossings,
 * then compact the columns.
 *
 * Spacing is stated in node terms and never in viewport terms: the canvas frames the result with
 * fit-to-view, so re-laying-out after zooming out yields the same tidy graph, simply seen further
 * back. Positions come back anchored at the top-left corner, which is what Vue Flow expects.
 */
export function layoutFlow(
  nodes: readonly { id: string }[],
  edges: readonly FlowEdge[],
): Record<string, { x: number; y: number }> {
  const positions: Record<string, { x: number; y: number }> = {}
  if (nodes.length === 0) return positions
  const known = new Set(nodes.map((n) => n.id))

  const graph = new Graph().setDefaultEdgeLabel(() => ({}))
  graph.setGraph({ rankdir: 'LR', ranksep: LAYOUT.colGap, nodesep: LAYOUT.rowGap })
  for (const n of nodes) graph.setNode(n.id, { width: LAYOUT.nodeWidth, height: LAYOUT.nodeHeight })
  // An edge to a node the graph never met would be created without dimensions, and dagre would
  // happily lay out a box of NaNs.
  for (const e of edges) {
    if (known.has(e.source) && known.has(e.target) && e.source !== e.target) {
      graph.setEdge(e.source, e.target)
    }
  }
  layout(graph)

  for (const n of nodes) {
    const placed = graph.node(n.id)
    // dagre anchors a node at its centre; Vue Flow anchors it at its top-left.
    positions[n.id] = { x: placed.x - LAYOUT.nodeWidth / 2, y: placed.y - LAYOUT.nodeHeight / 2 }
  }
  return positions
}

/**
 * Lay out a generated {@link ComposePack} as a flow graph through {@link layoutFlow}, so a
 * generated flow and a hand-drawn one re-tidied by the canvas are laid out by the same rules.
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

  // Positions come from the layout below; the nodes only have to exist for their wiring to be built.
  const nodes: FlowNode[] = steps.map((s, i) => ({
    id: 'step-' + i,
    type: 'step',
    position: { x: 0, y: 0 },
    data: {
      name: s.name,
      description: s.description,
      inputTypeNames: s.inputTypeNames,
      outputTypeName: s.outputTypeName,
      prompt: s.prompt,
      toolGroups: s.toolGroups ?? [],
    },
  }))
  nodes.push({
    id: 'goal',
    type: 'goal',
    position: { x: 0, y: 0 },
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
  // Ordered by where they land, so the node list reads left-to-right, top-to-bottom, the same order
  // the canvas colors the steps in.
  const positions = layoutFlow(nodes, edges)
  const ordered = [...nodes].sort(
    (a, b) => positions[a.id].x - positions[b.id].x || positions[a.id].y - positions[b.id].y,
  )
  return { nodes: ordered.map((n) => ({ ...n, position: positions[n.id] })), edges }
}

// ---- Edge colors -----------------------------------------------------------

/** Stroke of an edge that feeds the focused node: everything flowing *into* it. */
export const FOCUS_INPUT_COLOR = '#22c55e'

/** Stroke of an edge that leaves the focused node: everything it feeds downstream. */
export const FOCUS_OUTPUT_COLOR = '#ef4444'

/** Stroke of an edge the focused node neither feeds nor consumes: background, not information. */
export const FOCUS_NEUTRAL_COLOR = '#ffffff'

/**
 * Identity stroke of a step's output edges while nothing is focused, and the border of its box —
 * the border is what keys the colors, so a step's fan-out is traceable by eye. Nine hues chosen so
 * a generated flow gives every step its own color, and none of them the green, red or white the
 * focus state owns, which must never be mistaken for an identity.
 */
export const STEP_EDGE_COLORS: readonly string[] = [
  '#60a5fa', // blue
  '#fbbf24', // amber
  '#a78bfa', // violet
  '#22d3ee', // cyan
  '#f472b6', // pink
  '#fb923c', // orange
  '#e879f9', // fuchsia
  '#2dd4bf', // teal
  '#94a3b8', // slate
]

/**
 * Identity color of each node, assigned in graph order and cycling the palette. One map keys both
 * the edges (a step's out-edges carry its color) and that step's box border.
 */
export function nodeEdgeColors(nodes: readonly { id: string }[]): Record<string, string> {
  const colors: Record<string, string> = {}
  nodes.forEach((n, i) => {
    colors[n.id] = STEP_EDGE_COLORS[i % STEP_EDGE_COLORS.length]
  })
  return colors
}

/**
 * Stroke of one edge. Focus wins: an edge touching the focused node is read as an input (green) or
 * an output (red) of it, and every other edge falls back to white so the pair stands out. With
 * nothing focused the edge carries its producer's identity color.
 */
export function edgeStroke(
  edge: FlowEdge,
  colors: Readonly<Record<string, string>>,
  focusedNodeId: string | null,
): string {
  if (focusedNodeId !== null) {
    if (edge.target === focusedNodeId) return FOCUS_INPUT_COLOR
    if (edge.source === focusedNodeId) return FOCUS_OUTPUT_COLOR
    return FOCUS_NEUTRAL_COLOR
  }
  return colors[edge.source] ?? STEP_EDGE_COLORS[0]
}

/** The edges as the canvas needs them: topology plus the stroke that says what each one does. */
export function styleEdges<T extends FlowEdge>(
  edges: readonly T[],
  nodes: readonly { id: string }[],
  focusedNodeId: string | null,
): (T & { style: { stroke: string; strokeWidth: number } })[] {
  const colors = nodeEdgeColors(nodes)
  return edges.map((e) => ({
    ...e,
    style: { stroke: edgeStroke(e, colors, focusedNodeId), strokeWidth: 2 },
  }))
}
