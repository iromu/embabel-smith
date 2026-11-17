# Tasks

## 1. Ingestion in the builder

- [x] 1.1 `git mv` `BlueprintLoader` from `studio/generate` into `prod/embabel-smith-builder` under
      `com.embabel.smith.builder.blueprint`, body unchanged (200K cap, `--- <relPath> ---` separators,
      truncation marker, `.md`/`.markdown`/`.txt` filter intact), and verify `make build` compiles the
      builder module.
- [x] 1.2 `git mv` `BusinessProcessDiscovery` the same way (300K cap, `.md`/`.markdown` only,
      `git clone --depth 1` under a 180 s bound), and verify `make build` still compiles the builder.
- [x] 1.3 `git mv` `ProcessExtractor`, add the single-argument constructor that loads
      `/prompts/business_process_discovery.jinja` from the builder classpath while keeping the
      two-argument form for substitution, and verify a builder test asserts the discovery prompt
      reaches the caller as the system message and the model's answer is returned verbatim.
- [x] 1.4 `git mv` the prompt resource itself with no text change, and verify the studio's
      `src/main/resources/prompts/` holds nothing and the builder's holds eight files.
- [x] 1.5 Re-point the studio at the builder — `DiscoverController`, `ExtractController`,
      `GenerateController` and the `StudioConfiguration` beans `blueprintLoader`, `processExtractor`,
      `extractFn`, `businessProcessDiscovery` — delete the now-uncalled private `loadPrompt` helper,
      move the ingestion tests to the builder rather than duplicating them, and verify `make test`
      passes reactor-wide with the studio's REST tests asserting unchanged response shapes.
- [x] 1.6 Update `docs/08-builder.md` to describe the ingestion stages and `docs/09-studio.md` to say
      the studio delegates them, and verify each page names only classes that exist in the module it
      describes.

## 2. Documents as build input

- [x] 2.1 Add `--docs <path>` to `BuildCommand`, make the positional description optional and
      mutually exclusive with it, and verify `BuildCommandTest` covers neither-given and
      both-given as usage errors exiting 2.
- [x] 2.2 Add `--extract`, so `--docs` alone sends the assembled corpus to the build and `--docs
      --extract` runs the discovery prompt first, and verify a test shows the corpus-only path never
      invokes the extractor while `--extract` invokes it exactly once before the build.
- [x] 2.3 Give the ingestion clone the same non-interactive git posture `GitPublisher` uses
      (`GIT_TERMINAL_PROMPT=0`, `core.askpass=false`), and verify a test asserts the environment the
      clone process is started with, so a private repository cannot turn a run into a hang.
- [x] 2.4 Document both flags in the builder `README.md` usage block and `docs/08-builder.md`, and
      verify the command line as written parses.

## 3. Integration checks

- [x] 3.1 Verify both modules' `NoDirectModelRequestTest` still pass now that ingestion lives in the
      builder, so the moved code reaches the model only through the `Ai` layer.
- [x] 3.2 Verify `make test` and `make lint` are green reactor-wide, with particular attention to the
      studio's *test* sources compiling — the builder-API blind spot that has previously broken them
      unnoticed at HEAD.
      *Evidence 2026-11-17:* `make lint` reports 0 Checkstyle violations for all eight modules and
      `make test` is BUILD SUCCESS — 1318 tests, 0 failures, 0 errors, 0 skipped (common 39,
      specsource 29, skillevaluator 942, runtime 45, builder 142, studio 121), so the studio's test
      sources do compile against the moved builder API. Two failures found on the way were pre-existing
      and unrelated to this change, each reproduced at clean `ccbd8aa` and then fixed: a staged delete
      of `prod/embabel-smith-runtime/src/test/resources/test-packs/dup-a/types/.gitkeep` (git paired it
      as a rename into the new root `.graphifyignore`) had removed that fixture directory, and
      `LoopServiceTest` stubbed `ImprovementLoop.runOnce()` where `LoopService.trigger` calls the
      `runOnce(String)` overload, so on a mock the stub could never match and strict stubbing rejected
      every `runOnce(null)`.
- [x] 3.3 Verify the scenario the change exists for: `smith build --docs examples/blueprints/Aurora`
      reaches the build stage against a configured endpoint, and `--target skill` reaches the skill
      stage.
      *Evidence 2026-11-17 (live endpoint):* the docs build published
      `BUILD OK: aurora_pharma_order_to_delivery` (eval 1.000, security SAFE, lifecycle INCUBATING,
      10 steps + 8 types derived from the Aurora blueprint) with exit 0; the multi-agent candidate
      lost to the documented dangling-type limit, so the single-agent spec published. The skill run
      published `SKILL BUILD OK: system-specification-md-aurora-global-ph`, lift +0.235
      (with 0.882 vs baseline 0.647), exit 0.

## Workflow follow-up

- Resolve the two open questions in `design.md` if either becomes cheap while implementing.
- Archive only after the endpoint-gated paths have actually been run live, per the project's convention
  for changes touching the model layer.
- The module-ownership vocabulary in `.wiki/` cross-links 44 pages: re-ingest and lint, and republish
  to GitHub Pages, after `docs/08` and `docs/09` change.
