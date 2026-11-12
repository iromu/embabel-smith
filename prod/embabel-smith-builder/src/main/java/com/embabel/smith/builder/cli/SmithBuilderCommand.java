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
