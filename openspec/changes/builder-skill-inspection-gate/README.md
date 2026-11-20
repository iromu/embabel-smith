# builder-skill-inspection-gate

Give the skill target a second gate: run the Tier 1 skill evaluator (`prod/embabel-smith-skillevaluator`)
over a drafted `SKILL.md` before the expensive eval arms, and when it fails, feed the blocking findings
back to one bounded redraft. The evaluator already encodes the skill writing guidelines — body heading,
recommended sections, 500-line ceiling, name/folder consistency, and a 4-dimension quality score that
must reach 70 — and today nothing in the product reads any of them, so a skill publishes on lift alone.

This reverses `skillevaluator-dormant`: the owner ruled the evaluator "has to run also when creating
skills, to iterate over them until the inspection passes". That change's root `NOTICE` attribution and
`README.md` entry tasks are carried over here unchanged; its dormancy decision is dropped, its verified
provenance facts are kept.

The lineup is every check the library can run without an external tool — `schema`, `version`, `quality`,
`unicode`, `lint` and `license` block, `pii` runs and is reported only as an advisory. The three checks
that shell out to SkillSpector, Gitleaks, Bandit or Semgrep are not requested by default, and the report
and `docs/08-builder.md` say in plain words that they did not run — a default skill build does no secret
scanning and no script risk analysis. `--full-inspection` is the documented, off-by-default opt-in for an
operator who has installed those tools.
