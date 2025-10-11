import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;

/**
 * 最原始的socket服务器端
 *
 * @param args
 */
public class Step1Server {

    public static void main(String[] args) throws IOException {
        // 创建serverSocket 建立连接，开辟文件资源
        // 一次socket连接会被标识为的一个文件描述符
        ServerSocket serverSocket = new ServerSocket(8001);
        // 用户态（用户空间）一直在轮训
        /**
         * 完整的tcp连接基于ip+port   使用的是host到host的连接
         */
        while (true) {
            // 建立连接 创建新的socket文件对象
            var socket = serverSocket.accept();
            // 新的连接进来
            // 获取连接的输入刘
            var dataInputStream = new DataInputStream(socket.getInputStream());
            var bufferedReader = new BufferedReader(new InputStreamReader(dataInputStream));
            var requestBuilder = new StringBuilder();
            // 读取数据
            String line = "";
            while (!(line = bufferedReader.readLine()).isBlank()) {
                // 有数据包进来
                requestBuilder.append(line + "\n");
            }
            var request = requestBuilder.toString();
            System.out.println("收到请求：\n" + request);
            // 响应数据
            var response = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            response.write("HTTP/1.1 200 OK\r\n\nHello World");
            response.flush();
            // 关闭连接
            socket.close();
        }
    }
}