import { describe, expect, it } from 'vitest'

import {
  FOCUS_INPUT_COLOR,
  FOCUS_NEUTRAL_COLOR,
  FOCUS_OUTPUT_COLOR,
  LAYOUT,
  STEP_EDGE_COLORS,
  assemblePack,
  edgeStroke,
  isValidConnection,
  nodeEdgeColors,
  layoutFlow,
  packToNodes,
  styleEdges,
  validateFlow,
  type ComposeStep,
  type FlowEdge,
} from '../compose/model'

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

function node(id: string, x = 0, y = 0): { id: string; position: { x: number; y: number } } {
  return { id, position: { x, y } }
}

function edge(source: string, target: string): FlowEdge {
  return { id: `${source}->${target}`, source, target }
}

describe('edge colors', () => {
  const nodes = [node('step-0'), node('step-1'), node('goal')]
  const colors = nodeEdgeColors(nodes)

  it('gives every step its own color while nothing is focused', () => {
    expect(new Set(Object.values(colors)).size).toBe(nodes.length)
  })

  it('colors an edge with the color of the step that produces it', () => {
    expect(edgeStroke(edge('step-0', 'goal'), colors, null)).toBe(colors['step-0'])
    expect(edgeStroke(edge('step-1', 'goal'), colors, null)).toBe(colors['step-1'])
  })

  it('colors every line leaving a step with that step’s color', () => {
    const fanOut = [edge('step-0', 'step-1'), edge('step-0', 'goal')]
    const strokes = new Set(fanOut.map((e) => edgeStroke(e, colors, null)))
    expect([...strokes]).toEqual([colors['step-0']])
  })

  it('reads an edge into the focused step as green and one out of it as red', () => {
    expect(edgeStroke(edge('step-0', 'step-1'), colors, 'step-1')).toBe(FOCUS_INPUT_COLOR)
    expect(edgeStroke(edge('step-1', 'goal'), colors, 'step-1')).toBe(FOCUS_OUTPUT_COLOR)
  })

  it('whitens every line the focused step neither feeds nor consumes', () => {
    expect(edgeStroke(edge('step-0', 'step-1'), colors, 'step-0')).not.toBe(FOCUS_NEUTRAL_COLOR)
    expect(edgeStroke(edge('step-0', 'step-1'), colors, 'goal')).toBe(FOCUS_NEUTRAL_COLOR)
  })

  it('keeps the identity hues clear of the colors the focus state owns', () => {
    expect(STEP_EDGE_COLORS).not.toContain(FOCUS_INPUT_COLOR)
    expect(STEP_EDGE_COLORS).not.toContain(FOCUS_OUTPUT_COLOR)
    expect(STEP_EDGE_COLORS).not.toContain(FOCUS_NEUTRAL_COLOR)
  })

  it('gives the focused goal green inputs, since a goal has no outputs', () => {
    const strokes = [edge('step-0', 'goal'), edge('step-1', 'goal')].map((e) =>
      edgeStroke(e, colors, 'goal'),
    )
    expect(strokes).toEqual([FOCUS_INPUT_COLOR, FOCUS_INPUT_COLOR])
  })

  it('keeps coloring steps past the end of the palette', () => {
    const many = Array.from({ length: STEP_EDGE_COLORS.length + 2 }, (_, i) => node(`s${i}`))
    const wide = nodeEdgeColors(many)
    expect(wide['s0']).toBe(wide[`${'s'}${STEP_EDGE_COLORS.length}`])
    expect(wide['s0']).not.toBe(wide['s1'])
  })

  it('styles the edges the canvas is handed, keeping their wiring', () => {
    const styled = styleEdges([edge('step-0', 'goal')], nodes, 'goal')
    expect(styled).toEqual([
      {
        id: 'step-0->goal',
        source: 'step-0',
        target: 'goal',
        style: { stroke: FOCUS_INPUT_COLOR, strokeWidth: 2 },
      },
    ])
  })
})

/** Two placed boxes sharing a pixel — which a layout must never let happen. */
function collide(a: { x: number; y: number }, b: { x: number; y: number }): boolean {
  return (
    a.x < b.x + LAYOUT.nodeWidth &&
    b.x < a.x + LAYOUT.nodeWidth &&
    a.y < b.y + LAYOUT.nodeHeight &&
    b.y < a.y + LAYOUT.nodeHeight
  )
}

function placedApart(at: Record<string, { x: number; y: number }>): void {
  const boxes = Object.values(at)
  for (let i = 0; i < boxes.length; i++) {
    for (let j = i + 1; j < boxes.length; j++) {
      expect(collide(boxes[i], boxes[j])).toBe(false)
    }
  }
}

describe('layoutFlow', () => {
  it('places every node it is handed, at a real coordinate', () => {
    const at = layoutFlow(
      [node('a'), node('b'), node('goal')],
      [edge('a', 'b'), edge('b', 'goal')],
    )
    expect(Object.keys(at).sort()).toEqual(['a', 'b', 'goal'])
    for (const p of Object.values(at)) {
      expect(Number.isFinite(p.x)).toBe(true)
      expect(Number.isFinite(p.y)).toBe(true)
    }
  })

  it('puts a consumer to the right of the step that feeds it', () => {
    const at = layoutFlow([node('a'), node('b'), node('goal')], [edge('a', 'b'), edge('b', 'goal')])
    expect(at['a'].x).toBeLessThan(at['b'].x)
    expect(at['b'].x).toBeLessThan(at['goal'].x)
  })

  it('leaves the rank gap between a step and what it feeds', () => {
    const at = layoutFlow([node('a'), node('b')], [edge('a', 'b')])
    expect(at['b'].x - at['a'].x).toBeGreaterThanOrEqual(LAYOUT.nodeWidth + LAYOUT.colGap)
  })

  it('hands over a top-left anchor, which is the corner Vue Flow places a node by', () => {
    // dagre reports where a node's centre went. Relative gaps cannot tell the two anchors apart,
    // so this pins the absolute corner: a lone chain starts at the origin it was laid out in.
    expect(layoutFlow([node('a'), node('b')], [edge('a', 'b')])).toEqual({
      a: { x: 0, y: 0 },
      b: { x: LAYOUT.nodeWidth + LAYOUT.colGap, y: 0 },
    })
  })

  it('fans siblings out by the sibling gap', () => {
    const at = layoutFlow(
      [node('root'), node('c1'), node('c2')],
      [edge('root', 'c1'), edge('root', 'c2')],
    )
    expect(Math.abs(at['c2'].y - at['c1'].y)).toBeGreaterThanOrEqual(LAYOUT.nodeHeight + LAYOUT.rowGap)
  })

  it('never stacks boxes on a fan-out that also skips a rank', () => {
    // a feeds all three, and b also feeds c — the rank-spanning edge a->d is what a layout
    // without dummy nodes has to route around.
    const at = layoutFlow(
      [node('a'), node('b'), node('c'), node('d')],
      [edge('a', 'b'), edge('a', 'c'), edge('a', 'd'), edge('b', 'c'), edge('b', 'd'), edge('c', 'd')],
    )
    placedApart(at)
  })

  it('orders a rank so two edges do not cross', () => {
    // p1 -> c2 crosses p2 -> c1 exactly when the two ranks end up in opposite orders.
    const at = layoutFlow(
      [node('p1'), node('p2'), node('c1'), node('c2')],
      [edge('p1', 'c1'), edge('p1', 'c2'), edge('p2', 'c1')],
    )
    expect(at['c1'].y > at['c2'].y).toBe(at['p1'].y < at['p2'].y)
    placedApart(at)
  })

  it('ignores an edge to a node the flow does not have', () => {
    // dagre would invent the stranger, give it no dimensions, and push the real boxes apart by it.
    const nodes = [node('a'), node('b')]
    const edges = [edge('a', 'b')]
    expect(layoutFlow(nodes, [...edges, edge('a', 'ghost')])).toEqual(layoutFlow(nodes, edges))
  })

  it('lays the same flow out the same way twice', () => {
    const nodes = [node('a'), node('b'), node('goal')]
    const edges = [edge('a', 'b'), edge('b', 'goal')]
    expect(layoutFlow(nodes, edges)).toEqual(layoutFlow(nodes, edges))
  })

  it('has nothing to place', () => {
    expect(layoutFlow([], [])).toEqual({})
  })
})

describe('packToNodes', () => {
  function chain() {
    return assemblePack(
      { name: 'pipeline', description: 'explain', outputTypeName: 'Result' },
      [
        step({ outputTypeName: 'Draft' }),
        step({ id: 's2', name: 'polish', inputTypeNames: ['Draft'], outputTypeName: 'Polished' }),
        step({ id: 's3', name: 'finalize', inputTypeNames: ['Polished'], outputTypeName: 'Result' }),
      ],
    )
  }

  it('lays a generated flow out through the same rules as a drawn one', () => {
    const pack = chain()
    const { nodes, edges } = packToNodes(pack)
    const at = layoutFlow(nodes, edges)
    expect(nodes.map((n) => n.id)).toEqual(['step-0', 'step-1', 'step-2', 'goal'])
    expect(nodes.map((n) => n.position)).toEqual(nodes.map((n) => at[n.id]))
  })

  it('hands the canvas its nodes in reading order, which is the order it colors them in', () => {
    const { nodes } = packToNodes(chain())
    const xs = nodes.map((n) => n.position.x)
    expect([...xs].sort((a, b) => a - b)).toEqual(xs)
    expect(nodes[nodes.length - 1].id).toBe('goal')
    placedApart(Object.fromEntries(nodes.map((n) => [n.id, n.position])))
  })
})
