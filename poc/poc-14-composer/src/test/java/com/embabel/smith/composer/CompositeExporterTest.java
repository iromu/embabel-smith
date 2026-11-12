package com.embabel.smith.composer;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for composite export (POC 14): the exported composite is a single,
 * well-formed agent spec whose action chain is the composed plan, and it re-validates
 * cleanly.
 */
class CompositeExporterTest {

	private static final String BG = "com.embabel.agent.spec.model.BackgroundMessage";

	private final CompositeExporter exporter = new CompositeExporter();

	@Test
	void buildCompositeUnionsStepsAndTypesUnderOneGoal() {
		AgentSpec composite = exporter.buildComposite("compose-composite", validCanvas());

		assertThat(composite.goal().isExported()).isTrue();
		assertThat(composite.goal().outputTypeName()).isEqualTo(BG);
		assertThat(composite.steps()).extracting(ActionStepSpec::name)
			.containsExactly("extractTopic", "writeExplainer");
		assertThat(composite.types()).extracting(DynamicTypeDef::name).containsExactly("TopicBrief");
		assertThat(composite.manifest().name()).isEqualTo("compose-composite");
	}

	@Test
	void theExportedCompositeRevalidatesCleanly(@TempDir Path tmp) throws IOException {
		Path compositeDir = exporter.export("compose-composite", validCanvas(), tmp);

		AgentSpec reRead = new AgentSpecReader().read(compositeDir);
		assertThat(reRead.steps()).extracting(ActionStepSpec::name).containsExactly("extractTopic", "writeExplainer");
		assertThat(new AgentSpecValidator().validate(compositeDir).isValid())
			.as("the exported composite is a loadable agent spec")
			.isTrue();
	}

	private CompositionCanvas validCanvas() {
		CompositionCanvas canvas = new CompositionCanvas();
		canvas.addSpec(new SpecNode(extractPack()));
		canvas.addSpec(new SpecNode(explainPack()));
		canvas.connect("compose-extract", "compose-explain");
		return canvas;
	}

	// --- fixtures (mirroring the compose-extract / compose-explain samples) ---

	private static AgentSpec extractPack() {
		AgentSpecManifest manifest = new AgentSpecManifest("compose-extract", "1.0.0", "GOAP", null);
		GoalStepSpec goal = GoalStepSpec.exportedGoal("topicExtracted", "desc", "TopicBrief");
		ActionStepSpec action = ActionStepSpec.action("extractTopic", "desc",
				List.of("com.embabel.agent.domain.io.UserInput"), "TopicBrief",
				"Extract the topic: {{userInput.content}}");
		return new AgentSpec(manifest, goal, List.of(action), List.of(topicBrief()));
	}

	private static AgentSpec explainPack() {
		AgentSpecManifest manifest = new AgentSpecManifest("compose-explain", "1.0.0", "GOAP", null);
		GoalStepSpec goal = GoalStepSpec.exportedGoal("explainerDone", "desc", BG);
		ActionStepSpec action = ActionStepSpec.action("writeExplainer", "desc", List.of("TopicBrief"), BG,
				"Write the explainer: {{topicBrief.topic}}");
		return new AgentSpec(manifest, goal, List.of(action), List.of(topicBrief()));
	}

	private static DynamicTypeDef topicBrief() {
		return new DynamicTypeDef(
				"TopicBrief", "desc", List.of(new PropertyDef("topic", "string", "what"),
						new PropertyDef("audience", "string", "who"), new PropertyDef("tone", "string", "voice")),
				List.of(), true);
	}

}
