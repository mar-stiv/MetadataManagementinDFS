import java.io.*;
import java.net.*;
public class Client {
   public static void main(String[] args) {
       try (Socket socket = new Socket("127.0.0.1", 5000)) {
           System.out.println("Connected to the server.");
           // Input and output streams for communication
           BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
           PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
           BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
           String message;
           while (true) {
               System.out.print("Enter message: ");
               message = userInput.readLine();
               out.println(message);
               if ("exit".equalsIgnoreCase(message)) break;
               System.out.println(in.readLine());
           }
       } catch (IOException e) {
           e.printStackTrace();
       }
   }
}
