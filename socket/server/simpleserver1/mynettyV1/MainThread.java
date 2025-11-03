package mynettyV1;

/**
 * mynetty
 *
 * 测试类似EventLoopGroup
 *
 * 熟悉EventLoopGroup
 *
 */
public class MainThread {

    /**
     *
     * 正是由于多个selector存在 如果在多线程中处理事件接受 数据处理等操作
     * 当在一个selector中使用多线程处理数据时 可能会导致时间多次调起 导致服务出现死循环
     *
     * 因此倾向于在selector中线性处理事件  多个连接可以注册在同一个selector上 但是在处理事件的时候只能线性处理
     *
     * 这点非常重要 在IO中  多线程的导致的并发问题要尤其小心
     *
     *
     *
     *
     */
    public static void main(String[] args) {
        SelectThreadGroup selectThreadGroup = new SelectThreadGroup(3);
        selectThreadGroup.bind(8080);
    }




}
