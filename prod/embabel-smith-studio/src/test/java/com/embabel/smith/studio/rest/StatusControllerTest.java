package com.embabel.smith.studio.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.studio.rest.dto.StatusResponse;
import org.junit.jupiter.api.Test;

class StatusControllerTest {

    @Test
    void reportsConfiguredEndpointWithDefaultModel() {
        ModelEndpointConfig endpoint = new ModelEndpointConfig("http://x/v1", "key", "m1,m2", "m1");
        StatusResponse status = new StatusController(endpoint).status();
        assertThat(status.configured()).isTrue();
        assertThat(status.model()).isEqualTo("m1");
        assertThat(status.error()).isNull();
    }

    @Test
    void reportsMissingEndpointWithConfigError() {
        ModelEndpointConfig endpoint = new ModelEndpointConfig(null, "key", "m1", "m1");
        StatusResponse status = new StatusController(endpoint).status();
        assertThat(status.configured()).isFalse();
        assertThat(status.model()).isNull();
        assertThat(status.error()).contains("OPENAI_CUSTOM_BASE_URL");
    }
}
