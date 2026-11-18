/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.blueprint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.embabel.smith.builder.core.PromptData;
import com.embabel.smith.builder.core.SpecPromptCaller;

/**
 * The model-based process extraction: a corpus in, a business-process description
 * (markdown) out. It is a second prompt run against the same {@link SpecPromptCaller}
 * seam as generation, but with the {@code business_process_discovery} prompt and a
 * plain-text answer (no JSON pack to parse).
 *
 * <p>
 * The prompt it bundles asks for a <em>codebase</em> — it reconstructs the business
 * process an application implements while excluding the classes, files and schemas that
 * reveal it. So this is the step that turns a repository into a blueprint, not a step to
 * run over a blueprint: {@code --docs} reads documents only, and extraction over prose
 * that is already business-level re-summarises it into less detail for a full-context
 * call. The ingestion that feeds generation therefore reads documents directly and never
 * comes through here; {@link BlueprintGenerator} is the caller that runs this step over a
 * real codebase.
 *
 * @author Ivan Rodriguez
 */
public class ProcessExtractor {

	/** Classpath location of the bundled discovery prompt. */
	private static final String PROMPT_RESOURCE = "/prompts/business_process_discovery.jinja";

	private final SpecPromptCaller caller;

	private final String template;

	public ProcessExtractor(SpecPromptCaller caller) {
		this(caller, bundledPrompt(PROMPT_RESOURCE));
	}

	public ProcessExtractor(SpecPromptCaller caller, String template) {
		this.caller = caller;
		this.template = template;
	}

	/**
	 * Reads a bundled prompt from the builder's resources.
	 * @param resource the classpath location of the prompt.
	 * @return the prompt template text.
	 */
	private static String bundledPrompt(String resource) {
		try (InputStream in = ProcessExtractor.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("prompt resource not found: " + resource);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to load prompt " + resource + ": " + ex.getMessage(), ex);
		}
	}

	/**
	 * Runs the discovery prompt over the docs corpus, returning the business-process
	 * description.
	 * @param corpus the business-process documents to extract from.
	 * @return the business-process description produced by the model.
	 */
	public String extract(String corpus) {
		return extract(corpus, null);
	}

	/**
	 * As {@link #extract(String)}, running the discovery prompt on one named LLM.
	 * @param corpus the business-process documents to extract from.
	 * @param model the LLM to extract with, or empty for the configured default.
	 * @return the business-process description produced by the model.
	 */
	public String extract(String corpus, String model) {
		String user = "Below are the business-process documents for an application. Reconstruct the "
				+ "business processes and workflows they describe.\n\n" + PromptData.block("documents", corpus);
		return this.caller.call(this.template, user, model);
	}

	/**
	 * Runs the discovery prompt over a codebase corpus, returning the business-process
	 * description. This is the prompt's own job — the template asks the model to analyse
	 * a codebase and to translate implementation detail into business concepts — whereas
	 * {@link #extract(String)} feeds it documents that are already business-level.
	 * @param corpus the repository corpus, every file labelled with its path
	 * @return the business-process description produced by the model
	 */
	public String extractCodebase(String corpus) {
		String user = "Below is the source code and documentation of an application. Reconstruct the "
				+ "business processes the implementation carries, translating technical detail into "
				+ "business concepts. Everything you need is below: write the deliverable documents "
				+ "themselves in markdown, beginning with the first heading, rather than saying what "
				+ "you intend to do.\n\n<codebase>\n" + corpus + "\n</codebase>";
		return this.caller.call(this.template, user, null);
	}

}
