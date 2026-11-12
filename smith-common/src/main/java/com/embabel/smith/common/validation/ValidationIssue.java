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

package com.embabel.smith.common.validation;

/**
 * A single validation finding.
 *
 * @param code a stable, machine-readable category (e.g. {@code DANGLING_TYPE_REF})
 * @param message a human-readable explanation; for reference errors it names the
 * offending value
 * @param location the file or step the issue was found in, or {@code null} if pack-wide
 * @author Ivan Rodriguez
 */
public record ValidationIssue(String code, String message, String location) {

	public static ValidationIssue of(String code, String message) {
		return new ValidationIssue(code, message, null);
	}

	public static ValidationIssue of(String code, String message, String location) {
		return new ValidationIssue(code, message, location);
	}
}
