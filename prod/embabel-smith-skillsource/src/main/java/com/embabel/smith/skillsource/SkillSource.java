package com.embabel.smith.skillsource;

import java.nio.file.Path;

/**
 * A source of skill-packs: a local folder or a git repository, addressable as a source URI.
 *
 * <p>URI grammar:
 * <ul>
 *   <li>{@code folder:<absolute-or-relative-path>} — a directory whose immediate subdirectories
 *       are skill-packs;</li>
 *   <li>{@code git:<local-path-or-remote-url>[@<ref>]} — a git repository, optionally pinned to a
 *       branch, tag, or commit SHA (defaults to the repository's default branch).</li>
 * </ul>
 */
public interface SkillSource {

    /** Resolves this source into the skill-packs it currently contains. */
    ResolvedSkillSource resolve();

    /**
     * Parses a source URI into a {@link SkillSource}.
     *
     * @throws IllegalArgumentException naming the offending URI when the scheme is unknown or the
     *         URI is empty or malformed
     */
    static SkillSource parse(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("skill source URI is empty");
        }
        int colon = uri.indexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("skill source URI '" + uri + "' has no scheme");
        }
        String scheme = uri.substring(0, colon);
        String rest = uri.substring(colon + 1);
        if (rest.isBlank()) {
            throw new IllegalArgumentException("skill source URI '" + uri + "' has no path");
        }
        return switch (scheme) {
            case "folder" -> new FolderSkillSource(uri, Path.of(rest));
            case "git" -> parseGit(uri, rest);
            default -> throw new IllegalArgumentException(
                    "skill source URI '" + uri + "' has unknown scheme '" + scheme + "'");
        };
    }

    private static SkillSource parseGit(String uri, String rest) {
        int at = rest.lastIndexOf('@');
        if (at < 0) {
            return new GitSkillSource(uri, rest, null);
        }
        String url = rest.substring(0, at);
        String ref = rest.substring(at + 1);
        if (url.isBlank() || ref.isBlank()) {
            throw new IllegalArgumentException("skill source URI '" + uri + "' is malformed around '@'");
        }
        return new GitSkillSource(uri, url, ref);
    }
}
