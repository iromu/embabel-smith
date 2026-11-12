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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationIssue;
import com.embabel.smith.common.validation.ValidationResult;

/**
 * A spec source rooted at a local directory. The root is a *library* of packs: each
 * immediate subdirectory holding a {@code manifest.yml} is a pack candidate and must pass
 * {@link AgentSpecValidator} structure validation to be included.
 *
 * <p>
 * Deeper nesting is not scanned. Candidates that fail validation are excluded and named
 * in the result's warnings; a root with no valid packs resolves to an empty pack set plus
 * a warning (not an error).
 *
 * @author Ivan Rodriguez
 */
public final class FolderSpecSource implements SpecSource {

	private final String uri;

	private final Path root;

	private final AgentSpecValidator validator = new AgentSpecValidator();

	private final AgentSpecReader reader = new AgentSpecReader();

	public FolderSpecSource(String uri, Path root) {
		this.uri = uri;
		this.root = root;
	}

	/** The directory this source scans.
	 * @return the directory this source scans
	 */
	public Path root() {
		return this.root;
	}

	@Override
	public ResolvedSpecSource resolve() {
		if (!Files.isDirectory(this.root)) {
			throw new SpecSourceException(
					"folder source root does not exist or is not a directory: " + this.root + " (uri: " + this.uri + ")");
		}
		List<ResolvedSpec> packs = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		for (Path candidate : subdirectories(this.root)) {
			if (!Files.isRegularFile(candidate.resolve("manifest.yml"))) {
				continue;
			}
			ValidationResult result = this.validator.validate(candidate);
			if (result.isValid()) {
				packs.add(new ResolvedSpec(candidate, readName(candidate), PackContentHasher.hash(candidate), true));
			}
			else {
				warnings.add("pack '" + candidate.getFileName() + "' failed validation: "
						+ result.issues().stream().map(ValidationIssue::message).toList());
			}
		}
		if (packs.isEmpty()) {
			warnings.add("no valid agent specs found in folder source " + this.root);
		}
		return new ResolvedSpecSource(new ResolvedSpecSource.Origin(this.uri, this.root), packs, warnings);
	}

	private List<Path> subdirectories(Path dir) {
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(Files::isDirectory).sorted().toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot list " + dir, ex);
		}
	}

	private String readName(Path candidate) {
		try {
			return this.reader.read(candidate).manifest().name();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot read manifest of validated pack " + candidate, ex);
		}
	}

}
