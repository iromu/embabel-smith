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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.studio.rest.dto.DiscoveryRecordDto;
import com.embabel.smith.studio.rest.dto.ImportPackRequest;
import com.embabel.smith.studio.rest.dto.ImportPackResponse;
import com.embabel.smith.studio.rest.dto.SavePackRequest;
import com.embabel.smith.studio.rest.dto.SavePackResponse;
import com.embabel.smith.studio.rest.dto.StoredPackInfo;
import com.embabel.smith.studio.rest.dto.StoredPackResponse;
import com.embabel.smith.studio.store.ProcessStore;

import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's durable store surface (change: smith-studio-postgres-storage): save,
 * list, and reload generated agent specs, review the discovery history, export a saved
 * pack as a zip, and import an agent-spec directory from the filesystem. Offline — no
 * model call.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class StoreController {

	private final ProcessStore store;

	private final AgentSpecWriter writer = new AgentSpecWriter();

	private final AgentSpecReader reader = new AgentSpecReader();

	public StoreController(ProcessStore store) {
		this.store = store;
	}

	@GetMapping("/api/store/packs")
	public List<StoredPackInfo> listPacks() {
		return this.store.listPacks()
			.stream()
			.map((p) -> new StoredPackInfo(p.name(), p.goal(), p.steps(), p.savedAt()))
			.toList();
	}

	@GetMapping("/api/store/packs/{name}")
	public StoredPackResponse loadPack(@PathVariable("name") String name) {
		AgentSpec pack = this.store.loadPack(name);
		if (pack == null) {
			return StoredPackResponse.failed("No saved pack named '" + name + "'.");
		}
		return StoredPackResponse.of(pack);
	}

	@PostMapping("/api/store/packs")
	public SavePackResponse savePack(@RequestBody SavePackRequest request) {
		AgentSpec pack = request.pack();
		if (pack == null || pack.name() == null || pack.name().isBlank()) {
			return SavePackResponse.failed("No pack to save.");
		}
		try {
			return SavePackResponse.of(this.store.savePack(pack).name());
		}
		catch (RuntimeException ex) {
			return SavePackResponse.failed((ex.getMessage() == null) ? ex.toString() : ex.getMessage());
		}
	}

	/**
	 * Exports a saved pack as a zip in the standard on-disk agent-spec layout (change:
	 * smith-studio-postgres-storage): the stored content is materialized to a temp
	 * directory with the shared {@link AgentSpecWriter} and zipped on demand. An unknown
	 * pack is a 404 with a clear error.
	 * @param name the saved pack to export.
	 * @return the zip bytes as an octet-stream response.
	 */
	@GetMapping("/api/store/packs/{name}/download")
	public ResponseEntity<byte[]> downloadPack(@PathVariable("name") String name) {
		AgentSpec pack;
		try {
			pack = this.store.loadPack(name);
		}
		catch (RuntimeException ex) {
			return errorResponse(ex);
		}
		if (pack == null) {
			return ResponseEntity.notFound().build();
		}
		try {
			byte[] zip = zipPack(pack);
			ContentDisposition disposition = ContentDisposition.attachment().filename(name + ".zip").build();
			return ResponseEntity.ok()
				.header("Content-Disposition", disposition.toString())
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.body(zip);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to export pack '" + name + "': " + ex.getMessage(), ex);
		}
	}

	/**
	 * Imports an agent-spec directory from the local filesystem into the storage database
	 * (change: smith-studio-postgres-storage). An invalid path fails with a clear reason
	 * and stores nothing.
	 * @param request the import request carrying the directory path.
	 * @return the import result.
	 */
	@PostMapping("/api/store/packs/import")
	public ImportPackResponse importPack(@RequestBody ImportPackRequest request) {
		String path = request.path();
		if (path == null || path.isBlank()) {
			return ImportPackResponse.failed("No path given.");
		}
		Path dir = Path.of(expandHome(path.trim()));
		if (!Files.isRegularFile(dir.resolve("manifest.yml"))) {
			return ImportPackResponse.failed("Not a valid agent-spec directory (no manifest.yml): " + path);
		}
		AgentSpec pack;
		try {
			pack = this.reader.read(dir);
		}
		catch (IOException ex) {
			return ImportPackResponse.failed("Failed to read agent-spec directory: " + ex.getMessage());
		}
		if (pack.name() == null || pack.name().isBlank()) {
			return ImportPackResponse.failed("The agent-spec directory has no pack name.");
		}
		try {
			return ImportPackResponse.of(this.store.savePack(pack).name());
		}
		catch (RuntimeException ex) {
			return ImportPackResponse.failed((ex.getMessage() == null) ? ex.toString() : ex.getMessage());
		}
	}

	@GetMapping("/api/store/discoveries")
	public List<DiscoveryRecordDto> discoveries() {
		return this.store.discoveries()
			.stream()
			.map((d) -> new DiscoveryRecordDto(d.timestamp(), d.sourceType(), d.location(), d.docCount()))
			.toList();
	}

	private ResponseEntity<byte[]> errorResponse(RuntimeException ex) {
		// A "storage not configured" error is surfaced as a 503 with the message; other
		// errors as 500.
		int status = (ex instanceof com.embabel.smith.studio.storage.StorageNotConfiguredException) ? 503 : 500;
		return ResponseEntity.status(status).body(null);
	}

	/**
	 * Materializes the pack to a temp directory and zips its contents.
	 * @param pack the pack to export.
	 * @return the zip bytes in the standard on-disk agent-spec layout.
	 */
	private byte[] zipPack(AgentSpec pack) throws IOException {
		Path tmp = Files.createTempDirectory("smith-export-");
		try {
			this.writer.write(pack, tmp);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (ZipOutputStream zip = new ZipOutputStream(out)) {
				try (var files = Files.walk(tmp)) {
					for (Path file : files.filter(Files::isRegularFile).toList()) {
						String entryName = tmp.relativize(file).toString().replace('\\', '/');
						zip.putNextEntry(new ZipEntry(entryName));
						zip.write(Files.readAllBytes(file));
						zip.closeEntry();
					}
				}
			}
			return out.toByteArray();
		}
		finally {
			deleteTree(tmp);
		}
	}

	private static void deleteTree(Path root) {
		try (var files = Files.walk(root)) {
			files.sorted(Comparator.reverseOrder()).forEach((p) -> {
				try {
					Files.deleteIfExists(p);
				}
				catch (IOException ignored) {
					// Best-effort cleanup of the temp export directory.
				}
			});
		}
		catch (IOException ignored) {
			// Best-effort cleanup.
		}
	}

	private static String expandHome(String p) {
		if (p.equals("~")) {
			return System.getProperty("user.home");
		}
		if (p.startsWith("~/")) {
			return System.getProperty("user.home") + p.substring(1);
		}
		return p;
	}

}
