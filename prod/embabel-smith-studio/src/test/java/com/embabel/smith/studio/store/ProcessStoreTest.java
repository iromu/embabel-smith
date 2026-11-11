package com.embabel.smith.studio.store;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the durable store: a save → list → load round-trip, multiple packs, and the
 * append-only discovery history.
 */
class ProcessStoreTest {

    @TempDir
    Path root;

    private SkillPack pack(String name, String goal) {
        ActionStepSpec step = ActionStepSpec.action(name + "_step", "a step",
                List.of("UserInput"), name + "_Out", "Produce {{userInput}} into the result.");
        GoalStepSpec goalSpec = GoalStepSpec.exportedGoal(goal, "the goal", name + "_Out");
        DynamicTypeDef type = new DynamicTypeDef(name + "_Out", "an output", null, null, null);
        return new SkillPack(new SkillPackManifest(name, "1.0.0"), goalSpec, List.of(step), List.of(type));
    }

    @Test
    void saveListLoadRoundTripsAPack() {
        ProcessStore store = new ProcessStore(root);
        SkillPack original = pack("alpha", "alpha_goal");

        ProcessStore.PackRecord record = store.savePack(original);
        assertThat(record.name()).isEqualTo("alpha");
        assertThat(record.goal()).isEqualTo("alpha_goal");
        assertThat(record.steps()).isEqualTo(1);

        SkillPack loaded = store.loadPack("alpha");
        assertThat(loaded).isNotNull();
        assertThat(loaded.name()).isEqualTo("alpha");
        assertThat(loaded.version()).isEqualTo("1.0.0");
        assertThat(loaded.goal().name()).isEqualTo("alpha_goal");
        assertThat(loaded.goal().outputTypeName()).isEqualTo("alpha_Out");
        assertThat(loaded.steps()).hasSize(1);
        assertThat(loaded.step("alpha_step").prompt()).isEqualTo(original.step("alpha_step").prompt());
    }

    @Test
    void listReflectsMultiplePacks() {
        ProcessStore store = new ProcessStore(root);
        store.savePack(pack("alpha", "alpha_goal"));
        store.savePack(pack("beta", "beta_goal"));

        List<ProcessStore.PackRecord> records = store.listPacks();
        assertThat(records).hasSize(2);
        assertThat(records).extracting(ProcessStore.PackRecord::name).contains("alpha", "beta");
    }

    @Test
    void loadPackReturnsNullForAnUnknownName() {
        ProcessStore store = new ProcessStore(root);
        assertThat(store.loadPack("nope")).isNull();
    }

    @Test
    void discoveryHistoryAppendsAndReturnsMostRecentFirst() {
        ProcessStore store = new ProcessStore(root);
        store.recordDiscovery("folder", "/docs/a", 3);
        store.recordDiscovery("git", "https://x/repo", 7);

        List<ProcessStore.DiscoveryRecord> history = store.discoveries();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).location()).isEqualTo("https://x/repo");
        assertThat(history.get(0).docCount()).isEqualTo(7);
        assertThat(history.get(1).location()).isEqualTo("/docs/a");
        assertThat(history.get(1).docCount()).isEqualTo(3);
    }

    @Test
    void emptyStoreListsNoPacksAndNoDiscoveries() {
        ProcessStore store = new ProcessStore(root);
        assertThat(store.listPacks()).isEmpty();
        assertThat(store.discoveries()).isEmpty();
    }
}
