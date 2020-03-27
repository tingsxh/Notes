[TOC]

## AQS的内部结构记录

### NODE 节点 介绍

```java
// 这里只是一个标志位而已
  static final Node SHARED = new Node();
  /** Marker to indicate a node is waiting in exclusive mode */
  static final Node EXCLUSIVE = null;
 // 节点处于取消状态，可能由于等待超时会在被中断，需要从同步队列中取消等待 
  static final int CANCELLED =  1;
  // 该状态表示当前节点的后继节点处于等待状态，如果当前接待被取消会或者已经释放了锁需要通知后继节点
  static final int SIGNAL    = -1;
  /** waitStatus value to indicate thread is waiting on condition */
  static final int CONDITION = -2;
  /**
   * waitStatus value to indicate the next acquireShared should
   * unconditionally propagate
   */
  static final int PROPAGATE = -3;

/***
* 关于这个字段，在独占模式下，我们只需关注两点 cancelled、signal 还有就是初始化状态0了
*/
  volatile int waitStatus;
// 前置节点
  volatile Node prev;
// 后置
  volatile Node next;
// 当前节点所代表的线程
 volatile Thread thread;
// 
 Node nextWaiter;

 final boolean isShared() {
     return nextWaiter == SHARED;
 }
 final Node predecessor() throws NullPointerException {
     Node p = prev;
     if (p == null)
         throw new NullPointerException();
     else
         return p;
 }
```

### 独占锁的获取

```java
 // 来看下面这个获取独占锁的底层模板方法，就是AQS的子类获取锁的话，都会基于这个方法，final不可重写   
  public final void acquire(int arg) {
          if (!tryAcquire(arg) &&
              acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
              selfInterrupt();
      }
```

* 以上三轮方法执行完成之后，如果被中断了则会执行到 selfInterrupt() 方法

  ```java
  static void selfInterrupt() {
      Thread.currentThread().interrupt();
  }
  //这里只是简单的中断线程，因为当线程被park 挂起响应中断的方式是被唤醒。
  ```

  #### 接下来总体分三步来分析:

  * 先尝试获取独占锁，也就是 tryAcquire方法，这个不是由AQS提供的，是由其子类提供。我们先看下例如ReentrantLock的非公平锁的内部实现内，如下

  ```java
   final boolean nonfairTryAcquire(int acquires) {
      final Thread current = Thread.currentThread();
    	// 获取同步器的状态，AQS提供的
      int c = getState();
      if (c == 0) {
        // 这里也是利用AQS 提供的cas 能力来获取同步锁的
          if (compareAndSetState(0, acquires)) {
              setExclusiveOwnerThread(current);
              return true;
          }
      }
     // 锁的重入
      else if (current == getExclusiveOwnerThread()) {
          int nextc = c + acquires;
          if (nextc < 0) // overflow
              throw new Error("Maximum lock count exceeded");
          setState(nextc);
          return true;
      }
      return false;
  }
  ```

  * 当获取锁失败，也就是tryAcquire() 防护false ,那么久需要加入同步队列进行等待了，从AQS的源码可知，AQS内部维护的等待队列是一个以Node 节点为单元的双向带头节点的链表结构，因为存在pre,next

    接下来需要分析，这个构造队列节点的方法 **addWaiter(Node.EXCLUSIVE)**

  ```java
  /***
  **方法看起来也不复杂，详细分析一下    
  */
  private Node addWaiter(Node mode) {
    // 生成节点对象，此时节点的waitStatus 是空的
      Node node = new Node(Thread.currentThread(), mode);
      // Try the fast path of enq; backup to full enq on failure
    // 判断尾部节点，如果尾部节点不为空的话，则cas 设置尾部节点为 新的node 节点，并返回节点
      Node pred = tail;
      if (pred != null) {
          node.prev = pred;
          if (compareAndSetTail(pred, node)) {
              pred.next = node;
              return node;
          }
      }
    // 不管是尾部节点没有初始化，还是上面设置尾部节点失败了，都会走end() 方法
      enq(node);
      return node;
  }
  // 再分享一下 enq(node) 这个方法
  /**
   * Inserts node into queue, initializing if necessary. See picture above.
   * @param node the node to insert
   * @return node's predecessor
   */
  private Node enq(final Node node) {
      for (;;) {
        //无限循环，如果尾部节点为null，说明没有初始化，则需要初始化头部节点和尾部节点都为当前节点
          Node t = tail;
          if (t == null) { // Must initialize
            // 这里注意，初始化的时候，头结点设置是个kong 节点，然后依次往后追加
              if (compareAndSetHead(new Node()))
                  tail = head;
          } else {
            // 否则，再一次将node 节点设置为新的尾部节点，如果设置成功则返回当前节点的前置节点，否则一直循环
              node.prev = t;
            // 这里可能存在尾分叉问题，
              if (compareAndSetTail(t, node)) {
                  t.next = node;
                  return t;
              }
          }
      }
  }
  ```

  * 经过上述 addWaiter 方法，生成了一个新的节点，进一步看下acquireQueued() 方法做了什么事情

  ```java
  /**
   * Acquires in exclusive uninterruptible mode for thread already in
   * queue. Used by condition wait methods as well as acquire.
   * 注释解释为，以不可中断的方式，来获取排它锁
   * @param node the node
   * @param arg the acquire argument
   * @return {@code true} if interrupted while waiting
   */
  final boolean acquireQueued(final Node node, int arg) {
          boolean failed = true;
          try {
              boolean interrupted = false;
              for (;;) {
               // 获取前置节点，如果前置节点是头节点，并且尝试获取锁成功
               // 因为头结点是成功获取了同步器状态的节点，并且在头节点释放之后会主动唤醒次节点
                  final Node p = node.predecessor();
                  if (p == head && tryAcquire(arg)) {
                    // 那么设置新的头结点
                      setHead(node);
                   // 将原来的头节点断掉
                      p.next = null; // help GC
                      failed = false;
                    // 这里返回false 表示没有被中断
                      return interrupted;
                  }
                // 如果获取锁失败了，那么久需要等待了，具体看下等待是如何进行的
                // 首先从方法名可以看出，是判断该线程在获取锁失败之后是否应该被挂起，如果返回true 则执行后面的挂起操作
                // 如果这里返回false ,那么只会继续循环
                  if (shouldParkAfterFailedAcquire(p, node) &&
                      parkAndCheckInterrupt())
                    // 看到这里，但是这里有一点疑问，如果被中断了，那么接下来还要继续去拿锁吗？，
                    // 原因就在于默认的lock 不响应中断，还是会去拿锁，知道成功，然后返回true 再回到
                      interrupted = true;
              }
          } finally {
              if (failed)
                  cancelAcquire(node);
          }
  }
  
  // 判断是否应该被挂起，这个方法就是一直尝试将 当前节点的前置节点的waitStatus 设置为signal 设置成功了则将线程挂起。为什么要等头结点，因为此时头节点可能已经获取到锁了。
  // 否则继续重试，如果前置节点
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
      		// 查看前置节点的状态
          int ws = pred.waitStatus;
      		// 如果前置节点处于等待唤醒状态
          if (ws == Node.SIGNAL)
              /*
               * This node has already set status asking a release
               * to signal it, so it can safely park.
               */
              return true;
          if (ws > 0) {
              /*
               * Predecessor was cancelled. Skip over predecessors and
               * indicate retry.
               */
            // 如果前置节点已经被取消了，那么需要跳过前置节点，就是将中间已经取消了的节点从队列中剔除
              do {
                  node.prev = pred = pred.prev;
              } while (pred.waitStatus > 0);
              pred.next = node;
          } else {
              /* 这里的0并不是一个状态，是初始的一个值
               * waitStatus must be 0 or PROPAGATE.  Indicate that we
               * need a signal, but don't park yet.  Caller will need to
               * retry to make sure it cannot acquire before parking.
               */
            // 这里继续尝试将当前节点设置为 signal 状态，然后返回false
              compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
          }
          return false;
      }
  // 进行了上一把判断是否允许挂起的操作时候，接下来就是挂起线程的操作了
    private final boolean parkAndCheckInterrupt() {
          LockSupport.park(this);
      	// 最后会返回 是否被中断了，如果是的话返回true,接下来就会执行
          return Thread.interrupted();
      }
  
  ```

* 到此就暂时分析完了，一个lock的基本过程，总结来看就是

  * 1.先直接 tryAcquire 获取锁，如果获取失败，则将当期线程封装成一个waitNode 节点，用cas 的方式追加至队列尾部。追加成功之后。

* 2.再进一步尝试获取锁（只有当判断头当前节点的前置节点是头节点的情况下，才真正尝试拿锁），如果获取失败则继续判断是否允许挂起当前线程，允许的话则直接挂起，等待被唤醒或被中断。

* 接下来分析一下独占锁的释放

### 独占锁的释放

  ```java
    public final boolean release(int arg) {
       if (tryRelease(arg)) {
           Node h = head;
         	// 如果头结点不为空，说明后续有其它节点，因为头结点初始化时有节点入队操作触发的，
         // 状态如果为0 那睡眠此时正好有一个队里正在入队，还没完成挂起的操作
           if (h != null && h.waitStatus != 0)
             // 这里是最为关键的唤醒后置线程的操作
               unparkSuccessor(h);
           return true;
       }
       return false;
   }
  ```

  * 同样的tryRelease 也是由子类去实现的, 我们同样参考一下 ReentrantLock 内部的非公平锁的实现

  ```java
  protected final boolean tryRelease(int releases) {
    // 这里不用关心这个release 传进来的都是1
      int c = getState() - releases;
    // 判断是否是当前线程
      if (Thread.currentThread() != getExclusiveOwnerThread())
          throw new IllegalMonitorStateException();
      boolean free = false;
      if (c == 0) {
          free = true;
        	// 将当前锁持有器置空
          setExclusiveOwnerThread(null);
      }
    	// 修改状态
      setState(c);
      return free;
  }
  ```

  * 唤醒正在等待的后置节点

  ```java
      private void unparkSuccessor(Node node) {
          /*
           * If status is negative (i.e., possibly needing signal) try
           * to clear in anticipation of signalling.  It is OK if this
           * fails or if status is changed by waiting thread.
           */
          int ws = node.waitStatus;
          if (ws < 0)
            	// 将需要释放的节点状态置0
              compareAndSetWaitStatus(node, ws, 0);
          /*
           * Thread to unpark is held in successor, which is normally
           * just the next node.  But if cancelled or apparently null,
           * traverse backwards from tail to find the actual
           * non-cancelled successor.
           * 正常情况下是唤醒下一个节点，如果下一个节点被取消了，那就从尾部便利开始选择一个节点唤醒
           */
          Node s = node.next;
        // 如果下一个节点为空，或者已经取消等待
          if (s == null || s.waitStatus > 0) {
              s = null;
             // 从尾部开始遍历，一直往前走，直到找到空节点才停下来，对应非空但是已经取消了的还是不处理
              for (Node t = tail; t != null && t != node; t = t.prev)
                  if (t.waitStatus <= 0)
                      s = t;
          }
          if (s != null)
            // 唤醒 找到的那个让它去竞争锁
              LockSupport.unpark(s.thread);
      }
  
  ```

  * 以上说的都是对排他锁的一些源码分析，除此之外还有共享锁的获取及释放


### 共享锁的获取

* 相较于排他锁，共享锁的特定在于同一个锁，可以允许多个线程同时持有，在实际中的应用例如 读写锁，信号量等等。排他锁的话内部实现会多了一个  exclusiveOwnerThread  属性，用于记录当前持有锁的线程

  ```java
  // AQS 中获取共享锁的模板方法 
  public final void acquireShared(int arg) {
          if (tryAcquireShared(arg) < 0)
            // 如果获取锁失败，则执行如下
              doAcquireShared(arg);
  }
  // tryAcquireShared 是留给子类去实现的方法，最典型的的是信号量 Semaphore的实现了, 返回大于0表示获取锁成功，小于0表示获取共享锁失败
  final int nonfairTryAcquireShared(int acquires) {
   for (;;) {
       int available = getState();
       int remaining = available - acquires;
       if (remaining < 0 ||
           compareAndSetState(available, remaining))
           return remaining;
   }
  }
  ```

  * 第一次尝试获取共享锁失败后，会执行cas 拿锁操作

  ```java
  private void doAcquireShared(int arg) {
    // 构造节点
  final Node node = addWaiter(Node.SHARED);
  boolean failed = true;
  try {
      boolean interrupted = false;
    // 循环，这里的结构其实和排他锁cas 拿锁很像
  for (;;) {
    // 获取前置节点，只有当前置节点是头节点的时候，才尝试去拿锁
      final Node p = node.predecessor();
      if (p == head) {
          int r = tryAcquireShared(arg);
          if (r >= 0) {
            // 拿锁成功则设置新的头结点，这里是与排他锁直接设置头部节点的不同之处。
              setHeadAndPropagate(node, r);
              p.next = null; // help GC
            // 并且处理中断
              if (interrupted)
                  selfInterrupt();
              failed = false;
              return;
          }
      }
    // 这里就和排他锁是一致的了，判断是否允许中断吗，允许则挂起，等待唤醒
      if (shouldParkAfterFailedAcquire(p, node) &&
          parkAndCheckInterrupt())
          interrupted = true;
  }
  }
  ```

  ```java
      private void setHeadAndPropagate(Node node, int propagate) {
          Node h = head; // Record old head for check below
        // 设置头部节点
          setHead(node);
          if (propagate > 0 || h == null || h.waitStatus < 0 ||
              (h = head) == null || h.waitStatus < 0) {
              Node s = node.next;
              if (s == null || s.isShared())
                // 如果是共享锁，则直接唤醒其他线程来抢锁，理论依据来自于: 既然是共享锁，当头结点获取到锁之后，剩余节点同样具有拿到锁的可能性。
                // 这点也是区别于独占锁很重要的一点
                  doReleaseShared();
          }
      }
  ```

### 共享锁的释放

  * 拿锁之后就是释放操作了，与排他锁一致，这里释放之后同样需要去唤醒排队等待的其他节点

  ```java
public final boolean releaseShared(int arg) {
      if (tryReleaseShared(arg)) {
        // 这里我猜应该是执行具体的唤醒操作，因为真正的释放，在刚才已经完成了
          doReleaseShared();
          return true;
      }
      return false;
  }
  ```

  ```java
 /**   这段头注释的意识大意是 唤醒后继节...*/
      private void doReleaseShared() {
          for (;;) {
            // 对于共享锁而言，有线程释放锁，那就自认意味着，其余的子节点可以获取到锁，
              Node h = head;
             // 这里就限制了，队列里面至少有两个节点
              if (h != null && h != tail) {
                  int ws = h.waitStatus;
                 // 如果当前头结点处于singal ，那么说明需要去唤醒后继节点
                  if (ws == Node.SIGNAL) {
                    // 尝试唤醒，将头结点状态置为0
                      if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                          continue;            // loop to recheck cases
                    // 唤醒后继子节点，使其继续竞争
                      unparkSuccessor(h);
                  }
                // 什么情况下回等于0，答案是 新的节点刚加入，执行了tail=node,但是还没来得及执行 shouldParkAfterFailedAcquire() 这里面的setWaiterStatus 这一步的操作 
                  else if (ws == 0 &&
                           !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                      continue;                // loop on failed CAS
              }
            // 只有当头结点 唤醒完子节点期间，没有其他节点成为头结点
              if (h == head)                   // loop if head changed
                  break;
          }
      }
  // 这里需要注意谁最后拿到锁，谁就是头结点，头结点一定时有锁的
  
  ```

* 值得注意的一点是非公平锁可能会造成线程饥饿的问题，但是非公平锁性能相对更优，所以其被设置为默认实现。

### 同步队列和等待队列

* 等待队列-->同步队列：当调用condition对象的signal 将会唤醒等待队列的头节点并将其移动至同步队列尾巴节点
* 同步队列-->条件队列：当调用condition对象的await 将当前获取锁的线程加入等待队列的尾部，并且唤醒同步队列下一个节点。

### 读写锁的原理

* 读写锁本质上内部是维护了一个继承自AQS的子类，共用其中的一个state变量，其中32位的变量被分割成高16位用于控制 共享模式，和低16位用于控制独占模式。
* 对于独占模式来说，通常就是 0 代表可获取锁，1 代表锁被别人获取了，重入例外
* 而共享模式下，每个线程都可以对 state 进行加减操作

##### 锁降级

* 写锁还未释放的时候，直接获取读锁，这样保证自己的修改不会被其他线程改掉。

## 参考

 [逐行分析AQS源码](https://segmentfault.com/a/1190000016447307)

[AQS 底层]( [https://github.com/CL0610/Java-concurrency/blob/master/09.%E6%B7%B1%E5%85%A5%E7%90%86%E8%A7%A3AbstractQueuedSynchronizer(AQS)/%E6%B7%B1%E5%85%A5%E7%90%86%E8%A7%A3AbstractQueuedSynchronizer(AQS).md](https://github.com/CL0610/Java-concurrency/blob/master/09.深入理解AbstractQueuedSynchronizer(AQS)/深入理解AbstractQueuedSynchronizer(AQS).md) 
