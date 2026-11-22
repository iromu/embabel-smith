# builder-skill-gate-integrity

Make the skill target's lift gate trustworthy: security-scan the evals.json the publisher writes beside the SKILL.md, block a drafted suite that contains a scenario with no expectations instead of scoring it 0/0, and expose the gate's minimum lift as a --min-lift CLI option instead of leaving it reachable only to a programmatic BuildOptions caller. The studio deliberately gets no lift knob — its build pins the agent-spec target, so the lift gate is unreachable there and the reason is recorded in docs/08-builder.md.
