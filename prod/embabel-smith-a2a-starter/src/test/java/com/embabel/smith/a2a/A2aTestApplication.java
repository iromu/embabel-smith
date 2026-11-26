/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.a2a;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * The configuration the module's {@code @SpringBootTest} classes boot into, since the
 * library no longer ships an application class of its own.
 *
 * <p>
 * <b>Why this is not {@code @SpringBootApplication}.</b> That annotation adds a component
 * scan of the annotated class's package, which here is {@code com.embabel.smith.a2a} — so
 * it would pick {@link A2aConfiguration} up directly and register the whole surface
 * whatever {@code smith.a2a.enabled} said. The gate under test is the
 * auto-configuration's, so the beans have to arrive through it:
 * {@code @EnableAutoConfiguration} alone loads {@link SmithA2aAutoConfiguration} from the
 * library's registration file, exactly as a host that does not scan this package would.
 *
 * @author Ivan Rodriguez
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class A2aTestApplication {

}
