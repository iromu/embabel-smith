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

package com.embabel.smith.studio.rest.dto;

import java.util.List;

import com.embabel.smith.builder.BuildResult;

/**
 * The build report (the Author pane): whether the pack was published or the build was
 * blocked, at which stage it stopped, and the evidence (eval score, security scan,
 * lifecycle, publish location). A blocked build names the stage and the reasons; a
 * missing endpoint is a structured {@code MODEL_NOT_CONFIGURED} error.
 *
 * @param status the build status (e.g. {@code PUBLISHED} or {@code BLOCKED}).
 * @param stage the stage the build stopped at.
 * @param packName the pack name the build produced.
 * @param score the eval score (null when not eval'd).
 * @param issues the blocking reasons (empty when published).
 * @param scan the security scan (null when not scanned).
 * @param lifecycle the lifecycle stamp (null when not stamped).
 * @param published the publish location (null when not published).
 * @param errorCode the error code ({@code MODEL_NOT_CONFIGURED} when the endpoint is absent).
 * @param error the error message (null on success).
 * @author Ivan Rodriguez
 */
public record BuildResponse(String status, String stage, String packName, Double score, List<String> issues,
		ScanInfo scan, LifecycleInfo lifecycle, PublishInfo published, String errorCode, String error) {

	public static BuildResponse of(BuildResult result) {
		return new BuildResponse(result.status().name(), result.stage(), result.packName(), result.score(),
				result.issues(), (result.scan() != null) ? ScanInfo.of(result.scan()) : null,
				(result.lifecycle() != null) ? LifecycleInfo.of(result.lifecycle()) : null,
				(result.published() != null) ? PublishInfo.of(result.published()) : null, null, null);
	}

	public static BuildResponse blocked(String stage, String packName, List<String> issues, String error) {
		return new BuildResponse("BLOCKED", stage, packName, null, issues, null, null, null, null, error);
	}

	/** The structured "no model endpoint" error. */
	public static BuildResponse noEndpoint(String message) {
		return new BuildResponse("BLOCKED", "config", null, null, List.of(), null, null, null, "MODEL_NOT_CONFIGURED",
				message);
	}
}
