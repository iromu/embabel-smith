import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Quasar } from 'quasar'
import type { Component } from 'vue'

import ComposePane from '../panes/ComposePane.vue'
import RetrievePane from '../panes/RetrievePane.vue'
import RunPane from '../panes/RunPane.vue'
import * as api from '../api/client'

// Stub the fetch-based methods; keep the pure helpers (e.g. isEndpointNotConfigured) real.
vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return {
    ...actual,
    listSkills: vi.fn(),
    compose: vi.fn(),
    retrieve: vi.fn(),
    run: vi.fn(),
  }
})

afterEach(() => {
  vi.clearAllMocks()
})

function mountPane(component: Component) {
  return mount(component, { global: { plugins: [Quasar] } })
}

describe('RetrievePane', () => {
  it('posts the query and renders the ranked results', async () => {
    api.retrieve.mockResolvedValue({
      scorer: 'lexical',
      results: [
        {
          name: 'compose-extract',
          description: 'd',
          score: 0.9,
          outputType: 'BackgroundMessage',
          state: 'ACTIVE',
        },
      ],
    })
    const wrapper = mountPane(RetrievePane)
    // eslint-disable-next-line no-console
    console.log('HTML>', wrapper.html())
    await wrapper.find('[data-test="retrieve-query"] input').setValue('extract the topic')
    await wrapper.find('[data-test="retrieve-btn"]').trigger('click')
    await flushPromises()

    expect(api.retrieve).toHaveBeenCalledWith({ query: 'extract the topic', k: 5 })
    expect(wrapper.find('[data-test="retrieve-result"]').text()).toContain('compose-extract')
    expect(wrapper.find('[data-test="retrieve-result"]').text()).toContain('lexical')
  })
})

describe('RunPane (endpoint-gated)', () => {
  it('shows the not-configured state when no endpoint is set', async () => {
    api.listSkills.mockResolvedValue([
      { name: 'compose-extract', externalInputTypes: ['UserInput'], outputType: 'BackgroundMessage' },
    ])
    api.run.mockResolvedValue({
      error: 'No resolvable model endpoint is configured. Missing: OPENAI_CUSTOM_BASE_URL.',
      output: null,
    })
    const wrapper = mountPane(RunPane)
    await flushPromises()
    await wrapper.find('[data-test="run-skill"] input').setValue('compose-extract')
    await wrapper.find('[data-test="run-btn"]').trigger('click')
    await flushPromises()

    expect(api.run).toHaveBeenCalledWith({ skillName: 'compose-extract', input: '' })
    expect(wrapper.find('[data-test="run-no-endpoint"]').exists()).toBe(true)
  })
})

describe('ComposePane', () => {
  it('adds skills, snaps a type-compatible edge, and exports the composite', async () => {
    api.listSkills.mockResolvedValue([
      { name: 'compose-extract', externalInputTypes: ['UserInput'], outputType: 'TopicBrief' },
      { name: 'compose-explain', externalInputTypes: ['TopicBrief'], outputType: 'BackgroundMessage' },
    ])
    api.compose.mockResolvedValue({
      path: '/tmp/compose-composite',
      goal: { name: 'compose-composite', outputType: 'BackgroundMessage', export: true },
      steps: ['extract', 'explain'],
      types: ['TopicBrief'],
      issues: [],
    })
    const wrapper = mountPane(ComposePane)
    await flushPromises()

    await wrapper.find('[data-test="compose-skill-input"] input').setValue('compose-extract')
    await wrapper.find('[data-test="compose-add-btn"]').trigger('click')
    await wrapper.find('[data-test="compose-skill-input"] input').setValue('compose-explain')
    await wrapper.find('[data-test="compose-add-btn"]').trigger('click')

    await wrapper.find('[data-test="compose-from"] input').setValue('compose-extract')
    await wrapper.find('[data-test="compose-to"] input').setValue('compose-explain')
    await wrapper.find('[data-test="compose-connect-btn"]').trigger('click')
    expect(wrapper.find('[data-test="compose-snap"]').text()).toContain('Snapped')

    await wrapper.find('[data-test="compose-export-btn"]').trigger('click')
    await flushPromises()

    expect(api.compose).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-test="compose-result"]').text()).toContain('compose-composite')
  })
})
