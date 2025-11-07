package mynettyV1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 事件循环组
 *
 */
public class SelectorThreadGroup {


    // 多路复用器的数量
    SelectorThread[] selectorThreads;

    /**
     * 为什么要使用线程安全的整数自增
     * 在进行多路复用器选择的时候 需要进行轮询 轮询是发生在多线程环境下的
     *
     */
    AtomicInteger idx = new AtomicInteger();

    ServerSocketChannel server;

    SelectorThreadGroup stg;

    // 设置子事件循环组
    public void  setWorker(SelectorThreadGroup stg) {
        this.stg = stg;
    }


    SelectorThreadGroup(int num) {
        if (num > 0) {
            selectorThreads = new SelectorThread[num];
            // 初始化多路复用器并且运行
            // 多个多路复用器会被创建
            // 多个多路复用器都在等待事件的注册
            for (int i = 0; i < num; i++) {
                // 这里已经变成了子时间循环组
                selectorThreads[i] = new SelectorThread(this);
                new Thread(selectorThreads[i]).start();
            }
        }
    }


    /**
     * 不管是不是采用事件循环组 在绑定的端口的时候
     * 都要将端口绑定在多路复用器上
     * @param port
     */
    public void bind(int port) {
        try {

            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.bind(new InetSocketAddress(port));
            System.out.println("服务器启动成功，监听端口：" + port);
            // 注册
//            SelectorThread selectorThread = getNextSelectorThread();
//            SelectorThread selectorThread = selectorThreads[0];
//            selectorThread.lbq.add(server);
//            selectorThread.selector.wakeup();
//            nextSelector(server);
            //
            nextSelectorV3(server);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }


    /**
     *
     *
     * 注册
     *
     *
     * @param channel
     */
    public void nextSelectorV3(Channel channel) {
        // 线程0作为接受事件 其他作为R/W
        try {
            if (channel instanceof ServerSocketChannel) {
                // 在父事件循环组选自一个selector
                // 这里需要在当前的boss事件循环组中找到连接需要注册的那个多路复用器
                // 将该连接注册到该多路复用器上
                SelectorThread nextSelectorThread = getNextSelectorThread();
                nextSelectorThread.lbq.put(channel);
                nextSelectorThread.selector.wakeup();
            } else {
                // 在工作事件循环组中进行选择
                SelectorThread selectorThread = getNextSelectorThreadV3();
                selectorThread.lbq.put(channel);
                selectorThread.selector.wakeup();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    // 获取多路复用器并注册
    public void nextSelectorV2(Channel channel) {
        // 线程0作为接受事件 其他作为R/W
        try {
            if (channel instanceof  ServerSocketChannel) {
                selectorThreads[0].lbq.put(channel);
                selectorThreads[0].selector.wakeup();
            } else {
                SelectorThread st = getNextSelectorThreadV2();
                st.lbq.add(channel);
                st.selector.wakeup();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    // 获取多路复用器并注册
    public void nextSelector(Channel channel) {
        // 在当前事件循环组中轮询选择一个多路复用器
        SelectorThread st = getNextSelectorThread();
        st.lbq.add(channel);
        st.selector.wakeup();

    }


    // 选择多路复用器

    /**
     * 这个方法实现的多路复用器的选择是轮训的方式会导致一个selector既要处理连接
     * 又要处理读写事件
     *
     * 这里轮询发生数据倾斜
     * <p>
     * 因此  考虑将连接交给一个单独的selector去处理
     *
     * @return
     */
    public SelectorThread getNextSelectorThread() {
        // 轮询获取需要注册的多路复用器
        int num = idx.incrementAndGet() % selectorThreads.length;
        return selectorThreads[num];
    }

    /**
     * 将读写事件交给另外的几个多路复用器线程
     * @return
     */
    public SelectorThread getNextSelectorThreadV2() {
        int num = idx.incrementAndGet() % (selectorThreads.length - 1);
        return selectorThreads[num + 1];
    }

    /**
     * 在子事件循环组上进行选择多路复用器
     *
     * @return
     */
    public SelectorThread getNextSelectorThreadV3() {
        int num = idx.incrementAndGet() % (stg.selectorThreads.length);
        return stg.selectorThreads[num];
    }

}
