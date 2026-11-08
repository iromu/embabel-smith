package com.embabel.smith.studio.rest;

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

import java.util.List;

/**
 * The Auto-improve surface (change 06): lists the pending candidates with their evidence, approves
 * or rejects one, triggers a single on-demand loop iteration, starts/stops the interval schedule,
 * and gets/sets auto-publish. Thin — delegates to the {@link LoopService}.
 */
@RestController
public class ImproveController {

    private final LoopService loopService;

    public ImproveController(LoopService loopService) {
        this.loopService = loopService;
    }

    @GetMapping("/api/improve/candidates")
    public List<CandidateInfo> candidates() {
        return loopService.candidates().stream().map(CandidateInfo::of).toList();
    }

    @PostMapping("/api/improve/candidates/{id}/approve")
    public ApproveResponse approve(@PathVariable("id") String id) {
        return loopService.approve(id);
    }

    @PostMapping("/api/improve/candidates/{id}/reject")
    public RejectResponse reject(@PathVariable("id") String id) {
        return loopService.reject(id);
    }

    @PostMapping("/api/loop/trigger")
    public IterationInfo trigger() {
        return loopService.trigger();
    }

    @PostMapping("/api/loop/start")
    public LoopStatus start() {
        return loopService.start();
    }

    @PostMapping("/api/loop/stop")
    public LoopStatus stop() {
        return loopService.stop();
    }

    @GetMapping("/api/loop/status")
    public LoopStatus status() {
        return loopService.status();
    }

    @GetMapping("/api/loop/auto-publish")
    public LoopStatus autoPublish() {
        return loopService.status();
    }

    @PutMapping("/api/loop/auto-publish")
    public LoopStatus setAutoPublish(@RequestBody AutoPublishRequest request) {
        return loopService.setAutoPublish(request.autoPublish());
    }
}
