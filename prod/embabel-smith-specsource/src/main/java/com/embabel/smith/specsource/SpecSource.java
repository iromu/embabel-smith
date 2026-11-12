/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.specsource;

import java.nio.file.Path;

/**
 * A source of agent specs: a local folder or a git repository, addressable as a source
 * URI.
 *
 * <p>
 * URI grammar:
 * <ul>
 * <li>{@code folder:<absolute-or-relative-path>} — a directory whose immediate
 * subdirectories are agent specs;</li>
 * <li>{@code git:<local-path-or-remote-url>[@<ref>]} — a git repository, optionally
 * pinned to a branch, tag, or commit SHA (defaults to the repository's default
 * branch).</li>
 * </ul>
 *
 * @author Ivan Rodriguez
 */
public interface SpecSource {

	/** Resolves this source into the agent specs it currently contains.
	 * @return the resolved source contents
	 */
	ResolvedSpecSource resolve();

	/**
	 * Parses a source URI into a {@link SpecSource}.
	 * @param uri the source URI to parse
	 * @return the parsed spec source
	 * @throws IllegalArgumentException naming the offending URI when the scheme is
	 * unknown or the URI is empty or malformed
	 */
	static SpecSource parse(String uri) {
		if (uri == null || uri.isBlank()) {
			throw new IllegalArgumentException("spec source URI is empty");
		}
		int colon = uri.indexOf(':');
		if (colon <= 0) {
			throw new IllegalArgumentException("spec source URI '" + uri + "' has no scheme");
		}
		String scheme = uri.substring(0, colon);
		String rest = uri.substring(colon + 1);
		if (rest.isBlank()) {
			throw new IllegalArgumentException("spec source URI '" + uri + "' has no path");
		}
		return switch (scheme) {
			case "folder" -> new FolderSpecSource(uri, Path.of(rest));
			case "git" -> parseGit(uri, rest);
			default ->
				throw new IllegalArgumentException("spec source URI '" + uri + "' has unknown scheme '" + scheme + "'");
		};
	}

	private static SpecSource parseGit(String uri, String rest) {
		int at = rest.lastIndexOf('@');
		if (at < 0) {
			return new GitSpecSource(uri, rest, null);
		}
		String url = rest.substring(0, at);
		String ref = rest.substring(at + 1);
		if (url.isBlank() || ref.isBlank()) {
			throw new IllegalArgumentException("spec source URI '" + uri + "' is malformed around '@'");
		}
		return new GitSpecSource(uri, url, ref);
	}

}
