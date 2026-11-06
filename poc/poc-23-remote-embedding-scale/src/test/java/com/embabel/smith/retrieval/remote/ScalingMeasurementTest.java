package com.embabel.smith.retrieval.remote;

import com.embabel.smith.retrieval.SkillEntry;
import com.embabel.smith.retrieval.scale.ScaleSkillLibrary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScalingMeasurementTest {

    @Test
    void sweepReportsOnePointPerSizeInAscendingOrder() {
        List<SkillEntry> library = LargeScaleLibrary.generate(120);
        List<ScaleSkillLibrary.QueryTarget> queries = LargeScaleLibrary.benchmark();
        List<Integer> sizes = List.of(20, 40, 80, 120);

        ScalingMeasurement measurement = new ScalingMeasurement();
        List<ScalingMeasurement.Point> points =
                measurement.sweep(StubEmbedding.embedder(StubEmbedding::hashVec), library, queries, sizes);

        assertThat(points).hasSize(4);
        for (int i = 0; i < points.size(); i++) {
            ScalingMeasurement.Point p = points.get(i);
            assertThat(p.librarySize()).isEqualTo(sizes.get(i));
            assertThat(p.precisionAt1()).isBetween(0.0, 1.0);
            assertThat(p.perQueryMillis()).isGreaterThanOrEqualTo(0.0);
            assertThat(p.indexBuildMillis()).isGreaterThanOrEqualTo(0L);
        }
        // ascending sizes
        assertThat(points.get(0).librarySize()).isLessThan(points.get(3).librarySize());
    }

    @Test
    void indexEmbeddingsAreAmortisedAcrossSizes() {
        // The shared memoising scorer means the total distinct embeddings across the whole sweep is
        // bounded by (largest size + queries), not the sum of every size.
        List<SkillEntry> library = LargeScaleLibrary.generate(60);
        List<ScaleSkillLibrary.QueryTarget> queries = LargeScaleLibrary.benchmark();
        RemoteEmbedder embedder = StubEmbedding.embedder(StubEmbedding::hashVec);

        new ScalingMeasurement().sweep(embedder, library, queries, List.of(20, 40, 60));

        // distinct texts embedded = 60 library skills + 6 benchmark queries, each once
        assertThat(embedder.requestCount()).isEqualTo(60 + queries.size());
    }
}
