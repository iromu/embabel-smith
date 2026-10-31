package com.embabel.smith.common.validation;

import java.util.List;

/**
 * The outcome of validating a skill-pack: the full list of findings. An empty list means the
 * pack is well-formed and eligible to be written / ingested.
 */
public record ValidationResult(List<ValidationIssue> issues) {

    public ValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean isValid() {
        return issues.isEmpty();
    }

    public List<ValidationIssue> issues() {
        return issues;
    }

    public List<ValidationIssue> issuesWithCode(String code) {
        return issues.stream().filter(i -> i.code().equals(code)).toList();
    }
}
