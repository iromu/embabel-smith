package com.embabel.smith.unified;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.net.ServerSocket;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the Remote pane (POC 22). The invocation drives POC 16's {@code RestAction} sidecar over
 * HTTP; when the sidecar is unreachable the pane returns a "backing process not running" body rather
 * than crashing. The down path is exercised by pointing the sidecar URL at a port nothing is
 * listening on (a freshly-released free port, so no live process can be hit).
 */
class RemoteControllerTest {

    @Test
    void aDownSidecarReportsNotRunning() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new RemoteController(deadSidecarUrl(), "http://localhost:8080")).build();
        mvc.perform(post("/api/remote").contentType(MediaType.APPLICATION_JSON).content("{\"input\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.error").value(containsString("backing process not running")))
                .andExpect(jsonPath("$.mcpUrl").value("http://localhost:8080"));
    }

    /** A localhost URL bound to a port nothing is currently listening on (connection-refused). */
    private static String deadSidecarUrl() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://localhost:" + socket.getLocalPort();
        }
    }
}
