

### HASHMap 分析

![45205ec2](C:\Users\d00464537\Desktop\45205ec2.png)



* 上图是贯穿 hashMap 生命周期的一个取余算法，它是通过 hash&(n-1)来计算的，因为n 总是2的二次幂，所以这个效果等于区域操作。

* **hashMap 插入源码解析**

```java
final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
               boolean evict) {
    HashMap.Node<K,V>[] tab; HashMap.Node<K,V> p; int n, i;
    if ((tab = table) == null || (n = tab.length) == 0)
        //如果tab 是空的则初始化
        n = (tab = resize()).length;
    if ((p = tab[i = (n - 1) & hash]) == null)
        //如果对应的桶位置，没有链表节点，则直接将该节点赋值给当前桶位置
        tab[i] = newNode(hash, key, value, null);
    else {
        //如果桶不为空，开始遍历链表
        HashMap.Node<K,V> e; K k;
        if (p.hash == hash &&
                ((k = p.key) == key || (key != null && key.equals(k))))
            //p 是链表的头节点，如果建的值与头结点匹配了的话，直接跳到后面，看是否需要替换值
            e = p;
        else if (p instanceof HashMap.TreeNode)
            //如果是红黑树，则只需红黑树遍历逻辑（略过）
            e = ((HashMap.TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
        else {
            //开始遍历所有链表
            for (int binCount = 0; ; ++binCount) {
                if ((e = p.next) == null) {
                    //当遍历的最后一个的时候，直接将值插入链表尾部
                    p.next = newNode(hash, key, value, null);
                    if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                        treeifyBin(tab, hash);
                    break;
                }
                //当找到了已存在相同的键值时，跳到后面看是否需要替换
                if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                    break;
                //将指针向后移位
                p = e;
            }
        }
        //如果找到了链表中已存在相同的值，判断是否需要替换值
        if (e != null) { // existing mapping for key
            V oldValue = e.value;
            if (!onlyIfAbsent || oldValue == null)
                e.value = value;
            afterNodeAccess(e);
            return oldValue;
        }
    }
    ++modCount;
    if (++size > threshold)
        //扩容机制
        resize();
    afterNodeInsertion(evict);
    return null;
}
```

* **查询源码分析**

```java
    final Node<K,V> getNode(int hash, Object key) {
        Node<K,V>[] tab; Node<K,V> first, e; int n; K k;
        //判断table 是否为空，以及相应桶位置是否为空
        if ((tab = table) != null && (n = tab.length) > 0 &&
            (first = tab[(n - 1) & hash]) != null) {
            //检查通坐标的头结点是不是
            if (first.hash == hash && // always check first node
                ((k = first.key) == key || (key != null && key.equals(k))))
                return first;
            //如果不是，并且头结点向下不为空的话，则继续向下遍历
            if ((e = first.next) != null) {
                //如果头结点是红黑树，那么以红黑树的方式查询
                if (first instanceof TreeNode)
                    return ((TreeNode<K,V>)first).getTreeNode(hash, key);
                do {
                    if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                        return e;
                } while ((e = e.next) != null);
            }
        }
        return null;
    }
```



* **hashMap 扩容源码解析**
* **扩容之后的，下标计算法则**
* ![](C:\Users\d00464537\Desktop\4d8022db.png)

```java
final HashMap.Node<K,V>[] resize() {
    HashMap.Node<K,V>[] oldTab = table;
    int oldCap = (oldTab == null) ? 0 : oldTab.length;
    int oldThr = threshold;
    int newCap, newThr = 0;
    //如果已经初始化过了
    if (oldCap > 0) {
        //已经达到了最大值
        if (oldCap >= MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return oldTab;
        }
        //还未达到最大值,newCap 数组大小，合扩容阀值都相应的翻倍
        else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                oldCap >= DEFAULT_INITIAL_CAPACITY)
            newThr = oldThr << 1; // double threshold
    }
    //说明老的oldCap==0 通数组还未初始化，但是阀值以及存在了
    else if (oldThr > 0) // initial capacity was placed in threshold
        //初始化桶数组大小
        newCap = oldThr;
    else {               // zero initial threshold signifies using defaults
        //完全初始化，调用无参构造方法时
        newCap = DEFAULT_INITIAL_CAPACITY;
        newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
    }
    //这里已经经过上面的初始化了，为什么还会为0，就是因为上面翻倍的过程很有可能会发生溢出，阀值等于 负载因子*桶的大小
    // 最大就是32位，为啥newCap不会溢出，因为newCap 最大限制就是2^28
    //但是thr 没有限制，在扩容的时候自动会翻两倍
    if (newThr == 0) {
        //如果溢出了需要从新计算
        float ft = (float)newCap * loadFactor;
        newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                (int)ft : Integer.MAX_VALUE);
    }
    threshold = newThr;
    @SuppressWarnings({"rawtypes","unchecked"})
    HashMap.Node<K,V>[] newTab = (HashMap.Node<K,V>[])new HashMap.Node[newCap];
    table = newTab;
    if (oldTab != null) {
        //遍历老的桶，需要将老的桶移动到新的桶
        for (int j = 0; j < oldCap; ++j) {
            HashMap.Node<K,V> e;
            if ((e = oldTab[j]) != null) {
                //对象已经转移至e,这里就及时释放了
                oldTab[j] = null;
                //如果该位置有只有一个节点，那就重新计算hash 值，放入新表位置
                if (e.next == null)
                    newTab[e.hash & (newCap - 1)] = e;
                //如果是红黑树，就另做处理
                else if (e instanceof HashMap.TreeNode)
                    ((HashMap.TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                else { // preserve order
                    //对于普通链表，需要将改位置的链表进行分组
                    //功能就相当于，重新计算hash 重新插入新的桶中（jdk7 就是这么实现的）
                    //因为具体放在新桶的哪个位置是由 (newCap-1)& hash 计算而来，对于newCap 来说相当于高位多了个1，其实我们只需要判断hsah 值上该位置对应的值是0还是1即可
                    //如果是0 ，则桶的位置不变，如果是1 则桶的位置等于加上一个newCap 大小
                    HashMap.Node<K,V> loHead = null, loTail = null;
                    HashMap.Node<K,V> hiHead = null, hiTail = null;
                    HashMap.Node<K,V> next;
                    do {
                        //将该位置的该条链表分组，一组继续保持原来位置，一组放在 原来位置+newCap 位置
                        next = e.next;
                        if ((e.hash & oldCap) == 0) {
                            //位置不变的链表分组
                            //如果该组链表还未赋值，先赋值给头节点
                            if (loTail == null)
                                loHead = e;
                            else
                                //如果已经存在了，则接在尾节点后面
                                loTail.next = e;
                            loTail = e;
                        }
                        else {
                            //位置需要改变的链表分支，逻辑与上保持一致
                            if (hiTail == null)
                                hiHead = e;
                            else
                                hiTail.next = e;
                            hiTail = e;
                        }
                    } while ((e = next) != null);
                    
                    //给新桶相应位置赋值
                    if (loTail != null) {
                        loTail.next = null;
                        newTab[j] = loHead;
                    }
                    if (hiTail != null) {
                        hiTail.next = null;
                        newTab[j + oldCap] = hiHead;
                    }
                }
            }
        }
    }
    return newTab;
}
```

* **关于红黑树的操作分析**

  * 涉及到红黑树的操作，一个就是在hashMap 在插入的时候，会判断一下当前区间的单链表长度是否大于8如果大于的话就需要转换成红黑树的结构**具体转换过程是，先将当前链表逐个转换为treeNode结构的双向链表，链表位置保持不变，转换完成之后，再由表头开始转换为红黑树的结构**
  * 还有就是在扩容的时候，需要将老的桶区间上的链表移动至新桶上，这里没有对红黑树进行链表化拆分，**保持和单链表迁移一致的方法，分成两颗树，一颗是挂在原来区间，一颗挂在原来区间+oldCap,移动完毕之后再按条件决定是否需要树化，如果红黑树链表小于6的话需要恢复成单链表格式，如果大于6的话则需要将双向链表重新树化**
  * 还有一个地方就是删除的时候，也需要根据条件将红黑树进行单链表化
  * 红黑树插入的所有节点，都保证为红色的这样方便调整：因为要满足如下几个性质
    * 根节点是黑的
    * 所有叶子节点是黑的
    * 不能有两个红节点相邻
    * 任意节点到器叶子节点的简单路径，需要包含相同个数的黑节点

* 红黑树的旋转操作

  * 左旋就是将某个节点旋转为其右孩子的左孩子，同时原来的右孩子的左孩子将变成 旋转过后该点的右孩子。
  * 右旋就是将某个节点旋转为其左孩子的右节点,  同时将原来左孩子的右节点 设置为当前节点左孩子

* 对应红黑树的增删目前还没看懂。

  ```java
  // jDK7 的实现 转移数据，相当于把原因的链表，倒序插入了，会造成死循环
  void transfer(Entry[] newTable, boolean rehash) {
          int newCapacity = newTable.length;
          for (Entry<K,V> e : table) { //遍历旧数组
              while(null != e) { //将这一个链表遍历添加到新的数组对应的bucket中
                  Entry<K,V> next = e.next;
                  if (rehash) {
                      e.hash = null == e.key ? 0 : hash(e.key);
                  }
                  int i = indexFor(e.hash, newCapacity);
                  e.next = newTable[i];
                  newTable[i] = e;
                  e = next;
              }
          }
      }
  ```

  

![](C:\Users\d00464537\Desktop\3.png)

![](C:\Users\d00464537\Desktop\4.png)



### TreeMap 源码解析

* treeMap 其实就是 红黑树的java 实现，存在如下几个疑问

* treeMap 是如何保证有序的

  * treeMap 默认是根据key 来进行排序的，当然也可以自定义排序规则，底层红黑树的性质决定了，最小的一个key 值肯定在最底层左孩子的位置，然后依次遍历**遍历规则是，取完了e节点，然后查看额节点是否有右孩子，如果有的话则以右孩子为父节点一直找最左边的孩子树。如果没有右孩子了，则需要判断当前是否是属于右孩子，如果是则**

    ```java
        static <K,V> TreeMap.Entry<K,V> successor(Entry<K,V> t) {
            if (t == null)
                return null;
            else if (t.right != null) {
               //如果当前节点的右节点还有，则继续往左遍历
                Entry<K,V> p = t.right;
                while (p.left != null)
                    p = p.left;
                return p;
            } else {
                //如果没有，则判断当前是属于左还是右，如果是左孩子，则直接next is paraent,如果是右，则下一个是父节点的父节点，因为右的话肯定比父大
                Entry<K,V> p = t.parent;
                Entry<K,V> ch = t;
                while (p != null && ch == p.right) {
                    ch = p;
                    p = p.parent;
                }
                return p;
            }
        }
    ```

    

### LinkHashMap 阅读笔记

- 初始化：集成至HashMap 首先初始化hashMap相关结构

- 关键在于linkNodeLast 记录了 Entry 插入的顺序，由一个双向链表保证,

- **双向链表的建立**，linkHashMap 大部分代码继承至hashMap 包括插入，只是覆写了部分方法，包括new Node

  ```java
    Node<K,V> newNode(int hash, K key, V value, Node<K,V> e) {
        //entry 继承至node 再额外添加了一个前驱及后驱，为了实现双向链表，为什么实现双向链表是因为可以反过来遍历
          LinkedHashMap.Entry<K,V> p =
              new LinkedHashMap.Entry<K,V>(hash, key, value, e);
        //这里便是双向链表建立的地方，将新的节点加入到链表尾部
          linkNodeLast(p);
          return p;
      }
  ```

- **linkHashMap 遍历查找**

  ```java
     final class LinkedEntrySet extends AbstractSet<Map.Entry<K,V>> {
          public final int size()                 { return size; }
          public final void clear()               { LinkedHashMap.this.clear(); }
         //这里是linkedHashMap返回的迭代器
          public final Iterator<Map.Entry<K,V>> iterator() {
              return new LinkedEntryIterator();
          ｝ 
      ｝
       //进一步查看
      abstract class LinkedHashIterator {
          LinkedHashMap.Entry<K,V> next;
          LinkedHashMap.Entry<K,V> current;
          int expectedModCount;
  
          LinkedHashIterator() {
              next = head;
              expectedModCount = modCount;
              current = null;
          }
  
          public final boolean hasNext() {
              return next != null;
          }
  
          //这里就是next 方法的关键所在，根据内部维护的双向链表进行遍历
          final LinkedHashMap.Entry<K,V> nextNode() {
              LinkedHashMap.Entry<K,V> e = next;
              if (modCount != expectedModCount)
                  throw new ConcurrentModificationException();
              if (e == null)
                  throw new NoSuchElementException();
              current = e;
              next = e.after;
              return e;
          }
  ｝
  ```

  

- 可以保证以最近访问来排序

  ```java
       void afterNodeAccess(Node<K,V> e) { // move node to last
                      LinkedHashMap.Entry<K,V> last;
                      if (accessOrder && (last = tail) != e) {
                          LinkedHashMap.Entry<K,V> p =
                                  (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
                          //访问的目标节点 p 以及p的前节点b p的后节点a
                          //先将p的尾节点置null
                          p.after = null;
                          if (b == null)
                              //说明p 为头结点，这个时候需要头结点上移
                              head = a;
                          else
                              //不是头节点的话，则直接将p 的一前一后连接起来
                              b.after = a;
                          if (a != null)
                              //如果p 不是尾节点的话，则a也指向b
                              a.before = b;
                          else
                              //如果a==null 则说明b 是尾节点
                              last = b;
                          //如果尾节点==null
                          if (last == null)
                              head = p;
                          else {
                              //移动至链表尾部
                              p.before = last;
                              last.after = p;
                          }
                          tail = p;
                          ++modCount;
                      }
       }
  ```

- **应用** LRUCaChe便是利用这个性质来完成的，我们还可以扩展 linkHashMap 覆写其中的决定是否删除最少访问元素的方法

### ConcurrentHashMap

* 笔记

#### HashMap 几个疑惑

* 单双向链表？
* 红黑树转换？
* jdk7与jdk8 对比
* 为什么能并发
* 为什么选择2的倍数

