import java.io.*;
import java.net.*;
public class Server {
   public static void main(String[] args) {
       try (ServerSocket serverSocket = new ServerSocket(5000)) {
           System.out.println("Server started. Waiting for a client...");
           // Accept client connection
           Socket socket = serverSocket.accept();
           System.out.println("Client connected.");
           // Input and output streams for communication
           BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
           PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
           String message;
           while ((message = in.readLine()) != null) {
               System.out.println("Client: " + message);
               if ("exit".equalsIgnoreCase(message)) break;
               out.println("Server: " + message.toUpperCase());
           }
           // Close resources
           socket.close();
       } catch (IOException e) {
           e.printStackTrace();
       }
   }
}
