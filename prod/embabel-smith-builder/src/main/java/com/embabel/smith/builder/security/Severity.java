package com.embabel.smith.builder.security;

/**
 * The severity of a single security finding. A {@link Severity#CRITICAL} finding means the skill
 * should not be published at all (verdict {@link ScanVerdict#BLOCK}); a {@link Severity#WARN}
 * finding means it may publish under scrutiny (verdict {@link ScanVerdict#REVIEW}).
 */
public enum Severity {
    INFO,
    WARN,
    CRITICAL
}
