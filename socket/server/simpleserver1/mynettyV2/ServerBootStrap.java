package mynettyV2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

public class ServerBootStrap {

    private EventLoopGroup group;
    private EventLoopGroup childGroup;

    ServerAcceptr serverAcceptr;

    public ServerBootStrap group(EventLoopGroup boos,EventLoopGroup child) {
        group = boos;
        childGroup = child;
        return this;
    }

    /**
     * 绑定端口
     *
     * @param port
     * @throws IOException
     */
    public void bind(int port) throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.configureBlocking(false);
        /**
         *
         * 一个serverSocketChannel只能绑定一个selector 重复注册会岛主旧selector失效
         * 如果 要提升端口的连接处理并发度 应该选择额多个serverSocketChannel共享端口
         * + 每个selector一个channel的方案 而不是让一个serverSocketChannel绑定多个selector
         *
         */
        server.bind(new InetSocketAddress(port));
        serverAcceptr = new ServerAcceptr(server,childGroup);
        EventLoop eventLoop = group.chooser();
        eventLoop.execute(new Runnable() {
            @Override
            public void run() {
                try{
                    eventLoop.name = Thread.currentThread() + eventLoop.name;
                    System.out.println("bind...server...to " + eventLoop.name);
                    server.register(eventLoop.selector, SelectionKey.OP_ACCEPT,serverAcceptr);
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

    }

}
