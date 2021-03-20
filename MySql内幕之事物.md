## 事务分类

#### 事务的实现机制

* 事务的隔离性是由锁来实现的，原子性、一致性、持久性都是通过数据库的redo、undo 日志来实现的。

* 扁平事务（带保存点的扁平事务）
* 链式事务: 将提交事务和开始下一个事务合并为一个原子操作，意味着当前事务能看到上一个事务。回滚也只能回滚当前事务
* 嵌套事务：任何子事务都在只能在顶层事务提交之后才能真正提交。
* 分布式事务
* 事务的传播特性：

#### 事务中的几种日志

* redoLog

  重做日志可按三个维度来理解(redo_log_buffer重做日志缓冲， redo_log_file_buffer重做日志文件缓冲， redo_log_file重做日志文件)

* redoLog Block(重做日志块)

* 重做日志块大小为512字节，如果一个页的重做日志超过了该大小则需要拆分为多个日志块。

* 重做日志块由日志块头、日志内容、日志块尾组成

  通过Force Log at Commit 机制实现事务的持久性，每次事务提交完成都需要进行重做日志的写入。

  1. LSN（记录重做日志的写入总量，在恢复过程中会将每个页的头部LSN和重做日志的中的LSN进行对比）
  2. checkpoint(代表每个页中已经刷新到磁盘上了的LSN位置)，每次日志的重做都从checkpoint 的位置开始，checkpoint~LSN。

* undoLog

  undoLog 是逻辑日志，每次回滚只是执行一次相反的操作，insert ->delete  delete->insert。

  1. undoLog存放于共享表空间中，

  ubdoLog 也是实现多版本并发控制的保障，另外undoLog 的产生也会伴随着redoLog 产生。

  1. insert undo log 

     是指在insert 操作中参数的undo log，因为insert 操作的记录，只对事务本身可见，对其他事务不可见，该事务提交之后可直接删除。

  2. update undo log

#### undoLog 的回滚原理：

#### Group Commit 失效

* 通过prepare_commit_mutex 锁来控制，保证MySql数据库上层二进制日志写入顺序和InnoDB层事务提交顺序一致，但是却导致group commit 失效的问题。

#### 内部XA事务控制 二进制日志与重做日志之间的事务

* 

