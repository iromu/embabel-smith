import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Mock } from 'vitest'
import { Quasar } from 'quasar'

import ModelRunTimer from '../components/ModelRunTimer.vue'
import ModelSelect from '../components/ModelSelect.vue'
import * as api from '../api/client'
import { modelTimer } from '../api/client'

// jsdom has no ResizeObserver; Quasar's QResizeObserver needs it to render.
vi.stubGlobal(
  'ResizeObserver',
  class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  },
)

// Only the catalog endpoint is mocked here — the timer state under test is the client's own.
vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, listModels: vi.fn() }
})

const listModels = api.listModels as Mock

const mountOptions = { global: { plugins: [Quasar] } }

function resetTimer() {
  modelTimer.running.value = false
  modelTimer.label.value = ''
  modelTimer.elapsedMs.value = 0
  modelTimer.lastElapsedMs.value = null
  modelTimer.lastFailed.value = false
}

afterEach(() => {
  resetTimer()
  vi.clearAllMocks()
})

describe('ModelSelect', () => {
  // Declared first: the catalog is cached module-wide, so this is the only place a first fetch
  // can be observed.
  it('fetches the catalog once and shares it across mounts', async () => {
    listModels.mockResolvedValue({ models: ['gpt-4.1', 'claude-sonnet-4-6'], defaultModel: 'gpt-4.1' })
    const first = mount(ModelSelect, mountOptions)
    await flushPromises()
    const second = mount(ModelSelect, mountOptions)
    await flushPromises()

    expect(listModels).toHaveBeenCalledTimes(1)
    expect(second.findComponent({ name: 'QSelect' }).props('options')).toEqual([
      'gpt-4.1',
      'claude-sonnet-4-6',
    ])
    first.unmount()
    second.unmount()
  })

  it('emits undefined rather than an empty string when cleared, so no model is sent', async () => {
    const wrapper = mount(ModelSelect, { ...mountOptions, props: { modelValue: 'gpt-4.1' } })
    wrapper.findComponent({ name: 'QSelect' }).vm.$emit('update:modelValue', null)
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toBeUndefined()

    wrapper.findComponent({ name: 'QSelect' }).vm.$emit('update:modelValue', 'claude-sonnet-4-6')
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toBe('claude-sonnet-4-6')
    wrapper.unmount()
  })

  it('keeps the caller-provided test hook on the control', () => {
    const wrapper = mount(ModelSelect, { ...mountOptions, props: { dataTest: 'run-model' } })
    expect(wrapper.find('[data-test="run-model"]').exists()).toBe(true)
    wrapper.unmount()
  })
})

describe('ModelRunTimer', () => {
  it('renders nothing before any model call has run', () => {
    const wrapper = mount(ModelRunTimer, mountOptions)
    expect(wrapper.find('[data-test="model-timer"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows the label and a ticking elapsed time while a call is in flight', () => {
    modelTimer.running.value = true
    modelTimer.label.value = 'Generate'
    modelTimer.elapsedMs.value = 12_400
    const wrapper = mount(ModelRunTimer, mountOptions)
    const timer = wrapper.find('[data-test="model-timer"]')
    expect(timer.exists()).toBe(true)
    expect(timer.text()).toContain('Generate')
    expect(wrapper.find('[data-test="model-timer-elapsed"]').text()).toBe('12.4s')
    wrapper.unmount()
  })

  it('keeps the final elapsed with a failed affordance once a failed call settles', () => {
    modelTimer.label.value = 'Evaluate'
    modelTimer.lastElapsedMs.value = 4_200
    modelTimer.lastFailed.value = true
    const wrapper = mount(ModelRunTimer, mountOptions)
    expect(wrapper.find('[data-test="model-timer"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="model-timer-elapsed"]').text()).toBe('4.2s')
    expect(wrapper.html()).toContain('text-negative')
    wrapper.unmount()
  })

  it('formats elapsed time past a minute as minutes and seconds', () => {
    modelTimer.label.value = 'Run'
    modelTimer.lastElapsedMs.value = 62_000
    const wrapper = mount(ModelRunTimer, mountOptions)
    expect(wrapper.find('[data-test="model-timer-elapsed"]').text()).toBe('1m 02s')
    wrapper.unmount()
  })
})
