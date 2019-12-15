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
