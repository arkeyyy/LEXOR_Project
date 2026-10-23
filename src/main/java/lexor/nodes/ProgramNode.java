package lexor.nodes;
import java.util.List;

public class ProgramNode extends Node {
    public final List<Node> statements;

    public ProgramNode(int line, List<Node> statements) {
        super(line);
        this.statements = statements;
    }
}
