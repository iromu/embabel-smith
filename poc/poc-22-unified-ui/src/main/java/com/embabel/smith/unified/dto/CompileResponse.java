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

package com.embabel.smith.unified.dto;

/**
 * The result of the author pane: the compiled {@link AuthorDraft}, or a clear error.
 * {@link #error} is non-null when the request could not be compiled (blank description,
 * no endpoint configured, or the compile failed) — in which case {@link #draft} is null.
 *
 * @param error The error when the request could not be compiled, or null.
 * @param draft The compiled draft, or null when the compile failed.
 * @author Ivan Rodriguez
 */
public record CompileResponse(String error, AuthorDraft draft) {

	public static CompileResponse ok(AuthorDraft draft) {
		return new CompileResponse(null, draft);
	}

	public static CompileResponse badRequest(String error) {
		return new CompileResponse(error, null);
	}

	public static CompileResponse noEndpoint(String error) {
		return new CompileResponse(error, null);
	}

	public static CompileResponse error(String error) {
		return new CompileResponse(error, null);
	}
}
