package com.embabel.smith.studio.rest;

import com.embabel.smith.studio.rest.dto.PackInfo;
import com.embabel.smith.studio.rest.dto.SourceInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The sources surface (change 06): lists the studio's configured sources, and the packs of a source
 * with their lifecycle state and content-hash drift status (the Lifecycle pane). Offline — reads
 * from disk, no model calls.
 */
@RestController
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    /** The configured sources the studio offers. */
    @GetMapping("/api/sources")
    public List<SourceInfo> sources() {
        return sourceService.listSources();
    }

    /** The packs of a source, each with its lifecycle state, baseline, and drift status. */
    @GetMapping("/api/packs")
    public List<PackInfo> packs(@RequestParam("source") String source) {
        return sourceService.listPacks(source);
    }

    /** The goal names a source's packs export — the Run pane runs a goal, not a pack name. */
    @GetMapping("/api/goals")
    public List<String> goals(@RequestParam("source") String source) {
        return sourceService.listGoals(source);
    }
}
