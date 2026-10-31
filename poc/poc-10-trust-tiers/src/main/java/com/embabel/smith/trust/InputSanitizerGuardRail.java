package com.embabel.smith.trust;

import com.embabel.agent.api.validation.guardrails.UserInputGuardRail;
import com.embabel.agent.core.Blackboard;
import com.embabel.common.core.validation.ValidationError;
import com.embabel.common.core.validation.ValidationResult;
import com.embabel.common.core.validation.ValidationSeverity;

import java.util.List;
import java.util.Locale;

/**
 * A global user-input guardrail (doc 02 caveat #3 — the security half). Blocks prompt-injection
 * attempts with {@link ValidationSeverity#CRITICAL}, which stops the LLM operation before it runs.
 * Registered globally via {@code embabel.agent.guardrails.user-input} in {@code application.yml}.
 *
 * <p>Matching is <em>normalized</em> (lowercased, punctuation and whitespace stripped) against a set
 * of known injection phrases, so it catches obfuscated variants — extra spacing, punctuation, mixed
 * case — that a plain substring match misses (docs/04 caveat #5). It is still a pattern matcher, not
 * a semantic detector: a novel rephrasing outside the pattern set can slip through.
 */
public class InputSanitizerGuardRail implements UserInputGuardRail {

    /** The legacy single marker, retained for reference; the matcher now uses {@link #FORBIDDEN_PATTERNS}. */
    public static final String FORBIDDEN_MARKER = "ignore previous instructions";

    /**
     * Normalized injection phrases (lowercase, alphanumerics only). Each is matched against the
     * normalized input, so spacing/punctuation obfuscation does not carry an injection through.
     */
    private static final List<String> FORBIDDEN_PATTERNS = List.of(
            "ignorepreviousinstructions",
            "ignoreallprevious",
            "disregardpreviousinstructions",
            "disregardtheabove",
            "overrideyourinstructions",
            "revealyoursystemprompt",
            "printyourinstructions",
            "youarenow");

    @Override
    public String getName() {
        return "input-sanitizer";
    }

    @Override
    public String getDescription() {
        return "Blocks prompt-injection attempts in user input";
    }

    /** Lowercase and strip every non-alphanumeric character, so obfuscation is neutralized. */
    public static String normalize(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @Override
    public ValidationResult validate(String input, Blackboard blackboard) {
        if (input == null) {
            return new ValidationResult(true, List.of());
        }
        String normalized = normalize(input);
        for (String pattern : FORBIDDEN_PATTERNS) {
            if (normalized.contains(pattern)) {
                return new ValidationResult(false, List.of(
                        new ValidationError("prompt-injection",
                                "Input contains a prompt-injection attempt and is blocked",
                                ValidationSeverity.CRITICAL)));
            }
        }
        return new ValidationResult(true, List.of());
    }
}
