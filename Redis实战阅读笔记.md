[TOC]

## Redis 笔记

### 数据库的结构

- 数据库主要由 `dict` 和 `expires` 两个字典构成，其中 `dict` 保存键值对，而 `expires` 则保存键的过期时间。
- 还有一个id 域，用于切换数据库使用。

#### LIST

* 内部由两种数据结构实现，当长度较短时会采用`zipList` 实现,否则会采用`linkedlist` 实现，

#### MAP

* 内部也可以使用`hashTable` 和`ziplist` 两种承载方式来实现，数据小的时候采用`ziplist`

#### 执行流程

- 服务器通过文件事件无阻塞地 `accept` 客户端连接，并返回一个套接字描述符 `fd` 。
- 服务器为 `fd` 创建一个对应的 `redis.h/redisClient` 结构实例，并将该实例加入到服务器的已连接客户端的链表中。
- **服务器在事件处理器为该 `fd` 关联读文件事件**，服务器通过读事件来处理传入数据，并将数据保存在客户端对应的`redisClient` 结构的查询缓存中。
- 根据客户端查询缓存中的内容，程序从命令表中查找相应命令的实现函数，程序执行命令的实现函数，修改服务器的全局状态 `server` 变量，并将命令的执行结果保存到客户端 `redisClient` 结构的回复缓存中，**然后为该客户端的 `fd` 关联写事件**
- 当客户端 `fd` 的写事件就绪时，将回复缓存中的命令结果传回给客户端。至此，命令执行完毕

#### 跳跃表的实现

### 如何保障数据的持久性

####　自动间隔性保存

* redis 可以配置多少秒的时间内修改过多少次数据则进行BGSAVE 操作
* redis 后台有一个serverCron 定时器，每300毫秒会进行一次扫描，判断是否符合持久化保存的条件，满足则执行BGSAVE。

#### 快照备份(默认实现)（定时触发性）

* 由使用者自行决定，可以设置在n秒内有m个键值被改变了则进行一次数据快照备份。数据备份的具体过程是，主进程，会fork 一个子进程出来，将内存中的数据写入到磁盘的临时文件中，当写完了之后再替换掉原来的快照文件。
* 通过 save 900 1 // 代表 900s内有一个键被改变了，由serverCron 每隔100毫秒会检查一次条件是否满足，如果满足则会fork一个子进程来执行bgsave
* 这种方式由一个缺点就是每次都会全量大覆盖快照文件，而且会出现丢失一部分数据的情况。
* save 命令可以将数据进行磁盘备份写入,save 是阻塞住进程的，bgsave 是非阻塞主进程的。
* RDB 文件模式是记录当前内存中的键值对数据来进行持久化的。

#### AOF 文件模式

* 具体实现逻辑是将每个写的指令都直接写入磁盘文件，在数据库重启的时候按照写的命令重新复原一次数据即可。
* 为了解决操作系统写入的实时性，可以通过如下参数来设置 appendfsync，保证写入数据的实时性。

* 除此之外aof 的问题在于 数据文件会过大，比如 100个 inr自增大命令，就会形成100条指令，但其实可以简化为1条指令。因此redis 还有bgrewriteaof 命令。
* （**AOF文件重写**）**bgrewriteaof** 的实现方式和快照写入但实现方式一致，从主进程fork 出一个子进程，**将内存中的值写入临时文件，再做文件替换。**，不会再去读原来的aof 文件
* 而父进程继续处理 client 请求，除了把写命令写入到原来的 aof 文件中。同时把收到的写命 令缓存起来。这样就能保证如果子进程重写失败的话并不会出问题。
* 子进程写完之后，再通知父进程把缓存的命令写入新的文件中。这样就解决了文件过大的问题

##### AppendFsync 配置

* always: 代表服务器每个事件执行完毕之后都要将aof_buf缓冲区中的所有内容写入到AOF文件，并且同步AOF文件，所以always 的效率是最慢的
* everySec: 服务器在每个事件循环都要将aof_buf缓冲区中的所有内容写入到AOF文件，并且每隔一秒就会进行AOF 文件的同步
* no: 服务器每个事件都会将aof_buf 缓冲区中的内容写入到AOF 文件中，然后由操作系统自己控制AOF 文件的同步。

### Redis 如何实现主从复制

* 只需要在从库的配置文件添加 slaveof xxxx 6379 指明当前从库的主库地址，如果需要密码的话 masterauth 指定主库的密码。
* 主库并不感知从库的存在，复制是从库主动连接主库，链接建立之后就开始了数据的复制，复制期间，主库会把写的指令存储在缓存区间内，复制结束后会把这部分缓存的指令同步给从库，之后主库的每一个写的指令都会主动发送给从库。

#### 数据同步

##### 全量复制(初次)

* 主从复制 需要 依赖  从节点复制偏移地址，主机点ID。全量复制的过程其实把主节点的快照文件返送至从节点，从节点收到之后存入磁盘再载入到内存当中。

##### 复制积压缓冲区

* redis 服务端会维护一个固定大小的积压缓冲区，主要是解决断线重连的从节点如何快速复制的问题。当主节点在进行写命令传播的时候，也会将写命令缓冲至队列里面，特别注意的是，每个命令都会记录它相应的偏移量。

##### 部分复制(断线重连)

* 主节点会维护一个缓存队列区间，主节点在接收写指令的时候也会把命令缓存至队列中。如果从节点中途中断了，则重新链接之后，从节点会发向主节点请求数据同步，并且发送自己的复制偏移量至主服务器，主节点根据自身的偏移量与从节点偏移量做对比，
* 如果从节点的偏移量还在积压缓冲区间，那么就采用部分复制，如果从节点已经不在积压缓冲区的偏移量范围内，那么就需要进行全量复制了。

##### 服务器ID

* 每个节点都维护着自身的服务器id,在服务启动的时候会自动生成，每次从节点进行同步的时候都会保存着主节点的id,当断开重连之后，如果发现主节点变化了，则需要进行全量同步了。

### 事务以及乐观锁实现

#### 事务基本操作 Multi

* 因为redis 是单线程运行的，所以其事务是实现是，将同一事务中的所有操作指令预先存储至队列，当从此连接接收到 exec 命令后，redis 会顺序的执行队列中的所有命令。并将所有命令的运行结果打包到 一起返回给 client.然后此连接就 结束事务上下文。但是redis 是不具有回滚操作的，只能做到给客户端返回整体的执行状态。但是事务的安全性没法保证，其他的连接依然可以修改事务涉及到的数据。

#### 事务的安全性 watch

* 对于事务的安全性控制，redis 可以使用watch 操作来实现，在进行事务之前。但是 [WATCH] 只能在客户端进入事务状态之前执行， 在事务状态下发送 [WATCH](http://redis.readthedocs.org/en/latest/transaction/watch.html#watch) 命令会引发一个错误， 但它不会造成整个事务失败， 也不会修改事务队列中已有的数据（和前面处理 [MULTI]的情况一样）。
* 因为上述原因，redis 的事务只能保证 一致性和隔离性，并不能保证持久性和原子性。

### Redis 的过期机制

* 虽然有那么多种不同单位和不同形式的设置方式， 但是 `expires` 字典的值只保存“以毫秒为单位的过期 UNIX 时间戳”， 这就是说， 通过进行转换， 所有命令的效果最后都和 [PEXPIREAT](http://redis.readthedocs.org/en/latest/key/pexpireat.html#pexpireat) 命令的效果一样。
* redis 默认实现是采用惰性删除结合定期删除的策略进行过期键值信息的管理的。
* 附属节点即使发现过期键，也不会自作主张地删除它，而是等待主节点发来 `DEL` 命令，这样可以保证主节点和附属节点的数据总是一致的。 

#### Sentinel（哨兵模式）

* 如何监控主、从服务器

  sentinel 会以每十秒一次的频率向主服务器发送info命令，解析返回的信息从而获取主服务器下面的从服务器地址，以及服务器id 信息，

  同样也会向从服务器发送INFO 来获取 从服务器偏移量，角色，优先级，主服务器地址等等

  sentinel 会以每两秒一次向主服务器和从服务器发送以下格式的命令

* 通信

  哨兵会和主从节点发生双向通信，但和其他哨兵节点只发生单向通信，不会建立订阅连接。

  sentinel 集群只需要通过集群中的主节点或者集群中的从节点发来的消息来感知到其他sentinel 节点的存在。

* 主观下线

  单个哨兵节点判断主服务器超过设定的时长未返回正确回复信息，则将其置为主观下线

* 客观下线

  超过sentinel配置的值quorum 参数的哨兵任务主服务器已经下线 ，那么 就进入客观下线的状态

##### 如何选举新的主服务器

* 由一个领头的sentinel 负责进行故障转移操作。

  领头的sentinel 以sentinel 集群共同选出，当集群已经通过相互之间通信确定了主服务器确实已经下线了之后，需要向其他sentinel 发出通知，以求选举出领头的sentinel。

  **每个sentinel 节点都会要求另外的节点将自己设置为局部领头节点，但是sentinel 会按照顺序来处理，只响应第一个请求，后续的都会拒绝**。

  这里的选举也是由一轮轮投票规定的，每个哨兵节点会记录一个自增的**配置纪元值**

  哨兵集群之间如何通信以及投票：通过发送命令的形式

##### 故障转移

##### 选举新的主服务器

* 由局部领头的snetinel 服务器选举出新的主服务器地址，这里会首先排除掉一部分已经掉线了的从服务器
* 选取新的主服务器的规则有三个，**先看服务器的优先级，再看服务器的复制偏移量也就是谁的数据最全，最好再根据服务器id进行排序(选出id最小的)**
* 将其他从服务器的主服务器地址指向新的主服务器地址
* 将旧的主服务器设置为新的主服务器的从服务器

#### 集群模式

##### ClUSTER MEET 命令

* 通过向节点A 发送CLUSTER MEET 命令，让节点A 将节点B 加入到当前集群里面。

* 集群模式主要为了解决哨兵模式单主节点的性能瓶颈问题
* 多个主节点之间通过**gossip**协议来进行数据通信

##### 数据槽

* redis 数据库集群将数据分割为16384个集群，集群中的每个键都属于这个16384个槽中的一个，当所有的槽都有节点在处理时，集群处于上线模式，否则处于下线模式。
* 集群节点通过保存slots[] 数组来标记当前节点是否处理某个槽，如果处理的话则值为1，否则为0.
* 集群中每个主节点都会将自己所处理的槽信息发送给其他的主节点，并且自身也会保留所有槽对应的处理节点信息.

##### 记录槽的指派信息

* 每个节点会记录自己所处理的槽信息，并且会广播给其他节点自己所负责的槽节点信息。因此集群中的每个节点都知道16384个槽被指派给了哪个节点。

##### clusterState.slots

* 记录了每个槽对应的节点信息，这里是以数据槽为数组。

##### clusterNode.solts

* 记录了所有的被这个节点监管的槽信息,这里是只记录了当前节点所负责的solts 信息。

##### 执行命令

* 因为每个主节点都知道每个槽对应的处理机器在哪里，所以如果当前请求执行的槽信息不在当前主机负责范围内的话，那么将会返回MOVED命令给客户的，并且引导客户端向指定的主机发起请求。
* 但是当一个主节点正在执行槽迁移的过程中，则会返回给客户的ASK错误信息，这是一种临时的处理策略。

##### 计算键值属于哪个槽

CRC16(key) & (16384-1)

##### 重新分片

* 可以在线进行槽节点的重新分派，原理就是将原节点指定槽下的所有键都迁移到目标节点下，每个槽串行进行迁移。
* 节点的clusterState.importing_slots_from[i] 记录的是槽i 正在从其他节点导入，clusterState.migrating_slots_to[i] 表示槽i正在迁移往其他节点。

##### MOVED 与ASK 的区别

* 前者是会持久化影响槽的指派的，后者只是会单次影响槽的指派。访问槽i如果收到MOVED ，那么以后每次访问槽I 都会去新节点访问，如果返回ASK命令则只是在下次访问尝试指向新地址。

##### 故障迁移

* 集群模式其实是去中心化的模式，有集群中所有的主节点互相监控
* 当有过半主节点认为某一台主节点下线的时候，就需要发起 新的leader重新选举了。

##### 故障检测

* 集群中主节点A 会定时向集群中其他主节点B 发送PING 消息，如果超过一定时间内还没有接收到B 的PONG消息回复，则认为主节点B疑似下线了。
* 当主节点集群中存在过半的主节点都将某个主节点x报告为疑似下线状态，那么这个主节点将被标记为已下线状态。

##### 故障转移

* 因为每个主节点都保存了各个主节点的信息及其从节点的信息，所以当一个主节点下线之后会选举一个该主节点的从节点来作为新的主节点。
* 选举规则如下：
  1. 当从节点发现自己的主节点以及处于下线状态，那么他会向所有其他的主节点发出广播，要求主节点将自己置为新的主节点。
  2. 每个主节点有一次投票机会，并且只会将票投给第一个接收到的从节点，并且返回给从节点
  3. 当从节点接收到了过半的主节点的投票时，该从节点则晋升为新的主节点，并且承接原主节点所负责的槽。
* 选举的算法与sentinel 领头选举采用的是类似的算法

https://cloud.tencent.com/developer/article/1444057
