[TOC]

## KAFKA 笔记

### 基础知识

* kafka 的topic 可以设置多个分区，每个分区可以设置多个副本，但是同一时刻只有leader副本负责消息的写入以及读取，其余的follower副本只是负责保障高可用的特性。
* kafka 依赖于zookeeper 来保障消息的负载均衡以及多个集群节点之间的协调。

### 如何实现负载均衡，高可用？

* kafka 集群在运行时总是会选举一个**Controller** 角色来负责 各个Broker 节点之间的协调关系，出此之外controller 还负责创建topic ，以及确定该topic 下面的分区以及ISR 集群，以及leader的选举操作。
* 所有的broker 节点都会抢着在zk 的/controller 目录下创建znode 不过只有一个能创建成功，其余失败后便会监听该节点，一旦controller 挂了，其余broker 则会立即形成一个新的controller。

### 如何实现高吞吐？

* 搞吞吐时只写入数据与消费数据的速度。kafka 写入消息会采用一种分批写入的方式，即聚集到多少条消息之后，或者等待多少毫秒之后再进行消息的写入。

### Zookeeper 用来干什么的?

* 一个是为Kafka 集群中选举新的controller 提供帮助，保障集群的高可用，易扩展。
* 还有就是用于存放 topic,分区信息，以及topic 下的ISR 列表等等。

### Producer 设计

* topic 分区的选择，client 端会缓存着每个topic的分区信息，及每个分区所再broker 节点信息 这个工作交给 partitioner 进行处理。
* 再接着会将消息追加到batch 缓冲队列中，此时send 方法就返回了，说明消息的发送时异步完成的。
* 最后再时sender 线程，会一种轮询缓冲区，将以及就绪的batch 消息取出再根据不同分区leader 的broker 进行分组分别通过之前建立好的socker 链接发送给broker端。
* 发送完成之后，会等待服务器的response 返回，sender 工作线程收到返回之后将会依次触发client 端的回调方法，是按照消息发送的顺序来处理的。

### Consumer 设计

#### Consumer Group 

* 可以监听单个topic, 此时group 下的所有consumer 默认将会均分该topic 下所有的分区消息的订阅
* 同样也可以使用正则监听多个topic，此时组内的所有consumer 同样也可以以默认均分的方式订阅所有分区消息
* 在同一个Group 下面，可以保证同一个分区的消费顺序，因为一个分区只会被一个consumer 消费。

#### coordinator

* 负责管理一个消费组下的所有consumer 的位移位移提交，以及分区策略的重新分配。

#### poll 原理

* Kafka 消费组端总体的设计是单线程的，即右主干线程来执行poll 操作，另外还有一个辅助线程来执行心跳上报的操作。
* poll 方法默认是阻塞着的，可能合conusmer 每次获取的消息大小有关系，当然也是可以设置超时时间的。
* 在poll 的时候consumer 会将自身的offset 提交至 broker, offset 代表着当前消费者的读取记录，也就是本地位移。

#### 位移提交

* kafka 消费组的消费位移式保留在客户端的，kafka 每次poll 了相应的消息记录之后，本地维护着的offset 将会改变，并且这个offset 需要上报给broker 端，这个broker 值的就是`coordinator` 目的是为了防止 consumer 崩溃之后，相应分区的消息出现重复消费的情况。
* 位移的提交可以选择手动提交，也可以选择自动提交，自动提交时默认多少秒会将本地的offset 提交至broker.

#### 消费组内平衡(reblanace)

* reblanace 的含义主要是应带group 内部的变化，比如某个consumer 挂了，或者新加入了consumer,或者topic 的分区发生了变化，都会重新触发reblanace。
* 组内的分区分配策略其实是右消费组内的一个consumer Leader 实现的，再交由coordinator 分发给组内其他的consumer 消费者。

#### Generation

* 该值可以代表reblanace的次数，每次重新均衡一次之后该值都会加1，主要作用是防止无效的offset提交。

#### 提交语义

##### producer 端

* 生产者的提交语义是至少一次，因为producer 在发送消息至broker 时，如果由于网络原因未接收到响应，则会触发重发机制，这会使得同一消息被发送两次。
* producer端 通过引入消息序列号的形式来保障消息发送的幂等性，原理是在每个broker端都维护来一个Map key 是producer的PID+ 分区号，value 则是该分区上消息序列的最大值，如果发现写入的消息的序列号小于该值，则直接拒绝写入。

##### consumer 端

* consumer 的消费时取决于consumer 自身提交的offset 参数的，如果consumer 在poll 取了消息之后立即提交自身的offset ,那么这样就能保证消息**最多被消费一次**，有可能不被消费掉。
* 如果consumer 在每次处理完一次消息之后都立即提交一次，则可以保证**至少被消费一次的语义**
* 至于**精确控制只消费一次**

### Broker 设计

#### Controller 设计

* 负责创建topic, topic 分区leader的选举，topic 分区的重新分配
* 负责新broker 集群的加入，以及退出。

#### partition

* partition 分为leader 和follower，leader负责接收消息日志的写入及读取，follower 会定时的从leader同步消息日志。
* 对于每个分区对象，都存在两个重要的属性值，LEO 和 HW 值，LEO 代表着最新一条消息需要写入的位置，HW 代表的是最后一条已经完全备份的消息地址。而消费者最多只能消费到HW地址为止。

#### ISR 设计

* ISR 里面报存的是一个分区下，所有已经保持同步了副本集群，一旦某个分区副本落后了便会被踢出该集群。

#### LEO 

* LEO 属性即代表下一条消息应该写入的位置，kafka 内部维护了两套，**一个是维护在follower 机器上的，还有一个是维护在leader 机器上的，后者称之为Remote LEO**。那么对应这两套LEO 的更新机制也是不一样的。
* follower 机器上的LEO 是在follower 发起fetch 更新操作的时候，当读取到一条备份消息的时候，在写入本地log 文件的时候会同时更新LEO 值。
* Remote LEO 的更新机制发生在 leader 机器接收到 fetch 请求之后，在回答fetch请求之前，会首先更新Remote LEO 的值。

##### 为什么维护两套？

* 因为第一套是便于follower 机器自身更新HW ，而第二套则是为了Leader 机器更新自身的HW 值，总的来说HW 值将是所有副本LEO中的最小值。

#### HW

* hw 值是对消费者可见的，leader 和follower 的跟新机制也是不一样的。

* **follower 更新**

  更新hw 的机制是 在执行fetch request 之后，更新了leo 之后，会将fetch 请求返回的 leader的hw 值与当前的机器上的 leo 值做比较，取一个最小值作为hw 值。

* **leader 的更新**

  leader 更新发生在 leader 接收到新消息的写入的时候，或者leader 接收到 fetch 请求的时候，其实就是将HW 值设置为ISR 副本集中最小的LEO 值。

#### 分区日志的备份原理

##### 水印备份:

*  指的就是基于HW 的更新机制，详细过程如下：

1. 当producer 向leader 写入一条消息，leader 首先会写入log文件，然后更新leader 本身的LEO 值为1.
2. 当follower 发起fetch 请求的时候，leader 解析请求的参数，发现follower 的LEO 是0，所以leader 会更新其本地的Remote LEO 值为0.
3. 尝试更新本地的HW 值，取Remote LEO 中最小的值 0 和当前LEO之间的最小值，更新HW =0.
4. 再将本地的消息读取出来，再带上本地的HW 发送给follower .
5. follower 接收消息之后，写入本地日志文件，再更新LEO 值.
6. 尝试更新 follower的HW 值，取leader 的HW 值与当前LEO 之间的最小值 HW=0 

* 此时leader 和副本都已经完成了消息的写入，但是此时HW 值还是0，所以leader 的HW值真正更新是在第二轮fetch 操作的时候完成的。
* 因为第二轮fetch 的时候，请求中的参数已经是 1，所以leader 本地的Remote LEO 将会变为1.

##### 缺陷：

* 因为follower 的HW 值是在第二轮fetch 的时候更新的，到这一步出现异常的情况下会出现数据丢失的情况。

* leader epoch:

#### 日志的格式

* kafka 的消息都是以日志的形式存储的，关于kafka有三种日志，xxx.log, xxx.index, xxx.timeindex。

#### 日志的清理

* 日志的清理分为日志段的清理，以及日志的压缩。前者是指会定期删除某些日志分区文件，日志压缩是指 只保留指定key 的最新的值，这点类似于redis 持久化的文件压缩方式。









