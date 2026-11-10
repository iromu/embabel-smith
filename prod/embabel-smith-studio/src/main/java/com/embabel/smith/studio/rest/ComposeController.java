package com.embabel.smith.studio.rest;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.publish.CollisionException;
import com.embabel.smith.builder.publish.FolderPublisher;
import com.embabel.smith.builder.publish.GitPublisher;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.studio.rest.dto.ComposeRequest;
import com.embabel.smith.studio.rest.dto.ComposeResponse;
import com.embabel.smith.studio.rest.dto.LifecycleInfo;
import com.embabel.smith.studio.rest.dto.PublishInfo;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Compose pane (change 07): export an assembled GOAP flow — a goal plus its action steps, wired
 * by type names — as a skill-pack to a source. Unlike the Author pane's builder, no model is in the
 * loop: the client has already authored the pack in the graph editor, so this endpoint only
 * validates its shape and writes it. A fresh pack is stamped {@code INCUBATING} with a 0.0 baseline
 * (not yet eval'd) and published through the same folder/git publishers the builder uses, so the
 * result is immediately runnable from the Run pane.
 */
@RestController
public class ComposeController {

    private final LifecycleStamper stamper = new LifecycleStamper();

    @PostMapping("/api/compose")
    public ComposeResponse compose(@RequestBody ComposeRequest request) {
        String validationError = validate(request);
        if (validationError != null) {
            return ComposeResponse.failed(validationError);
        }
        SkillPack pack = request.pack();
        try {
            PublishTarget target = PublishTarget.parse(request.source());
            LifecycleMetadata lifecycle = stamper.stamp(pack, 0.0);
            try (Publisher publisher = publisherFor(target)) {
                PublishResult published = publisher.publish(pack, lifecycle,
                        request.overwrite() != null && request.overwrite(),
                        request.push() != null && request.push());
                return ComposeResponse.published(pack.name(), pack.goal().name(),
                        PublishInfo.of(published), LifecycleInfo.of(lifecycle));
            }
        } catch (CollisionException e) {
            return ComposeResponse.failed(e.getMessage());
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            return ComposeResponse.failed(message);
        }
    }

    /**
     * The shape check for a composed pack: it must name a source, carry an exported goal, at least
     * one action step, and the goal's output type must be produced by some step — a GOAP flow that
     * can never reach its goal is not exportable.
     */
    private static String validate(ComposeRequest request) {
        if (request.source() == null || request.source().isBlank()) {
            return "No source provided; pick where to publish the pack.";
        }
        SkillPack pack = request.pack();
        if (pack == null) {
            return "No pack provided; author a goal and at least one step.";
        }
        if (pack.goal() == null || !pack.goal().isExported()) {
            return "The flow needs an exported goal (the type the run returns).";
        }
        if (pack.steps().isEmpty()) {
            return "The flow needs at least one action step.";
        }
        String goalOutput = pack.goal().outputTypeName();
        boolean goalProduced = pack.steps().stream()
                .anyMatch(s -> goalOutput != null && goalOutput.equals(s.outputTypeName()));
        if (!goalProduced) {
            return "No step produces the goal's output type '" + goalOutput + "'; the goal is unreachable.";
        }
        return null;
    }

    private static Publisher publisherFor(PublishTarget target) {
        return switch (target.type()) {
            case FOLDER -> new FolderPublisher(target);
            case GIT -> new GitPublisher(target);
        };
    }
}
