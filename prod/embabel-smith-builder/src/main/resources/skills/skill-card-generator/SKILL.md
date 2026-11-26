---
name: "skill-card-generator"
description: "Use only to generate or update a governance skill card for a specified existing agent skill directory. Do not use for explaining, listing, comparing, or discussing skill capabilities."
license: CC-BY-4.0 AND Apache-2.0
compatibility: "Any agent that can run JavaScript scripts and write files"
permissions:
  file_read:
    - "target_skill_directory"
    - "references/"
    - "scripts/"
  file_write:
    - "target_skill_directory"
  shell:
    allowed_scripts:
      - "scripts/discover_assets.js"
      - "scripts/render_card.js"
      - "scripts/validate_submission.js"
---

# Generate Skill Card

**Skill directory to analyze**: $ARGUMENTS

## Purpose

Create a draft NVIDIA governance skill card for an existing agent skill. The skill gathers source signals, guides the agent to build a grounded JSON context, renders a deterministic markdown card, and checks that human-review markers were removed before submission.

Use this when:
- A skill directory already exists and needs a new governance card.
- A changed skill needs its existing card refreshed.
- A skill owner is preparing legal/safety review material.

Do NOT use for:
- Explaining, listing, comparing, or discussing skills or skill capabilities.
- Creating or rewriting the source skill itself.
- Generating cards for non-skill assets such as models, datasets, containers, or full systems.
- Signing, publishing, or approving a skill card.
- Replacing required human legal, safety, or owner review.

## Prerequisites

- JavaScript scripts are executable in this skill. On the Embabel builder that means `smith.builder.skill-scripts.enabled=true`, which installs the in-JVM GraalJS engine; without it the scripts are listed as text and never run.
- The agent can list the target directory's files, stage them for a script call, and write files itself. The scripts do no directory walking and run no commands: a script only sees files the agent staged for that call.
- The target path is a skill directory containing `SKILL.md` or `skill.md`.
- The agent can hold the context JSON and the rendered card as text (they pass through stdin and stdout, not through a temp file).
- Runtime permissions allow reads from `target_skill_directory` plus this skill's `references/` and `scripts/`, writes only to the target skill directory, and script execution only for the three scripts listed below.

## Instructions

1. First, read this `SKILL.md` completely before running any script.
2. Resolve the target skill directory from `$ARGUMENTS`; if omitted, use the current working directory.
3. Stay within the declared permission scope. Do not read `.env`, credential files, hidden auth folders, or unrelated repo files; do not write outside the target skill directory or `/tmp/`.
4. List the target's files with your own file tools, then run `scripts/discover_assets.js` with those paths staged as `inputFiles` and a manifest of them on stdin. The script reads the staged files itself, so do not read them first. Use the structured signal summary it prints; if output is truncated, read only targeted files or small excerpts.
5. Build the context JSON from the structured signal summary first, then from extracted file contents only when needed. Pass it to the renderer on stdin; it does not need to be a file.
6. Populate `credential_requirements` with only two fields: `requires_api_key_or_credential` and `credential_types`. Ground the classification in SKILL.md prose documentation — not script inspection alone. For `credential_types`, use the controlled vocabulary in `references/style-guide.md`. Never include credential values, assignments, or raw environment variable names.
7. Follow `references/style-guide.md` for every context field. Use `HUMAN-REQUIRED` only when no source supports a truthful value.
8. Render the card with `scripts/render_card.js`, passing the context on stdin, and fix any schema errors before proceeding. It prints the card and writes it as an artifact; copy the artifact into the target skill directory with your own file tools rather than retyping the card.
9. Review the card manually, remove resolved VERIFY and SELECT markers, then run `scripts/validate_submission.js` with the reviewed card piped back in on stdin.
10. Before finishing, confirm the rendered card has no unrendered `{{ ... }}` or `{% ... %}` template fragments.

## Available Scripts

| Script | Tool | Purpose | Input |
| --- | --- | --- | --- |
| `scripts/discover_assets.js` | `skill-card-generator_discover_assets` | Extracts skill files, repo signals, style guide, and template into one discovery report. | `stdin`: the manifest JSON (below); `inputFiles`: every target and repo file it should read |
| `scripts/render_card.js` | `skill-card-generator_render_card` | Validates the context and renders the card deterministically. | `stdin`: the context JSON; `args`: `["--out", "<name>.md"]` |
| `scripts/validate_submission.js` | `skill-card-generator_validate_submission` | Fails if the rendered card still contains VERIFY or SELECT review markers. | `stdin`: the reviewed card text |

Each script is published as its own tool, named `<skill-name>_<script-name>`, and takes `args`, `stdin` and `inputFiles`. A script sees no filesystem of its own: `readFile` resolves only files staged for that call plus this skill's own folder, and `writeFile` lands in the run's artifact directory, never in the target.

The discovery manifest names what the script should read; contents are never in it.

```text
{
  "target": "/abs/path/to/target-skill",
  "repo_root": "/abs/path/to/repo",
  "git": {"describe": "...", "last_commit_sha": "...", "last_commit_date": "...", "remote_url": "..."},
  "files": [{"path": "SKILL.md", "scope": "skill"}, {"path": "README.md", "scope": "repo"}]
}
```

`path` is relative to `target` for scope `skill` and to `repo_root` for scope `repo`. `git` is what your own shell found, if anything; omit any key you did not resolve.

## Examples

Discover signals for a target skill — stage its files, then pass the manifest on stdin:

```text
skill-card-generator_discover_assets(
  inputFiles=["/path/to/target-skill/SKILL.md",
              "/path/to/target-skill/scripts/run.js",
              "/path/to/repo/README.md",
              "/path/to/repo/CHANGELOG.md"],
  stdin="{\"target\":\"/path/to/target-skill\",\"repo_root\":\"/path/to/repo\",\"git\":{\"describe\":\"v1.4.0\"},\"files\":[{\"path\":\"SKILL.md\",\"scope\":\"skill\"},{\"path\":\"scripts/run.js\",\"scope\":\"skill\"},{\"path\":\"README.md\",\"scope\":\"repo\"},{\"path\":\"CHANGELOG.md\",\"scope\":\"repo\"}]}"
)
```

Render a card from the completed context:

```text
skill-card-generator_render_card(
  args=["--out", "target-skill-card.md"],
  stdin="{\"skill_name\":\"target-skill\", ... the full context ... }"
)
```

It prints the card and writes it as an artifact. Copy that artifact to
`/path/to/target-skill/target-skill-card.md`, then read it back to review it.

Validate the reviewed card before submission:

```text
skill-card-generator_validate_submission(stdin="<the reviewed card text>")
```

## Limitations

- The generated card is a draft and must be reviewed by a human owner.
- Discovery is limited to the files staged for the call, plus whatever git and repository-root facts the agent resolved itself; no script lists a directory or runs a command.
- The renderer validates required context shape, not the legal or safety correctness of field values.
- Canned limitation and risk catalogs are starting points; remove entries that do not apply.

## Troubleshooting

| Error | Cause | Solution |
| --- | --- | --- |
| `no script tool offered` | The script engine is not installed in the host — on the builder, `smith.builder.skill-scripts.enabled` is off, so the scripts are listed as text only. | Turn the engine on, or do the discovery reads yourself and fill the context by hand. |
| `readFile: '<name>' is neither staged for this run ... nor inside the skill's own folder` | A file the manifest names was not staged as an `inputFile` of that call. | Re-run with the full file list staged; a script sees nothing but what it was handed. |
| `directory not found` | The target path is wrong or not mounted in the workspace. | Re-run discovery with the absolute path to the skill directory. |
| `Context validation failed` | Required fields are missing or typed incorrectly. | Fix the context JSON using `references/style-guide.md`. |
| Unresolved marker failure | VERIFY or SELECT markers remain after review. | Confirm each marked field, prune catalog entries, then re-run `validate_submission.js`. |

## Files in this skill

- `SKILL.md` - this file (orchestration)
- `references/style-guide.md` - per-context-field guidance
- `references/skill-card.md.j2` - the card layout, in its readable form; `scripts/render_card.js` is the executable form of the same layout, and the two change together
- `Skill Card Generator License` - license text for this skill package, at the top level of this folder
- `references/catalog/limitations.json` - canned technical-limitations catalog
- `references/catalog/risks.json` - canned risk-management catalog
- `scripts/discover_assets.js` - discovery and signal extraction
- `scripts/render_card.js` - context validation and card rendering
- `scripts/validate_submission.js` - pre-submission marker validator
