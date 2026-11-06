package com.embabel.smith.builder.publish;

/**
 * The destination a build is published to, parsed from the {@code --out} source URI:
 * {@code folder:<abs-path>} or {@code git:<url>[@<ref>]}. The git ref (branch) is optional; when
 * absent the repository's default branch is used.
 */
public record PublishTarget(Type type, String location, String ref) {

    public enum Type {
        FOLDER,
        GIT
    }

    public static PublishTarget parse(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("source URI is empty (expected folder:<abs-path> or git:<url>)");
        }
        if (uri.startsWith("folder:")) {
            String path = uri.substring("folder:".length());
            if (path.isBlank()) {
                throw new IllegalArgumentException("folder source URI has no path: " + uri);
            }
            return new PublishTarget(Type.FOLDER, path, null);
        }
        if (uri.startsWith("git:")) {
            String rest = uri.substring("git:".length());
            if (rest.isBlank()) {
                throw new IllegalArgumentException("git source URI has no url: " + uri);
            }
            int at = rest.lastIndexOf('@');
            if (at > 0) {
                return new PublishTarget(Type.GIT, rest.substring(0, at), rest.substring(at + 1));
            }
            return new PublishTarget(Type.GIT, rest, null);
        }
        throw new IllegalArgumentException("Unknown source URI scheme: " + uri + " (expected folder: or git:)");
    }
}
