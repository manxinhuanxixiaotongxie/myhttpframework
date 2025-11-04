package mynettyV1;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 多路复用器线程
 */
public class SelectorThread extends Thread {

    /**
     * 
     * 补充：
     * 每个线程都是默认是boss 当在bossgroup里面的时候
     * bossgroup的长度是可以为多个的 并且要给bossgroup里面的每个线程都初始化一个多路复用器 并且给每个多路复用器注册一一个accept事件
     *
     * 这里有个知识点需要补充下：
     * 之所以可以这么做 根本原因是因为核心支持  SO_REUSEPORT 下面有详细的解释
     *
     *
     * 当前线程所持有的多路复用器
     * 每个线程对应一个多路复用器
     * 每个多路复用器处理多个连接
     * 但是在处理每个链接的时候 要保证是进行的线性处理
     *
     */
    public Selector selector;

    public SelectorThreadGroup stg;

    public LinkedBlockingQueue<Channel> lbq;


    SelectorThread(SelectorThreadGroup stg) {
        try {
            this.stg = stg;
            selector = Selector.open();
            lbq = new LinkedBlockingQueue<>();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 重写run方法
    @Override
    public void run() {

        // 最原始版本
//            selector = Selector.open();

        // 这个方法是阻塞的 这点尤其要注意
        /**
         * 在单线程中 这个地方不会阻塞是因为
         * 在服务端初始化的时候 就已经注册了一个连接事件
         * 会将多路复用器至少有一个服务端的监听事件
         *
         * 但是在多线程环境上  selector都是提前初始化的  没有事件注册上来
         * 因此这里是阻塞的
         */

        while (true) {
            try {
                int num = selector.select();
                // 准备好的事件进行处理
                if (num > 0) {
                    // 有事件进行处理
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while (iterator.hasNext()) {
                        // 如果还有事件需要继续处理
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        // 连接事件 处理连接事件
                        if (key.isAcceptable()) {
                            acceptHandler(key.channel());
                        }

                    }
                }

                // process task
                if (!lbq.isEmpty()) {
                    Channel channel = lbq.take();
                    if (channel instanceof ServerSocketChannel) {
                        ServerSocketChannel server = (ServerSocketChannel) channel;
                        server.register(selector, SelectionKey.OP_ACCEPT);
                    } else if (channel instanceof SocketChannel) {
                        SocketChannel client = (SocketChannel) channel;
                        ByteBuffer buffer = ByteBuffer.allocate(4096);
                        // 这个事件已经是boss分发过来的读写事件了 在链接建立的时候就会在子事件循环组中选择某一个多路复用器进行连接事件的注册
                        client.register(selector, SelectionKey.OP_READ, buffer);
                        System.out.println(Thread.currentThread().getName() + "register client" + client.getRemoteAddress());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 连接事件到达 处理连接事件
     */
    public void acceptHandler(Channel channel) {
        System.out.println(Thread.currentThread().getName() + "accept channel.....");
        ServerSocketChannel server = (ServerSocketChannel) channel;
        // 服务端获得客户端的连接的
        try {
            SocketChannel client = server.accept();
            client.configureBlocking(false);
            // 注册到某个selector上
            // 获取多路复用器
//            SelectorThread nextSelectorThread = stg.nextSelector(client);


            /**
             *
             *
             *
             * 读写事件不能在bossgroup上的selector上注册 必须注册到workerGroup的selector上中
             *
             *
             * bossGroup     核心职责： 接受客户端连接（三次握手）  仅接受OP_ACCEPT事件
             *
             *
             * workerGroup   核心职责： 负责已经建立连接的读写操作  负责OP_READ  OP_WRITE事件
             *
             * 每个线程堆成对应一个多路复用器
             *
             * 注意：写事件通常不会主动注册 原因之前已经解释过了  只有但需要向客户端发送大量数据 待会注册写事件 待缓冲区有空闲后触发写操作 避免数据丢失
             *
             *
             * bossgroup只负责处理连接 不处理任何读写 读写事件必须交给workergroup的多路复用器去处理    --->避免单线程同时处理连接和读写操作 导致效率低下
             *
             *
             * 读写事件注册到workergroup的完整流程：
             *
             * 客户端进来之后   netty会通过boss连接 分配给worker  --《  注册读写事件  三步 将读写事件绑定到workergroup某个selector上 全程自动化
             *
             *
             * 1.bossGroup接受连接  处理OP_ACCEPT事件
             *   1.客户端发起连接 内核通过SO_REUSEPORT机制 选择某个boss线程
             *   2.该boss被唤醒 执行accept()洗头膏调用 获取已经连接socket封装为通道
             *   3，boss线程的核心工作到此结束   他不会在自己selector上注册任何读写事件 而是将读写事件交给workergroup去处理
             *
             *
             * 2.netty自动选择workergroup中的eventloop(子事件循环)
             *   workergroup 是子事件循环组 内部包含多个worker线程（每个线程对应一个多路复用器）  netty会通过负载均衡算法 为nioSocketChannel选择一个worker线程
             *
             *   1.默认算法：子线程数小于等于3 轮训  大于3 用位运算hash 保证均匀分
             *     一个NIOSocketChannel对象 只会被绑定到一个worker线程上 并且后续的读写操作 都会被绑定到这个worker线程上
             *
             * 3.选定worker线程注册读写事件
             *    workergroup接受NIOSocketChannel之后 会自动完成读写事件的注册
             *
             *    1.调用SocketChannel的register方法 将读写事件注册到该worker线程的多路复用器上
             *    2。底层通过epoll_ctl将该socket的FD加入到该worker线程的epoll就绪链表中
             *    3.该worker线程从selector.select()阻塞中唤醒 检测到OP_READ事件  执行读写操作
             *
             *
             *
             *
             *
             *
             *
             *
             * 连接事件需要注册到bossGroup上 完全由linux内核决定  SO_REUSEPOR机制决定 连接本身无需主动选择 而是被内核分配到某个boss线程
             *
             * 整个过程与netty无关 是内核级的负载均衡
             *
             * 连接（客户端）只知道链接请求服务端端口 完全不关系服务端内部有多少个boss线程
             *
             * 整个选择boss线程的过程 是内核在服务端内部完成的  连接本身是被动接受分配结果 无需任何主动决策
             *
             *
             * 在本实例中：
             *
             * 1.内核首先提取四元组（全局唯一 不能重复）
             * 2.计算四元组的hash值 想通的四元组一定得到相同的hash值 不同的四元组有可能得到相同的hash值（hash碰撞）
             *              内核使用蘖枝的hash算法（如CRC32  Jenkins）算法 对四元组进程计算 得到一个32位或者64位的hash值
             *
             *
             * 3.哈希值取模 定位魔表boss进程
             *   内核会维护一个监听某个端口的socket列表 这个列表里面每个socket 对应一个boss线程
             *   假设bossgroup有三个进程 内核会用hash值对N做取模运算 得到一个索引
             *   这个索引直接对应监听socket列表的一个socket 而这个socket所属的boss进程 就是本次连接的目标boss进程
             *
             * 4.触发目标boss进程的selector事件
             *  内核会完成三次握手
             *    1.创建已连接socket（对应客户端连接 FD为新值）
             *    2.将目标socket（boss线程的serversocketchannel）的FD加入到该boss线程selector对应的epoll就绪链表中
             *    3.该boss线程从selector.select()阻塞中唤醒 检测到OP_ACCEPT事件 执行accept()获取已经连接的socket 挂载到该boss线程
             *
             *
             *
             *
             *
             *
             *
             *
             *
             *
             *
             */



            stg.nextSelectorV3(client);
            /**
             * 注意：这里这样写是有问题的？
             * 为什么？
             * 在多路复用器初始化的时候 是没有事件注册上去的
             * 会导致selector.select()阻塞
             * 这个地方的注册操作 会因为多路复用器阻塞导致无法注册连接事件上去
             * 怎么解决这个问题？
             * 第一种方式
             */
            // V1.1 解决方案 对这个多路复用器进行唤醒 这个方法执行以后会导致多路复用器并不会阻塞 能够进行继续
            /**
             * 这里唤醒的原因是因为accept()方法是阻塞的
             * 调用wakeup()之后 selector.select()方法会立刻返回 不会阻塞
             * 但是这样可能会依然有问题 在register还没执行的时候 由于SelectorThread是一个死循环 会再次快速调用accept()方法
             * 导致在注册之前再次陷入阻塞
             *  这个问题怎么解决呢？ 可以在SelectorThread中 将线程休眠一会 在wakeUp之后 让注册能有时间去执行
             *  但是这并不是解决方案 在多线程下  会让多路复用器市场需要考虑更多问题
             *
             *
             *  这个问题 最终解决
             *  在多路复用器新增一个队列
             */
//            nextSelectorThread.selector.wakeup();
//            client.register(nextSelectorThread.selector, SelectionKey.OP_ACCEPT);
            // V1.2 解决方案 将注册操作放到多路复用器的队列中去处理
            // 线性运行
//            nextSelectorThread.lbq.add(client);
//            nextSelectorThread.selector.wakeup();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
