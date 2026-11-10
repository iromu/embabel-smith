package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.eval.EvalScorer;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.builder.security.ScanVerdict;
import com.embabel.smith.builder.security.SkillSecurityScanner;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.validation.SkillPackValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * The gate a candidate must clear before it may be published, run cheap-first so a broken or
 * unsafe candidate never spends eval budget (design "Gate order"):
 *
 * <ol>
 *   <li><b>D5 validation</b> — structural validation of the candidate (free, deterministic);</li>
 *   <li><b>security scan</b> — the static scanner; a critical finding rejects (free,
 *       deterministic);</li>
 *   <li><b>re-eval</b> — the candidate is deployed and run against the eval tasks (costs model
 *       calls);</li>
 *   <li><b>baseline comparison</b> — the candidate's pass-rate must be <em>strictly</em> greater
 *       than the pack's baseline (design "Beat the baseline is strict").</li>
 * </ol>
 *
 * <p><b>Failure-fix fallback (the ceiling case).</b> A builder-built pack is stamped at its
 * build-time score, which the build's score gate pins to the max achievable on the (binary) smoke
 * task — a baseline the strict re-eval can never beat. When the re-eval score does not beat the
 * baseline <em>and</em> the candidate was refined from concrete production failures, the gate
 * instead proves the actual improvement: it re-runs the candidate on those failing inputs and
 * requires a successful (non-blank) output for each. The strict baseline-beat remains the primary
 * criterion; the fallback only applies when the trigger was real failures, never for a drift-only
 * pass.
 *
 * <p>A rejection names the stage and records the reasons (and, when reached, the score and scan).
 */
public class GateChain {

    private final SkillPackValidator validator;
    private final SkillSecurityScanner scanner;
    private final InvokerFactory invokerFactory;
    private final List<EvalTask> evalTasks;

    public GateChain(InvokerFactory invokerFactory, List<EvalTask> evalTasks) {
        this(new SkillPackValidator(), new SkillSecurityScanner(), invokerFactory, evalTasks);
    }

    public GateChain(SkillPackValidator validator, SkillSecurityScanner scanner,
                     InvokerFactory invokerFactory, List<EvalTask> evalTasks) {
        if (evalTasks == null || evalTasks.isEmpty()) {
            throw new IllegalArgumentException("eval tasks must not be empty");
        }
        this.validator = validator;
        this.scanner = scanner;
        this.invokerFactory = invokerFactory;
        this.evalTasks = List.copyOf(evalTasks);
    }

    /** Runs the gate without triggering failures (a drift-only pass: no failure-fix fallback). */
    public GateResult gate(SkillPack candidate, double baseline) {
        return gate(candidate, baseline, List.of());
    }

    /**
     * Runs the gate with the triggering production failures threaded in. When the strict re-eval
     * cannot beat the baseline (the ceiling case), the gate proves the actual improvement by
     * re-running the candidate on each triggering failure and requiring an output that is both
     * non-blank and <em>different</em> from the recorded failure (a candidate that reproduces the
     * same truncated output has not fixed anything).
     */
    public GateResult gate(SkillPack candidate, double baseline, List<FailedInput> failedInputs) {
        // a. D5 validation (cheap, deterministic)
        var validation = validator.validate(candidate);
        if (!validation.isValid()) {
            List<String> issues = validation.issues().stream()
                    .map(i -> i.code() + " — " + i.message())
                    .toList();
            return GateResult.reject("validation", issues, null, null);
        }

        // b. security scan (cheap, deterministic) — a critical finding blocks before any eval spend
        ScanReport scan = scanner.scan(candidate);
        if (scan.verdict() == ScanVerdict.BLOCK) {
            List<String> findings = scan.findings().stream()
                    .map(f -> f.severity() + " " + f.category() + ": " + f.message() + " [" + f.evidence() + "]")
                    .toList();
            return GateResult.reject("security", findings, null, scan);
        }

        // c. re-eval (expensive — only reached by a valid, safe candidate)
        double score = reeval(candidate);

        // d. baseline comparison (strict: must beat the baseline) — the primary criterion
        if (score > baseline) {
            return GateResult.pass(score, scan);
        }

        // e. failure-fix fallback (the ceiling case): the re-eval cannot beat the baseline AND the
        //    candidate is already at the ceiling on the eval tasks (score at max — it cannot do
        //    better), so prove the actual improvement — re-run the candidate on the triggering
        //    failures and require a successful (non-blank) output for each. A candidate that is
        //    failing its own eval tasks (score below the ceiling) is broken, not merely at the
        //    ceiling, and is rejected without the fallback.
        if (score >= 1.0 && failedInputs != null && !failedInputs.isEmpty()) {
            List<String> unfixed = failingInputs(candidate, failedInputs);
            if (unfixed.isEmpty()) {
                return GateResult.pass(score, scan);
            }
            List<String> reasons = List.of(
                    "re-eval " + String.format("%.3f", score) + " does not beat baseline "
                            + String.format("%.3f", baseline) + " and the candidate still fails "
                            + unfixed.size() + " of " + failedInputs.size() + " triggering input(s)");
            return GateResult.reject("baseline", reasons, score, scan);
        }

        List<String> reasons = List.of(
                "re-eval " + String.format("%.3f", score)
                        + " does not beat baseline " + String.format("%.3f", baseline));
        return GateResult.reject("baseline", reasons, score, scan);
    }

    /**
     * The triggering inputs the candidate has not fixed: those for which it produces no (blank)
     * output, or output that merely reproduces the recorded failure (the same truncated output
     * excerpt, so the candidate is repeating the defect rather than correcting it).
     */
    private List<String> failingInputs(SkillPack candidate, List<FailedInput> failedInputs) {
        var invoker = invokerFactory.invokerFor(candidate);
        List<String> unfixed = new ArrayList<>();
        for (FailedInput failure : failedInputs) {
            String output = invoker.invoke(failure.input());
            if (output == null || output.isBlank()) {
                unfixed.add(failure.input());
                continue;
            }
            String failed = failure.failedOutput();
            if (failed != null && !failed.isBlank() && output.startsWith(failed)) {
                unfixed.add(failure.input());
            }
        }
        return unfixed;
    }

    /**
     * A triggering production failure: the input that failed and the (possibly truncated) output
     * excerpt the runtime recorded for it. The gate re-runs the candidate on the input and requires
     * an output that is non-blank and does not merely reproduce {@code failedOutput}.
     */
    public record FailedInput(String input, String failedOutput) {
    }

    /**
     * Deploys the pack and runs it against the eval tasks, returning its pass-rate. Exposed so the
     * loop can do the "live re-eval &lt; baseline" regression check on a pack's current content.
     */
    public double reeval(SkillPack pack) {
        var invoker = invokerFactory.invokerFor(pack);
        List<String> outputs = new ArrayList<>();
        for (EvalTask task : evalTasks) {
            outputs.add(invoker.invoke(task.input()));
        }
        return EvalScorer.score(evalTasks, outputs);
    }
}
