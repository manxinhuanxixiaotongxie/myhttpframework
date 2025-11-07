package mynettyV2;

import java.io.IOException;

/**
 * 基于NIO 仿netty框架
 *
 */
public class MainThread {

    public static void main(String[] args) throws IOException {
        EventLoopGroup boss = new EventLoopGroup(1);
        EventLoopGroup worker = new EventLoopGroup(3);
        ServerBootStrap b = new ServerBootStrap();
        b.group(boss, worker).bind(9090);
    }
}
