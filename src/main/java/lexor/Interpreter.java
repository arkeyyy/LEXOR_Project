package lexor;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import lexor.nodes.*;

import static lexor.LexorException.Phase.RUNTIME;

public class Interpreter {
    private final SymbolTable symbolTable;
    private final Scanner scanner;
    private final PrintStream output;

    public Interpreter(SymbolTable symbolTable) {
        this(symbolTable, System.in, System.out);
    }

    public Interpreter(SymbolTable symbolTable, InputStream input, PrintStream output) {
        this.symbolTable = symbolTable;
        this.scanner = new Scanner(input, StandardCharsets.UTF_8);
        this.output = output;
    }

    public void execute(ProgramNode program) {
        executeBlock(program.statements);
    }

    private void executeNode(Node node) {
        switch (node) {
            case DeclareNode n -> {
                Object value = n.value == null ? n.type.defaultValue() : evaluate(n.value);
                symbolTable.setValue(n.name, value, n.line);
            }
            case AssignNode n -> executeAssign(n);
            case PrintNode n -> executePrint(n);
            case ScanNode n -> executeScan(n);
            case IfNode n -> executeIf(n);
            case ForNode n -> executeFor(n);
            case RepeatNode n -> {
                while (asBoolean(evaluate(n.condition), n.condition.line)) executeBlock(n.body);
            }
            case IncDecNode n -> evaluateIncDec(n);
            default -> throw error(node.line, "Unknown node: " + node.getClass().getSimpleName());
        }
    }

    private Object executeAssign(AssignNode node) {
        Object value = evaluate(node.value);
        symbolTable.setValue(node.name, value, node.line);
        return symbolTable.getValue(node.name, node.line);
    }

    private void executePrint(PrintNode node) {
        StringBuilder text = new StringBuilder();
        for (Node part : node.parts) text.append(formatValue(evaluate(part)));
        output.print(text);
    }

    private String formatValue(Object value) {
        if (value instanceof Boolean b) return b ? "TRUE" : "FALSE";
        if (value instanceof Character c && c == '\0') return "";
        if (value instanceof Float f) return Float.toString(f);
        return String.valueOf(value);
    }

    private void executeScan(ScanNode node) {
        output.flush();
        if (!scanner.hasNextLine()) {
            throw error(node.line, "Expected input for SCAN, but reached end of input.");
        }
        String[] inputs = scanner.nextLine().split(",", -1);
        if (inputs.length != node.variables.size()) {
            throw error(node.line, "SCAN expected " + node.variables.size()
                    + " values, got " + inputs.length + ".");
        }

        List<Object> values = new ArrayList<>(inputs.length);
        for (int i = 0; i < inputs.length; i++) {
            String name = node.variables.get(i);
            ValueType type = symbolTable.getType(name, RUNTIME, node.line);
            String raw = type == ValueType.CHAR ? inputs[i] : inputs[i].trim();
            Object value;
            try {
                value = switch (type) {
                    case INT -> Integer.parseInt(raw);
                    case FLOAT -> Float.parseFloat(raw);
                    case CHAR -> {
                        if (raw.length() != 1) throw invalidInput(node.line, name, type, raw);
                        yield raw.charAt(0);
                    }
                    case BOOL -> {
                        if (raw.equals("TRUE")) yield true;
                        if (raw.equals("FALSE")) yield false;
                        throw invalidInput(node.line, name, type, raw);
                    }
                    default -> throw invalidInput(node.line, name, type, raw);
                };
            } catch (NumberFormatException e) {
                throw invalidInput(node.line, name, type, raw);
            }
            values.add(type.coerce(value, RUNTIME, node.line));
        }

        for (int i = 0; i < values.size(); i++) {
            symbolTable.setValue(node.variables.get(i), values.get(i), node.line);
        }
    }

    private LexorException invalidInput(int line, String name, ValueType type, String raw) {
        return error(line, "Invalid input '" + raw + "' for variable '" + name + "' of type " + type + ".");
    }

    private void executeIf(IfNode node) {
        if (asBoolean(evaluate(node.condition), node.condition.line)) {
            executeBlock(node.thenBranch);
            return;
        }
        for (IfNode elseIf : node.elseIfBranches) {
            if (asBoolean(evaluate(elseIf.condition), elseIf.condition.line)) {
                executeBlock(elseIf.thenBranch);
                return;
            }
        }
        if (node.elseBranch != null) executeBlock(node.elseBranch);
    }

    private void executeFor(ForNode node) {
        executeNode(node.initialization);
        while (asBoolean(evaluate(node.condition), node.condition.line)) {
            executeBlock(node.body);
            executeNode(node.update);
        }
    }

    private void executeBlock(List<Node> block) {
        for (Node node : block) executeNode(node);
    }

    private Object evaluate(Node node) {
        return switch (node) {
            case LiteralNode n -> n.value;
            case VariableNode n -> symbolTable.getValue(n.name, n.line);
            case AssignNode n -> executeAssign(n);
            case BinaryOpNode n -> evaluateBinary(n);
            case UnaryOpNode n -> evaluateUnary(n);
            case IncDecNode n -> evaluateIncDec(n);
            default -> throw error(node.line, "Cannot evaluate node: " + node.getClass().getSimpleName());
        };
    }

    private Object evaluateBinary(BinaryOpNode node) {
        Object left = evaluate(node.left);
        if (node.operator.equals("AND")) {
            return asBoolean(left, node.line) && asBoolean(evaluate(node.right), node.line);
        }
        if (node.operator.equals("OR")) {
            return asBoolean(left, node.line) || asBoolean(evaluate(node.right), node.line);
        }
        Object right = evaluate(node.right);
        return switch (node.operator) {
            case "+", "-", "*", "/", "%" -> arithmetic(left, right, node.operator, node.line);
            case ">" -> compareNumbers(left, right, node.line) > 0;
            case "<" -> compareNumbers(left, right, node.line) < 0;
            case ">=" -> compareNumbers(left, right, node.line) >= 0;
            case "<=" -> compareNumbers(left, right, node.line) <= 0;
            case "==" -> equalValues(left, right, node.line);
            case "<>" -> !equalValues(left, right, node.line);
            default -> throw error(node.line, "Unknown operator: " + node.operator);
        };
    }

    private Object arithmetic(Object left, Object right, String operator, int line) {
        if (left instanceof Integer a && right instanceof Integer b) {
            try {
                return switch (operator) {
                    case "+" -> Math.addExact(a, b);
                    case "-" -> Math.subtractExact(a, b);
                    case "*" -> Math.multiplyExact(a, b);
                    case "/" -> {
                        checkDivisor(b, line);
                        if (a == Integer.MIN_VALUE && b == -1) throw error(line, "INT overflow.");
                        yield a / b;
                    }
                    case "%" -> {
                        checkDivisor(b, line);
                        yield a % b;
                    }
                    default -> throw error(line, "Unknown operator: " + operator);
                };
            } catch (ArithmeticException e) {
                throw error(line, "INT overflow.");
            }
        }

        float a = asFloat(left, line);
        float b = asFloat(right, line);
        if (operator.equals("/") || operator.equals("%")) checkDivisor(b, line);
        float result = switch (operator) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> a / b;
            case "%" -> a % b;
            default -> throw error(line, "Unknown operator: " + operator);
        };
        return finite(result, line);
    }

    private void checkDivisor(double value, int line) {
        if (value == 0) throw error(line, "Division or modulo by zero.");
    }

    private int compareNumbers(Object left, Object right, int line) {
        double a = asDouble(left, line);
        double b = asDouble(right, line);
        return a < b ? -1 : a > b ? 1 : 0;
    }

    private boolean equalValues(Object left, Object right, int line) {
        if (left instanceof Boolean a && right instanceof Boolean b) return a.equals(b);
        if (left instanceof Character a && right instanceof Character b) return a.equals(b);
        return compareNumbers(left, right, line) == 0;
    }

    private Object evaluateUnary(UnaryOpNode node) {
        Object value = evaluate(node.operand);
        return switch (node.operator) {
            case "-" -> {
                if (value instanceof Integer i) {
                    try {
                        yield Math.negateExact(i);
                    } catch (ArithmeticException e) {
                        throw error(node.line, "INT overflow.");
                    }
                }
                yield finite(-asFloat(value, node.line), node.line);
            }
            case "+" -> {
                if (value instanceof Integer) yield value;
                yield finite(asFloat(value, node.line), node.line);
            }
            case "NOT" -> !asBoolean(value, node.line);
            default -> throw error(node.line, "Unknown unary operator: " + node.operator);
        };
    }

    private Object evaluateIncDec(IncDecNode node) {
        Object current = symbolTable.getValue(node.name, node.line);
        Object next = arithmetic(current, 1, node.isIncrement ? "+" : "-", node.line);
        symbolTable.setValue(node.name, next, node.line);
        return node.isPrefix ? next : current;
    }

    private boolean asBoolean(Object value, int line) {
        if (value instanceof Boolean b) return b;
        throw error(line, "Expected BOOL, got " + value + ".");
    }

    private float asFloat(Object value, int line) {
        if (value instanceof Integer i) return i.floatValue();
        if (value instanceof Float f) return finite(f, line);
        throw error(line, "Expected numeric value, got " + value + ".");
    }

    private double asDouble(Object value, int line) {
        if (value instanceof Integer i) return i.doubleValue();
        if (value instanceof Float f) return finite(f, line);
        throw error(line, "Expected numeric value, got " + value + ".");
    }

    private float finite(float value, int line) {
        if (!Float.isFinite(value)) throw error(line, "FLOAT value must be finite.");
        return value;
    }

    private LexorException error(int line, String message) {
        return new LexorException(RUNTIME, line, message);
    }
}
