// importing libraries
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.lang.*;

public class MetadataServer {
    private final int port; // port number that the server listens on
    private final String serverId; // unique id for the server instance
    private final Map<String, MetadataEntry> metadata; // in-memory storage of file metadata
    private HttpServer server; // http server instance

    // 1. Initialise + declare the file where we persistenty store metadata so that it survives server restarts
    private static final String DATA_FILE = "/data/meta.txt";

    // 2. Constructor
    public MetadataServer(int port, String serverId) {
        this.port = port;
        this.serverId = serverId;
        this.metadata = new ConcurrentHashMap<>(); // thread-safe map for concurrent access
        load(); // load any existing metadata from disk

        // Ensure the root directory exists
//        if (!metadata.containsKey("/")) {
//            metadata.put("/", new MetadataEntry("/", "dir", null, System.currentTimeMillis()));
//            save();
//            System.out.println("[Server " + serverId + "] Created root directory");
//        }

        System.out.println("[Server " + serverId + "] Initialized");
    }

    // 3. Load metadata from disk file when server starts
    private void load() {
        try {
            Path file = Paths.get(DATA_FILE);
            if (Files.exists(file)) {
                // 3.1 Try to read the file line by line
                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;

                        String[] parts = line.split("\\|"); // each line format: path/type/parent/timestamp
                        if (parts.length == 4) {
                            String path = parts[0];
                            String type = parts[1];
                            String parent = parts[2].equals("null") ? null : parts[2];
                            long timestamp = Long.parseLong(parts[3]);
                            // 3.2 Restoring the emtadata entry to memory
                            metadata.put(path, new MetadataEntry(path, type, parent, timestamp));
                        }
                    }
                }
                System.out.println("[Server " + serverId + "] Loaded " + metadata.size() + " entries from checkpoint");
            }
        } catch (Exception e) {
            System.out.println("[Server " + serverId + "] No checkpoint found or error loading: " + e.getMessage());
        }
    }

    // 4. Saving the current metadata to the disk, this is called after each change
    private void save() {
        try {
            Path file = Paths.get(DATA_FILE);
            Files.createDirectories(file.getParent()); // creates the directory if it does not exist
            // 4.1 Writing all metadata entries to file
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
                for (MetadataEntry entry : metadata.values()) {
                    writer.printf("%s|%s|%s|%d%n",
                            entry.getPath(),
                            entry.getType(),
                            entry.getParent() != null ? entry.getParent() : "null", // here parent is saved
                            entry.getTimestamp());
                }
            }
        } catch (Exception e) {
            System.err.println("[Server " + serverId + "] Error saving checkpoint: " + e.getMessage());
        }
    }

    // 5. Starting the http server + register API endpoints
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // 5.1 Register endpoints that the server will handle
        server.createContext("/mkdir", this::handleMkdir);
        server.createContext("/touch", this::handleTouch);
        server.createContext("/readdir", this::handleReaddir); // list directory contents
        server.createContext("/stat", this::handleStat); // get file/directory info
        server.createContext("/rm", this::handleRm); // remove file/directory
        server.createContext("/dump", this::handleDump); // show all metadata (for debugging)
        server.createContext("/tree", this::handleTree); // show the tree of the directory with relative paths
        server.createContext("/fulltree", this::handleFullTree); // show the tree of the directory

        server.start();
        System.out.println("[Server " + serverId + "] port=" + port);
    }

    // 6. Handling the creation of a new directory
    private void handleMkdir(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }

        // 6.1 Extracting the path param from the URL query string
        String query = exchange.getRequestURI().getQuery();
        String path = getQueryParam(query, "path");

        if (path == null || path.isEmpty()) {
            sendResponse(exchange, 400, "Missing or invalid 'path' parameter");
            return;
        }

        try {
            // 6.2 Checking if the path already exists
            System.out.println("In: handleMkdir, where path is checked as: " + path);
            if (metadata.containsKey(path)) {
                sendResponse(exchange, 409, "Path already exists");
                return;
            }

            // 6.3 Checking if parent exists (skip check for root "/")
            String parent = getParentPath(path);
            if (parent != null && !"/".equals(parent) && !metadata.containsKey(parent)) {
                sendResponse(exchange, 404, "Parent directory does not exist");
                return;
            }

            // 6.4 Creating a new directory entry + save to disk
            MetadataEntry entry = new MetadataEntry(path, "dir", parent, System.currentTimeMillis());
            metadata.put(path, entry);
            save(); // persist the change
            System.out.println("[Server " + serverId + "] Created directory: " + path);
            sendResponse(exchange, 200, "Directory created: " + path);
        } catch (Exception e) {
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }

    // 7. Handling the creation of a new file
    private void handleTouch(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        String path = getQueryParam(query, "path");

        if (path == null || path.isEmpty()) {
            sendResponse(exchange, 400, "Missing or invalid 'path' parameter");
            return;
        }

        try {
            if (metadata.containsKey(path)) {
                sendResponse(exchange, 409, "File already exists");
                return;
            }

            // 7.1 Checking that parent exists (skip check for root "/")
            String parent = getParentPath(path);
            if (parent != null && !"/".equals(parent) && !metadata.containsKey(parent)) {
                sendResponse(exchange, 404, "Parent directory does not exist");
                return;
            }

            // 7.2 Creating a new file entry + save to disk
            MetadataEntry entry = new MetadataEntry(path, "file", parent, System.currentTimeMillis());
            metadata.put(path, entry);
            save();
            System.out.println("[Server " + serverId + "] Created file: " + path);
            sendResponse(exchange, 200, "File created: " + path);
        } catch (Exception e) {
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }

    // 8. Handling listing directory contents
    private void handleReaddir(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        if (!"GET".equals(requestMethod)) {
            System.out.println("Unsupported method: " + requestMethod);
            System.out.flush();
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        String path = getQueryParam(query, "path");

        if (path == null || path.isEmpty()) {
            sendResponse(exchange, 400, "Missing or invalid 'path' parameter");
            return;
        }

        try {
            // 8.1 Checking if the path exists
            MetadataEntry entry = metadata.get(path);
            if (entry == null) {
                sendResponse(exchange, 404, "Path not found");
                return;
            }

            // 8.2 Verifying that it is a directory
            if (!"dir".equals(entry.getType())) {
                sendResponse(exchange, 400, "Path is not a directory");
                return;
            }

            // 8.3 Finding all children of the directory
            List<String> children = new ArrayList<>();
            for (Map.Entry<String, MetadataEntry> e : metadata.entrySet()) {
                // aka entries where this path is the parent
                if (path.equals(e.getValue().getParent())) {
                    children.add(e.getKey());
                }
            }

            // 8.4 Returning the sorted list of children
            Collections.sort(children);
            String response = String.join(", ", children);
            System.out.println("[Server " + serverId + "] Listed directory: " + path);
            sendResponse(exchange, 200, response.isEmpty() ? "(empty)" : response);
        } catch (Exception e) {
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }

    // 9. Handling getting file/directory metadata
    private void handleStat(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        String path = getQueryParam(query, "path");

        if (path == null || path.isEmpty()) {
            sendResponse(exchange, 400, "Missing or invalid 'path' parameter");
            return;
        }

        try {
            MetadataEntry entry = metadata.get(path);
            if (entry == null) {
                sendResponse(exchange, 404, "Path not found");
                return;
            }

            // 9.1 Formating + returning the metadata
            String response = String.format("Path: %s, Type: %s, Parent: %s, Timestamp: %d",
                    entry.getPath(), entry.getType(),
                    entry.getParent() != null ? entry.getParent() : "root",
                    entry.getTimestamp());
            System.out.println("[Server " + serverId + "] Stat: " + path);
            sendResponse(exchange, 200, response);
        } catch (Exception e) {
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }

    // 10. Handling the removal of a file or directory
    private void handleRm(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }

        String query = exchange.getRequestURI().getQuery();
        String path = getQueryParam(query, "path");

        if (path == null || path.isEmpty()) {
            sendResponse(exchange, 400, "Missing or invalid 'path' parameter");
            return;
        }

        try {
            MetadataEntry entry = metadata.get(path);
            if (entry == null) {
                sendResponse(exchange, 404, "Path not found");
                return;
            }

            // 10.1 Check if directory is empty (only for directories)
            if ("dir".equals(entry.getType())) {
                for (Map.Entry<String, MetadataEntry> e : metadata.entrySet()) {
                    if (path.equals(e.getValue().getParent())) {
                        sendResponse(exchange, 400, "Directory not empty");
                        return;
                    }
                }
            }

            // 10.2 Remove the entry + save to disk
            metadata.remove(path);
            save();
            System.out.println("[Server " + serverId + "] Removed: " + path);
            sendResponse(exchange, 200, "Removed: " + path);
        } catch (Exception e) {
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }

    // 11. Dumping all metadata stored on the server
    private void handleDump(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[Server ").append(serverId).append("]\n");

            // 11.1 Getting all entries sorted by path for readble output
            List<MetadataEntry> entries = new ArrayList<>(metadata.values());
            entries.sort(Comparator.comparing(MetadataEntry::getPath));

            // 11.2 Formating each entry
            for (MetadataEntry entry : entries) {
                sb.append(String.format("  %s -> {type=%s, parent=%s, ts=%d}\n",
                        entry.getPath(),
                        entry.getType(),
                        entry.getParent() != null ? entry.getParent() : "root",
                        entry.getTimestamp()));
            }

            if (entries.isEmpty()) {
                sb.append("  (no entries)\n");
            }

            System.out.println("[Server " + serverId + "] Dump requested");
            sendResponse(exchange, 200, sb.toString());
        } catch (Exception e) {
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }

    // 12. Handling the tree command to list directory contents in a tree-like format
    // with relative paths
    private void handleTree(HttpExchange exchange) throws IOException {
        handleGenericTree(exchange, false);
    }

    // with absolute paths
    private void handleFullTree(HttpExchange exchange) throws IOException {
        handleGenericTree(exchange, true);
    }

    // Generic handling (relative or absolute paths)
    private void handleGenericTree(HttpExchange exchange, boolean useAbsolutePaths) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String path;

        // If there is no query string, default to root ("/")
        if (query == null || query.isEmpty()) {
            path = "./";
        }
        else {
            path = getQueryParam(query, "path");
            if (path == null || path.isEmpty()) {
                sendResponse(exchange, 400, "Missing or invalid 'path' parameter");
                return;
            }
        }
        try {
            MetadataEntry entry = metadata.get(path);
            if (entry == null) {
                sendResponse(exchange, 404, "Path not found");
                return;
            }
            if (!"dir".equals(entry.getType())) {
                sendResponse(exchange, 400, "Path is not a directory");
                return;
            }
            // 12.1 Build the tree output
            StringBuilder treeOutput = new StringBuilder();
            treeOutput.append(path);
            treeOutput.append("\n");
            buildTree(path, treeOutput, 0, useAbsolutePaths);
            System.out.println("[Server " + serverId + "] Tree: " + path);
            sendResponse(exchange, 200, treeOutput.toString());
        } catch (Exception e) {
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }

    // Helper method: extracting parent path from a given path
    // example: "/home/maria" -> "/home", "/home" -> "/", "/" -> null
    private String getParentPath(String path) {
        if (path.equals("/")) {
            return null; // root has no parent
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == 0) {
            return "/"; // parent is root
        }
        return lastSlash > 0 ? path.substring(0, lastSlash) : null;
    }

    // Helper method: extracting query param from URL
    private String getQueryParam(String query, String key) {
        if (query == null) return null;
        String[] params = query.split("&");
        for (String param : params) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && key.equals(pair[0])) {
                try {
                    return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
                } catch (Exception e) {
                    return pair[1];
                }
            }
        }
        return null;
    }

    // Helper method: sending HTTP response
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        byte[] bytes = response == null ? new byte[0] : response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }


    // Helper method: recursively build the tree output for a given path
    private void buildTree(String path, StringBuilder output, int depth, boolean useAbsolutePaths) {
        // Find all children of the current path
        List<String> children = new ArrayList<>();
        for (Map.Entry<String, MetadataEntry> entry : metadata.entrySet()) {
            String parent = entry.getValue().getParent();
            if (path.equals(parent)) {
                children.add(entry.getKey());
            }
        }
        Collections.sort(children); // Sort children for consistent output

        // Sort children for consistent output
        for (int i = 0; i < children.size(); i++) {
            String child = children.get(i);
            MetadataEntry childEntry = metadata.get(child);
            boolean isLast = (i == children.size() - 1);

            // Indentation
            for (int j = 0; j < depth; j++) {
                output.append("    ");
            }

            // Branch symbol
            output.append(isLast ? "└── " : "├── ");

            // Child name
            String name;
            if (useAbsolutePaths){
                name = child;
            }
            else {
                name = child.substring(child.lastIndexOf('/') + 1);
            }
            output.append(name);
            output.append("\n");

            // Recurse if it's a directory
            if ("dir".equals(childEntry.getType())) {
                buildTree(child, output, depth + 1, useAbsolutePaths);
            }
        }
    }

    // Inner class: representing a single metadata entry
    public static class MetadataEntry {
        private final String path; // full path
        private final String type; // "file" or "dir"
        private final String parent; // parent directory path
        private final long timestamp; // creation time

        public MetadataEntry(String path, String type, String parent, long timestamp) {
            this.path = path;
            this.type = type;
            this.parent = parent;
            this.timestamp = timestamp;
        }

        public String getPath() { return path; }
        public String getType() { return type; }
        public String getParent() { return parent; }
        public long getTimestamp() { return timestamp; }
    }

    // 13. Stop the HTTP server
    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[Server " + serverId + "] HTTP server stopped");
        }
    }
}
