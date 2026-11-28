/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.core.Action;
import com.embabel.agent.skills.script.ScriptTool;
import com.embabel.agent.spec.model.ActionSpec;
import com.embabel.agent.spec.model.PromptedActionSpec;
import com.embabel.agent.spec.model.StepSpec;
import com.embabel.agent.spec.model.StepSpecContext;
import com.embabel.agent.spec.persistence.StepSpecRepository;

/**
 * The {@link StepSpecRepository} the runtime hands the scope builder: the real repository
 * underneath, with every step it returns rewritten to name — and be given — the reference
 * that actually holds what it declared.
 *
 * <p>
 * <b>Why a wrapper and not a property.</b> {@code StepSpecAgentScopeBuilder} takes tool
 * groups and tools but no references, so the {@link StepSpecContext} an step is emitted
 * against carries an empty {@code availableReferences}. Upstream then resolves each
 * declared name against that list with a plain {@code map.get} and an {@code ifnull}
 * skip, which is why an unhandled declaration does not fail: it vanishes, and the run
 * behaves as though the pack carried no skill at all. This class closes both halves of
 * that — it rewrites the step's declared names into container names so the lookup hits,
 * and it appends those containers to the context so there is something to hit.
 *
 * <p>
 * <b>The wrapper is also the honest surface.</b> A deployed {@code AgentScope} publishes
 * no tool list, so nothing downstream can be asked what a step was offered. What is
 * assertable offline is the context this class puts in front of the step it wrapped,
 * which is what its test pins.
 *
 * <p>
 * <b>A name nothing carries is refused, by name.</b> Resolution is per definition: the
 * references {@link PackReferences} reads for it, which are its own skill folders, the
 * folder's shared set, and what its {@code references.yml} declares — so a name held only
 * by a sibling definition does not resolve, and the miss is recorded for the run report
 * instead of being left to the upstream skip that would hide it.
 *
 * @author Ivan Rodriguez
 */
public final class ReferenceAwareStepSpecRepository implements StepSpecRepository {

	private final StepSpecRepository delegate;

	private final Map<String, PackReferences> byStepName;

	private final Set<String> unresolved = new LinkedHashSet<>();

	private final Set<String> attached = new LinkedHashSet<>();

	private final Set<String> scriptOffer = new LinkedHashSet<>();

	private final Set<String> formOffer = new LinkedHashSet<>();

	/**
	 * Wraps a repository so each emitted step also carries the references its own pack
	 * can answer with.
	 * @param delegate the repository that actually loads the steps, consulted for every
	 * read and write
	 * @param byStepName each step name to the references its own definition can answer
	 * with — its skill folders and whatever its {@code references.yml} declares; a step
	 * missing from it is emitted with an unchanged reference list
	 */
	public ReferenceAwareStepSpecRepository(StepSpecRepository delegate, Map<String, PackReferences> byStepName) {
		this.delegate = delegate;
		this.byStepName = Map.copyOf(byStepName);
	}

	@Override
	public StepSpec<?> save(StepSpec<?> stepSpec) {
		return wrap(this.delegate.save(stepSpec));
	}

	@Override
	public Iterable<StepSpec<?>> saveAll(Iterable<? extends StepSpec<?>> stepSpecs) {
		List<StepSpec<?>> saved = new ArrayList<>();
		for (StepSpec<?> stepSpec : this.delegate.saveAll(stepSpecs)) {
			saved.add(wrap(stepSpec));
		}
		return saved;
	}

	@Override
	public Iterable<StepSpec<?>> findAll() {
		List<StepSpec<?>> found = new ArrayList<>();
		for (StepSpec<?> stepSpec : this.delegate.findAll()) {
			found.add(wrap(stepSpec));
		}
		return found;
	}

	/**
	 * The declared names that resolved to nothing, each already stated as the run should
	 * state it. Reported rather than skipped: upstream's own handling is a silent miss,
	 * so a pack that declares a skill it does not carry looks exactly like one that
	 * carries it.
	 * @return the unresolved declarations, in the order they were read
	 */
	public List<String> unresolved() {
		return List.copyOf(this.unresolved);
	}

	/**
	 * What each step ended up attached to, as {@code step -> containers}. A run that
	 * offered a skill and one that offered nothing are indistinguishable from outside a
	 * deployed {@code AgentScope} — it publishes no tool list — so what this class
	 * decided to attach is stated here and printed with the run.
	 * @return the attachments, in the order the steps were read
	 */
	public List<String> attached() {
		return List.copyOf(this.attached);
	}

	/**
	 * Which step was handed a script it could call, as {@code step -> what it can call}.
	 *
	 * <p>
	 * This is the per-step half of a statement the run otherwise makes only once, for the
	 * whole pack: {@link PackSkills} says whether a definition carries scripts and
	 * whether the selected engine offers them, which is true of the pack and not of any
	 * step in it. Whether <em>this</em> step could reach a script is decided here,
	 * because this is where a declaration becomes a reference handed to a step — and it
	 * is decided by asking the attached reference what it offers, not by inferring it
	 * from the engine, so a script the engine refused (see {@link PackSkills}) is not
	 * counted as offered.
	 * @return the per-step script offers, in the order the steps were read
	 */
	public List<String> scriptsOffered() {
		return List.copyOf(this.scriptOffer);
	}

	/**
	 * Which shape each reference a step was actually given ended up in, as
	 * {@code step forms names-and-shapes}.
	 *
	 * <p>
	 * <b>Why its own line and not a suffix on {@link #attached()}.</b> The two lines
	 * answer in different vocabularies: {@code attached} names the <em>container</em> a
	 * declaration resolved to, which is what upstream's lookup keys on, while this names
	 * what the step <em>declared</em>, which is what the shape was asked of —
	 * {@code ask-skills} and {@code notes} are the same reference under the two names,
	 * and only the declared one is a name {@code references.yml} can put behind a facade.
	 * The attachment line is also pinned by tests this change has no business repricing,
	 * so the shape is stated beside it rather than inside it.
	 * <p>
	 * The shape is read from {@link PackReferences#forms()} rather than inferred from
	 * whether a facade exists, and that map covers every carried name — a reference
	 * nobody asked about is {@code direct}, which is a fact about it and not a default
	 * standing in for an unanswered question.
	 * @return the per-reference shapes, in the order the steps were read
	 */
	public List<String> formsOffered() {
		return List.copyOf(this.formOffer);
	}

	/**
	 * Rewrite one step to name the containers that hold its declarations.
	 * @param stepSpec the step as the delegate returned it
	 * @return the step to deploy, unchanged when it declares nothing and nothing resolved
	 */
	private StepSpec<?> wrap(StepSpec<?> stepSpec) {
		if (!(stepSpec instanceof PromptedActionSpec action) || action.getReferences().isEmpty()) {
			return stepSpec;
		}
		PackReferences carried = this.byStepName.get(action.getName());
		List<LlmReference> attached = new ArrayList<>();
		List<String> containers = new ArrayList<>();
		List<String> forms = new ArrayList<>();
		for (String declared : action.getReferences()) {
			LlmReference holder = (carried != null) ? carried.carried().get(declared) : null;
			if (holder == null) {
				this.unresolved.add("step '" + action.getName() + "' declares reference '" + declared
						+ "' that neither its definition's skills nor its references file carries");
				continue;
			}
			if (!attached.contains(holder)) {
				attached.add(holder);
				containers.add(holder.getName());
				forms.add(declared + " " + carried.forms().get(declared));
			}
		}
		if (containers.isEmpty()) {
			return stepSpec;
		}
		this.attached.add(action.getName() + " -> " + containers);
		this.formOffer.add(action.getName() + " forms " + forms);
		this.scriptOffer.add(action.getName() + " " + callableScripts(attached));
		return new ResolvedAction(namesContainers(action, containers), List.copyOf(attached));
	}

	/**
	 * What the attached references actually offer a step in the way of scripts, asked of
	 * the references rather than inferred from the engine that was selected.
	 * @param attached the references this step is being given
	 * @return the phrase that completes the step's line
	 */
	private static String callableScripts(List<LlmReference> attached) {
		List<String> offering = attached.stream()
			.filter((reference) -> reference.tools().stream().anyMatch(ScriptTool.class::isInstance))
			.map(LlmReference::getName)
			.toList();
		return offering.isEmpty() ? "offers no script tool" : "can call a script via " + offering;
	}

	/**
	 * The step rewritten to declare containers rather than skill names, which is what
	 * makes upstream's lookup hit: it looks a reference up by the name a step declares,
	 * and that name is a container's, not a skill folder's.
	 * @param action the step as authored
	 * @param containers the container names to declare instead
	 * @return the spec that will be emitted
	 */
	private static PromptedActionSpec namesContainers(PromptedActionSpec action, List<String> containers) {
		return action.copy(action.getName(), action.getDescription(), action.getLlm(), action.getInputTypeNames(),
				action.getOutputTypeName(), action.getPre(), action.getPost(), action.getCost(), action.getValue(),
				action.getCanRerun(), action.getPrompt(), action.getToolGroups(), action.getTools(), containers,
				action.getNullable(), action.getExport(), action.getStepType());
	}

	/**
	 * The same context, additionally carrying the given references.
	 *
	 * <p>
	 * This is the context a wrapped step's {@code emit} receives, which is the surface
	 * worth pinning: a deployed {@code AgentScope} publishes no tool list, so nothing
	 * downstream can be asked what a step was offered. Everything the builder put in the
	 * context survives; the references are appended, never substituted, so a reference
	 * contributed by the scope builder itself is not lost.
	 * @param context the context the scope builder built
	 * @param references the references the step is to be given
	 * @return a copy whose {@code availableReferences} holds them, or the same context
	 * when there is nothing to add
	 */
	static StepSpecContext carrying(StepSpecContext context, List<LlmReference> references) {
		if (references.isEmpty()) {
			return context;
		}
		List<LlmReference> available = new ArrayList<>(context.getAvailableReferences());
		for (LlmReference reference : references) {
			if (available.stream().noneMatch((held) -> held.getName().equals(reference.getName()))) {
				available.add(reference);
			}
		}
		return context.copy(context.getName(), context.getDataDictionary(), context.getToolGroups(), context.getTools(),
				available);
	}

	/**
	 * A step that declares containers and is given them.
	 *
	 * <p>
	 * It is a distinct object rather than the {@code PromptedActionSpec} it wraps because
	 * nothing else in the deploy path can put a reference into the context: the scope
	 * builder takes tool groups and tools but no references, so the context it builds
	 * carries none. The scope builder reads only {@code findAll} and {@code emit} off the
	 * step, so substituting the interface for the class costs nothing.
	 */
	private record ResolvedAction(PromptedActionSpec spec, List<LlmReference> attached) implements ActionSpec {

		@Override
		public String getName() {
			return this.spec.getName();
		}

		@Override
		public String getDescription() {
			return this.spec.getDescription();
		}

		@Override
		public String getStepType() {
			return this.spec.getStepType();
		}

		@Override
		public Action emit(StepSpecContext context) {
			return this.spec.emit(carrying(context, this.attached));
		}

	}

}
