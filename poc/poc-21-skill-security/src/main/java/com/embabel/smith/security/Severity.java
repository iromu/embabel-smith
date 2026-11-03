package com.embabel.smith.security;

/**
 * The severity of a single security finding. A {@link Severity#CRITICAL} finding means the skill
 * should not be loaded at all (verdict {@link ScanVerdict#BLOCK}); a {@link Severity#WARN} finding
 * means it may load under scrutiny at a higher trust tier (verdict {@link ScanVerdict#REVIEW}).
 */
public enum Severity {
    INFO,
    WARN,
    CRITICAL
}
