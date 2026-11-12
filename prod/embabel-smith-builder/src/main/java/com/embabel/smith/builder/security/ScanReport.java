package com.embabel.smith.builder.security;

import java.util.List;

/**
 * The result of scanning one agent spec: the ordered findings, the aggregated verdict, and the
 * minimum trust tier at which the spec's declared tool groups are all permitted.
 */
public record ScanReport(String specName, List<Finding> findings, ScanVerdict verdict, TrustTier recommendedTier) {

    public ScanReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public boolean hasCritical() {
        return findings.stream().anyMatch(Finding::isCritical);
    }

    public boolean hasWarning() {
        return findings.stream().anyMatch(Finding::isWarning);
    }

    public List<Finding> findingsIn(String category) {
        return findings.stream().filter(f -> f.category().equals(category)).toList();
    }

    /** One-line human summary, e.g. {@code BLOCK (2 critical, 1 warning) at T3_PARTNER}. */
    public String summary() {
        long critical = findings.stream().filter(Finding::isCritical).count();
        long warning = findings.stream().filter(Finding::isWarning).count();
        return verdict + " (" + critical + " critical, " + warning + " warning) at " + recommendedTier;
    }
}
