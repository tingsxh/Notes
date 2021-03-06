[TOC]

## KAFKA 笔记

### 基础知识

* kafka 的topic 可以设置多个分区，每个分区可以设置多个副本，但是同一时刻只有leader副本负责消息的写入以及读取，其余的follower副本只是负责保障高可用的特性。
* kafka 依赖于zookeeper 来保障消息的负载均衡以及多个集群节点之间的协调。

### 如何实现负载均衡，高可用？

* kafka 集群在运行时总是会选举一个**Controller** 角色来负责 各个Broker 节点之间的协调关系，出此之外controller 还负责创建topic ，以及确定该topic 下面的分区以及ISR 集群，以及分区leader的选举操作。
* 所有的broker 节点都会抢着在zk 的/controller 目录下创建znode 不过只有一个能创建成功，其余失败后便会监听该节点，一旦controller 挂了，其余broker 则会立即形成一个新的controller。

### 如何实现高吞吐？

* 高吞吐是指写入数据与消费数据的速度。kafka 写入消息会采用一种分批写入的方式，即聚集到多少条消息之后，或者等待多少毫秒之后再进行消息的写入。

#### 分区

* 每个主题下面会有多个分区，每个分区分布在不同的集群节点上

#### 副本

* 每个分区会有多个副本，副本只负责跟随该分区的leader

#### leader、follower



### Zookeeper 用来干什么的?

* 一个是为Kafka 集群中选举新的controller 提供帮助，保障集群的高可用，易扩展。
* 还有就是用于存放 topic,分区信息，以及topic 下的ISR 列表等等。

```shell
# 在zookeeper 内部维护了如下节点目录
/broker/ids/xxxx 保存了集群中所有的注册信息，包括集群上的所有topic 信息等等
/controller 保存了集群中的领导者的信息，也负责领导者的动态选取
/cluster
```

### Producer 设计

#### 消息的发送（异步）

* 如果没有指定具体的分区的话，partitioner 会根据topic 下的分区信息，以轮询的方式均匀的发送给各个分区。
* 确认了目标分区之后然后客户端需要找到该分区的leader 位于哪一台broker 机器上，leader 的响应也有不同的策略。

1. 分区的选择

   topic 分区的选择，client 端会缓存着每个topic的分区信息，及每个分区所在broker 节点信息 这个工作交给 partitioner 进行处理。

2. 消息的缓冲

   再接着会将消息追加到batch 缓冲队列中，此时send 方法就返回了，说明消息的发送时异步完成的。

3. 消息的发送

   最后再时sender 线程，会一直轮询缓冲区，将已经就绪的batch 消息取出再根据不同分区leader 的broker 进行分组分别通过之前建立好的socker 链接发送给broker端。

4. 消息的回调

   发送完成之后，会等待服务器的response 返回，sender 工作线程收到返回之后将会依次触发client 端的回调方法，是按照消息发送的顺序来处理的。

#### 处理消息丢失

* 可以设置只有当消息已经被正确同步到所有分区副本之后才算发送成功

### Consumer 设计

#### Consumer Group 

* consumer group 存在的意义在于可以保证高可靠性，即使一个消费者挂了，那么也会提高重新均衡将该消费者负责的分区转移到其他消费者上。

* 可以监听单个topic, 此时group 下的所有consumer 默认将会均分该topic 下所有的分区消息的订阅
* 同样也可以使用正则监听多个topic，此时组内的所有consumer 同样也可以以默认均分的方式订阅所有分区消息
* 在同一个Group 下面，可以保证同一个分区的消费顺序，因为一个分区只会被一个consumer 消费，但是一个consumer 可以消费多个分区。

#### coordinator

* 负责管理一个消费组下的所有consumer 的位移位移提交，以及分区策略的重新分配。

#### poll 原理

* Kafka 消费组端总体的设计是单线程的，即用主干线程来执行poll 操作，另外还有一个辅助线程来执行心跳上报的操作。
* poll 方法默认是阻塞着的，可能合conusmer 每次获取的消息大小有关系，当然也是可以设置超时时间的。
* 在poll 的时候consumer 会将自身的offset 提交至 broker, offset 代表着当前消费者的读取记录，也就是本地位移。
* 官方推荐的做法是，poll 阻塞式的拉取消息，阻塞时间设置为小间隔，这样还可以定时处理其他业务。

#### 位移提交

* kafka 消费组的消费位移式保留在客户端的，kafka 每次poll 了相应的消息记录之后，本地维护着的offset 将会改变，并且这个offset 需要上报给broker 端，这个broker 指的就是`coordinator` 目的是为了防止 consumer 崩溃之后，相应分区的消息出现重复消费的情况。
* 位移的提交可以选择手动提交，也可以选择自动提交，自动提交时默认多少秒会将本地的offset 提交至broker.自动提交是由消费者组完成的
* CommitOffsets() 默认会把该consumer 订阅的所有分区都提交位移，若是调用commitOffsets(Map) 版本，则可以实现更细粒度化的位移提交。
* 新版本的kafka 位移存储位置是在consumer 内部的一个topic 上，_consumer_offsets 

#### 消费组内平衡(reblanace)

* reblanace 的含义主要是应带group 内部的变化，比如某个consumer 挂了，或者新加入了consumer,或者topic 的分区发生了变化，都会重新触发reblanace。
* 组内的分区分配策略其实是右消费组内的一个consumer Leader 实现的，再交由coordinator 分发给组内其他的consumer 消费者。

##### 流程（consumer 负责分配方案制定）

1. 在新版的consumer 中，rebalance 的流程是由coordinator来完成的，coordinator 是一个broker,

2. 寻找 topic _consumer_offset_ 某个分区的leader 副本所在的机器，就是该group的一个coordinator。
3. 加入组的动作，触发重新分配动作之后，coordinator 会选举一个consumer 作为leader ，该leader 负责跟进用户定义的参数重新制定新的分区分配方案上报给coordinator, coordinator 收到之后将会把每个consumer的方案单独返回给每个consumer。
4. 将分区实现下移至consumer 端的好处在于，当分区策略变更之后不需要重启服务端。

#### Generation

* 该值可以代表reblanace的次数，每次重新均衡一次之后该值都会加1，主要作用是防止无效的offset提交。

#### 独立Consumer

* 通过调用Consumer.assign(partitions) 制定consumer 订阅指定的分区



#### 提交语义

##### producer 端

* 生产者的提交语义是至少一次，因为producer 在发送消息至broker 时，如果由于网络原因未接收到响应，则会触发重发机制，这会使得同一消息被发送两次。
* producer端 通过引入消息序列号的形式来保障消息发送的幂等性，原理是在每个broker端都维护来一个Map key 是producer的PID+ 分区号，value 则是该分区上消息序列的最大值，如果发现写入的消息的序列号小于该值，则直接拒绝写入。

##### consumer 端

* consumer 的消费时取决于consumer 自身提交的offset 参数的，如果consumer 在poll 取了消息之后立即提交自身的offset ,那么这样就能保证消息**最多被消费一次**，有可能不被消费掉。
* 如果consumer 在每次处理完一次消息之后都立即提交一次，则可以保证**至少被消费一次的语义**
* 至于**精确控制只消费一次**

### Broker 设计

#### 请求处理逻辑

* kafka Broker 使用的是IO 多路复用的模型来处理客户端的消息发送或者消费请求的，Broker 将多路复用应用在了两个地方。
* 首先Broker 启动的时候会维护一个 **Acceptor** 线程，专门用于接收客户端发起的Socket连接请求，当接收之后会交给**Processor**  线程组来处理，该线程组默认为3个，采用的是轮询的方式来均衡。
* **Processor** 上的线程会 维护一个Selector 来监听该线程所负责的每个Socket 上的请求信息，一旦有消息的话便会将消息发送至 **RequestHandler** 请求队列上面，RequestHandler 会维护一个默认大小为8的线程池来处理这些请求消息。线程处理完消息之后，会将请求响应发送至 **Processor** 所对应的请求响应队列中
* **Processor** 还有一个很重要的作用就是负责实时处理消息的响应返回，通过建立的Socker 返回至Client。
* 三层响应模型：Acceptor 负责接收来接收连接，并交给Processor 去处理， Processor 将其注册在自身的Selector上，一旦有有新的请求数据来，再将该请求的处理发送给RequestHandler 请求队列中处理。

#### Controller 设计

* 负责创建topic, topic 分区leader的选举，topic 分区的重新分配。维护着每台broker上的分区副本和每个分区的Leader副本信息。
* 负责监测新broker 集群的加入后的数据同步，以及崩溃后的重新均衡。

**分区重分配**： 

* 分区重分配的意思是打乱一个topic 下所有分区在不同broker 上的分布，与消费者组内重新平衡是两个概念。

#### partition

* partition 分为leader 和follower，leader负责接收消息日志的写入及读取，follower 会定时的从leader同步消息日志。
* 对于每个分区对象，都存在两个重要的属性值，LEO 和 HW 值，LEO 代表着最新一条消息需要写入的位置，HW 代表的是最后一条已经完全备份的消息地址。而消费者最多只能消费到HW地址为止。

#### ISR 设计

* ISR 里面报存的是一个分区下，所有已经保持同步了副本集群，一旦某个分区副本落后了便会被踢出该集群。
* 在早先的版本中是通过检测副本分区落后了leader 分区多少个位移量之后便将其提出ISR, 后续的改进版本将这个检查优化为检查持续多长时间落后则提出

#### LEO 

* LEO 属性即代表下一条消息应该写入的位置，kafka 内部维护了两套，**一个是维护在follower 机器上的，还有一个是维护在leader 机器上的，后者称之为Remote LEO**。那么对应这两套LEO 的更新机制也是不一样的。
* follower 机器上的LEO 是在follower 发起fetch 更新操作的时候，当读取到一条备份消息的时候，在写入本地log 文件的时候会同时更新LEO 值。
* Remote LEO 的更新机制发生在 **leader 机器**接收到 fetch 请求之后，在回答fetch请求之前，会首先更新Remote LEO 的值。

##### 为什么维护两套？

* 因为第一套是便于follower 机器自身更新HW ，而第二套则是为了Leader 机器更新自身的HW 值，总的来说HW 值将是所有副本（ISR）LEO中的最小值。
* Leader 机器保存了其所有副本的集合的LEO数据，用于更新自身的HW值

#### HW

* hw 值是对消费者可见的，leader 和follower 的跟新机制也是不一样的。

* **follower 更新**

  更新hw 的机制是 在执行fetch request 之后，更新了leo 之后，会将fetch 请求返回的 leader的hw 值与当前的机器上的 leo 值做比较，取一个最小值作为hw 值。

* **leader 的更新**

  leader 更新发生在 leader 接收到新消息的写入的时候更新LEO值，当leader 接收到 fetch 请求的时候，其实就是将HW 值设置为ISR 副本集中最小的LEO 值。

#### 分区日志的备份原理

##### 水印备份:

*  指的就是基于HW 的更新机制，详细过程如下：

1. 当producer 向leader 写入一条消息，leader 首先会写入log文件，然后更新leader 本身的LEO 值为1.
2. 当follower 发起fetch 请求的时候，leader 解析请求的参数，发现follower 的LEO 是0，所以leader 会更新其本地的Remote LEO 值为0.
3. 尝试更新本地的HW 值，取Remote LEO 中最小的值 0 和当前LEO之间的最小值，更新HW =0.
4. 再将本地的消息读取出来，再带上本地的HW 发送给follower .
5. follower 接收消息之后，写入本地日志文件，再更新LEO 值.
6. **尝试更新 follower的HW 值，取leader 的HW 值与当前LEO 之间的最小值 HW=0** 

* 此时leader 和副本都已经完成了消息的写入，但是此时HW 值还是0，所以leader 的HW值真正更新是在第二轮fetch 操作的时候完成的。
* **因为第二轮fetch 的时候，请求中的参数已经是 1，所以leader 本地的Remote LEO 将会变为1.**

##### 缺陷：

* 因为follower 的HW 值是在第二轮fetch 的时候更新的，到这一步出现异常的情况下会出现数据丢失的情况，会导致leader的HW 值落后，而出现数据截断

* leader epoch(不再单纯依赖HW 水印值来标识副本进度)

#### 日志的格式

* kafka 的消息都是以日志的形式存储的，关于kafka有三种日志，xxx.log, xxx.index, xxx.timeindex。

#### 日志的清理

* 日志的清理分为日志段的清理，以及日志的压缩。前者是指会定期删除某些日志分区文件，日志压缩是指 只保留指定key 的最新的值，这点类似于redis 持久化的文件压缩方式。
* 会清理七天前的日志段数据，会清理文件大小大于指定数的文件。
