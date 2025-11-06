package mynettyV2;

import java.util.concurrent.atomic.AtomicInteger;

public class EventLoopGroup {

    AtomicInteger cid = new AtomicInteger(0);
    EventLoop[] childrens = null;
    EventLoopGroup(int nThreads) {
        childrens = new EventLoop[nThreads];
        for (int i = 0; i < nThreads; i++) {
            childrens[i] = new EventLoop("T" + i);
        }
    }

    /**
     * 选择一个EventLoop
     * @return
     */
    public EventLoop chooser() {

        return childrens[cid.getAndIncrement() % childrens.length];

    }
}
