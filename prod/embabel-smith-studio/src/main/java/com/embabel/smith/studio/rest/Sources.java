package com.embabel.smith.studio.rest;

import com.embabel.smith.skillsource.FolderSkillSource;
import com.embabel.smith.skillsource.GitSkillSource;
import com.embabel.smith.skillsource.SkillSource;

import java.nio.file.Path;

/**
 * Parses a studio source URI into a {@link SkillSource}, honouring the studio's configured git
 * source cache root (the {@code smith.studio.source-cache-dir} property). Mirrors
 * {@link SkillSource#parse(String)} but threads the cache root through to {@link GitSkillSource}.
 */
public final class Sources {

    private Sources() {
    }

    public static SkillSource parse(String uri, Path cacheRoot) {
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
            case "git" -> parseGit(uri, rest, cacheRoot);
            default -> throw new IllegalArgumentException(
                    "skill source URI '" + uri + "' has unknown scheme '" + scheme + "'");
        };
    }

    private static SkillSource parseGit(String uri, String rest, Path cacheRoot) {
        int at = rest.lastIndexOf('@');
        if (at < 0) {
            return new GitSkillSource(uri, rest, null, cacheRoot);
        }
        String url = rest.substring(0, at);
        String ref = rest.substring(at + 1);
        if (url.isBlank() || ref.isBlank()) {
            throw new IllegalArgumentException("skill source URI '" + uri + "' is malformed around '@'");
        }
        return new GitSkillSource(uri, url, ref, cacheRoot);
    }

    /** The URI scheme as a short kind label ({@code folder} or {@code git}). */
    static String kind(String uri) {
        int colon = uri.indexOf(':');
        return colon > 0 ? uri.substring(0, colon) : "unknown";
    }
}
