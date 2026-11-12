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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.SpecContentHash;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.specsource.GitSpecSource;
import com.embabel.smith.specsource.ResolvedSpec;
import com.embabel.smith.specsource.ResolvedSpecSource;
import com.embabel.smith.specsource.SpecSource;
import com.embabel.smith.studio.rest.dto.PackInfo;
import com.embabel.smith.studio.rest.dto.SourceInfo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The studio's view over its spec sources (change 06): lists the configured sources,
 * lists the packs of a source with their lifecycle state and content-hash drift status,
 * and reads a named pack for the Security / Trust / Eval panes. Offline — reads from
 * disk, no model calls.
 *
 * @author Ivan Rodriguez
 */
@Service
public class SourceService {

	private final List<String> configuredSources;

	private final Path cacheRoot;

	private final AgentSpecReader reader = new AgentSpecReader();

	public SourceService(@Value("${smith.studio.sources:}") String sources,
			@Value("${smith.studio.source-cache-dir:}") String cacheDir) {
		this.configuredSources = split(sources);
		this.cacheRoot = cacheRoot(cacheDir);
	}

	/**
	 * The configured sources the studio offers, with their kind.
	 * @return the configured sources, each with its kind.
	 */
	public List<SourceInfo> listSources() {
		return this.configuredSources.stream()
			.map((uri) -> new SourceInfo(uri, Sources.kind(uri)))
			.toList();
	}

	/**
	 * The packs of a source, each with its lifecycle state, baseline, and drift status.
	 * @param sourceUri the source URI to list packs from.
	 * @return the packs of the source.
	 */
	public List<PackInfo> listPacks(String sourceUri) {
		ResolvedSpecSource resolved = resolve(sourceUri);
		List<PackInfo> packs = new ArrayList<>();
		for (ResolvedSpec pack : resolved.packs()) {
			LifecycleMetadata sidecar = readSidecar(pack.root());
			// The sidecar records the pack's semantic content hash (SpecContentHash) —
			// the same
			// scheme the builder stamps and the loop compares — so both the displayed
			// hash and the
			// drift flag are assessed on that, not on ResolvedSpec's raw file-bytes hash,
			// which
			// never matches the sidecar.
			String hash = contentHashOf(pack.root());
			boolean drift = sidecar != null && sidecar.contentHash() != null && hash != null
					&& !hash.equals(sidecar.contentHash());
			packs.add(new PackInfo(pack.name(), (hash != null) ? hash : pack.contentHash(),
					(sidecar != null) ? sidecar.state().name() : null,
					(sidecar != null) ? sidecar.baselinePassRate() : null, drift,
					(sidecar != null) ? sidecar.updatedAt() : null));
		}
		return packs;
	}

	/**
	 * The goal names a source's packs export (the Run pane's goal picker): each pack
	 * exports the goal named in its goal spec, and the run is addressed by goal, not pack
	 * name. Packs that cannot be read, or carry no goal, contribute none.
	 * @param sourceUri the source URI to list goals from.
	 * @return the exported goal names of the source's packs.
	 */
	public List<String> listGoals(String sourceUri) {
		ResolvedSpecSource resolved = resolve(sourceUri);
		List<String> goals = new ArrayList<>();
		for (ResolvedSpec pack : resolved.packs()) {
			try {
				AgentSpec read = this.reader.read(pack.root());
				if (read.goal() != null) {
					goals.add(read.goal().name());
				}
			}
			catch (IOException ex) {
				// an unreadable pack exports no goal to offer
			}
		}
		return goals;
	}

	/**
	 * Reads the named pack from a source (for the Security / Trust / Eval panes).
	 * @param sourceUri the source URI to read from.
	 * @param specName the pack name to read.
	 * @return the named pack.
	 */
	public AgentSpec readPack(String sourceUri, String specName) {
		ResolvedSpecSource resolved = resolve(sourceUri);
		for (ResolvedSpec pack : resolved.packs()) {
			if (pack.name().equals(specName)) {
				try {
					return this.reader.read(pack.root());
				}
				catch (IOException ex) {
					throw new UncheckedIOException("cannot read pack '" + specName + "'", ex);
				}
			}
		}
		throw new IllegalArgumentException("spec '" + specName + "' not found in source " + sourceUri);
	}

	private ResolvedSpecSource resolve(String sourceUri) {
		if (sourceUri == null || sourceUri.isBlank()) {
			throw new IllegalArgumentException("no source selected");
		}
		SpecSource source = Sources.parse(sourceUri, this.cacheRoot);
		return source.resolve();
	}

	/**
	 * The pack's semantic content hash, or {@code null} when the pack cannot be read.
	 * @param root the pack root directory to hash.
	 * @return the pack's semantic content hash, or {@code null} when it cannot be read.
	 */
	private String contentHashOf(Path root) {
		try {
			return SpecContentHash.of(this.reader.read(root));
		}
		catch (IOException ex) {
			return null;
		}
	}

	/**
	 * The pack's lifecycle sidecar, or {@code null} when it has none.
	 * @param root the pack root directory to read the sidecar from.
	 * @return the pack's lifecycle sidecar, or {@code null} when it has none.
	 */
	private LifecycleMetadata readSidecar(Path root) {
		Path sidecar = root.resolve("lifecycle.yml");
		if (!Files.isRegularFile(sidecar)) {
			return null;
		}
		try {
			return Yaml.mapper().readValue(Files.readString(sidecar), LifecycleMetadata.class);
		}
		catch (IOException ex) {
			return null; // a corrupt sidecar is treated as absent (drift is then not
							// reported)
		}
	}

	private static List<String> split(String sources) {
		List<String> out = new ArrayList<>();
		if (sources != null) {
			for (String s : sources.split(",")) {
				if (!s.isBlank()) {
					out.add(s.trim());
				}
			}
		}
		return List.copyOf(out);
	}

	private static Path cacheRoot(String configured) {
		if (configured == null || configured.isBlank()) {
			return GitSpecSource.defaultCacheRoot();
		}
		return expandHome(configured.trim());
	}

	private static Path expandHome(String p) {
		if (p.equals("~")) {
			return Path.of(System.getProperty("user.home"));
		}
		if (p.startsWith("~/")) {
			return Path.of(System.getProperty("user.home"), p.substring(2));
		}
		return Path.of(p);
	}

}
