# Proposal

## Why

`prod/embabel-smith-studio` is a UI utility. Its own standing rule is that generation, ingestion and
skill logic live in the builder. The self-improvement engine violates that rule: all twelve classes of
`studio/improve/` — `Detector`, `GateChain`, `GateResult`, `ApprovalQueue`, `PendingCandidate`,
`LoopPublishPolicy`, `LifecycleTransitions`, `ImprovementLoop`, `InFlightSet`, `LoopPushDisabledException`,
`Refiner`, `TraceWindowReader` — sit in the UI module, and they have **no Spring coupling at all**: zero
`org.springframework` imports across the whole package. They are builder logic that happens to be filed
under a web app.

The cost is already being paid. `poc/poc-27-recursive-self-improvement` needs eight of these types, so its
`pom.xml` declares `embabel-smith-studio` and must exclude `spring-boot-starter-web`,
`spring-boot-starter-data-jpa`, `postgresql` and `spring-boot-docker-compose` merely to boot — a POC
dragging a server application onto its classpath to reach a scoring gate. It also blocks the reframe of
POC 27 onto the builder's own AgentSpecs, which cannot be authored while the improver is unreachable
without the studio.

## What Changes

- The twelve classes move to a new `com.embabel.smith.builder.improve` package in
  `embabel-smith-builder`. This is a package move, not a rewrite: behaviour, names and signatures are
  preserved.
- **One seam replaces the only studio coupling.** `LoopPublishPolicy` reaches into studio exactly once —
  `packStore.savePackWhenConfigured(...)` at `LoopPublishPolicy.java:114`, whose return value is
  discarded. A one-method builder-side interface takes its place and studio keeps an adapter over
  `ProcessStore`.
- **The trace types move to `smith-common`, which is what keeps the module graph intact.** Five classes are
  typed against `com.embabel.smith.runtime.trace.TraceRecord` / `TraceStore`
  (`Detector:58-170`, `PendingCandidate:33`, `Refiner:37-145` need only the record;
  `TraceWindowReader:28`, `ImprovementLoop:112` need the SPI), and the builder neither depends on
  `embabel-smith-runtime` nor has ever referenced it. `TraceRecord` is 56 lines importing only `Instant` and
  `List`; `TraceStore` is an interface importing only `List` — neither holds Embabel agent code, so both
  qualify for `smith-common`, which is a top-level module rather than a `prod/` one. This adds **no** module
  edge and needs **no** edit to `docs/05-architecture.md`; builder and runtime stay siblings that meet only
  through the spec source. Price: 26 + 11 import lines, behaviour untouched. Design D6.
- **One constant stops being a reason to depend on a JPA entity.** `Refiner.java:104` uses
  `PackEntity.DEFAULT_VERSION` ("1.0.0") and nothing else from that class. The default version moves to a
  non-persistence home and `PackEntity` keeps its public constant as a delegate, so no caller breaks.
- Studio's four non-`improve` callers (`LoopService`, `TraceController`, `rest/dto/CandidateInfo`,
  `StudioConfiguration`) and its three affected test classes are re-imported, not rewritten.
- `poc-27` drops its `embabel-smith-studio` dependency and the four exclusions that existed only because
  of it.
- `AgentSpecValidator` gains a rule that an LLM-bound step's declared input variables must actually
  appear in that step's prompt. This is the guard that would have caught POC 27's invalid run offline:
  today an input-blind pack validates green and only fails against a live model, and no test in the
  repository compares answers across eval tasks.

## Impact

- Affected specs: `improvement-loop-location` (new), `agent-spec-validation` (modified).
- Affected code: `prod/embabel-smith-builder` (new package, one interface, one constant),
  `prod/embabel-smith-studio` (deleted package, one adapter, four re-imported callers, three test files),
  `smith-common` (validator rule), `poc/poc-27-recursive-self-improvement` (pom only).
- `prod/pom.xml`'s `ban-poc-dependencies` enforcer rule is unaffected — it bans `poc-*` from prod, and
  nothing here introduces one.
- No behaviour change to the loop itself. The gate order, approval queue, publish policy and lifecycle
  blocks are the same code with a different package declaration.
