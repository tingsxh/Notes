## 事务分类

* 扁平事务（带保存点的扁平事务）
* 链式事务: 将提交事务和开始下一个事务合并为一个原子操作，意味着当前事务能看到上一个事务。回滚也只能回滚当前事务
* 嵌套事务：任何子事务都在只能在顶层事务提交之后才能真正提交。
* 分布式事务
* 事务的传播特性：

#### 事务中的几种日志

* redoLog

  通过Force Log at Commit 机制实现事务的持久性，每次事务提交完成都需要进行重做日志的写入。

  1. LSN
  2. checkpoint(代表每个页中已经刷新到磁盘上了的LSN位置)

  

* undoLog

  undoLog 是逻辑日志，每次回滚只是执行一次相反的操作，insert ->delete  delete->insert。

  ubdoLog 也是实现多版本并发控制的保障，另外undoLog 的产生也会伴随着redoLog 产生。

  1. insert undo log 

     是指在insert 操作中参数的undo log，因为insert 操作的记录，只对事务本身可见，对其他事务不可见，该事务提交之后可直接删除。

  2. update undo log

* binLog
