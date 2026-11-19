# Proposal

## Why

Nothing in this repository verifies a change on its own. `.github/workflows/` holds one workflow,
`wiki-pages.yml`, which builds and deploys the wiki and never touches the reactor — so the only
automated thing that happens to a commit here is publishing documentation. Every build, test and
style guarantee is whatever the next person remembers to run locally.

The consequences are already on record. A builder API change broke the studio's test-compile and
went unnoticed at `HEAD`. The whole-codebase review of 2026-11-16 found one module's style violations
were the *sole* reason `make build`, `make test` and `make lint` were red, which made every other
module's state invisible behind them. And `docs/12-backlog.md` states that without a model endpoint
the offline suite passes while the publish, run and loop paths go untested — a green run reading as
full coverage. None of that is detectable from a pull request today.

The cheapest fix is the run that needs nothing: no endpoint, no secrets, no Docker. The reactor's
own entry points already exist (`make build`, `make test`, `make lint`), the live model tests are
already excluded by tag (`test.excludedGroups=live`), and the `Makefile` already tolerates a missing
endpoint file. So the pipeline is a workflow file, not a project.

## What Changes

- A new workflow, `.github/workflows/offline-verification.yml`, running on pushes to the default
  branch and on pull requests: `make build`, then `make test`, then `make lint`.
- It runs with **no model endpoint configured** and no secrets. The live group stays excluded by the
  root pom's default, so the run cannot come to depend on a model answering.
- The POC reactor stays out: the workflow runs the default reactor, matching `make build` and
  `make test` without `POC=1`. `poc/` is frozen evidence, and re-verifying it on every commit is not
  what this is for.
- `README.md`'s run book and `docs/05-architecture.md`'s build-entry-point section name the workflow
  as where these three guarantees are now enforced.

Not changed: no live or scheduled model run, no endpoint secrets in GitHub, no coverage thresholds,
no publishing. Those are separate decisions with costs attached, and each is recorded in
`design.md` as rejected rather than omitted.

## Capabilities

### New Capabilities

- `offline-verification-pipeline`: the automated run that checks a change without a model — which
  commands it runs, that it runs with no endpoint and therefore cannot depend on one, which parts of
  the repository it covers and deliberately does not, and what a green result does and does not
  license someone to believe.

### Modified Capabilities

None. There are no capability specs under `openspec/specs/` — they were consolidated into `docs/` on
2026-11-15 — so there is no existing requirement text to modify. The durable home for this rule is
`docs/05-architecture.md`, which already carries the build entry points.

## Impact

- **`.github/workflows/offline-verification.yml`**: new file. Needs the runner's JDK set to the
  version the build targets (`pom.xml` sets `java.version` and `maven.compiler.release` to 21), and
  uses the repository's own `./mvnw` through the `Makefile`, which prefers the wrapper when present.
- **`README.md`** and **`docs/05-architecture.md`**: one statement each of where the offline
  guarantees are enforced, so the next reader does not conclude they are unenforced.
- **`docs/12-backlog.md`**: the *Test coverage* bullet gains the fact that the offline suite now runs
  automatically, while keeping what it still does not prove — the four endpoint-gated paths remain
  unverified by CI, and saying otherwise would be the exact misreading the bullet exists to prevent.
- **Cost**: GitHub Actions minutes on the public runners for a Maven reactor build. No model calls, so
  no endpoint spend.
- **Depends on**: nothing. It runs against the tree as it stands.
