package com.embabel.smith.skillsource;

import java.nio.file.Path;
import java.util.List;

/**
 * The outcome of resolving a {@link SkillSource}: where the source materialized, the skill-packs
 * found in it, and any non-fatal warnings encountered along the way.
 *
 * <p>{@code packs} holds only packs that passed skill-pack structure validation; candidates that
 * failed validation are excluded and named in {@code warnings} instead.
 */
public record ResolvedSkillSource(Origin origin, List<ResolvedPack> packs, List<String> warnings) {

    public ResolvedSkillSource {
        packs = packs == null ? List.of() : List.copyOf(packs);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** Where a source came from and where it materialized on this machine. */
    public record Origin(String uri, Path root) {
    }
}
