/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.embabel.agent.core.ToolGroup;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * What this addon did with the configuration it was given, and what the rest of the
 * process did, as one line an operator can read without inferring it from the absence of
 * tool calls.
 *
 * <p>
 * <b>The distinction that matters is between off and unknown.</b> A group the deployment
 * switched off and a group the addon never heard of look identical from a run that was
 * offered nothing, and they call for different fixes. So each of the addon's two groups
 * is reported as registered by default, switched on, switched off, or off by default —
 * and any {@code smith.tools.<group>.enabled} naming a group this module has no recipe
 * for is listed by name rather than ignored, because a typo'd switch that silently
 * registers nothing is the same invisible failure this change exists to remove for steps.
 *
 * <p>
 * <b>Which source decided is a separate answer from what was decided.</b> A group can
 * arrive because a switch said so, because a preset said so, or because nothing said
 * anything, and the two configuration answers look the same in the four buckets above —
 * both register the group. So {@link #decidedBy()} names, per group, the property source
 * the value it got was read from: a preset's value arrives in a source of its own, placed
 * below the deployment's, so the source that answers is the source that decided and this
 * class does not have to reimplement the precedence to report it. A preset name this
 * addon does not define is listed by {@link #unrecognizedPresets()} rather than
 * half-applied: nothing was contributed under it, and the line says so with the presets
 * it does define, which is the same sentence the stray-switch list says about a switch
 * for an addon that is not on the classpath at all.
 *
 * <p>
 * <b>The registry is read, not just the switches.</b> The switches say what this module
 * was asked to do; only the bean map says what a run can actually resolve. Two things
 * come of reading it. A role that arrived with no switch of ours — {@code math} being the
 * one that matters, since it is upstream's own
 * {@code ToolGroupsConfiguration.mathToolGroup()} and nothing in this repo chose it — is
 * listed as not ours, so a reader cannot mistake somebody else's default for this
 * addon's. And a role a second claimant in the registry also answers is listed with that
 * claimant's source, because the platform's resolver takes the <em>first</em> group whose
 * role matches and builds its list from individual {@code ToolGroup} beans before the
 * {@code List<ToolGroup>} providers: our bean answers, and the other claimant is shadowed
 * rather than absent. The claim comes from a group that is actually present, not from a
 * property that could have built one — a deployment can carry upstream's includes entry
 * with nothing on the classpath to act on it, and calling that a shadow would report a
 * collision no run can observe. A second claimant on a role this addon does not register
 * at all shows up in {@link #notOurs()} instead, which is the same fact from the other
 * side.
 *
 * <p>
 * <b>What this cannot see.</b> Another addon's jar that registered nothing leaves no
 * trace this module can read — there is no registry of addons to consult — so the silence
 * reported here is this addon's own groups' silence, with the reason, plus the two
 * signals an unrelated addon does leave: a role in the registry that is not ours, and a
 * switch naming a group this module contains no recipe for. A deployment that needs every
 * addon on its classpath named needs a report per module; this line says what it can
 * prove.
 *
 * <p>
 * <b>The documented defaults are stated twice on purpose.</b> They live in the
 * {@code @ConditionalOnProperty} annotations, where an annotation cannot read a constant,
 * and here, where the report needs them to say how a role arrived. The boot test is what
 * keeps the two copies honest: it asserts, for each state, that the set of groups that
 * registered is the set this class claims registered — and that a boot containing nothing
 * but this addon's own beans reports nothing as not ours, which is the same claim about
 * the bean names listed in {@link #OUR_BEANS}.
 *
 * @author Ivan Rodriguez
 */
public final class CodingToolsBootReport {

	/** The switch suffix every group's gate reads. */
	static final String ENABLED_SUFFIX = ".enabled";

	/** The property prefix every group's switch sits under. */
	static final String PREFIX = "smith.tools.";

	/**
	 * The bean names of this module's {@code ToolGroup} beans, which is how the report
	 * tells its own roles from roles that arrived from elsewhere. The boot test keeps
	 * this honest: a boot holding nothing but this addon's beans must list nothing as not
	 * ours, so renaming a {@code @Bean} method without renaming it here fails a test
	 * rather than quietly labelling our own work as somebody else's.
	 */
	static final Set<String> OUR_BEANS = Set.of("fileToolGroup", "shellToolGroup");

	/**
	 * The bean name of this module's {@code List<ToolGroup>} provider bean, whose roles
	 * are also ours. Without it the composed roles an operator names in
	 * {@code smith.tools.expose.<role>} would be reported as roles that arrived from
	 * elsewhere — this addon's own work labelled as somebody else's, which is the exact
	 * error the bean-name rule exists to prevent. Upstream's provider bean is
	 * deliberately absent from this set, so its contents read as not ours.
	 */
	static final Set<String> OUR_PROVIDER_BEANS = Set.of("exposedToolGroups");

	/**
	 * Upstream's own operator-authored roles. Its {@code toToolGroup} builds an
	 * MCP-backed group per entry, so a claim from here is backed by whatever servers the
	 * deployment happens to be connected to.
	 */
	static final String UPSTREAM_INCLUDES_PREFIX = "embabel.agent.platform.tools.includes.";

	/**
	 * Each group this addon can register, and whether it registers with nothing
	 * configured.
	 */
	static final Map<String, Boolean> DOCUMENTED_DEFAULTS = Map.of(CodingToolGroups.FILE_ROLE, Boolean.TRUE,
			CodingToolGroups.SHELL_ROLE, Boolean.FALSE);

	private final List<String> registeredByDefault;

	private final List<String> switchedOn;

	private final List<String> switchedOff;

	private final List<String> offByDefault;

	private final Map<String, String> unknownSwitches;

	private final List<String> notOurs;

	private final Map<String, String> shadowedByUs;

	private final Map<String, String> decidedBy;

	private final List<String> unrecognizedPresets;

	private CodingToolsBootReport(List<String> registeredByDefault, List<String> switchedOn, List<String> switchedOff,
			List<String> offByDefault, Map<String, String> unknownSwitches, List<String> notOurs,
			Map<String, String> shadowedByUs, Map<String, String> decidedBy, List<String> unrecognizedPresets) {
		this.registeredByDefault = registeredByDefault;
		this.switchedOn = switchedOn;
		this.switchedOff = switchedOff;
		this.offByDefault = offByDefault;
		this.unknownSwitches = unknownSwitches;
		this.notOurs = notOurs;
		this.shadowedByUs = shadowedByUs;
		this.decidedBy = decidedBy;
		this.unrecognizedPresets = unrecognizedPresets;
	}

	/**
	 * Classify every switch the environment carries, against the groups this module
	 * contains, with no registry to consult.
	 * @param environment the environment the context was built with
	 * @return the report, whose two registry-derived answers are empty because there was
	 * nothing registered to read
	 */
	public static CodingToolsBootReport from(Environment environment) {
		return from(environment, Map.of(), Map.of());
	}

	/**
	 * Classify every switch the environment carries, and every group the context
	 * registered as a bean of its own.
	 * @param environment the environment the context was built with
	 * @param registered the {@code ToolGroup} beans by bean name, which is how the report
	 * separates its own roles from roles that arrived without a switch of ours
	 * @return the report
	 */
	public static CodingToolsBootReport from(Environment environment, Map<String, ToolGroup> registered) {
		return from(environment, registered, Map.of());
	}

	/**
	 * Classify every switch the environment carries, and every group the context can
	 * answer with — the ones published individually and the ones that arrive through a
	 * {@code List<ToolGroup>} provider bean, which is the channel upstream's
	 * {@code includedToolGroups} uses and the channel this addon's composed roles use
	 * too.
	 * @param environment the environment the context was built with
	 * @param registered the {@code ToolGroup} beans by bean name
	 * @param providerGroups the {@code List<ToolGroup>} beans by bean name, read for
	 * their roles only — asking a group for its tools would make an MCP-backed one shake
	 * hands with its server during start-up
	 * @return the report
	 */
	public static CodingToolsBootReport from(Environment environment, Map<String, ToolGroup> registered,
			Map<String, List<ToolGroup>> providerGroups) {
		List<String> registeredByDefault = new ArrayList<>();
		List<String> switchedOn = new ArrayList<>();
		List<String> switchedOff = new ArrayList<>();
		List<String> offByDefault = new ArrayList<>();
		Map<String, String> decidedBy = new TreeMap<>();
		for (Map.Entry<String, Boolean> documented : DOCUMENTED_DEFAULTS.entrySet()) {
			String group = documented.getKey();
			Boolean switched = configured(environment, group);
			if (switched == null) {
				decidedBy.put(group, "no switch set (documented default)");
				(documented.getValue() ? registeredByDefault : offByDefault).add(group);
			}
			else {
				decidedBy.put(group, decidedBySource(environment, group));
				(switched ? switchedOn : switchedOff).add(group);
			}
		}
		List<String> ours = new ArrayList<>(registeredByDefault);
		ours.addAll(switchedOn);
		List<ToolGroup> others = new ArrayList<>();
		registered.forEach((beanName, group) -> {
			if (!OUR_BEANS.contains(beanName)) {
				others.add(group);
			}
		});
		providerGroups.forEach((beanName, groups) -> {
			if (!OUR_PROVIDER_BEANS.contains(beanName)) {
				others.addAll(groups);
			}
		});
		return new CodingToolsBootReport(ordered(registeredByDefault), ordered(switchedOn), ordered(switchedOff),
				ordered(offByDefault), unknownSwitches(environment), notOurs(others, ours),
				shadowed(environment, ours, others), decidedBy, unrecognizedPresets(environment));
	}

	/**
	 * The groups that registered because nothing said otherwise.
	 * @return the role names registered by default
	 */
	public List<String> registeredByDefault() {
		return this.registeredByDefault;
	}

	/**
	 * The groups a property turned on.
	 * @return the role names switched on
	 */
	public List<String> switchedOn() {
		return this.switchedOn;
	}

	/**
	 * The groups a property turned off, which is a different answer from "never heard of
	 * it".
	 * @return the role names switched off
	 */
	public List<String> switchedOff() {
		return this.switchedOff;
	}

	/**
	 * The groups that are off unless asked, and were not asked.
	 * @return the role names off by default
	 */
	public List<String> offByDefault() {
		return this.offByDefault;
	}

	/**
	 * The {@code smith.tools.<group>.enabled} properties naming a group this module has
	 * no recipe for. Reported rather than ignored: nothing registered, and the run says
	 * there was nothing to enable.
	 * @return property name to the value it carries
	 */
	public Map<String, String> unknownSwitches() {
		return this.unknownSwitches;
	}

	/**
	 * The roles this context can answer that this addon did not register — arrived from
	 * elsewhere, with no switch of ours involved. Presence without a switch reads as
	 * presence here, which is the only honest way to report {@code math}: it is
	 * upstream's own bean, and a report that stayed silent about it would read as though
	 * this addon had put it there.
	 * @return the role names, sorted, empty when every group in the registry is ours
	 */
	public List<String> notOurs() {
		return this.notOurs;
	}

	/**
	 * The roles this addon registered that upstream's own includes map also names, and
	 * which property makes that claim. This addon's group is the one a step resolves
	 * against, so the other claimant is shadowed — a fact worth naming when the
	 * deployment's own configuration suggests it should have answered.
	 * @return role name to the shadowed source, in role order
	 */
	public Map<String, String> shadowedByUs() {
		return this.shadowedByUs;
	}

	/**
	 * Which source decided each group this addon contains. The four buckets above say
	 * what happened; this says who said so, which is the question a preset makes
	 * unavoidable — a group registered by a preset and a group registered by an explicit
	 * switch look the same from the registry, and an operator reading a boot they did not
	 * configure needs to know which of the two to go and change. The answer is where the
	 * value was read from rather than a second precedence rule: a preset's switches
	 * arrive in a property source of its own placed below the deployment's, so the first
	 * source carrying the switch is the source that decided, and this class is reporting
	 * the environment's ordering rather than repeating it.
	 * @return group name to the source that decided it, in group order
	 */
	public Map<String, String> decidedBy() {
		return this.decidedBy;
	}

	/**
	 * The preset the deployment asked for that this addon defines no such preset for.
	 * Nothing was applied under it — not a matching part, not a default part — so this is
	 * the whole of what it did, stated as a name plus the presets that would have been
	 * honoured. It is the preset-shaped version of {@link #unknownSwitches()}: a switch
	 * naming an addon that is not on the classpath at all, and a preset naming a bundle
	 * this addon does not contain, both mean there was nothing here to enable.
	 * @return one entry per unrecognised preset name, empty when none was asked for or
	 * the name was recognised
	 */
	public List<String> unrecognizedPresets() {
		return this.unrecognizedPresets;
	}

	/**
	 * This addon's groups that registered nothing, each with the reason — the question an
	 * operator asks second, which is not "what is there" but "why is the one I expected
	 * not there".
	 * @return one entry per silent group, as {@code role: reason}
	 */
	public List<String> registeredNothing() {
		List<String> silent = new ArrayList<>();
		this.switchedOff.forEach((role) -> silent.add(role + ": disabled by configuration"));
		this.offByDefault.forEach((role) -> silent.add(role + ": off by default, nothing asked for it"));
		return silent;
	}

	/**
	 * The one-line form, for the log line the start-up prints.
	 * @return the report as text
	 */
	public String describe() {
		StringBuilder line = new StringBuilder("coding addon: ");
		line.append("ours by default ").append(roles(this.registeredByDefault));
		line.append(", ours switched on ").append(roles(this.switchedOn));
		line.append(", ours disabled by configuration ").append(roles(this.switchedOff));
		line.append(", ours off by default ").append(roles(this.offByDefault));
		line.append(", registered nothing from ").append(roles(registeredNothing()));
		line.append(", not ours ").append(roles(this.notOurs));
		line.append(", ours shadowing ").append(this.shadowedByUs);
		line.append(", decided by ").append(this.decidedBy);
		if (!this.unknownSwitches.isEmpty()) {
			line.append(", switches naming no group this addon contains: ").append(this.unknownSwitches);
		}
		if (!this.unrecognizedPresets.isEmpty()) {
			line.append(", presets naming no group this addon contains: ").append(this.unrecognizedPresets);
		}
		return line.toString();
	}

	@Override
	public String toString() {
		return describe();
	}

	private static String roles(List<String> names) {
		return names.isEmpty() ? "[none]" : names.toString();
	}

	/**
	 * What a group's own switch says, if it says anything.
	 * @param environment the environment to read
	 * @param group the group name
	 * @return the switch value, or {@code null} when no property names it
	 */
	private static Boolean configured(Environment environment, String group) {
		String property = PREFIX + group + ENABLED_SUFFIX;
		String value = environment.getProperty(property);
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("on") || trimmed.equals("1");
	}

	/**
	 * Which property source decided a group. The sources are read in the order the
	 * environment holds them and the first one carrying the switch wins, which is what
	 * makes this a report rather than a second precedence rule: it asks the environment
	 * where the value came from instead of deciding again where it would have come from.
	 * A preset's own source sits below every source the deployment owns, so a deployment
	 * that set the switch is found first and the preset is named only when it was the one
	 * that answered.
	 * @param environment the environment to read
	 * @param group the group whose switch carries a value
	 * @return the deciding source, phrased so an operator can go and change the right
	 * thing
	 */
	private static String decidedBySource(Environment environment, String group) {
		String property = PREFIX + group + ENABLED_SUFFIX;
		if (environment instanceof ConfigurableEnvironment configurable) {
			for (PropertySource<?> source : configurable.getPropertySources()) {
				if (!(source instanceof EnumerablePropertySource<?> enumerable) || !carries(enumerable, property)) {
					continue;
				}
				return ToolGroupPresets.SOURCE_NAME.equals(source.getName())
						? "preset '" + preset(environment) + "' (" + ToolGroupPresets.PRESET_PROPERTY + ")"
						: "switch '" + property + "' from source '" + source.getName() + "'";
			}
		}
		// A value the beans resolved that no enumerable source will admit to. Naming the
		// preset here would be a guess about a source this scan could not read.
		return "a property source this scan cannot read";
	}

	/**
	 * Whether a source holds a group's switch, in either of the two spellings a container
	 * leaves it in.
	 * @param enumerable the source to ask
	 * @param property the switch in its dotted form
	 * @return true when the source answers for the value
	 */
	private static boolean carries(EnumerablePropertySource<?> enumerable, String property) {
		for (String name : enumerable.getPropertyNames()) {
			if (name.equalsIgnoreCase(property) || name.replace('_', '.').equalsIgnoreCase(property)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The preset the deployment asked for, in the case this addon's vocabulary uses.
	 * @param environment the environment to read
	 * @return the preset name, empty when none was set
	 */
	private static String preset(Environment environment) {
		return environment.getProperty(ToolGroupPresets.PRESET_PROPERTY, "").trim().toLowerCase();
	}

	/**
	 * The preset that was asked for and this addon defines nothing under. Listing it is
	 * the whole of what it did — the contributor wrote no switches under a name it does
	 * not know, so there is no half-applied state to describe, only a name to report
	 * alongside the ones that would have been honoured.
	 * @param environment the environment to read
	 * @return one entry naming the preset, empty when none was asked for or this addon
	 * defines it
	 */
	private static List<String> unrecognizedPresets(Environment environment) {
		String asked = preset(environment);
		if (asked.isEmpty() || ToolGroupPresets.named(asked).isPresent()) {
			return List.of();
		}
		return List.of(asked + ": this addon defines no such preset, so nothing here was applied under it; the "
				+ "presets it defines are " + ToolGroupPresets.names());
	}

	/**
	 * The roles the context can answer with that this addon's own groups do not already
	 * account for. Subtracting our roles is what leaves the honest remainder: a group
	 * that shares a role with ours is already named by {@link #shadowedByUs()}, and
	 * everything left is somebody else's, which is how {@code math} reads — upstream's
	 * bean, present with no switch of ours involved.
	 * @param others the groups this addon's beans did not produce, whether published
	 * individually or carried by a provider list
	 * @param ours the roles this addon registered
	 * @return the roles, sorted and deduplicated, so two claimants answering one role
	 * read as one name
	 */
	private static List<String> notOurs(List<ToolGroup> others, List<String> ours) {
		Set<String> roles = new TreeSet<>();
		others.forEach((group) -> roles.add(group.getMetadata().getRole()));
		ours.forEach(roles::remove);
		return new ArrayList<>(roles);
	}

	/**
	 * The roles this addon registered that a second claimant also answers with. The claim
	 * comes from the registry — a group that is really there — and the environment is
	 * read only to attribute it: when upstream's includes map carries an entry for the
	 * role, the source is named as that property, since that is what built the group. A
	 * claimant with no such entry is named by the provider and name its own metadata
	 * carries. Reading the registry rather than only the property matters: a deployment
	 * can carry the property with nothing to act on it, and a report that called that a
	 * shadow would be claiming a collision that no run can observe.
	 * @param environment the environment to read for upstream's includes map
	 * @param ours the roles this addon registered
	 * @param others the groups this addon's beans did not produce
	 * @return role to the shadowed source, in role order
	 */
	private static Map<String, String> shadowed(Environment environment, List<String> ours, List<ToolGroup> others) {
		Map<String, String> claims = new LinkedHashMap<>();
		for (String role : new TreeSet<>(ours)) {
			ToolGroup claimant = others.stream()
				.filter((group) -> role.equals(group.getMetadata().getRole()))
				.findFirst()
				.orElse(null);
			if (claimant == null) {
				continue;
			}
			String upstream = includesProperty(environment, role);
			claims.put(role,
					(upstream != null) ? upstream + " (MCP-backed, built by upstream from its includes map)"
							: "a group from provider '" + claimant.getMetadata().getProvider() + "' named '"
									+ claimant.getMetadata().getName() + "'");
		}
		return claims;
	}

	/**
	 * The property that makes upstream claim a role, if any does.
	 * @param environment the environment to scan
	 * @param role the role this addon registered
	 * @return the property name, or {@code null} when nothing claims the role from there
	 */
	private static String includesProperty(Environment environment, String role) {
		String prefix = UPSTREAM_INCLUDES_PREFIX + role + ".";
		if (!(environment instanceof ConfigurableEnvironment configurable)) {
			return null;
		}
		for (PropertySource<?> source : configurable.getPropertySources()) {
			if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
				continue;
			}
			for (String name : enumerable.getPropertyNames()) {
				if (name.regionMatches(true, 0, prefix, 0, prefix.length())) {
					return name;
				}
			}
		}
		return null;
	}

	/**
	 * Every {@code smith.tools.<group>.enabled} in the environment naming a group this
	 * addon does not contain. Both spellings are checked — {@code smith.tools.x.enabled}
	 * and the {@code SMITH_TOOLS_X_ENABLED} a container sets — because a switch an
	 * operator believes they made is worth naming whatever form they made it in.
	 * @param environment the environment to scan
	 * @return property name to value, in the order the sources carry them
	 */
	private static Map<String, String> unknownSwitches(Environment environment) {
		Map<String, String> unknown = new LinkedHashMap<>();
		if (!(environment instanceof ConfigurableEnvironment configurable)) {
			// Nothing enumerable to scan; the switches this addon owns were still
			// classified
			// above, which is the half the report cannot do without.
			return unknown;
		}
		for (PropertySource<?> source : configurable.getPropertySources()) {
			if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
				continue;
			}
			for (String name : enumerable.getPropertyNames()) {
				String group = groupOf(name);
				if (group == null || DOCUMENTED_DEFAULTS.containsKey(group)) {
					continue;
				}
				Object value = enumerable.getProperty(name);
				unknown.putIfAbsent(name, String.valueOf(value));
			}
		}
		return unknown;
	}

	/**
	 * The group a property name claims to switch, if it is a group switch at all.
	 * @param name the property name as its source carries it
	 * @return the group name, lower-cased, or {@code null} for anything else
	 */
	private static String groupOf(String name) {
		String flat = name.replace('_', '.');
		if (!flat.regionMatches(true, 0, PREFIX, 0, PREFIX.length()) || !flat.regionMatches(true,
				flat.length() - ENABLED_SUFFIX.length(), ENABLED_SUFFIX, 0, ENABLED_SUFFIX.length())) {
			return null;
		}
		String group = flat.substring(PREFIX.length(), flat.length() - ENABLED_SUFFIX.length());
		return (group.isEmpty() || group.contains(".")) ? null : group.toLowerCase();
	}

	/**
	 * A category in a stable order, so a boot's report does not depend on the order the
	 * documented map happens to iterate.
	 * @param names the names as classified
	 * @return the same names, sorted
	 */
	private static List<String> ordered(List<String> names) {
		return new ArrayList<>(new TreeSet<>(names));
	}

}
