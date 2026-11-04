package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * The result of scanning a skill-pack (POC 21): the aggregated verdict, the recommended minimum
 * trust tier, a one-line summary, and the ordered findings. {@link #error} is non-null when the
 * request could not be scanned (no skill selected, or an unknown skill).
 */
public record SecurityResponse(
        String error,
        String skillName,
        String verdict,
        String recommendedTier,
        String summary,
        List<SecurityFinding> findings) {

    public static SecurityResponse error(String error) {
        return new SecurityResponse(error, null, null, null, null, List.of());
    }

    public static SecurityResponse ok(String skillName, String verdict, String tier, String summary,
                                     List<SecurityFinding> findings) {
        return new SecurityResponse(null, skillName, verdict, tier, summary, findings);
    }
}
