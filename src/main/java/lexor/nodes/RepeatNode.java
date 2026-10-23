package lexor.nodes;

import java.util.List;

// node for "while loop"
public class RepeatNode extends Node{
    public final Node condition;
    public final List<Node> body;

    public RepeatNode(int line, Node condition, List<Node> body) {
        super(line);
        this.condition = condition;
        this.body = body;
    }
}
