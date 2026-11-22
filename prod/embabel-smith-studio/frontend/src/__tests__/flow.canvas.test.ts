import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Mock } from 'vitest'
import { QLayout, QPageContainer, Quasar } from 'quasar'
import { VueFlow } from '@vue-flow/core'
import { defineComponent, h } from 'vue'
import type { Component } from 'vue'

import ComposePane from '../panes/ComposePane.vue'
import ProcessWorkspace from '../panes/ProcessWorkspace.vue'
import * as api from '../api/client'
import {
  FOCUS_INPUT_COLOR,
  FOCUS_NEUTRAL_COLOR,
  FOCUS_OUTPUT_COLOR,
  STEP_EDGE_COLORS,
  layoutFlow,
  type ComposePack,
} from '../compose/model'

// jsdom has no ResizeObserver; Quasar's QResizeObserver and Vue Flow both need it to render.
vi.stubGlobal(
  'ResizeObserver',
  class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  },
)

// Stub the fetch-based calls the two flow editors make; keep the pure helpers real.
vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return {
    ...actual,
    listSources: vi.fn(),
    listModels: vi.fn(),
    compose: vi.fn(),
    discover: vi.fn(),
    extract: vi.fn(),
    generate: vi.fn(),
    importPack: vi.fn(),
    listDiscoveries: vi.fn(),
    listStoredPacks: vi.fn(),
    loadStoredPack: vi.fn(),
    savePack: vi.fn(),
    simulate: vi.fn(),
  }
})

const listSources = api.listSources as Mock
const listModels = api.listModels as Mock
const listStoredPacks = api.listStoredPacks as Mock
const listDiscoveries = api.listDiscoveries as Mock
const generate = api.generate as Mock

beforeEach(() => {
  vi.clearAllMocks()
  listSources.mockResolvedValue([{ uri: 'folder:/s', kind: 'folder' }])
  listModels.mockResolvedValue({ models: ['gpt-4.1'], defaultModel: 'gpt-4.1' })
  listStoredPacks.mockResolvedValue([])
  listDiscoveries.mockResolvedValue([])
})

function mountPane(component: Component) {
  return mount(
    defineComponent({
      setup() {
        return () => h(QLayout, () => h(QPageContainer, () => h(component)))
      },
    }),
    { global: { plugins: [Quasar] } },
  )
}

/** The stroke the canvas really painted on each edge, keyed by the edge id Vue Flow rendered. */
function paintedStrokes(wrapper: ReturnType<typeof mountPane>): Record<string, string> {
  const strokes: Record<string, string> = {}
  for (const g of wrapper.findAll('.vue-flow__edge')) {
    const style = g.find('path.vue-flow__edge-path').attributes('style') ?? ''
    strokes[g.attributes('data-id') ?? '?'] = (style.match(/stroke:\s*([^;]+)/) ?? [])[1] ?? ''
  }
  return strokes
}

/** The color a step's box is outlined in — the key that makes the edge colors readable. */
function boxColor(wrapper: ReturnType<typeof mountPane>, nodeId: string): string {
  const box = wrapper.find(`.vue-flow__node[data-id="${nodeId}"] .compose-node--step`)
  return (box.element as HTMLElement).style.getPropertyValue('--step-color')
}

/** Where the canvas actually placed a node, read off its rendered transform. */
function paintedPosition(wrapper: ReturnType<typeof mountPane>, nodeId: string) {
  const style = wrapper.find(`.vue-flow__node[data-id="${nodeId}"]`).attributes('style') ?? ''
  const m = style.match(/transform:\s*translate\(([-\d.]+)px,\s*([-\d.]+)px\)/)
  return m ? { x: Number(m[1]), y: Number(m[2]) } : null
}

/** Clicks a step on the canvas, the way a user focuses it. */
async function focusStep(wrapper: ReturnType<typeof mountPane>, nodeId: string) {
  await wrapper.find(`.vue-flow__node[data-id="${nodeId}"]`).trigger('click')
  await flushPromises()
}

/** Clicks the empty canvas, which drops the focus. */
async function blurCanvas(wrapper: ReturnType<typeof mountPane>) {
  await wrapper.find('.vue-flow__pane').trigger('click')
  await flushPromises()
}

/** A drag-connect between two handles, without a pointer. */
function connectOnCanvas(wrapper: ReturnType<typeof mountPane>, source: string, type: string) {
  wrapper.findComponent(VueFlow).vm.$emit('connect', {
    source,
    sourceHandle: `out:${type}`,
    target: 'goal',
    targetHandle: `in:${type}`,
  })
}

/** A compose canvas of two steps that each feed the goal, so each owns one output line. */
async function mountTwoStepCompose() {
  const wrapper = mountPane(ComposePane)
  await flushPromises()
  await wrapper.find('[data-test="compose-add-step"]').trigger('click')
  await wrapper.find('[data-test="compose-step-output"]').setValue('Result')
  await wrapper.find('[data-test="compose-add-step"]').trigger('click')
  await wrapper.find('[data-test="compose-step-output"]').setValue('Result')
  await wrapper.find('[data-test="compose-add-goal"]').trigger('click')
  connectOnCanvas(wrapper, 'step-1', 'Result')
  connectOnCanvas(wrapper, 'step-2', 'Result')
  await flushPromises()
  return wrapper
}

const STEP_EDGE = 'step-1-out:Result-goal-in:Result'
const OTHER_EDGE = 'step-2-out:Result-goal-in:Result'

describe('compose canvas edge colors', () => {
  it('colors each output line with its step and outlines that step’s box to match', async () => {
    const wrapper = await mountTwoStepCompose()
    await blurCanvas(wrapper)

    const strokes = paintedStrokes(wrapper)
    expect(Object.keys(strokes).length).toBe(2)
    expect(strokes[STEP_EDGE]).toBe(STEP_EDGE_COLORS[0])
    expect(strokes[OTHER_EDGE]).toBe(STEP_EDGE_COLORS[1])
    expect(strokes[STEP_EDGE]).not.toBe(strokes[OTHER_EDGE])
    expect(boxColor(wrapper, 'step-1')).toBe(strokes[STEP_EDGE])
    expect(boxColor(wrapper, 'step-2')).toBe(strokes[OTHER_EDGE])
  })

  it('turns the focused step’s inputs green, its outputs red and the rest white', async () => {
    const wrapper = await mountTwoStepCompose()

    // Adding the goal leaves it focused: both lines feed it, so both are green.
    expect(Object.values(paintedStrokes(wrapper))).toEqual([FOCUS_INPUT_COLOR, FOCUS_INPUT_COLOR])

    await focusStep(wrapper, 'step-1')
    const strokes = paintedStrokes(wrapper)
    expect(strokes[STEP_EDGE]).toBe(FOCUS_OUTPUT_COLOR)
    expect(strokes[OTHER_EDGE]).toBe(FOCUS_NEUTRAL_COLOR)
  })

  it('falls back to per-step colors when the focus is dropped on the canvas', async () => {
    const wrapper = await mountTwoStepCompose()
    await focusStep(wrapper, 'step-1')
    expect(paintedStrokes(wrapper)[STEP_EDGE]).toBe(FOCUS_OUTPUT_COLOR)

    await blurCanvas(wrapper)
    const strokes = paintedStrokes(wrapper)
    expect(strokes[STEP_EDGE]).toBe(STEP_EDGE_COLORS[0])
    expect(strokes[OTHER_EDGE]).toBe(STEP_EDGE_COLORS[1])
  })
})

// A three-step chain into the goal: each step produces what the next one consumes.
const PACK: ComposePack = {
  manifest: { name: 'pipeline', version: '1.0.0' },
  goal: {
    stepType: 'goal',
    name: 'pipeline',
    description: '',
    outputTypeName: 'Result',
    export: true,
  },
  steps: [
    {
      stepType: 'action',
      name: 'draft',
      description: '',
      inputTypeNames: ['UserInput'],
      outputTypeName: 'Draft',
      prompt: '',
    },
    {
      stepType: 'action',
      name: 'polish',
      description: '',
      inputTypeNames: ['Draft'],
      outputTypeName: 'Polished',
      prompt: '',
    },
    {
      stepType: 'action',
      name: 'finalize',
      description: '',
      inputTypeNames: ['Polished'],
      outputTypeName: 'Result',
      prompt: '',
    },
  ],
  types: [],
}

async function mountGeneratedWorkspace() {
  generate.mockResolvedValue({ pack: PACK, error: null, errorCode: null })
  const wrapper = mountPane(ProcessWorkspace)
  await flushPromises()
  await wrapper.find('[data-test="pw-generate"]').trigger('click')
  await flushPromises()
  return wrapper
}

describe('process workspace flow canvas', () => {
  it('colors each step’s output line with that step’s color and outlines its box to match', async () => {
    const wrapper = await mountGeneratedWorkspace()

    expect(paintedStrokes(wrapper)).toEqual({
      'step-0-out-Draft-step-1-in': STEP_EDGE_COLORS[0],
      'step-1-out-Polished-step-2-in': STEP_EDGE_COLORS[1],
      'step-2-out-Result-goal-in': STEP_EDGE_COLORS[2],
    })
    expect(boxColor(wrapper, 'step-0')).toBe(STEP_EDGE_COLORS[0])
    expect(boxColor(wrapper, 'step-1')).toBe(STEP_EDGE_COLORS[1])
  })

  it('highlights the focused step: green in, red out, everything else white', async () => {
    const wrapper = await mountGeneratedWorkspace()
    await focusStep(wrapper, 'step-1')

    expect(paintedStrokes(wrapper)).toEqual({
      'step-0-out-Draft-step-1-in': FOCUS_INPUT_COLOR,
      'step-1-out-Polished-step-2-in': FOCUS_OUTPUT_COLOR,
      'step-2-out-Result-goal-in': FOCUS_NEUTRAL_COLOR,
    })
  })

  it('rearranges the flow onto the same layout a generated one arrives on', async () => {
    const wrapper = await mountGeneratedWorkspace()
    await wrapper.find('[data-test="pw-rearrange"]').trigger('click')
    await flushPromises()

    // The canvas re-runs the shared layout and re-frames it. It does not stretch the flow to fill
    // whatever box it happens to be viewed in.
    const expected = layoutFlow(
      [{ id: 'step-0' }, { id: 'step-1' }, { id: 'step-2' }, { id: 'goal' }],
      [
        { id: 'e1', source: 'step-0', target: 'step-1' },
        { id: 'e2', source: 'step-1', target: 'step-2' },
        { id: 'e3', source: 'step-2', target: 'goal' },
      ],
    )
    for (const [id, at] of Object.entries(expected)) {
      const painted = paintedPosition(wrapper, id)
      expect(painted, id).not.toBeNull()
      expect(painted!.x).toBeCloseTo(at.x)
      expect(painted!.y).toBeCloseTo(at.y)
    }
    const draft = paintedPosition(wrapper, 'step-0')!
    const polish = paintedPosition(wrapper, 'step-1')!
    expect(draft.x).toBeLessThan(polish.x)
    expect(polish.x).toBeLessThan(paintedPosition(wrapper, 'step-2')!.x)
  })

  it('anchors the handles on the sides the flow actually runs along', async () => {
    const wrapper = await mountGeneratedWorkspace()
    const box = wrapper.find('.vue-flow__node[data-id="step-0"]')
    expect(box.find('.vue-flow__handle-left').exists()).toBe(true)
    expect(box.find('.vue-flow__handle-right').exists()).toBe(true)
    // Top and bottom anchors on a left-to-right flow make every edge swoop down and around.
    expect(box.find('.vue-flow__handle-top').exists()).toBe(false)
    expect(box.find('.vue-flow__handle-bottom').exists()).toBe(false)
  })

  it('spreads a step’s input handles down its left edge so each one can be grabbed', async () => {
    const wrapper = mountPane(ComposePane)
    await flushPromises()
    await wrapper.find('[data-test="compose-add-step"]').trigger('click')
    await wrapper.find('[data-test="compose-step-input"]').setValue('MarketData')
    await wrapper.find('[data-test="compose-step-input-add"]').trigger('click')
    await flushPromises()

    // Vue Flow parks every handle of one side on the same midpoint, so stacked handles are
    // indistinguishable to a pointer: only the topmost one could ever be dragged from.
    const handles = wrapper.findAll('.vue-flow__node[data-id="step-1"] .vue-flow__handle-left')
    expect(handles).toHaveLength(2)
    expect(handles.map((h) => (h.element as HTMLElement).style.top)).toEqual(['33.33%', '66.67%'])
  })

  it('offers the rearrange beside the fit control', async () => {
    const wrapper = await mountGeneratedWorkspace()
    const fit = wrapper.find('[data-test="pw-fit"]')
    const rearrange = wrapper.find('[data-test="pw-rearrange"]')
    expect(fit.text()).toContain('Fit view')
    expect(rearrange.text()).toContain('Rearrange view')
    expect(fit.element.compareDocumentPosition(rearrange.element)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    )
  })
})
