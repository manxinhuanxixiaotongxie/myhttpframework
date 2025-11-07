package mynettyV2;

import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;


public class ServerAcceptr implements Handler{

    // epoll事件
    ServerSocketChannel key;

    // 子事件循环组
    EventLoopGroup cGroup;

    ServerAcceptr(ServerSocketChannel key,EventLoopGroup cGroup) {
        this.key=key;
        this.cGroup=cGroup;
    }

    /**
     * 先有连接再注册读写事件
     *
     */
    @Override
    public void doRead() {
        try {
            // 事件循环组分配子事件循环
            EventLoop eventLoop = cGroup.chooser();
            // 接受连接 这个方法不会阻塞  内核建立了连接 开辟了资源 连接事件就绪
            // 上层才可以拿到连接  向epoll中注册连接事件 后续epoll才能在数据到达
            // 硬件发起中断 经过内核与硬件的操作之后 在epoll_wait中标识读写事件就绪
            SocketChannel client = key.accept();
            client.configureBlocking(false);
            client.setOption(StandardSocketOptions.TCP_NODELAY, true);
            // 处理读写事件
            ClientReader cHandler = new ClientReader(client);
            // 注册事件
            eventLoop.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        eventLoop.name = Thread.currentThread() + eventLoop.name;
                        System.out.println("socket...send...to " + eventLoop.name);
                        client.register(eventLoop.selector, SelectionKey.OP_READ,cHandler);
                    }catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
