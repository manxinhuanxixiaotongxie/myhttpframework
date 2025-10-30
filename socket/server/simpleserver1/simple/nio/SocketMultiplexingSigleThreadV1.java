package simple.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * 多路复用器单线程版本测试
 *
 */
public class SocketMultiplexingSigleThreadV1 {

    private ServerSocketChannel serverSocketChannel = null;

    // 多路复用器抽象
    private Selector selector = null;

    /**
     * 监听端口
     *
     * @param port
     */
    public void listen(int port) throws IOException {
        // 绑定端口
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(port));


        // 上述的行为其实与多路复用器无关
        // 建立连接的过程是一致的
        // selector是抽象层 对应可以是poll select kquue epoll
        selector = Selector.open();

        // 注册accept 在poll、select、epoll有较大差异
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.printf("SocketMultiplexingSignleThreadV1 listen on port %d\n", port);
        while (true) {
            this.accept();
        }
    }

    /**
     * 多路复用器下的accept行为
     *
     * @throws IOException
     */
    public void accept() throws IOException {
        // 已经可以进行读取事件 对连接进行处理
        while (selector.select() > 0) {
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    // 连接
                    acceptHandler(key);
                } else if (key.isReadable()) {
                    // 可读
                    readHandler(key);
                }
            }
        }
    }

    /**
     * 读处理
     */
    public void readHandler(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        buffer.clear();
        int read = 0;
        try {
            // 读取数据
            while (true) {
                read = socketChannel.read(buffer);
                if (read > 0) {
                    // 有数据到达
                    // 开始读取 翻转
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        socketChannel.write(buffer);
                    }
                    buffer.clear();
                } else if (read == 0) {
                    break;
                } else {
                    socketChannel.close();
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }


    }

    /**
     * 新连接进来
     *
     * @param key
     * @throws IOException
     */
    private void acceptHandler(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        // 连接进来 表示连接socket
        SocketChannel accept = serverChannel.accept();
        if (accept == null) {
            //
        }
        // 这里accept
        accept.configureBlocking(false);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        // 注册
        accept.register(selector, SelectionKey.OP_READ, buffer);
    }

    public static void main(String[] args) throws IOException {
        SocketMultiplexingSigleThreadV1 simpleServer = new SocketMultiplexingSigleThreadV1();
        simpleServer.listen(9099);
    }

}
