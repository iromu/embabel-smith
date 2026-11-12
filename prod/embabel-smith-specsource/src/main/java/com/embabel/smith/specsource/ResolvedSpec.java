package com.embabel.smith.specsource;

import java.nio.file.Path;

/**
 * A single agent spec resolved from a source.
 *
 * @param root         the directory holding the pack's format files
 * @param name         the pack name declared in its {@code manifest.yml}
 * @param contentHash  SHA-256 over the pack's format-owned files; stable for identical content
 * @param validationOk whether the pack passed agent spec structure validation
 */
public record ResolvedSpec(Path root, String name, String contentHash, boolean validationOk) {
}
