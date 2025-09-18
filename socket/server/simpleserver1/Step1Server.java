import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.util.function.Function;

/**
 * 针对Server1代码进行重构
 */
public class Step1Server {

    ServerSocket serverSocket;

    /**
     * 处理器
     */
    Function<String, String> handler;

    public Step1Server(Function<String, String> handler) {
        this.handler = handler;
    }

    public void listen(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        while (true) {
            this.accept();
        }
    }


    public void accept() {
        try {
            var socket = serverSocket.accept();
            // 获取输入流
            var inputStream = new DataInputStream(socket.getInputStream());
            var bufferReader = new BufferedReader(new InputStreamReader(inputStream));
            var stringBuilder = new StringBuilder();
            String line = "";
            while (!((line = bufferReader.readLine()).isBlank())) {
                // 输入信息存在
                stringBuilder.append(line + "\n");
            }
            var request = stringBuilder.toString();
            System.out.printf("收到请求：%s\n", request);
            var bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            String apply = this.handler.apply(request);
            bufferedWriter.write(apply);
            bufferedWriter.flush();
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) throws IOException {
        Step1Server server = new Step1Server(request -> {
            // 处理请求
            String httpResponse = "HTTP/1.1 200 OK\r\n\r\nHello World!\n";
            return httpResponse;
        });
        server.listen(8001);
    }
}
