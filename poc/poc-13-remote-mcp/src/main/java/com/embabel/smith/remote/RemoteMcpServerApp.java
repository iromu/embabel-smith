package com.embabel.smith.remote;

import com.embabel.smith.common.config.ModelEndpointConfig;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * POC 13 launcher — the remote/MCP invocation demo (the other half of C11, doc 03 §2.3).
 *
 * <p>Boots the Embabel MCP server ({@code embabel-agent-starter-mcpserver}), which auto-publishes
 * the {@link RemoteExplainerAgent#explainTopic} goal as an MCP tool over Streamable HTTP
 * (default {@code http://localhost:8080/mcp}). After the server is up, this app connects to that
 * endpoint as an MCP client (the MCP SDK) and invokes the tool over the network — the "consume" half
 * of remote invocation. It is a same-process round-trip by design (the fallback the plan endorses);
 * a separate client process is a natural extension.
 *
 * <p>Run (with a live OpenAI-compatible endpoint):
 * <pre>
 *   export OPENAI_CUSTOM_BASE_URL=... OPENAI_CUSTOM_API_KEY=... OPENAI_CUSTOM_MODELS=... EMBABEL_MODELS_DEFAULT_LLM=...
 *   BUILDER_TOPIC="how a coffee machine works" mvn -pl poc/poc-13-remote-mcp spring-boot:run
 * </pre>
 */
@SpringBootApplication
public class RemoteMcpServerApp implements CommandLineRunner {

    private final String mcpUrl;
    private final String topic;
    private final ModelEndpointConfig endpoint;
    private final ConfigurableApplicationContext context;

    public RemoteMcpServerApp(
            ConfigurableApplicationContext context,
            @Value("${builder.mcp-url:http://localhost:8080}") String mcpUrl,
            @Value("${builder.topic:how a coffee machine works}") String topic,
            @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
            @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
            @Value("${OPENAI_CUSTOM_MODELS:}") String models,
            @Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm) {
        this.context = context;
        this.mcpUrl = mcpUrl;
        this.topic = topic;
        this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
    }

    public static void main(String[] args) {
        SpringApplication.run(RemoteMcpServerApp.class, args);
    }

    @Override
    public void run(String... args) {
        String configError = endpoint.configError();
        if (configError != null) {
            System.out.println("(Endpoint not configured — skipping the remote MCP run: " + configError + ")");
            context.close();
            return;
        }

        System.out.println("=== POC 13: remote / MCP invocation ===");
        System.out.println("MCP server at " + mcpUrl + "/mcp (Streamable HTTP)");

        ConnectResult connected = connectWithRetry(mcpUrl);
        McpSyncClient client = connected.client();
        try {
            McpSchema.InitializeResult init = connected.init();
            System.out.println("Connected. protocol=" + init.protocolVersion()
                    + ", server=" + init.serverInfo().name() + " v" + init.serverInfo().version());

            McpSchema.ListToolsResult tools = client.listTools();
            System.out.println("Published MCP tools (" + tools.tools().size() + "):");
            for (McpSchema.Tool tool : tools.tools()) {
                System.out.println("  - " + tool.name() + " — " + tool.description());
                System.out.println("    inputSchema: " + tool.inputSchema());
            }

            String toolName = tools.tools().stream()
                    .map(McpSchema.Tool::name)
                    .filter(n -> n.toLowerCase().contains("explain"))
                    .findFirst()
                    .orElse(tools.tools().get(0).name());

            System.out.println("\nInvoking remote MCP tool '" + toolName + "' (topic: " + topic + ")");
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    toolName,
                    Map.of("content", topic, "timestamp", java.time.Instant.now().toString())));
            String text = result.content().stream()
                    .filter(c -> c instanceof McpSchema.TextContent)
                    .map(c -> ((McpSchema.TextContent) c).text())
                    .collect(Collectors.joining("\n"));
            System.out.println("Remote result:\n" + text);
            System.out.println("\n=== POC 13: remote/MCP invocation PROVEN ===");
        } finally {
            client.close();
            context.close();
        }
    }

    /** An initialized MCP client plus its handshake result. */
    private record ConnectResult(McpSyncClient client, McpSchema.InitializeResult init) {}

    /** Build and initialize the client, retrying briefly in case the server needs a moment to accept connections. */
    private ConnectResult connectWithRetry(String mcpUrl) {
        Exception last = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            McpSyncClient client = McpClient.sync(
                    HttpClientStreamableHttpTransport.builder(mcpUrl).endpoint("/mcp").build())
                    .requestTimeout(Duration.ofMinutes(2))
                    .initializationTimeout(Duration.ofSeconds(30))
                    .build();
            try {
                McpSchema.InitializeResult init = client.initialize();
                return new ConnectResult(client, init);
            } catch (Exception e) {
                last = e;
                client.close();
                System.out.println("MCP connect attempt " + attempt + " failed: " + e.getMessage());
                sleepQuietly(2000);
            }
        }
        throw new IllegalStateException("Could not connect to the MCP server at " + mcpUrl + "/mcp", last);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
