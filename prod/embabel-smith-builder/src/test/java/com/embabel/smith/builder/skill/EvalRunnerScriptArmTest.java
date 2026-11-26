/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.skills.script.NoOpExecutionEngine;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.builder.core.ReferenceAwarePromptCaller;
import com.embabel.smith.builder.core.SpecPromptCaller;
import com.embabel.smith.builder.skill.script.GraalJsSkillScriptExecutionEngine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The pair that makes §1 and §2 one feature: the same builder, the same skill, the same
 * two eval arms, differing only in whether {@code smith.builder.skill-scripts.enabled}
 * installed the script engine.
 *
 * <p>
 * <b>Why the seam is the eval arm.</b> Nothing in {@code prod/} assembles a
 * {@code Skills} (design D8), so there was no existing line to hand the engine to. The
 * one place the builder puts a generated skill in front of a model is
 * {@link EvalRunner}'s with-skill arm — and that arm is prompt substitution, so the
 * honest way to let the builder run its skill's scripts is to attach the skill as an
 * Embabel reference to that call and let the framework's own tool list decide what the
 * model can execute. The generator was the other candidate and was rejected: at
 * generation time the scripts do not exist yet, so there is nothing for a script tool to
 * run.
 *
 * <p>
 * <b>What the off state proves, and what it does not.</b> With no engine the arm carries
 * no reference at all — not an inert reference, nothing — which is why the lift numbers
 * taken before this switch existed still mean what they meant. With the engine the arm
 * carries a reference whose tools include {@code <skill>_<script>}: that is the tool
 * <em>offered</em>. Whether a model then calls it is a live-endpoint question these
 * offline tests cannot answer, and it is reported rather than assumed either way. The
 * off-state case enforces its absence at the recorder: the reference-carrying call fails
 * the test the moment it arrives there, so a leak names the arm that leaked instead of
 * surfacing as a null the runner trips over further on.
 *
 * <p>
 * The baseline arm is asserted alongside every case: the reference belongs to the
 * with-skill arm only, so the comparison never becomes "two armed runs" against "no
 * skill". The judge is a separate caller here so that what this records is exactly the
 * two arm calls.
 *
 * @author Ivan Rodriguez
 */
class EvalRunnerScriptArmTest {

	private static final String WITH_MARKER = "WITH-ARM";

	private static final String WITH_PROMPT = WITH_MARKER + "\n{skill}";

	private static final String BASELINE_PROMPT = "BASELINE-ARM";

	private static final String SKILL_NAME = "cold-store-goods-receipt";

	private static final String SCRIPT_TOOL = "cold-store-goods-receipt_check-temperature";

	private static final String SCRIPTED_BODY = "## Workflow\n1. Read the carrier temperature record.\n"
			+ "2. Run scripts/check-temperature.js and quote what it printed.\n";

	private static final String SCRIPT = "const reading = Number(stdin.trim());\n"
			+ "if (!Number.isFinite(reading)) {\n" + "  console.error('no temperature reading on stdin');\n"
			+ "  process.exit(2);\n" + "}\n"
			+ "console.log(reading > 4 ? 'reject and escalate' : 'accept and file the record');\n";

	private static final Skill SKILL = new Skill(SKILL_NAME, "Receive chilled goods and reject a temperature breach.",
			SCRIPTED_BODY, List.of(new SkillScript("scripts/check-temperature.js", SCRIPT)));

	private static final EvalSuite EVALS = new EvalSuite(SKILL_NAME, List.of(new EvalSuite.Eval(1,
			"receive a chilled pallet", "accepted after a check", List.of(), List.of("must apply FEFO"))));

	/**
	 * The judge, kept off the recorder so a recorded request is always one of the two
	 * arms.
	 */
	private static final SpecPromptCaller JUDGE = (system, user) -> "{\"expectations\": ["
			+ "{\"text\": \"a\", \"passed\": true, \"evidence\": \"e\"}, {\"text\": \"b\", \"passed\": true, \"evidence\": \"e\"}]}";

	/**
	 * The with-skill system prompt both states send: the skill's own SKILL.md,
	 * substituted.
	 */
	private static String withSystem() {
		return WITH_PROMPT.replace("{skill}", SKILL.toSkillMd());
	}

	@Test
	void aBuildWithoutTheEngineAttachesNoReferenceToEitherArm() {
		// A recorder that refuses the reference-carrying call the moment it arrives, so
		// the absence this case is about is asserted rather than inferred from a null
		// read afterwards.
		RecordingCaller model = new RecordingCaller(false);

		run(model, null);

		// The switch-off state is an absence, and it is the arm's own request that shows
		// it: both arms were made, in the order the runner makes them, and neither
		// reached the call that attaches something. Not "carries an inert pack" —
		// nothing to inspect.
		assertThat(model.systems()).containsExactly(withSystem(), BASELINE_PROMPT);
		assertThat(model.referenceArms()).as("neither arm used the reference-carrying call").isEmpty();
	}

	@Test
	void aBuildWithTheEngineOffersTheSkillScriptAsAToolOnTheWithSkillArm() {
		RecordingCaller model = new RecordingCaller();

		run(model, new GraalJsSkillScriptExecutionEngine());

		// Same two arms and the same prompts; the only difference is what the with-skill
		// call carries. The baseline call still carries nothing, so the arms are still
		// "skill" against "no skill".
		assertThat(model.systems()).containsExactly(withSystem(), BASELINE_PROMPT);
		assertThat(model.referenceArms())
			.as("only the with-skill arm reached the reference-carrying call, so the baseline still carries nothing")
			.containsExactly("with-skill");
		LlmReference withSkill = model.references().get(0);
		assertThat(withSkill).as("the with-skill arm carries the skill as a reference").isNotNull();
		// The folder behind it really loaded: the skill activates and its body comes
		// back,
		// so the missing-or-present tool below is a decision and not an unloaded pack.
		// Both
		// are read after the run finished, which is also the assertion that the host's
		// scratch folder outlived the call: Embabel lists a skill's scripts from disk
		// when
		// it builds a request, so a host that deleted its folder at the end of the run
		// would report this list empty while the skill still activated.
		assertThat(activate(withSkill)).contains("Read the carrier temperature record");
		assertThat(toolNames(withSkill)).contains(SCRIPT_TOOL);
	}

	@Test
	void anEngineThatClaimsNoLanguageLeavesTheSameArmWithoutAnyScriptTool() {
		RecordingCaller model = new RecordingCaller();

		run(model, NoOpExecutionEngine.INSTANCE);

		// The containment half of the switch, on the builder's own path rather than a
		// reader's: the host is installed and the reference is attached either way, so
		// the
		// script stays text purely because Embabel filters a skill's scripts by the
		// engine's supportedLanguages(). The skill still loaded and still activated, so
		// this is not a folder that failed to read.
		assertThat(model.references().get(0)).isNotNull();
		assertThat(activate(model.references().get(0))).contains("Read the carrier temperature record");
		assertThat(toolNames(model.references().get(0))).doesNotContain(SCRIPT_TOOL).contains("activate");
		assertThat(model.referenceArms()).containsExactly("with-skill");
	}

	private static void run(RecordingCaller model, SkillScriptExecutionEngine engine) {
		SkillScriptHost host = (engine != null) ? new SkillScriptHost(engine) : null;
		new EvalRunner(model, new SkillGrader(JUDGE, "GRADE"), WITH_PROMPT, BASELINE_PROMPT, host).run(SKILL, EVALS);
	}

	private static List<String> toolNames(LlmReference reference) {
		return reference.tools().stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

	/**
	 * What the platform gets when the model activates the skill by name — the control
	 * that the reference holds a loaded skill rather than an empty pack.
	 * @param reference the reference the arm carried.
	 * @return the activation text, or the not-found answer.
	 */
	private static String activate(LlmReference reference) {
		Tool activate = reference.tools()
			.stream()
			.filter((tool) -> tool.getDefinition().getName().equals("activate"))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("the reference offers no activate tool"));
		Tool.Result result = activate.call("{\"name\": \"" + SKILL_NAME + "\"}");
		if (result instanceof Tool.Result.Error error) {
			return "[tool error] " + error.getMessage();
		}
		if (result instanceof Tool.Result.Text text) {
			return text.getContent();
		}
		return String.valueOf(result);
	}

	/**
	 * The model the two arms go through, recording which call each arm arrived by and
	 * refusing, on arrival, a reference-carrying call the case forbids. It implements the
	 * reference-carrying call rather than standing in for it with a lambda, which is the
	 * point: a lambda fake cannot see a reference it was never given a method to receive.
	 * The refusal belongs here for the same reason — the off state's claim is that this
	 * method is never entered, and a recorder that only wrote the absence down to a list
	 * would let a violation surface as whatever the runner tripped over next instead of
	 * as an assertion naming the arm that leaked.
	 */
	private static final class RecordingCaller implements SpecPromptCaller, ReferenceAwarePromptCaller {

		private final List<String> systems = new ArrayList<>();

		private final List<LlmReference> references = new ArrayList<>();

		private final List<String> referenceArms = new ArrayList<>();

		private final boolean referencesAllowed;

		RecordingCaller() {
			this(true);
		}

		RecordingCaller(boolean referencesAllowed) {
			this.referencesAllowed = referencesAllowed;
		}

		@Override
		public String call(String system, String user) {
			this.systems.add(system);
			this.references.add(null);
			return answer(system);
		}

		@Override
		public String call(String system, String user, LlmReference reference) {
			this.systems.add(system);
			this.references.add(reference);
			this.referenceArms.add(arm(system));
			if (!this.referencesAllowed) {
				return fail("the " + arm(system) + " arm reached the reference-carrying call, attaching "
						+ attached(reference) + ", on a build that installed no script engine");
			}
			return answer(system);
		}

		private static String arm(String system) {
			return system.startsWith(WITH_MARKER) ? "with-skill" : "baseline";
		}

		private static String answer(String system) {
			return system.startsWith(WITH_MARKER) ? "PASS the with-skill answer" : "FAIL the baseline answer";
		}

		private static String attached(LlmReference reference) {
			return (reference == null) ? "no reference"
					: "a reference offering " + reference.tools().size() + " tool(s)";
		}

		private List<String> systems() {
			return List.copyOf(this.systems);
		}

		private List<LlmReference> references() {
			// An ArrayList on purpose: an arm that attached nothing is recorded as a
			// null,
			// and List.copyOf refuses to hold the very absence these tests are about.
			return new ArrayList<>(this.references);
		}

		private List<String> referenceArms() {
			return List.copyOf(this.referenceArms);
		}

	}

}
