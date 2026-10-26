<div align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=A96045&height=100" alt="LEXOR banner" />
  <h1>LEXOR</h1>
  <p><strong>A small language. A complete interpreter.</strong></p>
  <p>Write readable <code>.lxr</code> scripts with typed variables, structured control flow, and interactive console I/O.</p>
  <p>
    <img src="https://img.shields.io/badge/Java-23%2B-D8224A?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 23 or later" />
    <img src="https://img.shields.io/badge/Build-Maven_Wrapper-A96045?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Built with Maven Wrapper" />
    <img src="https://img.shields.io/badge/Source-.lxr-161616?style=for-the-badge" alt="LEXOR source files use .lxr" />
    <img src="https://img.shields.io/badge/Course-CS322-161616?style=for-the-badge" alt="CS322 Programming Languages" />
  </p>
  <p>
    <a href="#quick-start">Quick start</a> &nbsp;·&nbsp;
    <a href="#running-scripts">Running scripts</a> &nbsp;·&nbsp;
    <a href="#language-guide">Language guide</a> &nbsp;·&nbsp;
    <a href="#examples">Examples</a> &nbsp;·&nbsp;
    <a href="#verification">Verification</a>
  </p>
</div>

---

LEXOR is a Java interpreter developed for **CS322 — Programming Languages**. It takes source code through lexical analysis, parsing, semantic checks, and execution, making the stages of a language implementation concrete.

| Capability | What you can write |
| :--- | :--- |
| **Typed variables** | `INT`, `FLOAT`, `CHAR`, and `BOOL`, with declarations and chained assignments |
| **Expressions** | Arithmetic, comparisons, short-circuit logic, and prefix/postfix `++` and `--` |
| **Control flow** | `IF` / `ELSE IF` / `ELSE`, `FOR`, and `REPEAT WHEN`, including nested blocks |
| **Interactive I/O** | `SCAN` for typed input; `PRINT` for text, values, and formatting |
| **Diagnostics** | Errors with a phase and source line, checked arithmetic, and atomic SCAN assignment |
| **Run modes** | A native Windows terminal or console execution on Windows, macOS, and Linux |

```text
SCRIPT AREA
START SCRIPT
    DECLARE INT answer = 6 * 7
    PRINT: "Hello, LEXOR! The answer is " & answer & $
END SCRIPT
```

```text
Hello, LEXOR! The answer is 42
```

## Quick start

**Requirements:** Install JDK **23 or later** and point `JAVA_HOME` to it. The included Maven Wrapper downloads Maven **3.9.11** and build dependencies on its first run.

### Windows

```powershell
.\mvnw.cmd verify
java -jar target/Lexor_Project-1.0-SNAPSHOT.jar
```

Choose a `.lxr` file in the picker. LEXOR opens a separate terminal for input and output, then shows the exit code and waits at `Press Enter to close...`. Cancelling the picker exits successfully.

### macOS and Linux

```sh
sh mvnw verify
java -jar target/Lexor_Project-1.0-SNAPSHOT.jar examples/specification-sample.lxr
```

Execution uses the current terminal. A script path is required on macOS, Linux, and headless systems.

> **Source files:** Save scripts as UTF-8 with the `.lxr` extension. One leading BOM is supported. Quote paths containing spaces.

## Running scripts

Put options **before** the script path.

| Option | Behavior |
| :--- | :--- |
| `--console` | Use the existing terminal, including redirected input and automation |
| `--debug` | Write tokens, parser/semantic information, and error traces to stderr |
| `--help` | Show usage without opening a window or requiring a script |

```powershell
# Open a Windows terminal directly, without the picker
java -jar target/Lexor_Project-1.0-SNAPSHOT.jar myprogram.lxr

# Run in the current terminal
java -jar target/Lexor_Project-1.0-SNAPSHOT.jar --console myprogram.lxr

# Pipe input into the script
"3" | java -jar target/Lexor_Project-1.0-SNAPSHOT.jar --console myprogram.lxr

# Inspect execution or display usage
java -jar target/Lexor_Project-1.0-SNAPSHOT.jar --console --debug "examples/inc-dec.lxr"
java -jar target/Lexor_Project-1.0-SNAPSHOT.jar --help
```

Console mode sends only script output to stdout and diagnostics to stderr. It does not display a picker, completion message, or closing prompt. On Windows, `--debug` without a script opens the picker and enables tracing in the new terminal.

| Exit code | Meaning |
| :---: | :--- |
| **0** | Successful execution, help, or picker cancellation |
| **1** | Language or execution error |
| **2** | Invalid arguments, unreadable source, or terminal infrastructure failure |

<details>
<summary><strong>Windows terminal behavior and path support</strong></summary>

- Each launch runs one script in the original working directory, including UNC network shares.
- Closing a running script's terminal stops it and returns its process interruption status. Stopping the launcher also terminates its owned terminal session.
- Completion is recorded atomically **before** the closing prompt appears. Pressing Enter or closing the completed window preserves the script's exit code.
- Failure to record completion is an infrastructure error. Launch failures also appear in a dialog.
- Cleanup attempts every owned temporary file, preserves unrelated files, and reports leftovers on launcher stderr without replacing a completed result.
- Running `lexor.Main` from an IDE or with `java -cp target/classes lexor.Main` uses the same Windows flow.

If a Windows JDK misreads non-ASCII paths supplied on the command line, select the script through the picker. The windowed session preserves Unicode paths and input.

</details>

<details>
<summary><strong>Embedding LEXOR in another Java program</strong></summary>

Use `Main.run(String[], InputStream, PrintStream, PrintStream)` to execute a script without terminating the JVM.

The caller retains ownership of every stream. LEXOR leaves them open and flushes output and diagnostics on every return. Consecutive runs can share LF/CRLF input without consuming each other's lines. SCAN read failures and PRINT write/flush failures produce runtime diagnostics at the statement's source line.

</details>

## Language guide

A program begins with `SCRIPT AREA`, followed by `START SCRIPT` and `END SCRIPT`. Put declarations immediately after `START SCRIPT`, before executable statements. Each statement, control-flow header, and block marker occupies its own line; blank lines and `%%` comments are allowed.

| Type | Example declaration | Default value |
| :--- | :--- | :--- |
| `INT` | `DECLARE INT count = 3` | `0` |
| `FLOAT` | `DECLARE FLOAT price = 2.5` | `0.0` |
| `CHAR` | `DECLARE CHAR marker = '*'` | Null character; prints no text |
| `BOOL` | `DECLARE BOOL ready = "TRUE"` | `FALSE` |

Use ASCII `'` and `"` quote delimiters. CHAR holds one UTF-16 code unit. Strings are printable literals, rather than a declaration type.

The snippets below are **program fragments**. Place them inside a script and declare their variables before executable statements.

<details open>
<summary><strong>Variables, input, and output</strong></summary>

```text
DECLARE INT x, y, sum
DECLARE CHAR marker = '*'

PRINT: "Enter two integers: "
SCAN: x, y
sum = x + y
PRINT: "Sum: " & sum & $ & marker & $
```

Enter comma-separated values for `SCAN`, such as `3,4`. It requires exactly one field per target and validates every field before assigning values. Invalid input or a read failure leaves all targets unchanged. CHAR input preserves whitespace: a single space is valid.

`PRINT` adds **no implicit newline**. Use `&` to join parts and `$` to emit a newline. Bracket escapes print one enclosed character, such as `[#]`, `[[]`, or `[]]`.

```text
x = 10
y = 5
sum = x + y
x = y = 4
```

Chained assignments evaluate from right to left. Assignment preserves the declared type and supports INT-to-FLOAT promotion.

</details>

<details>
<summary><strong>Arithmetic, comparisons, and logic</strong></summary>

| Group | Operators |
| :--- | :--- |
| Arithmetic | `+`, `-`, `*`, `/`, `%` |
| Numeric comparison | `>`, `<`, `>=`, `<=`, `==`, `<>` |
| Logic | `AND`, `OR`, `NOT` |
| Unary and update | `+`, `-`, `++`, `--` |

```text
sum = x + y
difference = x - y
product = x * y
quotient = x / y
remainder = x % y
```

- Integer arithmetic is checked for overflow. FLOAT uses a 32-bit floating-point representation.
- Division or modulo by zero, overflow, and non-finite FLOAT values produce errors with a source line.
- `AND` and `OR` short-circuit at runtime; both operands must still pass semantic checks.
- Mixed INT/FLOAT comparisons are supported. BOOL and CHAR support same-type equality and inequality, but not ordering.
- Prefix `++` / `--` returns the updated value; postfix returns the previous value.
- FLOAT output is locale-independent and may use scientific notation.

**Numeric syntax:** Source numbers use decimal digits. FLOAT literals require digits on both sides of the decimal point, as in `1.5`. Signs are unary operators. Source exponents, hexadecimal notation, suffixes, `.5`, and `5.` are unsupported. SCAN retains Java numeric conversions, including exponent and hexadecimal FLOAT input.

</details>

<details>
<summary><strong>Conditions and alternatives</strong></summary>

```text
IF (x > 0 AND x < 10)
START IF
    PRINT: "Between 1 and 9" & $
END IF
ELSE IF (x == 0 OR x == 10)
START IF
    PRINT: "At a boundary" & $
END IF
ELSE
START IF
    PRINT: "Outside the range" & $
END IF
```

`IF` can stand alone or include `ELSE IF` alternatives and an `ELSE` branch. Conditions must evaluate to BOOL.

</details>

<details>
<summary><strong>Loops and nested blocks</strong></summary>

```text
FOR (i = 1, i <= 3, i++)
START FOR
    FOR (j = 1, j <= 3, j++)
    START FOR
        PRINT: '*'
    END FOR
    PRINT: $
END FOR
```

```text
REPEAT WHEN (x > 0)
START REPEAT
    PRINT: x & $
    x--
END REPEAT
```

`REPEAT WHEN` checks its condition before each iteration, so its body may execute zero times. Blocks can nest within conditions and loops.

</details>

<details>
<summary><strong>Complexity limits and diagnostics</strong></summary>

| Limit | How it is counted |
| :--- | :--- |
| **Parser nesting: 128** | One shared budget for active control statements, assignment chains, recursive unary `+` / `-` / `NOT`, and expression parentheses |
| **AST depth: 256** | The root counts as depth one; each child adds a level. Long flat expressions also contribute to depth. |

Limit violations report a phase and source line. These limits constrain source structure; script execution has no timer or iteration limit.

For exact counting rules, numeric behavior, and source-layout details, see the [language specification and implementation clarifications](Language%20Specification%20of%20LEXOR.md#implementation-clarifications).

</details>

## Examples

| Script | Demonstrates | Output or input |
| :--- | :--- | :--- |
| [Specification sample](examples/specification-sample.lxr) | Declarations, assignment, concatenation, and escapes | `4TRUE5`, newline, then `c#last` |
| [Arithmetic](examples/specification-arithmetic.lxr) | Grouped expressions and unary minus | `[-60]` |
| [Logic](examples/specification-logic.lxr) | BOOL expressions and comparisons | `TRUE` |
| [Increment and decrement](examples/inc-dec.lxr) | Prefix, postfix, and standalone updates | A trace of values before and after updates |
| [Diamond pattern](myprogram.lxr) | SCAN, conditions, and nested FOR loops | Enter an integer; try `3` |

<details>
<summary><strong>View the diamond-pattern program</strong></summary>

The complete example is also available in [`myprogram.lxr`](myprogram.lxr).

```text
SCRIPT AREA
START SCRIPT
    DECLARE INT x, mid, i, j, space, w
    PRINT: "Enter a number: "
    SCAN: x

    IF (x % 2 == 0)
    START IF
        mid = x / 2
    END IF
    ELSE
    START IF
        mid = (x / 2) + 1
    END IF

    space = mid - 1
    w = 1

    FOR (i = 1, i <= x, i = i + 1)
    START FOR
        FOR (j = 1, j <= space, j = j + 1)
        START FOR
            PRINT: ' '
        END FOR

        FOR (j = 1, j <= w, j = j + 1)
        START FOR
            IF (i == mid AND j == mid)
            START IF
                PRINT: x
            END IF
            ELSE
            START IF
                PRINT: '*'
            END IF
        END FOR

        IF (i < mid)
        START IF
            space = space - 1
            w = w + 2
        END IF
        ELSE
        START IF
            space = space + 1
            w = w - 2
        END IF

        PRINT: $
    END FOR
END SCRIPT
```

</details>

## Verification

Run the full build and test suite:

```powershell
# Windows
.\mvnw.cmd verify
```

```sh
# macOS / Linux
sh mvnw verify
```

| Check | Coverage |
| :--- | :--- |
| **Unit tests** | Language behavior, diagnostics, I/O failures, structural limits, and terminal lifecycle |
| **Packaged-JAR tests** | Console/debug/help modes, redirected input, and headless execution through Maven Failsafe |
| **GitHub Actions** | Windows, Ubuntu, and macOS with Temurin Java 23 on pushes and pull requests |

Packaged-JAR tests run without opening windows. The [CI workflow](.github/workflows/verify.yml) uses the Maven Wrapper with dependency caching.

<details>
<summary><strong>Opt-in native Windows smoke tests</strong></summary>

Use a built JAR and an interactive Windows desktop:

```powershell
.\tools\windows-smoke.ps1

# Optional: verify an existing writable UNC share
.\tools\windows-smoke.ps1 -UncDirectory '\\server\share\existing-folder'
```

The [smoke suite](tools/windows-smoke.ps1) applies timeouts and tracks its owned windows and processes. It checks picker cancellation, repeated SCAN, a space CHAR, Unicode and shell metacharacter paths, completion codes, closing an infinite loop, and process cleanup.

- The suite prints pass/skip counts.
- Full window-closure coverage requires **Windows Console Host**. Windows Terminal's pseudoconsole does not expose the terminal's actual window through `GetConsoleWindow`, so these cases are explicitly skipped and the suite exits with `2` to indicate partial coverage.
- UNC verification is skipped when no `-UncDirectory` is supplied.

See Microsoft's [console window documentation](https://learn.microsoft.com/en-us/windows/console/getconsolewindow) for the host limitation.

</details>

---

<p align="center">
  <a href="Language%20Specification%20of%20LEXOR.md">Read the specification</a> &nbsp;·&nbsp;
  <a href="examples/">Browse examples</a> &nbsp;·&nbsp;
  <a href="#lexor">Back to top</a>
</p>
