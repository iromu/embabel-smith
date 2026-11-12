package com.embabel.smith.security;

/**
 * A single security finding from scanning a agent spec: how serious it is, what class of risk it
 * is, a human-readable message, and the concrete evidence (the offending text or tool group).
 */
public record Finding(Severity severity, String category, String message, String evidence) {

    /** Prompt-injection text in the spec's own prompt (doc 02 §5.3 — the spec as an attack vector). */
    public static final String CAT_PROMPT_INJECTION = "PROMPT_INJECTION";

    /** The spec declares a high-privilege tool group (e.g. {@code shell}). */
    public static final String CAT_HIGH_PRIVILEGE_TOOL = "HIGH_PRIVILEGE_TOOL";

    /** The spec's prompt reaches an external network source (URL / fetch command). */
    public static final String CAT_NETWORK_REFERENCE = "NETWORK_REFERENCE";

    /** The spec's prompt asks to read or exfiltrate secrets (API keys, tokens, dotfiles). */
    public static final String CAT_SECRET_EXFIL = "SECRET_EXFIL";

    /** Untrusted user input reaches a {@code shell} tool group (the injection-to-execution chain). */
    public static final String CAT_UNTRUSTED_INPUT_TO_SHELL = "UNTRUSTED_INPUT_TO_SHELL";

    public boolean isCritical() {
        return severity == Severity.CRITICAL;
    }

    public boolean isWarning() {
        return severity == Severity.WARN;
    }
}
