import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Function;

public class Step3Server {

    private ServerSocket serverSocket;
    // 函数编程
    private Function<String, String> handler;

    public Step3Server(Function<String, String> handler) {
        this.handler = handler;
    }

    public void listen(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        while (true) {
            this.accept();
        }
    }

    public void accept() throws IOException {
        Socket socket = serverSocket.accept();
        new Thread(() -> {
            this.handler(socket);
        }).start();
    }

    public void handler(Socket socket) {
        try {
            // blocking
            // thread  -->sleep --> other thread
            // 需要更多的线程处理更多的请求  阻塞模型
            DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(dataInputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String line = "";
            while (!((line = bufferedReader.readLine()).isBlank())) {
                // 输入有数据
                stringBuilder.append(line + "\r\n");
            }
            String request = stringBuilder.toString();
            System.out.println("收到请求:");
            var bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            String response = handler.apply(request);
            bufferedWriter.write(response);
            bufferedWriter.flush();
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        Step3Server step3Server = new Step3Server(req -> {
            String httpResponse = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=UTF-8\r\n" +
                    "\r\n" +
                    "<html><body><h1>Hello, World!</h1></body></html>";
            return httpResponse;
        });
        step3Server.listen(8001);
    }
}
