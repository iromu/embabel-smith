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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Computes a agent spec's content hash: SHA-256 over the sorted relative paths and bytes
 * of the pack's format-owned files — {@code manifest.yml}, {@code goal.yml}, and the YAML
 * files under {@code steps/} and {@code types/} (the same files {@code smith-common}
 * reads; everything else is ignored).
 *
 * <p>
 * The hash is stable for identical content and changes when any format-owned file changes
 * — the quantity POC 18's drift detection consumes.
 *
 * @author Ivan Rodriguez
 */
public final class PackContentHasher {

	private PackContentHasher() {
	}

	public static String hash(Path packRoot) {
		MessageDigest digest = sha256();
		for (Path file : formatFiles(packRoot)) {
			digest.update(packRoot.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			digest.update(readBytes(file));
			digest.update((byte) 0);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static List<Path> formatFiles(Path packRoot) {
		List<Path> files = new ArrayList<>();
		for (String name : List.of("manifest.yml", "goal.yml")) {
			Path file = packRoot.resolve(name);
			if (Files.isRegularFile(file)) {
				files.add(file);
			}
		}
		for (String dir : List.of("steps", "types")) {
			Path dirPath = packRoot.resolve(dir);
			if (!Files.isDirectory(dirPath)) {
				continue;
			}
			try (var stream = Files.list(dirPath)) {
				stream.filter(Files::isRegularFile).filter(PackContentHasher::isYaml).forEach(files::add);
			}
			catch (IOException ex) {
				throw new UncheckedIOException("cannot list " + dirPath, ex);
			}
		}
		files.sort(Comparator.comparing((f) -> packRoot.relativize(f).toString()));
		return files;
	}

	private static boolean isYaml(Path file) {
		String name = file.getFileName().toString();
		return name.endsWith(".yml") || name.endsWith(".yaml");
	}

	private static byte[] readBytes(Path file) {
		try {
			return Files.readAllBytes(file);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot read " + file, ex);
		}
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

}
