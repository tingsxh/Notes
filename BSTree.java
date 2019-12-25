package com.atings.leetcode;

import java.util.ArrayDeque;
import java.util.Queue;

public class BSTree {

    private Node root;

    private int count;

    /**
     * 插入
     *
     * @param key
     * @param value
     * @return
     */
    public Node insert(Integer key, String value) {
        Node root = this.root;
        count++;
        while (root != null) {
            if (key < root.key) {
                if (root.left == null) {
                    root.left = new Node(key, value);
                    break;
                } else {
                    root = root.left;
                }
            } else if (key > root.key) {
                if (root.right == null) {
                    root.right = new Node(key, value);
                    break;
                } else {
                    root = root.right;
                }
            } else {
                root.key = key;
                root.value = value;
                count--;
                break;
            }
        }
        if (root == null) {
            this.root = new Node(key, value);
        }
        return this.root;
    }

    /***
     * 搜索
     * @param key
     * @return
     */
    public String search(Integer key) {
        return search(root, key);
    }

    /***
     *  递归搜索
     * @param root
     * @param key
     * @return
     */
    private String search(Node root, Integer key) {
        if (root == null) {
            return null;
        }
        if (key.equals(root.key)) {
            return (String) root.value;
        }
        if (key < root.key) {
            return search(root.left, key);
        } else {
            return search(root.right, key);
        }
    }

    /**
     * 前序遍历
     *
     * @param root
     */
    private void preOrder(Node root) {
        if (root != null) {
            System.out.println(root.key);
            preOrder(root.left);
            preOrder(root.right);
        }
    }

    /**
     * 中序遍历
     *
     * @param root
     */
    private void midOrder(Node root) {
        if (root != null) {
            midOrder(root.left);
            System.out.println(root.key);
            midOrder(root.right);
        }
    }

    /***
     * 后序遍历
     * @param root
     */
    private void aftOrder(Node root) {
        if (root != null) {
            aftOrder(root.left);
            aftOrder(root.right);
            System.out.println(root.key);
        }
    }

    /**
     * 层序遍历 ，利用队列来辅助实现
     */
    private void layerOrder() {
        Queue<Node> queue = new ArrayDeque<>();
        queue.offer(root);
        StringBuilder stringBuilder = new StringBuilder();
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            stringBuilder.append(node.key + " ");
            if (node.left != null) {
                queue.offer(node.left);
            }
            if (node.right != null) {
                queue.offer(node.right);
            }
        }
        System.out.println(stringBuilder.toString());

    }

    /**
     * 返回最小值
     */
    public Node minNode() {
        Node node = this.root;
        while (node.left != null) {
            node = node.left;
        }
        return node;
    }

    /**
     * 返回最大值
     */
    public Node maxNode() {
        Node node = this.root;
        while (node.right != null) {
            node = node.right;
        }
        return node;
    }

    public void removeMaxNode() {

    }

    public void removeMin() {
        //最小的肯定没有左子数
        Node parent = this.root;
        Node current=this.root;

        while (current.left!=null) {
            parent=current;
            current=current.left;
        }
        //当左子树为空了，说明已经找到最小值了, 用该值当右树替换掉
        parent.left=current.right;
    }


    public void removeMax() {
        //最小的肯定没有左子数
        Node parent = this.root;
        Node current=this.root;

        while (current.right!=null) {
            parent=current;
            current=current.right;
        }
        //当左子树为空了，说明已经找到最小值了, 用该值当右树替换掉
        parent.right=current.left;
    }

    /***
     * 删除节点
     * @param key
     */
    public void removeNode(Integer key){
        //首先找到该节点
        Node currentNode=this.root;
        Node parentNode=this.root;
    }


    class Node {
        private Integer key;
        private Object value;
        private Node left;
        private Node right;

        public Node(Integer key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    public static void main(String[] args) {
        BSTree bsTree = new BSTree();
        bsTree.insert(10, "123");
        bsTree.insert(5, "5");
        bsTree.insert(20, "203333");
        bsTree.insert(12, "20455");
        bsTree.insert(2, "22220");
        bsTree.insert(30, "20221");
        bsTree.insert(50, "2033");
        bsTree.insert(3, "233");
        bsTree.insert(21, "22");
        Node root = bsTree.insert(39, "39");

        bsTree.layerOrder();

        bsTree.removeMax();

        bsTree.layerOrder();


    }

    private static void printTree(Node root) {
        System.out.println(getTree(root));
    }

    private static String getTree(Node root) {
        if (root == null) {
            return "";
        }
        if (root.left == null && root.right == null) {
            return root.key + "";
        }
        return root.key + " " + getTree(root.left) + " " + getTree(root.right);
    }

    /**
     * 获取二叉树的层数
     *
     * @param x
     * @return
     */
    private static int getLayer(double x) {
        double value = Math.log(x + 1) / Math.log(2);
        if (value % (int) value > 0) {
            return (int) value + 1;
        }
        return (int) value;
    }
}
