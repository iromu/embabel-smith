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
 * A rejection names the stage and records the reasons (and, when reached, the score and scan).
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

    public GateResult gate(SkillPack candidate, double baseline) {
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


        // d. baseline comparison (strict: must beat the baseline)
        if (score <= baseline) {
            List<String> reasons = List.of(
                    "re-eval " + String.format("%.3f", score)
                            + " does not beat baseline " + String.format("%.3f", baseline));
            return GateResult.reject("baseline", reasons, score, scan);
        }

        return GateResult.pass(score, scan);
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
