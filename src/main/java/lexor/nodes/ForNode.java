package lexor.nodes;

import java.util.List;

// node for "for loop"
public class ForNode extends Node{
    public final Node initialization;
    public final Node condition;
    public final Node update;
    public final List<Node> body;

    public ForNode(Node initialization, Node condition, Node update, List<Node> body) {
        this.initialization = initialization;
        this.condition = condition;
        this.update = update;
        this.body = body;
    }

}
