package com.embabel.smith.ui;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.composer.CompositeExporter;
import com.embabel.smith.composer.CompositionCanvas;
import com.embabel.smith.composer.SpecNode;
import com.embabel.smith.ui.dto.CanvasRequest;
import com.embabel.smith.ui.dto.CanvasResponse;
import com.embabel.smith.ui.dto.ComposeRequest;
import com.embabel.smith.ui.dto.ComposeResponse;
import com.embabel.smith.ui.dto.Edge;
import com.embabel.smith.ui.dto.GoalInfo;
import com.embabel.smith.ui.dto.SpecInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The REST surface behind the drag-and-drop canvas (POC 15). Every endpoint delegates to POC 14's
 * proven composer model ({@link CompositionCanvas} / {@link CompositeExporter}), so the visual
 * editor's "snap" (edge type-check), chain, and composite export are the same logic the offline POC
 * proves.
 */
@RestController
public class CanvasController {

    private final SpecCatalog catalog;
    private final CompositeExporter exporter = new CompositeExporter();
    private final Path outDir;

    public CanvasController(SpecCatalog catalog,
                            @Value("${builder.compose-output-dir:target/composed-ui}") Path outDir) {
        this.catalog = catalog;
        this.outDir = outDir;
    }

    /** The palette: every available spec with its external inputs and output type. */
    @GetMapping("/api/specs")
    public List<SpecInfo> specs() {
        return catalog.all().values().stream()
                .map(node -> new SpecInfo(node.id(), new ArrayList<>(node.externalInputTypes()),
                        node.outputType()))
                .toList();
    }

    /** Validates a canvas: returns whether it is a valid chain, the issues, and the composed chain. */
    @PostMapping("/api/canvas")
    public CanvasResponse canvas(@RequestBody CanvasRequest request) {
        CompositionCanvas canvas = buildCanvas(request.nodes(), request.edges());
        List<String> issues = canvas.validate();
        List<String> chain = issues.isEmpty()
                ? canvas.chain().stream().map(SpecNode::id).toList()
                : List.of();
        return new CanvasResponse(issues.isEmpty(), issues, chain);
    }

    /** Exports a valid canvas as a composite agent spec and reports its shape. */
    @PostMapping("/api/compose")
    public ComposeResponse compose(@RequestBody ComposeRequest request) throws IOException {
        CompositionCanvas canvas = buildCanvas(request.nodes(), request.edges());
        List<String> issues = canvas.validate();
        if (!issues.isEmpty()) {
            return new ComposeResponse(null, null, List.of(), List.of(), issues);
        }
        String name = request.name() == null || request.name().isBlank() ? "compose-composite" : request.name();
        AgentSpec composite = exporter.buildComposite(name, canvas);
        Path dir = exporter.export(name, canvas, outDir);
        return new ComposeResponse(
                dir.toString(),
                new GoalInfo(composite.goal().name(), composite.goal().outputTypeName(),
                        composite.goal().isExported()),
                composite.steps().stream().map(ActionStepSpec::name).toList(),
                composite.types().stream().map(DynamicTypeDef::name).toList(),
                List.of());
    }

    /** Builds a canvas from spec names + edges; unknown specs / dangling edges are dropped. */
    private CompositionCanvas buildCanvas(List<String> nodeNames, List<Edge> edges) {
        CompositionCanvas canvas = new CompositionCanvas();
        Set<String> added = new LinkedHashSet<>();
        if (nodeNames != null) {
            for (String name : nodeNames) {
                SpecNode node = catalog.get(name);
                if (node != null && added.add(name)) {
                    canvas.addSpec(node);
                }
            }
        }
        if (edges != null) {
            for (Edge edge : edges) {
                if (added.contains(edge.from()) && added.contains(edge.to())) {
                    canvas.connect(edge.from(), edge.to());
                }
            }
        }
        return canvas;
    }
}
