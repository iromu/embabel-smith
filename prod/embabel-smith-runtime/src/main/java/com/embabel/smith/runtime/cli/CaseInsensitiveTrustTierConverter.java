/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.cli;

import java.util.Locale;

import com.embabel.smith.runtime.core.TrustTier;
import picocli.CommandLine;

/**
 * Parses the {@code --trust-tier} option case-insensitively ({@code high} or
 * {@code HIGH}).
 *
 * @author Ivan Rodriguez
 */
public class CaseInsensitiveTrustTierConverter implements CommandLine.ITypeConverter<TrustTier> {

	@Override
	public TrustTier convert(String value) {
		return TrustTier.valueOf(value.toUpperCase(Locale.ROOT));
	}

}
