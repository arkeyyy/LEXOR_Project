package lexor.nodes;

import java.util.List;

public class ScanNode extends Node{
    public final List<String> variables;

    public ScanNode(List<String> variables) {
        this.variables = variables;
    }

}
