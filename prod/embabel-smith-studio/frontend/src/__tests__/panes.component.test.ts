import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Mock } from 'vitest'
import { QLayout, QPageContainer, Quasar } from 'quasar'
import { defineComponent, h } from 'vue'
import type { Component } from 'vue'

import AutoImprovePane from '../panes/AutoImprovePane.vue'
import ComposePane from '../panes/ComposePane.vue'
import RetrievePane from '../panes/RetrievePane.vue'
import RemotePane from '../panes/RemotePane.vue'
import RunPane from '../panes/RunPane.vue'
import SecurityPane from '../panes/SecurityPane.vue'
import StoragePane from '../panes/StoragePane.vue'
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
    listModels: vi.fn(),
    run: vi.fn(),
    scan: vi.fn(),
    retrieve: vi.fn(),
    listBridges: vi.fn(),
    executeRemote: vi.fn(),
    compose: vi.fn(),
    loopStatus: vi.fn(),
    listCandidates: vi.fn(),
    getAutoPublish: vi.fn(),
    triggerLoop: vi.fn(),
    getStorageSettings: vi.fn(),
    saveStorageSettings: vi.fn(),
  }
})

const listSources = api.listSources as Mock
const listPacks = api.listPacks as Mock
const listGoals = api.listGoals as Mock
const listModels = api.listModels as Mock
const run = api.run as Mock
const scan = api.scan as Mock
const retrieve = api.retrieve as Mock
const listBridges = api.listBridges as Mock
const executeRemote = api.executeRemote as Mock
const compose = api.compose as Mock
const loopStatus = api.loopStatus as Mock
const listCandidates = api.listCandidates as Mock
const getAutoPublish = api.getAutoPublish as Mock
const triggerLoop = api.triggerLoop as Mock
const getStorageSettings = api.getStorageSettings as Mock
const saveStorageSettings = api.saveStorageSettings as Mock

// The catalog is cached module-wide on first use, so it is seeded once for every pane's selector
// rather than per test (which test mounts first is not meaningful here).
listModels.mockResolvedValue({ models: ['gpt-4.1', 'claude-sonnet-4-6'], defaultModel: 'gpt-4.1' })

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
 * Pick an option in a q-select (the panes use selects for source/spec). The select is located by
 * its unique label prop; its v-model is set by emitting update:modelValue, then flushed so the
 * pane's ref updates.
 */
async function selectOption(wrapper: ReturnType<typeof mountPane>, label: string, value: string) {
  const select = wrapper.findAllComponents({ name: 'QSelect' }).find((s) => s.props('label') === label)
  if (!select) throw new Error(`no q-select with label="${label}"`)
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}

/** Seed the shared source/spec picker state used by the studio panes. */
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

  it('sends the model chosen in the pane selector', async () => {
    seedSources()
    listGoals.mockResolvedValue(['explainerDone'])
    run.mockResolvedValue({
      success: true,
      output: 'ok',
      error: null,
      errorCode: null,
      runId: 'r1',
    })
    const wrapper = mountPane(RunPane)
    await flushPromises()

    await selectOption(wrapper, 'Model', 'claude-sonnet-4-6')
    await selectOption(wrapper, 'Goal', 'explainerDone')
    await wrapper.find('[data-test="run-btn"]').trigger('click')
    await flushPromises()

    expect(run).toHaveBeenCalledWith(expect.objectContaining({ model: 'claude-sonnet-4-6' }))
    expect(wrapper.find('[data-test="run-output"]').exists()).toBe(true)
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
      publishedSpecs: [],
      queuedSpecs: [],
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
        specName: 'alpha',
        verdict: 'SAFE',
        recommendedTier: null,
        summary: 'clean',
        findings: [],
      },
    })
    const wrapper = mountPane(SecurityPane)
    await flushPromises()

    await selectOption(wrapper, 'Spec', 'alpha')
    await wrapper.find('[data-test="security-btn"]').trigger('click')
    await flushPromises()

    expect(scan).toHaveBeenCalledWith({ source: 'folder:/s', spec: 'alpha' })
    expect(wrapper.find('[data-test="security-result"]').text()).toContain('SAFE')
  })
})

describe('RetrievePane', () => {
  it('ranks the curated hits, naming the scorer and each pack lifecycle state', async () => {
    seedSources()
    retrieve.mockResolvedValue({
      error: null,
      scorer: 'lexical',
      results: [
        {
          name: 'alpha',
          description: 'reconcile invoices',
          score: 0.6,
          outputType: 'Report',
          lifecycleState: 'ACTIVE',
        },
        {
          name: 'bravo',
          description: 'reconcile invoices',
          score: 0.3,
          outputType: 'Report',
          lifecycleState: 'DEGRADED',
        },
      ],
    })
    const wrapper = mountPane(RetrievePane)
    await flushPromises()

    await wrapper.find('[data-test="retrieve-query"]').setValue('reconcile invoices')
    await wrapper.find('[data-test="retrieve-btn"]').trigger('click')
    await flushPromises()

    expect(retrieve).toHaveBeenCalledWith({ source: 'folder:/s', query: 'reconcile invoices' })
    const table = wrapper.find('[data-test="retrieve-table"]')
    expect(table.text()).toContain('alpha')
    expect(table.text()).toContain('bravo')
    // The de-ranked pack is shown with the state that halved it, so the user can see why
    // it sank rather than guessing.
    expect(table.text()).toContain('DEGRADED')
    expect(wrapper.find('[data-test="retrieve-scorer"]').text()).toContain('lexical')
  })

  it('does not surface a retired pack the endpoint dropped', async () => {
    // The source itself lists a retired pack, so if the pane ever rendered the pack list
    // rather than the retrieval hits, the retired one would appear.
    listSources.mockResolvedValue([{ uri: 'folder:/s', kind: 'folder' }])
    listPacks.mockResolvedValue([
      {
        name: 'keeper',
        contentHash: 'h',
        lifecycleState: 'ACTIVE',
        baselinePassRate: 0.9,
        drift: false,
        updatedAt: '2026-01-01T00:00:00Z',
      },
      {
        name: 'retired-pack',
        contentHash: 'h2',
        lifecycleState: 'RETIRED',
        baselinePassRate: 0.1,
        drift: true,
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ])
    // The endpoint applies the curation rules; the retired pack is not in what it returns.
    retrieve.mockResolvedValue({
      error: null,
      scorer: 'lexical',
      results: [
        {
          name: 'keeper',
          description: 'summarise a thread',
          score: 0.5,
          outputType: 'Summary',
          lifecycleState: 'ACTIVE',
        },
      ],
    })
    const wrapper = mountPane(RetrievePane)
    await flushPromises()

    await wrapper.find('[data-test="retrieve-query"]').setValue('summarise')
    await wrapper.find('[data-test="retrieve-btn"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('retired-pack')
    expect(wrapper.find('[data-test="retrieve-table"]').text()).toContain('keeper')
    expect(wrapper.find('[data-test="retrieve-scorer"]').text()).toContain('1 match')
  })

  it('shows the no-match state when the source has nothing to rank', async () => {
    seedSources()
    retrieve.mockResolvedValue({ error: null, scorer: 'lexical', results: [] })
    const wrapper = mountPane(RetrievePane)
    await flushPromises()

    await wrapper.find('[data-test="retrieve-query"]').setValue('teleport the moon')
    await wrapper.find('[data-test="retrieve-btn"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="retrieve-none"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="retrieve-table"]').exists()).toBe(false)
    // A search that came back empty must not leave a spinner running.
    expect(wrapper.find('.q-spinner').exists()).toBe(false)
    expect(wrapper.find('[data-test="retrieve-scorer"]').text()).toContain('0 match')
  })

  it('shows a source-resolution failure as a banner rather than a table', async () => {
    seedSources()
    retrieve.mockResolvedValue({
      error: 'Retrieve failed: unsupported source scheme: nonsense',
      scorer: null,
      results: [],
    })
    const wrapper = mountPane(RetrievePane)
    await flushPromises()

    await wrapper.find('[data-test="retrieve-query"]').setValue('anything')
    await wrapper.find('[data-test="retrieve-btn"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="retrieve-error"]').text()).toContain('unsupported source scheme')
    expect(wrapper.find('[data-test="retrieve-results"]').exists()).toBe(false)
  })
})

describe('RemotePane (sidecar bridges)', () => {
  const digestBridge = {
    name: 'digest',
    baseUrl: 'http://127.0.0.1:18090',
    urlProbed: 'http://127.0.0.1:18090/api/v1/actions',
    reachable: true,
    reason: null,
    actions: [
      {
        name: 'remoteDigest',
        description: 'Digest the input text on the sidecar',
        inputTypeNames: ['com.embabel.agent.domain.io.UserInput'],
        runnable: true,
      },
      {
        name: 'compareBriefs',
        description: 'Needs an input this pane has no field for',
        inputTypeNames: ['com.embabel.agent.domain.io.UserInput', 'TopicBrief'],
        runnable: false,
      },
    ],
  }

  it('shows a bridge that is not running as a state, naming the URL it probed, with nothing to start it', async () => {
    listBridges.mockResolvedValue({
      bridges: [
        {
          name: 'gone',
          baseUrl: 'http://127.0.0.1:19999',
          urlProbed: 'http://127.0.0.1:19999/api/v1/actions',
          reachable: false,
          reason: 'ConnectException',
          actions: [],
        },
      ],
      malformedBridges: [],
      mcpEndpoint: null,
    })
    const wrapper = mountPane(RemotePane)
    await flushPromises()

    expect(wrapper.find('[data-test="bridge-unreachable"]').exists()).toBe(true)
    const reason = wrapper.find('[data-test="bridge-reason"]')
    expect(reason.text()).toContain('ConnectException')
    expect(reason.text()).toContain('http://127.0.0.1:19999/api/v1/actions')
    // The pane reports a process it did not start; it must not offer to start one.
    const labels = wrapper.findAllComponents({ name: 'QBtn' }).map((btn) => String(btn.props('label')).toLowerCase())
    expect(labels.some((label) => label.includes('start'))).toBe(false)
  })

  it('labels the configured MCP endpoint as not invoked and shows no reachability for it', async () => {
    listBridges.mockResolvedValue({
      bridges: [digestBridge],
      malformedBridges: [],
      mcpEndpoint: 'http://127.0.0.1:8081/mcp',
    })
    const wrapper = mountPane(RemotePane)
    await flushPromises()

    const line = wrapper.find('[data-test="mcp-line"]')
    expect(line.text()).toContain('http://127.0.0.1:8081/mcp')
    expect(line.text()).toContain('not invoked')
    // The bridge row above does carry a reachable badge; the MCP line must not borrow its signal.
    expect(line.text()).not.toMatch(/reachab/i)
    expect(wrapper.find('[data-test="bridge-reachable"]').exists()).toBe(true)
  })

  it('lists an action it cannot run with its declared inputs, and does not offer it in the picker', async () => {
    listBridges.mockResolvedValue({ bridges: [digestBridge], malformedBridges: [], mcpEndpoint: null })
    const wrapper = mountPane(RemotePane)
    await flushPromises()

    const rows = wrapper.findAll('[data-test="action-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[1].text()).toContain('compareBriefs')
    expect(rows[1].text()).toContain('TopicBrief')
    expect(rows[1].find('[data-test="action-unrunnable"]').exists()).toBe(true)
    expect(rows[0].find('[data-test="action-unrunnable"]').exists()).toBe(false)

    await selectOption(wrapper, 'Bridge', 'digest')
    const action = wrapper.findAllComponents({ name: 'QSelect' }).find((s) => s.props('label') === 'Action')
    expect(action?.props('options')).toEqual(['remoteDigest'])
  })

  it('runs the advertised action and shows the output the sidecar produced', async () => {
    listBridges.mockResolvedValue({ bridges: [digestBridge], malformedBridges: [], mcpEndpoint: null })
    executeRemote.mockResolvedValue({
      error: null,
      bridge: 'digest',
      action: 'remoteDigest',
      urlProbed: 'http://127.0.0.1:18090/api/v1/actions/execute',
      output: 'Digested on the sidecar: 2 words, 9 chars',
      reason: null,
      status: null,
      sidecarBody: null,
    })
    const wrapper = mountPane(RemotePane)
    await flushPromises()

    await selectOption(wrapper, 'Bridge', 'digest')
    await selectOption(wrapper, 'Action', 'remoteDigest')
    await wrapper.find('[data-test="remote-input"]').setValue('two words')
    await wrapper.find('[data-test="remote-run"]').trigger('click')
    await flushPromises()

    expect(executeRemote).toHaveBeenCalledWith({ bridge: 'digest', action: 'remoteDigest', input: 'two words' })
    expect(wrapper.find('[data-test="remote-output"]').text()).toContain(
      'Digested on the sidecar: 2 words, 9 chars',
    )
  })

  it('says the bridge is not running when the execute call reports it unreachable', async () => {
    listBridges.mockResolvedValue({ bridges: [digestBridge], malformedBridges: [], mcpEndpoint: null })
    executeRemote.mockResolvedValue({
      error: null,
      bridge: 'digest',
      action: 'remoteDigest',
      urlProbed: 'http://127.0.0.1:18090/api/v1/actions/execute',
      output: null,
      reason: 'ConnectException',
      status: null,
      sidecarBody: null,
    })
    const wrapper = mountPane(RemotePane)
    await flushPromises()

    await selectOption(wrapper, 'Bridge', 'digest')
    await selectOption(wrapper, 'Action', 'remoteDigest')
    await wrapper.find('[data-test="remote-run"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="remote-unreachable"]').text()).toContain('ConnectException')
    expect(wrapper.find('[data-test="remote-output"]').exists()).toBe(false)
  })

  it('shows the sidecar own refusal, status and body, instead of an empty output', async () => {
    listBridges.mockResolvedValue({ bridges: [digestBridge], malformedBridges: [], mcpEndpoint: null })
    executeRemote.mockResolvedValue({
      error: null,
      bridge: 'digest',
      action: 'nope',
      urlProbed: 'http://127.0.0.1:18090/api/v1/actions/execute',
      output: null,
      reason: null,
      status: 400,
      sidecarBody: '{"error":"unknown action: nope"}',
    })
    const wrapper = mountPane(RemotePane)
    await flushPromises()

    await selectOption(wrapper, 'Bridge', 'digest')
    await selectOption(wrapper, 'Action', 'remoteDigest')
    await wrapper.find('[data-test="remote-run"]').trigger('click')
    await flushPromises()

    // A refusal is the sidecar's answer, not ours: the status and its body are both shown.
    const declined = wrapper.find('[data-test="remote-declined"]')
    expect(declined.text()).toContain('400')
    expect(declined.text()).toContain('unknown action: nope')
    expect(wrapper.find('[data-test="remote-output"]').exists()).toBe(false)
    // And it is not silently swallowed into the "nothing to show" state.
    expect(wrapper.find('[data-test="remote-nothing"]').exists()).toBe(false)
  })

  it('shows a bridge the configuration could not express rather than dropping it', async () => {
    listBridges.mockResolvedValue({
      bridges: [],
      malformedBridges: ['http://localhost:18090'],
      mcpEndpoint: null,
    })
    const wrapper = mountPane(RemotePane)
    await flushPromises()

    expect(wrapper.find('[data-test="malformed-entry"]').text()).toContain('http://localhost:18090')
    expect(wrapper.find('[data-test="bridges-empty"]').exists()).toBe(false)
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

describe('StoragePane', () => {
  it('prefills the form from the stored settings and saves a new endpoint', async () => {
    getStorageSettings.mockResolvedValue({
      packDb: { url: 'jdbc:postgresql://localhost:5432/smith_studio', user: 'smith', password: 'smith' },
      trace: { type: 'jsonl', url: null, user: null, password: null },
    })
    saveStorageSettings.mockResolvedValue({ ok: true, settings: null, error: null })
    const wrapper = mountPane(StoragePane)
    await flushPromises()

    // The stored endpoint is loaded into the form.
    const urlInput = wrapper.find('[data-test="storage-url"]') as unknown as {
      element: HTMLInputElement
    }
    expect(urlInput.element.value).toBe('jdbc:postgresql://localhost:5432/smith_studio')

    await wrapper.find('[data-test="storage-save"]').trigger('click')
    await flushPromises()

    expect(saveStorageSettings).toHaveBeenCalledWith(
      expect.objectContaining({ url: 'jdbc:postgresql://localhost:5432/smith_studio', traceType: 'jsonl' }),
    )
    expect(wrapper.find('[data-test="storage-error"]').exists()).toBe(false)
  })

  it('shows the connection-failure message when the endpoint is unreachable', async () => {
    getStorageSettings.mockResolvedValue({
      packDb: { url: null, user: null, password: null },
      trace: { type: 'jsonl', url: null, user: null, password: null },
    })
    saveStorageSettings.mockResolvedValue({
      ok: false,
      settings: null,
      error: 'Cannot connect to the storage database: connection refused',
    })
    const wrapper = mountPane(StoragePane)
    await flushPromises()

    await wrapper.find('[data-test="storage-url"]').setValue('jdbc:postgresql://nowhere:5432/none')
    await wrapper.find('[data-test="storage-save"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="storage-error"]').text()).toContain('connection refused')
  })
})
