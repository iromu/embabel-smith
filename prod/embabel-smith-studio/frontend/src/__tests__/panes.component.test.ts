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
    run: vi.fn(),
    scan: vi.fn(),
    loopStatus: vi.fn(),
    listCandidates: vi.fn(),
    getAutoPublish: vi.fn(),
    triggerLoop: vi.fn(),
  }
})

const listSources = api.listSources as Mock
const listPacks = api.listPacks as Mock
const run = api.run as Mock
const scan = api.scan as Mock
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
    run.mockResolvedValue({
      success: false,
      output: null,
      error: 'No model endpoint configured',
      errorCode: 'MODEL_NOT_CONFIGURED',
      runId: null,
    })
    const wrapper = mountPane(RunPane)
    await flushPromises()

    await wrapper.find('[data-test="run-goal"]').setValue('alpha')
    await flushPromises()
    await wrapper.find('[data-test="run-input"]').setValue('hi')
    await wrapper.find('[data-test="run-btn"]').trigger('click')
    await flushPromises()

    expect(run).toHaveBeenCalledWith(
      expect.objectContaining({ source: 'folder:/s', goal: 'alpha', input: 'hi' }),
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

    await wrapper.find('[data-test="security-skill"]').setValue('alpha')
    await flushPromises()
    await wrapper.find('[data-test="security-btn"]').trigger('click')
    await flushPromises()

    expect(scan).toHaveBeenCalledWith({ source: 'folder:/s', skill: 'alpha' })
    expect(wrapper.find('[data-test="security-result"]').text()).toContain('SAFE')
  })
})

describe('POC-only panes are disabled in the product', () => {
  it('Compose renders the POC-only notice instead of a working canvas', async () => {
    const wrapper = mountPane(ComposePane)
    await flushPromises()
    expect(wrapper.find('[data-test="poc-only-notice"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="poc-only-notice"]').text()).toContain('not backed by the studio')
  })
})
