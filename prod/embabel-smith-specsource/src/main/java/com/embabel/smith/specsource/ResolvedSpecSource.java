package com.embabel.smith.specsource;

import java.nio.file.Path;
import java.util.List;

/**
 * The outcome of resolving a {@link SpecSource}: where the source materialized, the agent specs
 * found in it, and any non-fatal warnings encountered along the way.
 *
 * <p>{@code packs} holds only packs that passed agent spec structure validation; candidates that
 * failed validation are excluded and named in {@code warnings} instead.
 */
public record ResolvedSpecSource(Origin origin, List<ResolvedSpec> packs, List<String> warnings) {

    public ResolvedSpecSource {
        packs = packs == null ? List.of() : List.copyOf(packs);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** Where a source came from and where it materialized on this machine. */
    public record Origin(String uri, Path root) {
    }
}
