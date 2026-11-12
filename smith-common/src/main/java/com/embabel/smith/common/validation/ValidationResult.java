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

import java.util.List;

/**
 * The outcome of validating a agent spec: the full list of findings. An empty list means
 * the pack is well-formed and eligible to be written / ingested.
 *
 * @param issues the findings of the validation
 * @author Ivan Rodriguez
 */
public record ValidationResult(List<ValidationIssue> issues) {

	public ValidationResult {
		issues = (issues != null) ? List.copyOf(issues) : List.of();
	}

	public boolean isValid() {
		return this.issues.isEmpty();
	}

	public List<ValidationIssue> issues() {
		return this.issues;
	}

	public List<ValidationIssue> issuesWithCode(String code) {
		return this.issues.stream().filter((i) -> i.code().equals(code)).toList();
	}
}
