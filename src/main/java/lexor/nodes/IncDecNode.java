package lexor.nodes;

public class IncDecNode extends Node {
    public final String name;                   // variable being incremented/decremented
    public final boolean isIncrement;           // true = ++, false = --
    public final boolean isPrefix;              // true = ++x, false = x++

    public IncDecNode(int line, String name, boolean isIncrement, boolean isPrefix) {
        super(line);
        this.name = name;
        this.isIncrement = isIncrement;
        this.isPrefix = isPrefix;
    }
}
