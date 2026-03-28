package lexor.nodes;

public class UnaryOpNode extends Node{
    public final String operator;  // + or - or NOT
    public final Node operand;

    public UnaryOpNode(String operator, Node operand) {
        this.operator = operator;
        this.operand = operand;
    }

}
