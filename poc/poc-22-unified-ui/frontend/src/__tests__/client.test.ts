import { afterEach, describe, expect, it, vi } from 'vitest'

import * as api from '../api/client'

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
  it('listSkills GETs /api/skills and parses the palette', async () => {
    const { calls } = stubFetch([
      { name: 'compose-extract', externalInputTypes: ['UserInput'], outputType: 'BackgroundMessage' },
    ])
    const skills = await api.listSkills()
    expect(calls).toHaveLength(1)
    expect(calls[0].url).toBe('/api/skills')
    expect(calls[0].init?.method).toBeUndefined()
    expect(skills[0].name).toBe('compose-extract')
    expect(skills[0].outputType).toBe('BackgroundMessage')
  })

  it('retrieve POSTs the query as JSON and parses the ranked results', async () => {
    const { calls } = stubFetch({
      scorer: 'lexical',
      results: [
        {
          name: 'compose-extract',
          description: 'extract topic/audience',
          score: 0.9,
          outputType: 'BackgroundMessage',
          state: 'ACTIVE',
        },
      ],
    })
    const res = await api.retrieve({ query: 'extract the topic', k: 3 })
    expect(calls[0].url).toBe('/api/retrieve')
    expect(calls[0].init?.method).toBe('POST')
    expect(calls[0].init?.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(bodyOf(calls[0])).toEqual({ query: 'extract the topic', k: 3 })
    expect(res.scorer).toBe('lexical')
    expect(res.results[0].name).toBe('compose-extract')
    expect(res.results[0].score).toBe(0.9)
    expect(res.results[0].state).toBe('ACTIVE')
  })

  it('scan POSTs the skill and parses the verdict + findings', async () => {
    const { calls } = stubFetch({
      error: null,
      skillName: 'shell-runner',
      verdict: 'REVIEW',
      recommendedTier: 'T3_PARTNER',
      summary: 'one high-privilege finding',
      findings: [{ severity: 'HIGH', category: 'HIGH_PRIVILEGE_TOOL', message: 'm', evidence: 'e' }],
    })
    const res = await api.scan({ skillName: 'shell-runner' })
    expect(calls[0].url).toBe('/api/security')
    expect(bodyOf(calls[0])).toEqual({ skillName: 'shell-runner' })
    expect(res.verdict).toBe('REVIEW')
    expect(res.findings[0].category).toBe('HIGH_PRIVILEGE_TOOL')
  })

  it('run POSTs the request and parses the endpoint-gated response', async () => {
    const { calls } = stubFetch({ error: 'No resolvable model endpoint is configured.', output: null })
    const res = await api.run({ skillName: 'compose-extract', input: 'hi' })
    expect(calls[0].url).toBe('/api/run')
    expect(bodyOf(calls[0])).toEqual({ skillName: 'compose-extract', input: 'hi' })
    expect(res.output).toBeNull()
    expect(res.error).toContain('No resolvable model endpoint')
  })

  it('scale GETs /api/scale and parses the P@1 table', async () => {
    const { calls } = stubFetch({
      scorer: 'lexical',
      coreSize: 10,
      coreP1: 1.0,
      fullSize: 20,
      fullP1: 0.5,
      curve: [{ librarySize: 10, precisionAt1: 1.0 }],
    })
    const res = await api.scale()
    expect(calls[0].url).toBe('/api/scale')
    expect(calls[0].init?.method).toBeUndefined()
    expect(res.coreP1).toBe(1.0)
    expect(res.curve[0].librarySize).toBe(10)
  })

  it('throws when the endpoint returns a non-2xx status', async () => {
    stubFetch({}, false, 500)
    await expect(api.retrieve({ query: 'x', k: 1 })).rejects.toThrow(/status 500/)
  })
})
