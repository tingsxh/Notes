[TOC]

## QUARTZ 分析



### 写在前头

* quartz 的核心逻辑分为三块，分别是调度处理逻辑、哑火处理逻辑、集群处理逻辑，下面分别分析下这三个部分。

#### 状态流转分析

* `qrtz_triggers` 触发器的的状态流转状态，TRIGGER_STATE 状态流转的过程？
* `qrtz_fired_triggers` 的STATE 状态流转过程？
* 见下图

![](D:\Notes\框架分析\quartz_status2.png)



#### 数据库表



#### QUARTZ 调度处理流程

##### 实例的调度循环

* 在QUARTZ 中存在一个循环拉取触发器的线程`QuartzSchedulerThread` ，该线程在会循环拉取一个batchSize 大小的`trigger` 触发器，详见源码

```java
// 详见 QuartzSchedulerThread中的run 方法
try {
triggers = qsRsrcs.getJobStore().acquireNextTriggers(now + idleWaitTime, Math.min(availThreadCount, qsRsrcs.getMaxBatchSize()), qsRsrcs.getBatchTimeWindow());
                        acquiresFailed = 0;
} catch (JobPersistenceException jpe) {
    }
```

##### 批量拉取触发器

* 从`triggers` 表中 拉取一定数量的trigger 触发器，查询条件为 `next_trigger_time < now()+30*1000` ,并且还需要获取到数据库锁，锁的字段为`TRIGGER_ACCESS`, 具体逻辑源码如下

```java
// 位于JobStoreSupport.java文件
public List<OperableTrigger> acquireNextTriggers(final long noLaterThan, final int maxCount, final long timeWindow)
        throws JobPersistenceException {
        String lockName;
        if(isAcquireTriggersWithinLock() || maxCount > 1) { 
            lockName = LOCK_TRIGGER_ACCESS;
        } else {
            lockName = null;
        }
        return executeInNonManagedTXLock(lockName, 
                // 这里是两个回调方法，具体在executeInNonManagedTXLock 这里面回调
                new TransactionCallback<List<OperableTrigger>>() {
                    /*****省略****/
                },
                new TransactionValidator<List<OperableTrigger>>() {
                    /**省略**/
                    }
                });
    }
```

* 再分析如下方法`executeInNonManagedTXLock`

```java
    protected <T> T executeInNonManagedTXLock(
            String lockName, 
            TransactionCallback<T> txCallback, final TransactionValidator<T> txValidator) throws JobPersistenceException {
        boolean transOwner = false;
        Connection conn = null;
        try {
            // 这里是拿锁的过程
            if (lockName != null) {
                if (getLockHandler().requiresConnection()) {
                    conn = getNonManagedTXConnection();
                }
                transOwner = getLockHandler().obtainLock(conn, lockName);
            }
            if (conn == null) {
                conn = getNonManagedTXConnection();
            }
            // 这里执行上面提到的回调方法
            final T result = txCallback.execute(conn);
            try {
                commitConnection(conn);
            } catch (JobPersistenceException e) {
                // 如果出现异常，则手动回滚
                rollbackConnection(conn);
                // 这里是一个立即重试机制，通过retryExecuteInNonManagedTXLock 再走回到这个方法
                if (txValidator == null || !retryExecuteInNonManagedTXLock(lockName, new TransactionCallback<Boolean>() {
                    @Override
                    public Boolean execute(Connection conn) throws JobPersistenceException {
                        return txValidator.validate(conn, result);
                    }
                })) {
                    throw e;
                }
            }
            Long sigTime = clearAndGetSignalSchedulingChangeOnTxCompletion();
            if(sigTime != null && sigTime >= 0) {
                // ？
                signalSchedulingChangeImmediately(sigTime);
            }
            return result;
        }finally {
            try {
                // 释放锁
                releaseLock(lockName, transOwner);
            } finally {
                cleanupConnection(conn);
            }
        }
    }
    
```

* 再来看下txCallback 所回调的方法，也就是真正的拉取过程

```java
protected List<OperableTrigger> acquireNextTrigger(Connection conn, long noLaterThan, int maxCount, long timeWindow) {
    // 代码很长，总共做了一下几件事情
    // 操作数据库，获取指定条件下的一批触发器 trigger
List<TriggerKey> keys = getDelegate().selectTriggerToAcquire(conn, noLaterThan + timeWindow, getMisfireTime(), maxCount);
    
for(TriggerKey triggerKey: keys) {
// If our trigger is no longer available, try a new one.
OperableTrigger nextTrigger = retrieveTrigger(conn, triggerKey);   
    // 判断触发器是否有效
    // 接着在判断触发器所绑定的jobKey 是否有效
    JobKey jobKey = nextTrigger.getJobKey();
    job = retrieveJob(conn, jobKey);
    // 判断该job 是否允许并发执行，如果不允许的需要记录下来，稍后做block 处理
      if (job.isConcurrentExectionDisallowed()) {
                        if (acquiredJobKeysForNoConcurrentExec.contains(jobKey)) {
                            continue; // next trigger
                        } else {
                            acquiredJobKeysForNoConcurrentExec.add(jobKey);
                        }
          
      }
       
 // 这里是利用乐观锁的方式，修改trigger 的状态，由waiting -> ACQUIRED
int rowsUpdated = getDelegate().updateTriggerStateFromOtherState(conn, triggerKey, STATE_ACQUIRED, STATE_WAITING);
if (rowsUpdated <= 0) {
  continue; // next trigger
}   
 // 紧接着去创建一天 fired_trigger的触发记录，状态为ACQUIRED
 nextTrigger.setFireInstanceId(getFiredTriggerRecordId());
 getDelegate().insertFiredTrigger(conn, nextTrigger, STATE_ACQUIRED, null);
}

}
```

* 走完了批量查询触发器的过程之后来到了外层的循环

```java
 now = System.currentTimeMillis();
long triggerTime = triggers.get(0).getNextFireTime().getTime();
long timeUntilTrigger = triggerTime - now;
// 循环判断第一个触发器距离当前的距离，一旦小于2毫秒了就立即触发
while (timeUntilTrigger > 2) {
    synchronized (sigLock) {
        if (halted.get()) {
            break;
        }
        if (!isCandidateNewTimeEarlierWithinReason(triggerTime, false)) {
            try {
                // we could have blocked a long while
                // on 'synchronize', so we must recompute
                now = System.currentTimeMillis();
                timeUntilTrigger = triggerTime - now;
                if (timeUntilTrigger >= 1)
                    sigLock.wait(timeUntilTrigger);
            } catch (InterruptedException ignore) {
            }
        }
    }
    // 如果触发器的调度被改变了，则需要直接退出
    if (releaseIfScheduleChangedSignificantly(triggers, triggerTime)) {
        break;
    }
    now = System.currentTimeMillis();
    timeUntilTrigger = triggerTime - now;
```

##### 执行 队列中的触发器

* 直到触发时间到了之后，才继续往下执行

```java
// 开始触发执行
List<TriggerFiredResult> res = qsRsrcs.getJobStore().triggersFired(triggers);
// 接下来是遍历所有的triggers 逐个触发执行
for (OperableTrigger trigger : triggers) {
   try {
       // 进行单个触发
    TriggerFiredBundle bundle = triggerFired(conn, trigger);
      // 包装下触发执行的结果
    result = new TriggerFiredResult(bundle);
    } catch (JobPersistenceException jpe) {
    result = new TriggerFiredResult(jpe);
    } catch(RuntimeException re) {
    result = new TriggerFiredResult(re);
    }
    results.add(result);
}
```

* 接着分析`triggerFired()`该方法主要完成以下几点功能

1. 校验触发器`triggers` 表 中对应触发器的状态是否是 ACQUIRED 状态

2. 校验JOB 是否有效，关联的Class 是否有效

3. 更新状态,如下所示这里是更新 fired_trigger 表中的触发器状态，更新为EXECUTING 执行中的状态

   ```java
   getDelegate().updateFiredTrigger(conn, trigger, STATE_EXECUTING, job);
   ```

4. 接着再更新trigger 触发器的状态，这里会根据当前job 否是允许并发执行来更新状态，代码如下

   ```java
   if (job.isConcurrentExectionDisallowed()) {
        state = STATE_BLOCKED;
        force = false;
        try {
            // 如果是不允许并发执行，则更新触发器状态BLOCKED
            getDelegate().updateTriggerStatesForJobFromOtherState(conn, job.getKey(),
                    STATE_BLOCKED, STATE_WAITING);
            getDelegate().updateTriggerStatesForJobFromOtherState(conn, job.getKey(),
                    STATE_BLOCKED, STATE_ACQUIRED);
            getDelegate().updateTriggerStatesForJobFromOtherState(conn, job.getKey(),
                    STATE_PAUSED_BLOCKED, STATE_PAUSED);
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't update states of blocked triggers: "
                            + e.getMessage(), e);
        }
    } 
   ```

5. 如果job 是默认的允许并发，则会根据下一次的触发时间是否为空来觉得更新的状态，如果下一次触发时间为空，则更新触发器状态为COMPLETED,否则更新触发器状态为WAITING 等待被触发状态。

6. 最后就剩下job 的执行动作了，job是被包装成一个jobShell 任务去执行的,代码如下

   ```java
         JobRunShell shell = null;
   try {
       // 这里时间任务包装初始化
       shell = qsRsrcs.getJobRunShellFactory().createJobRunShell(bndle);
       shell.initialize(qs);
   } catch (SchedulerException se) {
      qsRsrcs.getJobStore().triggeredJobComplete(triggers.get(i), bndle.getJobDetail(), CompletedExecutionInstruction.SET_ALL_JOB_TRIGGERS_ERROR);
      continue;
   }
   // 这里是真正的触发执行
   if (qsRsrcs.getThreadPool().runInThread(shell) == false) {
       getLog().error("ThreadPool.runInThread() return false!");
       qsRsrcs.getJobStore().triggeredJobComplete(triggers.get(i), bndle.getJobDetail(), CompletedExecutionInstruction.SET_ALL_JOB_TRIGGERS_ERROR);
                               }
   ```

7. 一步步跟进去，在最后执行完成之后，会根据执行的任务状态进行回调到

   ```java
   qs.notifyJobStoreJobComplete(trigger, jobDetail, instCode); 
   // 再进一步进入到
   protected void triggeredJobComplete(Connection conn,
               OperableTrigger trigger, JobDetail jobDetail,
               CompletedExecutionInstruction triggerInstCode) throws JobPersistenceException {
       // 再这个方法里面，会根据执行的返回状态码刷新trigger 的状态
       // 并且会删除掉fired_trigger表中的那条相应触发器的记录。
   }
   ```

* 上面分析的整个流程都是 `QuartzSchedulerThread` 调度线程 的处理逻辑，除此之外还有 `MisfireHandler` 哑火触发器处理线程，和集群管理处理线程`ClusterManager`

#### 集群处理线程

##### 循环检查所有实例

* `ClusterManager.java` 是在schedulerStarted 方法中被初始化，并且启动执行的，主要逻辑在run 方法中

  ```java
  public void run() {
      // 检查集群
        if (!shutdown && this.manage()) {
                      signalSchedulingChangeImmediately(0L);
       }
  }
  // manage() --->doCheckin()
   if (!firstCheckIn) {
       // 如果是第一次，需要先插入schedue_stat 表
       failedRecords = clusterCheckIn(conn);
       commitConnection(conn);
   }
  // 这里是去获取哪些实例失效了
  failedRecords = (firstCheckIn) ? clusterCheckIn(conn) : findFailedInstances(conn);
  
  ```

* 进到`findFailedInstances()`方法

  ```java
  // 获取所有schedule_state 记录
  List<SchedulerStateRecord> states = getDelegate().selectSchedulerStateRecords(conn, null);
  for(SchedulerStateRecord rec: states) {
      // find own record...
      // 寻找实例，如果与当前实例一致的话
      if (rec.getSchedulerInstanceId().equals(getInstanceId())) {
          foundThisScheduler = true;
          // 如果是第一次启动的话，所有的实例都需要重新恢复
          if (firstCheckIn) {
              failedInstances.add(rec);
          }
      } else {
          // find failed instances...
          // 如果上一次检查的时间，也可以理解为心跳时间，比当前时间小于一定差距了，则该实例被算作故障
          if (calcFailedIfAfter(rec) < timeNow) {
              failedInstances.add(rec);
          }
      }
  }
  // The first time through, also check for orphaned fired triggers.
  ```

##### 故障实例的恢复

* 获取到了所有的失效的实例之后，再进一步处理逻辑如下

  ```java
  if (failedRecords.size() > 0) {
      // 获锁
      getLockHandler().obtainLock(conn, LOCK_TRIGGER_ACCESS);
  
      //getLockHandler().obtainLock(conn, LOCK_JOB_ACCESS);
      transOwner = true;
      // 将故障实例所触发的 triggers 的状态恢复
      clusterRecover(conn, failedRecords);
      recovered = true;
  }
  ```

* 进入到`clusterRecover` 方法

  ```java
  // 遍历所有的故障实例，根据每个实例去 fired_trigger 表查询被其触发过的triggers
  List<FiredTriggerRecord> firedTriggerRecs = getDelegate
          .selectInstancesFiredTriggerRecords(conn,
                  rec.getSchedulerInstanceId());
  int acquiredCount = 0;
  int recoveredCount = 0;
  int otherCount = 0;
  Set<TriggerKey> triggerKeys = new HashSet<TriggerKey>()
  for (FiredTriggerRecord ftRec : firedTriggerRecs) {
      // 对单个triggers 进行恢复，
  }
  ```

* 在对触发器恢复的过程中，会将之前故障实例处理的触发器状态恢复，对以及完成的触发器进行删除等等操作，这样就保障了任务允许的高可用。

##### 注意点

* 这里有一点需要注意**对于JOB 有一个requestRecovery 属性**, 以下是特殊处理的过程

  ```java
  else if (ftRec.isJobRequestsRecovery()) {
    // handle jobs marked for recovery that were not fully
    // executed..
    if (jobExists(conn, jKey)) {
        // 对于有要求需要恢复的job，会理解创建一个新的触发器
        @SuppressWarnings("deprecation")
        SimpleTriggerImpl rcvryTrig = new SimpleTriggerImpl(
                "recover_"
                        + rec.getSchedulerInstanceId()
                        + "_"
                        + String.valueOf(recoverIds++),
                Scheduler.DEFAULT_RECOVERY_GROUP,
                new Date(ftRec.getScheduleTimestamp()));
        rcvryTrig.setJobName(jKey.getName());
        rcvryTrig.setJobGroup(jKey.getGroup());
        rcvryTrig.setMisfireInstruction(SimpleTrigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY);
        rcvryTrig.setPriority(ftRec.getPriority());
    } 
  ```

  

#### 激活失败处理器

* 如上所述，在QUARTZ 中，单独有一个线程来管理哑火的触发器如何处置，就是`MisfireHandler` 具体业务逻辑在run  方法中

  ```java
  // 这里是去获取所有的哑火
  protected RecoverMisfiredJobsResult doRecoverMisfires() 
  // 再到
  RecoverMisfiredJobsResult recoverMisfiredJobs()
  // 获取已经哑火的触发器，存放在misfiredTriggers中
   boolean hasMoreMisfiredTriggers =
   getDelegate().hasMisfiredTriggersInState(
   conn, STATE_WAITING, getMisfireTime(), 
   maxMisfiresToHandleAtATime, misfiredTriggers);
   
  ```

* 完成的事情就是根据每个哑火的触发器trigger 去查找其相应的执行策略，另外每个类型的触发器都有其默认的处理实现，哑火处理的四种策略如下。

* ```java
  //所有的misfile任务马上执行，所有丢失的全部都执行回来
  public static final int MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY = -1;
  //在Trigger中默认选择MISFIRE_INSTRUCTION_FIRE_ONCE_NOW 策略
  public static final int MISFIRE_INSTRUCTION_SMART_POLICY = 0;
  // CornTrigger默认策略，合并部分misfire 会执行一次，正常执行下一个周期的任务。
  public static final int MISFIRE_INSTRUCTION_FIRE_ONCE_NOW = 1;
  //所有的misFire都不管，执行下一个周期的任务。
  public static final int MISFIRE_INSTRUCTION_DO_NOTHING = 2;
  ```



### 参考

[blog](https://techblog.ppdai.com/2018/07/13/20180713/)

[blog2](https://www.jianshu.com/p/572322b36383)

[blog3](http://wenqy.com/2018/04/14/quartz管中窥豹之线程处理.html)
