package com.embabel.smith.builder.core;

import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationResult;
import com.embabel.smith.specsource.FolderSpecSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests for the single-shot generation technique: a canned model answer (the real
 * one, with the snake_case prompt vars the model actually emits) is injected through the
 * {@link SpecPromptCaller} seam, and the parsed, normalised agent spec is checked — no
 * model and no endpoint needed.
 */
class SingleShotAgentSpecGeneratorTest {

	@TempDir
	Path tmp;

	private final AgentSpecValidator validator = new AgentSpecValidator();

	private SingleShotAgentSpecGenerator generator() throws IOException {
		return generator(cannedAnswer());
	}

	private SingleShotAgentSpecGenerator generator(String answer) {
		return new SingleShotAgentSpecGenerator((system, user) -> answer, "system prompt");
	}

	private static String cannedAnswer() throws IOException {
		return Files.readString(Path.of("src/test/resources/fixtures/process_raw.json"));
	}

	@Test
	void parsesTheAnswerIntoANormalisedAgentSpec() throws IOException {
		CompiledDraft draft = generator().generate("a business process");

		AgentSpec pack = draft.pack();
		assertThat(draft.agentName()).isEqualTo(pack.name());
		assertThat(draft.issues()).isEmpty();
		assertThat(pack.manifest()).isNotNull();
		assertThat(pack.manifest().name()).isNotBlank();
		assertThat(pack.goal().isExported()).isTrue();
		assertThat(pack.steps()).hasSize(9);
		assertThat(this.validator.validate(pack).issues()).isEmpty();
	}

	@Test
	void normalisedPromptVarsMatchTheDeclaredInputs() throws IOException {
		AgentSpec pack = generator().generate("a business process").pack();

		// The research_debate step consumed snake_case vars; they must now be canonical.
		assertThat(pack.step("research_debate").prompt()).contains("{{technicalReport}}")
			.doesNotContain("{{technical_report}}");
		assertThat(pack.step("market_technical_analysis").prompt()).contains("{{userInput}}")
			.doesNotContain("{{user_input}}");
	}

	@Test
	void fencesAndProseAroundTheJsonAreStripped() {
		String answer = "Here is the spec:\n```json\n" + "{\"manifest\": {\"name\": \"tiny\", \"version\": \"1.0.0\"},"
				+ " \"goal\": {\"stepType\": \"goal\", \"name\": \"done\", \"description\": \"done\","
				+ " \"outputTypeName\": \"BackgroundMessage\"},"
				+ " \"steps\": [{\"stepType\": \"action\", \"name\": \"say\", \"description\": \"say it\","
				+ " \"inputTypeNames\": [\"UserInput\"], \"outputTypeName\": \"BackgroundMessage\","
				+ " \"prompt\": \"say {{ user_input }}\"}], \"types\": []}\n```";

		AgentSpec pack = generator(answer).generate("say things").pack();

		assertThat(pack.manifest().name()).isEqualTo("tiny");
		assertThat(pack.goal().isExported()).isTrue();
		assertThat(pack.steps().get(0).prompt()).contains("{{userInput}}").doesNotContain("{{ user_input }}");
	}

	@Test
	void manifestIsDerivedFromTheGoalWhenTheModelOmitsIt() {
		String answer = "{\"goal\": {\"stepType\": \"goal\", \"name\": \"final_decision\","
				+ " \"description\": \"the decision\", \"outputTypeName\": \"FinalDecision\"},"
				+ " \"steps\": [], \"types\": []}";

		AgentSpec pack = generator(answer).generate("decide").pack();

		assertThat(pack.manifest().name()).isEqualTo("final-decision");
		assertThat(pack.manifest().version()).isEqualTo("1.0.0");
	}

	@Test
	void unparseableAnswerFailsRatherThanYieldingAnEmptySpec() {
		assertThatThrownBy(() -> generator("the model apologised").generate("anything"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("No JSON object found");
	}

	@Test
	void writtenPackPassesOnDiskValidationAndIsDiscoverable() throws IOException {
		AgentSpec pack = generator().generate("a business process").pack();

		Path root = tmp.resolve("simulate");
		new AgentSpecWriter().write(pack, root.resolve(pack.name()));

		ValidationResult onDisk = this.validator.validate(root.resolve(pack.name()));
		assertThat(onDisk.issues()).isEmpty();

		var source = new FolderSpecSource("folder:" + root, root);
		assertThat(source.resolve().packs()).hasSize(1);
	}

}
