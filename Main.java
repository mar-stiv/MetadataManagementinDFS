import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // 1. Read environment variables to determine how to run (either router/server mode)
        String mode = System.getenv("MODE");
        String serverId = System.getenv("SERVER_ID");
        String portStr = System.getenv("PORT");
        String serversEnv = System.getenv("SERVERS");

        // 2. Validating that environment variables have been set
        if (mode == null || mode.isEmpty()) {
            System.err.println("Error: MODE environment variable is required");
            System.err.println("Set MODE=router or MODE=server");
            System.exit(1);
        }

        if (portStr == null || portStr.isEmpty()) {
            System.err.println("Error: PORT environment variable is required");
            System.exit(1);
        }

        int port = 0;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid PORT value: " + portStr);
            System.exit(1);
        }

        if (port <= 0) {
            System.err.println("Error: PORT must be a positive integer");
            System.exit(1);
        }

        try {
            // 3. Start the router which distributes requests to servers
            if ("router".equals(mode)) {
                System.out.println("Starting Router on port " + port);

                // 3.1 The router needs to know which servers are available
                if (serversEnv == null || serversEnv.isEmpty()) {
                    System.err.println("Error: SERVERS environment variable is required for router mode");
                    System.err.println("Example: SERVERS=http://server1:8081,http://server2:8082,http://server3:8083");
                    System.exit(1);
                }

                // 3.2 Parsing the servers env vars into a list of server URLs
                List<String> backends = Arrays.asList(serversEnv.trim().split("\\s*,\\s*"));

                System.out.println("Starting Router on port " + port + " with backends: " + backends);
                RouterGateway router = new RouterGateway(backends, port); // create + start the router
                router.start();
                System.out.println("Router is running. Press Ctrl+C to stop.");
                Thread.currentThread().join(); // keep the main thread alive so that the router runs in the background threads

            } else if ("server".equals(mode)) {
                // 4. Start a metadata server
                // 4.1 Check that serverId is unqiue
                if (serverId == null || serverId.isEmpty()) {
                    System.err.println("Error: SERVER_ID environment variable is required for server mode");
                    System.exit(1);
                }

                System.out.println("Starting Metadata Server " + serverId + " on port " + port);

                // 4.2 Create + start metadata server
                MetadataServer metadataServer = new MetadataServer(port, serverId);
                metadataServer.start();

                System.out.println("Metadata Server " + serverId + " is running. Press Ctrl+C to stop.");

                Thread.currentThread().join(); // 4.3 Keeping the main thread alive
            } else {
                System.err.println("Error: Unknown MODE: " + mode);
                System.err.println("Valid modes: router, server");
                System.exit(1);
            }

        } catch (InterruptedException e) {
            // Occurs when pressing Ctrl+C
            System.out.println("Shutting down...");
        } catch (Exception e) {
            // Handling other unexpected errors
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
