# Design

## Context

The claim this change rests on is measured, not assumed: the twelve classes of `studio/improve/` import
**zero** `org.springframework` types. Most of what they do import is `builder.*` already — `GateChain`
pulls in `eval.EvalScorer`, `EvalTask`, `InvokerFactory` and three `security.*` types;
`ImprovementLoop` pulls in `eval.*`, `lifecycle.*` and `publish.*` — which is the strongest evidence that
this package was filed under the wrong module. That is what makes this a move rather than a redesign.

There are **three** couplings to break, not two, and the third was missed in the first draft of this
change: five classes import `com.embabel.smith.runtime.trace.*` — `Detector:14`, `ImprovementLoop:31-32`,
`PendingCandidate:13`, `Refiner:17`, `TraceWindowReader:10-11` — while `embabel-smith-builder` does **not**
depend on `embabel-smith-runtime` (its pom declares smith-common, specsource and skillevaluator, and its
sources contain zero `com.embabel.smith.runtime` references today). See D6.

## Goals / Non-Goals

**Goals:**

- The improvement engine lives in the builder, reachable without a web application on the classpath.
- The move preserves behaviour exactly; a reviewer can diff a moved file and see only the package line.
- The one genuine coupling becomes an interface the builder owns.
- An input-blind pack stops validating green.

**Non-Goals:**

- No behaviour change, no gate reordering, no new policy.
- No merge of studio's `Refiner` with the builder's existing `eval/RefinementLoop`. They do different
  jobs — `Refiner` rewrites the prompts of steps named in failure traces; `RefinementLoop` is the
  builder's eval-driven refinement — and the near-collision of names is worth a javadoc sentence, not a
  refactor.
- No POC work. Reframing POC 27 onto the builder's own AgentSpecs is a separate change that depends on
  this one.

## Decisions

### D1 — Move the package wholesale into the builder

`com.embabel.smith.studio.improve` → `com.embabel.smith.builder.improve`, all twelve classes, names
unchanged.

*Alternative considered*: move only the seven classes POC 27 imports. Rejected — it splits a cohesive
package and leaves `ImprovementLoop` in the UI module importing its own collaborators across a module
boundary, which is the coupling we are removing.
*Alternative considered*: a new `smith-improve` module. Rejected — nothing else would depend on it, and a
module for one consumer is the abstraction this repository's Simplicity First rule exists to refuse.

### D2 — The seam is one method, and it returns nothing

`LoopPublishPolicy` calls the store exactly once and discards the result. The builder therefore declares:

```java
public interface CandidateStore {
    void save(AgentSpec candidate);
}
```

Studio keeps `ProcessStoreCandidateStore implements CandidateStore` delegating to
`ProcessStore.savePackWhenConfigured(...)`, which returns `null` when storage is unbound — a fact the
current call site already ignores.

*Alternative considered*: mirroring the real signature (`PackRecord save(...)`) in the builder. Rejected —
`PackRecord` is a studio persistence type, so importing it back into the builder would recreate the
dependency this seam exists to delete, and the return value is thrown away today.

### D3 — The default version stops living in a JPA entity

`Refiner` needs the string "1.0.0" and nothing else from `PackEntity`. The authoritative constant moves to
a non-persistence home in the builder; `PackEntity.DEFAULT_VERSION` stays public and is assigned from it,
so every existing caller keeps compiling and there is still exactly one definition.

*Alternative considered*: `Refiner` inlines `"1.0.0"`. Rejected — a second literal for a value the store
also uses is how two defaults drift apart.

### D4 — The validator rejects a step that cannot see its own input

An LLM-bound step whose declared input types never appear as template variables in its prompt is
rejected at validation. Today `PromptedActionSpecAction` renders the literal template and a variable the
template never names is simply never sent, so the pack compiles, validates, deploys, and then answers
every distinct eval task with the same byte-identical request.

This is placed in validation rather than in the loop because that is where it is cheap: it fails offline,
against the artifact, instead of after a paid call.

*Alternative considered*: a POC-side assertion. Rejected — the defect is not POC-shaped; the product can
emit the same pack from a blueprint today.
*Deliberately out of scope here*: a rule against an LLM-bound step returning a type whose schema contains
a property-less object (the `BackgroundMessage` trap). Deferred pending a decision on scope.

### D5 — Studio keeps the HTTP surface and nothing else

`LoopService`, `TraceController`, `CandidateInfo` and `StudioConfiguration` stay in studio and re-import
from the builder. Studio's job after this change is to schedule a loop, expose it over HTTP, and persist
what it produced.

### D6 — The trace types are the coupling nobody planned for

Five of the twelve classes are typed against `com.embabel.smith.runtime.trace.*`, and the builder has no
dependency on `embabel-smith-runtime` and never has, so the move cannot start until that is resolved. The
five split by *which* type they need, and the split is what makes this cheap:

- `TraceStore`, the SPI, is needed by two classes only — `TraceWindowReader:28,32,36` and
  `ImprovementLoop:112`.
- `TraceRecord` alone is needed by the other three — `Detector:58-170`, `PendingCandidate:33`,
  `Refiner:37-145`.

**Decided: sink `TraceRecord` and `TraceStore` into `smith-common`, as `com.embabel.smith.common.trace`.**

Both qualify on the repository's own stated test for what may be shared. `TraceRecord` is 56 lines importing
only `java.time.Instant` and `java.util.List`; `TraceStore` is an interface importing only `java.util.List`
and naming `TraceRecord`. Neither holds Embabel agent code — which is exactly why
`docs/05-architecture.md` says the shared piece "stays offline-testable". And `smith-common` is a
**top-level module, not under `prod/`**, already holding `model`, `validation`, `io` and `config`; a
`trace` package there follows the existing pattern rather than starting a new one. The implementations stay
put — `JsonlTraceStore`, `TraceRecorder` and `TraceRecordJson` remain in `runtime/trace/` — so nothing about
how a trace is written changes.

The consequence that matters for the rest of this change: **the builder needs no new dependency at all.**
It already depends on `smith-common`, so once the trace types move, `D2`'s seam and `D3`'s constant are the
only two edits left before the twelve classes can move.

*Alternative considered*: add the `builder → runtime` pom edge. Rejected for a structural reason rather than
a file count — builder and runtime are deliberate siblings that never reference each other and meet only
through the spec source on disk (the runtime *reads* it, the builder *publishes into it*, per `docs/05`).
One pom line would trade that decoupling for a compile-time edge, then have to rewrite the sentence that
records it.
*Alternative considered*: give `TraceWindowReader` a `Supplier<List<TraceRecord>>` so only `TraceRecord`
moves and `TraceStore` stays in runtime. Rejected — a seam invented to route around a dependency question
that has a clean answer, and it would split the SPI across two modules for no gain.

**Cost, measured:** 26 prod files import `runtime.trace.TraceRecord`, 11 import `runtime.trace.TraceStore`
(overlapping). Each is an import-line change with no behaviour edit — more files than the pom edge, and the
honest price of keeping the module graph as documented.

## Risks / Trade-offs

- **A package move touches many imports and can hide a behaviour edit in the noise.** Mitigation: moved
  files differ only in the `package` line and imports; the review is a rename diff, and the module's test
  count must be unchanged across the move (studio's improve tests move with the classes).
- **Builder and studio both refine things.** `builder/eval/RefinementLoop` and the moved
  `builder/improve/Refiner` will sit in one module with similar names. Mitigation: a javadoc line on each
  naming which job it does, rather than a merge that changes behaviour.
- **The validator rule could reject a pack that works.** A step could legitimately ignore its input (a
  fixed generator). Mitigation: the rule fires only when the step declares input types and names none of
  them in the prompt, which a deliberate fixed-output step does not match — and the rule is exercised by
  a test that asserts it does *not* fire on such a pack.
