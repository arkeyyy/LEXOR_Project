package lexor.nodes;

public class VariableNode extends Node{
    public final String name;

    public VariableNode(int line, String name) {
        super(line);
        this.name = name;
    }
}
