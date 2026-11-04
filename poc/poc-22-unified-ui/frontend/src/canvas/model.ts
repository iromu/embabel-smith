/**
 * The type-checked "snap" rule and chain validation for the Compose pane, as pure functions.
 *
 * This mirrors POC 14's `CompositionCanvas` (validate + chain + the edge type-check) so the browser
 * can check a connection the instant it is made — the drag-and-drop "snap" is exactly "skill A's
 * output type is one of skill B's external inputs". Keeping it pure (no DOM, no Vue) makes it
 * trivially unit-testable and small enough to hold and edit.
 */

/** A skill offered by the palette (mirrors the backend `SkillInfo` DTO). */
export interface SkillInfo {
  name: string
  externalInputTypes: string[]
  outputType: string
}

/** A directed canvas edge: `from`'s output feeds `to`'s input. */
export interface Edge {
  from: string
  to: string
}

/** The canvas to validate: the placed skills, the connections, and the palette. */
export interface CanvasInput {
  nodes: string[]
  edges: Edge[]
  skills: SkillInfo[]
}

/** The validation result: whether it is a valid chain, the issues, and the composed chain. */
export interface CanvasResult {
  valid: boolean
  issues: string[]
  chain: string[]
}

/** The snap rule for one connection: whether it is type-compatible, and the reason if not. */
export interface EdgeCheck {
  valid: boolean
  reason?: string
}

/** Short name of the framework entry type a composed chain must start from. */
export const USER_INPUT = 'UserInput'
/** Short name of the framework output type a composed chain must end in. */
export const BACKGROUND_MESSAGE = 'BackgroundMessage'

/** Strip a package qualifier to the short type name (mirrors POC 14's `shortName`). */
export function shortName(typeName: string): string {
  const idx = typeName.lastIndexOf('.')
  return idx >= 0 ? typeName.slice(idx + 1) : typeName
}

/**
 * The snap rule: a connection is valid only when the source skill's output type is one of the
 * target skill's external input types. An incompatible connection is flagged with the
 * type-mismatch reason.
 */
export function checkEdge(source: SkillInfo, target: SkillInfo): EdgeCheck {
  const targetInputs = new Set(target.externalInputTypes.map(shortName))
  if (targetInputs.has(shortName(source.outputType))) {
    return { valid: true }
  }
  return {
    valid: false,
    reason: `'${shortName(source.outputType)}' is not one of ${target.name}'s inputs [${target.externalInputTypes
      .map(shortName)
      .join(', ')}]`,
  }
}

/**
 * Validates a canvas as a single, executable skill chain. Returns the issues (empty when valid) and
 * the composed chain. Mirrors POC 14's `validate()` / `chain()`.
 */
export function validateCanvas(input: CanvasInput): CanvasResult {
  const { nodes, edges, skills } = input
  const byName = new Map(skills.map((s) => [s.name, s]))
  const issues: string[] = []

  // Only placed, known skills participate; duplicates are collapsed.
  const placed: string[] = []
  const seenNode = new Set<string>()
  for (const name of nodes) {
    if (byName.has(name) && !seenNode.has(name)) {
      placed.push(name)
      seenNode.add(name)
    }
  }
  const placedSet = new Set(placed)

  // Every edge must reference placed skills and be type-compatible.
  for (const edge of edges) {
    if (!placedSet.has(edge.from) || !placedSet.has(edge.to)) {
      issues.push(`edge ${edge.from} -> ${edge.to} references a missing skill`)
      continue
    }
    const source = byName.get(edge.from)!
    const target = byName.get(edge.to)!
    const check = checkEdge(source, target)
    if (!check.valid) {
      issues.push(`edge ${source.name} -> ${target.name} is type-incompatible: ${check.reason}`)
    }
  }

  const entries = placed.filter((n) =>
    byName.get(n)!.externalInputTypes.some((t) => shortName(t) === USER_INPUT),
  )
  const exits = placed.filter((n) => shortName(byName.get(n)!.outputType) === BACKGROUND_MESSAGE)

  if (entries.length !== 1) {
    issues.push(`expected exactly one entry skill (input ${USER_INPUT}), found ${entries.length}`)
  }
  if (exits.length !== 1) {
    issues.push(`expected exactly one exit skill (output ${BACKGROUND_MESSAGE}), found ${exits.length}`)
  }

  // Structure: every non-entry skill has exactly one incoming edge; every non-exit skill has exactly
  // one outgoing edge.
  const inDeg = new Map<string, number>()
  const outDeg = new Map<string, number>()
  placed.forEach((n) => {
    inDeg.set(n, 0)
    outDeg.set(n, 0)
  })
  for (const edge of edges) {
    if (placedSet.has(edge.from) && placedSet.has(edge.to)) {
      outDeg.set(edge.from, (outDeg.get(edge.from) ?? 0) + 1)
      inDeg.set(edge.to, (inDeg.get(edge.to) ?? 0) + 1)
    }
  }
  const entry = entries.length === 1 ? entries[0] : null
  const exit = exits.length === 1 ? exits[0] : null
  for (const n of placed) {
    const expectedIn = entry === n ? 0 : 1
    const expectedOut = exit === n ? 0 : 1
    if ((inDeg.get(n) ?? 0) !== expectedIn) {
      issues.push(`${n} has ${inDeg.get(n)} incoming edge(s), expected ${expectedIn}`)
    }
    if ((outDeg.get(n) ?? 0) !== expectedOut) {
      issues.push(`${n} has ${outDeg.get(n)} outgoing edge(s), expected ${expectedOut}`)
    }
  }

  let chain: string[] = []
  if (issues.length === 0) {
    try {
      chain = computeChain(placed, edges, entry!, exit!)
    } catch (e) {
      issues.push(e instanceof Error ? e.message : String(e))
    }
  }
  if (issues.length === 0 && chain.length !== placed.length) {
    issues.push('the chain does not cover every skill on the canvas')
  }

  return { valid: issues.length === 0, issues, chain }
}

/** The ordered path from the entry skill to the exit skill (the plan the composite will run). */
function computeChain(placed: string[], edges: Edge[], entry: string, exit: string): string[] {
  const path: string[] = []
  const seen = new Set<string>()
  let current: string | null = entry
  while (current !== null) {
    if (seen.has(current)) {
      throw new Error(`cycle detected at ${current}`)
    }
    seen.add(current)
    path.push(current)
    const next = edges.find((e) => e.from === current)
    current = next ? next.to : null
  }
  if (path[path.length - 1] !== exit) {
    throw new Error(`chain does not reach the exit skill ${exit}`)
  }
  return path
}
