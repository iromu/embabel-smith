package com.embabel.smith.runtime.cli;

import picocli.CommandLine.Command;

/**
 * The CLI root: {@code smith run ...}. The {@code run} subcommand is registered by the
 * launcher ({@link com.embabel.smith.runtime.SmithRuntimeApplication}) with the booted platform.
 */
@Command(name = "smith", description = "The Embabel Smith skill runtime")
public class SmithCommand {
}
