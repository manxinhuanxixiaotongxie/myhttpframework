package simple.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * 多路复用器单线程版本测试
 *
 */
public class SocketMultiplexingSingleThreadV1 {

    private ServerSocketChannel serverSocketChannel = null;

    // 多路复用器抽象
    private Selector selector = null;

    /**
     * 监听端口
     *
     * @param port
     */
    public void listen(int port) throws IOException {
        // 绑定端口
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(port));


        // 上述的行为其实与多路复用器无关
        // 建立连接的过程是一致的
        // selector是抽象层 对应可以是poll select kquue epoll
        selector = Selector.open();

        // 注册accept 在poll、select、epoll有较大差异
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.printf("SocketMultiplexingSignleThreadV1 listen on port %d\n", port);
        while (true) {
            this.accept();
        }
    }

    /**
     * 多路复用器下的accept行为
     *
     * @throws IOException
     */
    public void accept() throws IOException {
        // 已经可以进行读取事件 对连接进行处理
        while (selector.select() > 0) {
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    // 连接
                    acceptHandler(key);
                } else if (key.isReadable()) {
                    // 可读
                    readHandler(key);
                } else if (key.isWritable()) {
                    // 写事件
                     writeHandler(key);
                }
            }
        }
    }

    /**
     * 读处理
     *
     *
     * 数据包到达之后：
     * 两次中断   硬件中断 + 软件中断
     *
     * 补充一个网卡中断作为之前linux内核与计算机组成原理的补充：
     *
     * 当网络IO中断（如网卡接收到数据包）发生时，内核 cpu会经历一系列寄存器和程序计数器（PC）数据的保存与恢复流程
     * 以确保中断处理完成侯能回到中断前的正常执行流程
     * 详细流程：
     *
     * 1.中断触发：硬件到内核的切换
     * 当网络接收到数据之后，会通过硬件中断信号（如pcle中断信号）通知cpu 此时cpu会立即暂停当前正在执行的用户态或内核程序 进入中断处理流程：
     *   1.硬件自动保护现场
     *       cpu硬件自动完成以下操作（无需软件干预）
     *       1.将当前程序计数器（PC）的值（即下一条要执行的指令地址）保存到栈中（内核栈或中断栈 根据具体架构会有部分差异）
     *       2.将状态寄存器（如x86的EFLAGS、ARM的CPSR）的值压栈 记录当前cpu的运行状态（如特权级、中断屏蔽位等）
     *       3.将通用寄存器（如x86的eax、ebx  ARM的r0-r15）的值压栈 保存当前程序执行上下文
     *   2.切换到内核态
     *    cpu自动将特权级切换到内核态 并从中断向量表中查找对应的中断服务程序入口地址 将其加载在PC中（中断向量表是内核初始化时设置的 每个中断信号对应一个处理函数地址）
     *
     * 2.内核中断服务程序的处理
     *      内核的中断服务程序开始执行 进一步保存现场并处理中断时间
     *      1.软件层面保存更多上下文
     *       中断服务程序的第一条指令通常是保存硬件未自动保存的寄存器（如段寄存器、浮点寄存器等）到内核栈 确保完整保存当前执行状态
     *
     *       例如： x86架构中 内核会执行pusha指令将所有通用寄存器再次压栈（尽管硬件可能已经部分保存 双重保险）
     *
     *      2.处理网络数据
     *       网络中断ISR主要完成：
     *       1.通知网卡已收到中断 避免网卡重复发送中断
     *       2.将网卡缓冲区的数据复制到内核缓冲区
     *       3.触发软终端 延迟处理复杂逻辑 以缩短硬中断处理时间
     *      3.恢复现场与返回
     *       中断处理完成后 ISR执行与保存相反的操作
     *        1.从栈中弹出软件保存的寄存器
     *        2.执行iret等指令 硬件自动从栈中恢复PC、状态寄存器和通用寄存器的值
     *        3.cpu切换会中断前的特权级（用户态或内核态） 继续执行中断前的程序
     *
     *
     *  3.关键数据的复制路径
     *      整个过程中 寄存器和PC数据的复制本质是栈操作（压栈、出栈） 流程如下
     *
     *      1.用户态程序执行
     *      2.网卡触发中断
     *      4.硬件自动压栈 PC 状态寄存器 通用寄存器
     *      5.切换到内核态 pc指向ISR入口
     *      6.ISR软件压栈：额外寄存器
     *      7.处理中断（数据从网卡-》内核缓冲区）
     *      8.ISR软件出栈 恢复额外寄存器
     *      9.硬件自动出栈  通用寄存器 状态寄存器 pc
     *      10.回到用户态自动执行
     *
     *   为什么需要这样的复制流程：
     *     1.原子性保障：硬件自动保存关键寄存器 确保中断发生时 现场不丢失 避免程序执行混乱
     *     2.隔离性：中断处理在kernel态执行 与用户态程序严格隔离 通过栈保存、恢复实现上下文切换
     *     3.效率权衡 硬件快速保存核心寄存器 软件补充保存非核心寄存器 平衡中断响应速度和上下文完整性
     *
     *     网络IO中断核心是上下文切换：cpu通过硬件自动+软件的复辅助的方式 将中断前的寄存器、pc等数据保存在栈中 执行中断处理后再从栈中恢复
     *     最终无缝回到原执行流程 这一过程是内核处理异步事件（如网络数据到达）的基础 也是操作系统并发能力的关键
     *
     *
     *
     *   还差一个过程：内核态数据复制到用户态数据的过程：
     *
     *
     *   核心是数据从内核缓冲区到用户缓冲区的拷贝 同时伴随CPU上下文的再次切换
     *   1.数据从内核态到用户态触发条件
     *     数据到达内核缓冲区之后 并不会主动进入用户态 需要用户程序通过主动发起系统调用触发（这点很关键）常见场景有两种
     *       1.阻塞IO模型 用户程序调用read()或者recv()后 进入的阻塞等待 直到内核数据准备好并完成拷贝
     *       2.非阻塞IO/多路复用模型：用户程序通过select()或者epoll_wait()感知到”数据就绪之后“ 再调用read/recv触发拷贝
     *   2.完成流程：从内核缓冲区到用户缓冲区
     *   以epoll模型为例 整个过程分为 系统调用触发 内核数据拷贝 上下文恢复三个阶段 同时伴随cpu寄存器和PC的切换
     *
     *          1.用户态发器rea()系统调用 触发上下文切换
     *           用户程序在感知到数据就绪之后（如从selector拿到op_read事件） 调用read 此时cpu会执行以下操作
     *            1.保存用户态上下文
     *               硬件自动将当前PC（下一条执行的用户态的指令地址）、状态寄存器（如特权级、中断屏蔽位）压入用户栈
     *            2.通用寄存器也会压栈 确保当前用户程序后续能够恢复执行
     *
     *          2.切换到内核态
     *            cpu将特权级用用户态切换到内核态
     *            从系统调用表找到read对应内核处理函数 将其地址加载到PC 开始执行内核代码
     *
     *              2.内核执行数据拷贝（核心步骤）
     *                 内核sys_read函数会完成数据从内核缓冲区到用户缓冲区的关键操作 具体流程如下：
     *                   1，参数校验与缓冲区检查
     *                       1.校验用户输入的fd是否有效 user_buf用户缓冲区地址是否合法 长度是否合理
     *                   2.数据拷贝
     *                     1.内核调用内存拷贝函数 将内核缓冲区中的数据 拷贝到用户程序传入的user_buf中
     *                     2.这里的拷贝是物理内存层面的字节级复制： 内核通过MMU（内存管理单元）将用户态的user_buf虚拟地址映射到物理地址 再直接
     *                     将内核缓冲区的物理地址数据写入该地址
     *                     3.若缓冲区数据不足（如只收到部分数据包） 则拷贝已有全部数据 并返回实际的拷贝的字节数 若数据足够 则拷贝len指定的长度
     *                   3.更新内核状态
     *                     1.拷贝完成后 内核会更新内核缓冲区的指针（如移除已拷贝的数据 腾出空间接收新数据） 同时更新fd的状态（如重置“数据就绪”标记）
     *
     *
     *           3.拷贝完成，恢复用户态上下文
     *             数据拷贝结束之后  内核需要将cpu控制权还给用户程序 流程与进入内核态相反
     *
     *             1.保存内核态上下文
     *                内核会将当前执行的pC 内核寄存器压入内核栈  确保后续再次进入内核能恢复状态
     *             2.恢复用户态上下文
     *               1.从用户栈中弹出之前保存的状态寄存器（恢复用户态特权级） 通用寄存器（恢复程序的变量值）
     *               2.最后弹出PC（恢复到read()调用函数的下一条用户指令地址） cpu切回用户态 继续执行用户程序
     *
     *
     *             3.用户程序接收结果
     *                1.用户程序拿到read的返回值（实际拷贝的字节数） 开始处理user_buf中的数据
     *
     *    3.关键细节：数据拷贝的效率瓶颈与优化
     *         1.传统IO的两次拷贝的问题：
     *           整个网络IO流程 （从网卡到用户程序）默认会经历两次数据拷贝：
     *            第一次：网卡到内存缓冲区（硬件中断完成 DMA直接拷贝 不占用CPU）
     *            第二次：内缓冲区到用户缓冲区（read函数调用时 copy_to_user()完成的  占用cpu）
     *
     *            这两次拷贝是传统IO主要效率瓶颈
     *          2.零拷贝（zero-copy）优化：
     *           为减少拷贝开销 linux提供了sendFile() mmap()等零拷贝机制
     *             mmap() 将内核缓冲区与用户缓冲区做内存映射 数据无需实际拷贝 用户程序直接操作内核缓冲区（但需要处理并发安全）
     *             sendfile()： 数据从内核缓冲区直接发送到网卡（如静态文件传输）  跳过用户态 仅需一次拷贝（内核到网卡）
     *
     *
     *   总结：
     *     数据从内核态到用户态的核心是触发系统调用 + cpu拷贝+上下文切换  用户程序通过read()发起请求  cpu切换到内核态执行copy_to_user（）完成数据拷贝
     *     最后恢复用户上下文 让用户程序处理数据 这一步的效率直接影响IO行难呢过也是的零拷贝等优化技术的核心改进点
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
     */
    public void readHandler(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        buffer.clear();
        int read = 0;
        try {
            // 读取数据
            while (true) {
                read = socketChannel.read(buffer);
                if (read > 0) {
                    // 有数据到达
                    // 开始读取 翻转
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        socketChannel.write(buffer);
                    }
                    buffer.clear();
                } else if (read == 0) {
                    break;
                } else {
                    // 这个地方其实是个bug
                    /**
                     * 事件之前被触发过 会导致监听事件在epoll存在
                     * 但是客户端端突然断开连接 已经没有数据可以读了
                     * 会导致服务端出现空轮训
                     */
                    socketChannel.close();
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }


    }

    /**
     * 新连接进来
     *
     * @param key
     * @throws IOException
     */
    private void acceptHandler(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        // 连接进来 表示连接socket
        /**
         * 在linux环境下 SelectionKey.acceptable() 返回true的本质是：
         * serverSocketChannel绑定的底层TCP套接字（listen socket） 其内核中的等待队列非空
         * -- 也就是有客户端完成了三次握手 连接已建立并等待服务器接受
         */
        SocketChannel accept = serverChannel.accept();
        if (accept == null) {
            // 这里要注意 因为没有新的连接进来 会导致accept为null
        }
        // 这里accept
        accept.configureBlocking(false);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        // 注册
        accept.register(selector, SelectionKey.OP_READ, buffer);
    }

    /**
     * 写处理:
     * 完整过程：  与接收数据过程相反
     * 核心：数据从用户缓冲区写入内核缓冲区 再由内核通过网卡发送到网络 期间同样涉及到用户态内核态切换、数据拷贝以及硬件交互
     *
     * 详细流程：
     * 概览：  用户发送数据的完整路径为：  用户缓冲区  --》内核缓冲区（socket发送缓冲区） --》 网卡缓冲区  --》网络
     * 涉及系统调用write()  send() 内核协议处理 DMA传输 中断通知
     *
     * 详细步骤拆解：
     * 阶段1：用户态准备的数据并发起系统调用
     *        1.用户在应用层将等待发送的数据写入用户缓冲区（java 中的 bytes[]） 然后调用SocketChannel.write()方法或者c中的send()、write()等系统调用
     *        2.触发系统调用 切换到内核态
     *         与接收数据read()类似 write()会触发cpu上下文切换
     *           1.硬件自动保存用户态PC（下一条指令地址）、状态寄存器、通用寄存器到用户栈
     *           2.切换特权级到内核态 从系统调用表找到sys_write对应的内核处理函数地址 加载到PC 开始执行内核代码
     * 阶段2：内核处理发送请求（核心步骤）
     *     1.参数校验与缓冲区检查
     *          1.内核校验文件描述符fd是否为合法的socket 用户缓冲区地址是否有效（避免越界） 发送长度是否合理
     *          2.找到fd对用的socket内核对象 检查器发送缓冲区（sk_send_queue）是否有空闲空间
     *           若缓冲区未满 继续处理
     *           缓冲区已满（对方窗口过小、网络拥塞） 则根据socket类型（阻塞、非阻塞）处理
     *             1.阻塞socket： 挂起进程  等待缓冲区有空间侯环形
     *             2.非阻塞socket 直接返回EAGAIN错误 让用户程序重试
     *      2.数据从用户缓冲区拷贝到内核发送缓冲区
     *        1.内核调用copy_from_user()函数 将用户缓冲区的数据拷贝到socket的内核发送缓冲区（sk_send_queue）
     *        2.这里的拷贝是cpu参与的内存复制（用户态虚拟地址 -->内核态虚拟地址 通过mmu映射到物理地址）
     *
     *      3。协议栈处理（TCP/UDP封装）
     *       内核协议栈对数据进程分层封装
     *        1.用用层数据 -》传输层（tcp会添加首部 包含端口 序号 校验和等  udp仅添加简单首部）
     *        2.传输层数据包 -》网络层（添加ip首部 包含源ip 目的ip 协议类型等）
     *        3.网络层数据包 -》链路层（添加以太
     *        封装完成后 数据包仍保存在内核发送缓冲区中 等待发送
     *
     *  阶段3：内核将数据发送到网卡
     *    1.触发数据发送（软中断或直接调度）
     *       1.数据进入内核发送缓冲区后 内核会通过软中断触发发送流程 或者直接调用网卡驱动处理
     *       2.网卡驱动检查网卡状态（如是否有空闲 是否支持DMA）准备发送
     *    2.DMA传输 内核缓冲区到网卡缓冲区
     *       1.现代网卡支持DMA（直接内存访问）无需CPU参与 直接将内核发送缓冲区的数据（物理地址）拷贝到网卡的硬件缓冲区（如网卡SRAM）
     *       2.驱动程序向网卡写入DMA命令（包含物理地址、长度） 网卡收到命令后启动DMA传输
     *     3.网卡发送数据到网络
     *      1.网卡缓冲区数据满后  网卡硬件按照以太网协议将数据帧发送到物理链路（网线  无线）最终通过路由到达目标主机
     *      2.发送完成后 网卡通过硬中断通知cpu发送完成
     *
     *   阶段4：发送完成后的内核清理与用户态恢复
     *      1.硬中断处理发送完成事件
     *        1。网卡发送完成后 触发中断  cpu中断当前任务 进入中断服务程序（ISR）
     *          硬件自动保存内核态上下文（PC 寄存器等）到内核态
     *         2.ISR通知网卡已经收到发送完成信号 并释放内核缓冲区中与发送的数据
     *       2，更新socket状态与唤醒进程
     *         1.内核更新socket发送缓冲区的空闲空间 并唤醒因“缓冲区满”而阻塞的发送进程（若有）
     *         2.若开启了TCP确认机制 后续会等待对方的ack数据包（通过接受流程处理） 确认数据已被接受
     *       3.恢复用户态上下文
     *        1.内核完成清理后 从用户栈恢复用户态PC 寄存器 程序寄存器 cpu切回用户态
     *        2.用户程序收到write()的返回值（实际发送的字节数） 继续执行后续逻辑
     * 关键差异
     *
     *
     *
     *  优化点：
     *
     *      1.用户态到内核态的拷贝开销：copy_from_user()是cpu密集度型操作 大文件发送可通过sendfile()实现零拷贝（跳过用户态 直接从内核文件缓冲区发送到网卡）
     *      2.内核缓冲区阻塞：非阻塞socket + 多路复用可避免阻塞进程   提高并发效率(通过select epoll感知缓冲区状态)
     *      3.tcp拥塞控制：内核会根据网络状态（如丢包） 动态调整发送窗口 影响实际发送效率 应用层需合理设置发送缓冲区大小
     *
     *
     *  总结：
     *    用户发送数据的核心是   用户数据通过系统调用进入内核  经协议封装后由DMA传输到网卡发送  期间的上下文切换和数据拷贝是性能关键 理解这一流程有助于优化网络程序
     *    （如减少数据拷贝 设置合理的缓冲区 领用零拷拷贝机制）
     *
     *
     *
     *
     *
     *
     * 补充知识点：   sendilfe()   mmap()零拷贝技术
     *
     *
     * sendfile()和mmap()是linux中两种经典的零拷贝技术 核心是减少内核态与用户态之间的数据拷贝次数 甚至是完全避免 来提升IO效率
     * 他们适用场景、实现原理和针对的IO类型有明确区别
     * （一） 零拷贝的目标：减少  用户态-》》内核态的数据拷贝
     *      传统的IO流程中   数据磁盘/网络到用户程序 或者拆欧国用户程序到网络 往往需要多次拷贝
     *         1.用户态-》内核态或者内核态到用户态的拷贝（由cpu执行 开销大 字节级别复制 实际是MMU内存映射管理  参考读取处理说明）
     *         2.内核内部不同缓冲区之间的拷贝（如磁盘缓冲区到socket缓冲区）
     *       零拷贝的技术的核心是省略用户态与内核态之间的cpu拷贝 或者进一步减少内核内部拷贝 从而降低cpu开销和内存带宽占用
     *  （二） mmap() ： 通过内存映射实现用户态直接访问内核缓冲区
     *        1.实现原理：
     *           mmap()(memery map)将内核缓冲区（如的磁盘文件的页缓存、socket接受缓冲区）直接映射到用户进程的虚拟地址空间 用户程序可以
     *           像访问自己的内存一样直接读写内核缓冲区 无需通过read()/write()等系统调用触发数据拷贝
     *
     *           流程对比：
     *              1.传统read() 内核缓冲区  用户缓冲区(cpu拷贝)
     *              2.mmap() 内核缓冲区与用户虚拟地址空建立映射 用户直接操作内核缓冲区（无拷贝）
     *        2.适用场景
     *           1.主要用于读操作（如读取大文件 接受网络数据） 但是也可以用于写操作（需注意并发安全）
     *           典型场景：
     *              1.并发风险：内核缓冲区与用户态共享 若内核修改烟冲去（磁盘文件比其他进程写入） 用户程序可能读到不一致数据 需通过锁或信号量同步
     *              2.不适用于频繁小数据读写 mmap()建立映射的开销较高 小数据场景可能得不尝试
     *              3.本质是伪零拷贝 仅仅是省略了用户态与内核态的拷贝 内核内部的拷贝（磁盘到页缓存）仍然存在
     *
     *
     *  （三）sendfile()：直接在内核中完成文件到网络的传输
     *        实现原理：
     *           sendfile()是专门  磁盘文件  网络发送 设计的系统调用 允许内核直接将磁盘文件的页缓存数据传输到socket发送缓冲区 全程不需要经过
     *           用户态 且在支持DMA散射-聚集特性的硬件上 可进一步省略内核内部的拷贝
     *
     *        适用场景：仅用于磁盘文件网络的发送（单向流程）
     *        典型场景：
     *             1.web程序发生静态资源（如nginx发送html文件 图片 视频文件）:无需用户态介入 直接在内核完文件到网络传输 效率很高
     *
     *             2.大文件分发（p2p种子发送 日志文件上传）：避免大量数据在用户态与内核态之间拷贝 节省cpu资源
     *
     *        局限性：
     *          1.单向性：只能从文件发送到网络 无法反向（网络接收写入文件） 也不能用于用户态数据的发送（动态生成的业务数据）
     *          2.依赖硬件支持：要实现完全零拷贝（省略内核拷贝） 需要网卡支持DMA散射-聚集特性（通过SG-DMA直接读取多个内核缓冲区） 否则仍需内核拷贝到网卡缓冲区
     *
     *
     *
     *
     *
     * @param
     * @throws IOException
     */
    public void writeHandler(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        // 处理写事件
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        try {
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            buffer.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
        key.cancel();
    }

    public static void main(String[] args) throws IOException {
        SocketMultiplexingSingleThreadV1 simpleServer = new SocketMultiplexingSingleThreadV1();
        simpleServer.listen(9099);
    }

}
