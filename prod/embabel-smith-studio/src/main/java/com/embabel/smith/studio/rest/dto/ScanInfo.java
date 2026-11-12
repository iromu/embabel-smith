package com.embabel.smith.studio.rest.dto;

import java.util.List;

/** The security scan of a agent spec (mirrors {@code ScanReport}). */
public record ScanInfo(String specName, List<SecurityFinding> findings, String verdict,
                       String recommendedTier, String summary) {

    public static ScanInfo of(com.embabel.smith.builder.security.ScanReport report) {
        List<SecurityFinding> findings = report.findings().stream()
                .map(f -> new SecurityFinding(f.severity().name(), f.category(), f.message(), f.evidence()))
                .toList();
        return new ScanInfo(report.specName(), findings, report.verdict().name(),
                report.recommendedTier() == null ? null : report.recommendedTier().name(), report.summary());
    }
}
