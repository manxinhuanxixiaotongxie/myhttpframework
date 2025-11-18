package netty.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import mynettyV2.EventLoopGroup;
import mynettyV2.ServerBootStrap;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * 测试netty的类
 * netty的初级使用 服务端为什么这么写
 */
public class MyNettyTest {



    /**
     * 客户端
     * 连接服务端
     * 1.主动发送数据给服务端
     * 2.服务端什么什么时间发送数据给客户端   event selector
     */

    @Test
    public void testClient() {
        NioEventLoopGroup selector = new NioEventLoopGroup(2);
        selector.execute(() -> {
            // 提交任务
            try{
                for (;;) {
                    System.out.println("客户端1发送数据");
                    Thread.sleep(2000);
                }
            }catch (Exception e) {

            }
        });

        /**
         * 如果NioEventLoopGroup 只有一个线程
         * 下面的任务会被发送到时间循环组吗？
         * 是不会的
         */
        selector.execute(() -> {
            // 提交任务
            try{
                for (;;) {
                    System.out.println("客户端2发送数据");
                    Thread.sleep(2000);
                }
            }catch (Exception e) {

            }
        });
    }


    /**
     * netty
     * NioSocketChannel
     * Pipline 绑定的责任链 是处理网络事件（读 写 连接）流程管道
     *
     *
     *
     * channel:单个网络连接抽象封装 相当于连接“代言人”--所有连接相关的操作（读数据  写数据  关闭连接 绑定端口等）都通过channel完成
     *
     * 把他想象成一个快读运输通道  通道的一端是你的应用（netty程序） 另一端是远端服务（客户端、服务器）
     * 数据必须通过这条通道才能在两端之间传输
     * 通道本身还会记录“运输状态”（是否连接成功  是否关闭）、“运输配置”（比如超时时间、缓冲区大小等）
     *
     * 关键特性：
     * 1.双向性 可读  可写
     * 2.绑定唯一的pipline：每个channel初始化会自动创建一个专属的额pipline（相当于这个通道的“分拣流水线” 包裹必须走流水线处理）
     * 3.生命周期管理：channel有明确的状态流转（如 unregisterted -> registered -> active  -> inactive）
     * 对应“未注册到事件循环” “已注册” “连接活跃” “连接关闭”
     *
     *
     * pipline：channel的事件处理流水线
     * 1.核心定位  pipline是channel绑定的“责任链”  本质是一个双向链表 链表中的每个节点是channelHandler（处理器）
     *
     * 所有通过channel传输的事件（比如读数据 写数据 连接建立，关闭） 都会流经pipline 由链表中的handler依次处理
     *
     * 2.形象比喻：
     * pipline就是通道里面的“分拣流水线” 每个channelHandler是流水线上的一个工位（比如安检工位 贴单工位 打包工位）
     *
     * 发送数据：数据从pipline尾部流入 依次的经过每个出站handler处理（编码、加密、粘包处理）
     *
     * 核心原理：双向链表 + 事件流向
     * pipline的链表结构有两个关键节点：
     *
     * headContext：链表头 是内置的默认入站处理器 负责将底层IO事件（比如操作系统通知有数据可读）触发为pipline的入站事件
     * tailContext：链表尾部 是内置的默认出站处理器  负责处理未被自定义handler处理完的事件（比如入站数据没被处理会被丢弃）
     *
     * 入站事件：  head  --》 tail  处理接收数据 或链接变化等事件
     * 出站事件：tail -?head 处理发送数据或主动操作连接  write close
     *
     *
     *
     * channel 和pipline的关系
     * 1.一对一绑定：每个channel初始化时会创建一个专属pipline 生命周期和channel一致（channel关闭 pipline也失效）
     * 2.事件的桥梁：channel本身不处理数据 只负责接受、发送事件，所有事件都委托给pipline处理
     *     比如调用channel。read() 实际是触发Pipline的入站写事件 从head开始流转
     *     比如调用channel。write()实际是触发pipline的出站写事件 从tail开始流转
     *
     * 3.hadnler上下文：每个handler被添加到pipline时，会创建一个channelHandlerContext 用于handler之间的通信
     *
     *
     *
     * 误区：
     * 1.pipline不是共享的  每个channel的pipline互相不个干扰
     * 2.入站与出站方向是不一样的 注意反编码 解码的顺序  解码要在入栈靠前的位置  编码要在出站靠前的位置
     * 3.channel本身不直接处理数据  channel是载体  数据处理核心是pipline中的handler channel只负责触发事件
     *
     *
     *
     * netty与NIO之间的关系：
     *
     *
     *
     * serverSocketChannel   --》 nioServerSocketChannel
     *
     * SocketChannel     -->  nioSocketChannel
     *
     *
     * selector    --》 封装在eventloop中   netty自动管理selector的创建 事件轮训  无需手动调用select（）
     *
     * 事件处理：nio手工遍历selectionkey netty通过 pipline+ channelHandler   用责任链模式 替代原生的手动判断事件类型 解耦解码 业务 编码逻辑
     *
     * 线程模型：手动设计线程池   netty eventloopgroup自动管理线程池  事件分发   封装了线程+ selector + 队列 保证一个eventloop对应一个线程 避免并发问题
     *
     *
     * 总结：netty把jdk nio中 手动操作selector 遍历事件 处理数据的繁琐工作 全部封装在channel pipline eventloop中
     *
     *
     * 核心问题：事件是怎么注册到多路复用器的：
     *
     * epoll模型下  netty时间注册本质是 将nettychannel对应的jdk通道（socketchannel serverSocketChannel）注册到eventloop管理的selector上
     * 全程由netty自动完成 无需手动干预
     *
     * 注册流程拆解：
     * 1.初始化eventloopgroup，epoll模型下 其实是epolleventloopgroup netty会根据os自动适配
     *
     *
     * eventloopgroup 是eventloop的线程池  每个eventlopp对应一个独立线程
     *
     * 每个eventloop初始化时，会创建一个selector（epoll模型下是EpollSelectorImpl） 本质是通过JNI调用linux的epoll_create（）系统调用
     * 创建一个epoll实例（用于监听文件描述符的事件）
     *
     *
     * 2.绑定端口：nioserversocketchannel.bind()  绑定端口时 netty会做两件关键字：
     *    （1）创建NioServerSocketChannel对象  内部会包装一个ServerSocketChannel.open()（jdk通道）并初始化pipline（defaultChannelPipline）
     *    (2)将NioServerSocketChannel注册到bossgroup上的某个eventloop上
     *       1.调用channe.register() 最终会触发eventlopp的selector.register(jdkchannel)
     *       2.底层通过jdk的selector.register（）方法 将channel对应的文件描述符注册到epoll实例中 监听accept事件
     *       3.注册成功之后 会生成一个selectionkey 绑定通道 + selector + 事件类型 epoll会监听该通道对应的文件描述符（FD）
     *
     * 3.客户端连接：niosocketchannel注册（读写事件）
     *   当客户端发起连接 bossgroup的eventlopp轮询到accept事件之后
     *       （1）NiosServerSocketChannel的pipline会触发channelread事件 处理的连接请求 创建一个新的NioSocketChannel对象（包装jdk的socketchannel）
     *       （2）netty将这个新的NioSocketChannel注册到workergroup的某个eventloop上 并注册read事件 监听客户端发送数据
     *       （3）底层同样是socketchannel.register(selector read事件） 将客户端连接对应的文件描述符注册到epoll实例中 epoll实例开始监听该连接对应的FD读事件
     *
     *  结论：时间注册的自动性  开发者无需关注事件的注册等一系列操作
     *
     *  epoll模型下：事件的住、读写与channel、pipline的关联关系
     *
     *  这是netty事件驱动模型的核心：epoll监听到底层IO事件（有数据可写、有数据可读） 会通过eventloop触发channel的对应事件 最终流转到pipline中有channelHandler处理
     *
     *  完整链路：  epoll事件  -》channel  --》pipline  --》业务处理
     *
     *  以客户端发送数据 服务端接受并响应为例 拆解全程：
     *  1.事件触发：epoll监听到底层IO事件
     *    （1）客户端读取数据后：数据会被内核缓冲区接受 内核会通过epoll实例 触发epoll_in事件（可读事件）
     *    （2）workgroup中的eventloop线程正在循环调用selctor.select() 发现有可读事件 会感知到这个事件 返回就绪的selectionkey集合
     *
     *  2.事件分发： eventloop触发channel事件
     *     eventloop遍历就绪的selectionkey 判断事件类型
     *     通过selectionkey关联到对应的NIOSocketChannel对象 调用channel的unsafe.read()方法(Unsafe是channel的底层不安全操作接口 只能由netty内部调用 负责事件读写)
     *     unsafe.read()方法从jdk的socketchannel读取字节到netty的ByteBuf中 然后触发channel的入栈事件（channelread）
     *
     *  3.事件流转：pipline责任链机制
     *    channel的channelread事件会直接委托其绑定的pipline 从pipline的头部headcontext开始 依次将入栈事件传递给每个channelhandler处理
     *
     *    业务处理完成之后 调用ctx.writeAndFlush()发送响应数据 触发出站事件
     *
     *
     *  4.出站事件   pipline  --》epoll  --》发送数据
     *   ctx.writeAndFlush()会触发pipline的出站写事件 从tailcontext开始 依次经过每个出站handler处理（编码、加密等）
     *   最终流转到tailcontext 由tailcontext调用channel.unsafe.write()方法将数据写入到底层的jdk socketchannel中
     *   底层通过jdk的socketchannel.write()方法 将数据写入内核缓冲区 底层会利用内核的epoll的多路复用 确保高效发送
     *
     *
     *   5.事件收尾
     *     1.入站事件流转到tailcontext 如果没有被自定义handler消费 会默认丢弃（避免内存泄漏）
     *     2.出站事件流转到headcontext时 会完成实际的IO操作 事件结束
     *
     *
     *
     *
     *  netty中niosocketchannel与pipline绑定过程 + pipline中handler的顺序规则：
     *    结论：
     *    1.绑定过程 socketchannel初始化时会创建一个默认的pipline（defaultchannelpipeline） 二者是一对一绑定关系 生命周期完全一致（channel关闭 pipline失效）
     *    2.handler顺序：严格遵循【添加顺序】 且时间流转时区分【入站inbuund】【出站outbound】方向--入站事件按照添加顺序执行 出站按添加顺序反方向执行
     *
     *
     *  补充一个知识点：对于固定容量的bytebuf 遇到“半包+ 数据包到达”的场景 核心结论：
     *  固定容量bytebuf本身不会自动扩容 也不会主动复制数据 -必须由用户手动处理“半包积累” 否则会丢书数据或者抛出异常；
     *  手动处理的核心是“用新的可扩容缓冲区承接累积数据” 这个过程会涉及到数据复制（用户手动触发或netty间接完成）
     *  因为固定容量bytebuf设计初衷是容量不可变 netty不会为其突破容量限制（否则违背“固定容量”的语义） 所以 所有半包处理逻辑都需要用户显式编码实现
     *
     *  协议规定一个完整数据包是 10 字节；
     * 你创建了一个固定容量 6 字节的 ByteBuf（fixedBuf = Unpooled.fixedBuffer(6)）；
     * 第一批数据到达：5 字节（半包，不足以构成完整包），写入 fixedBuf 后，fixedBuf 还剩 1 字节空闲；
     * 第二批数据到达：7 字节（新数据），尝试写入 fixedBuf 时，发现剩余空间（1 字节）不足以容纳 7 字节 —— 此时固定容量 ByteBuf 不会扩容，
     * 直接抛出 IndexOutOfBoundsException（写越界），或因未处理导致新数据丢失。
     *
     *
     * 问题：既然定长的bytebuf有长度限制  为什么接受的时候不会抛出异常？
     * 一、先明确：msg的bytebuf的长度由谁决定
     *       Handler中channelread收到msg(bytebuf) 其长度是否固定 完全由netty的【接收缓冲区配置】决定 与handler类型无关
     *         1.默认情况（无特殊配置）：netty会使用【可扩容的池化bytebuf】作为接收缓冲区（由ChannelOption.ALLOCATOR）控制 默认是PolledByteBufAllocator
     *           此时msg是可扩容的 长度等于“本次从网络读取的字节数”（可能似乎1字节、100字节 取决于底层操作系统的TCP缓冲区数量） 不是固定长度
     *         2.手动配置固定长度接收缓冲区：如果通过ChannelOption.SO_RCVBUF配置了固定长度的bytebuf作为接收缓冲区 msg长度是固定的
     *          实例：ch.config.setRecvBufAllocator(new FixedRecvByteBufAllocator(16));->每次接受缓冲区固定10字节 msg长度最多十字节（不足时为实际读取长度）
     *
     *
     *      简单说：msg是否为固定长度 是“接收缓冲区的配置项” 不是handler决定的
     *
     *  二：固定长度接收buf+半包处理   需要手动控制半包累计逻辑 尤其是使用自定的固定长度bytebuf作为接收缓冲区时
     *
     *
     * 疑问：当socket接受的数据包（tcp数据流中的一段）超过【固定长度的bytebuf】的容量时？会不会丢数据、越界？
     *
     *    如果是netty配置的固定长度接收缓冲区（FixedRecvByteBufAllocator）：netty会自动做分片读取  把大数据包拆分成多批 每批刚好填满（或者不超过）
     *    固定长度buf 依次传递给handler 不会越界 不会丢数据 如果是手动创建的固定长度bytebuf 需要用户手动处理半包累计逻辑
     *
     *    （一）tcp是流式协议 没有数据包概念
     *     首先要明确：tcp传输的是字节流 不是一个个独立的数据包  数据包的本职是的netty从操作系统tcp缓冲区中读取的一段字节（长度由netty配置决定）
     *     例子：发送一个30字节的大数据包 tcp可能会拆分成10字节 12字节 8字节三批发送 也可能一次性发送30字节 但对接收端来说 netty只能一批批读取
     *     每批的长度由【接收缓冲区容量】决定
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
     *
     *
     * @throws InterruptedException
     * @throws IOException
     */
    @Test
    public void clientMode() throws InterruptedException, IOException {
        NioEventLoopGroup thread = new NioEventLoopGroup(1);
        // 客户端
        NioSocketChannel client = new NioSocketChannel();
        // 注册监听事件
        thread.register(client);

        ChannelPipeline pipeline = client.pipeline();
        pipeline.addLast(new MyInHandler());

        ChannelFuture connectFuture = client.connect(new InetSocketAddress("localhost", 8080));
        ChannelFuture sync = connectFuture.sync();
        ByteBuf byteBuf = Unpooled.copiedBuffer("hello world".getBytes());
        // 写数据 发送包裹：数据会进入channel绑定的pipline处理后发送
        ChannelFuture send = client.writeAndFlush(byteBuf);

        send.sync();

        sync.channel().closeFuture().sync();

        System.in.read();
    }

    /**
     * 基于netty的客户端模式
     *
     * @throws InterruptedException
     */
    @Test
    public void nettyClient() throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        Bootstrap bootstrap = new Bootstrap();
        ChannelFuture connect = bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new MyInHandler());
                    }
                }).connect(new InetSocketAddress("localhost", 8080));
        // 获取连接
        Channel client = connect.sync().channel();
        ByteBuf byteBuf = Unpooled.copiedBuffer("hello server".getBytes());
        ChannelFuture send = client.writeAndFlush(byteBuf);
        send.sync();
        client.closeFuture().sync();

    }

    @Test
    public void serverMode() throws InterruptedException {
        // 服务端测试
        NioEventLoopGroup thread = new NioEventLoopGroup(1);
        NioServerSocketChannel server = new NioServerSocketChannel();
        thread.register(server);
        ChannelPipeline pipeline = server.pipeline();
        // 连接注册
        pipeline.addLast(new MyAcceptHandler(thread,new ChannelInit()));
        ChannelFuture bind = server.bind(new InetSocketAddress("localhost", 8080));
        bind.sync()
                .channel()
                .closeFuture()
                .sync();
        System.out.println("server closed");

    }

    @Test
    public void nettyServer() throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        ChannelFuture bind = serverBootstrap.group(group, group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new MyInHandler());
                    }
                }).bind(new InetSocketAddress("localhost", 8080));
        bind.sync().channel().closeFuture().sync();
        System.out.println("server closed");

    }

}


class MyAcceptHandler extends ChannelInboundHandlerAdapter {
    private final io.netty.channel.EventLoopGroup selector;
    private final ChannelHandler handler;

    public MyAcceptHandler(io.netty.channel.EventLoopGroup thread, ChannelHandler myInHandler) {
        this.selector = thread;
        this.handler = myInHandler;  // ChannelInit
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("server registerd...");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //  listen  socket   accept    client
        //  socket           R/W
        SocketChannel client = (SocketChannel) msg;  // accept  我怎么没调用额？
        // 2，响应式的  handler
        ChannelPipeline p = client.pipeline();
        p.addLast(handler);  // 1,client::pipeline[ChannelInit,]
        // 1，注册
        selector.register(client);
    }
}

// 为啥要有一个inithandler，可以没有，但是MyInHandler就得设计成单例
@ChannelHandler.Sharable
class ChannelInit extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        Channel client = ctx.channel();
        ChannelPipeline p = client.pipeline();
        p.addLast(new MyInHandler());// 2,client::pipeline[ChannelInit,MyInHandler]
        ctx.pipeline()
                .remove(this);
        // 3,client::pipeline[MyInHandler]
    }
//    @Override
//    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        System.out.println("haha");
//        super.channelRead(ctx, msg);
//    }
}


/*
就是用户自己实现的，你能说让用户放弃属性的操作吗
@ChannelHandler.Sharable  不应该被强压给coder
 */
class MyInHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client  registed...");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client active...");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
//        CharSequence str = buf.readCharSequence(buf.readableBytes(), CharsetUtil.UTF_8);
        CharSequence str = buf.getCharSequence(0, buf.readableBytes(), CharsetUtil.UTF_8);
        System.out.println(str);
        ctx.writeAndFlush(buf);
    }
}
