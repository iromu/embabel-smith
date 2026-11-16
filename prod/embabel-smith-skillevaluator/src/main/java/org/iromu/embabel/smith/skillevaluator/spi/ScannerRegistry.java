/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.spi;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers {@link SecretsScanner} / {@link SecurityScanner} engines: first a
 * {@link ServiceLoader}-registered implementation, else the built-in default.
 */
public final class ScannerRegistry {

	private ScannerRegistry() {
	}

	public static SecretsLoader secrets() {
		return new SecretsLoader();
	}

	public static SecurityLoader security() {
		return new SecurityLoader();
	}

	public static final class SecretsLoader {

		public SecretsScanner load(SecretsScanner fallback) {
			List<SecretsScanner> found = ServiceLoader.load(SecretsScanner.class)
				.stream()
				.map(ServiceLoader.Provider::get)
				.toList();
			return found.isEmpty() ? fallback : found.get(0);
		}

	}

	public static final class SecurityLoader {

		public SecurityScanner load(SecurityScanner fallback) {
			List<SecurityScanner> found = ServiceLoader.load(SecurityScanner.class)
				.stream()
				.map(ServiceLoader.Provider::get)
				.toList();
			return found.isEmpty() ? fallback : found.get(0);
		}

	}

}
