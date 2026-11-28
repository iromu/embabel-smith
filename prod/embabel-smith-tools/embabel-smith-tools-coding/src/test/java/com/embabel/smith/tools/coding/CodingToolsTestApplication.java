/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * The configuration this module's {@code @SpringBootTest} classes boot into.
 *
 * <p>
 * <b>Why this is not {@code @SpringBootApplication}.</b> That annotation adds a component
 * scan of the annotated package, which here is the addon's own package — so it would
 * register {@link CodingToolsAutoConfiguration} as a scanned configuration whatever its
 * conditions said, and the thing under test would be untestable.
 * {@code @EnableAutoConfiguration} alone brings the addon in through
 * {@code META-INF/spring/...AutoConfiguration.imports}, which is the route a host
 * actually uses and which the boot-state tests therefore also prove.
 *
 * <p>
 * No Embabel platform starter is on this classpath, so nothing else here boots a platform
 * or a model provider: the context is Boot's auto-configurations plus this addon.
 *
 * @author Ivan Rodriguez
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class CodingToolsTestApplication {

}
