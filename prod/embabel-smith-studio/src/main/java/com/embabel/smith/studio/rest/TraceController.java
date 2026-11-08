package com.embabel.smith.studio.rest;

import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.runtime.trace.TraceRecord;
import com.embabel.smith.studio.improve.TraceWindowReader;
import com.embabel.smith.studio.rest.dto.TraceInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Traces surface (change 06): the rolling window of a skill's trace records, most recent first,
 * each carrying its per-step records (for step-level drill-down) and the failure reason where
 * present. Offline — reads the JSONL trace store, no model calls.
 */
@RestController
public class TraceController {

    private final TraceWindowReader reader;

    public TraceController(JsonlTraceStore traceStore) {
        this.reader = new TraceWindowReader(traceStore);
    }

    /** The recent traces for a skill, most recent first, bounded by {@code limit}. */
    @GetMapping("/api/traces")
    public List<TraceInfo> traces(@RequestParam("skill") String skill,
                                  @RequestParam(value = "limit", required = false,
                                          defaultValue = "100") int limit) {
        List<TraceRecord> window = reader.window(skill);
        int from = Math.max(0, window.size() - Math.max(1, limit));
        List<TraceRecord> recent = new ArrayList<>(window.subList(from, window.size()));
        Collections.reverse(recent); // the window is completion order; the pane wants most recent first
        return recent.stream().map(TraceInfo::of).toList();
    }
}
