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
  <b>Lexor</b> is a custom scripting language interpreter developed in Java for the Programming Languages course.
</div>

---


## 🚀 Features

The **Lexor** syntax is designed to be intuitive and heavily structured using standard block terminators.

*   <strong style="color:#D8224A">Custom Lexical Analysis:</strong> Parses custom `.lxr` syntax files.
*   <strong style="color:#A96045">Control Structures:</strong> Supports nested loops (`FOR`) and explicit conditional blocks (`IF` / `ELSE`).
*   <strong style="color:#D8224A">Mathematical Operations:</strong> Handles arithmetic with order of operations and modulo (`%`).
*   <strong style="color:#A96045">I/O Methods:</strong> Integrated `PRINT` and `SCAN` keywords for console interaction.

---

## 💻 Sample Code

Here is an example of what **Lexor** looks like in action. This script takes an integer input and dynamically prints a patterned diamond to the console!

```text
SCRIPT AREA
    START SCRIPT
        DECLARE INT x, mid, i, j, space, w
        PRINT: "Enter a number: "
        SCAN: x

        IF (x % 2 == 0)
            START IF
                mid = x/2
            END IF
        ELSE
            START IF
                mid = (x/2) + 1
            END IF

        space = mid-1
        w = 1

        FOR (i=1, i <= x, i=i+1)
            START FOR

                FOR (j = 1, j <= space, j=j+1)
                    START FOR
                        PRINT: ' '
                    END FOR

                FOR (j = 1, j <= w, j=j+1)
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
                        space = space-1
                        w = w+2
                    END IF
                ELSE
                    START IF
                        space = space+1
                        w = w-2
                    END IF
            PRINT: $
            END FOR
    END SCRIPT
```

<div align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=D8224A&height=100&section=footer" />
</div>
