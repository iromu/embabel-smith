# embabel-smith-tools-coding

A tool addon. It registers the tool groups a pack's step names when it writes
`toolGroups: [file]` or `toolGroups: [shell]`, so a pack can do filesystem and command work
without any deployment writing Java. Everything it offers is confined to one directory — the
run root — and every switch it reads is reported at start-up rather than left to be inferred
from a run that was offered nothing.

It is inert until a deployment declares it. `embabel-smith-runtime` declares it at
*provided* scope — a scope that stops at the module that declares it — so a plain
`smith run` gets the `file` group while `embabel-smith-studio` and an A2A host, which
reach the runtime as a dependency, still resolve neither role. Why that scope rather
than `optional` is settled in `docs/05-architecture.md`. A deployment that wants file- or
command-backed tools declares this module itself; it does not inherit them from something
that happens to depend on the runtime.

## What registers

| Role | Switch | With nothing configured | What it offers |
| --- | --- | --- | --- |
| `file` | `smith.tools.file.enabled` | **on** | `readFile`, `listFiles`, `findFiles` inside the run root. No write or edit tool at all. |
| `shell` | `smith.tools.shell.enabled` | **off** | nothing — the group is not registered, so the role resolves to nothing and the run says so |

`file` registers by default because it is a role the runtime's default trust tier already
names and the platform ships the tools behind it. `shell` does not, because it is a role of
the trust vocabulary's highest band: an addon arriving by itself must not start offering a
process-spawning tool. Setting `smith.tools.shell.enabled=true` registers `runCommand`, which
runs a command inside the run root and reports its exit code and captured output; a pack
naming `shell` still has to pass the tier gate.

Turning a switch off is not the same as the group being unknown, and the boot report says
which of the two it is.

## Setting them together: `smith.tools.preset`

```properties
smith.tools.preset=coding
smith.tools.file.enabled=true   # beats the preset; the boot line names which one decided
```

A preset is shorthand for the switches, not a third gate beside them. Selecting one writes
`smith.tools.<group>.enabled` for **every** group this module contains, into a property
source placed below the deployment's own — and that placement is the whole of the precedence.
An explicit switch resolves ahead of the entry sitting under it, so nothing in this addon
compares a preset with a switch and there is no second rule to keep in step with the first.
The consequence is the one to know before selecting one: a preset decides every group, so a
preset naming only some of them switches the rest off rather than leaving them at their
documented defaults.

`coding` is the only preset here, and it names both groups. A name this module does not define
is reported and applies nothing at all — not the matching part of it — listed beside the
presets it does define, which is the same sentence the stray-switch list says about a switch
for an addon that is not on the classpath. So `smith.tools.preset=video` in a boot carrying
only this module says there was nothing here to enable, and the boot keeps the untouched
default. A second tool family adds presets to its own submodule's vocabulary; selecting a
preset is never a way to reach a module that is not there.

**One caveat, and it belongs to the gates rather than to the preset:** an AOT or native build
decides the `@ConditionalOnProperty` gates while it compiles, so a runtime property — a
preset included — cannot add back a bean `process-aot` froze out. A native image that needs a
group asks for it through the same build-time route the switches use.

## Settings

| Property | Default | Meaning |
| --- | --- | --- |
| `smith.tools.coding.root` | the process working directory | The single directory every tool works inside, for the life of the process. A boot-time value, not a per-run one: the platform resolves tools from beans. |
| `smith.tools.coding.writes` | `false` | Whether the group that registers by default is allowed to hold a writer. Off means no writer is *built* — not one built and hidden — so `createFile`, `writeFile`, `editFile`, `appendFile` and `delete` are absent rather than refused. On builds the platform's read-write file tools at the run root, plus this addon's edit tool. |
| `smith.tools.coding.command-timeout` | `60s` | How long a command may run before it and its whole process tree are terminated and the result reports a timeout. |
| `smith.tools.coding.max-output-chars` | `20000` | How many characters of each of a command's stdout and stderr the result keeps. Past it the remainder is dropped and the result states the truncation with the kept and total counts. |

The edit tool is this addon's own, and it replaces the platform's `editFile`: a match that
appears more than once is refused rather than rewritten everywhere. It is offered only when
writes are on, since it writes.

## Composing a role: `smith.tools.expose.<role>`

```properties
smith.tools.expose.review=file-read,search
smith.tools.expose.builder=file-write
```

Each key becomes a tool-group role a step can name; each value is built from a closed
vocabulary and nothing else:

| Capability | Composes |
| --- | --- |
| `file-read` | `readFile`, `fileCount` and `fileSize` at the run root — the platform's read-only `FileTools`. Carries no writer of any kind. |
| `file-write` | The read surface plus `createFile`, `writeFile`, `appendFile`, `delete` and `createDirectory`, and this addon's `editFile` standing in for upstream's. |
| `search` | `listFiles`, `findFiles` and `findPatternInProject` — the platform's `LocalDirectory` — confined to the run root. |
| `command` | `runCommand` at the run root, under the two bounds above. |

**A search that found more than 200 matches says so.** `findFiles` and `findPatternInProject`
are the platform's, and the platform's walk returns every match it finds with no hint that
the list might go on. The same wrapper that checks where a call was aimed caps both at
`ConfiningTool.SEARCH_CAP` matches and appends the statement that the cap was reached, with
the number found, so a shortened listing cannot read as the whole repository. Under the cap
nothing is added or restated — every match comes back with its path. The bound is a constant
rather than a property, because a knob invites a deployment to raise it until the listing
stops meaning anything, and it is scoped to those two names: clipping a `readFile` the same
way would quietly make it a different tool from the one the platform describes. Path matching
is offered through `findFiles`, which takes a `glob`, rather than as a separate yes/no
predicate tool — a predicate would cost a round-trip to answer a question about a pattern the
model cannot see.

Empty by default, and empty is the documented state: nothing registers a role nobody named.
Composed roles reach the platform the way upstream's own operator-authored roles do — as one
`List<ToolGroup>` bean, which the platform appends *after* the individual group beans — so a
composed role adds a name without displacing `file` or `shell`. That last clause is measured
from a runtime boot rather than asserted here: this module has no platform to resolve
against, so `ResolvedToolGroupsTest` (in `prod/embabel-smith-runtime`) is the test that asks
`resolveToolGroup` for a composed role and lists what it offers.

## What gets refused, out loud

- **A path that leaves the run root** — by traversal, by absolute path, or through a link —
  is refused with an error naming both the path asked for and the root. Every composed and
  published tool goes through that check.
- **An expose entry outside the four capabilities** refuses the *boot*, naming the property,
  the entry and the whole vocabulary. A composed role that named something unbuildable would
  otherwise register a group with no tools behind it, which a step reaches for and gets
  nothing from, with the explanation only in a resolver log line nobody reads.
- **A class name in that map** is refused the same way, with a sentence saying that this map
  is a closed vocabulary and never instantiates a class a property names. Upstream's
  operator-authored roles are built from tool names, so the instinct is a normal one; the
  answer is given rather than left to be guessed.
- **An empty capability list** for a role is refused too, since it would register an empty
  group.

## What the boot says

One line, whatever the switches say:

```
[smith-tools] coding addon: ours by default [file], ours switched on [none], ours disabled by
configuration [none], ours off by default [shell], registered nothing from [shell: off by
default, nothing asked for it], not ours [none], ours shadowing {}, decided by {file=no
switch set (documented default), shell=no switch set (documented default)}
```

Each of the addon's two groups lands in exactly one of the first four categories, so "off"
never reads as "unknown" and a group the deployment switched off is distinguishable from one
the addon never heard of. A `smith.tools.<group>.enabled` naming a group this module has no
recipe for is listed by name — a typo'd switch that silently registers nothing is the same
invisible failure this addon exists to remove for steps.

`decided by` answers the next question, which the first four cannot: *who* said so. A group
registered by a preset and a group registered by an explicit switch sit in the same
category, so the report names the property source the value was read from — a deployment
switch, `preset 'coding'`, or the documented default with nothing set. It is read from the
environment's own ordering rather than recomputed, which is the same reason the preset
writes switches instead of being consulted: one rule, in one place, reported as a fact about
the boot.

The last two clauses come from reading the registry rather than the switches, which is the
only way to answer the question that actually matters: what can a run resolve. `not ours`
names roles the context can answer with that this addon did not register — `math` being the
one that matters, since it is upstream's own bean and nothing in this repo chose it.
`ours shadowing` names a role this addon registered that a second claimant also answers, and
which property built that claimant: the platform's resolver takes the *first* group whose role
matches and builds its list from individual `ToolGroup` beans before the provider lists, so
when upstream's `embabel.agent.platform.tools.includes.file` also names `file`, this addon's
group is the one that answers and the other is shadowed rather than absent. The claim is read
from groups that exist; a deployment carrying the property with nothing to act on it is
reported as no collision, because that is what it is.
