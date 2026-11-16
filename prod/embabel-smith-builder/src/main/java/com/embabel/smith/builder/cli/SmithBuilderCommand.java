/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.cli;

import picocli.CommandLine.Command;

/**
 * The CLI root: {@code smith build ...}. The {@code build} subcommand is registered by
 * the launcher ({@link com.embabel.smith.builder.SmithBuilderApplication}) with the
 * booted platform.
 *
 * @author Ivan Rodriguez
 */
@Command(name = "smith", description = "The Embabel Smith spec builder")
public class SmithBuilderCommand {

}
