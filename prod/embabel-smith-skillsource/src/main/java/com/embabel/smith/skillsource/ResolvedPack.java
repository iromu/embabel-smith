package com.embabel.smith.skillsource;

import java.nio.file.Path;

/**
 * A single skill-pack resolved from a source.
 *
 * @param root         the directory holding the pack's format files
 * @param name         the pack name declared in its {@code manifest.yml}
 * @param contentHash  SHA-256 over the pack's format-owned files; stable for identical content
 * @param validationOk whether the pack passed skill-pack structure validation
 */
public record ResolvedPack(Path root, String name, String contentHash, boolean validationOk) {
}
