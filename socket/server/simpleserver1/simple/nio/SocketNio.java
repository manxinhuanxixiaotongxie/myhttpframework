package simple.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;

/**
 * 服务端代码
 *
 *
 */
public class SocketNio {
    ServerSocketChannel serverSocketChannel = null;

    List<SocketChannel> linkedList = new LinkedList<>();

    public void listen(int port) throws IOException {
        // 监听端口
        serverSocketChannel = ServerSocketChannel.open();
        // 设置非阻塞
        serverSocketChannel.configureBlocking(false);
        // 绑定端口
        serverSocketChannel.bind(new InetSocketAddress(port));

        while (true) {
            this.accept();
        }
    }


    /**
     * accept时代
     *
     * 轮询发生在用户态
     *
     * @throws IOException
     */
    public void accept() throws IOException {
        // 非阻塞
        SocketChannel accept = serverSocketChannel.accept();
        if (accept == null) {
            System.out.printf("Socket accept() returned null\n");
        } else {
            // 有连接
            linkedList.add(accept);
        }
        // 检查是否有文件描述符就绪
        // 可以在堆内  也可以在堆外
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        for (SocketChannel socketChannel : linkedList) {
            // 读
            int read = socketChannel.read(buffer);
            // 什么时候有数据包到达
            if (read > 0) {
                // socket数据到达 可以操作读
                // 翻转
                buffer.flip();
                byte[] bytes = new byte[buffer.limit()];
                buffer.get(bytes);

                String b = new String(bytes);
                System.out.println(b);
                buffer.clear();
            }
        }
    }


    /**
     * nio测试
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        // 测试Java NIO
        SocketNio socketNio = new SocketNio();
        socketNio.listen(9090);
    }
}
