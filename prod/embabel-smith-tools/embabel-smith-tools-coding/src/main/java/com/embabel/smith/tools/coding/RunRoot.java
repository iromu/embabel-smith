/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The one directory every tool this addon publishes is confined to, and the check that
 * keeps it there.
 *
 * <p>
 * <b>The root is a boot-time value, not a per-run one.</b> The platform resolves tools
 * from beans, so a tool instance is shared by every run in the process; a root a run
 * could rewrite would be mutable shared state in a process the studio also embeds. So the
 * root arrives as {@code smith.tools.coding.root}, defaulting to the process's own
 * working directory, and is fixed when the group is built.
 *
 * <p>
 * <b>Why the check is here at all, when upstream already has one.</b>
 * {@code FileUtilsKt.resolvePath} — which both file interfaces call — compares a
 * normalized path against the root and throws {@code SecurityException} on a traversal,
 * so {@code ../etc/passwd} and an absolute path outside the root are refused for free.
 * That comparison is <em>lexical</em>: it never follows a link. Measured on 1.5.4 against
 * a root containing a symlink to a directory outside it, {@code readFile("link/secret")}
 * returned the outside file's contents, {@code listFiles("link")} listed it, and
 * {@code delete("link/secret")} removed a file that was never inside the root. So
 * containment is decided here as well, by comparing real paths, and the addon publishes
 * no path-taking tool that skips it — see {@link ConfiningTool}.
 *
 * <p>
 * A refusal always names the requested path and the root, because "the run cannot reach
 * that" and "that does not exist" call for different fixes and an operator reading only
 * the tool result has to be able to tell them apart.
 *
 * @author Ivan Rodriguez
 */
public final class RunRoot {

	/** The root as configured: absolute, lexically normalized, links unfollowed. */
	private final Path declared;

	/**
	 * The root with links followed, which is what a candidate's real path is compared to.
	 */
	private final Path real;

	private RunRoot(Path declared, Path real) {
		this.declared = declared;
		this.real = real;
	}

	/**
	 * The configured root, or the process's working directory when nothing was
	 * configured.
	 * @param configuredRoot the value of {@code smith.tools.coding.root}, blank or null
	 * to mean the working directory
	 * @return the root to confine to
	 * @throws IllegalStateException when the configured value is not a usable directory
	 * path
	 */
	public static RunRoot of(String configuredRoot) {
		String value = (configuredRoot != null) ? configuredRoot.trim() : "";
		Path base = value.isEmpty() ? Paths.get(System.getProperty("user.dir")) : Paths.get(value);
		try {
			Path declared = base.toAbsolutePath().normalize();
			return new RunRoot(declared, realPath(declared));
		}
		catch (IllegalArgumentException | UnsupportedOperationException ex) {
			throw new IllegalStateException(
					"smith.tools.coding.root='" + configuredRoot + "' is not a usable run root: " + ex.getMessage(),
					ex);
		}
	}

	/**
	 * The root as an operator wrote it, which is the string a refusal quotes.
	 * @return the absolute, normalized root
	 */
	public Path path() {
		return this.declared;
	}

	/**
	 * The root with its own links followed.
	 * @return the real root, or the declared root when it does not exist yet
	 */
	public Path realPath() {
		return this.real;
	}

	/**
	 * Where a tool's path argument points, refusing anything that lands outside the root.
	 * <p>
	 * Two comparisons, not one. The first is the lexical one upstream also makes, which
	 * catches {@code ../} and an absolute path. The second resolves the candidate through
	 * the filesystem — the deepest existing ancestor of the candidate, since the
	 * candidate itself need not exist for a write to be aimed at it — and refuses a
	 * candidate whose real path sits outside the real root. That second half is the
	 * symlink case: a link <em>inside</em> the root pointing outside it passes the
	 * lexical check and fails this one.
	 * @param path the path as the caller named it; blank or null means the root itself
	 * @return the resolved path, inside the root
	 * @throws EscapesRootException when it resolves outside the root, naming path and
	 * root
	 */
	public Path resolve(String path) {
		String requested = (path != null) ? path.trim() : "";
		Path candidate;
		try {
			candidate = requested.isEmpty() ? this.declared : this.declared.resolve(requested).normalize();
		}
		catch (InvalidPathException ex) {
			throw new EscapesRootException(refusal(requested, "it is not a valid path: " + ex.getReason()));
		}
		if (!candidate.startsWith(this.declared)) {
			throw new EscapesRootException(refusal(requested, "it resolves to " + candidate + " outside the root"));
		}
		Path resolved = realPath(candidate);
		if (!resolved.startsWith(this.real)) {
			throw new EscapesRootException(
					refusal(requested, "it resolves through a link to " + resolved + ", which is outside the root"));
		}
		return candidate;
	}

	/**
	 * The refusal text, always carrying both the path that was asked for and the root, so
	 * a reader can tell confinement from an ordinary failure.
	 * @param requested the path as it was named
	 * @param reason why it is refused
	 * @return the refusal
	 */
	public String refusal(String requested, String reason) {
		return "refusing '" + requested + "': " + reason + " [path=" + requested + ", root=" + this.declared + "]";
	}

	@Override
	public String toString() {
		return this.declared.toString();
	}

	/**
	 * The candidate's real path, following links as far as the filesystem has something
	 * to follow. For a path that does not exist yet — the target of a create — the
	 * nearest existing ancestor is resolved and the remainder appended, because it is
	 * that ancestor's links a later write would be written through.
	 * @param candidate the lexically resolved candidate
	 * @return the real path, or the candidate when nothing on it exists to follow
	 */
	private static Path realPath(Path candidate) {
		Path existing = candidate;
		StringBuilder pending = new StringBuilder();
		while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
			if (pending.length() > 0) {
				pending.insert(0, '/');
			}
			pending.insert(0, existing.getFileName());
			existing = existing.getParent();
		}
		if (existing == null) {
			return candidate;
		}
		try {
			Path anchor = existing.toRealPath();
			return (pending.length() != 0) ? anchor.resolve(pending.toString()).normalize() : anchor;
		}
		catch (java.io.IOException ex) {
			return candidate;
		}
	}

	/**
	 * A path that would take a tool outside the run root. It is a refusal rather than a
	 * failure, so the tools report it as a tool error naming the path and the root rather
	 * than letting the run die on an exception.
	 */
	public static final class EscapesRootException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		EscapesRootException(String message) {
			super(message);
		}

	}

}
