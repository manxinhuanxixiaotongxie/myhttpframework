package myrpctest.v1;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * rpc测试的第一个版本
 * 熟悉rpc的基本原理
 * 知道rpc是什么东西 大概得实现流程
 * 后续会在V2版本的实现一个相对现代化一点的rpc框架
 * 初步具备服务路由等功能
 *
 *
 * 1.想象一下dubbo  调用远端方法就像调用本地方法一样
 * 2.能够通信  有入参  有出参 客户端能够发送数据给服务端 服务端也能发送数据给客户端 双向通信
 * 3.动态代理
 * 4.连接池
 *
 *
 *
 *
 */
public class MyRPCTest {

    /**
     * 调用端
     * 客户端只是调用了一个本地方法 只只知道发送的数据包
     * 远端服务不清楚有哪些 服务端回来的数据包也不清楚 另外发送的数据包只有字节数据
     * 服务端无法识别这种数据包
     * 还有一个因素  同一个客户端 可能会存在一个连接被多个服务公用的情形
     * 还有一个就是 服务端应该不止一个 存在一个发现路由的过程
     *
     * 因此需要动态代理 ：找到远端服务列表进行路由、封装数据包（包含很多方面 一方面是要能够区分本地的调用方
     * 另一方面要知道最后建立连接具体服务端的地址（也就是实际建立连接 发生通信的机器）
     * ）、发送数据包、处理服务端回来的数据包
     */
    @Test
    public void consumer() {


    }

    /**
     * 对对象进行代理、增强
     *
     * 使用不同类型代理机制
     *
     * 使得具备客户端的能力
     *
     */
    public static <T> T proxyGet(Class<T> interfaceInfo) {
        // 类加载器
        ClassLoader classLoader = interfaceInfo.getClassLoader();

        Class<?>[] interfaces = new Class<?>[]{interfaceInfo};

        // jdk动态代理 动态创建一个代理对象 让代理对象去拦截目标对象的方法调用（比如增加日志 权限校验 事务控制等）
        /**
         * jdk动态代理参数含义
         * classloader：类加载器 用来加载动态生成的代理类到jvm中
         * 本质：java在任何类都需要通过类加载器加载侯才能使用 而proxy动态生成的代理类是临时的、内存中的 必须制定一个累加器来完成加载
         *
         * 第二个参数:Class<?>[] interfaces 目标对象实现的接口数组
         * 作用：指定代理对象要实现哪些接口  代理对象会完全继承这些接口的所有方法 从而保证代理对象可以被当做对象的接口类型使用（里氏替换原则）
         * 核心逻辑：动态代理的本职是基于接口的代理（jdk代理的限制） 代理对象不会继承目标对象的类 而是通过实现目标对象的接口 达到和目标对象行为一致的效果
         * 注意点：
         *   必须传入目标对象实际实现的接口（不能是目标类本身 也不能是父类接口的超集 子集 要完全匹配目标对象实现的接口）
         *   如果目标对象没有实现任何接口 jdk动态代理会报错 此时需要用cglib代理（基于继承）
         *
         * 第三个参数：InvocationHandler h  方法调用处理器
         * 作用：代理逻辑的核心  所有通过代理对象调用的方法 都会被转发到这个接口的invoke方法中 你可以在invoke里写拦截逻辑（比如前置拦截 后置增强 异常处理等）
         *
         */
        return (T) Proxy.newProxyInstance(classLoader, interfaces, new InvocationHandler() {

            /**
             *
             *
             *
             * proxy 代理对象
             *
             * 代理对象实例
             *
             * 几乎不用 除非你需要在拦截逻辑中标识当前是哪个代理对象
             *
             * 绝对不能在开发过程中调用 method.invoke(proxy, ...) 否则会导致死循环 调用自己
             *
             * method:当前被调用的目标方法
             *   对应 代理对象被调用的那个方法的反射对象 包含了该方法的所有元信息
             *   方法名 参数类型 返回值 注解等
             *
             *
             * Object[] args  当前方法的实际参数数组
             *   存储调用代理对象方法时传入的实际参数 数组元素类型与方法参数类型一致 若方法无参数 则为null
             *
             *
             *  用途：查看、修改方法参数（校验参数合法性 脱敏敏感参数）
             *  把参数传递给目标对象的原始方法（配合method的invoke（）使用）
             *
             *  注意：数组可能为null 避免空指针
             *  若方法参数是基本类型（如int boolean） 数组中会自动装箱为对应的包装类如Integer Boolean
             *
             *
             *
             *
             *
             * @param proxy the proxy instance that the method was invoked on
             *
             * @param method the {@code Method} instance corresponding to
             * the interface method invoked on the proxy instance.  The declaring
             * class of the {@code Method} object will be the interface that
             * the method was declared in, which may be a superinterface of the
             * proxy interface that the proxy class inherits the method through.
             *
             * @param args an array of objects containing the values of the
             * arguments passed in the method invocation on the proxy instance,
             * or {@code null} if interface method takes no arguments.
             * Arguments of primitive types are wrapped in instances of the
             * appropriate primitive wrapper class, such as
             * {@code java.lang.Integer} or {@code java.lang.Boolean}.
             *
             * @return
             * @throws Throwable
             */
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                // 接口名称
                String name = interfaceInfo.getName();
                // 调用的方法名称
                String memthodName = method.getName();
                // 方法的入参类型
                Class<?>[] parameterTypes = method.getParameterTypes();

                MyContent content = new MyContent();
                // 入参信息
                content.setArgs(args);
                content.setMethodName(memthodName);
                content.setName(name);
                // 入参参数类型
                content.setParameterTypes(parameterTypes);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ObjectOutputStream oout = new ObjectOutputStream(out);
                oout.writeObject(content);

                // 消息体
                byte[] msgBody = out.toByteArray();

                MyHeader header = createHeader(msgBody);
                out.reset();

                oout = new ObjectOutputStream(out);
                oout.writeObject(header);

                byte[] msgHeader = out.toByteArray();




                return null;
            }
        });

    }


    /**
     *  封装头部信息
     *
     * @param body
     * @return
     */
    public static MyHeader createHeader(byte[] body) {
        MyHeader myHeader = new MyHeader();
        int size = body.length;
        long requestId = Math.abs(UUID.randomUUID().getLeastSignificantBits());
        int flag = 0x14141414;
        myHeader.setDataLen(size);
        myHeader.setRequestId(requestId);
        myHeader.setFlag(flag);
        return myHeader;
    }

}

interface Car {
    void drive(String msg);
}

interface  Fly {
    void fly(String msg);
}

class  ClientPool {
    // 连接池
    private int poolSize;

    // 伴生锁
    Object[] lock;

    NioSocketChannel[] clients;
    ClientPool(int poolSize) {
        this.poolSize = poolSize;
        clients = new NioSocketChannel[poolSize];
        lock = new Object[poolSize];
        for (int i = 0; i < lock.length; i++) {
            if (lock[i] == null) {
                lock[i] = new Object();
            }
        }
    }
}

/**
 * 连接池
 */
class ClientFactory {
    // 连接池工厂
    int poolSize = 1;
    NioEventLoopGroup clientWorker;
    Random rand = new Random();

    // 单例
    private ClientFactory() {}

    private static final ClientFactory factory;

    static {
        factory = new ClientFactory();
    }

    public ClientFactory getFactory() {
        return factory;
    }

    ConcurrentHashMap<InetSocketAddress,ClientPool> outBoxes = new ConcurrentHashMap<>();

    /**
     * 获取连接
     * @return
     */
    public synchronized NioSocketChannel getClient(InetSocketAddress address) {
        ClientPool clientPool = outBoxes.get(address);
        if (clientPool == null) {
            // 创建连接池
            outBoxes.putIfAbsent(address,new ClientPool(poolSize));
            clientPool = outBoxes.get(address);
        }
        // 拿到连接池子之后 进行连接的处理
        // 获取随机的连接 这里有很多算法可以扩展
        // 扩展的内容是 怎么高效快速拿到连接
        int i = rand.nextInt(poolSize);
        // 连接存在且活跃 直接返回连接
        if (clientPool.clients[i] !=null && clientPool.clients[i].isActive()) {
            return clientPool.clients[i];
        }
        // 创建新连接


    }

    /**
     * 创建新连接
     *
     * @return
     */
    private NioSocketChannel create(InetSocketAddress address) {
        clientWorker = new NioEventLoopGroup(1);
        // 创建新连接
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(clientWorker)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new ClientResponse())
                    }
                })
    }


}

/**
 * 客户端返回连接
 *
 */

class ClientResponse extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;
        // 为什么要这么写
        // 11是一个完整的数据包
        if (byteBuf.readableBytes() > 110) {
            // 每次读取一个固定长度的字节数组
            byte[] bytes = new byte[110];
            // 将数据从缓冲区读取进字节数组
            byteBuf.readBytes(bytes);
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            ObjectInputStream oin = new ObjectInputStream(in);
            MyHeader header = (MyHeader) oin.readObject();
            // 读取客户端数据
            ResponseHandler.runCallBack(header.requestId);
            if (byteBuf.readableBytes() >=header.getDataLen()) {
                byte[] data = new byte[(int) header.getDataLen()];
                byteBuf.readBytes(data);
                ByteArrayInputStream din = new ByteArrayInputStream(data);
                ObjectInputStream doin = new ObjectInputStream(din);
                MyContent content = (MyContent) doin.readObject();
                System.out.println(content.getName());
            }
        }
        super.channelRead(ctx, msg);
    }
}

/**
 * 设置回调信息
 *
 * 为什么需要回调
 *
 */
class ResponseHandler {

    static ConcurrentHashMap<Long,Runnable> mapping = new ConcurrentHashMap<>();


    public static void addCallBack(Long requestId,Runnable runnable) {
        mapping.putIfAbsent(requestId,runnable);
    }

    public static void runCallBack(long requestId) {
        if (mapping.get(requestId) == null) return;
        Runnable runnable = mapping.get(requestId);
        runnable.run();
        removeCallBack(requestId);
    }

    public void removeCallBack(long requestId) {
        mapping.remove(requestId);
    }

}


/**
 * 调用方法信息
 *
 * 调用接口、方法、参数信息
 *
 * 封装消息体 包含调用方以及目标接口信息
 *
 */
class MyContent implements Serializable {
    @Serial
    private static final long serialVersionUID = 8392870076555982731L;
    String name;
    String methodName;
    Class<?>[] parameterTypes;
    Object[] args;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }
}

/**
 * rpc发送的数据包的头部信息
 *
 */
class MyHeader implements Serializable {

    @Serial
    private static final long serialVersionUID = 8793529699353353047L;

    // 可以使用整数的位信息存储很多信息 与服务端约定 某些位信息可以做哪些事情
    int flag;

    /**
     * 这个很关键
     * 为什么需要这个参数
     * 在通信过程中 同一个连接  可能会发送很多数据包
     * 必须要知道数据包是谁发送的 在服务端处理完成回传的时候 需要将这个信息返回给客户端
     * 客户端才能知道原始的数据包是谁发送的 客户端要缓存requestId与方法的映射 找到原始的调用方
     */
    long requestId;

    /**
     * 发送的数据包的大小
     * 服务端拆包进行使用 数据包到达都是根据recv_queue进行接收的
     * 在通信的过程中 数据包的接收都是字节维度 必须要进行解析
     * 才能得到原始的报文
     *  因此数据包必须要进行处理才行  消费端必须与服务端约定数据包解析的形式
     *  这里就是多大的数据包大小进行解析
     *  本实现的  一个完成的数据包由一个头信息 与body信息组成 发送给服务端端
     */
    long dataLen;

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public long getDataLen() {
        return dataLen;
    }

    public void setDataLen(long dataLen) {
        this.dataLen = dataLen;
    }
}
