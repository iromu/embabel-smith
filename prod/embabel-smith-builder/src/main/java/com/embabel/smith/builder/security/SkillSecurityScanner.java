package com.embabel.smith.builder.security;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.samples.SampleSkillPacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * The builder's pre-publish security gate (design D4, extracted from POC 21): a static scanner that
 * vets a skill-pack <em>before</em> it is published. It flags prompt-injection text in the skill's
 * own prompt, over-privileged tool groups, network references, and secret-exfiltration patterns,
 * and reports a risk verdict plus the minimum trust tier at which the skill's tool groups are
 * permitted. A {@link Severity#CRITICAL} finding means the build is blocked.
 *
 * <p>Pure static analysis over the {@link SkillPack} model — no model endpoint, fully offline.
 * Matching is <em>normalized</em> (lowercased, punctuation/whitespace stripped), so
 * spacing/punctuation obfuscation does not carry a finding through; and, like POC 21's scanner, it
 * is a pattern matcher, not a semantic detector — a novel rephrasing outside the pattern set can
 * slip through. That is the honest limit of a non-LLM install-time scan.
 */
public class SkillSecurityScanner {

    /**
     * Normalized prompt-injection phrases (lowercase, alphanumerics only). A skill whose <em>own</em>
     * prompt carries one of these is trying to hijack the agent that loads it — the "skill as an
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

    /** Scans a skill-pack and returns its findings, verdict, and recommended load tier. */
    public ScanReport scan(SkillPack pack) {
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
    private List<String> allPrompts(SkillPack pack) {
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
            String normalized = normalize(prompt);
            for (String pattern : INJECTION_PATTERNS) {
                if (normalized.contains(pattern)) {
                    out.add(new Finding(Severity.CRITICAL, Finding.CAT_PROMPT_INJECTION,
                            "Skill prompt contains a prompt-injection marker", pattern));
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
                            "Skill prompt references a secret or credential", marker));
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
                            "Skill prompt reaches an external network source", marker.trim()));
                    break;
                }
            }
        }
        return out;
    }

    private List<Finding> toolGroupFindings(SkillPack pack) {
        List<Finding> out = new ArrayList<>();
        for (String group : new TreeSet<>(declaredToolGroups(pack))) {
            if (HIGH_PRIVILEGE_GROUPS.contains(group)) {
                out.add(new Finding(Severity.WARN, Finding.CAT_HIGH_PRIVILEGE_TOOL,
                        "Skill declares a high-privilege tool group", group));
            }
        }
        return out;
    }

    /**
     * Flags the injection-to-execution chain: untrusted {@code UserInput} feeding a step that also
     * declares the {@code shell} tool group (doc 02 §5.3 — "a benign approval can carry over to
     * harmful actions").
     */
    private Finding untrustedInputToShellFinding(SkillPack pack) {
        boolean readsUserInput = pack.steps().stream()
                .anyMatch(step -> step.inputTypeNames().contains(SampleSkillPacks.USER_INPUT));
        boolean hasShell = declaredToolGroups(pack).contains("shell");
        if (readsUserInput && hasShell) {
            return new Finding(Severity.WARN, Finding.CAT_UNTRUSTED_INPUT_TO_SHELL,
                    "Untrusted user input reaches a shell tool group", "UserInput -> shell");
        }
        return null;
    }

    private List<String> declaredToolGroups(SkillPack pack) {
        List<String> groups = new ArrayList<>();
        pack.steps().forEach(step -> groups.addAll(step.toolGroups()));
        return groups;
    }

    /** The lowest tier whose allowlist covers every tool group the skill declares. */
    private TrustTier minimumTier(SkillPack pack) {
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

    /** Lowercase and strip every non-alphanumeric character, so obfuscation is neutralized. */
    private static String normalize(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
