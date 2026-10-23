package lexor.nodes;

public class UnaryOpNode extends Node{
    public final String operator;  // + or - or !
    public final Node operand;

    public UnaryOpNode(int line, String operator, Node operand) {
        super(line);
        this.operator = operator;
        this.operand = operand;
    }

}
