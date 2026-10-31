---
title: "Validation issue codes"
type: "reference"
status: "active"
language: "default"
source_paths:
  - "smith-common/src/main/java/com/embabel/smith/common/validation/SkillPackValidator.java"
  - "smith-common/src/main/java/com/embabel/smith/common/validation/ValidationIssue.java"
  - "smith-common/src/test/java/com/embabel/smith/common/validation/SkillPackValidatorTest.java"
updated_at: "2026-11-01"
---

# Validation issue codes

`SkillPackValidator` (in `smith-common`) is the format's structural gate. Every POC that touches a
skill-pack runs it before ingesting, building, or writing one. See [[skill-pack-format]] for what
the checks mean and [[validate-before-write]] for why the builder depends on it.

## The four checks, in order

1. **Layout** — `manifest.yml`, `goal.yml`, a non-empty `steps/`, and a `types/` directory are all
   present.
2. **Exported goal** — exactly one goal, and it is `export: true`.
3. **Prompt references resolve** — every `{{var}}` / `{{var.prop}}` in an action prompt is bound by
   one of that action's declared input types, and `prop` exists on the declared type when it is a
   dynamic type.
4. **Type references resolve** — every input/output type name used by the goal or any step is either
   framework-provided or declared in `types/`.

A `ValidationIssue` is a `(code, message, location)` record — a stable machine-readable `code`, a
human-readable `message`, and the file/step the issue was found in (or `null` if pack-wide).

## Issue codes

| Code | Meaning |
| --- | --- |
| `LAYOUT_MISSING_MANIFEST` | `manifest.yml` is missing |
| `LAYOUT_MISSING_GOAL` | `goal.yml` is missing |
| `LAYOUT_MISSING_STEPS` | `steps/` directory is missing |
| `LAYOUT_EMPTY_STEPS` | `steps/` contains no action-step files |
| `LAYOUT_MISSING_TYPES` | `types/` directory is missing |
| `GOAL_MISSING` | No goal defined; a skill-pack must expose a goal |
| `GOAL_NOT_EXPORTED` | `goal.yml` does not declare `export: true` — no callable skill exposed |
| `DANGLING_TYPE_REF` | An input/output type is neither framework-provided nor declared in `types/` |
| `PROMPT_MISSING` | An action has no prompt |
| `PROMPT_UNDECLARED_INPUT` | A prompt reference is not bound by any declared input type |
| `PROMPT_UNKNOWN_PROPERTY` | A prompt references a property not declared on the type |
| `PARSE_ERROR` | The pack failed to parse (thrown during `read`) |

## Framework types

These built-in Embabel types need not be declared in `types/` (from
`SkillPackValidator.defaultFrameworkTypes()`): `UserInput`, `SystemInput`, `BackgroundMessage`,
`AssistantMessage` — each accepted by short name or fully-qualified name
(e.g. `com.embabel.agent.domain.io.UserInput`).

## How to read a failure

When a POC throws "Skill-pack failed validation: …", the list of `ValidationIssue`s names the
`code` and the offending value/file. Fix the pack (data) first; only change the format contract if
the pack is genuinely well-formed. The behaviour is pinned by
`smith-common/src/test/java/com/embabel/smith/common/validation/SkillPackValidatorTest.java`
(accepts a well-formed pack, rejects a dangling type reference).
