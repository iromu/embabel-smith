/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.smith.builder.core.AgentSpecNormalizer;
import com.embabel.smith.builder.security.AgentSpecSecurityScanner;
import com.embabel.smith.builder.security.Finding;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.common.model.AgentSpec;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The {@code security} tool group: the builder's own static scan, as <em>findings</em>,
 * for a step drafting a spec.
 *
 * <p>
 * <b>What is deliberately missing.</b> {@link ScanReport#recommendedTier()} is not
 * rendered, and neither is the verdict. A tier is a decision the host makes, and a step
 * that reads "this would be T3" has been handed a permission it cannot earn:
 * {@code TrustTierPolicy} filters the <em>declared strings</em> on a spec, so the
 * recommendation is not evidence that anything was granted (design D7, and the open item
 * in {@code docs/12-backlog.md}). What the step gets is the same list the publish-time
 * scanner produces — category, severity, message, evidence — so it can stop writing the
 * thing that would be flagged. Deciding what a finding costs stays with the gate that
 * already decides it.
 *
 * @author Ivan Rodriguez
 */
@Configuration
public class SecurityScanToolGroup {

	/** The tool-group role a pack step declares to see scan findings. */
	public static final String ROLE = "security";

	/** The tool name: the scanner, reported as findings. */
	public static final String SCAN_SPEC = "scan_spec";

	/** The argument's name: the spec the step is about to return. */
	public static final String SPEC_JSON = "specJson";

	private static final ObjectMapper JSON = new ObjectMapper()
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	/**
	 * The group the platform registers under {@link #ROLE}.
	 * @param surface what the driver opened for this build; its memo answers an identical
	 * repeat of the call
	 * @return the {@code security} tool group
	 */
	@Bean
	public ToolGroup securityScanTools(ToolSurface surface) {
		return ToolGroups.ofTools(ROLE, "Report what the builder's security scan finds in the spec you are about to "
				+ "return. Findings only: no verdict and no trust tier, because those are the build's decision and not "
				+ "yours.", List.of(scanSpec(surface)));
	}

	private static Tool scanSpec(ToolSurface surface) {
		return Tool.Companion.of(SCAN_SPEC,
				"Run the builder's static security scan over a spec and report its findings (prompt injection "
						+ "patterns, secret references, network references, high-privilege tool declarations). The "
						+ "argument is the spec JSON you are about to return. Findings only — the tier and the verdict "
						+ "are not reported here.",
				Tool.InputSchema.of(Tool.Parameter.string(SPEC_JSON,
						"The agent spec this step is about to return, as " + "its JSON document", true)),
				Tool.Metadata.DEFAULT, (Tool.Function) (arguments) -> Tool.Result.text(ToolOutput.cap(surface
					.remembered(SCAN_SPEC, arguments, () -> findings(ToolArguments.string(arguments, SPEC_JSON))))));
	}

	/**
	 * The scan's findings for a spec, as text.
	 * @param specJson the spec as the model sent it
	 * @return one line per finding, or the reason there are none
	 */
	static String findings(String specJson) {
		if (specJson.isBlank()) {
			return SCAN_SPEC + " needs the '" + SPEC_JSON + "' argument: the agent spec JSON this step is about to "
					+ "return.";
		}
		AgentSpec spec;
		try {
			spec = JSON.readValue(AgentSpecNormalizer.extractJson(specJson), AgentSpec.class);
		}
		catch (Exception ex) {
			return SCAN_SPEC + " could not read that as an agent spec: " + ex.getMessage();
		}
		ScanReport report = new AgentSpecSecurityScanner().scan(spec);
		if (report.findings().isEmpty()) {
			return "No security findings in '" + report.specName() + "'.";
		}
		List<String> lines = new ArrayList<>();
		lines.add(report.findings().size() + " security findings (what the build will weigh is the gate's decision, "
				+ "not this list):");
		report.findings().forEach((finding) -> lines.add(describe(finding)));
		return String.join("\n", lines);
	}

	private static String describe(Finding finding) {
		return finding.severity() + " " + finding.category() + " — " + finding.message()
				+ ((finding.evidence() == null || finding.evidence().isBlank()) ? "" : " [" + finding.evidence() + "]");
	}

}
