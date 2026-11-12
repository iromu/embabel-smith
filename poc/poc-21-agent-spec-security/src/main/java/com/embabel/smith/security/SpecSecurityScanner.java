package com.embabel.smith.security;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import com.embabel.smith.trust.InputSanitizerGuardRail;
import com.embabel.smith.trust.TrustTier;
import com.embabel.smith.trust.TrustTierPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * An install-time static scanner for a agent spec (doc 02 caveat 3 — the security half). It vets a
 * spec <em>before</em> it is loaded, complementing POC 10's <em>runtime</em> trust tiers: it flags
 * prompt-injection text in the spec's own prompt, over-privileged tool groups, network references,
 * and secret-exfiltration patterns, and reports a risk verdict plus the minimum POC 10 trust tier at
 * which the spec is safe to load.
 *
 * <p>Pure static analysis over the {@link AgentSpec} model — no model endpoint, fully offline. Like
 * POC 10's guardrail, matching is <em>normalized</em> (lowercased, punctuation/whitespace stripped),
 * so spacing/punctuation obfuscation does not carry a finding through; and, like that guardrail, it
 * is a pattern matcher, not a semantic detector — a novel rephrasing outside the pattern set can slip
 * through. That is the honest limit of a non-LLM install-time scan.
 */
public class SpecSecurityScanner {

    /**
     * Normalized prompt-injection phrases (lowercase, alphanumerics only). A spec whose <em>own</em>
     * prompt carries one of these is trying to hijack the agent that loads it — the "spec as an
     * attack vector" case (doc 02 §5.3, Schmotz et al.).
     */
    private static final List<String> INJECTION_PATTERNS = List.of(
            "ignorepreviousinstructions",
            "ignoreallprevious",
            "disregardpreviousinstructions",
            "disregardtheabove",
            "overrideyourinstructions",
            "revealyoursystemprompt",
            "printyourinstructions",
            "youarenow",
            "newinstructions",
            "donottelltheuser",
            "withouttellingtheuser");

    /** Tool groups that grant host-level or write capability — flagged as high-privilege. */
    private static final List<String> HIGH_PRIVILEGE_GROUPS = List.of("shell", "file");

    /** Network-reach markers: a URL scheme or a fetch command. */
    private static final List<String> NETWORK_MARKERS = List.of("http://", "https://", "curl ", "wget ");

    /** High-precision secret markers (kept precise to avoid false positives on benign text). */
    private static final List<String> SECRET_MARKERS = List.of(
            "api key", "api_key", "apikey", "client secret", "access token", "auth token",
            "credential", "private key", "process.env", ".env", ".aws/credentials");

    private final TrustTierPolicy tiers = new TrustTierPolicy();

    /** Scans a agent spec and returns its findings, verdict, and recommended load tier. */
    public ScanReport scan(AgentSpec pack) {
        List<Finding> findings = new ArrayList<>();
        List<String> prompts = allPrompts(pack);

        findings.addAll(injectionFindings(prompts));
        findings.addAll(secretExfilFindings(prompts));
        findings.addAll(networkFindings(prompts));
        findings.addAll(toolGroupFindings(pack));
        Finding chain = untrustedInputToShellFinding(pack);
        if (chain != null) {
            findings.add(chain);
        }

        TrustTier tier = minimumTier(pack);
        ScanVerdict verdict = verdict(findings);
        return new ScanReport(pack.name(), findings, verdict, tier);
    }

    /** Every prompt body in the pack (the attack surface a static scan can see). */
    private List<String> allPrompts(AgentSpec pack) {
        List<String> prompts = new ArrayList<>();
        for (ActionStepSpec step : pack.steps()) {
            if (step.prompt() != null) {
                prompts.add(step.prompt());
            }
        }
        return prompts;
    }

    private List<Finding> injectionFindings(List<String> prompts) {
        List<Finding> out = new ArrayList<>();
        for (String prompt : prompts) {
            String normalized = InputSanitizerGuardRail.normalize(prompt);
            for (String pattern : INJECTION_PATTERNS) {
                if (normalized.contains(pattern)) {
                    out.add(new Finding(Severity.CRITICAL, Finding.CAT_PROMPT_INJECTION,
                            "Spec prompt contains a prompt-injection marker", pattern));
                    break;
                }
            }
        }
        return out;
    }

    private List<Finding> secretExfilFindings(List<String> prompts) {
        List<Finding> out = new ArrayList<>();
        for (String prompt : prompts) {
            String lower = prompt.toLowerCase(Locale.ROOT);
            for (String marker : SECRET_MARKERS) {
                if (lower.contains(marker)) {
                    out.add(new Finding(Severity.CRITICAL, Finding.CAT_SECRET_EXFIL,
                            "Spec prompt references a secret or credential", marker));
                    break;
                }
            }
        }
        return out;
    }

    private List<Finding> networkFindings(List<String> prompts) {
        List<Finding> out = new ArrayList<>();
        for (String prompt : prompts) {
            String lower = prompt.toLowerCase(Locale.ROOT);
            for (String marker : NETWORK_MARKERS) {
                if (lower.contains(marker)) {
                    out.add(new Finding(Severity.WARN, Finding.CAT_NETWORK_REFERENCE,
                            "Spec prompt reaches an external network source", marker.trim()));
                    break;
                }
            }
        }
        return out;
    }

    private List<Finding> toolGroupFindings(AgentSpec pack) {
        List<Finding> out = new ArrayList<>();
        for (String group : new TreeSet<>(declaredToolGroups(pack))) {
            if (HIGH_PRIVILEGE_GROUPS.contains(group)) {
                out.add(new Finding(Severity.WARN, Finding.CAT_HIGH_PRIVILEGE_TOOL,
                        "Spec declares a high-privilege tool group", group));
            }
        }
        return out;
    }

    /**
     * Flags the injection-to-execution chain: untrusted {@code UserInput} feeding a step that also
     * declares the {@code shell} tool group (doc 02 §5.3 — "a benign approval can carry over to
     * harmful actions").
     */
    private Finding untrustedInputToShellFinding(AgentSpec pack) {
        boolean readsUserInput = pack.steps().stream()
                .anyMatch(step -> step.inputTypeNames().contains(SampleAgentSpecs.USER_INPUT));
        boolean hasShell = declaredToolGroups(pack).contains("shell");
        if (readsUserInput && hasShell) {
            return new Finding(Severity.WARN, Finding.CAT_UNTRUSTED_INPUT_TO_SHELL,
                    "Untrusted user input reaches a shell tool group", "UserInput -> shell");
        }
        return null;
    }

    private List<String> declaredToolGroups(AgentSpec pack) {
        List<String> groups = new ArrayList<>();
        pack.steps().forEach(step -> groups.addAll(step.toolGroups()));
        return groups;
    }

    /** The lowest POC 10 tier whose allowlist covers every tool group the spec declares. */
    private TrustTier minimumTier(AgentSpec pack) {
        for (TrustTier tier : TrustTier.values()) {
            if (tiers.check(pack, tier).allowed()) {
                return tier;
            }
        }
        return TrustTier.T4_PLATFORM;
    }

    private ScanVerdict verdict(List<Finding> findings) {
        if (findings.stream().anyMatch(Finding::isCritical)) {
            return ScanVerdict.BLOCK;
        }
        if (findings.stream().anyMatch(Finding::isWarning)) {
            return ScanVerdict.REVIEW;
        }
        return ScanVerdict.SAFE;
    }
}
