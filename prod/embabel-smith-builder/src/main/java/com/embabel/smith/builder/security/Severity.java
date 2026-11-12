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

package com.embabel.smith.builder.security;

/**
 * The severity of a single security finding. A {@link Severity#CRITICAL} finding means
 * the spec should not be published at all (verdict {@link ScanVerdict#BLOCK}); a
 * {@link Severity#WARN} finding means it may publish under scrutiny (verdict
 * {@link ScanVerdict#REVIEW}).
 *
 * @author Ivan Rodriguez
 */
public enum Severity {

	/** Informational finding that does not affect the verdict. */
	INFO,

	/** Finding that allows publication only under review. */
	WARN,

	/** Finding that blocks publication of the spec. */
	CRITICAL

}
