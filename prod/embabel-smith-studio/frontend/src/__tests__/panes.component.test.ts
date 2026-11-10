import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Mock } from 'vitest'
import { QLayout, QPageContainer, Quasar } from 'quasar'
import { defineComponent, h } from 'vue'
import type { Component } from 'vue'

import AutoImprovePane from '../panes/AutoImprovePane.vue'
import ComposePane from '../panes/ComposePane.vue'
import RunPane from '../panes/RunPane.vue'
import SecurityPane from '../panes/SecurityPane.vue'
import * as api from '../api/client'

// jsdom has no ResizeObserver; Quasar's QResizeObserver needs it to render.
vi.stubGlobal(
  'ResizeObserver',
  class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  },
)

// Stub the fetch-based methods the panes call; keep the pure helpers real.
vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return {
    ...actual,
    listSources: vi.fn(),
    listPacks: vi.fn(),
    listGoals: vi.fn(),
    run: vi.fn(),
    scan: vi.fn(),
    compose: vi.fn(),
    loopStatus: vi.fn(),
    listCandidates: vi.fn(),
    getAutoPublish: vi.fn(),
    triggerLoop: vi.fn(),
  }
})

const listSources = api.listSources as Mock
const listPacks = api.listPacks as Mock
const listGoals = api.listGoals as Mock
const run = api.run as Mock
const scan = api.scan as Mock
const compose = api.compose as Mock
const loopStatus = api.loopStatus as Mock
const listCandidates = api.listCandidates as Mock
const getAutoPublish = api.getAutoPublish as Mock
const triggerLoop = api.triggerLoop as Mock

afterEach(() => {
  vi.clearAllMocks()
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

/**
 * Pick an option in a q-select (the panes use selects for source/skill). The select is located by
 * its unique label prop; its v-model is set by emitting update:modelValue, then flushed so the
 * pane's ref updates.
 */
async function selectOption(wrapper: ReturnType<typeof mountPane>, label: string, value: string) {
  const select = wrapper.findAllComponents({ name: 'QSelect' }).find((s) => s.props('label') === label)
  if (!select) throw new Error(`no q-select with label="${label}"`)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

/** Seed the shared source/skill picker state used by the studio panes. */
function seedSources() {
  listSources.mockResolvedValue([{ uri: 'folder:/s', kind: 'folder' }])
  listPacks.mockResolvedValue([
    {
      name: 'alpha',
      contentHash: 'h',
      lifecycleState: 'ACTIVE',
      baselinePassRate: 0.9,
      drift: false,
      updatedAt: '2026-01-01T00:00:00Z',
    },
  ])
}

describe('RunPane (endpoint-gated)', () => {
  it('shows the model-not-configured banner when the endpoint is unset', async () => {
    seedSources()
    listGoals.mockResolvedValue(['explainerDone'])
    run.mockResolvedValue({
      success: false,
      output: null,
      error: 'No model endpoint configured',
      errorCode: 'MODEL_NOT_CONFIGURED',
      runId: null,
    })
    const wrapper = mountPane(RunPane)
    await flushPromises()

    await selectOption(wrapper, 'Goal', 'explainerDone')
    await wrapper.find('[data-test="run-input"]').setValue('hi')
    await wrapper.find('[data-test="run-btn"]').trigger('click')
    await flushPromises()

    expect(listGoals).toHaveBeenCalledWith('folder:/s')
    expect(run).toHaveBeenCalledWith(
      expect.objectContaining({ source: 'folder:/s', goal: 'explainerDone', input: 'hi' }),
    )
    expect(wrapper.find('[data-test="run-no-endpoint"]').exists()).toBe(true)
  })
})

describe('AutoImprovePane', () => {
  it('shows the model-not-configured banner when an iteration has no endpoint', async () => {
    const loopStatusResponse = { running: false, autoPublish: false }
    loopStatus.mockResolvedValue(loopStatusResponse)
    listCandidates.mockResolvedValue([])
    getAutoPublish.mockResolvedValue(loopStatusResponse)
    triggerLoop.mockResolvedValue({
      ran: false,
      candidates: 0,
      publishedSkills: [],
      queuedSkills: [],
      autoPublish: false,
      errorCode: 'MODEL_NOT_CONFIGURED',
      error: 'No model endpoint configured',
    })
    const wrapper = mountPane(AutoImprovePane)
    await flushPromises()

    await wrapper.find('[data-test="loop-trigger"]').trigger('click')
    await flushPromises()

    expect(triggerLoop).toHaveBeenCalled()
    expect(wrapper.find('[data-test="loop-no-endpoint"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="loop-no-endpoint"]').text()).toContain(
      'No model endpoint configured',
    )
  })
})

describe('SecurityPane', () => {
  it('scans a pack and renders the verdict', async () => {
    seedSources()
    scan.mockResolvedValue({
      error: null,
      scan: {
        skillName: 'alpha',
        verdict: 'SAFE',
        recommendedTier: null,
        summary: 'clean',
        findings: [],
      },
    })
    const wrapper = mountPane(SecurityPane)
    await flushPromises()

    await selectOption(wrapper, 'Skill', 'alpha')
    await wrapper.find('[data-test="security-btn"]').trigger('click')
    await flushPromises()

    expect(scan).toHaveBeenCalledWith({ source: 'folder:/s', skill: 'alpha' })
    expect(wrapper.find('[data-test="security-result"]').text()).toContain('SAFE')
  })
})

describe('ComposePane (GOAP flow editor)', () => {
  it('adds steps and a goal, validates a reachable flow, and exports it', async () => {
    listSources.mockResolvedValue([{ uri: 'folder:/s', kind: 'folder' }])
    compose.mockResolvedValue({
      ok: true,
      packName: 'goal',
      goal: 'goal',
      published: { location: '/s/goal', pushed: false },
      lifecycle: {
        contentHash: 'h',
        baselinePassRate: 0,
        state: 'INCUBATING',
        updatedAt: 'now',
      },
      error: null,
    })
    const wrapper = mountPane(ComposePane)
    await flushPromises()

    // Add a step (auto-selected) and point its output at the goal's default type.
    await wrapper.find('[data-test="compose-add-step"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="compose-step-name"]').exists()).toBe(true)
    await wrapper.find('[data-test="compose-step-output"]').setValue('Result')
    await flushPromises()

    // Add the goal, then validate — the step now produces the goal's type.
    await wrapper.find('[data-test="compose-add-goal"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-test="compose-validate"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="compose-valid"]').exists()).toBe(true)

    // Export to the source.
    await wrapper.find('[data-test="compose-export"]').trigger('click')
    await flushPromises()
    expect(compose).toHaveBeenCalledWith(
      expect.objectContaining({
        source: 'folder:/s',
        pack: expect.objectContaining({
          goal: expect.objectContaining({ outputTypeName: 'Result', export: true }),
          steps: [
            expect.objectContaining({
              outputTypeName: 'Result',
              inputTypeNames: ['UserInput'],
            }),
          ],
        }),
      }),
    )
    expect(wrapper.find('[data-test="compose-exported"]').exists()).toBe(true)
  })
})
