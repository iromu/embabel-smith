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

package com.embabel.smith.studio.rest;

import java.util.List;

import com.embabel.smith.studio.loop.LoopService;
import com.embabel.smith.studio.rest.dto.ApproveResponse;
import com.embabel.smith.studio.rest.dto.AutoPublishRequest;
import com.embabel.smith.studio.rest.dto.CandidateInfo;
import com.embabel.smith.studio.rest.dto.IterationInfo;
import com.embabel.smith.studio.rest.dto.LoopStatus;
import com.embabel.smith.studio.rest.dto.RejectResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Auto-improve surface (change 06): lists the pending candidates with their evidence,
 * approves or rejects one, triggers a single on-demand loop iteration, starts/stops the
 * interval schedule, and gets/sets auto-publish. Thin — delegates to the
 * {@link LoopService}.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class ImproveController {

	private final LoopService loopService;

	public ImproveController(LoopService loopService) {
		this.loopService = loopService;
	}

	@GetMapping("/api/improve/candidates")
	public List<CandidateInfo> candidates() {
		return this.loopService.candidates().stream().map(CandidateInfo::of).toList();
	}

	@PostMapping("/api/improve/candidates/{id}/approve")
	public ApproveResponse approve(@PathVariable("id") String id) {
		return this.loopService.approve(id);
	}

	@PostMapping("/api/improve/candidates/{id}/reject")
	public RejectResponse reject(@PathVariable("id") String id) {
		return this.loopService.reject(id);
	}

	@PostMapping("/api/loop/trigger")
	public IterationInfo trigger() {
		return this.loopService.trigger();
	}

	@PostMapping("/api/loop/start")
	public LoopStatus start() {
		return this.loopService.start();
	}

	@PostMapping("/api/loop/stop")
	public LoopStatus stop() {
		return this.loopService.stop();
	}

	@GetMapping("/api/loop/status")
	public LoopStatus status() {
		return this.loopService.status();
	}

	@GetMapping("/api/loop/auto-publish")
	public LoopStatus autoPublish() {
		return this.loopService.status();
	}

	@PutMapping("/api/loop/auto-publish")
	public LoopStatus setAutoPublish(@RequestBody AutoPublishRequest request) {
		return this.loopService.setAutoPublish(request.autoPublish());
	}

}
