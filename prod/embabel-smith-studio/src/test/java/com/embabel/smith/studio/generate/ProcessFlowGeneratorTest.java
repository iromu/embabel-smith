package com.embabel.smith.studio.generate;

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

/**
 * Offline tests for the generation core: the real model answer (with the snake_case
 * prompt vars the model actually emits) must be parsed and normalised into a pack that
 * passes structural validation and is discoverable by the runtime's folder spec source.
 */
class ProcessFlowGeneratorTest {

	@TempDir
	Path tmp;

	private final AgentSpecValidator validator = new AgentSpecValidator();

	private ProcessFlowGenerator generator() throws IOException {
		String answer = Files.readString(Path.of("src/test/resources/fixtures/process_raw.json"));
		return new ProcessFlowGenerator((system, user) -> answer, "system prompt");
	}

	@Test
	void modelAnswerWithSnakeCaseVarsNormalisesToAValidPack() throws IOException {
		AgentSpec pack = generator().generate("a business process");

		assertThat(pack.steps()).hasSize(9);
		assertThat(validator.validate(pack).issues()).isEmpty();
	}

	@Test
	void normalisedPromptVarsMatchTheDeclaredInputs() throws IOException {
		AgentSpec pack = generator().generate("a business process");

		// The research_debate step consumed snake_case vars; they must now be canonical.
		assertThat(pack.step("research_debate").prompt()).contains("{{technicalReport}}")
			.doesNotContain("{{technical_report}}");
		assertThat(pack.step("market_technical_analysis").prompt()).contains("{{userInput}}")
			.doesNotContain("{{user_input}}");
	}

	@Test
	void writtenPackPassesOnDiskValidationAndIsDiscoverable() throws IOException {
		AgentSpec pack = generator().generate("a business process");

		Path root = tmp.resolve("simulate");
		new AgentSpecWriter().write(pack, root.resolve(pack.name()));

		ValidationResult onDisk = validator.validate(root.resolve(pack.name()));
		assertThat(onDisk.issues()).isEmpty();

		var source = new FolderSpecSource("folder:" + root, root);
		assertThat(source.resolve().packs()).hasSize(1);
	}

}
