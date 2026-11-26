---
title: "Trust tiers and guardrails (POC 10) + install-time scanning (POC 21)"
type: "feature"
status: "active"
language: "default"
source_paths:
  - "poc/poc-10-trust-tiers/src/main/java/com/embabel/smith/trust/TrustTier.java"
  - "poc/poc-10-trust-tiers/src/main/java/com/embabel/smith/trust/TrustTierPolicy.java"
  - "poc/poc-10-trust-tiers/src/main/java/com/embabel/smith/trust/InputSanitizerGuardRail.java"
  - "poc/poc-10-trust-tiers/src/main/java/com/embabel/smith/trust/TrustRunner.java"
  - "poc/poc-21-agent-spec-security/src/main/java/com/embabel/smith/security/SpecSecurityScanner.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/security/AgentSpecSecurityScanner.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/GraalJsSkillScriptExecutionEngine.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillScriptHost.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/PublishedSkillHost.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/cli/SkillRunCommand.java"
  - "prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/SkillScriptEngineConfiguration.java"
updated_at: "2026-11-25"
---

# Trust tiers and guardrails (POC 10)

POC 10 is the **security** half (doc 03 §2.1, doc 02 caveat #3 — the 26% vuln rate). It has two
parts: a **per-tier tool-group allowlist** that gates high-privilege specs (e.g. `shell`) behind a
trust tier, and a **globally-registered user-input guardrail** that blocks prompt injection before
the LLM runs.

## The tiers (`TrustTier`)

Each tier allows a set of tool-group roles; a spec may only run under a tier whose allowlist
covers every tool group the spec declares.

| Tier | Allowed tool groups |
| --- | --- |
| `T1_COMMUNITY` | none (instructions only, full isolation) |
| `T2_VERIFIED` | `search`, `web` (read-only) |
| `T3_PARTNER` | `search`, `web`, `file`, `shell` (write + MCP) |
| `T4_PLATFORM` | `*` (everything, including host execution) |

**`TrustTierPolicy`** enforces this: `declaredToolGroups(spec)` collects the distinct tool-group
roles a spec's actions declare, and `check(spec, tier)` returns whether the spec is allowed and
which groups are not. So the `shell-runner` spec (which declares `shell`) is **refused under
T1** and **allowed under T3**, while a plain spec (no tool groups) is allowed under T1. This is
the offline, pure half — the tier checks always run.

## The input guardrail (`InputSanitizerGuardRail`)

A `UserInputGuardRail` registered globally via `embabel.agent.guardrails.user-input` in
`application.yml`. It normalizes the input (lowercase, punctuation/whitespace stripped via
`normalize()`) and matches a set of known injection phrases (`ignorepreviousinstructions`,
`disregardtheabove`, `revealyoursystemprompt`, …) with `ValidationSeverity.CRITICAL`, which stops
the LLM operation before it runs. Because matching happens on the normalized string, it catches
obfuscated variants — extra spacing, punctuation, mixed case — that a plain substring match on the
literal marker would miss. `TrustRunner` distinguishes a normal goal achievement from a run
**blocked by a guardrail** (walking the cause chain for a `GuardRailViolation`).

## What it proves

Per-tier allowlists and a global guardrail work: a clean run is allowed, and a prompt-injection is
blocked with a CRITICAL `GuardRailViolationException`. The caveat (see [[what-the-pocs-prove]]):
the guardrail is a **normalized pattern matcher**, not a semantic detector — it now catches
obfuscated variants (spacing/punctuation/case and other known rephrasings) a plain substring match
missed, but a *novel* rephrasing outside the pattern set can still slip through. A blocking
(CRITICAL) guardrail triggers platform **retries** — the injection run retried ~5× over ~4 min
before surfacing. Seven offline tests pin the matcher (classic marker, obfuscated spacing,
punctuation, other phrases, clean pass). The live run is in `docs/04-poc-run-results.md` §5. See
[[runtime-adapter]] for the `shell` group this policy gates.

## What the tier does not bound

In `prod/` the tier model is two-valued — `standard` (default) or `high`, chosen by
`smith run --trust-tier` — and it bounds *names*, not capabilities. `core/TrustTierPolicy` collects the
tool-group strings a pack's steps declare and keeps the ones the granted tier does not allow
(`tier.allows(group)`); it resolves a name against nothing. So an unbacked name — a typo included — has
to be absent from the allowlist to be refused, and the permissive tier allows `*`, which means a pack can
land at the maximally privileged tier while reaching no extra capability whatsoever.

Nothing in Embabel 1.5.4 maps a declared group to a service by itself: `@LlmTool` carries no group member,
the platform's `@ToolGroup` annotation is referenced by no other class in the shipped jars, and the
resolver's registry is the `ToolGroup` beans Spring can see. Those beans are repo work, and the tree now has
seven of them: POC 7's `shell` group, built by reflection in `ShellToolGroupFactory`, and six in the builder —
`validate` since 2026-11-22 (`tools/ValidationToolGroup`) and, since 2026-11-23, `blueprint`, `library`,
`security`, `traces` and `process` — the five newer ones through `tools/ToolGroups.ofTools`, which is that
class's reflection extracted rather than repeated five times. So a
step's `toolGroups:` line resolves where a group is registered and resolves
to nothing where none is, and either way it remains exactly what the security scanner reads
([[spec-security-scanning]]). The builder's two drafting steps — `builder-spec-one-shot/steps/draftAgentSpec.yml`
and `builder-single-spec/steps/assembleAgentSpec.yml` — declare `validate`, and the validator is now
*offered* to them (`starting tool loop [validatePack]`), where the same run previously logged
`starting tool loop []` behind an unresolved-role warning; `builder-agentic-blueprint`'s two steps declare
`blueprint`, `library`, `validate` and `security`, and `builder-harness`'s three declare the same four
between them (`surveyDocumentRoots` `blueprint` and `library`, `planWhatToReadNext` `blueprint`,
`draftAgentSpecFromRoots` `blueprint`, `validate` and `security`), so the fourth flow added a deciding act
rather than a name the tier had not seen. It is *called* on the `--flow pack` path and not
called on the test-seam deploy, so `called=0` is a fact about that seam rather than about packs. What a call
bought turned on the tool's argument: until `99f9cc0` that was a directory no drafting step could name, so
the counts recorded then — 2, 3 and 116 calls across three inputs — belong to that argument, not to the
current one, which takes the spec the step is about to return (`docs/12-backlog.md`). A misspelled name
still resolves to nothing with
only that warning under `AgentSpecValidator`, which inspects no `toolGroups` at all; on the agentic deploy
the same typo is refused before any model call, against the resolver that deploy is about to use
(`eval/AgenticAgentSpecGenerator#refuse`). What the registrations moved is reach, not the tier: none of the
six names is in an allowlist, so every builder pack still reads `T4_PLATFORM` through T4's `*`, and the tier
test is untouched ([[smith-builder]]). Leaving them out was the choice, not an omission — a permissive entry
for a new role would lower the tier that pack needs on a reach argument the tier cannot make, and
`BuilderPackTierTest`'s control says the stronger version of the point: the roles a builder pack really
resolves to report the same tier as a name that resolves to nothing. The containment argument
therefore rests on the scorer-out guard rather than on the tier — and no longer on reach living wholly
outside the mutable packs.

## The part of a skill that runs

A published skill can now carry `scripts/*.js`, and a script is the one part of it that *executes*: the
reader's agent runs it, in the builder's own JVM through
`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/GraalJsSkillScriptExecutionEngine`
where that build has the engine switched on. Neither half of the POC 10 model bounds that on its own — the
tier bounds declared names rather than capabilities (above), and the install-time scan was a pattern matcher
over documents. The builder closed the second gap for scripts specifically:
`security/AgentSpecSecurityScanner.scanSkill` reads `scripts/*.js` as code, and a script reaching for the
host (the module loader, a host class), for the network, or for a path above the skill's own folder blocks
the build at the security stage rather than being discovered by the reader's runtime. Those checks are the
sandbox's own contract read backwards, which is also their limit: they name constructs, so a script that
stays inside the promised surface and computes something wrong is a quality problem no scanner sees, and the
enforcement that actually bites is the engine's — host access denied, files confined to the skill folder,
statement and time limits enforced. The engine sits behind `smith.builder.skill-scripts.enabled`, and the
property really does default it off: the bean is `@ConditionalOnProperty(name = ENABLED_PROPERTY,
havingValue = "true")` with no `matchIfMissing`
(`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/SkillScriptEngineConfiguration.java:40,47`).
Why it is a switch and not a default is that class's own argument (`:18-26`): Embabel offers a skill's
script as a tool only when an engine claiming that script's language is installed, and its default engine
claims nothing, so installing one *adds* tools to a build — a script that was inert text a run ago becomes
callable. That changes what a build can reach, and the repo's rule for that is somebody's explicit
decision rather than a side effect of a dependency landing on the classpath.

What the switch installs also has a caller now, which falsifies what this page used to claim — that
nothing in `prod/` handed the engine to a `Skills` at all. The docs put it more precisely: that stronger
absence "is half-filled as of `2b16c27`" (`docs/12-backlog.md` §Builder — skill scripts, "A published script executes in `prod/`, on one route"), and the change
`builder-skill-script-host` filled the reader's half of it too, one route deep
(`docs/08-builder.md:389-404`). Two callers of that seam exist in `prod/` main. The build-internal one is
`skill/SkillScriptHost.skillsFor`, which writes the skill *under build* out as an Agent Skill folder and
attaches the engine
(`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/SkillScriptHost.java:90-91`),
reached from `cli/BuildCommand.java:270`; `skill/EvalRunner.java:116-118` hands that reference to the
with-skill arm (`docs/08-builder.md:382-385`). The reader-side one is `skill/PublishedSkillHost`, which
points the same `withLocalSkills` at the directory `publish` wrote — the real folder, not a re-derived copy
of it — and installs the engine only when the launcher's bean provider offered one, at
`skill/PublishedSkillHost.java:111-112`; `cli/SkillRunCommand` (`smith skill-run --skill-dir <dir>
<request>`) is the route that attaches it to a model call. Both subcommands take the engine from the *one*
lookup at `SmithBuilderApplication.java:64` (`scriptEngine`, `:90`), so what
`SmithBuilderScriptEngineWiringTest` guards is the pair rather than the bean: with the property unset that
path constructs no engine and offers no script tool, with it set exactly one script tool appears — the name
it appears under, and the prefix a reference-carrying call puts in front of it, are
[[skill-output-target]]'s to keep. So the two eval arms are prompt substitution on an ordinary build and are not prompt substitution
on a switch-on one, and a published script does execute in `prod/` — on that route. What still does not
exist is a readership: `a2a-starter/SpecSourceSkills` publishes `io.a2a.spec.AgentSkill` card entries,
which describe a capability rather than load a folder, and a `smith run` is offered nothing a published
skill's folder holds (`docs/08-builder.md:405-412`). On an ordinary build, with the switch off, a published
script is still a file nothing executes — the half of the old sentence that holds.

One tier statement did move, and it is the scanner's, not the runtime's.
`security/AgentSpecSecurityScanner.scanSkill` reported `T1_COMMUNITY` for every skill, executable or not,
and now reports `T3_PARTNER` for one that ships JavaScript, with the scriptless skill the case that still
reads `T1_COMMUNITY` (`security/AgentSpecSecurityScanner.java:199,221`). That is a report about what a
folder contains rather than a gate on what runs: a trust tier is a set of tool-group role names, a script
declares no role, so neither host consults a tier on the way to executing a script, and the test that holds
that is a type check with the scan report as its control case. The allowlists are unchanged, so every
builder pack still lands at `T4_PLATFORM` through T4's `*`, and backed-ness is proved against a booted
registry rather than by a tier ([[smith-builder]], [[spec-security-scanning]]).

## The install-time complement (POC 21)

POC 21 ([[spec-security-scanning]]) is the **install-time** half of the same security hypothesis:
a static scanner vets a pack's declared prompts and tool groups *before* it is loaded, and maps
each pack to the minimum POC 10 trust tier. The scanner's "load at T3+" is exactly what POC 10
enforces at runtime. Both halves share the same honest limit — a normalized pattern matcher over
declared content, not a semantic detector. See `docs/04-poc-run-results.md` §7.3.
