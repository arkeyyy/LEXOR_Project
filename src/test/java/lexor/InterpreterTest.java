package lexor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Stream;
import lexor.nodes.ProgramNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class InterpreterTest {
    private record Execution(ProgramNode program, SymbolTable symbols,
                             Interpreter interpreter, ByteArrayOutputStream output) {
        void execute() {
            interpreter.execute(program);
        }

        String text() {
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private Execution prepare(String body, String input) {
        String source = "SCRIPT AREA\nSTART SCRIPT\n" + body + "\nEND SCRIPT\n";
        ProgramNode program = new Parser(new Lexer(source).tokenize()).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(program);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Interpreter interpreter = new Interpreter(analyzer.getSymbolTable(),
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, false, StandardCharsets.UTF_8));
        return new Execution(program, analyzer.getSymbolTable(), interpreter, output);
    }

    private Execution run(String body) {
        Execution execution = prepare(body, "");
        execution.execute();
        return execution;
    }

    private LexorException assertRuntimeError(Execution execution, int line) {
        LexorException error = assertThrows(LexorException.class, execution::execute);
        assertEquals(LexorException.Phase.RUNTIME, error.getPhase());
        assertEquals(line, error.getLine(), error.getMessage());
        return error;
    }

    @Test
    void preservesDeclaredTypesInInitializationAssignmentAndChains() {
        Execution execution = run("""
                DECLARE INT i
                DECLARE FLOAT initialized=3, assigned, chained
                assigned=3
                chained=i=3
                PRINT: initialized/2 & "|" & assigned/2 & "|" & chained/2 & "|" & i
                """);
        assertEquals("1.5|1.5|1.5|3", execution.text());
        assertInstanceOf(Integer.class, execution.symbols().getValue("i"));
        for (String name : new String[] {"initialized", "assigned", "chained"}) {
            assertInstanceOf(Float.class, execution.symbols().getValue(name));
            assertEquals(3.0f, execution.symbols().getValue(name));
        }
    }

    @Test
    void evaluatesChainedAssignmentRightHandSideOnce() {
        Execution execution = run("""
                DECLARE INT a, b, c=7
                a=b=c++
                PRINT: a & "|" & b & "|" & c
                """);
        assertEquals("7|7|8", execution.text());
    }

    @Test
    void usesTypeDefaultsAndPrintsEscapesWithoutImplicitNewline() {
        Execution execution = run("""
                DECLARE INT i
                DECLARE FLOAT f
                DECLARE CHAR c
                DECLARE BOOL b
                PRINT: i & "|" & f & "|" & c & "|" & b & $ & [[] & [#] & []]
                """);
        assertEquals("0|0.0||FALSE\n[#]", execution.text());
        assertEquals('\0', execution.symbols().getValue("c"));
    }

    @Test
    void floatFormattingPreservesRepresentedValuesRegardlessOfLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            Execution execution = run("""
                    PRINT: 3000000000.0 & "|" & 0.0001 & "|" & -1.25 & "|" & 2.0 & "|" & 1.234567
                    """);
            assertEquals("3.0E9|1.0E-4|-1.25|2.0|1.234567", execution.text());
        } finally {
            Locale.setDefault(previous);
        }
    }

    static Stream<Arguments> numericExpressions() {
        return Stream.of(
                Arguments.of("-5/2", "-2"),
                Arguments.of("5/-2", "-2"),
                Arguments.of("-5%2", "-1"),
                Arguments.of("5%-2", "1"),
                Arguments.of("-2147483648%-1", "0"),
                Arguments.of("-2147483648", "-2147483648"),
                Arguments.of("2147483647", "2147483647"),
                Arguments.of("+(-5)", "-5"),
                Arguments.of("5.0/2", "2.5"),
                Arguments.of("-5.0%2", "-1.0"),
                Arguments.of("1+2.5*2", "6.0"),
                Arguments.of("(1+2)*3", "9"),
                Arguments.of("0.0==-0.0", "TRUE"),
                Arguments.of("0.0>-0.0", "FALSE"),
                Arguments.of("0.0>=-0.0", "TRUE"),
                Arguments.of("1<1.5", "TRUE"),
                Arguments.of("16777217>16777216.0", "TRUE"),
                Arguments.of("2147483647<2147483648.0", "TRUE"),
                Arguments.of("1==1.0", "TRUE"),
                Arguments.of("'a'=='a'", "TRUE"),
                Arguments.of("'a'<>'b'", "TRUE"),
                Arguments.of("\"TRUE\"<>\"FALSE\"", "TRUE"));
    }

    @ParameterizedTest
    @MethodSource("numericExpressions")
    void evaluatesNumericAndEqualityExpressions(String expression, String expected) {
        assertEquals(expected, run("PRINT: " + expression).text());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2147483647+1", "-2147483648-1", "50000*50000",
            "-(-2147483648)", "-2147483648/-1", "1/0", "1%0",
            "1.0/0", "1/0.0", "1.0%-0.0", "0.0/0.0",
            "300000000000000000000000000000000000000.0*2"
    })
    void reportsArithmeticFailuresAtOperatorLine(String expression) {
        Execution execution = prepare("PRINT: " + expression, "");
        assertRuntimeError(execution, 3);
        assertEquals("", execution.text());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2147483647+1", "-2147483648/-1", "1%0"})
    void failedAssignmentKeepsPreviousTargetValue(String expression) {
        Execution execution = prepare("DECLARE INT x=7\nx=" + expression, "");
        assertRuntimeError(execution, 4);
        assertEquals(7, execution.symbols().getValue("x"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"++x", "x++"})
    void incrementOverflowLeavesTargetUnchanged(String expression) {
        Execution execution = prepare("DECLARE INT x=2147483647\nPRINT: " + expression, "");
        assertRuntimeError(execution, 4);
        assertEquals(Integer.MAX_VALUE, execution.symbols().getValue("x"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--x", "x--"})
    void decrementOverflowLeavesTargetUnchanged(String expression) {
        Execution execution = prepare("DECLARE INT x=-2147483648\nPRINT: " + expression, "");
        assertRuntimeError(execution, 4);
        assertEquals(Integer.MIN_VALUE, execution.symbols().getValue("x"));
    }

    @Test
    void floatOverflowLeavesAssignmentTargetUnchanged() {
        Execution execution = prepare("""
                DECLARE FLOAT x=2.0
                x=300000000000000000000000000000000000000.0*2
                """, "");
        assertRuntimeError(execution, 4);
        assertEquals(2.0f, execution.symbols().getValue("x"));
    }

    @Test
    void skipsUnneededLogicalOperandsAndTheirSideEffects() {
        Execution execution = run("""
                DECLARE INT x=0
                PRINT: "FALSE" AND (1/0==0) & "|" & "TRUE" OR (1%0==0) & "|"
                PRINT: "FALSE" AND (x++>0) & "|" & "TRUE" OR (++x>0) & "|" & x
                """);
        assertEquals("FALSE|TRUE|FALSE|TRUE|0", execution.text());
        assertEquals(0, execution.symbols().getValue("x"));
    }

    @Test
    void evaluatesRequiredLogicalOperandsOnceInOrder() {
        Execution execution = run("""
                DECLARE INT x=0
                PRINT: "TRUE" AND (++x==1) & "|" & "FALSE" OR (x++==1) & "|" & x
                """);
        assertEquals("TRUE|TRUE|2", execution.text());
    }

    @Test
    void preservesPrefixPostfixResultsAndLeftToRightExpressionOrder() {
        Execution execution = run("""
                DECLARE INT x=1, y
                DECLARE FLOAT f=2.5
                y=x++ + ++x
                PRINT: y & "|" & x & "|" & f++ & "|" & --f
                """);
        assertEquals("4|3|2.5|2.5", execution.text());
        assertInstanceOf(Float.class, execution.symbols().getValue("f"));
    }

    @Test
    void runsNestedLoopsAndChoosesFirstMatchingElseIf() {
        Execution execution = run("""
                DECLARE INT i, j, total=0
                FOR (i=0, i<2, i++)
                START FOR
                FOR (j=0, j<3, ++j)
                START FOR
                total=total+1
                END FOR
                END FOR
                IF (total==0)
                START IF
                PRINT: "wrong"
                END IF
                ELSE IF (total==6)
                START IF
                IF ("TRUE")
                START IF
                PRINT: "six"
                END IF
                END IF
                ELSE IF ("TRUE")
                START IF
                PRINT: "later"
                END IF
                ELSE
                START IF
                PRINT: "else"
                END IF
                PRINT: "|" & total
                """);
        assertEquals("six|6", execution.text());
    }

    @Test
    void repeatChecksConditionBeforeFirstAndEveryLaterIteration() {
        Execution execution = run("""
                DECLARE INT x=0
                REPEAT WHEN ("FALSE")
                START REPEAT
                x=99
                END REPEAT
                REPEAT WHEN (x<3)
                START REPEAT
                ++x
                END REPEAT
                PRINT: x
                """);
        assertEquals("3", execution.text());
    }

    @Test
    void forFalseConditionExecutesInitializationOnlyAndElseBranchWorks() {
        Execution execution = run("""
                DECLARE INT x=0
                FOR (x=1, "FALSE", x++)
                START FOR
                x=99
                END FOR
                IF ("FALSE")
                START IF
                PRINT: "wrong"
                END IF
                ELSE
                START IF
                PRINT: x
                END IF
                """);
        assertEquals("1", execution.text());
    }

    @Test
    void loopConditionFailureKeepsItsOwnSourceLineAfterBodyExecution() {
        Execution execution = prepare("""
                DECLARE INT x=1
                REPEAT WHEN (1/x>0)
                START REPEAT
                x=0
                END REPEAT
                """, "");
        assertRuntimeError(execution, 4);
    }

    @Test
    void scanParsesAllTypesPreservingSingleSpaceCharacter() {
        Execution execution = prepare("""
                DECLARE INT i
                DECLARE FLOAT f
                DECLARE BOOL b
                DECLARE CHAR c
                SCAN: i, f, b, c
                PRINT: i & "|" & f & "|" & b & "|" & c
                """, " -5 , 2.5 , TRUE , \n");
        execution.execute();
        assertEquals("-5|2.5|TRUE| ", execution.text());
        assertInstanceOf(Integer.class, execution.symbols().getValue("i"));
        assertInstanceOf(Float.class, execution.symbols().getValue("f"));
        assertEquals(' ', execution.symbols().getValue("c"));
    }

    @Test
    void scanSupportsMultipleLinesAndFinalInputWithoutNewline() {
        Execution execution = prepare("""
                DECLARE INT a, b
                SCAN: a
                SCAN: b
                PRINT: a+b
                """, "2\n3");
        execution.execute();
        assertEquals("5", execution.text());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1\n", "1,2,3\n", "1,\n", ",2\n", "1,wrong\n", "1,2147483648\n"})
    void invalidScanLeavesEveryTargetUnchanged(String input) {
        Execution execution = prepare("DECLARE INT a=7, b=8\nSCAN: a,b", input);
        assertRuntimeError(execution, 4);
        assertEquals(7, execution.symbols().getValue("a"));
        assertEquals(8, execution.symbols().getValue("b"));
    }

    static Stream<Arguments> invalidTypedInput() {
        return Stream.of(
                Arguments.of("CHAR", "'z'", "", 'z'),
                Arguments.of("CHAR", "'z'", "ab", 'z'),
                Arguments.of("CHAR", "'z'", " x ", 'z'),
                Arguments.of("BOOL", "\"TRUE\"", "true", true),
                Arguments.of("BOOL", "\"TRUE\"", "0", true),
                Arguments.of("FLOAT", "2.0", "NaN", 2.0f),
                Arguments.of("FLOAT", "2.0", "Infinity", 2.0f),
                Arguments.of("FLOAT", "2.0", "-Infinity", 2.0f),
                Arguments.of("FLOAT", "2.0", "1e100", 2.0f),
                Arguments.of("FLOAT", "2.0", "invalid", 2.0f));
    }

    @ParameterizedTest
    @MethodSource("invalidTypedInput")
    void invalidTypedScanIsAtomic(String type, String initial, String raw, Object expected) {
        Execution execution = prepare("DECLARE INT a=7\nDECLARE " + type + " b=" + initial
                + "\nSCAN: a,b", "9," + raw + "\n");
        assertRuntimeError(execution, 5);
        assertEquals(7, execution.symbols().getValue("a"));
        assertEquals(expected, execution.symbols().getValue("b"));
    }

    @Test
    void leavesCallerStreamsOpenAndFlushesPromptBeforeReadingInput() {
        class Output extends ByteArrayOutputStream {
            boolean closed;
            boolean flushed;

            @Override
            public void close() {
                closed = true;
            }

            @Override
            public void flush() {
                flushed = true;
            }
        }
        Output output = new Output();
        class Input extends ByteArrayInputStream {
            boolean closed;

            Input() {
                super("4\n".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                assertTrue(output.flushed, "Prompt must be flushed before SCAN blocks.");
                return super.read(buffer, offset, length);
            }

            @Override
            public void close() throws IOException {
                closed = true;
                super.close();
            }
        }
        Input input = new Input();
        ProgramNode program = new Parser(new Lexer("""
                SCRIPT AREA
                START SCRIPT
                DECLARE INT x
                PRINT: "number: "
                SCAN: x
                PRINT: x
                END SCRIPT
                """).tokenize()).parse();
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(program);
        new Interpreter(analyzer.getSymbolTable(), input,
                new PrintStream(output, false, StandardCharsets.UTF_8)).execute(program);
        assertEquals("number: 4", output.toString(StandardCharsets.UTF_8));
        assertFalse(input.closed);
        assertFalse(output.closed);
    }
}
