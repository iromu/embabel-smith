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

package com.embabel.smith.builder.publish;

/**
 * The destination a build is published to, parsed from the {@code --out} source URI:
 * {@code folder:<abs-path>} or {@code git:<url>[@<ref>]}. The git ref (branch) is
 * optional; when absent the repository's default branch is used.
 *
 * @param type the destination kind (folder or git)
 * @param location the absolute folder path or git repository URL
 * @param ref the git branch to publish to, or null when the default branch is used
 * @author Ivan Rodriguez
 */
public record PublishTarget(Type type, String location, String ref) {

	public enum Type {

		/** Publish to a local folder. */
		FOLDER,

		/** Publish to a git repository. */
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
