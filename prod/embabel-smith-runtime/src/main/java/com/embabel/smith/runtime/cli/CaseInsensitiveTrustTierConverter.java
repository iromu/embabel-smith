package com.embabel.smith.runtime.cli;

import com.embabel.smith.runtime.core.TrustTier;
import picocli.CommandLine;

import java.util.Locale;

/** Parses the {@code --trust-tier} option case-insensitively ({@code high} or {@code HIGH}). */
public class CaseInsensitiveTrustTierConverter implements CommandLine.ITypeConverter<TrustTier> {

    @Override
    public TrustTier convert(String value) {
        return TrustTier.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
