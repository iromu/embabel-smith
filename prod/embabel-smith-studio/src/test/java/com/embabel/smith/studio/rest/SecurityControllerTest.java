package com.embabel.smith.studio.rest;

import com.embabel.smith.studio.rest.dto.SecurityRequest;
import com.embabel.smith.studio.rest.dto.SecurityResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Offline tests for the Security pane: the static scan of a pack from a source. */
class SecurityControllerTest {

    @Test
    void scansPackAndReturnsVerdict(@TempDir Path tmp) {
        RestTestFixtures.writePack(tmp, RestTestFixtures.pack("summarizer", "summarize"));
        SecurityController controller =
                new SecurityController(new SourceService("folder:" + tmp, ""));
        SecurityResponse response =
                controller.scan(new SecurityRequest("folder:" + tmp, "summarizer"));
        assertThat(response.error()).isNull();
        assertThat(response.scan().skillName()).isEqualTo("summarizer");
        assertThat(response.scan().verdict()).isIn("SAFE", "REVIEW", "BLOCK");
        assertThat(response.scan().summary()).isNotBlank();
    }

    @Test
    void returnsErrorWhenNoSourceOrSkill() {
        SecurityController controller = new SecurityController(new SourceService("", ""));
        SecurityResponse response = controller.scan(new SecurityRequest("", ""));
        assertThat(response.error()).contains("No source or skill");
    }

    @Test
    void returnsErrorForUnknownSkill(@TempDir Path tmp) {
        SecurityController controller =
                new SecurityController(new SourceService("folder:" + tmp, ""));
        SecurityResponse response =
                controller.scan(new SecurityRequest("folder:" + tmp, "nope"));
        assertThat(response.error()).contains("not found");
    }
}
