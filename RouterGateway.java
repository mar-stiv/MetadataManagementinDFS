import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class RouterGateway {
    private final List<String> backends; // list of backend servers which the router can forward requests to
    private final int port; // port which the router listens on

    // 1. Constructor
    public RouterGateway(List<String> backends, int port) {
        this.backends = backends;
        this.port = port;
    }

    // 2. Main function (always called first in java)
    public static void main(String[] args) throws Exception {
        // 2.1 Reading the list of servers from env vars
        String csv = System.getenv("SERVERS");
        if (csv == null || csv.trim().isEmpty()) {
            System.err.println("SERVERS env var not set");
            return;
        }

        // 2.2 Parsing comma-separated list of server URLs
        List<String> backends = Arrays.asList(csv.split(","));
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8000"));

        RouterGateway g = new RouterGateway(backends, port);
        g.start();
    }

    // 3.
    public void start() throws Exception {
        // 3.1 Creating a http server which listens on a specific port:
        HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);

        // 3.2 Registering API endpoints with their handlers
        // use parent-based routing for consistency
        http.createContext("/mkdir",   ex -> forward(ex, true));
        http.createContext("/touch",   ex -> forward(ex, true));
        http.createContext("/rm",      ex -> forward(ex, true));
        http.createContext("/readdir", ex -> forward(ex, false));
        http.createContext("/stat",    ex -> forward(ex, false));
        http.createContext("/tree",    ex -> forward(ex, false));
        http.createContext("/fulltree",ex -> forward(ex, false));
        http.createContext("/chkdist", this::chkdist); // shows cluster distribution
        http.createContext("/cluster", this::clusterStatus); // show cluster health
        http.createContext("/health",  x -> ok(x, "ok")); // checks health

        // 3.3 Using a thread pool to handle concurrent requests
        http.setExecutor(Executors.newCachedThreadPool());
        System.out.println("[Router] listening on port " + port + " -> " + backends);
        http.start();
    }

    // 4. Helper method: normalising a path by ensuring that it starts with / + does not end with /
    private static String normalize(String p) {
        if (p == null || p.trim().isEmpty()) return "/";
        p = p.replaceAll("/+", "/");
        if (!p.startsWith("/")) p = "/" + p;
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        System.out.println("In: Normalise, where p is returned as: " + p);
        return p;
    }

    // 5. Helper method: extracting the routing key from a path, using the first directory level
    private String getRoutingKey(String path) {
        String normalized = normalize(path);

        // 5.1 Root always goes to same routing
        if (normalized.equals("/")) {
            return "/";
        }

        // 5.2 Extract first directory level for routing
        // example:
        // /home/maria/file.txt -> /home
        // /home/maria -> /home
        // /home -> /home
        String[] parts = normalized.substring(1).split("/");
        return "/" + parts[0];
    }

    // 6. Helper method: picking which backend server should handle a write operation
    private String pickBackendForWrite(String path) {
        System.out.println("In: Router, where path is sent as: " + path);
        String normalizedPath = normalize(path);
        String routingKey = getRoutingKey(normalizedPath);

        // 6.1 using hash-based distribution
        int i = Math.abs(routingKey.hashCode()) % backends.size();
        String selected = backends.get(i);
        System.out.println("In: Router, where path is sent as: " + normalizedPath);
        System.out.println("[Router] WRITE path='" + normalizedPath + "' routingKey='" + routingKey + "' -> " + selected);
        return selected;
    }

    // 7. Helper method: picking which backend server should handle a read operation
    private String pickBackendForRead(String path) {
        // For reads, use the same routing key logic to ensure we find the data
        String normalizedPath = normalize(path);
        String routingKey = getRoutingKey(normalizedPath);
        int i = Math.abs(routingKey.hashCode()) % backends.size();
        String selected = backends.get(i);
        System.out.println("[Router] READ path='" + normalizedPath + "' routingKey='" + routingKey + "' -> " + selected);
        return selected;
    }

    // 8. Forwarding: forwards a http request to the appropriate backend server
    private void forward(HttpExchange ex, boolean isWrite) throws IOException {
        // 8.1 Extracting the path parameter from the query string
        String path = getQueryParam(ex, "path");
        if (path == null || path.trim().isEmpty()) {
            sendResponse(ex, 400, "Missing or invalid 'path' parameter");
            return;
        }

        // 8.2 Choosing which backend server to forward this request to
        String normalizedPath = normalize(path);
        String backend = isWrite ? pickBackendForWrite(normalizedPath) : pickBackendForRead(normalizedPath);
        System.out.println("[Router] " + (isWrite ? "WRITE" : "READ") + " path='" + normalizedPath + "' -> " + backend);

        // 8.3 Constructing the target URL: backend + original path + query parameters
        String targetUrl = backend + ex.getRequestURI().getPath() + "?path=" +
                URLEncoder.encode(normalizedPath, "UTF-8");

        try {
            // 8.4 Making the http call to the backend server
            String response = httpCall(targetUrl, ex.getRequestMethod());
            sendResponse(ex, 200, response);
        } catch (IOException e) {
            // 8.5 Handling backend server failures
            System.err.println("[Router] Backend error for " + backend + ": " + e.getMessage());
            sendResponse(ex, 503, "Backend unavailable: " + backend);
        }
    }

    // 9. Cluster management endpoint: Showing how the metadata is distributed across servers
    private void chkdist(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("=== Cluster Metadata Distribution ===\n\n");
        // 9.1 Querying each server's dump endpoint to see what they store
        for (int i = 0; i < backends.size(); i++) {
            String backend = backends.get(i);
            sb.append("--- Server ").append(i + 1).append(" (").append(backend).append(") ---\n");
            try {
                String dump = httpCall(backend + "/dump", "GET");
                sb.append(dump).append("\n");
            } catch (Exception e) {
                sb.append("(unreachable or error: ").append(e.getMessage()).append(")\n\n");
            }
        }
        sendResponse(ex, 200, sb.toString());
    }

    // 10. Cluster management endpoint: Showing the cluster status + health info
    private void clusterStatus(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("=== Cluster Status ===\n\n");
        sb.append("Router: http://localhost:").append(port).append("\n");
        sb.append("Backend servers:\n");

        // 10.1 Checking each server's health
        for (int i = 0; i < backends.size(); i++) {
            String backend = backends.get(i);
            sb.append("  Server ").append(i + 1).append(": ").append(backend);

            try {
                String health = httpCall(backend + "/dump", "GET");
                sb.append(" Alive\n");
            } catch (Exception e) {
                sb.append("Unreachable: ").append(e.getMessage()).append(")\n");
            }
        }

        // 10.2 Explaining the routing strategy
        sb.append("\nRouting Strategy:\n");
        sb.append("  - All operations: hash(first directory level)\n");
        sb.append("  - Ensures entire directory tree on same server\n");
        sb.append("  - Example: /home, /home/maria, /home/maria/file.txt all same server\n");

        sendResponse(ex, 200, sb.toString());
    }

    // 11. Utility method: making a http call to a backend server
    private static String httpCall(String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(3000); // 3 sec connection timeout
        conn.setReadTimeout(5000); // 5 sec read timeout

        try {
            int responseCode = conn.getResponseCode();

            // 11.1 Getting the response stream: success or error
            InputStream inputStream = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (inputStream == null) {
                return "No response body (status: " + responseCode + ")";
            }

            return readStreamFully(inputStream);
        } finally {
            conn.disconnect();
        }
    }

    // Utility method: reading all data from an inputstream into a string
    private static String readStreamFully(InputStream in) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = in.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(String.valueOf(StandardCharsets.UTF_8));
    }

    // Utility method: extracting a query param from the http request
    private static String getQueryParam(HttpExchange ex, String key) {
        String query = ex.getRequestURI().getQuery();
        if (query == null) return null;

        // splitting query string into key=value pairs
        String[] params = query.split("&");
        for (String param : params) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && key.equals(pair[0])) {
                try {
                    return URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
                } catch (Exception e) {
                    return pair[1];
                }
            }
        }
        return null;
    }

    // Utility method: sending an http response
    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private static void ok(HttpExchange ex, String body) throws IOException {
        sendResponse(ex, 200, body);
    }
}