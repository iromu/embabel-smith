# Design

## Context

**The evaluator's API.** `Tier1Runner` is the whole public surface
(`prod/embabel-smith-skillevaluator/src/main/java/org/iromu/embabel/smith/skillevaluator/Tier1Runner.java`):

```java
public static Builder builder()                                                   // line 91
public List<ValidationResult> run(Path targetPath, Options options)               // line 134
public List<ValidationResult> run(Path targetPath)                                // line 242
public static final List<String> DEFAULT_CHECKS = List.of("schema", "version", "security", "pii",
        "license", "code-integrity", "unicode", "quality", "lint");               // line 61
```

It takes an **on-disk path** — "the skill file or folder of skills to validate" — never in-memory texts.
Options come from the builder: `checks(String...)`, `skip(String...)`, `minScore(int)`,
`previousVersion(String)`, `failFast(boolean)`, `continueOnFailure(boolean)`, `secretsScanner(...)`,
`securityScanner(...)`. `Options` exposes only `getChecks()`, `getMinScore()`, `isFailFast()` publicly.

**Report shape and severities.** One `ValidationResult` per validator, in canonical pipeline order.
`isPassed()` (line 63), `getStatus()` returning `passed` / `failed` / `incomplete` (line 100),
`getFindings()`, `getErrors()`, `getWarnings()`, `getGateFailures()` (line 132), `getIncompleteScans()`.
`Severity` is `CRITICAL, HIGH, MEDIUM, LOW, INFO` with `isError(failOnMedium, failOnLow)`
(`model/Severity.java:59`) whose error levels are `CRITICAL` and `HIGH` only — so with default flags,
**MEDIUM and LOW never fail a result**; they land in `getWarnings()`. Each `Finding` carries
`getSuggestion()` (line 92) and `toLegacyString()` (line 121), which is `[CATEGORY-SEVERITY] message in
file:line`. "Passes" therefore means: no CRITICAL/HIGH finding, no `addError`, no `failGate`, and no
incomplete scan.

**The gate's central constraint: an incomplete scan is a failed result.** `markScanIncomplete`
(`model/ValidationResult.java:275`) records the scanner name *and sets `passed = false`*. So a naive
"every result passed" gate fails on any machine without the binaries. The module's own integration test
dodges this by injecting always-available fake scanners (`Tier1RunnerIntegrationTest`'s `CLEAN_SECURITY`
and `CLEAN_SECRETS`); the builder gets no such luxury, which is why the lineup D3 requests excludes every
shelling-out check by default and states, in the report and the docs, that it did not run them.

**Which validators need external tooling** (all read this session):

| Check | Validators | Pure JVM or external binary | Behaviour when the binary is absent |
| --- | --- | --- | --- |
| `schema` | `SchemaValidator` | pure JVM (SnakeYAML) | n/a — always runs |
| `version` | `VersionValidator` | pure JVM | n/a |
| `quality` | `QualityScoreValidator` | pure JVM | n/a |
| `lint` | `ScriptLintValidator` + `PythonSyntax` | pure JVM — "This port may neither embed a Python parser nor shell out to one", so the AST rules run over a tokenizer port | n/a |
| `unicode` | `UnicodeSmuggleValidator` | pure JVM | n/a |
| `pii` | `PiiScanner` | pure JVM (regex) | n/a |
| `license` | `LicenseValidator` | pure JVM | n/a (lenient by default: a missing licence is a warning, `strictMode` makes it an error, line 262) |
| `security` | `SecurityValidator` → `SkillspectorSecurityScanner` | **external `skillspector`** | warns `"skillspector not installed - skipping security scan. " + INSTALL_HINT` (line 119) and `markScanIncomplete` → **result not passed** |
| `secrets` (inside `code-integrity`) | `SecretsValidator` → `GitleaksSecretsScanner` | **external `gitleaks`** | warns `"gitleaks not installed - secrets scanning skipped. "` (line 172) + `markScanIncomplete` → **result not passed** |
| `code-integrity` | `CodeRiskValidator` (Bandit for `.py`, Semgrep when any `.py`/`.sh`/`.js` exists) | **external `bandit` and `semgrep`** | Bandit: warns `"bandit not installed. "` (line 245) + `markScanIncomplete("bandit")` → **not passed**. Semgrep: *skipped with a message only* unless `SKILLEVALUATOR_REQUIRE_SEMGREP` is set (lines 441-447) — the one missing-tool case that does not fail the result, and the precedent for how to treat an absent optional tool. Both are skipped entirely when the skill has no scripts (line 197) |

**No model calls: confirmed.** `prod/embabel-smith-skillevaluator/pom.xml` declares only SnakeYAML,
jackson-databind and test dependencies — no Embabel, no `Ai`, no HTTP client. `grep -ri "embabel|llm|
OpenAI|OperationContext|springframework"` over its POM returns only the module's own coordinates.
`SkillspectorSecurityScanner` passes `--no-llm` unconditionally and documents that "this port has no LLM
stage at all". `docs/05-architecture.md:21` says the same: "it runs with no model call and depends only
on SnakeYAML and Jackson". The claim holds.

**Where the skill target stands.** `skill/SkillBuilderService.build` runs generate (blocked at
`generate`, line 103) → `SkillMdValidator.validate` (line 107) → evals drafted → benchmark arms (line 124)
→ lift gate (line 125) → `scanner.scanText` (line 130) → lifecycle stamp → publish (line 145). There is
**no refinement loop on this path**: `eval/RefinementLoop` takes an `AgentSpec` and rewrites the terminal
action's prompt, and its `--max-refine` knob (`cli/BuildCommand.java:65`, default
`BuildOptions.DEFAULT_MAX_REFINE = 3` at `BuildOptions.java:41`) belongs to the agent-spec target.
The draft is `SkillGenerator.generate(skillName, description)` (line 46) returning an in-memory
`record Skill(String name, String description, String body)`; `SkillPublishable.writeTo` is what turns it
into `SKILL.md` + `evals.json` + `lifecycle.yml`, and only at publish time.

## Goals / Non-Goals

**Goals**

- Make the skill writing guidelines part of the built process, as the owner ruled, by running them on
  every skill build.
- Use the library for the whole set of checks it can run without an external tool — not a three-check
  slice of it — and name out loud, in the report and in the docs, the three checks that need a binary and
  therefore do not run.
- Run them where they save money: before the benchmark, not after it.
- Close the loop — findings go back to the model, bounded, and a skill that still fails is refused.
- Keep `make build` and `make test` green on a machine with none of the four scanner binaries installed
  and no network.
- Give the evaluator its first real consumer so it stops being dead code, without making the builder
  depend on anything the enforcer bans.

**Non-Goals**

- No use of SkillSpector, Gitleaks, Bandit or Semgrep **on the default path**, and therefore no decision
  about shipping their install hints (D3a and Risks). The opt-in flag exists for an operator who installed
  them deliberately; enabling them by default is not in this change.
- No change to the eval arms, the lift gate, `Benchmark`, `EvalRunner`, or the agent-spec target.
- No second security gate; `AgentSpecSecurityScanner` keeps owning security, and
  `builder-skill-gate-integrity` keeps widening what it scans.
- No CLI or standalone entry point for the evaluator; the builder is the consumer.
- No `evals.json` inspection — that file is not a skill document and the other change owns it.

## Decisions

### D1: a normal Maven dependency, not an extraction

`embabel-smith-builder` depends on `embabel-smith-skillevaluator`, and the module gains a root
`dependencyManagement` entry. The ban is `<exclude>org.iromu.embabel.smith:poc-*</exclude>`
(`prod/pom.xml:46`); the artifact does not match it, and the "Extract, do not depend" rule in
`docs/05-architecture.md` is about `poc-*` launchers whose Spring configuration would leak — the
evaluator has no Spring and no Embabel on its classpath at all.
**Rejected: copy the validators into the builder.** 50 source files, a Python-port with its own
`NOTICE` and NVIDIA-derived headers; a copy would fork the provenance the carried-over `NOTICE` task
exists to record. **Rejected: reflection or ServiceLoader indirection to avoid the dependency.** That
buys flexibility nobody asked for to route around a ban that does not apply.
The differing package root (`org.iromu.embabel.smith.skillevaluator` versus `com.embabel.smith.*`) is
cosmetic: it is the same groupId as the parent, there is no component scan crossing it (the evaluator has
no Spring beans), and `checkstyle.xml` carries no `ImportControl`/`IllegalImport` — only the license
header rules. One new builder class imports it, so the seam is greppable.

### D2: the gate sits after the frontmatter check, before the eval arms

Order becomes: generate → frontmatter → **inspect** → evals drafted → arms + lift → security scan →
stamp → publish.
**Why before the arms:** inspection costs no model calls and can reject a draft for being over 500 lines,
missing its `# Title`, or scoring under 70 — every one of which makes a benchmark run wasteful. Putting
it after would pay ~4 calls per scenario to measure a skill the evaluator already refuses.
**Why after the frontmatter check:** `SkillMdValidator` is cheap, already there, and its failures are a
strict subset of "this is not a well-formed skill"; the two must not be merged, because the evaluator's
schema check reads the rendered file and the frontmatter validator reads the record.
**Ordering against `builder-skill-gate-integrity`:** that change scans `evals.json` in the *security*
stage and refuses scenarios with no expectations at the *eval* stage. Neither overlaps this gate, and
this gate must not read `evals.json` at all — it materialises only `SKILL.md`. The two gates therefore
run in a fixed order with different inputs: `inspect` (SKILL.md, authoring rules, this change) →
`eval` (suite shape) → `security` (SKILL.md + evals.json, injection markers). If both are implemented,
this one fires first and can only ever *reduce* the number of builds that reach the other.

### D3: the lineup is every check that needs no binary; the binary-backed ones are skipped and disclosed

The owner ruled "skip and disclose, and the Java library must actually be used". The gate therefore
requests **every check the module can run without an external tool** — `schema`, `version`, `quality`,
`unicode`, `lint`, `license`, plus `pii` — through
`Tier1Runner.builder().checks("schema", "version", "quality", "unicode", "lint", "license", "pii")`, and
**does not enable** `security`, `secrets` or `code-integrity`. Because `run` builds its step list from the
enabled set (`Tier1Runner.java:134`), a check that is not requested is never constructed, so no subprocess
is spawned and no install hint is reachable.

Splitting the seven the way the ruling requires:

| Check | In the lineup | Effect on the build | Why |
| --- | --- | --- | --- |
| `schema` | yes | blocking | the authoring rules the owner named: `# Title` body heading (HIGH), recommended sections, the 500-line ceiling (`util/Constants.java:100`), name/folder consistency |
| `version` | yes | blocking | validates `metadata.version` when present and requires strict bumps; on a builder skill it passes with a `version_optional` success (`VersionValidator.java:259`) |
| `quality` | yes | blocking | the 4-dimension score against the 70 floor (`util/Constants.java:177`) |
| `unicode` | yes | blocking | hidden-Unicode smuggling is invisible to every other gate and pure JVM |
| `lint` | yes | blocking | kept deliberately, per the ruling: on a scriptless builder skill it records `"No scripts/ or tools/ directory found"` (`ScriptLintValidator.java:148`) and cannot fail — it reports, and it starts biting the moment a skill ships a script |
| `license` | yes | blocking | kept per the ruling even though a missing licence is only a warning off `strictMode` (`LicenseValidator.java:262`); it does block on a *blocked* or unsupported licence expression, so it is not inert |
| `pii` | **yes** | **report-only** | the ruling accepts the reasoning: a legitimate skill naming a contact address must not block a build on a regex — but it belongs in the lineup and in the report, labelled advisory, so a reader sees it ran |
| `security`, `secrets`, `code-integrity` | **no** | skipped, **disclosed by name** | each needs a binary (`skillspector`, `gitleaks`, `bandit`, `semgrep`) whose absence sets `passed = false` through `markScanIncomplete`, and `security` is owned by `AgentSpecSecurityScanner` at stage `security` |

**Rejected: the original three-check lineup (`schema, quality, unicode`).** It used the library as a
narrow authoring linter when seven checks need no binary at all; under-using a dependency that the owner
has ruled must be used is its own defect. **Rejected: full `DEFAULT_CHECKS`.** The three binary-backed
checks would make the build machine's tool inventory decide whether a build succeeds — every absent tool
sets `passed = false` — and `security` would duplicate the stage that owns it. **Rejected: enabling the
binary checks and tolerating their `incomplete` verdicts.** That runs a 1200-second-watchdog subprocess per
skill build for a result that cannot affect the verdict, which is cost with no authority.
**Rejected: dropping `lint` and `license` as checks that cannot fire.** They cost microseconds, they
report what they looked at, and one of them starts failing the day a skill carries a script.

`pii` is report-only **by check name, not by severity**: the seam classifies a result as blocking only if
its check is in the blocking set, so a PII result that reports `isPassed() == false` contributes advisory
findings to the report and no redraft. Doing it any other way would let a PII finding silently acquire
blocking authority the moment the validator got stricter.

### D3a: an operator opt-in for a machine that has the tools, off by default

`--full-inspection` adds `security`, `secrets` and `code-integrity` to the requested lineup for someone
who has `skillspector`/`gitleaks`/`bandit`/`semgrep` installed. It is the only path on which the install
hints can surface, so it is opt-in, documented, and named in `docs/08-builder.md` beside the statement
that the default build does none of that scanning. It is also the only path on which D7's
`incomplete`-is-never-a-pass rule has a live code path rather than a hypothetical one — which is why the
rule is worth keeping despite the default lineup never exercising it.
**Rejected: an env var in the evaluator's own idiom (`SKILLEVALUATOR_REQUIRE_SEMGREP`-style).** The
builder's operator-facing knob is picocli options (`--extract`, `--min-lift`), and a hidden env var
changing what a build checks is the wrong shape for a check whose absence is a stated product limit.
**Rejected: shipping it on by default when the tools are present.** Then a build's verdict depends on
what happens to be installed, and two people running the same command get different guarantees.

### D4: the loop is the builder's own, bounded by a new `--max-inspect`, not `--max-refine`

`--max-inspect` (default 2 → at most three drafts and two redraft calls), a new `BuildOptions` field.
Each round: generate → materialise → inspect → if blocking findings remain and rounds are left, append
them to the next generation request.
**Rejected: reusing `--max-refine`.** It already means "agent-spec eval refinement rounds", is documented
that way, is accepted on both targets but only read by one, and defaulting to 3; overloading it would
couple two unrelated bounds and silently change skill-build cost whenever someone tuned refinement for a
spec build. **Rejected: reusing `eval/RefinementLoop`.** It is typed to `AgentSpec` and `EvalTask` and
rewrites a terminal action's prompt; adapting it means generalising a class the agent-spec path depends
on for a consumer with nothing in common with it. The new loop copies its two properties that matter: the
`round <= max` bound with an explicit break at the last round, and never accepting a rewrite that fails
validation.

### D5: only blocking-check findings are fed back; everything else is reported

Fed back: the findings of every result whose check is in the blocking set **and** whose `isPassed()` is
false, formatted `toLegacyString()` plus `getSuggestion()` when present, plus the quality validator's
`getGateFailures()` (the score shortfall is a gate failure with no finding of its own). Bounded to a
fixed number of lines so a pathological skill cannot bloat the prompt.
Reported, never fed back: the `pii` result's findings whatever they say (D3), and everything in
`getWarnings()`. By `Severity.isError` the warnings are all MEDIUM/LOW — `SchemaValidator` labels its
recommended-section findings MEDIUM and says in the suggestion text that it "is a convention nudge … this
can be skipped". Feeding those to the model would spend a redraft per nudge, and the model would
plausibly comply by padding the body until the 500-line check fires. The build report carries them,
**labelled advisory and naming the check that produced them**, so a reader can tell "PII scan ran and
found two email-shaped strings" from "PII scan did not run".
**Rejected: feed everything back.** More calls, no better skills. **Rejected: feed nothing back (inspect
and block only).** That is not what the owner asked for ("iterate over them until the inspection passes")
and it wastes a gate whose findings are actionable text. **Rejected: decide report-only versus blocking by
severity alone.** `pii` would flip to blocking the day its heuristics got stricter, which is a policy
change smuggled in as a validator change.

### D6: still failing after the last round → blocked at stage `inspect`, nothing published

`SkillBuildResult.blocked("inspect", skillName, findings, null, null)`; the report names the round count
and every blocking finding. No publish-with-warning, because the pipeline's existing contract is
"Each gate stops the build and reports the stage and reason; nothing is published when a gate fails"
(`SkillBuilderService` class javadoc) and the studio/loop callers key off `stage`.
**Rejected: publish and stamp a `needs-work` flag.** The lifecycle sidecar has no such state and
inventing one is a bigger change than refusing a bad skill.

### D7: the binary-backed checks are skipped and disclosed, and an incomplete scan is never a pass

Under the default lineup **no external binary is on the path**, so an offline build is neither blocked nor
slowed. The honest half is the disclosure, and it has two places to live. The build report names the
checks that did not run — `security`, `secrets`, `code-integrity` — as a standing line on both the built
and the blocked path, not only when something went wrong. `docs/08-builder.md` states the consequence in
plain words: **the product does no secret scanning and no script risk analysis of a generated skill**, and
the only security gate is `AgentSpecSecurityScanner`'s prompt-injection scan. A reader must be able to
learn that limit from the documentation without reading this design file.
Because the skipped checks are simply not requested, the evaluator never marks them and the gate emits the
disclosure itself; that is deliberate. The runner's own skip vocabulary
(`Tier1Runner.SKIP_REASON = "skipped with --skip"`, recorded on a `skipped` metadata result) describes an
operator's deliberate omission, and stamping "the product chose not to scan for secrets" as
`skipped with --skip` would misreport a product limit as a user choice.
On the `--full-inspection` path the rule earns its keep: a result whose `getStatus()` is `incomplete`
(its `getIncompleteScans()` is non-empty — and `markScanIncomplete` has already set `passed = false`) is
**never** counted as a pass and **never** blocks; the scanner name is reported as "did not run". Treating
incomplete as failure would let the machine's tool inventory decide whether a build succeeds. The
evaluator's own Semgrep branch — skipped with a message unless `SKILLEVALUATOR_REQUIRE_SEMGREP`
(`CodeRiskValidator.java:441-447`) — is the precedent for that shape of honesty, and the same escalation
is available to a later change that decides a tool *must* be present.
**Rejected: install the scanners as part of the build, or enable them by default.** Their install hints
name third-party release URLs (`spi/SkillspectorSecurityScanner.java:96` holds the SkillSpector wheel URL;
`GitleaksSecretsScanner` carries the gitleaks equivalent), so an enabled path is the moment the product
starts telling a user to fetch and run a third-party binary. That is a supply-chain decision for the human
and stays parked with it; the opt-in flag is the only place it can be reached, and it is documented as
such.

### D8: materialise to a temp folder named after the skill

`Tier1Runner` takes a `Path`, so the gate writes `skill.toSkillMd()` to `<tmp>/<skillName>/SKILL.md`,
runs, and deletes the directory. The folder name must be the skill name because `SchemaValidator`
reports a `name_consistency` HIGH finding when frontmatter `name` and folder name disagree; a random
temp directory would fail every build. The directory is created under the system temp root, is never
inside the publish target, and is removed in a `finally`.
**Rejected: point the gate at the published folder.** That would require publishing before gating.
**Rejected: inspect `<tmp>` root directly.** `findAllSkills` would treat the temp dir as a folder of
skills and prefix findings per skill, which is not the shape we want reported.

## Risks / Trade-offs

- **Quality 70 may be unreachable for a thin skill.** The floor is `QUALITY_DEFAULT_MIN_SCORE = 70` and
  the score is deductions over four dimensions; a short, correct skill could sit below it and burn both
  redrafts before being refused. Mitigation: the report shows the score and the deductions, so the first
  person to hit this can see whether the floor or the skill is wrong; the floor is a `minScore(int)`
  builder call and is deliberately *not* exposed as a flag until someone has evidence.
- **A redraft is a regeneration, not an edit.** A near-miss draft is thrown away and re-drawn from the
  same prompt plus findings, which can regress something that was already good. Accepted: it is the
  simplest seam that needs no new prompt text, `LlmJson`'s retry path already tolerates a fresh call, and
  the bound caps the damage at two calls.
- **Two gates over one artifact, again.** The dormant change's objection — "two gates with different
  severities, vocabularies and failure modes over one artifact" — is partly answered by D2's fixed order
  and D3's disjoint lineup, but not wholly: a build can now be refused for authoring reasons at `inspect`
  and for injection markers at `security`, and the two use different finding vocabularies. Accepted
  because the owner ruled this gate in, and because the vocabularies never mix in one report section.
- **SnakeYAML version resolution.** The evaluator pins 2.3 (`pom.xml:19`); the root POM imports the
  Spring Boot BOM, which also manages SnakeYAML, so the builder may resolve a different one transitively.
  Task 1.3 checks `dependency:tree` and pins the evaluator's version at the root if the BOM wins, rather
  than discovering it as a YAML parse failure in a live build.
- **Cost is real but bounded and reported.** Worst case is two extra model calls per skill build; the
  report line states how many drafts it took, so a loop that spins is visible rather than silent.
- **`make lint` applies to the new builder code** and the evaluator's own sources are already clean, so
  the new seam inherits the Spring Javaformat and header rules with no exemption. The new class carries
  the project author header, per team memory.
- **The evaluator keeps its own test suite as the source of truth.** Nothing here changes a validator, so
  the module's tests stay green unchanged; the builder's tests must not restate the evaluator's rules,
  only that its verdict is honoured — otherwise a rule change would break two suites.
