import { afterEach, describe, expect, it, vi } from 'vitest'

import * as api from '../api/client'
import type { ComposePack } from '../compose/model'

interface Call {
  url: string
  init?: RequestInit
}

/** Stub the global `fetch` to return `body`, recording each call for inspection. */
function stubFetch(body: unknown, ok = true, status = 200) {
  const calls: Call[] = []
  const mock = vi.fn(async (url: string, init?: RequestInit) => {
    calls.push({ url, init })
    return { ok, status, json: async () => body } as unknown as Response
  })
  vi.stubGlobal('fetch', mock)
  return { calls, mock }
}

function bodyOf(call: Call): Record<string, unknown> {
  return JSON.parse(String(call.init?.body)) as Record<string, unknown>
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('api/client — the single fetch site', () => {
  it('listSources GETs /api/sources and parses the source list', async () => {
    const { calls } = stubFetch([{ uri: 'folder:/specs', kind: 'folder' }])
    const sources = await api.listSources()
    expect(calls).toHaveLength(1)
    expect(calls[0].url).toBe('/api/sources')
    expect(calls[0].init?.method).toBeUndefined()
    expect(sources[0].uri).toBe('folder:/specs')
    expect(sources[0].kind).toBe('folder')
  })

  it('listPacks GETs /api/packs with the source encoded in the query', async () => {
    const { calls } = stubFetch([
      {
        name: 'alpha',
        contentHash: 'h',
        lifecycleState: 'ACTIVE',
        baselinePassRate: 0.9,
        drift: false,
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ])
    const packs = await api.listPacks('git:https://x/repo.git')
    expect(calls[0].url).toBe(`/api/packs?source=${encodeURIComponent('git:https://x/repo.git')}`)
    expect(packs[0].name).toBe('alpha')
    expect(packs[0].lifecycleState).toBe('ACTIVE')
  })

  it('run POSTs the request and parses the endpoint-gated response', async () => {
    const { calls } = stubFetch({
      success: false,
      output: null,
      error: 'No model endpoint configured',
      errorCode: 'MODEL_NOT_CONFIGURED',
      runId: null,
    })
    const res = await api.run({ source: 'folder:/s', goal: 'g', input: 'hi', trustTier: 'STANDARD' })
    expect(calls[0].url).toBe('/api/run')
    expect(calls[0].init?.method).toBe('POST')
    expect(calls[0].init?.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(bodyOf(calls[0])).toEqual({
      source: 'folder:/s',
      goal: 'g',
      input: 'hi',
      trustTier: 'STANDARD',
    })
    expect(res.errorCode).toBe('MODEL_NOT_CONFIGURED')
  })

  it('build POSTs the description and output source and parses the report', async () => {
    const { calls } = stubFetch({
      status: 'BUILT',
      stage: 'publish',
      packName: 'new-spec',
      score: 0.8,
      issues: [],
      scan: null,
      lifecycle: null,
      published: null,
      errorCode: null,
      error: null,
    })
    const res = await api.build({ description: 'a spec', outputSource: 'folder:/out' })
    expect(calls[0].url).toBe('/api/build')
    expect(bodyOf(calls[0])).toEqual({ description: 'a spec', outputSource: 'folder:/out' })
    expect(res.status).toBe('BUILT')
    expect(res.packName).toBe('new-spec')
  })

  it('evaluate POSTs source + spec and parses the eval response', async () => {
    const { calls } = stubFetch({
      error: null,
      errorCode: null,
      spec: 'alpha',
      score: 0.75,
      refinementCount: 1,
      failingTasks: ['t1'],
      passed: false,
      revisedPrompt: 'improved',
    })
    const res = await api.evaluate({ source: 'folder:/s', spec: 'alpha' })
    expect(calls[0].url).toBe('/api/eval')
    expect(bodyOf(calls[0])).toEqual({ source: 'folder:/s', spec: 'alpha' })
    expect(res.score).toBe(0.75)
    expect(res.failingTasks).toEqual(['t1'])
  })

  it('scan POSTs source + spec and parses the verdict + findings', async () => {
    const { calls } = stubFetch({
      error: null,
      scan: {
        specName: 'shell-runner',
        verdict: 'REVIEW',
        recommendedTier: 'HIGH',
        summary: 'one finding',
        findings: [{ severity: 'HIGH', category: 'HIGH_PRIVILEGE_TOOL', message: 'm', evidence: 'e' }],
      },
    })
    const res = await api.scan({ source: 'folder:/s', spec: 'shell-runner' })
    expect(calls[0].url).toBe('/api/security')
    expect(bodyOf(calls[0])).toEqual({ source: 'folder:/s', spec: 'shell-runner' })
    expect(res.scan?.verdict).toBe('REVIEW')
    expect(res.scan?.findings[0].category).toBe('HIGH_PRIVILEGE_TOOL')
  })

  it('checkTrust POSTs source + spec + tier and parses the allowlist result', async () => {
    const { calls } = stubFetch({
      error: null,
      specName: 'shell-runner',
      tier: 'STANDARD',
      allowed: false,
      disallowedGroups: ['shell'],
      declaredToolGroups: ['shell', 'files'],
    })
    const res = await api.checkTrust({ source: 'folder:/s', spec: 'shell-runner', tier: 'STANDARD' })
    expect(calls[0].url).toBe('/api/trust')
    expect(bodyOf(calls[0])).toEqual({ source: 'folder:/s', spec: 'shell-runner', tier: 'STANDARD' })
    expect(res.allowed).toBe(false)
    expect(res.disallowedGroups).toEqual(['shell'])
  })

  it('traces GETs /api/traces with spec + limit and parses the window', async () => {
    const { calls } = stubFetch([
      {
        runId: 'r1',
        timestamp: '2026-01-01T00:00:00Z',
        sourceUri: null,
        sourceRef: null,
        resolvedAt: null,
        pack: 'alpha',
        goal: 'g',
        input: null,
        planner: null,
        trustTier: null,
        steps: [],
        success: true,
        error: null,
        outputExcerpt: null,
      },
    ])
    const res = await api.traces('alpha', 50)
    expect(calls[0].url).toBe(`/api/traces?spec=${encodeURIComponent('alpha')}&limit=50`)
    expect(res[0].runId).toBe('r1')
  })

  it('listCandidates GETs /api/improve/candidates and parses the list', async () => {
    const { calls } = stubFetch([
      {
        id: 'c1',
        spec: 'alpha',
        score: 0.9,
        baseline: 0.7,
        diffSummary: 'tightened prompt',
        scan: null,
        evidence: [],
        proposedState: 'ACTIVE',
      },
    ])
    const res = await api.listCandidates()
    expect(calls[0].url).toBe('/api/improve/candidates')
    expect(res[0].id).toBe('c1')
  })

  it('approveCandidate and rejectCandidate POST to the candidate endpoints', async () => {
    const { calls } = stubFetch({ ok: true, location: 'folder:/out', error: null })
    await api.approveCandidate('c 1')
    await api.rejectCandidate('c 1')
    expect(calls[0].url).toBe('/api/improve/candidates/c%201/approve')
    expect(calls[1].url).toBe('/api/improve/candidates/c%201/reject')
    expect(calls[0].init?.method).toBe('POST')
  })

  it('loop control endpoints hit the /api/loop surface', async () => {
    const { calls } = stubFetch({ ran: true, candidates: 1, publishedSpecs: [], queuedSpecs: ['a'], autoPublish: false, errorCode: null, error: null })
    await api.triggerLoop()
    await api.startLoop()
    await api.stopLoop()
    await api.loopStatus()
    await api.getAutoPublish()
    await api.setAutoPublish({ autoPublish: true })
    expect(calls[0].url).toBe('/api/loop/trigger')
    expect(calls[1].url).toBe('/api/loop/start')
    expect(calls[2].url).toBe('/api/loop/stop')
    expect(calls[3].url).toBe('/api/loop/status')
    expect(calls[4].url).toBe('/api/loop/auto-publish')
    expect(calls[5].url).toBe('/api/loop/auto-publish')
    expect(calls[5].init?.method).toBe('PUT')
    expect(bodyOf(calls[5])).toEqual({ autoPublish: true })
  })

  it('extract POSTs the docs source and parses the endpoint-gated description', async () => {
    const { calls } = stubFetch({ description: '# Business Overview', error: null, errorCode: null })
    const res = await api.extract({ path: '/docs' })
    expect(calls[0].url).toBe('/api/extract')
    expect(calls[0].init?.method).toBe('POST')
    expect(bodyOf(calls[0])).toEqual({ path: '/docs' })
    expect(res.description).toBe('# Business Overview')
    expect(res.errorCode).toBeNull()
  })

  it('extract surfaces the MODEL_NOT_CONFIGURED error', async () => {
    stubFetch({ description: null, error: 'No endpoint', errorCode: 'MODEL_NOT_CONFIGURED' })
    const res = await api.extract({ url: 'https://x/repo' })
    expect(res.errorCode).toBe('MODEL_NOT_CONFIGURED')
  })

  it('listStoredPacks GETs /api/store/packs and parses the list', async () => {
    const { calls } = stubFetch([
      { name: 'alpha', goal: 'alpha_goal', steps: 3, savedAt: '2026-01-01T00:00:00Z' },
    ])
    const packs = await api.listStoredPacks()
    expect(calls[0].url).toBe('/api/store/packs')
    expect(packs[0].name).toBe('alpha')
    expect(packs[0].steps).toBe(3)
  })

  it('loadStoredPack GETs /api/store/packs/{name} with the name encoded', async () => {
    const { calls } = stubFetch({ pack: { manifest: { name: 'a b', version: '1.0.0' } }, error: null })
    const res = await api.loadStoredPack('a b')
    expect(calls[0].url).toBe(`/api/store/packs/${encodeURIComponent('a b')}`)
    expect(res.error).toBeNull()
  })

  it('savePack POSTs the pack and parses the save result', async () => {
    const { calls } = stubFetch({ ok: true, name: 'alpha', error: null })
    const pack: ComposePack = {
      manifest: { name: 'alpha', version: '1.0.0' },
      goal: { stepType: 'goal', name: 'alpha', description: 'd', outputTypeName: 'Final', export: true },
      steps: [],
      types: [],
    }
    const res = await api.savePack({ pack })
    expect(calls[0].url).toBe('/api/store/packs')
    expect(calls[0].init?.method).toBe('POST')
    expect(bodyOf(calls[0])).toEqual({ pack })
    expect(res.ok).toBe(true)
    expect(res.name).toBe('alpha')
  })

  it('listDiscoveries GETs /api/store/discoveries and parses the history', async () => {
    const { calls } = stubFetch([
      { timestamp: '2026-01-01T00:00:00Z', sourceType: 'folder', location: '/docs', docCount: 2 },
    ])
    const history = await api.listDiscoveries()
    expect(calls[0].url).toBe('/api/store/discoveries')
    expect(history[0].location).toBe('/docs')
    expect(history[0].docCount).toBe(2)
  })

  it('throws when the endpoint returns a non-2xx status', async () => {
    stubFetch({}, false, 500)
    await expect(api.run({ source: 's', goal: 'g', input: 'i' })).rejects.toThrow(/status 500/)
  })
})
