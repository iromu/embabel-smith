package com.embabel.smith.common.validation;

/**
 * A single validation finding.
 *
 * @param code     a stable, machine-readable category (e.g. {@code DANGLING_TYPE_REF})
 * @param message  a human-readable explanation; for reference errors it names the offending value
 * @param location the file or step the issue was found in, or {@code null} if pack-wide
 */
public record ValidationIssue(String code, String message, String location) {

    public static ValidationIssue of(String code, String message) {
        return new ValidationIssue(code, message, null);
    }

    public static ValidationIssue of(String code, String message, String location) {
        return new ValidationIssue(code, message, location);
    }
}
