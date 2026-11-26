/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import com.embabel.smith.builder.core.SpecPromptCaller;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests for skill generation (task 1.1): a canned model answer is injected
 * through the {@link SpecPromptCaller} seam and the rendered {@code SKILL.md} is checked
 * — the name is fixed by the description, the description and body come from the model —
 * with no model and no endpoint needed.
 *
 * @author Ivan Rodriguez
 */
class SkillGeneratorTest {

	private static final String DESCRIPTION = "Receive chilled goods into the cold store, rejecting any delivery "
			+ "whose temperature record shows a breach.";

	private static final String CANNED = "{\"description\": \"Handle chilled and frozen goods receipts, "
			+ "checking the temperature record before accepting a delivery.\", "
			+ "\"body\": \"## Workflow\\n1. Read the carrier's temperature record.\\n"
			+ "2. Reject a breach and escalate.\\n\\n## Gotchas\\n- A delivery is not done until the recipient confirms.\\n\"}";

	private static SkillGenerator generator(String answer) {
		return new SkillGenerator((system, user) -> answer, "You convert a responsibility into a SKILL.md");
	}

	@Test
	void aCannedAnswerProducesAWellFormedSkillMd() {
		Skill skill = generator(CANNED).generate("cold-store-goods-receipt", DESCRIPTION);

		assertThat(skill.name()).isEqualTo("cold-store-goods-receipt");
		assertThat(skill.description()).startsWith("Handle chilled and frozen goods receipts");
		assertThat(skill.body()).contains("## Workflow").contains("## Gotchas");

		String skillMd = skill.toSkillMd();
		assertThat(skillMd).startsWith("---\nname: cold-store-goods-receipt\ndescription: ");
		assertThat(skillMd).contains("\n---\n\n## Workflow");
		assertThat(SkillMdValidator.validate(skill.name(), skill.description())).isEmpty();
	}

	@Test
	void aDescriptionWithYamlMetacharactersStillLoadsFromTheRenderedFrontmatter() {
		// The description is model text written into YAML frontmatter, so a colon, a hash
		// or a
		// newline — all ordinary prose for a model — must not yield a published SKILL.md
		// that no
		// loader can read. The validator sees the field; this checks the artifact.
		String tricky = "Handle chilled receipts: reject a breach (#1),\nthen escalate to quality.";
		String canned = "{\"description\": \"Handle chilled receipts: reject a breach (#1),\\nthen escalate to quality.\","
				+ " \"body\": \"## Workflow\\n1. Read the temperature record.\\n\"}";

		Skill skill = generator(canned).generate("cold-store-goods-receipt", DESCRIPTION);
		assertThat(skill.description()).isEqualTo(tricky);
		assertThat(SkillMdValidator.validate(skill.name(), skill.description())).isEmpty();

		String skillMd = skill.toSkillMd();
		String frontmatter = skillMd.substring(4, skillMd.indexOf("\n---\n", 4));
		Map<String, Object> loaded = new Yaml().load(frontmatter);

		assertThat(loaded.get("name")).isEqualTo("cold-store-goods-receipt");
		assertThat(loaded.get("description")).isEqualTo(tricky);
	}

	@Test
	void theDescriptionArrivesInsideDocumentTags() {
		List<String> users = new ArrayList<>();
		SpecPromptCaller caller = (system, user) -> {
			users.add(user);
			return "{\"description\": \"d\", \"body\": \"b\"}";
		};

		new SkillGenerator(caller, "sys").generate("cold-store-goods-receipt", DESCRIPTION);

		assertThat(users.get(0)).contains("<document>\n" + DESCRIPTION + "\n</document>");
	}

	@Test
	void theNameIsFixedByTheDescriptionAndTheModelSeesBoth() {
		List<String> users = new ArrayList<>();
		// The model tries to name the skill itself; the caller-supplied name wins,
		// because it
		// is the name the folder and the collision gate are keyed on.
		SpecPromptCaller caller = (system, user) -> {
			users.add(user);
			return "{\"name\": \"hijacked-name\", \"description\": \"d\", \"body\": \"b\"}";
		};

		Skill skill = new SkillGenerator(caller, "sys").generate("cold-store-goods-receipt", DESCRIPTION);

		assertThat(skill.name()).isEqualTo("cold-store-goods-receipt");
		assertThat(users).hasSize(1);
		assertThat(users.get(0)).contains("cold-store-goods-receipt").contains(DESCRIPTION);
	}

	@Test
	void fencesAndProseAroundTheJsonAreStripped() {
		String answer = "Sure, here is the skill:\n```json\n" + CANNED + "\n```\nHope that helps.";

		Skill skill = generator(answer).generate("cold-store-goods-receipt", DESCRIPTION);

		assertThat(skill.description()).startsWith("Handle chilled");
		assertThat(skill.body()).contains("## Gotchas");
	}

	@Test
	void aMissingDescriptionOrBodyFailsRatherThanYieldingAnEmptySkill() {
		assertThatThrownBy(() -> generator("{\"description\": \"\", \"body\": \"b\"}").generate("s", DESCRIPTION))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("missing description or body");

		assertThatThrownBy(() -> generator("{\"description\": \"d\"}").generate("s", DESCRIPTION))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("missing description or body");
	}

	@Test
	void anUnparseableAnswerFailsRatherThanYieldingAnEmptySkill() {
		assertThatThrownBy(() -> generator("the model apologised, no JSON here").generate("s", DESCRIPTION))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Failed to obtain the generated skill");
	}

	@Test
	void aFirstDraftIsAskedTheQuestionThatPredatedTheInspectionGate() {
		List<String> users = new ArrayList<>();
		SpecPromptCaller caller = (system, user) -> {
			users.add(user);
			return "{\"description\": \"d\", \"body\": \"b\"}";
		};

		new SkillGenerator(caller, "sys").generate("cold-store-goods-receipt", DESCRIPTION, List.of());

		// The redraft entry point is additive: with no findings to fix, the request is
		// the
		// one the two-argument generate has always sent.
		assertThat(users.get(0)).doesNotContain("<findings>").contains("<document>\n" + DESCRIPTION + "\n</document>");
	}

	@Test
	void theRedraftRequestCarriesAtMostTheCapOfFindingsAndSaysSo() {
		List<String> users = new ArrayList<>();
		SpecPromptCaller caller = (system, user) -> {
			users.add(user);
			return "{\"description\": \"d\", \"body\": \"b\"}";
		};
		List<String> findings = new ArrayList<>();
		for (int i = 0; i < SkillGenerator.MAX_FINDINGS_IN_PROMPT + 7; i++) {
			findings.add("[SCHEMA-HIGH] finding " + i + " in SKILL.md:1");
		}

		new SkillGenerator(caller, "sys").generate("cold-store-goods-receipt", DESCRIPTION, findings);

		// A pathological draft can report findings without limit; the prompt bound is
		// what
		// stops that from becoming an unbounded request. The count is quoted so the model
		// is not told the quoted list was the whole verdict.
		String block = users.get(0);
		block = block.substring(block.indexOf("<findings>"), block.indexOf("</findings>"));
		assertThat(block.lines().filter((line) -> line.startsWith("[SCHEMA-HIGH]")).count())
			.isEqualTo(SkillGenerator.MAX_FINDINGS_IN_PROMPT);
		assertThat(block).doesNotContain("finding " + SkillGenerator.MAX_FINDINGS_IN_PROMPT)
			.contains("and 7 more findings");
	}

	@Test
	void theScriptsTheModelWritesArriveOnTheSkill() {
		String withScript = "{\"description\": \"d\", \"body\": \"b\", \"scripts\": ["
				+ "{\"path\": \"scripts/check-temperature.js\", \"content\": \"console.log(Number(stdin) > 4);\"}]}";

		Skill skill = generator(withScript).generate("cold-store-goods-receipt", DESCRIPTION);

		assertThat(skill.scripts()).singleElement().satisfies((script) -> {
			assertThat(script.path()).isEqualTo("scripts/check-temperature.js");
			assertThat(script.content()).isEqualTo("console.log(Number(stdin) > 4);\n");
		});
		// The scripts are files in the folder, not part of SKILL.md — which is what lets
		// a
		// script-carrying skill keep the frontmatter the validator already accepts.
		assertThat(skill.toSkillMd()).doesNotContain("scripts");
	}

	@Test
	void anAnswerWithNoScriptsMemberIsTheInstructionsOnlySkillItAlwaysWas() {
		Skill skill = generator(CANNED).generate("cold-store-goods-receipt", DESCRIPTION);

		assertThat(skill.scripts()).isEmpty();
	}

	@Test
	void aModelThatDropsTheScriptsFolderPrefixStillWritesIntoIt() {
		// The folder is the format's, not the model's to choose: the name is reduced to a
		// file and addressed under scripts/, so neither "totals.js" nor "../totals.js"
		// can
		// put a file anywhere else in the published folder.
		String declaredElsewhere = "{\"description\": \"d\", \"body\": \"b\", \"scripts\": ["
				+ "{\"path\": \"../outside/totals.js\", \"content\": \"console.log(1);\"}]}";

		Skill skill = generator(declaredElsewhere).generate("cold-store-goods-receipt", DESCRIPTION);

		assertThat(skill.scripts()).singleElement()
			.satisfies((script) -> assertThat(script.path()).isEqualTo("scripts/totals.js"));
	}

	@Test
	void aScriptThatIsNotJavaScriptStopsTheBuildRatherThanVanishing() {
		// Dropping it quietly would publish a body that tells the agent to run a file
		// that
		// was never written, and the engine could not run it either way.
		String notJs = "{\"description\": \"d\", \"body\": \"b\", \"scripts\": ["
				+ "{\"path\": \"scripts/notes.md\", \"content\": \"do the thing\"}]}";

		assertThatThrownBy(() -> generator(notJs).generate("cold-store-goods-receipt", DESCRIPTION))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("not a JavaScript file");
	}

	@Test
	void aScriptCarryingNoSourceIsNotPublishedAsTheStringNull() {
		// A model can omit the content member or set it to null, and a JSON null's own
		// text is
		// the four characters "null" — which would publish as a runnable file that runs
		// to a syntax error. Both spellings of "no source" are the blank case the
		// generator already skips, not a script.
		String explicitNull = "{\"description\": \"d\", \"body\": \"b\", \"scripts\": ["
				+ "{\"path\": \"scripts/totals.js\", \"content\": null}]}";
		String absentContent = "{\"description\": \"d\", \"body\": \"b\", \"scripts\": ["
				+ "{\"path\": \"scripts/totals.js\"}]}";

		assertThat(generator(explicitNull).generate("cold-store-goods-receipt", DESCRIPTION).scripts()).isEmpty();
		assertThat(generator(absentContent).generate("cold-store-goods-receipt", DESCRIPTION).scripts()).isEmpty();
	}

}
