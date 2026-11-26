/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
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
 * <p>
 * A published skill is scanned as documents <em>plus scripts</em>
 * ({@link #scanSkill(String, List, Map)}): a {@code scripts/*.js} file is code that
 * executes in the reader's JVM, so it gets its own construct checks — host access,
 * network reach, and a path that leaves the skill's own folder — on top of the text
 * checks, each with a finding code of its own. Those checks are the script sandbox's
 * contract read backwards, which is why they are static: the runtime tier gate they would
 * otherwise lean on resolves nothing in this builder.
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

	/**
	 * Constructs that reach the host from inside a script: the module loader, the host
	 * class and package namespaces, a subprocess, the global object reached for by way of
	 * an escape, and runtime-generated code. Each is denied by the engine's sandbox
	 * ({@code skill/script/}), so a script carrying one was written against a promise the
	 * runtime will not keep.
	 */
	private static final List<String> SCRIPT_HOST_MARKERS = List.of("java.type(", "java.from(", "java.to(", "packages.",
			"require(", "import(", "child_process", "process.binding", "globalthis.", "eval(", "new function(");

	/** Network APIs a script might call. The engine gives a script no network at all. */
	private static final List<String> SCRIPT_NETWORK_MARKERS = List.of("fetch(", "xmlhttprequest", "websocket",
			"net.connect", "dgram.", "http.request", "https.request");

	/**
	 * Path markers that leave the skill folder. The engine binds {@code readFile} and
	 * {@code writeFile} to the skill's own directory, so a script that walks up out of it
	 * is naming a file the published artifact does not contain.
	 */
	private static final List<String> SCRIPT_PATH_MARKERS = List.of("../");

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
		return scanText(name, List.of(text));
	}

	/**
	 * The same text checks over the several documents that make up one artifact, all
	 * reported against one name. The skill target publishes two model-authored documents
	 * (its {@code SKILL.md} and the {@code evals.json} it was validated against), and an
	 * artifact is not vetted until every document it publishes has been, so the scan
	 * takes the set rather than assuming a single text.
	 * @param name the artifact name to report the findings against.
	 * @param texts the documents to scan.
	 * @return the scan report with findings, verdict, and recommended tier.
	 */
	public ScanReport scanText(String name, List<String> texts) {
		List<Finding> findings = textFindings(texts);
		return new ScanReport(name, findings, verdict(findings), TrustTier.T1_COMMUNITY);
	}

	/**
	 * Scans a published skill: its two model-authored documents <em>and</em> the
	 * JavaScript it publishes beside them. A script is code that runs in the reader's
	 * JVM, so it is the one artifact in this builder whose payload executes, and a skill
	 * is not vetted until the code has been read too — the same reasoning that made
	 * {@code evals.json} part of this scan rather than an exception to it.
	 * <p>
	 * The script checks are the sandbox's own contract read backwards. The engine runs a
	 * script with host class lookup, environment, threads, processes and network all
	 * denied, and file access bound to the skill's own folder; so a script that reaches
	 * for a module loader, a host class, a subprocess, a socket or a parent-directory
	 * path is written outside the contract it will be run under, and is refused rather
	 * than shipped for the runtime to discover. Each is a {@link Severity#CRITICAL}
	 * finding with its own code, so the refusal names what it objected to. The text
	 * checks then run over the script source as well, because a script is also a document
	 * the model wrote.
	 * <p>
	 * A skill that publishes scripts is published at a tier that permits execution; see
	 * {@link #skillTier(Map)}. This is the trust tier's one say over a skill's executable
	 * half, and it is a report rather than a gate.
	 * @param name the artifact name to report the findings against.
	 * @param texts the documents to scan.
	 * @param scriptsByPath the scripts to scan, keyed by the path they publish to, so a
	 * finding names the file it came from.
	 * @return the scan report with findings, verdict, and recommended tier.
	 */
	public ScanReport scanSkill(String name, List<String> texts, Map<String, String> scriptsByPath) {
		List<Finding> findings = new ArrayList<>(textFindings(texts));
		for (Map.Entry<String, String> script : new TreeMap<>(scriptsByPath).entrySet()) {
			findings.addAll(scriptFindings(script.getKey(), script.getValue()));
		}
		return new ScanReport(name, findings, verdict(findings), skillTier(scriptsByPath));
	}

	/**
	 * The tier a published skill asks its reader to run it at.
	 * <p>
	 * A skill that publishes JavaScript is not the same kind of artifact as an
	 * instructions-only document: whatever the sandbox denies at execution, the reader is
	 * being asked to run code a model wrote, and a published claim that says "community
	 * tier, no tool access" about that is a claim about a document this one is not.
	 * {@code T3_PARTNER} is the lowest tier whose allowlist covers running files and
	 * commands, and it is not {@code T4_PLATFORM} because the engine denies host-level
	 * execution — a tier naming T4 would overstate what the skill asks for.
	 * <p>
	 * This is a report, not a gate. A tier admits a source by the tool-group roles its
	 * packs declare, and a script declares none, so no loader can consult a tier about
	 * one (design D3). The pack-side rule is the separate
	 * {@link #minimumTier(AgentSpec)}; skills were never packs, and the per-spec tier
	 * rows on the record are about packs.
	 * @param scriptsByPath the scripts the skill publishes, keyed by path.
	 * @return the tier to publish the skill at.
	 */
	private static TrustTier skillTier(Map<String, String> scriptsByPath) {
		return scriptsByPath.isEmpty() ? TrustTier.T1_COMMUNITY : TrustTier.T3_PARTNER;
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

	/**
	 * The checks over one published script: the text checks, reported against the file it
	 * sits in, plus the three constructs the script sandbox exists to deny.
	 * @param path the path the script publishes to.
	 * @param source the JavaScript source.
	 * @return the findings raised over that script.
	 */
	private List<Finding> scriptFindings(String path, String source) {
		List<Finding> out = new ArrayList<>(attributed(path, textFindings(List.of(source))));
		out.addAll(markerFindings(path, source, SCRIPT_HOST_MARKERS, Finding.CAT_SCRIPT_HOST_ACCESS,
				"Skill script reaches the host outside its sandbox"));
		out.addAll(markerFindings(path, source, SCRIPT_NETWORK_MARKERS, Finding.CAT_SCRIPT_NETWORK,
				"Skill script reaches the network, which the script sandbox denies"));
		out.addAll(markerFindings(path, source, SCRIPT_PATH_MARKERS, Finding.CAT_SCRIPT_PATH_ESCAPE,
				"Skill script names a path outside the skill's own folder"));
		return out;
	}

	/**
	 * One marker list over one script. Matching is lowercased but not normalized the way
	 * prompt text is: these are API names, and an obfuscated {@code f e t c h} is not a
	 * call the engine can make either. The first hit per class is enough to block, so the
	 * report names the file and the construct rather than every occurrence.
	 * @param path the path the script publishes to.
	 * @param source the JavaScript source.
	 * @param markers the constructs that class blocks.
	 * @param category the finding code to report.
	 * @param message the finding message to report.
	 * @return one finding when the script carries one of the constructs, else none.
	 */
	private List<Finding> markerFindings(String path, String source, List<String> markers, String category,
			String message) {
		String lower = source.toLowerCase(Locale.ROOT);
		for (String marker : markers) {
			if (lower.contains(marker)) {
				return List.of(new Finding(Severity.CRITICAL, category, message, path + ": " + marker));
			}
		}
		return List.of();
	}

	/**
	 * Re-reports the document-level findings against the script file they were found in,
	 * so a refusal says which of the published files carried it.
	 * @param path the path the script publishes to.
	 * @param findings the findings raised over its text.
	 * @return the same findings with the file named in the evidence.
	 */
	private static List<Finding> attributed(String path, List<Finding> findings) {
		return findings.stream()
			.map((finding) -> new Finding(finding.severity(), finding.category(), finding.message(),
					path + ": " + finding.evidence()))
			.toList();
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
