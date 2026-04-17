package lexor;

import lexor.nodes.*;
import java.util.List;
import java.util.Scanner;

public class Interpreter {
    private final SymbolTable symbolTable;
    private final Scanner scanner = new Scanner(System.in);

    // Takes the SymbolTable already built by SemanticAnalyzer
    public Interpreter(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    // Entry Point /////////////////////////////////////////////////////////////////////////////

    public void execute(ProgramNode program) {
        for (Node statement : program.statements) {
            executeNode(statement);
        }
    }

    // Dispatcher /////////////////////////////////////////////////////////////////////////////

    private void executeNode(Node node) {
        switch (node) {
            case DeclareNode n -> executeDeclare(n);
            case AssignNode   n -> executeAssign(n);
            case PrintNode    n -> executePrint(n);
            case ScanNode     n -> executeScan(n);
            case IfNode       n -> executeIf(n);
            case ForNode      n -> executeFor(n);
            case RepeatNode n -> executeRepeat(n);
            default -> throw new RuntimeException(
                    "Unknown node: " + node.getClass().getSimpleName()
            );
        }
    }

    // Declare ////////////////////////////////////////////////////////////////////////////////

    private void executeDeclare(DeclareNode node) {
        Object value = node.value != null
                ? evaluate(node.value)
                : getDefault(node.type);
        symbolTable.setValue(node.name, value);
    }

    private Object getDefault(String type) {
        return switch (type) {
            case "INT"   -> 0;
            case "FLOAT" -> 0.0f;
            case "CHAR"  -> '\0';
            case "BOOL"  -> false;
            default      -> null;
        };
    }

    // Assign ///////////////////////////////////////////////////////////////////////////////////

    private void executeAssign(AssignNode node) {
        Object value = evaluate(node.value);
        symbolTable.setValue(node.name, value);

        // Handle chained assignment: x = y = 4
        // The right side AssignNode already executed above via evaluate(),
        // but AssignNode as a value needs special handling
        if (node.value instanceof AssignNode inner) {
            symbolTable.setValue(node.name, symbolTable.getValue(inner.name));
        }
    }

    // Print ////////////////////////////////////////////////////////////////////////////////////

    private void executePrint(PrintNode node) {
        StringBuilder output = new StringBuilder();
        for (Node part : node.parts) {
            Object value = evaluate(part);
            output.append(formatValue(value));
        }
        System.out.print(output);
    }

    private String formatValue(Object value) {
        if (value instanceof Boolean b) return b ? "TRUE" : "FALSE";
        if (value instanceof Character c && c == '\0') return "";
        return String.valueOf(value);
    }

    // Scan  ////////////////////////////////////////////////////////////////////////////////////////

    private void executeScan(ScanNode node) {
        // System.out.print("Input: ");
        String[] inputs = scanner.nextLine().split(",");

        for (int i = 0; i < node.variables.size(); i++) {
            String varName = node.variables.get(i);
            String varType = symbolTable.getType(varName);
            String raw     = inputs[i].trim();

            Object value = switch (varType) {
                case "INT"   -> Integer.parseInt(raw);
                case "FLOAT" -> Float.parseFloat(raw);
                case "CHAR"  -> raw.charAt(0);
                case "BOOL"  -> raw.equalsIgnoreCase("TRUE");
                default      -> raw;
            };

            symbolTable.setValue(varName, value);
        }
    }

    // If ///////////////////////////////////////////////////////////////////////////////////

    private void executeIf(IfNode node) {
        boolean condition = (boolean) evaluate(node.condition);

        if (condition) {
            executeBlock(node.thenBranch);
            return;
        }

        for (IfNode elseIf : node.elseIfBranches) {
            if ((boolean) evaluate(elseIf.condition)) {
                executeBlock(elseIf.thenBranch);
                return;
            }
        }

        if (node.elseBranch != null) {
            executeBlock(node.elseBranch);
        }
    }

    // For ///////////////////////////////////////////////////////////////////////////////////

    private void executeFor(ForNode node) {
        executeNode(node.initialization);

        while ((boolean) evaluate(node.condition)) {
            executeBlock(node.body);
            executeNode(node.update);
        }
    }

    // Repeat ////////////////////////////////////////////////////////////////////////////////

    private void executeRepeat(RepeatNode node) {
        while ((boolean) evaluate(node.condition)) {
            executeBlock(node.body);
        }
    }

    // Block ////////////////////////////////////////////////////////////////////////////////

    private void executeBlock(List<Node> block) {
        for (Node node : block) executeNode(node);
    }

    // Evaluator /////////////////////////////////////////////////////////////////////////////

    private Object evaluate(Node node) {
        return switch (node) {
            case LiteralNode  n -> n.value;
            case VariableNode n -> symbolTable.getValue(n.name);
            case AssignNode   n -> { executeAssign(n); yield symbolTable.getValue(n.name); }
            case BinaryOpNode n -> evaluateBinary(n);
            case UnaryOpNode  n -> evaluateUnary(n);
            default -> throw new RuntimeException(
                    "Cannot evaluate node: " + node.getClass().getSimpleName()
            );
        };
    }

    // Binary Expressions ///////////////////////////////////////////////////////////////////////

    private Object evaluateBinary(BinaryOpNode node) {
        Object left  = evaluate(node.left);
        Object right = evaluate(node.right);

        return switch (node.operator) {
            case "+"  -> addValues(left, right);
            case "-"  -> subtractValues(left, right);
            case "*"  -> multiplyValues(left, right);
            case "/"  -> divideValues(left, right);
            case "%"  -> moduloValues(left, right);
            case ">"  -> compareValues(left, right) > 0;
            case "<"  -> compareValues(left, right) < 0;
            case ">=" -> compareValues(left, right) >= 0;
            case "<=" -> compareValues(left, right) <= 0;
            case "==" -> compareValues(left, right) == 0;
            case "<>" -> compareValues(left, right) != 0;
            case "AND"-> (boolean) left && (boolean) right;
            case "OR" -> (boolean) left || (boolean) right;
            default   -> throw new RuntimeException("Unknown operator: " + node.operator);
        };
    }

    private Object addValues(Object left, Object right) {
        if (left instanceof Float || right instanceof Float)
            return toFloat(left) + toFloat(right);
        return toInt(left) + toInt(right);
    }

    private Object subtractValues(Object left, Object right) {
        if (left instanceof Float || right instanceof Float)
            return toFloat(left) - toFloat(right);
        return toInt(left) - toInt(right);
    }

    private Object multiplyValues(Object left, Object right) {
        if (left instanceof Float || right instanceof Float)
            return toFloat(left) * toFloat(right);
        return toInt(left) * toInt(right);
    }

    private Object divideValues(Object left, Object right) {
        if (left instanceof Float || right instanceof Float)
            return toFloat(left) / toFloat(right);
        int divisor = toInt(right);
        if (divisor == 0) throw new RuntimeException("Division by zero.");
        return toInt(left) / divisor;
    }

    private Object moduloValues(Object left, Object right) {
        if (left instanceof Float || right instanceof Float)
            return toFloat(left) % toFloat(right);
        return toInt(left) % toInt(right);
    }

    private int compareValues(Object left, Object right) {
        if (left instanceof Integer l && right instanceof Integer r) return Integer.compare(l, r);
        if (left instanceof Float   || right instanceof Float)       return Float.compare(toFloat(left), toFloat(right));
        if (left instanceof Boolean l && right instanceof Boolean r)  return Boolean.compare(l, r);
        if (left instanceof Character l && right instanceof Character r) return Character.compare(l, r);
        throw new RuntimeException("Cannot compare " + left + " and " + right);
    }

    // Unary Expressions ///////////////////////////////////////////////////////////////////////////////

    private Object evaluateUnary(UnaryOpNode node) {
        Object operand = evaluate(node.operand);
        return switch (node.operator) {
            case "-" -> operand instanceof Float f ? -f : -toInt(operand);
            case "+" -> operand;
            case "NOT" -> !(boolean) operand;
            default  -> throw new RuntimeException("Unknown unary operator: " + node.operator);
        };
    }

    // Type Helpers /////////////////////////////////////////////////////////////////////////////////////

    private int toInt(Object value) {
        if (value instanceof Integer i) return i;
        if (value instanceof Float   f) return f.intValue();
        throw new RuntimeException("Cannot convert to INT: " + value);
    }

    private float toFloat(Object value) {
        if (value instanceof Float   f) return f;
        if (value instanceof Integer i) return i.floatValue();
        throw new RuntimeException("Cannot convert to FLOAT: " + value);
    }
}
