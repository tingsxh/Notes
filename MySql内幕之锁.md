## Innodb 中锁的细节

### 锁的分类

* Lock锁

  lock 里面可以再细分为行级索，表锁，页锁之类的，其中行锁分为共享锁（S锁），排他锁（X排他锁）

* **意向锁**，将锁定的对象分为多个层次，在更细粒度上进行加锁，意向锁中分为IS(意向共享锁)，IX(意向排他锁)

* 在innodb 存储引擎支持的意向锁比较简单，其意向锁即为表级别的锁，设计的目的是为了咋下一个事务中揭示下一行被请求的锁类型。

* 自增长锁

  在innodb 中自增长

#### 锁的算法（存在 10,11,13,20 几个数据）

* Record Lock (行记录锁)，总是会锁住索引记录，如果InnoDB 存储引擎表在建立的时候没有索引，则会使用主键来锁定
* Gap Lock (间隙锁)，锁定一个范围不包含本身 但是在主键索引的情况下会降级为记录锁
* Next-Key Lock (行锁+间隙锁), 会锁定  `(~,10),[10,11),[11,13),[13,20),[20,~)`
* 在innodb 中可重复读的隔离级别下，默认的查询中都是使用这种方法，以避免幻读的问题。但是当查询的条件是主键且唯一的情况下，就会降级为Record Lock 。只有在隔离级别为可重复读的情况下，才会使用该锁来解决幻读问题。
* 当查询是非唯一索引，或者是队列索引中的一个时，还是会延用Next-Key Lock 来加锁。
* 在Innodb 存储引擎中，对于Insert 操作，其会检查插入记录的下一条记录是否被锁定，若被锁定则不允许被查询

##### 举列说明

```mysql
table (a,b) a 是主键，b是辅助索引，有如下记录 (1,2)  (3,4) (6,5) (8,5) (13,11)
# 例1
select * from table where a=1 for update # 这个时候锁会降级为记录锁，只锁定(1,2) 这一行记录
# 例2
select * from table where a<3  for update# 这个时候会锁定 (1,3] 和(~,3)
# 例3
select * from table  where b=4 for update # 这个时候锁定的是 (2,4] 和 (4,5) innodb 会为该记录的左右两侧最接近的值作为区间
# 例4
select * from table where b=13 for update # 锁定 (11,~)
# 例5
select * from table where b>4 for update # 锁定 (4,5](5,11](11,~]
```

#### 锁升级

* 

#### 锁排查

* `INNODB_LOCK_WAITS`

##### 一致性锁定读

* 使用加行锁的读法，例如select for update 、select lock in share mode
* 在使用**select ... form update** 的时候，where 下的条件需要为明确的主键才会使用行级锁，否则会退化为表级锁。

##### 一致性非锁定读

* 普通读取，利用MVVC 多版本并发控制实现读取历史版本的数据。

##### 外键和锁

* 前面已经介绍了外键，外键主要用于引用完整性约束检查，在InnoDB存储引擎中，对于一个外键列，如果没有显式的对这个列加索引，innodb 会自动为期添加索引。

#### 幻读

#### 什么是幻读？

* 幻读是在`可重复读`的事务隔离级别下会出现的一种问题，简单来说，`可重复读`保证了当前事务不会读取到其他事务已提交的 `UPDATE` 操作。但同时，也会导致当  前事务无法感知到来自其他事务中的 `INSERT` 或 `DELETE` 操作，这就是`幻读`。

```shell
# 比如在同一个事务A中查询，select * from t where a>2 
# 如果这个时候存在其他事务也在插入a>2 的数据并且提交，那么对于事务A来说的话，前后读取的数据将会不一致。
```

* 解决幻读的方法，就是根据查询的条件加上相应的间隙锁。

#### 脏读

* 读取了未提交的数据。

#### 不可重复读

* 针对同一数据的读取，多次读取返回结果值不一样，通过并发版本控制

#### 更新丢失

* 当前数据库系统下不会出现更新丢失的问题。
