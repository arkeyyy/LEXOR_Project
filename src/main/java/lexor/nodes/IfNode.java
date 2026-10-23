package lexor.nodes;

import java.util.List;

public class IfNode extends Node{
    public final Node condition;
    public final List<Node> thenBranch;
    public final List<IfNode> elseIfBranches;  // ELSE IF chains
    public final List<Node> elseBranch;         // nullable

    public IfNode(int line, Node condition, List<Node> thenBranch, List<IfNode> elseIfBranches, List<Node> elseBranch) {
        super(line);
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseIfBranches = elseIfBranches;
        this.elseBranch = elseBranch;
    }

}
