package com.embabel.smith.builder.cli;

import picocli.CommandLine.Command;

/**
 * The CLI root: {@code smith build ...}. The {@code build} subcommand is registered by the
 * launcher ({@link com.embabel.smith.builder.SmithBuilderApplication}) with the booted platform.
 */
@Command(name = "smith", description = "The Embabel Smith skill builder")
public class SmithBuilderCommand {
}
