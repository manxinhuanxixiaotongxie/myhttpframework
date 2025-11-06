package mynettyV2;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 事件循环组
 */
public class EventLoop implements Executor {


    Thread thread = null;

    Selector selector;

    String name;

    BlockingQueue events = new LinkedBlockingQueue();

    EventLoop(String name) {
        try {
            this.name = name;
            selector = Selector.open();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void execute(Runnable task) {

        try{
            events.put(task);
            this.selector.wakeup();
        }catch (Exception e) {

        }

    }

    /**
     * 死循环用于处理连接事件或者读写事件
     *
     */
    public void run() throws IOException {


//        while (true) {
//
//        }
        // 死循环
        for (;;) {

            // 处理事件
            int num = selector.select();

            if (num > 0) {
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (key.isAcceptable()) {
                        // 处理连接事件
                    }else if (key.isReadable()) {
                        // 处理读事件
                    }
                }
            }

        }


    }



}
