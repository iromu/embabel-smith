# skill-build-inspection Specification

## Purpose
What the skill target checks about a drafted skill before it pays for the benchmark, which of those
checks may block and send the draft back to the model, which run but are reported only as advisories,
which are skipped by default and disclosed by name, what happens when a skill still fails after the last
allowed redraft, and what the gate states plainly that it did not check.

## Requirements

### Requirement: The drafted skill is inspected before the eval arms
The skill build SHALL run the Tier 1 skill evaluator over the drafted skill before it drafts any eval
scenario or runs any benchmark arm, so a skill that fails authoring rules never pays for the benchmark.

#### Scenario: A draft fails inspection
- **WHEN** a generated skill fails an inspection check
- **THEN** no eval scenario is drafted and no benchmark arm runs for it

#### Scenario: A draft passes inspection
- **WHEN** a generated skill passes every blocking inspection check
- **THEN** the build proceeds to drafting eval scenarios exactly as it does today

### Requirement: The blocking lineup is the evaluator's binary-free checks
The inspection SHALL run the `schema`, `version`, `quality`, `unicode`, `lint` and `license` checks on
every skill build and treat a failing result from any of them as blocking. These are the checks the
evaluator performs without an external tool.

#### Scenario: A skill breaks an authoring rule
- **WHEN** a drafted skill exceeds the line ceiling, omits its top-level heading, mismatches its folder
  name, or scores below the quality floor
- **THEN** the inspection reports it as a blocking finding

#### Scenario: A check has nothing to examine
- **WHEN** a generated skill ships no `scripts/` or `tools/` directory and carries no LICENSE file
- **THEN** script lint records a pass and license a warning, so both appear in the report as having run
  rather than as skipped

#### Scenario: A change drops one of the six
- **WHEN** a change removes a check from the blocking lineup without naming a reason
- **THEN** it is refused: the lineup is the whole set the library can run offline, not a convenient subset

### Requirement: The pii check runs and is advisory
The inspection SHALL also run the `pii` check on every skill build, and SHALL report its findings as
advisories: they SHALL NOT block a build and SHALL NOT be sent to the model, whatever severity they
carry.

#### Scenario: A legitimate skill names a contact address
- **WHEN** a drafted skill contains text the pii check reads as personal information
- **THEN** the build is allowed and the report shows the finding labelled as a pii advisory

#### Scenario: A change lets a pii finding block
- **WHEN** a change routes a pii finding into the blocking set or the redraft prompt
- **THEN** it is refused: a regex over generated prose is not a publication gate

### Requirement: Checks needing an external tool are skipped and disclosed
The default inspection SHALL NOT request the `security`, `secrets` or `code-integrity` checks. Both the
built and the blocked build report SHALL name those three as checks that did not run, and the skill
target's documentation SHALL state plainly that a default skill build does no secret scanning and no
script risk analysis.

#### Scenario: A skill build finishes on the default path
- **WHEN** a skill is built without any inspection option
- **THEN** its report names the three checks that did not run, and the build is not penalised for their
  absence

#### Scenario: A skill build is blocked at inspection
- **WHEN** a build stops at the `inspect` stage
- **THEN** its report carries the same statement about what did not run

#### Scenario: The build machine has none of the tools
- **WHEN** a skill build runs with none of gitleaks, skillspector, bandit or semgrep installed
- **THEN** the inspection completes and its verdict is unchanged by the missing tools

#### Scenario: A change enables one by default
- **WHEN** a change requests a shelling-out check on the default path
- **THEN** it is refused: the verdict would depend on the build machine's tool inventory, and an absent
  tool marks its own scan as failed

### Requirement: The externally-backed checks are reachable only by explicit opt-in
An operator SHALL be able to request the three externally-backed checks through a documented option that
is off by default, and that option SHALL be the only path on which a third-party install hint can reach a
user.

#### Scenario: An operator who installed the tools asks for them
- **WHEN** the opt-in option is passed
- **THEN** the three checks are requested, and a check whose tool is then missing is reported as an
  incomplete scan and named, not counted as a pass

#### Scenario: A default build suggests installing software
- **WHEN** a default skill build would print a hint naming a third-party release URL
- **THEN** it is refused: fetching and running that artifact is the human's decision

### Requirement: A skill is inspected in the shape the evaluator accepts
The build SHALL inspect the skill as a folder named after the skill holding its rendered SKILL.md and
nothing else, and SHALL remove that folder afterwards whatever the outcome.

#### Scenario: An inspection runs
- **WHEN** the gate inspects a drafted skill
- **THEN** the folder it writes is named with the skill's own name, so the name-consistency rule judges
  the skill and not the scratch directory

#### Scenario: An inspection throws
- **WHEN** an inspection run fails part-way through
- **THEN** the scratch folder is removed and nothing is left inside the publish target

#### Scenario: Only the skill document is written
- **WHEN** the gate prepares the folder
- **THEN** it writes the SKILL.md alone and never the eval suite, whose scanning belongs to the security
  stage

### Requirement: A failing inspection is fed back to a bounded redraft
The build SHALL hand the blocking findings back to the model and regenerate the skill, at most
`max-inspect` times, counted by its own option and not by the agent-spec target's refinement option.

#### Scenario: A redraft is asked for
- **WHEN** a draft has blocking findings and redraft rounds remain
- **THEN** the next generation request carries those findings and their suggestions

#### Scenario: The bound is reached
- **WHEN** a skill has been drafted `max-inspect` plus one times and still fails
- **THEN** no further redraft is requested

#### Scenario: A spec build tunes its refinement knob
- **WHEN** someone changes the agent-spec refinement bound
- **THEN** the number of skill drafts is unchanged

### Requirement: Advisory findings are reported, not fed back
A finding that does not block SHALL appear in the build report labelled with the check that produced it,
and SHALL NOT be sent to the model as a reason to redraft.

#### Scenario: A convention nudge fires
- **WHEN** a check reports a recommended section as missing without failing the result
- **THEN** the redraft prompt does not contain it and the build report does, naming that check

#### Scenario: A gate failure has no finding of its own
- **WHEN** the quality score falls short and the check records it as a gate failure rather than a finding
- **THEN** that shortfall is fed back to the model as a blocking reason

### Requirement: An incomplete external scan is neither a pass nor a block
Where a check reports that an external scanner did not produce trustworthy evidence, the build SHALL NOT
count that check as passed and SHALL NOT block on it, and SHALL name the scanner in the report.

#### Scenario: A scanner is unavailable on an opted-in build
- **WHEN** a check reports an incomplete scan because its tool is missing
- **THEN** the report names that tool as not having run and the skill's verdict rests on the checks that
  did run

#### Scenario: An incomplete result is silently treated as clean
- **WHEN** a change maps an incomplete status onto a pass
- **THEN** it is refused, because the report would claim a check that never ran

### Requirement: A skill that still fails is refused, not published
When the last allowed round still has blocking findings, the build SHALL stop at the `inspect` stage,
report the findings and the number of drafts, and publish nothing.

#### Scenario: The loop ends in failure
- **WHEN** the final inspected draft still has blocking findings
- **THEN** no publisher is opened and the report names stage `inspect` with the findings listed in full

#### Scenario: Someone wants a warning instead of a block
- **WHEN** a change proposes publishing a skill that failed inspection with a flag on the sidecar
- **THEN** it is refused: the pipeline publishes only skills whose gates all passed

### Requirement: The evaluator is reached through one seam and a legal dependency
The builder SHALL depend on the evaluator as an ordinary managed Maven dependency, SHALL confine its
imports to one class, and SHALL NOT introduce a dependency the build's ban on POC artifacts forbids.

#### Scenario: The dependency is added
- **WHEN** the builder declares the evaluator
- **THEN** the POC dependency ban still passes and the evaluator resolves from the reactor, not a stale
  local artifact

#### Scenario: A second class reaches into the evaluator
- **WHEN** another builder class imports the evaluator's package
- **THEN** the single-seam rule is broken and the change is refused

### Requirement: Inspection costs no model call unless it asks for a redraft
The inspection itself SHALL make no model call, and each requested redraft SHALL cost exactly one.

#### Scenario: A clean skill
- **WHEN** a drafted skill passes inspection on the first round
- **THEN** the build makes no model call for inspection beyond the draft it already made

#### Scenario: A spinning loop is visible
- **WHEN** a build uses its full redraft allowance
- **THEN** the report states how many drafts it took, so the extra calls are not silent
