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

package com.embabel.smith.studio.rest;

import java.nio.file.Path;

import com.embabel.smith.specsource.FolderSpecSource;
import com.embabel.smith.specsource.GitSpecSource;
import com.embabel.smith.specsource.SpecSource;

/**
 * Parses a studio source URI into a {@link SpecSource}, honouring the studio's configured
 * git source cache root (the {@code smith.studio.source-cache-dir} property). Mirrors
 * {@link SpecSource#parse(String)} but threads the cache root through to
 * {@link GitSpecSource}.
 *
 * @author Ivan Rodriguez
 */
public final class Sources {

	private Sources() {
	}

	/**
	 * Parses a studio source URI into a {@link SpecSource}, honouring the configured
	 * git source cache root.
	 * @param uri the source URI to parse.
	 * @param cacheRoot the git source cache root to thread through.
	 * @return the parsed source.
	 */
	public static SpecSource parse(String uri, Path cacheRoot) {
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
			case "git" -> parseGit(uri, rest, cacheRoot);
			default ->
				throw new IllegalArgumentException("spec source URI '" + uri + "' has unknown scheme '" + scheme + "'");
		};
	}

	private static SpecSource parseGit(String uri, String rest, Path cacheRoot) {
		int at = rest.lastIndexOf('@');
		if (at < 0) {
			return new GitSpecSource(uri, rest, null, cacheRoot);
		}
		String url = rest.substring(0, at);
		String ref = rest.substring(at + 1);
		if (url.isBlank() || ref.isBlank()) {
			throw new IllegalArgumentException("spec source URI '" + uri + "' is malformed around '@'");
		}
		return new GitSpecSource(uri, url, ref, cacheRoot);
	}

	/**
	 * The URI scheme as a short kind label ({@code folder} or {@code git}).
	 * @param uri the source URI to classify.
	 * @return the scheme as a kind label, or {@code unknown} when the URI has none.
	 */
	static String kind(String uri) {
		int colon = uri.indexOf(':');
		return (colon > 0) ? uri.substring(0, colon) : "unknown";
	}

}
