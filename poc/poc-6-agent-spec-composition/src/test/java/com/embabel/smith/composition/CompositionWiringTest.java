package com.embabel.smith.composition;

import com.embabel.agent.core.DomainType;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import com.embabel.smith.common.validation.AgentSpecValidator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the composition wiring (offline, no model): the two independent agent specs
 * are valid on their own, share the intermediate type that is the composition's glue, and
 * merge into one flat step-spec directory with both goals and both actions. The actual
 * GOAP chain across the two packs is exercised by the endpoint-gated run.
 */
class CompositionWiringTest {

	private final AgentSpecReader reader = new AgentSpecReader();

	private final AgentSpecValidator validator = new AgentSpecValidator();

	private final CompositionRunner runner = new CompositionRunner(null);

	private Path pack(String name) {
		return repoRoot().resolve("agent-specs").resolve(name);
	}

	private AgentSpec load(String name) {
		try {
			return reader.read(pack(name));
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to read " + name, e);
		}
	}

	@Test
	void bothPacksAreValidIndependentSpecs() {
		assertThat(validator.validate(pack("compose-extract")).isValid()).isTrue();
		assertThat(validator.validate(pack("compose-explain")).isValid()).isTrue();
	}

	@Test
	void theFinalGoalDependsOnTheOtherSpecThroughTheSharedType() {
		AgentSpec extract = load("compose-extract");
		AgentSpec explain = load("compose-explain");

		// explain's goal is BackgroundMessage, produced by an action that consumes the
		// shared type.
		assertThat(explain.goal().outputTypeName()).isEqualTo(SampleAgentSpecs.BACKGROUND_MESSAGE);
		ActionStepSpec producer = explain.steps()
			.stream()
			.filter(s -> SampleAgentSpecs.BACKGROUND_MESSAGE.equals(s.outputTypeName()))
			.findFirst()
			.orElseThrow();
		assertThat(producer.inputTypeNames()).contains("TopicBrief");

		// ...and explain never produces TopicBrief itself — only extract does, from
		// UserInput.
		assertThat(explain.steps()).noneMatch(s -> "TopicBrief".equals(s.outputTypeName()));
		ActionStepSpec source = extract.steps()
			.stream()
			.filter(s -> "TopicBrief".equals(s.outputTypeName()))
			.findFirst()
			.orElseThrow();
		assertThat(source.inputTypeNames()).contains(SampleAgentSpecs.USER_INPUT);
	}

	@Test
	void combinedTypesDeduplicateTheSharedIntermediate() {
		List<DomainType> types = runner.combinedTypes(List.of(load("compose-extract"), load("compose-explain")));

		assertThat(types).extracting(DomainType::getName)
			.containsExactlyInAnyOrder(SampleAgentSpecs.USER_INPUT, SampleAgentSpecs.BACKGROUND_MESSAGE, "TopicBrief");
	}

	@Test
	void assembleCombinedMergesBothGoalsAndBothActions() throws Exception {
		Path target = runner.assembleCombined(List.of(pack("compose-extract"), pack("compose-explain")));

		long goalCount;
		try (var files = Files.list(target)) {
			goalCount = files.filter(f -> f.getFileName().toString().startsWith("goal-")).count();
		}
		assertThat(goalCount).as("both goals are merged (renamed to avoid collision)").isEqualTo(2);
		assertThat(Files.isRegularFile(target.resolve("extractTopic.yml"))).isTrue();
		assertThat(Files.isRegularFile(target.resolve("writeExplainer.yml"))).isTrue();
		// manifest and types are not step specs, so they are not copied
		assertThat(Files.isRegularFile(target.resolve("manifest.yml"))).isFalse();
		assertThat(Files.isDirectory(target.resolve("types"))).isFalse();
	}

	/**
	 * Walks up from the working directory to the repo root (where {@code agent-specs/}
	 * lives).
	 */
	private static Path repoRoot() {
		Path dir = Path.of("").toAbsolutePath();
		while (dir != null) {
			if (Files.isDirectory(dir.resolve("agent-specs/compose-extract"))) {
				return dir;
			}
			dir = dir.getParent();
		}
		throw new IllegalStateException("Could not locate repo root (agent-specs/compose-extract not found)");
	}

}
