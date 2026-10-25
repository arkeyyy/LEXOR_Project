## **Language Specification of LEXOR Programming Language** 

## **Introduction** 

LEXOR is a strongly – typed programming language developed to teach Senior High School students the basics of programming. It was developed by a group of students enrolled in the Programming Languages course. LEXOR is a pure interpreter. 

Sample Program: 

## %% this is a sample program in LEXOR **SCRIPT AREA START SCRIPT** 

**DECLARE INT** x, y, z=5 **DECLARE CHAR** a_1=’n’ **DECLARE BOOL** t=”TRUE” x=y=4 a_1=’c’ %% this is a comment **PRINT:** x & t & z & $ & a_1 & [#] & “last” **END SCRIPT** 

Output of the sample program: 4TRUE5 c#last 

## **Language Grammar** 

Program Structure: 

- all codes starts with SCRIPT AREA 

- all codes are placed inside START SCRIPT and END SCRIPT 

- all variable declaration follow right after the START SCRIPT keyword. It cannot be placed anywhere. 

- all variable names are case sensitive and starts with letter or an underscore (_) and followed by a letter, underscore or digits. 

- every line contains a single statement 

- comments starts with double percent sign (%%) and it can be placed anywhere in the program 

- executable codes are placed after variable declaration 

- all reserved words are in capital letters and cannot be used as variable names 

- dollar sign($) signifies next line or carriage return 

- ampersand(&) serves as a concatenator 

- the square braces([]) are as escape code 

Data Types: 

1. INT – an ordinary number with no decimal part. It occupies 4 bytes in the memory. 

2. CHAR – a single symbol. 

3. BOOL – represents the literals true or false. 

4. FLOAT – a number with decimal part.  It occupies 4 bytes in the memory. 

Operators: 

Arithmetic operators ( ) - parenthesis *, /, %     - multiplication, division, modulo +, - - addition, subtraction >, < - greater than, lesser than >=, <=    - greater than or equal to, lesser than or equal to ==, <>     - equal, not equal 

Logical operators (<BOOL expression><LogicalOperator><BOOL expression>) AND - needs the two BOOL expression to be true to result to true, else false OR - if one of the BOOL expressions evaluates to true, returns true, else false NOT - the reverse value of the BOOL value 

Unary operator + - positive - - negative 

## **Sample Programs** 

1. A program with arithmetic operation 

## **SCRIPT AREA START SCRIPT** 

**DECLARE INT** xyz, abc=100 xyz= ((abc *5)/10 + 10) * -1 **PRINT:** [[] & xyz & []] **END SCRIPT** 

Output of the sample program: [-60] 

2. A program with logical operation **SCRIPT AREA START SCRIPT DECLARE INT** a=100, b=200, c=300 **DECLARE BOOL** d=”FALSE” d = (a < b AND c <>200) **PRINT:** d **END SCRIPT** 

Output of the sample program: TRUE 

Output statement: **PRINT - writes formatted output to the output device** 

Input statement: **SCAN – allow the user to input a value to a data type. Syntax: SCAN: <variableName>[,<variableName>]* Sample use:** 

**SCAN: x, y It means in the screen you have to input two values separated by comma(,)** 

**Control flow structures:** 

**1. Conditional** 

**a. if selection IF (<BOOL expression>) START IF <statement> … <statement> END IF** 

**b. if-else selection IF (<BOOL expression>) START IF <statement> … <statement> END IF ELSE START IF <statement> … <statement> END IF** 

**c. if-else with multiple alternatives** 

**IF (<BOOL expression>) START IF <statement> … <statement> END IF ELSE IF (<BOOL expression>) START IF <statement> … <statement>** 

**END IF ELSE START IF <statement>** 

**…** 

**<statement> END IF** 

## **2. Loop Control Flow Structures** 

**a. FOR (initialization, condition, update) START  FOR** 

**<statement>** 

**… <statement> END FOR** 

- **b. REPEAT WHEN (<BOOL expression>) START  REPEAT** 

**<statement>** 

**… <statement> END REPEAT** 

**Note: You may use any language to implement the interpreter except Python and Javascript.** 

## Implementation clarifications

These rules resolve behavior left unspecified above and document the retained increment/decrement extension.

### Source layout and literals

- Each statement, control-flow header, and block marker occupies its own line. Expressions and compound keywords cannot span lines.
- Blank lines and trailing `%%` comments are allowed. LF, CRLF, and CR are recognized as line endings. A final newline after `END SCRIPT` is optional.
- Source files use UTF-8. One BOM (U+FEFF) is accepted only at source offset zero and does not change line numbering. U+FEFF inside a literal remains part of its value; it is not otherwise treated as whitespace.
- Spaces and tabs may separate the words in a compound keyword. Keyword matching uses whole identifiers; all constituent reserved words are unavailable as variable names.
- Quote delimiters are ASCII `'` and `"`. The typographic quotes and collapsed layout in the original examples are presentation formatting.
- CHAR is one Java character (one UTF-16 code unit). Raw line endings are forbidden inside CHAR, string, and bracket-escape literals.
- STRING is a printable literal type, not a declaration type. Bracket escapes print their single enclosed character; `$` emits LF without an implicit trailing newline.

### Types, values, and operators

- INT is signed 32-bit, from `-2147483648` through `2147483647`. FLOAT uses Java's 32-bit floating-point representation.
- Source INT literals are decimal digit sequences; leading zeros remain decimal. Source FLOAT literals require digits on both sides of a decimal point, such as `0.5`. A sign is a unary operator. Exponents, hexadecimal notation, type suffixes, digit separators, `.5`, and `5.` are not source literal syntax. INT conversion retains Java's Unicode decimal-digit support; FLOAT conversion requires Java floating-point syntax and does not accept Unicode digits.
- A unary minus immediately before the magnitude `2147483648` forms the minimum INT, including leading zeros. Bare `2147483648` and `-(2147483648)` are invalid positive literals.
- Uninitialized INT, FLOAT, CHAR, and BOOL variables default to `0`, `0.0`, the null character, and `FALSE`. Printing the null character produces no text.
- Assignment accepts matching types and INT-to-FLOAT promotion. Every assignment preserves the target's declared type. Chained assignments evaluate from right to left.
- Arithmetic requires numeric operands. INT division truncates toward zero; mixed arithmetic produces FLOAT. Overflow is an error, including unary negation and `INT_MIN / -1`. `INT_MIN % -1` is zero.
- Division and modulo reject a zero divisor for both numeric types. NaN, Infinity, and non-finite results are errors. Finite FLOAT underflow follows Java float rounding.
- Numeric comparisons allow mixed INT/FLOAT operands. Comparison uses exact widening to `double`, without changing the variables' storage types. Positive and negative zero compare equal.
- BOOL and CHAR support only same-type `==` and `<>`. STRING comparisons and BOOL/CHAR ordering are invalid.
- `AND` and `OR` evaluate left to right and skip the right operand when its value is unnecessary. Semantic analysis checks both operands regardless of runtime evaluation.
- The retained `++` and `--` extensions accept INT/FLOAT variables. Prefix returns the updated value; postfix returns the old value. Both support standalone statements and FOR updates. Overflow leaves the target unchanged.
- FLOAT output uses `Float.toString`, independent of locale and without fixed decimal rounding. Scientific notation is permitted. BOOL output is uppercase `TRUE` or `FALSE`.
- `REPEAT WHEN` checks its condition before each iteration, so its body may execute zero times.

### Input and diagnostics

- SCAN reads one input line with exactly one comma-separated field per target. It validates all fields before assigning any values.
- Numeric and BOOL fields ignore surrounding whitespace. BOOL input is exactly `TRUE` or `FALSE` after trimming.
- SCAN uses Java's decimal `Integer.parseInt` and `Float.parseFloat` conversions. INT allows an optional sign and leading zeros. FLOAT input also allows exponents (`1e2`), hexadecimal floats (`0x1.8p1`), leading/trailing decimal points, and `f`/`d` suffixes. Non-finite values are rejected. This input syntax is broader than source literals. See the Java 23 [Integer](https://docs.oracle.com/en/java/javase/23/docs/api/java.base/java/lang/Integer.html#parseInt(java.lang.String)) and [Float](https://docs.oracle.com/en/java/javase/23/docs/api/java.base/java/lang/Float.html#parseFloat(java.lang.String)) conversion documentation.
- CHAR input preserves whitespace and requires exactly one character. A single space is valid; an empty field or multiple characters are invalid.
- End-of-input, read failures (including failures after partial input), wrong field counts, invalid values, and numeric range errors produce runtime diagnostics at the SCAN statement's line and leave all its targets unchanged.
- PRINT checks output health, including flushing, after each statement. Write or flush failures produce runtime diagnostics at the PRINT statement's line.
- Language errors report their phase and a one-based source line. The command-line interface returns a nonzero exit code for failures.
- The embedding API `Main.run(String[], InputStream, PrintStream, PrintStream)` flushes output and diagnostics on every exit and never closes caller streams. Consecutive runs sharing LF/CRLF input consume only their own SCAN lines.

### Complexity limits

- Parsing permits at most 128 simultaneously active nesting levels, shared across control statements (`IF`, `FOR`, `REPEAT WHEN`), assignment nodes in chains, recursive unary `+`/`-`/`NOT`, and expression parentheses. A control header's required parentheses do not add another level. Sibling statements and ELSE IF alternatives do not accumulate nesting.
- The maximum AST depth is 256, counting the supplied root as depth one and each parent-to-child edge as another level. For a complete program, the program and statement nodes count too: a PRINT of 254 terms joined by `+` has depth 256. Parentheses do not create AST nodes, but long left-associated expressions do.
- Depth is checked iteratively before semantic analysis or execution. Public tree entrypoints also validate manually constructed ASTs; `inferType` counts the supplied expression as its root. Parser limits report `SyntaxError`; tree limits report the phase of the entrypoint that rejects the tree, with a source line.
- These limits constrain source structure only. Script execution has no timer or iteration limit.

### Native terminal completion

- A Windows session atomically records its completed exit code before displaying the completion message and waiting for Enter. Enter and closing the completed window preserve codes `0`, `1`, and `2`. Closing a running script retains its process interruption status.
- Failure to publish completion is a visible session infrastructure error with code `2`. Cleanup attempts every owned protocol file, including temporary status files, without deleting unrelated files or replacing a completed result. Leftovers are reported on launcher stderr.
- The launcher keeps shell bootstrap files in local temporary storage and supports UNC working directories using Windows [`pushd`/`popd`](https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/pushd) with command extensions enabled.
- Run `mvnw.cmd verify` (Windows) or `sh mvnw verify` (macOS/Linux) for unit and packaged-JAR integration tests. Native window tests are opt-in through `tools/windows-smoke.ps1`; see the README for the optional UNC check.

### Executable examples

The following files preserve the original examples using executable source layout and ASCII quotes:

- [Introduction example](examples/specification-sample.lxr): outputs `4TRUE5`, LF, then `c#last`.
- [Arithmetic example](examples/specification-arithmetic.lxr): outputs `[-60]`.
- [Logical example](examples/specification-logic.lxr): outputs `TRUE`.
- [Increment/decrement extension](examples/inc-dec.lxr): preserves the previous embedded demo.

