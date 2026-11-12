package com.embabel.smith.studio.rest;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.studio.rest.dto.ComposeRequest;
import com.embabel.smith.studio.rest.dto.ComposeResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Offline tests for the Compose pane: the shape guards and the folder export. */
class ComposeControllerTest {

	@TempDir
	Path sourceRoot;

	private final ComposeController controller = new ComposeController();

	/** A valid flow: a step produces "Result", and the goal wants "Result". */
	private static AgentSpec validPack() {
		return new AgentSpec(new AgentSpecManifest("explainer", "1.0.0"),
				GoalStepSpec.exportedGoal("explainer", "explain something", "Result"),
				List.of(ActionStepSpec.action("explain", "explain the input", List.of("UserInput"), "Result",
						"explain {{userInput.content}}")),
				List.of());
	}

	@Test
	void exportsAValidFlowToTheFolderSource() throws Exception {
		ComposeResponse response = controller
			.compose(new ComposeRequest("folder:" + sourceRoot, validPack(), false, false));
		assertThat(response.ok()).isTrue();
		assertThat(response.packName()).isEqualTo("explainer");
		assertThat(response.goal()).isEqualTo("explainer");
		assertThat(response.published().location()).isEqualTo(sourceRoot.resolve("explainer").toString());
		Path packDir = sourceRoot.resolve("explainer");
		assertThat(Files.isRegularFile(packDir.resolve("manifest.yml"))).isTrue();
		assertThat(Files.isRegularFile(packDir.resolve("goal.yml"))).isTrue();
		assertThat(Files.isRegularFile(packDir.resolve("steps").resolve("explain.yml"))).isTrue();
		assertThat(Files.isRegularFile(packDir.resolve("lifecycle.yml"))).isTrue();
	}

	@Test
	void rejectsAMissingSource() {
		ComposeResponse response = controller.compose(new ComposeRequest("", validPack(), false, false));
		assertThat(response.ok()).isFalse();
		assertThat(response.error()).contains("No source");
	}

	@Test
	void rejectsAnUnexportedGoal() {
		AgentSpec pack = new AgentSpec(new AgentSpecManifest("x", "1.0.0"),
				new GoalStepSpec(GoalStepSpec.STEP_TYPE, "x", "g", "Result", false),
				List.of(ActionStepSpec.action("explain", "d", List.of("UserInput"), "Result", "p")), List.of());
		ComposeResponse response = controller.compose(new ComposeRequest("folder:" + sourceRoot, pack, false, false));
		assertThat(response.ok()).isFalse();
		assertThat(response.error()).contains("exported goal");
	}

	@Test
	void rejectsAFlowWithNoSteps() {
		AgentSpec pack = new AgentSpec(new AgentSpecManifest("x", "1.0.0"),
				GoalStepSpec.exportedGoal("x", "g", "Result"), List.of(), List.of());
		ComposeResponse response = controller.compose(new ComposeRequest("folder:" + sourceRoot, pack, false, false));
		assertThat(response.ok()).isFalse();
		assertThat(response.error()).contains("at least one action step");
	}

	@Test
	void rejectsAnUnreachableGoal() {
		AgentSpec pack = new AgentSpec(new AgentSpecManifest("x", "1.0.0"),
				GoalStepSpec.exportedGoal("x", "g", "Wanted"),
				List.of(ActionStepSpec.action("explain", "d", List.of("UserInput"), "Result", "p")), List.of());
		ComposeResponse response = controller.compose(new ComposeRequest("folder:" + sourceRoot, pack, false, false));
		assertThat(response.ok()).isFalse();
		assertThat(response.error()).contains("unreachable");
	}

}
