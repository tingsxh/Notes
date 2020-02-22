### 排序

* 普通的选择排序
```java

    int length = nums.length;
    for (int i = 0; i < length - 1; i++) {
        for (int j = 1; j < length; j++) {
            if (nums[j] < nums[i]) {
                int t = nums[j];
                nums[j] = nums[i];
                nums[i] = t;
                }
            }
        }

```
* 普通插入排序

```java

  int length = nums.length;
  for (int i = 1; i < length; i++) {
    for (int j=i;j>0;j--){
      if (nums[j]<nums[j-1]){
        int t=nums[j];
        nums[j]=nums[j-1];
        nums[j-1]=t;
      }else {
        break;
      }
    }
  }
  //以上是一种最常见的插入排序，原理就是：在第一轮循环前面的永远都是有序的。保障了这一点，第二轮循将一个新的值插入到前面已经排好序的数组中。
  //它的缺点在于，新的值每对比一次都需要发送一次交换，这是很耗时的。
  
```
* 优化插入排序

```java

   int length = nums.length;
      for (int i = 1; i < length; i++) {
         // 暂存新的值，找到了合适的位置，再进行
          int temp = nums[i];
          int j;
          for ( j = i; j > 0; j--) {
            //判断 temp 是否应该在当前位置
              if (temp < nums[j - 1]) {
                  nums[j] = nums[j - 1];
              } else {
                  break;
              }
          }
          nums[j] = temp;
      }
  //判断的标志，便是拿temp 和前一个索引值nums[j-1]对比，如果小于,则nums[j-1] 向前移动一位，
  // 如果不小于，说明temp 的准确位置就应该在 j 这个位置，则直接赋值。
  //与上述对比，不用每一次对比成功都进行交换数据，只有找到合适的位置，才进行数据交换。
  
```

* 希尔排序





* 数组堆化

```java
    public HeapTest(int[] nums) {
        /***
         * 最大堆的话，我们只需要从第一个不是叶子节点的元素（n/2）这个位置开始即可
         */
        int length = nums.length;
        for (int i = (length - 1) / 2; i > 0; i--) {
            //将数组上移动
            shiftDown(nums, i);
        }
        this.nums = nums;
    }

    //元素上移
    private void shiftDown(int[] nums, int i) {
        //判断是否是合适大位置，只需要判断是否比子元素都大
        int length = nums.length-1;
        int leftchild = 2 * i + 1;
        while (leftchild <= length) {
            if (leftchild + 1 <= length && nums[leftchild] < nums[leftchild + 1]) {
                leftchild++;
            }

            if (nums[i] >= nums[leftchild]) {
                break;
            }
            //交换
            int t = nums[i];
            nums[i] = nums[leftchild];
            nums[leftchild] = t;
            leftchild = 2 * leftchild + 1;
        }
    }
```
* 快速排序

```java

 /**
     * 快速排序分割
     *
     * @param nums
     * @param left
     * @param right
     */
    private void quickSplit(int[] nums, int left, int right) {
        if (left >= right) {
            return;
        }
        //对left~right 这段数组进行前后分组，以left 找到left 元素所应该在的一个中间位置
        //这里考虑一下：[left~right] 里面至少有两个数字，
        int position = partition2(nums, left, right);
        //当只有两个数组进行前后分组了之后，就到底了，然后会一层层返回。
        quickSplit(nums, left, position - 1);
        quickSplit(nums, position + 1, right);
    }

    private Integer partition(int[] nums, int left, int right) {
        //这个是整个快速排序最核心的部分
        //选取，第一个元素为中间值
        int random = new Random().nextInt(right - left + 1) + left;
        int temp1 = nums[random];
        nums[random] = nums[left];
        nums[left] = temp1;
        int v = nums[left];
        //position 代表该中间值应该在的索引位置，所以初始值当然就是原始位置 left 了，
        int position = left;
        for (int i = left + 1; i <= right; i++) {
            //开始从第二个元素遍历
            //只有当找到了小于 中间值的元素，意味着应该放中间值的左边
            if (nums[i] < v) {
                //这里将该值，与中间值位置后一个元素position+1交换，为什么不是与position 交换呢?因为position的定义是v本身合适的位置，那么如果
                //找到了小于v的值，就应该扩大position 位置，所以应该防在position+1 的位置，同时position 应该+1
                position++;
                int temp = nums[position];
                nums[position] = nums[i];
                nums[i] = temp;
            }
        }
        //最后交换 一下v 与position 的位置即可
        int temp = nums[position];
        nums[position] = v;
        nums[left] = temp;
        return position;
    }
    
```
   

