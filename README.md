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

## 🚀 Features

- **Custom Lexical Analysis**  
  Reads and processes Lexor source code from `.lxr` files.

- **Variable Declaration and Assignment**  
  Supports integer variables and basic value assignment.

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

Use `DECLARE INT` to create integer variables.

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