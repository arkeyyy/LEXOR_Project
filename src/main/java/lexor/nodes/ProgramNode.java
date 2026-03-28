package lexor.nodes;
import java.util.List;

public class ProgramNode extends Node {
    public final List<Node> statements;

    public ProgramNode(List<Node> statements) {
        this.statements = statements;
    }
}
