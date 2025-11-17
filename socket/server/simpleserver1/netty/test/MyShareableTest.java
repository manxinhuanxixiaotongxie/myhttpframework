package netty.test;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class MyShareableTest {


    /**
     *
     */
    @Test
    public void sharableTest01() {

    }
}


/**
 ChannelHandler.Sharable netty核心注解  用于标记一个channelHandler实例可以被多个channel共享使用 无需为每个新连接创建新的handler实例

 1.未加该注解的handler 默认不可共享 每个channel必须使用独立实例（重复添加会报异常）
 2.。加了该注解的handler：可以共享 所有channel共用一个实例（节省内存、统一配置）
 3.共享的前提 handler必须是线程安全的（无状态或线程状态安全） 否则会出现并发问题

 为什么需要@Sharable（核心场景）
 netty中一个serverSocketChannel会接受大量的socketchannel（每个连接对应一个） 如果每个连接都创建一个handler实例（比如日志handler 编码handler）
 会导致：
 1.内存浪费：成千上万个链接的会创建成千上万的handler实例
 2.配置冗余：修改handler逻辑需同步所有的实例（几乎不可能）
 3.性能开销：频繁创建、销毁handler增加gc压力

 @sharable允许所有handler共用一个handler实例 完美解决以上问题 --典型场景包括：
 1.日志handler ：仅打印日志 无状态
 2.编码、解码handler：仅做数据转换 无链接状态
 3.权限校验handler（如token校验）：逻辑固定 无每个连接的私有状态
 4.统计handler（如连接数统计） 需共享统计计数器（线程安全）

 （二）：@Sharable核心机制：
 1.未加@sharable netty会认为是“非线程安全 有连接私有状态”的 禁止共享：
 若尝试将同一个handler实例添加到多个channel的pipline IllegalStateException异常
 2.必须为每个channel创建独立handler实例（通常在ChannelInitializer中完成）
 *
 *
 */
class MyNotSharabelHandler extends ChannelInboundHandlerAdapter {

    /**
     * 非共享handler展示
     */
    private int count = 0;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        count++;
        System.out.println("当前连接接受数据次数：" + count);
        ctx.fireChannelRead(msg);
    }
}

class MyNotSharableChannelInitializer extends ChannelInitializer<SocketChannel> {

    /**
     * 非共享handler注册 每次新连接创建新的handler实例
     *
     * @param ch
     * @throws Exception
     */
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        // 初始化
        ch.pipeline().addLast(new MyNotSharableChannelInitializer());
    }
}

@ChannelHandler.Sharable
class MySharableHandler extends ChannelInboundHandlerAdapter {
    /**
     * 可共享handler
     */
    private final AtomicInteger totalCount = new AtomicInteger(0);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        int count = totalCount.incrementAndGet();
        System.out.println("所有连接接受数据总次数：" + count);
        ctx.fireChannelRead(msg);
    }
}

class MyGlobalHanderFactory {
    public static final  MySharableHandler INSTANCE = new MySharableHandler();
}

class MySharableChannelInitializer extends ChannelInitializer<SocketChannel> {

    /**
     * 共享handler注册 所有连接共用同一个handler实例
     *
     * @param ch
     * @throws Exception
     */
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        // 初始化
        ch.pipeline().addLast(MyGlobalHanderFactory.INSTANCE);
    }
}


