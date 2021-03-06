

[TOC]

### 指令重排序

* 重排序分为编译器层面的重排序优化和处理器的重排序优化。
* JMM的处理器重排序规则会要 求Java编译器在生成指令序列时，插入特定类型的内存屏障（Memory Barriers，Intel称之为 Memory Fence）指令，通过内存屏障指令来禁止特定类型的处理器重排序。

####　依赖性

* 编译器和处理器会对指令序列做重排序，前提是在单个线程中，两条指令序列不存在数据依赖关系。

#### as-if-serial

* 指的是不管怎么排序，执行结果都一样。
* 在单线程程序中，对存在控制依赖的操作重排序，不会改变执行结果（这也是as-if-serial
  语义允许对存在控制依赖的操作做重排序的原因）；但在多线程程序中，对存在控制依赖的操
  作重排序，可能会改变程序的执行结果。

```java
public void test(){
   
    int a=2;
    if(flag){
        int i=a*a;// 这里会发生重排序，将操作提取到if 语句的外部
    }
}
```

####　happens-before

* 在JMM中如果一个操作的执行结果需要对另外一个操作可见的话，那么这个两个操作之间必须要存在happens-before的关系。
* 在一个线程内，书写在前面的控制流先行发生于书写在后面的。
* 对一个对象加锁之前需要先进行解锁。
* **volatile 对象的写操作先行发生于 对象的读操作**。
* 线程的start() 方法先行发生于该线程的任何操作。
* 先行发生原则的传递性，A 先于B B先于C 那么A 同样先于C。

#### 顺序一致性

* 顺序一致性内存模型是一个理论参考模型，在设计的时候，处理器的内存模型和编程语
  言的内存模型都会以顺序一致性内存模型作为参照

**顺序一致性模型的特性**

1. 一个线程中所有操作按程序顺序执行

2. 所有线程都只能看到一个单一的操作顺序，级每个线程的操作必须是原子的，且立刻对所有线程可见。

   实际上是通过，将多线程操作串行化，轮流对主内存执行读写操作来完成的。

3. JMM 只保证正确同步的程序执行语义和其在顺序一致性模型下执行语义一致。

**两者模型差异**

1. JMM 不保证单线程内所有操作顺序执行
2. JMM 不保证所有线程都能看到一致的执行顺序

### volatile 语义

* 一个是只能保障可见性，多线程之间共享变量时，能保证线程看到的永远是最新的。
* 禁止指令重排序优化，当一个变量被声明为volatile 修饰时，对该变量的赋值不能发生重排序，也就是必须得按照代码上的顺序来执行。
* 原子性，对volatile 变量的读写都具有原子性。

#### volatile 写-读内存语义

* volatile 的**写-读** 和 锁的 **释放获取** 具有相同的语义，都是先行发生关系。
* 线程A写一个volatile 变量，随后线程B读取这个变量(**立即可见**)，这个过程实质上是线程A通过主内存向线程B发送消息。

#### volatile 写-读内存的实现

* 当第二个操作是volatile 写时不允许重排序。**写前插入StoreStore, 写后插入StoreLoad**
* 当第一个操作是volatile 读时不允许重排序。 **读后插入 LoadLoad LoadStore**
* 当第一个操作是volatile写时，第二个操作是volatile读时，不能重排序。
* 为了实现volatile的内存语义，编译器在生成字节码时，会在指令序列中插入内存屏障来
  禁止特定类型的处理器重排。

### 锁的内存语义及其实现

* CAS 操作同时具有volatile 写与读的双重内存语义。

###　Final 域内存语义及其实现

1. **final 域的写入规则**

   在构造函数内对一个final 域的写入，与随后把这个构造对象的引用赋值给一个引用变量，这两个操作之间不能重排序。（也就是说不能将一个还未初始化完成的final 引用暴露出去，这违反了final 不可变的特性）

2. **final 域的读取规则**

   初次读一个包含final域调度对象引用，于随后初次读这个final 域，不能重排序，读final域的重排序规则可以确保：在读一个对象的final域之前，一定会先读包含这个final 域的对象的引用。在这个示例程序中，如果该引用不为null，那么引用对象的final域一定已经 被A线程初始化过了。

### JMM内存模型可见性保障

1. 单线程程序，不会出现内存可见性问题，程序的执行结果与改程序在顺序一致性模型中的执行结果相同。
2. 正确同步的多线程中，正确同步的多线程程序的执行将具有顺序一致性（与顺序一致性模型一致）



