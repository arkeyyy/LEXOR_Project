<div align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=A96045&height=100" />
</div>

<h1 align="center">Lexor Project</h1>

<div align="center">
  <img src="https://img.shields.io/badge/Java-D8224A?style=for-the-badge&logo=openjdk&logoColor=161616" alt="Java Badge"/>
  <img src="https://img.shields.io/badge/Custom_Script-A96045?style=for-the-badge&logo=gnubash&logoColor=161616" alt="Custom Script Badge"/>
  <img src="https://img.shields.io/badge/CS322-161616?style=for-the-badge&logo=bookmeter&logoColor=D8224A" alt="CS322 Badge"/>
</div>

<br>

<div align="center">
  <b>Lexor</b> is a custom scripting language interpreter built in Java for the Programming Languages course.
</div>

---

## 📌 Overview

**Lexor** is a custom scripting language designed to demonstrate the basic concepts of programming language design and interpretation.

The project allows users to write and run `.lxr` scripts using a simple structured syntax. It supports variable declaration, value assignment, arithmetic operations, user input, console output, conditional statements, and loop structures.

Lexor was developed to show how a programming language can be processed and executed through Java using lexical analysis, syntax handling, and interpretation.

---

## Build and run

Install JDK 23 or later and set `JAVA_HOME` to that installation. The Maven Wrapper downloads Maven 3.9.11 and the required build dependencies on its first run.

On Windows:

```powershell
.\mvnw.cmd verify
java -jar target/Lexor_Project-1.0-SNAPSHOT.jar
```

Select a `.lxr` file in the picker, which starts in the current directory. Cancelling exits successfully. LEXOR opens a separate native terminal for the selected script: enter values for `SCAN` and see `PRINT` output as it runs. The terminal shows the exit status after execution and waits at `Press Enter to close...`, including when the script reports an error.

Supply a script path to skip the picker and open its terminal directly:

```powershell
java -jar target/Lexor_Project-1.0-SNAPSHOT.jar myprogram.lxr
java -jar target/Lexor_Project-1.0-SNAPSHOT.jar --debug "examples/inc-dec.lxr"
```

Closing the terminal while a script is running stops that script. Stopping the launcher also terminates its owned terminal session. Each launch runs one script, using the original working directory. Running `lexor.Main` from an IDE or through `java -cp target/classes lexor.Main` uses the same Windows flow.

Use `--console` to run inside the existing terminal, including redirected input and automation. This mode writes only script output to stdout, with errors and optional `--debug` traces on stderr; it has no picker, completion message, or closing prompt.

```powershell
java -jar target/Lexor_Project-1.0-SNAPSHOT.jar --console myprogram.lxr
"3" | java -jar target/Lexor_Project-1.0-SNAPSHOT.jar --console myprogram.lxr
java -jar target/Lexor_Project-1.0-SNAPSHOT.jar --console --debug "examples/inc-dec.lxr"
java -jar target/Lexor_Project-1.0-SNAPSHOT.jar --help
```

`--help` never opens a window. Put options before the script path. Use `--debug` without a script on Windows to select a file and show diagnostic traces in its terminal.

On macOS, Linux, or a headless system, execution stays in the existing terminal and requires a script path:

```sh
sh mvnw verify
java -jar target/Lexor_Project-1.0-SNAPSHOT.jar myprogram.lxr
```

Quote script paths containing spaces. Source files use UTF-8. `SCAN` reads comma-separated values from standard input.

If a Windows JDK misreads non-ASCII paths passed on the command line, select the script with the file picker. The windowed session preserves Unicode paths and input.

Exit codes are `0` for success, help, or picker cancellation; `1` for language or execution errors; and `2` for invalid arguments, unreadable files, or terminal launch failures. Launch failures also appear in a dialog. The windowed launcher returns the script's exit code after the terminal closes.

The [language specification](Language%20Specification%20of%20LEXOR.md) includes implementation clarifications. Runnable versions of its examples are in [`examples/`](examples/). The original increment/decrement demo is preserved in [`examples/inc-dec.lxr`](examples/inc-dec.lxr).

### Language behavior

- Put each statement, header, and block marker on its own line. Blank lines and `%%` comments are allowed.
- Use ASCII `'` and `"` quote delimiters. Strings are printable literals; declared variables use `INT`, `FLOAT`, `CHAR`, or `BOOL`.
- Numeric assignments allow `INT` to `FLOAT` promotion. Integer arithmetic is checked for overflow; FLOAT values use 32-bit precision.
- Division or modulo by zero, overflow, and non-finite FLOAT values produce source-positioned errors.
- `AND` and `OR` short-circuit at runtime. Both operands must still pass semantic checks.
- Mixed INT/FLOAT comparisons are supported. BOOL and CHAR support same-type equality/inequality, but not ordering.
- `++` and `--` are supported extensions. Prefix returns the updated value; postfix returns the previous value.
- `SCAN` requires exactly one field per target. Invalid input leaves every target unchanged. CHAR input must contain exactly one character, including a single space.
- FLOAT output uses locale-independent decimal notation and may use scientific notation for very large or small values.

---

## 🚀 Features

- **Custom Lexical Analysis**  
  Reads and processes Lexor source code from `.lxr` files.

- **Variable Declaration and Assignment**  
  Supports INT, FLOAT, CHAR, and BOOL variables, checked assignments, and chained assignments.

- **Arithmetic Operations**  
  Handles addition, subtraction, multiplication, division, and modulo.

- **Conditional Statements**  
  Supports structured `IF` and `ELSE` blocks.

- **Looping Structures**  
  Supports `FOR` loops, including nested loops.

- **Console Input and Output**  
  Uses `SCAN` for user input and `PRINT` for displaying output.

- **Structured Block Syntax**  
  Uses clear block markers such as `START SCRIPT`, `END SCRIPT`, `START IF`, `END IF`, `START FOR`, and `END FOR`.

---

## 🧠 Basic Operations

Below are the basic operations supported by **Lexor**, with small code snippets for each one.

---

### 1. Program Structure

Every Lexor program starts with `SCRIPT AREA` and contains a `START SCRIPT` and `END SCRIPT` block.

```text
SCRIPT AREA
    START SCRIPT
        PRINT: "Hello, Lexor!"
    END SCRIPT
```

---

### 2. Declare Variables

Use `DECLARE INT`, `DECLARE FLOAT`, `DECLARE CHAR`, or `DECLARE BOOL` immediately after `START SCRIPT` to declare variables.

```text
DECLARE INT x
```

You can also declare multiple variables in one line.

```text
DECLARE INT x, y, sum
```

---

### 3. Assign Values

Variables can store numbers or the result of an expression.

```text
x = 10
y = 5
sum = x + y
```

---

### 4. Scan Input

Use `SCAN` to get input from the user.

```text
PRINT: "Enter a number: "
SCAN: x
```

---

### 5. Print Output

Use `PRINT` to display text, characters, numbers, or variable values.

```text
PRINT: "Hello, World!"
PRINT: x
PRINT: '*'
```

To move to the next line, use:

```text
PRINT: $
```

---

### 6. Arithmetic Operations

Lexor supports basic arithmetic operations.

```text
sum = x + y
difference = x - y
product = x * y
quotient = x / y
remainder = x % y
```

---

### 7. Conditional Statement

Use `IF` to execute code only when a condition is true.

```text
IF (x > 10)
    START IF
        PRINT: "x is greater than 10"
    END IF
```

---

### 8. If-Else Statement

Use `ELSE` to provide another action when the condition is false.

```text
IF (x % 2 == 0)
    START IF
        PRINT: "Even number"
    END IF
ELSE
    START IF
        PRINT: "Odd number"
    END IF
```

---

### 9. For Loop

Use `FOR` to repeat a block of code.

```text
FOR (i = 1, i <= 5, i = i + 1)
    START FOR
        PRINT: i
        PRINT: $
    END FOR
```

---

### 10. Nested For Loop

Lexor also supports loops inside another loop.

```text
FOR (i = 1, i <= 3, i = i + 1)
    START FOR
        FOR (j = 1, j <= 3, j = j + 1)
            START FOR
                PRINT: '*'
            END FOR

        PRINT: $
    END FOR
```

---

### 11. Logical Operators

Conditions can use logical operators such as `AND` and `OR`.

```text
IF (x > 0 AND x < 10)
    START IF
        PRINT: "x is between 1 and 9"
    END IF
```

```text
IF (x == 1 OR x == 2)
    START IF
        PRINT: "x is either 1 or 2"
    END IF
```

---

## 💻 Sample Lexor Code

The sample below asks the user for an integer input and prints a diamond-like pattern in the console.

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

---

## 🛠️ Technologies Used

- Java
- Custom scripting syntax
- Lexical analysis
- Interpreter design
- Console-based execution

---

## 📁 File Extension

Lexor source files use the `.lxr` extension.

Example:

```text
diamond.lxr
```

---

## 🎯 Project Purpose

This project was developed for the **CS322 Programming Languages** course.

Its main purpose is to apply the core concepts of programming language implementation, including:

- Reading source code
- Tokenizing custom syntax
- Evaluating expressions
- Handling variables
- Executing control structures
- Running script instructions through an interpreter

---

## 👨‍💻 Developers

Developed by the Lexor Project team for academic purposes.

---

<div align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=D8224A&height=100&section=footer" />
</div>
