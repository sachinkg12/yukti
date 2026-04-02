package io.yukti.api.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.api.dto.OptimizeRequestDto;
import io.yukti.api.dto.OptimizeResponseDto;
import io.yukti.api.handler.OptimizeHandler;
import io.yukti.api.handler.V1ApiHandler;
import io.yukti.api.mapper.OptimizationMapper;
import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Optimizer;
import io.yukti.core.domain.OptimizationRequest;
import io.yukti.core.domain.OptimizationResult;
import io.yukti.engine.optimizer.OptimizerRegistry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class LocalServer {
    private static final ObjectMapper OM = new ObjectMapper();
    private static V1ApiHandler v1Handler;

    /** Creates and configures the server. Caller must start/stop it. Used by tests. */
    public static HttpServer createServer(int port) throws Exception {
        Catalog catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
        Optimizer optimizer = new OptimizerRegistry().select();
        v1Handler = new V1ApiHandler();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", t -> handleRoot(t));
        server.createContext("/optimize", t -> handleOptimize(t, catalog, optimizer));
        server.createContext("/v1", t -> handleV1(t));
        server.createContext("/health", t -> {
            t.getResponseHeaders().set("Content-Type", "text/plain");
            t.sendResponseHeaders(200, 0);
            t.getResponseBody().close();
        });
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()));
        return server;
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 18000;
        HttpServer server = createServer(port);
        server.start();
        System.out.println("Yukti local server on http://localhost:" + port);
    }

    private static void handleRoot(HttpExchange t) throws IOException {
        String path = t.getRequestURI().getPath();
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            t.getResponseHeaders().set("Location", "/");
            t.sendResponseHeaders(302, 0);
            t.getResponseBody().close();
            return;
        }
        byte[] html;
        try (var in = LocalServer.class.getClassLoader().getResourceAsStream("static/index.html")) {
            html = in != null ? in.readAllBytes() : "<h1>Not found</h1>".getBytes(StandardCharsets.UTF_8);
        }
        t.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        t.sendResponseHeaders(200, html.length);
        try (OutputStream os = t.getResponseBody()) {
            os.write(html);
        }
    }

    private static void handleOptimize(HttpExchange t, Catalog catalog, Optimizer optimizer) throws IOException {
        if ("OPTIONS".equals(t.getRequestMethod())) {
            t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            t.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            t.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            t.sendResponseHeaders(204, -1);
            return;
        }
        if (!"POST".equals(t.getRequestMethod())) {
            sendJson(t, 405, Map.of("error", "Method not allowed"));
            return;
        }
        String body = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var dto = OM.readValue(body, OptimizeRequestDto.class);
        OptimizationRequest request = OptimizationMapper.toRequest(dto);
        OptimizationResult result = optimizer.optimize(request, catalog);
        OptimizeResponseDto responseDto = OptimizationMapper.toResponse(result);

        t.getResponseHeaders().set("Content-Type", "application/json");
        t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        String json = OM.writeValueAsString(responseDto);
        t.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = t.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void handleV1(HttpExchange t) throws IOException {
        String path = t.getRequestURI().getPath();
        String requestId = t.getRequestHeaders().getFirst("X-Request-Id");
        if (requestId == null || requestId.isBlank()) requestId = java.util.UUID.randomUUID().toString();

        t.getResponseHeaders().set("X-Request-Id", requestId);
        t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        if ("OPTIONS".equals(t.getRequestMethod())) {
            t.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            t.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Request-Id");
            t.sendResponseHeaders(204, -1);
            return;
        }

        if ("/v1/optimize".equals(path) && "POST".equals(t.getRequestMethod())) {
            long start = System.currentTimeMillis();
            System.out.println("[v1/optimize] Request received, requestId=" + requestId);
            String body = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var result = v1Handler.handleOptimize(body, requestId);
            long ms = System.currentTimeMillis() - start;
            System.out.println("[v1/optimize] Completed in " + ms + "ms, status=" + result.status());
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(result.status(), result.body().length);
            t.getResponseBody().write(result.body());
            return;
        }
        if ("/v1/catalog/cards".equals(path) && "GET".equals(t.getRequestMethod())) {
            String cv = null;
            var query = t.getRequestURI().getQuery();
            if (query != null) {
                for (String p : query.split("&")) {
                    if (p.startsWith("catalogVersion=")) {
                        cv = URLDecoder.decode(p.substring(15), StandardCharsets.UTF_8);
                        break;
                    }
                }
            }
            byte[] out = v1Handler.handleCatalogCards(cv);
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(200, out.length);
            t.getResponseBody().write(out);
            return;
        }
        if ("/v1/config/goals".equals(path) && "GET".equals(t.getRequestMethod())) {
            byte[] out = v1Handler.handleConfigGoals();
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(200, out.length);
            t.getResponseBody().write(out);
            return;
        }
        if ("/v1/config/optimizers".equals(path) && "GET".equals(t.getRequestMethod())) {
            var ids = v1Handler.availableOptimizerIds();
            byte[] out = OM.writeValueAsBytes(Map.of("optimizers", ids));
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(200, out.length);
            t.getResponseBody().write(out);
            return;
        }

        t.sendResponseHeaders(404, -1);
    }

    private static void sendJson(HttpExchange t, int status, Object obj) throws IOException {
        String json = OM.writeValueAsString(obj);
        t.getResponseHeaders().set("Content-Type", "application/json");
        t.sendResponseHeaders(status, json.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = t.getResponseBody()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
