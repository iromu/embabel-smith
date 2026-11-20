# Detection policies

A detection policy is one JavaScript skill script. It is the surface design **D3** mutates: the
product's `Detector` is already a pure, model-free function, so a replacement for it can be scored
against the frozen labelled corpus for zero model calls. This folder holds the two seeds the inner
loop starts from — a hand-written script and a prompt the loop mutates — and this file states the
contract both must satisfy. The mutable/fixed boundary is not restated here; it is
[`../evidence/MUTABILITY.md`](../evidence/MUTABILITY.md), and a policy that reaches past it is
recorded as a rejected generation rather than scored.

Every script here runs **only** inside POC 26's `GraalJsSkillScriptExecutionEngine`, on the actor
side. It never runs inside the referee process (design **D9**).

## The script contract

Input is on `stdin`, one JSON object:

```json
{"cases":[{"runId":"t-001","success":false,"error":"…","steps":[{"status":"failed","durationMs":1902}],
           "pack":{"contentHash":"sha256:…"},"spec_context":{"sidecarContentHash":"sha256:…",
           "baselinePassRate":1.0,"reevalPassRate":null}}]}
```

`cases[].runId`/`success`/`error`/`steps`/`pack` are the trace fields the runtime records.
`spec_context` carries the pack facts `Detector` takes as *arguments* rather than as trace fields —
the sidecar's content hash and baseline pass-rate, and the re-evaluated pass-rate where one was
recorded — which is the documented simplification the corpus makes so that a per-line label is well
defined (`../corpus/traces/README.md`).

**The label never appears in the input.** `label` and `label_reason` are the answer the policy is
scored against; a policy that could read them would score 1.0/1.0 for free and the whole inner loop
would be measuring nothing. `PolicyInput` renders the evidence and strips the answer, and
`PolicyInputTest` pins it.

Output is one JSON object per case, on stdout, in any order:

```json
{"runId":"t-001","defect":true,"reasons":["failed_step"]}
```

`reasons` is the set of triggers that fired, from `failed_step`, `content_drift`,
`below_baseline`. A script that exits non-zero, prints something unparseable, or omits a case is a
**failed generation**, not a policy that answered "no" — an unanswered case would otherwise be
silently scored as a correct rejection.

## The three triggers

The hand-written policy is the human baseline the generated ones are compared to, and it
reproduces `prod/embabel-smith-studio/src/main/java/com/embabel/smith/studio/improve/Detector.java`
exactly: a failed run that is neither policy block (matched by **message prefix**, not keyword), a
pack whose content hash no longer matches the sidecar's, and a re-eval below the recorded baseline.

## The prompt grammar

`seed-prompt.txt` is the frozen seed the loop mutates. It is a line-oriented description of the
rules; `PolicyPrompt` parses it and `PolicyCompiler` renders it to JavaScript. `#` starts a comment
line.

| Line | Meaning |
| --- | --- |
| `POLICY <name>` | the variant name, and the skill folder the script is written into |
| `TRIGGER <name> WHEN <condition> [UNLESS <condition>]…` | one trigger: a reason the policy records when the condition holds and none of the exclusions do |

| Condition | Fires when |
| --- | --- |
| `failed` | the run did not achieve its goal |
| `error_prefix "<text>"` | the recorded error starts with the text — the product's rule |
| `error_contains "<text>"` | the recorded error mentions the text somewhere — a keyword, and the caricature a mutation tries |
| `hash_differs` | the pack's content hash differs from the sidecar's |
| `reeval_below_baseline` | a recorded re-eval pass-rate is below the sidecar's baseline |
| `slow_step` | any step exceeded 60 000 ms — latency, which the corpus labels as not a defect |

## The mutation operators

`PolicyGenerator` draws operators with a seeded `java.util.Random`, so a seed reproduces its
generation exactly. Each generation applies **one** operator to the frozen seed prompt, which is
what makes a generation attributable; a generation that rewrote everything would attribute to
nothing.

| Operator | What it does | What it is there to show |
| --- | --- | --- |
| `SEED` | emits the seed unchanged | the baseline the others are measured against |
| `KEYWORD_INSTEAD_OF_PREFIX` | swaps a prefix exclusion for the keyword `trust tier` | the corpus decoys: a keyword policy loses the two defects whose messages mention the tier |
| `DROP_EXCLUSION` | removes the last exclusion | buying recall with precision — a policy block becomes a defect |
| `DROP_TRIGGER` | removes the last trigger | the other direction: a whole trigger's worth of recall gone |
| `ADD_LATENCY_TRIGGER` | adds `slow_step` | a trigger the labels earn nothing from |
| `MALFORMED_EMIT` | truncates the emitted source mid-function | a generator that emits broken code; the loop records it and carries on |
| `HOST_CLASS_PROBE` | prepends a `Java.type` lookup | a generation reaching past the sandbox — rejected, naming `script-engine-limits` |
| `CORPUS_READ_PROBE` | prepends `readFile('heldout.jsonl')` | a generation reaching for its own judge — rejected, naming `trace-corpus` |

The last two are the fixed-artifact cases in the spec: the script is run, the engine's hardening
denies it, and the denial — not the operator's label — is what the run records as the reason. No
score is recorded for that generation.
