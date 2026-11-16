/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.nlcompile;

import java.util.List;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.spec.model.StepSpecContext;

/**
 * Builds the {@link StepSpecContext} the natural-language compiler needs: the platform's
 * data dictionary (the types it may reference) plus the available tool groups, tools and
 * LLM references. The POC agent specs are pure-LLM, so the tool/tool/reference lists are
 * empty.
 *
 * @author Ivan Rodriguez
 */
public class StepSpecContextFactory {

	public StepSpecContext forAgent(String agentName, DataDictionary dataDictionary) {
		return new StepSpecContext(agentName, dataDictionary, List.of(), List.of(), List.of());
	}

}
