# Design

## Context

See `proposal.md` for motivation; `specs/business-process-ingestion/spec.md` for the contract.

The three classes being moved are plain classes with no Spring dependency, which is what makes this a
move rather than a redesign:

| Class | Depends on | Notes |
|---|---|---|
| `BusinessProcessDiscovery` | nothing (JDK + `git` CLI) | 300K corpus cap, `.md`/`.markdown` only, `git clone --depth 1` with a 180 s bound |
| `BlueprintLoader` | nothing | 200K cap, adds `.txt`, joins with `--- <relPath> ---`, emits a truncation marker |
| `ProcessExtractor` | `builder.core.SpecPromptCaller` | already a builder type — the dependency already points studio-to-builder |

Two constraints worth stating before the decisions:

- **The two caps are different on purpose.** `BusinessProcessDiscovery` caps the corpus it *presents*
  at 300K; `BlueprintLoader` caps the corpus it *generates from* at 200K. They are two boundaries, not
  one drifted value.
- **The studio's Generate path does not extract.** `GenerateController` feeds the `BlueprintLoader`
  corpus straight into the generator; extraction is a separate pane action whose result the UI then
  offers as the next blueprint. Any CLI surface must keep both paths available, not invent a third.
- **The prompt has exactly one call site** (`StudioConfiguration:266`), so moving the resource cannot
  strand a reader.

## Goals / Non-Goals

**Goals:**

- Documents become a builder input, reachable from the CLI and from the builder's own tests.
- The move is behaviour-preserving enough that the studio's REST responses can be diffed unchanged.
- The three ingestion steps stay independently callable, because the UI's interaction depends on it.

**Non-Goals:**

- No change to what a build *does*: `BuilderService`, its gates, and `SkillBuilderService` are
  untouched.
- No unification of the two corpus caps, and no new ingestion behaviour (chunking, summarising,
  document selection) beyond what exists today.
- No move of `improve/` or `loop/`, which are the same problem at larger scale.

## Decisions

**D1 — New package `com.embabel.smith.builder.blueprint`, class names unchanged.**
`blueprint` is the concept `BlueprintLoader` already names, and it reads as a stage rather than a
layer. Rejected: folding into `core/`, which is about generation and would make ingestion and
generation one package with two reasons to change; and naming it `docs`, which collides with the
repository's own `docs/` meaning. Names are kept because a rename turns a reviewable `git` rename into
an unreadable diff for no behavioural gain.

**D2 — Composable pre-stages; `build()` keeps its signature.**
Ingestion ends in a `String` and stops there. Rejected: an overload taking a documents path and
extracting inside `build()` — extraction has no gate to belong to, so it would sit inside a pipeline
whose stages are all defined by what they can block, and it would force the studio to pay for a step
its UI exposes separately. The cost of the chosen shape is that a caller who wants documents-to-build
composes two calls; that is what D3 removes for the CLI.

**D3 — CLI takes `--docs <path>`, with `--extract` opting into extraction first.**
`--docs` alone sends the assembled corpus to the build, which is exactly what the studio's Generate
path does today; `--docs` with `--extract` inserts the discovery prompt first, which is what the UI's
two-step interaction does. The positional description becomes optional and mutually exclusive with
`--docs`, so an invocation naming neither is a usage error (exit 2, matching the existing picocli
convention). Rejected: always extracting — it doubles the model cost of every docs build and would be
slower and more expensive for the path that already works; and a separate `smith ingest` subcommand,
which puts the description in a temp file for a human to paste.

**D4 — `ProcessExtractor` carries its own prompt default, overridable per instance.**
It gains `ProcessExtractor(SpecPromptCaller)` loading
`/prompts/business_process_discovery.jinja` from the builder classpath, keeping the existing
two-argument constructor for substitution. This mirrors `SkillBuilderService(SpecPromptCaller)` and
`SingleShotAgentSpecGenerator.PROMPT_RESOURCE`. `StudioConfiguration`'s private `loadPrompt` helper
loses its last caller and is deleted, along with the studio's now-empty `prompts/` directory. Rejected:
a builder-wide `BlueprintPrompts` holder for a single prompt, where `SkillPrompts` exists because it
holds five.

**D5 — The duplicated `expandHome` stays duplicated.**
Both loaders carry an identical eight-line private helper. Consolidating them is a correct
micro-improvement and is not part of a behaviour-preserving move; it would add a type whose only
purpose is to make the diff harder to audit.

**D6 — The studio keeps its bean graph, with the types re-pointed.**
`blueprintLoader`, `processExtractor`, `extractFn` and `businessProcessDiscovery` keep their names,
return types from the builder package, and the controllers keep their constructor shapes. The
controllers are the UI's seam and they do not change; only what they are wired to.

## Risks / Trade-offs

- **Studio test-compile breaks silently behind the builder.** A builder-package change has previously
  broken studio test sources without the builder's own build noticing. → Verification is
  reactor-wide (`make test`), not `make test -pl prod/embabel-smith-builder`.
- **A move that is not behaviour-preserving hides in the prompt.** The prompt text travels unchanged;
  if it is edited during the move, extraction quality changes and the cause is invisible in a rename
  diff. → The prompt travels as a pure `git mv`, and the studio's extraction test asserts the prompt is
  threaded as the system message before and after.
- **Two caps, one glance, tempting to unify.** → Both caps keep their own constant with the boundary
  it belongs to named in the javadoc, so the difference reads as a decision.
- **`git clone` in the builder widens what the builder shells out to.** It already shells out to `git`
  for `GitPublisher`, with `GIT_TERMINAL_PROMPT=0`; ingestion must use the same non-interactive
  posture, or a private repository turns a build into a hang. → Ingestion's clone inherits the
  publisher's environment settings.
- **Zero behaviour change means no natural test delta.** → The tests move with the classes and gain
  one case for the CLI's `--docs` path, which is the only genuinely new behaviour.

## Open Questions

- Whether `--extract` should also be reachable from the studio's Generate request body rather than
  only through its separate extract action. Deferrable: it changes neither the spec, nor D2, nor the
  task breakdown, and the UI already reaches extraction directly.
