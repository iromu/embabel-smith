/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.a2a;

import com.embabel.agent.a2a.server.AgentCardHandler;
import com.embabel.agent.a2a.server.config.A2AConfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration of the A2A surface this library owns: it imports
 * {@link A2aConfiguration}, whose beans put {@code /a2a} on a host that asks for it.
 *
 * <p>
 * <b>The surface is off unless a host opts in.</b> {@code smith.a2a.enabled} has no
 * {@code matchIfMissing} default, so a module that merely inherits this library as a
 * transitive dependency — realistically through {@code embabel-smith-studio} — gets no
 * endpoint. Adding a dependency must not open a port; a host sets the flag, and
 * {@link SmithA2aEnvironmentPostProcessor} then supplies the servlet mode and the default
 * port these beans need. There is no launcher: the surface is hosted by whatever process
 * opts in.
 *
 * <p>
 * <b>Why the component scan lives here rather than on a host.</b> The beans the endpoint
 * needs are mostly the library's own — the registrar that writes the mappings, the
 * streaming handler, the task state manager — and only the card handler is replaced. So
 * the library's package is scanned with {@code A2AConfiguration} filtered out, which
 * keeps everything except the Autonomy-backed card handler this module supersedes. Two
 * {@code AgentCardHandler} beans would mean two mappings on {@code /a2a} and a failed
 * boot, which is also why the library's
 * {@code com.embabel.agent.autoconfigure.a2a.AgentA2AAutoConfiguration} is suppressed by
 * {@link SmithA2aEnvironmentPostProcessor} instead of being run alongside this one.
 *
 * <p>
 * The scan is on the gated class, not on {@link A2aConfiguration}, so it is reachable
 * only through the {@code smith.a2a.enabled} path: a host that neither sets the flag nor
 * scans this package runs none of it.
 *
 * @author Ivan Rodriguez
 */
// Boot 4's @AutoConfiguration is meta-annotated @Configuration(proxyBeanMethods = false),
// and the annotation no longer exposes the attribute to restate it.
@AutoConfiguration
@ConditionalOnClass(AgentCardHandler.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnProperty(prefix = "smith.a2a", name = "enabled", havingValue = "true")
@Import(A2aConfiguration.class)
@ComponentScan(basePackages = "com.embabel.agent.a2a",
		excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = A2AConfiguration.class))
public class SmithA2aAutoConfiguration {

}
