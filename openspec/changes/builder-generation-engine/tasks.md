# Tasks

## 1. Single-shot generator in the builder

- [x] 1.1 Add the `process_to_flow` prompt resource to `prod/embabel-smith-builder` and implement
      `SingleShotAgentSpecGenerator` (render prompt → `Ai` call → fence-stripped JSON parse →
      normalize manifest/goal/prompt-vars → `AgentSpec`). Verify with an offline unit test that
      injects a canned JSON answer and asserts the parsed, normalized `AgentSpec` (manifest present,
      goal exported, prompt vars canonicalized).
- [x] 1.2 Introduce the `AgentSpecGenerator` seam (returns the shared format in the builder's
      own `CompiledDraft`, so no Embabel type crosses it) and an
      `EmbabelAgentSpecGenerator` that wraps the existing `DefaultAgentSpecCompiler` +
      `DraftToAgentSpec`. Verify the embabel path still yields the same normalized `AgentSpec`
      (the `void` → `BackgroundMessage` goal normalization is preserved) via the existing
      `BuildCoreTest` / `DraftToAgentSpecTest`.

## 2. Technique selection

- [x] 2.1 Add `technique` to `BuildOptions` (default `single-shot`) and thread it through
      `BuilderService.build` → `BuilderAgent.compileFromDescription` so the build selects the right
      generator. Verify with an offline test that a build with each technique dispatches to the
      corresponding generator.
- [x] 2.2 Add `--technique <single-shot|embabel>` to the CLI (`BuildCommand`) with `single-shot`
      as the default. Verify `smith build --help` lists the flag and a build with
      `--technique embabel` selects the embabel generator.

## 3. Studio delegates to the builder

- [x] 3.1 Rewire `StudioConfiguration.generateFn` so both techniques route to the builder's
      engine. Verify the studio's generate tests still pass for both `single-shot` and `embabel`
      (the returned `AgentSpec` matches the previous behavior).
- [x] 3.2 Remove the studio's `ProcessFlowGenerator` and `NlCompilerFlowGenerator` (and the
      generate-only `FlowPromptCaller` / `LlmFlowPromptCaller` if nothing else references them).
      Verify the studio module compiles and its generate tests pass.

## 4. Docs + verification

- [x] 4.1 Update the builder `README.md` to document the `--technique` flag and the single-shot
      default. Verify the README reflects the flag and default.
- [x] 4.2 Run the builder's offline suite (`mvn -pl prod/embabel-smith-builder test`) and the
      studio's suite, both green with no endpoint configured, and a live build per technique:
      single-shot publishes a valid pack, and the embabel technique is verified at the generation
      seam — it compiles to the shared format but blocks before publish (see the README's
      known-limitation note and the matching design risk).
