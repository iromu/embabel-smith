package com.embabel.smith.builder;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.core.CompiledDraft;
import com.embabel.smith.builder.core.ValidatedDraft;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.builder.eval.RefinementLoop;
import com.embabel.smith.builder.eval.RefinementOutcome;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.publish.CollisionException;
import com.embabel.smith.builder.publish.FolderPublisher;
import com.embabel.smith.builder.publish.GitPublisher;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.builder.security.ScanVerdict;
import com.embabel.smith.builder.security.AgentSpecSecurityScanner;
import com.embabel.smith.common.model.AgentSpec;

import java.io.IOException;
import java.util.List;

/**
 * The product builder (design D1): a thin, plain-class orchestration that the studio reuses. It
 * runs the build against a description and collects the published pack — the orchestration is not
 * hardcoded into any CLI:
 *
 * <pre>
 *   1. BuilderAgent   — compile → validate → write (D5: an invalid draft is never written)
 *   2. RefinementLoop — score the built pack, rewrite prompts from failures, re-validate (bounded)
 *   3. AgentSpecSecurityScanner — static, offline; a critical finding blocks the build
 *   4. LifecycleStamper — content hash + baseline pass-rate + state, written as lifecycle.yml
 *   5. Publisher      — FolderPublisher | GitPublisher (collision gate; no push by default)
 * </pre>
 *
 * <p>Each gate stops the build and reports the stage and reason; nothing is published when a gate
 * fails.
 */
public class BuilderService {

    private final BuilderAgent builder;
    private final InvokerFactory invokerFactory;
    private final PromptRewriter rewriter;
    private final List<EvalTask> evalTasks;

    private final AgentSpecSecurityScanner scanner = new AgentSpecSecurityScanner();
    private final LifecycleStamper stamper = new LifecycleStamper();

    public BuilderService(BuilderAgent builder, InvokerFactory invokerFactory,
                          PromptRewriter rewriter, List<EvalTask> evalTasks) {
        this.builder = builder;
        this.invokerFactory = invokerFactory;
        this.rewriter = rewriter;
        this.evalTasks = evalTasks == null ? List.of() : List.copyOf(evalTasks);
    }

    public BuildResult build(String description, PublishTarget target, BuildOptions options) {
        // 1. Build: compile → validate → write (D5)
        CompiledDraft compiled = builder.compileFromDescription(new UserInput(description));
        if (compiled.hasIssues()) {
            // The description could not be resolved (ambiguous/unsupported) — report the compiler's issues
            // and retain the draft for review.
            return BuildResult.blocked("build", compiled.pack().name(), compiled.issues(), null, null, compiled.pack());
        }
        ValidatedDraft validated = builder.validateDraft(compiled);
        if (!validated.valid()) {
            return BuildResult.blocked("build", validated.pack().name(), validated.issues(), null, null, validated.pack());
        }
        builder.writeAgentSpec(validated);
        AgentSpec pack = validated.pack();

        // 2. Eval + bounded refinement (score gate)
        RefinementLoop loop = new RefinementLoop(invokerFactory, rewriter, evalTasks,
                options.scoreGate(), options.maxRefine());
        RefinementOutcome outcome = loop.refine(pack);
        if (!outcome.passed()) {
            return BuildResult.blocked("eval", pack.name(),
                    List.of("eval score " + String.format("%.3f", outcome.score())
                            + " is below the gate " + options.scoreGate()
                            + "; failing tasks: " + outcome.failingTasks()),
                    outcome.score(), null, null);
        }
        AgentSpec finalPack = outcome.pack();

        // 3. Security gate (static, offline)
        ScanReport scan = scanner.scan(finalPack);
        if (scan.verdict() == ScanVerdict.BLOCK) {
            List<String> findings = scan.findings().stream()
                    .map(f -> f.severity() + " " + f.category() + ": " + f.message()
                            + " [" + f.evidence() + "]")
                    .toList();
            return BuildResult.blocked("security", finalPack.name(), findings, outcome.score(), scan, null);
        }

        // 4. Lifecycle stamp
        LifecycleMetadata lifecycle = stamper.stamp(finalPack, outcome.score());

        // 5. Publish (collision gate; no push by default)
        try (Publisher publisher = publisherFor(target)) {
            PublishResult published = publisher.publish(finalPack, lifecycle,
                    options.overwrite(), options.push());
            return BuildResult.built(finalPack.name(), outcome.score(), scan, lifecycle, published);
        } catch (CollisionException e) {
            return BuildResult.blocked("publish", finalPack.name(), List.of(e.getMessage()), outcome.score(), scan, null);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to publish pack '" + finalPack.name() + "': " + e.getMessage(), e);
        }
    }

    private static Publisher publisherFor(PublishTarget target) {
        return switch (target.type()) {
            case FOLDER -> new FolderPublisher(target);
            case GIT -> new GitPublisher(target);
        };
    }
}
