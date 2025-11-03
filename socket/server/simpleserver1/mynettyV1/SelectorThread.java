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
                    // 链表解决的是多路复用器存在的问题 由于多个多个多路复用器被创建
                    // 但是并没有连接进来 也并没有写入服务端的OP_ACCEPT事件
                    // 导致这些被创建的多路复用器被阻塞
                    // 链表就是为了解决 这些事件需要被注册的时候 由于多路复用器被阻塞导致的程序中断
                    // 因此链表就是的为了注册监听事件或者是注册读取事件
                    // 用于将事件注册到多个多路复用器上面来
                    Channel channel = lbq.take();
                    if (channel instanceof ServerSocketChannel) {
                        ServerSocketChannel server = (ServerSocketChannel) channel;
                        // 在这个被选择的selector上注册监听事件
                        // 有多个selector可以被选择
                        // 对与每个多路复用器来说都可以处理连接以及处理读写
                        // 这个链表里面的逻辑是处理注册事件
                        server.register(selector, SelectionKey.OP_ACCEPT);
                    } else if (channel instanceof SocketChannel) {
                        SocketChannel client = (SocketChannel) channel;
                        ByteBuffer buffer = ByteBuffer.allocate(4096);
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
             * 多个多路复用器已经被创建，
             * 在某个多路复用器上的有连接进来的时候 需要选择这个
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
