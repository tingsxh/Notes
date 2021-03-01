### InnoDb 系统架构

#### 线程

* Master Thread

  负责刷新异步缓存到磁盘，负责将脏页刷新到磁盘以及合并 INSERT BUFFER、回收UNDO 页等。

* IO Thread

  InnoDb 内部使用了大量的AIO, 所以这些IO Thread 主要是用来完成IO请求的回调处理。

* Purge Thread

  为了减轻Master 线程的负担，将undo页回收的能力抽到单独的线程中处理

#### 缓冲池

* 数据的查找首先检索缓冲池中是否存在，如果存在则直接返回。对应数据的修改也是先修改缓冲池中的页，然后通过一种checkpoint的机制刷新回磁盘。
* 缓冲池的数据更新维护是采用定制的LRU 算法类维护的，每次新读取的数据不是立即插入到队首的位置，而是插入到midpoint的位置，主要是为了防止非热点数据的读取将大量热点数据冲出缓存池。

##### 索引页

##### 数据页

##### undo 页

##### 插入缓冲

##### CheckPoint 技术（就是在合适的时候将内存中的脏页刷新到磁盘当中）

* 缩短数据库恢复的时间，缓冲池不够用，将脏页刷新到磁盘，重做日志不可用时，刷新脏页。
* Sharp CheckPoint 发生在数据库关闭时将所有的脏页都刷新会磁盘。
* Master Thread 中的checkpoint 以没秒汇总每十秒的速度从缓冲池的脏页表中刷新一定比例回磁盘。

#### Master Thread

* 每秒一次的操作：日志缓冲刷新到磁盘，即使这个事务也还没提交
* 合并插入缓冲（可能）
* 至多刷新100个InnoDB的缓冲池中的脏页到磁盘
* 如果当前没有用户活动，则切换到background loop

##### InnoDb 关键特性

* Insert Buffer、change Buffer

  使用insert Buffer 的条件必须是插入的对象是**非唯一的辅助索引**，因为如果是唯一的聚集索引的话，那么还需要去判断该条记录是否重复，会进一步增加操作性能损耗。

  insert Buffer 的存在会增加缓冲池内存的消耗

  **insert Buffer 的内部结构是一颗B+树**

* **insert buffer 的更新时间**

  1. 在辅助页被读取到缓冲池时，会检查一下insert buffer 中是否存在对改页数据的修改，是通过对insert Buffer BitMap 页追踪到该辅助页上的修改动作的。
  2. 当记录insert buffer BitMap 页追踪到该辅助索引页已无可用空间时 会强制将该buffer 刷入到缓冲池页中
  3. 还有一个就是master thread 会每10秒进行一次**merge insert  buffer** 的操作

* **两次写**

  1. wield防止数据页损坏导致数据丢失的问题，故先将脏页复制到doublewrite buffer ，然后先将doublewrite buffer 写入到共享表空间中，然后再写入数据表文件中。

* **异步Io**

  1. 采用非阻塞IO，并且AIO 可以进行IO合并
