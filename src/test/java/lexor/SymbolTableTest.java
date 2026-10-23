package lexor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SymbolTableTest {
    private void assertError(LexorException error, LexorException.Phase phase, int line, String message) {
        assertEquals(phase, error.getPhase());
        assertEquals(line, error.getLine());
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }

    @Test
    void preservesDeclaredFloatTypeAcrossIntegerWrites() {
        SymbolTable symbols = new SymbolTable();
        symbols.declare("f", ValueType.FLOAT, 3, 3);
        assertInstanceOf(Float.class, symbols.getValue("f", 4));
        assertEquals(3.0f, symbols.getValue("f", 4));
        symbols.setValue("f", 7, 5);
        assertEquals(ValueType.FLOAT, symbols.getType("f"));
        assertInstanceOf(Float.class, symbols.getValue("f", 6));
        assertEquals(7.0f, symbols.getValue("f", 6));
    }

    static Stream<Arguments> invalidAssignments() {
        return Stream.of(
                Arguments.of(ValueType.INT, 3, 3.5f),
                Arguments.of(ValueType.INT, 3, "3"),
                Arguments.of(ValueType.FLOAT, 3.5f, true),
                Arguments.of(ValueType.FLOAT, 3.5f, 3.5d),
                Arguments.of(ValueType.CHAR, 'a', "a"),
                Arguments.of(ValueType.BOOL, true, "TRUE"),
                Arguments.of(ValueType.BOOL, true, 1));
    }

    @ParameterizedTest
    @MethodSource("invalidAssignments")
    void failedAssignmentPreservesValueAndType(ValueType type, Object initial, Object rejected) {
        SymbolTable symbols = new SymbolTable();
        symbols.declare("x", type, 3, initial);
        LexorException error = assertThrows(LexorException.class, () -> symbols.setValue("x", rejected, 8));
        assertError(error, LexorException.Phase.RUNTIME, 8, "Type mismatch");
        assertEquals(type, symbols.getType("x"));
        assertEquals(initial, symbols.getValue("x", 9));
    }

    static Stream<Float> nonFiniteValues() {
        return Stream.of(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
    }

    @ParameterizedTest
    @MethodSource("nonFiniteValues")
    void rejectsNonFiniteValuesWithoutChangingSymbols(float value) {
        SymbolTable symbols = new SymbolTable();
        symbols.declare("f", ValueType.FLOAT, 3, 2.5f);
        LexorException assignment = assertThrows(LexorException.class, () -> symbols.setValue("f", value, 4));
        assertError(assignment, LexorException.Phase.RUNTIME, 4, "must be finite");
        assertEquals(2.5f, symbols.getValue("f", 5));
        LexorException declaration = assertThrows(LexorException.class,
                () -> symbols.declare("invalid", ValueType.FLOAT, 6, value));
        assertError(declaration, LexorException.Phase.SEMANTIC, 6, "must be finite");
        assertFalse(symbols.isDeclared("invalid"));
    }

    @Test
    void acceptsFiniteFloatExtremesAndSignedZero() {
        SymbolTable symbols = new SymbolTable();
        symbols.declare("f", ValueType.FLOAT, 3, Float.MAX_VALUE);
        assertEquals(Float.MAX_VALUE, symbols.getValue("f", 4));
        symbols.setValue("f", Float.MIN_VALUE, 5);
        assertEquals(Float.MIN_VALUE, symbols.getValue("f", 6));
        symbols.setValue("f", -0.0f, 7);
        assertEquals(Float.floatToIntBits(-0.0f),
                Float.floatToIntBits((Float) symbols.getValue("f", 8)));
    }

    @Test
    void undeclaredAccessReportsRequestedPhaseAndLine() {
        SymbolTable symbols = new SymbolTable();
        LexorException type = assertThrows(LexorException.class,
                () -> symbols.getType("missing", LexorException.Phase.SEMANTIC, 11));
        assertError(type, LexorException.Phase.SEMANTIC, 11, "Undeclared variable 'missing'");
        LexorException read = assertThrows(LexorException.class, () -> symbols.getValue("missing", 12));
        assertError(read, LexorException.Phase.RUNTIME, 12, "Undeclared variable 'missing'");
        LexorException write = assertThrows(LexorException.class, () -> symbols.setValue("missing", 1, 13));
        assertError(write, LexorException.Phase.RUNTIME, 13, "Undeclared variable 'missing'");
        assertFalse(symbols.isDeclared("missing"));
    }

    @Test
    void duplicateDeclarationLeavesExistingEntryUnchanged() {
        SymbolTable symbols = new SymbolTable();
        symbols.declare("x", ValueType.INT, 3, 7);
        LexorException error = assertThrows(LexorException.class,
                () -> symbols.declare("x", ValueType.FLOAT, 4, 9.0f));
        assertError(error, LexorException.Phase.SEMANTIC, 4, "already declared");
        assertEquals(ValueType.INT, symbols.getType("x"));
        assertEquals(7, symbols.getValue("x", 5));
    }

    @Test
    void rejectsStringVariablesAndInvalidInitialValues() {
        SymbolTable symbols = new SymbolTable();
        LexorException string = assertThrows(LexorException.class,
                () -> symbols.declare("s", ValueType.STRING, 3, "text"));
        assertError(string, LexorException.Phase.SEMANTIC, 3, "printable literals");
        LexorException initial = assertThrows(LexorException.class,
                () -> symbols.declare("i", ValueType.INT, 4, false));
        assertError(initial, LexorException.Phase.SEMANTIC, 4, "Type mismatch");
        assertFalse(symbols.isDeclared("s"));
        assertFalse(symbols.isDeclared("i"));
    }

    @Test
    void rejectsNullValuesWithoutReplacingExistingValue() {
        SymbolTable symbols = new SymbolTable();
        symbols.declare("i", ValueType.INT, 3, 9);
        LexorException error = assertThrows(LexorException.class, () -> symbols.setValue("i", null, 4));
        assertError(error, LexorException.Phase.RUNTIME, 4, "Type mismatch");
        assertEquals(9, symbols.getValue("i", 5));
    }
}
