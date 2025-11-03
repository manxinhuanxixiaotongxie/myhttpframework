package mynettyV1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 事件循环组
 *
 */
public class SelectThreadGroup {


    // 多路复用器的数量
    SelectorThread[]  selectorThreads;

    AtomicInteger idx = new AtomicInteger();


    SelectThreadGroup(int num) {
        if (num > 0) {
            selectorThreads = new SelectorThread[num];
            // 初始化多路复用器并且运行
            for (int i = 0;i < num;i++) {
                selectorThreads[i] = new SelectorThread(this);
                new Thread(selectorThreads[i]).start();
            }
        }
    }


    public void bind(int port) {
        try {

            ServerSocketChannel server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.bind(new InetSocketAddress(port));
            System.out.println("服务器启动成功，监听端口：" + port);
            // 注册
//            SelectorThread selectorThread = getNextSelectorThread();
            SelectorThread selectorThread = selectorThreads[0];
            selectorThread.lbq.add(server);
            selectorThread.selector.wakeup();
        }catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    // 选择多路复用器

    /**
     * 这个方法实现的多路复用器的选择是轮训的方式会导致一个selector既要处理连接
     * 又要处理读写事件
     *
     * 因此  考虑将连接交给一个单独的selector去处理
     * @return
     */
    public SelectorThread getNextSelectorThread() {
        // 轮询获取需要注册的多路复用器
        int num = idx.getAndIncrement() % selectorThreads.length;
        return  selectorThreads[num];
    }

    public SelectorThread getNextSelectorThreadV2() {
        int num = idx.getAndIncrement() % (selectorThreads.length -1) ;
        return  selectorThreads[num +1];
    }





    // 获取多路复用器并注册
    public void next(){

    }



}
