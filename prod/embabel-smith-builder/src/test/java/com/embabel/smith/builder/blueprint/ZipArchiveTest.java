/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.blueprint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests for archive ingestion: the wrapper directory a forge puts around a
 * repository is lifted away, so the paths a blueprint reports stay the repository's own,
 * and an entry named to write outside the extraction directory is refused rather than
 * written.
 */
class ZipArchiveTest {

	@TempDir
	Path archives;

	@Test
	void theWrapperDirectoryAForgeAddsIsLiftedAway() throws IOException {
		Path root = ZipArchive.extract(archive("acme-main/README.md", "DOCUMENT", "acme-main/src/main.rs", "SOURCE"));

		try {
			assertThat(Files.readString(root.resolve("README.md"))).isEqualTo("DOCUMENT");
			assertThat(Files.readString(root.resolve("src/main.rs"))).isEqualTo("SOURCE");
		}
		finally {
			BusinessProcessDiscovery.deleteQuietly(root);
		}
	}

	@Test
	void anArchiveThatWrapsNothingIsReadAsItIs() throws IOException {
		Path root = ZipArchive.extract(archive("README.md", "DOCUMENT", "notes.md", "NOTE"));

		try {
			assertThat(Files.readString(root.resolve("README.md"))).isEqualTo("DOCUMENT");
			assertThat(Files.readString(root.resolve("notes.md"))).isEqualTo("NOTE");
		}
		finally {
			BusinessProcessDiscovery.deleteQuietly(root);
		}
	}

	@Test
	void anEntryNamedToWriteOutsideTheArchiveIsRefused() throws IOException {
		String probe = "smith-archive-escape-probe";

		assertThatThrownBy(() -> ZipArchive.extract(archive("../" + probe, "ESCAPED")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("outside");
		assertThat(Files.exists(Path.of(System.getProperty("java.io.tmpdir"), probe))).isFalse();
	}

	@Test
	void anArchiveThatIsNotThereIsRefused() {
		assertThatThrownBy(() -> ZipArchive.extract(this.archives.resolve("absent.zip")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("No such archive");
	}

	private Path archive(String... nameAndBody) throws IOException {
		Path archive = Files.createTempFile(this.archives, "codebase", ".zip");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
			for (int entry = 0; entry < nameAndBody.length; entry += 2) {
				out.putNextEntry(new ZipEntry(nameAndBody[entry]));
				out.write(nameAndBody[entry + 1].getBytes(StandardCharsets.UTF_8));
				out.closeEntry();
			}
		}
		return archive;
	}

}
