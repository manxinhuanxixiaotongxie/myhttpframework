package myhttp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Step3Server {

    ServerSocket serverSocket;
    IHandlerInterface httpHandler;

    public Step3Server(IHandlerInterface httpHandler) {
        this.httpHandler = httpHandler;
    }

    public void listen(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        while (true) {
            this.accept();
        }
    }

    // 接收客户端连接
    public void accept() throws IOException {
        // 客户端连接 阻塞
        Socket accept = serverSocket.accept();
        new Thread(() -> {
            // 客户端连接之后处理客户端请求
            try {
                this.handler(accept);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    private void handler(Socket socket) throws IOException {
        Request request = new Request(socket);
        Response response = new Response(socket);
        this.httpHandler.handler(request, response);
    }

    public static void main(String[] args) throws IOException {
        new Step3Server((req, res) -> {
            System.out.println(req.getHeaders());
            res.send("<html><body><h1>Hello World</h1></body></html>");
        }).listen(8001);

    }

}
