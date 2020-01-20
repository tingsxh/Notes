### 前言

* 大致了解AQS之后，知道它是以模版方法的形式，提供了同步器状态的底层管理，以及同步队列的排队策略等等
* 对外呈现的是锁的形式，对内其实是同步器的操作。AQS 提供了诸如状态获取，状态cas 等等操作。例如实现一个简单的锁就如下：

```java
  // 自定义的锁
    private class Sync extends AbstractQueuedSynchronizer{
        @Override
        protected boolean tryAcquire(int arg) {
          // 理由AQS实现的cas 获取锁操作 
            if (compareAndSetState(0,arg)){
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }
        @Override
        protected boolean tryRelease(int arg) {
            if(getState()==0){
                throw new RuntimeException();
            }
            if(!getExclusiveOwnerThread().equals(Thread.currentThread())){
                throw new RuntimeException();
            }
            setState(0);
            setExclusiveOwnerThread(null);
            return true;
        }
        @Override
        protected boolean isHeldExclusively() {
            return getState()==1&&getExclusiveOwnerThread().equals(Thread.currentThread());
        }
    }
```

* 那么AQS我想绝对不会如此简单，应该去了解下底层的实现。比如排队？队列的结构？非公平与公平锁等等操作。