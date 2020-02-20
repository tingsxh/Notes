public class Test{

   public TreeNode getTreeNode(Integer[] nums) {
        List<TreeNode> data = new ArrayList<>();
        TreeNode root = new TreeNode(nums[0]);
        data.add(root);
        for (int i = 1; i < nums.length; i = Math.min(nums.length, i + 2)) {
            TreeNode node = data.get(0);
            while (node == null) {
                data.remove(0);
                node = data.get(0);
            }
            node.left = (nums[i] == null ? null : new TreeNode(nums[i]));
            data.add(node.left);
            node.right = ((i + 1 >= nums.length || nums[i + 1] == null) ? null : new TreeNode(nums[i + 1]));
            data.add(node.right);
            data.remove(0);
        }
        return root;
    }
}
