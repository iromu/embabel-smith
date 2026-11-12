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

package com.embabel.smith.retrieval.gliner;

import java.util.List;

import com.embabel.smith.retrieval.SpecEntry;
import io.gravitee.lab.gliner4j.GLiNER4jClassifier;
import io.gravitee.lab.gliner4j.schema.ClassificationLabel;
import io.gravitee.lab.gliner4j.schema.ClassificationResult;

/**
 * The GLiNER2 transformer selector — a zero-shot fine-grained classifier over a small
 * candidate set (design D1). It builds one {@link ClassificationLabel} per candidate,
 * asks the classifier to score the query against that label set, and returns the
 * top-scored label's spec id.
 *
 * <p>
 * The classifier is injected through the {@link Classifier} seam so the label-building
 * and top-1 logic are testable offline with a stub (no ONNX bundle); {@link #fromModel}
 * wires the real {@link GLiNER4jClassifier} for the gated benchmark run.
 *
 * @author Ivan Rodriguez
 */
public final class GlinerSelector implements SpecSelector, AutoCloseable {

	/** Sigmoid scores lie in (0,1), so a zero threshold always returns the top label. */
	private static final float TOP1_THRESHOLD = 0.0f;

	private final Classifier classifier;

	private final AutoCloseable resource;

	public GlinerSelector(Classifier classifier) {
		this(classifier, null);
	}

	private GlinerSelector(Classifier classifier, AutoCloseable resource) {
		this.classifier = classifier;
		this.resource = resource;
	}

	/**
	 * A selector backed by a real loaded GLiNER2 classifier. The bundle is loaded once;
	 * the per-call label set (the candidate set) is passed on each {@link #select} call.
	 * @param model the verified bundle
	 * @param loadTimeLabels the label set the classifier is loaded with (any non-empty
	 * set; the per-call labels override it)
	 * @return a selector backed by the real loaded classifier
	 */
	public static GlinerSelector fromModel(GlinerModel model, List<ClassificationLabel> loadTimeLabels) {
		GLiNER4jClassifier clf = GLiNER4jClassifier.load(model.modelDir(), loadTimeLabels, model.variant());
		return new GlinerSelector((text, labels, threshold) -> clf.classify(text, labels, threshold), clf);
	}

	@Override
	public String select(String query, List<SpecEntry> candidates) {
		if (candidates.isEmpty()) {
			return null;
		}
		List<ClassificationResult> results = this.classifier.classify(query, labelsFor(candidates), TOP1_THRESHOLD);
		if (results.isEmpty()) {
			return null;
		}
		// Results are sorted by confidence descending; the label name is the spec id.
		return results.get(0).label();
	}

	/**
	 * One label per candidate: name = spec id (so the result maps straight back),
	 * description = the spec text.
	 * @param candidates the candidate specs to build one label per
	 * @return one classification label per candidate
	 */
	public static List<ClassificationLabel> labelsFor(List<SpecEntry> candidates) {
		return candidates.stream()
			.map((e) -> new ClassificationLabel(e.id(), e.description()))
			.toList();
	}

	@Override
	public void close() {
		if (this.resource != null) {
			try {
				this.resource.close();
			}
			catch (RuntimeException ex) {
				throw ex;
			}
			catch (Exception ex) {
				throw new RuntimeException("Failed to close the GLiNER2 classifier", ex);
			}
		}
	}

	/**
	 * The classification seam: score {@code text} against a label set at
	 * {@code threshold} and return the scored results (the real classifier returns them
	 * sorted by confidence descending).
	 */
	@FunctionalInterface
	public interface Classifier {

		List<ClassificationResult> classify(String text, List<ClassificationLabel> labels, float threshold);

	}

}
