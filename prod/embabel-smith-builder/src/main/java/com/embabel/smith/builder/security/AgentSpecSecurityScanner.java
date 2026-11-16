/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;

/**
 * The builder's pre-publish security gate (design D4, extracted from POC 21): a static
 * scanner that vets a agent spec <em>before</em> it is published. It flags
 * prompt-injection text in the spec's own prompt, over-privileged tool groups, network
 * references, and secret-exfiltration patterns, and reports a risk verdict plus the
 * minimum trust tier at which the spec's tool groups are permitted. A
 * {@link Severity#CRITICAL} finding means the build is blocked.
 *
 * <p>
 * Pure static analysis over the {@link AgentSpec} model — no model endpoint, fully
 * offline. Matching is <em>normalized</em> (lowercased, punctuation/whitespace stripped),
 * so spacing/punctuation obfuscation does not carry a finding through; and, like POC 21's
 * scanner, it is a pattern matcher, not a semantic detector — a novel rephrasing outside
 * the pattern set can slip through. That is the honest limit of a non-LLM install-time
 * scan.
 *
 * @author Ivan Rodriguez
 */
public class AgentSpecSecurityScanner {

	/**
	 * Normalized prompt-injection phrases (lowercase, alphanumerics only). A spec whose
	 * <em>own</em> prompt carries one of these is trying to hijack the agent that loads
	 * it — the "spec as an attack vector" case (doc 02 §5.3, Schmotz et al.).
	 */
	private static final List<String> INJECTION_PATTERNS = List.of("ignorepreviousinstructions", "ignoreallprevious",
			"disregardpreviousinstructions", "disregardtheabove", "overrideyourinstructions", "revealyoursystemprompt",
			"printyourinstructions", "youarenow", "newinstructions", "donottelltheuser", "withouttellingtheuser");

	/**
	 * Tool groups that grant host-level or write capability — flagged as high-privilege.
	 */
	private static final List<String> HIGH_PRIVILEGE_GROUPS = List.of("shell", "file");

	/** Network-reach markers: a URL scheme or a fetch command. */
	private static final List<String> NETWORK_MARKERS = List.of("http://", "https://", "curl ", "wget ");

	/**
	 * High-precision secret markers (kept precise to avoid false positives on benign
	 * text).
	 */
	private static final List<String> SECRET_MARKERS = List.of("api key", "api_key", "apikey", "client secret",
			"access token", "auth token", "credential", "private key", "process.env", ".env", ".aws/credentials");

	private final TrustTierPolicy tiers = new TrustTierPolicy();

	/**
	 * Scans a agent spec and returns its findings, verdict, and recommended load tier.
	 * @param pack the agent spec to scan.
	 * @return the scan report with findings, verdict, and recommended tier.
	 */
	public ScanReport scan(AgentSpec pack) {
		List<Finding> findings = new ArrayList<>();
		List<String> prompts = allPrompts(pack);

		findings.addAll(textFindings(prompts));
		findings.addAll(toolGroupFindings(pack));
		Finding chain = untrustedInputToShellFinding(pack);
		if (chain != null) {
			findings.add(chain);
		}

		TrustTier tier = minimumTier(pack);
		ScanVerdict verdict = verdict(findings);
		return new ScanReport(pack.name(), findings, verdict, tier);
	}

	/**
	 * Scans every pack a process publishes as one artifact. A process is published — and
	 * run — as a unit, so one pack carrying a critical finding blocks the whole build
	 * rather than being outvoted by the packs that came back clean; the recommended tier
	 * is the highest any pack needs, since the process can only run at a tier that
	 * permits all of them.
	 * @param name the artifact name to report the findings against.
	 * @param packs the packs of the process, in run order.
	 * @return the merged scan report with findings, verdict, and recommended tier.
	 */
	public ScanReport scanProcess(String name, List<AgentSpec> packs) {
		List<Finding> findings = new ArrayList<>();
		TrustTier tier = TrustTier.T1_COMMUNITY;
		for (AgentSpec pack : packs) {
			ScanReport report = scan(pack);
			findings.addAll(report.findings());
			if (report.recommendedTier().ordinal() > tier.ordinal()) {
				tier = report.recommendedTier();
			}
		}
		return new ScanReport(name, findings, verdict(findings), tier);
	}

	/**
	 * Scans the text of an artifact that is not an agent spec — the skill target's
	 * {@code SKILL.md} — with the same injection, secret-exfiltration and network checks
	 * a pack's prompts are vetted with. A skill declares no tool groups, so the
	 * tool-group and untrusted-input-to-shell checks have nothing to examine and the
	 * instructions-only tier permits it.
	 * @param name the artifact name to report the findings against.
	 * @param text the full text to scan.
	 * @return the scan report with findings, verdict, and recommended tier.
	 */
	public ScanReport scanText(String name, String text) {
		List<Finding> findings = textFindings(List.of(text));
		return new ScanReport(name, findings, verdict(findings), TrustTier.T1_COMMUNITY);
	}

	/**
	 * Every prompt body in the pack (the attack surface a static scan can see).
	 * @param pack the agent spec whose prompts are collected.
	 * @return the prompt bodies of the spec's steps.
	 */
	private List<String> allPrompts(AgentSpec pack) {
		List<String> prompts = new ArrayList<>();
		for (ActionStepSpec step : pack.steps()) {
			if (step.prompt() != null) {
				prompts.add(step.prompt());
			}
		}
		return prompts;
	}

	/**
	 * The text-only checks: prompt injection, secret exfiltration, and network reach.
	 * @param texts the texts to scan.
	 * @return the findings raised over those texts.
	 */
	private List<Finding> textFindings(List<String> texts) {
		List<Finding> findings = new ArrayList<>();
		findings.addAll(injectionFindings(texts));
		findings.addAll(secretExfilFindings(texts));
		findings.addAll(networkFindings(texts));
		return findings;
	}

	private List<Finding> injectionFindings(List<String> prompts) {
		List<Finding> out = new ArrayList<>();
		for (String prompt : prompts) {
			String normalized = normalize(prompt);
			for (String pattern : INJECTION_PATTERNS) {
				if (normalized.contains(pattern)) {
					out.add(new Finding(Severity.CRITICAL, Finding.CAT_PROMPT_INJECTION,
							"Spec prompt contains a prompt-injection marker", pattern));
					break;
				}
			}
		}
		return out;
	}

	private List<Finding> secretExfilFindings(List<String> prompts) {
		List<Finding> out = new ArrayList<>();
		for (String prompt : prompts) {
			String lower = prompt.toLowerCase(Locale.ROOT);
			for (String marker : SECRET_MARKERS) {
				if (lower.contains(marker)) {
					out.add(new Finding(Severity.CRITICAL, Finding.CAT_SECRET_EXFIL,
							"Spec prompt references a secret or credential", marker));
					break;
				}
			}
		}
		return out;
	}

	private List<Finding> networkFindings(List<String> prompts) {
		List<Finding> out = new ArrayList<>();
		for (String prompt : prompts) {
			String lower = prompt.toLowerCase(Locale.ROOT);
			for (String marker : NETWORK_MARKERS) {
				if (lower.contains(marker)) {
					out.add(new Finding(Severity.WARN, Finding.CAT_NETWORK_REFERENCE,
							"Spec prompt reaches an external network source", marker.trim()));
					break;
				}
			}
		}
		return out;
	}

	private List<Finding> toolGroupFindings(AgentSpec pack) {
		List<Finding> out = new ArrayList<>();
		for (String group : new TreeSet<>(declaredToolGroups(pack))) {
			if (HIGH_PRIVILEGE_GROUPS.contains(group)) {
				out.add(new Finding(Severity.WARN, Finding.CAT_HIGH_PRIVILEGE_TOOL,
						"Spec declares a high-privilege tool group", group));
			}
		}
		return out;
	}

	/**
	 * Flags the injection-to-execution chain: untrusted {@code UserInput} feeding a step
	 * that also declares the {@code shell} tool group (doc 02 §5.3 — "a benign approval
	 * can carry over to harmful actions").
	 * @param pack the agent spec to check for the chain.
	 * @return the finding if the chain is present, or null.
	 */
	private Finding untrustedInputToShellFinding(AgentSpec pack) {
		boolean readsUserInput = pack.steps()
			.stream()
			.anyMatch((step) -> step.inputTypeNames().contains(SampleAgentSpecs.USER_INPUT));
		boolean hasShell = declaredToolGroups(pack).contains("shell");
		if (readsUserInput && hasShell) {
			return new Finding(Severity.WARN, Finding.CAT_UNTRUSTED_INPUT_TO_SHELL,
					"Untrusted user input reaches a shell tool group", "UserInput -> shell");
		}
		return null;
	}

	private List<String> declaredToolGroups(AgentSpec pack) {
		List<String> groups = new ArrayList<>();
		pack.steps().forEach((step) -> groups.addAll(step.toolGroups()));
		return groups;
	}

	/**
	 * The lowest tier whose allowlist covers every tool group the spec declares.
	 * @param pack the agent spec whose declared tool groups are checked.
	 * @return the lowest tier permitting all declared tool groups.
	 */
	private TrustTier minimumTier(AgentSpec pack) {
		for (TrustTier tier : TrustTier.values()) {
			if (this.tiers.check(pack, tier).allowed()) {
				return tier;
			}
		}
		return TrustTier.T4_PLATFORM;
	}

	private ScanVerdict verdict(List<Finding> findings) {
		if (findings.stream().anyMatch(Finding::isCritical)) {
			return ScanVerdict.BLOCK;
		}
		if (findings.stream().anyMatch(Finding::isWarning)) {
			return ScanVerdict.REVIEW;
		}
		return ScanVerdict.SAFE;
	}

	/**
	 * Lowercase and strip every non-alphanumeric character, so obfuscation is
	 * neutralized.
	 * @param input the text to normalize.
	 * @return the lowercased alphanumeric-only form of the text.
	 */
	private static String normalize(String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

}
