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
 * 多路复用器多线程版本测试
 *
 *
 * 多路复用器第一个多线程版本
 *
 * 分析一下单线程多路复用器的缺点：
 * 一个多路复用器既要处理连接 又要处理读写事件
 * 如果某个读写出现阻塞 会导致整个服务端出现阻塞
 *
 * 于是可以考虑将IO事件处理交给线程池处理
 *
 * 但是会出现问题
 *
 * 由于写事件的触发机制是send_queue 只要send-queue为空
 * 或者send_queue有空间  写事件会被重复调起
 *
 * 在多线程场景下  由于存在CPU执行单元的切换等等一些列行为
 *
 * 会导致写事件被重复调起 导致出现死循环（写事件被重复调起 出现bug）
 *
 *
 *
 *
 * 本版本有bug
 *
 */
public class SocketMultiplexingSingleThreadV2 {

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
        // 或得所有的注册的文件描述符
        Set<SelectionKey> keys = selector.keys();
        System.out.println("SocketMultiplexingSignleThreadV1 accept" + keys.size());
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
                    // 出现问题的原因是：
                    /**
                     * 这里读变成了多线程
                     * 读事件只做了一件事 那就是给当前连接注册写事件
                     * 写事件只要被触发 就会被重复调起
                     * 为什么读事件需要这段代码：
                     * 是因为即使调用了cancel 只是将SelectionKey标记为取消状态
                     * 但是不会立即从多路复用器的selectionKeys集合中删除
                     * 在写事件处理中 如果有多线程并发处理同一个key 或者selectionkey还没来得及清理canceled key
                     * 下次selecto时 key可能还会被选中 导致写事件被重复到期
                     * 注意：key.cancel()只是将key标记为取消状态 真正移除要等到下次selector.select()时才会生效
                     *
                     * 因此：想要彻底避免这个问题 需要枷锁或者用队列等待机制保证同一个key只被一个线程处理
                     *
                     * 尽管这个缺陷可以被修复 但是会出现很多多余的系统调用
                     *
                     * 因此在商用的多个版本的io框架中 并不会采用这种多线程模型
                     *
                     * 必须要将io的读写操作与selectot强行绑定 并且保证他们是线性处理
                     *
                     * 因此 衍生出SelectorThread 在这个多线程模型中  seletcor与文件描述符在处理IO读写的时候是线性的
                     * 避免出现由于多线程导致的问题以及为了解决这个问题引入的更多的系统调用的开销
                     *
                     *
                     */
                    key.cancel();
                    readHandler(key);
                }else if (key.isWritable()) {
                    key.cancel();
                    writeHandler(key);
                }
            }
        }
    }


    /**
     *
     *
     * 这里补充一个知识：
     *
     * 为什么要使用红黑树 主要是为了提高遍历的效率
     * 什么场景下需求遍历呢：
     *
     * 1.事件注册/修改/删除时 内核需要遍历红红黑树查找目标FD 以完成对应操作
     *
     * 2.内核检测事件就绪时
     * 通常情况下 epoll依赖的IO事件回调机制（网卡中断、磁盘管理器中断等）直接将就绪的文件袋描述符擦混入就绪链表
     * 无需遍历红黑树 但是在某些特定场景下 可能会出现遍历的行为（如FD状态异常  事件掩码变更） 内核需要遍历红黑树检查部分FD的状态
     *
     * jvm红黑树的遍历场景：
     * 1.通道注销时 调用cancel()方法 或者通道关闭时 jvm需要遍历红黑树对应的selectionkey并移除 同时通过epoll_ctl(EPOLL_CTL_DEL)
     * 通知内核从epoll红黑树中删除该FD
     *
     * 2.查询所有注册的通道时：
     * 当用户调用selector,keys()获取所有注册的的selectionkey时 jvm需要遍历红黑树 并生成一个快照返回（避免并发修改问题）
     *
     * select()操作的准备阶段：
     *
     * 在调用select()方法之前 jvm可能需要遍历红黑树检查是否有已取消的slectionkey并清理这些无效节点 确保内核注册的FD与jvm维护的一致
     *
     * 事件就绪后的映射处理：
     * 当epoll_wait返回就绪FD之后 jvm需要遍历链表 将内核发挥的FD映射到对应的selectionkey 并将其加入selectionkey集合 供用户代码处理
     *
     *
     *
     * 在java的多路复用器中 注册写事件时
     *
     * 在poll、select中：
     *
     *
     *
     * 在epoll中：
     * jvm：创建selectionkey 封装以下信息：
     *
     *1.对用通道
     * 2.注册的选择器
     * 3.感兴趣的事件
     * 4.附加的用户数据
     * 5.文件描述符(File Descriptor, FD(对应内核的文件描述符))
     *
     * 内核：
     * 1.维护红黑树数据结构  通过红给树管理所有注册的selectionkey 注册时会将新生成的selectionkey插入红黑树
     *
     * 就绪链表：刚开始为空 当FD对应的事件就绪 内核会将改文件描述符移入此链表 供epoll_wait使用
     *
     *
     *
     *
     * @param key
     */
    private void writeHandler(SelectionKey key) {
        new Thread(()->{
            System.out.println("write handler...");
            SocketChannel client = (SocketChannel) key.channel();
            ByteBuffer buffer = (ByteBuffer) key.attachment();
            buffer.flip();
            while (buffer.hasRemaining()) {
                try {

                    client.write(buffer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            buffer.clear();
            key.cancel();
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

    }

    /**
     * 读处理
     */
    public void readHandler(SelectionKey key) throws IOException {

        new Thread(() -> {

            SocketChannel socketChannel = (SocketChannel) key.channel();
            ByteBuffer buffer = (ByteBuffer) key.attachment();
            buffer.clear();
            int read = 0;
            try {
                // 读取数据
                while (true) {
                    read = socketChannel.read(buffer);
                    if (read > 0) {
                        socketChannel.register(key.selector(),SelectionKey.OP_WRITE,buffer);
                    } else if (read == 0) {
                        break;
                    } else {
                        // 空轮训bug
                        socketChannel.close();
                        break;
                    }
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }).start();



    }

    /**
     * 新连接进来
     *
     * @param key
     * @throws IOException
     */
//    private void acceptHandler(SelectionKey key) throws IOException {
//        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
//        // 连接进来 表示连接socket
//        SocketChannel accept = serverChannel.accept();
//        if (accept == null) {
//            //  这段代码仅仅是为了演示 同一个连接会导致返回null
//        }
//        // 这里accept
//        accept.configureBlocking(false);
//        ByteBuffer buffer = ByteBuffer.allocate(1024);
//        // 注册
//        accept.register(selector, SelectionKey.OP_READ, buffer);
//    }

    public void acceptHandler(SelectionKey key) {
        try {
            ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
            SocketChannel client = ssc.accept();
            client.configureBlocking(false);
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            client.register(selector, SelectionKey.OP_READ, buffer);
            System.out.println("-------------------------------------------");
            System.out.println("新客户端：" + client.getRemoteAddress());
            System.out.println("-------------------------------------------");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        SocketMultiplexingSingleThreadV2 simpleServer = new SocketMultiplexingSingleThreadV2();
        simpleServer.listen(9099);
    }

}
