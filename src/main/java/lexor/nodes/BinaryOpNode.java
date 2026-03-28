package lexor.nodes;

public class BinaryOpNode extends Node{
    public final Node left;
    public final String operator;  // +, -, *, /, %, >, <, >=, <=, ==, <>, AND, OR
    public final Node right;

    public BinaryOpNode(Node left, String operator, Node right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }
}
