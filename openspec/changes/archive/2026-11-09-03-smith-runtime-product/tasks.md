# Tasks

## 1. Spike

- [x] 1.1 Spike: pin the per-step extraction seam on `AgentProcess` (blackboard entries vs process events) with a scratch run against the POC 1 pack; record the finding in `prod/embabel-smith-runtime/README.md` (section "Trace seam")

## 2. Runtime core (extraction)

- [x] 2.1 Extract POC 1's ingest/run into `RuntimeCore` (pack-parameterized: `StepSpecAssembler`, `SkillPackTypeMapper`, `PlannerResolver` equivalents)
- [x] 2.2 Generalize to multi-pack ingest from a `ResolvedSkillSource` (POC 6 pattern): shared DataDictionary, cross-pack plan, duplicate-goal-name collision error
- [x] 2.3 Install the POC 10 trust-tier guardrail per run, tier selectable (`standard` / `high`)
- [x] 2.4 Wire `SkillRunService` (source → resolve → ingest → run → record) as a plain class

## 3. Traces

- [x] 3.1 Define the `TraceRecord` model + JSONL serialization (schema per design.md)
- [x] 3.2 Implement per-step capture via the seam pinned in 1.1 (step name, status, duration, output excerpt)
- [x] 3.3 Implement the JSONL trace store (append, default `~/.smith/traces/runs.jsonl`, corrupt-line-tolerant reader)
- [x] 3.4 Tests: record on success, record on failure (unmet preconditions / tier block), append semantics, corrupt-line read, excerpt truncation

## 4. CLI

- [x] 4.1 Implement the picocli CLI `smith run` (args per spec; stdout goal output / stderr errors; exit codes)
- [x] 4.2 CLI tests: arg parsing, missing-goal error lists available goals, failure exit code

## 5. Verify + docs

- [x] 5.1 `mvn -pl prod/embabel-smith-runtime test` green offline
- [x] 5.2 Endpoint-gated integration test (skipped without `OPENAI_CUSTOM_*`): one live run emits a real trace record
- [x] 5.3 Write `prod/embabel-smith-runtime/README.md` (usage, trace schema, trace-seam finding)
