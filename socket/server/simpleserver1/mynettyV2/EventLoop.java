package mynettyV2;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 事件循环组
 */
public class EventLoop implements Executor {


    Thread thread = null;

    Selector selector;

    String name;

    BlockingQueue events = new LinkedBlockingQueue();

    /**
     * 每个线程对应一个多路复用器
     *
     * @param name
     */
    EventLoop(String name) {
        try {
            this.name = name;
            selector = Selector.open();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 自定义excute
     * @param task the runnable task
     */
    @Override
    public void execute(Runnable task) {

        try{
            events.put(task);
            this.selector.wakeup();
        }catch (Exception e) {
            e.printStackTrace();
        }

        if (!inEventLoop()) {
            new Thread(() -> {
                try {
                    thread = Thread.currentThread();
                    EventLoop.this.run();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, name).start();
        }

    }

    /**
     * 死循环用于处理连接事件或者读写事件
     *
     */
    public void run() throws IOException, InterruptedException {


//        while (true) {
//
//        }
        // 死循环  处理连接额
        for (;;) {

            // 处理事件
            int num = selector.select();

            if (num > 0) {
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
//                    if (key.isAcceptable()) {
//                        // 处理连接事件
//                    }else if (key.isReadable()) {
//                        // 处理读事件
//                    }
                    Handler handler = (Handler) key.attachment();
                    // 处理读事件
                    handler.doRead();
                }
            }

            runTask();

        }

    }

    public void runTask() throws IOException, InterruptedException {
        for (int i = 0; i < 5;i ++) {
            Runnable task = (Runnable) events.poll(10, TimeUnit.MILLISECONDS);
            if (task != null) {
                task.run();
            }
        }
    }


    private boolean inEventLoop() {
        return thread == Thread.currentThread();
    }



}
