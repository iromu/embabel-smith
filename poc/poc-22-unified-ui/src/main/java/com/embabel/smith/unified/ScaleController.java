/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.unified;

import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.ScaleRetrievalRunner;
import com.embabel.smith.retrieval.scale.ScaleSpecLibrary;
import com.embabel.smith.retrieval.scale.ScaleSpecRegistry;
import com.embabel.smith.retrieval.scale.SpecScorer;
import com.embabel.smith.unified.dto.ScalePoint;
import com.embabel.smith.unified.dto.ScaleResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Scale pane (POC 22): quantifies selection accuracy (precision@1) of the active
 * {@link SpecScorer} as a same-typed library grows and as near-duplicate (shadowing)
 * specs accumulate, via POC 12's {@link ScaleRetrievalRunner} over
 * {@link ScaleSpecLibrary}. Pure and offline (the lexical scorer is the default; a denser
 * scorer drops in via the shared seam).
 *
 * @author Ivan Rodriguez
 */
@RestController
public class ScaleController {

	private static final int CURVE_POINTS = 12;

	private final SpecScorer scorer;

	private final String scorerName;

	public ScaleController(ScorerBinding binding) {
		this.scorer = binding.scorer();
		this.scorerName = binding.name();
	}

	/** Precision@1 for the core and full libraries, plus a sampled growth curve.
	 * @return the scale response (precision@1 for both libraries + the growth curve).
	 */
	@GetMapping("/api/scale")
	public ScaleResponse scale() {
		List<SpecEntry> core = ScaleSpecLibrary.core();
		List<SpecEntry> full = ScaleSpecLibrary.full();
		List<ScaleSpecLibrary.QueryTarget> benchmark = ScaleSpecLibrary.benchmark();
		ScaleRetrievalRunner runner = new ScaleRetrievalRunner();
		double coreP1 = runner.precisionAt1(new ScaleSpecRegistry(core, this.scorer), benchmark);
		double fullP1 = runner.precisionAt1(new ScaleSpecRegistry(full, this.scorer), benchmark);
		List<ScaleRetrievalRunner.CurvePoint> curve = runner.growthCurve(core, ScaleSpecLibrary.growthSequence(),
				this.scorer, benchmark);
		return new ScaleResponse(this.scorerName, core.size(), round4(coreP1), full.size(), round4(fullP1),
				sample(curve, CURVE_POINTS));
	}

	/**
	 * Evenly sample up to {@code n} points from the growth curve to keep the response
	 * compact.
	 * @param curve the sampled growth curve.
	 * @param n the maximum number of points to return.
	 * @return the sampled points as scale points.
	 */
	private static List<ScalePoint> sample(List<ScaleRetrievalRunner.CurvePoint> curve, int n) {
		if (curve.size() <= n) {
			return curve.stream().map((p) -> point(p)).toList();
		}
		List<ScalePoint> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			int idx = (int) Math.round((double) i * (curve.size() - 1) / (n - 1));
			out.add(point(curve.get(idx)));
		}
		return out;
	}

	private static ScalePoint point(ScaleRetrievalRunner.CurvePoint p) {
		return new ScalePoint(p.librarySize(), round4(p.precisionAt1()));
	}

	private static double round4(double v) {
		return Math.round(v * 10000.0) / 10000.0;
	}

}
