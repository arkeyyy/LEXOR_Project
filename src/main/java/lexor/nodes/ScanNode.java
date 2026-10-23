package lexor.nodes;

import java.util.List;

public class ScanNode extends Node{
    public final List<String> variables;

    public ScanNode(int line, List<String> variables) {
        super(line);
        this.variables = variables;
    }

}
