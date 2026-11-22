# DeerFlow — Business Rules

> The rules that govern each process, with the exact figures the product uses.
> Where a figure is a **default**, it can be changed by whoever runs the
> installation. Implementation detail is excluded; the numbers are kept because
> they are business commitments.
>
> **Evidence convention:** **Confirmed** · **Assumption** · **Unresolved**.

---

## 1. Who may do what

| # | Rule | Figure |
|---|---|---|
| 1.1 | The first person to open a fresh installation becomes its administrator; nobody else can claim that role afterwards. | — | **Confirmed** |
| 1.2 | Self-registration always creates a **member**, never an administrator. | — | **Confirmed** |
| 1.3 | The last remaining administrator cannot demote or disable themselves. | — | **Confirmed** |
| 1.4 | A denied permission always beats any grant, and an unrecognised role is refused rather than trusted. | — | **Confirmed** |
| 1.5 | There are thirteen permissions in total: conversation read/write/delete, start work/read history/cancel work, project read/write/delete, memory read/write, custom-assistant read/write. Cancelling work is a permission in its own right. | **13** | **Confirmed** |
| 1.6 | A token used by a script may hold at most **nine** of the thirteen — memory and assistant-management are unreachable with one, whatever was requested. | **9 of 13** | **Confirmed** |
| 1.7 | A script token runs as its owner, on conversation-and-work routes only; everything else is refused by default, and it never carries administrator power. | — | **Confirmed** |
| 1.8 | Another person's conversation is reported as **not found**, not as forbidden. Ownership claimed by a caller is stripped and restamped from the signed-in identity. | — | **Confirmed** |
| 1.9 | Signing in is remembered for **7 days** by default. | **7 d (1–30)** | **Confirmed** |
| 1.10 | Repeated failed sign-ins are blocked per address; the lock is timed from the **first** failure, not the last. | **5 attempts / 300 s** | **Confirmed** |
| 1.11 | Password recovery is an operator action on the server: a random password is written to a file only the operator can read, and every existing session of that account is invalidated. | — | **Confirmed** |
| 1.12 | Signing in over plain HTTP on a network address does not get a persistent "stay signed in" credential unless the operator explicitly accepts the weaker option. | — | **Confirmed** |
| 1.13 | Company-sign-in colleagues whose address already belongs to a local account are **refused**, never silently merged. | — | **Confirmed** |

---

## 2. Linking a messaging account

| # | Rule | Figure |
|---|---|---|
| 2.1 | Linking uses a one-time code that is effectively unguessable, expires, and works once. | **128-bit · 10 min · single-use** | **Confirmed** |
| 2.2 | A person may not have more than a handful of codes outstanding at once. | **5 per person per platform** | **Confirmed** |
| 2.3 | **The newest link wins.** If someone else links the same platform account, the earlier person loses it. | — | **Confirmed** |
| 2.4 | A message from an unlinked account is rejected **before** any conversation is created. This is the default whenever authentication is on. | — | **Confirmed** |
| 2.5 | Duplicate deliveries of the same message are recognised and ignored within a window. | **10 min** | **Confirmed** |
| 2.6 | Inbound work is queued and drained by a small fixed crew. | **1 000 queued · 5 workers** | **Confirmed** |
| 2.7 | Bot credentials supplied at run time are stored encrypted; if the encryption key is not configured, the capability is unavailable and says so. | — | **Confirmed** |
| 2.8 | Proactive notifications stop being retried after repeated failure. | **5 attempts** | **Confirmed** |

---

## 3. Finishing honestly: the completion condition

| # | Rule | Figure |
|---|---|---|
| 3.1 | **One** completion condition may be attached to a conversation at a time. It survives context summarisation. | **1** | **Confirmed** |
| 3.2 | After each finished turn a **separate, non-reasoning judge** decides whether the condition is met, using only what is visible. | — | **Confirmed** |
| 3.3 | A step succeeding is **not** evidence the outcome was reached. | — | **Confirmed** |
| 3.4 | If the assistant had to guess at missing information, the verdict is "needs the person's input", not "met". | — | **Confirmed** |
| 3.5 | A silent continuation is started **only** when the last answer is durably saved, the blocker is "not met yet", the conversation has not changed during judging, and the no-progress breaker has not fired. | — | **Confirmed** |
| 3.6 | Silent continuations are capped; so is repeating without progress. | **8 continuations · 2 no-progress rounds** | **Confirmed** |
| 3.7 | The person's own new input, or clearing the condition, always wins over a queued continuation. | — | **Confirmed** |
| 3.8 | A judge that returns an unusable verdict is given **one** more chance on the same evidence; both attempts count; a second failure stops the loop without re-running the work. | **1 retry** | **Confirmed** |
| 3.9 | No-progress is judged on **observed work**, not on wording: rephrasing the reply does not reset the count. | — | **Confirmed** |
| 3.10 | A condition cannot be set or cleared while a turn is in flight — including a turn owned by a different worker. | — | **Confirmed** |
| 3.11 | Someone without permission to start work is refused **before** anything is prepared, and keeps their draft. | — | **Confirmed** |
| 3.12 | When auto-continue stops, the person is always told **which** of the thirteen reasons applied. | **13 reasons** | **Confirmed** |
| 3.13 | Regenerating an earlier answer keeps the current condition and its **already-used** continuation budget. | — | **Confirmed** |

---

## 4. Effort, repetition and safety limits on a turn

| # | Rule | Figure |
|---|---|---|
| 4.1 | A turn may be given an effort allowance; when switched on together with usage reporting, a lead turn's allowance **includes** the work its helpers did, and silent continuations **share** the same allowance. A new request from the person starts a fresh allowance. | default **200 000** | **Confirmed** |
| 4.2 | Repetition is watched two ways: repeated sets of actions, and how often each individual action is used. A hard limit stops the **whole batch** of actions before any of them run. | — | **Confirmed** |
| 4.3 | A safety check on each action is scored; below the bar it is refused, and a check that itself breaks **refuses by default** rather than letting the action through. | threshold **0.5** · **5 s** per attempt · **10 s** overall · **2 attempts** | **Confirmed** |
| 4.4 | An action whose request is implausibly large is refused locally, without consulting the checker. | **> 4 000 characters** | **Confirmed** |
| 4.5 | Judgement about whether a result was useful is **off by default**; when on, it acts after two consecutive problem results. | **2** | **Confirmed** |
| 4.6 | Provider connection and timeout failures are retried within a small budget; other unexplained provider failures get a single retry. | **3 attempts total** | **Confirmed** |
| 4.7 | When a provider halts an answer for safety or length, tool use from that answer is suppressed; an empty filtered answer gets an explanation, and a visible partial answer without tool use is kept. | — | **Confirmed** |
| 4.8 | An empty final answer after successful tool use is retried **once**, and then reported as a visible error rather than a silent success. | **1** | **Confirmed** |
| 4.9 | Words are not evidence: page titles, quoted error text, and words like "timeout" or "no results" do **not** establish that something failed or that a tool is permanently unavailable. | — | **Confirmed** |
| 4.10 | A report that cites no evidence receipts is shown as **unverified**; citing receipts proves only that the cited items exist, not that the narrative around them is complete. | — | **Confirmed** |
| 4.11 | Masking of personal identifiers before the information reaches a model is **off by default**; when enabled it keeps no mapping back to the original, and the person's own view of their conversation is left intact. | default **off** | **Confirmed** |

---

## 5. Delegating to helpers

| # | Rule | Figure |
|---|---|---|
| 5.1 | Helpers work in a context of their own and cannot see the main conversation or each other. | — | **Confirmed** |
| 5.2 | A helper may not itself delegate further unless that is deliberately switched on. | — | **Confirmed** |
| 5.3 | At most three helpers run at once; at most six delegations in one turn of work (configurable, hard range 1–50). | **3 concurrent · 6 per turn** | **Confirmed** |
| 5.4 | A built-in helper gets a thirty-minute allowance; where no built-in allowance applies the fallback is fifteen minutes. A helper is also capped by a number of reasoning rounds. | **1 800 s · 900 s fallback** | **Confirmed** |
| 5.5 | A helper that fails is reported as **failed** — never dressed up as a success; an unknown status stays "in progress" rather than being guessed. | — | **Confirmed** |
| 5.6 | Delegated work can be given **acceptance criteria** — a test outcome, a file or content check — which decide its verdict; thin evidence is marked **unverified**. | **50 000-byte check cap · 5 s check timeout** | **Confirmed** |
| 5.7 | A cancelled helper keeps its place until it has actually finished tidying up, so its work cannot be double-run. | — | **Confirmed** |
| 5.8 | Each item in a durable batch re-checks that it still belongs to the same person before starting and again after taking up capacity; a disabled or reset account, or a changed role, stops the item. | — | **Confirmed** |

---

## 6. Acquiring and using capabilities

### Skills — a documented method the assistant may follow

| # | Rule | Figure |
|---|---|---|
| 6.1 | Installing a skill pack is an **administrator** action. | — | **Confirmed** |
| 6.2 | Turning a skill on or off is a **per-person** choice, and takes effect immediately across the whole service without a restart. | — | **Confirmed** |
| 6.3 | A skill's own declaration of which tools it wants is **compatibility information only** — it never grants or restricts what may be done. | — | **Confirmed** |
| 6.4 | The assistant sees only the **list of names** of available skills; it looks up a description before reading one, and can read at most a handful per lookup. | **5 results · 256-character query** | **Confirmed** |
| 6.5 | A person may force a particular skill for one turn. | — | **Confirmed** |
| 6.6 | "Skills used" is recorded only from a **successful read** or a forced activation — a catalogue entry, a mention in the answer, a failed read, or a claim from the caller does not count, and work done by helpers is outside this record. | **≤ 64 skills per turn · ≤ 100 000 characters each** | **Confirmed** |
| 6.7 | Exporting skills is an **administrator** action and never carries credentials out. | **4 096 entries · 64 MiB per file · 100 MiB total** | **Confirmed** |

### Skill safety screening — two independent gates, both before installation completes

| # | Rule | Figure |
|---|---|---|
| 6.8 | **Gate one** is a fast, offline, deterministic scan with fixed rules — hidden file paths, links pointing outside the pack, executable programs, private keys, cloud and API tokens, code that runs shells or exfiltrates data, self-replicating loops. Anything serious **blocks**; everything else becomes context for gate two. | **≤ 4 096 entries · ≤ 512 MiB unpacked** | **Confirmed** |
| 6.9 | **Gate two** is a reviewer that returns allow / warn / block. A block on the method file **or on any file** kills the installation. | — | **Confirmed** |
| 6.10 | Executable material needs an **explicit allow** — a warning is not an allow. | — | **Confirmed** |
| 6.11 | If the reviewer cannot be understood, or cannot be reached, the result is a **block**. Only non-executable material may be let through on a warning, and only if the operator has deliberately switched off fail-closed behaviour. | — | **Confirmed** |
| 6.12 | A rejected installation names the **file and the reason**; unsafe packaging aborts before anything is written; a name already in use is refused. | — | **Confirmed** |
| 6.13 | A separate read-only **reviewer** can assess a pack without activating it, binding any credentials, or running its scripts, and grades how much assurance it actually has. | verdicts: blocked · revise · publish candidate | **Confirmed** |

### Connected external tools

| # | Rule | Figure |
|---|---|---|
| 6.14 | **Shared** connections are configured by an **administrator only**. | — | **Confirmed** |
| 6.15 | A person may add their **own** connections, but only to a public service over the ordinary secure web protocols; anything that would run a program on the server, or reach an internal address, stays administrator-only. | — | **Confirmed** |
| 6.16 | The programs a connection may be launched with are restricted to a short named list, and this is treated as defence in depth, **not** as a safe boundary — an administrator account is explicitly understood to be equivalent to running code on the machine. | **2 launchers by default** | **Confirmed** |
| 6.17 | An administrator's role is re-checked **before every privileged call**, so a demotion takes effect immediately rather than at the next sign-in. | — | **Confirmed** |
| 6.18 | Switching a connection on or off takes effect without a restart; a cached tool list can be retired deliberately. | **60 s** to establish a connection, per-call timeout configurable | **Confirmed** |
| 6.19 | A connection that cannot be reached is **skipped** — the others keep working — and returns on the next refresh. | — | **Confirmed** |
| 6.20 | When tools are numerous, they are discovered on demand rather than all presented at once, and a selection lasts for **one turn** only. | **≤ 5 results · 256-char search · 100 ms budget** | **Confirmed** |
| 6.21 | Preference hints telling the assistant to favour a particular connection are **advice only**. | priority **0–100** | **Confirmed** |
| 6.22 | A long-running external job is handed over with a reference; the assistant sees only the submission and the reference, while the status is tracked durably in the background and survives a restart. | poll **5 s** · claim lease **120 s** · **8** tracked at once · hint capped at **24 h** | **Confirmed** |
| 6.23 | Such a job may end as running / awaiting input / completed / failed / cancelled — but an answer to "awaiting input" **cannot yet be sent back**. | — | **Confirmed** (a known gap) |

### Custom assistants and extensions

| # | Rule | Figure |
|---|---|---|
| 6.24 | A named assistant is created with its own model, tools, connections, skills and memory policy; its creation is confirmed only by reading it back. | display name ≤ **100** characters | **Confirmed** |
| 6.25 | An assistant can be exported and imported as a versioned package. Import **never overwrites** an existing one, and an invalid package is shown as invalid. The package deliberately excludes memory contents, conversations, credentials and messaging links. | — | **Confirmed** |
| 6.26 | Choosing which connections and skills an assistant uses is a **preference at assembly time, not a permission** — it does not authorise anything. | — | **Confirmed** |
| 6.27 | Packaged extensions are installed by the **operator**, are shown a trust prompt because their code runs with the service's own privileges, and can only come from a vetted package index or a pinned secure address. | — | **Confirmed** |
| 6.28 | An extension change only takes effect at **start-up**; marking one as required turns a failure to load it into a refusal to start at all. | — | **Confirmed** |

---

## 7. Sharing and retention

| # | Rule | Figure |
|---|---|---|
| 7.1 | A shared snapshot is **frozen**: later messages and later file edits do not change it. | — | **Confirmed** |
| 7.2 | It contains the visible conversation and the output files presented at that moment. Uploads, hidden messages, system instructions and execution metadata are excluded — but **tool results are included**, so the conversation should be read before sharing. | — | **Confirmed** |
| 7.3 | Links expire; the owner can also revoke one, which deletes the stored copy. Expired, revoked and unknown links all answer "unavailable". | **7 d default · 1 h to 30 d** | **Confirmed** |
| 7.4 | A conversation may carry a limited number of live links, and a snapshot may not exceed its size limits — exceeding any of them refuses the whole snapshot rather than trimming it. | **10 links · 10 000 rows · 10 MiB text · 64 files · 20 MiB each · 100 MiB total** | **Confirmed** |
| 7.5 | Only a fingerprint of a share link is stored, the full link is shown once, and shared pages instruct browsers not to cache, not to leak the referring page, and not to index. | — | **Confirmed** |
| 7.6 | A shared page can preview text, tables, images, PDFs and static pages, and blocks scripts, forms and network requests in shared pages. Long text previews are truncated. | **512 KiB** | **Confirmed** |
| 7.7 | Continuing someone else's shared conversation creates an **independent copy** in the visitor's own workspace, using their own assistant and permissions — it does not inherit the original's assistant, project, workspace, uploads, tool calls or credentials. The copy survives revocation. | — | **Confirmed** |
| 7.8 | Deleting a conversation removes it and its files, permanently, after showing its title for confirmation. Archiving is the reversible alternative and does **not** stop in-flight work or pause any schedule. | — | **Confirmed** |
| 7.9 | A deleted project document goes to a trash bin and is retained before permanent cleanup; "Empty trash" removes everything immediately instead. | **30 days default** | **Confirmed** |
| 7.10 | Project instructions are refused outright when too long — never quietly cut down. | **8 192 bytes default (256 – 262 144)** | **Confirmed** |
| 7.11 | Project facts gathered automatically are capped. | **30 facts · 2 KiB of context** | **Confirmed** |
| 7.12 | Re-uploading identical content to a project shelf returns the existing entry instead of duplicating it. | shelf file size **50 MiB default** | **Confirmed** |
| 7.13 | There is **no implemented deletion policy** for a conversation's saved state — the retention rules exist as a contract only. What must never be removed is defined: anything needed to resume, ancestors of a branch, a write still in progress, the links holding history together, and the latest resumable state. | — | **Confirmed** |

---

## 8. Files and uploads

| # | Rule | Figure |
|---|---|---|
| 8.1 | Automatic conversion of office documents into readable text is **off by default** and must be deliberately enabled. | default **off** | **Confirmed** |
| 8.2 | Duplicate file names within one upload are renamed rather than overwriting; each call reports the names actually saved. | — | **Confirmed** |
| 8.3 | Directory paths are rejected as uploads; names that would escape the conversation's area, Windows reserved device names, and trailing dots are refused **before** any bytes are staged. | — | **Confirmed** |
| 8.4 | A cancelled upload waits for an in-progress conversion to finish before removing its temporary source, so cancellation can take as long as that conversion. | — | **Confirmed** |
| 8.5 | Quoting a passage from a produced file is limited; rewriting a passage in place requires an exact source range and an output file that is writable, and nothing is written to the file until the person explicitly saves. | **12 000 characters** | **Confirmed** |

---

## 9. Remembering

| # | Rule | Figure |
|---|---|---|
| 9.1 | Only what the person actually said and what the assistant finally replied is examined for durable facts; the assistant's intermediate tool activity is excluded. | — | **Confirmed** |
| 9.2 | Extraction is deliberately delayed and batched rather than run per message: a per-conversation quiet period, with a hard ceiling so continuous activity cannot postpone it forever. | **30 s quiet · 300 s maximum wait · queue depth 1 000** | **Confirmed** |
| 9.3 | A person's stored facts are capped, and so is how much of them is offered back to the assistant on any turn. | **100 facts (10–500) · 2 000 tokens of context (100–8 000)** | **Confirmed** |
| 9.4 | Every proposed fact is classified for scope and durability at a gate that **fails closed** — an unclear, task-local or project-local fact is not written into the person's general memory. | — | **Confirmed** |
| 9.5 | Near-duplicates are merged into what is already held rather than added alongside it, and a merge is **never** treated as the person's confirmation. Explicit corrections, and removals already proposed, are protected from that merging. | — | **Confirmed** |
| 9.6 | Project facts live in a separate pool, are used only inside that project, and are subordinate to explicit instructions and the current request. | **30 facts · 2 KiB of context** | **Confirmed** |
| 9.7 | Making room follows an eviction policy that protects explicit corrections; facts do not expire on a timetable. | — | **Confirmed** (the policy) / **Unresolved** (any retention horizon) |
| 9.8 | A memory read returns complete data or fails — **never a partial list**. Under a strict policy a failed read stops the turn. | — | **Confirmed** |
| 9.9 | A damaged store is set aside under a timestamped copy rather than trusted. | — | **Confirmed** |

---

## 10. Unattended work

| # | Rule | Figure |
|---|---|---|
| 10.1 | Scheduled work is **off until the operator switches it on**, and the ability to set one in conversation is switched on separately again. A deployment can therefore have no autonomous work at all by default. | default **off · off** | **Confirmed** |
| 10.2 | Concurrent scheduled work is capped service-wide and per person; the effective per-person cap is the smaller of the two. | **3 service-wide (1–32) · 2 per person (0 = uncapped)** | **Confirmed** |
| 10.3 | An occurrence that cannot get a slot waits, survives a restart, and is then **skipped with a message** rather than dropped silently. | **1 hour default (60 s – 7 days)** | **Confirmed** |
| 10.4 | The soonest a one-off task may be set is a minute out; a fixed interval may run up to a month; a repeating pattern and a written stop condition are both length-limited. | **60 s minimum · interval ≤ 30 d · pattern ≤ 256 chars · stop condition ≤ 500 chars** | **Confirmed** |
| 10.5 | Autonomous work created through conversation is capped per person, as are the notes a task may carry. | **20 live tasks · 10 notes × 500 chars** | **Confirmed** |
| 10.6 | Three consecutive occurrences judged "outcome not met" **pause the task automatically**. A failure to *check* the outcome neither counts towards this nor resets the count. | **3** | **Confirmed** |
| 10.7 | An unattended turn gets the **same** step budget as an interactive one, so scheduled and interactive work behave identically out of the box; a goal-checking occurrence gets a much smaller allowance. | **1 000 steps · 9 turns for a goal check** | **Confirmed** |
| 10.8 | Tasks created in conversation on a sub-hourly rhythm must carry an end condition. | — | **Confirmed** |
| 10.9 | Proactive messaging is **opt-in per application**; switching it off cancels what is still pending and does not resend on switching it back on. Delivery attempts stop after repeated failure. | **5 attempts** | **Confirmed** |
| 10.10 | A group is **never** chosen automatically for a notification; it must be explicitly bound per task, and a broken binding drops that recipient with a recorded failure. | — | **Confirmed** |
| 10.11 | Deliberately silent: manual trials, interrupted occurrences and start-up failures produce no notice. Ordinary web reminders for tasks and goals are **off by default**. | default **off** | **Confirmed** |
| 10.12 | **There are no quiet hours anywhere in the product** — confirmed by an exhaustive search of the code and documentation. Timing of unattended work and its notifications is entirely the operator's and the person's responsibility. | none | **Confirmed** |

---

## 11. What the product will not pretend about

| # | Rule |
|---|---|
| 9.1 | Recorded token usage may be incomplete during a running turn or when a provider omits it, and **is not a provider bill**. **Confirmed** |
| 9.2 | A run whose promised output could not be handed over is reported as **failed**, not as finished. **Confirmed** |
| 9.3 | A run left behind by a lost worker is back-filled as an error and marked as recovered, rather than left looking alive. **Confirmed** |
| 9.4 | Historical text inside an old message never grants present-day permission to do something. **Confirmed** |

---

## 12. Open questions

- **Unresolved.** The numeric default of the per-person concurrency setting for
  helpers (the hard constant behind it is three).
- **Unresolved.** Who may switch on a *shared* skill pack for everyone, as opposed
  to their own.
- **Unresolved.** Any retention period for recorded skill usage.
- **Unresolved.** The waiver route for a skill review that a reviewer flagged.
