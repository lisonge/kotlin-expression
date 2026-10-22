# kotlin-expression

[![Maven Central](https://img.shields.io/maven-central/v/li.songe/expression.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/li.songe/expression)

English | [简体中文](README-zh.md)

A Kotlin Multiplatform library for evaluating mathematical expressions with variables. Compile once and evaluate repeatedly or concurrently with different values. The Kotlin standard library is the only runtime dependency.

## Installation

Add Maven Central to your repositories:

```kotlin
repositories {
    mavenCentral()
}
```

For JVM or Android projects:

```kotlin
dependencies {
    implementation("li.songe:expression:0.1.0")
}
```

For Kotlin Multiplatform projects, add the dependency to `commonMain`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("li.songe:expression:0.1.0")
        }
    }
}
```

## Usage

```kotlin
import li.songe.expression.Expression
import li.songe.expression.ExpressionFunctions

val expression = Expression.compile("min(width * 0.5, screenWidth)")
val variables = expression.variables // setOf("width", "screenWidth")
val functions = expression.functions // setOf("min")
val result = expression.evaluate(
    variables = mapOf("width" to 200.0, "screenWidth" to 1080.0),
    functions = ExpressionFunctions.standard,
) // 100.0
```

## Supported syntax

### Numbers

Numeric literals use ASCII digits `0`–`9` in decimal notation and are parsed as `Double`.

| Form | Rule | Examples |
| --- | --- | --- |
| Integer | One or more digits; leading zeros are allowed | `0`, `12`, `0012` |
| Decimal | At least one digit on each side of the decimal point | `0.5`, `12.0` |
| Scientific notation | An integer or decimal followed by `e`/`E`, an optional sign, and one or more exponent digits | `1e3`, `1E-3`, `1.25e+2` |

A leading sign is a unary operator, so `-2` and `+0.5` are expressions. `.5`, `2.`, `1e`, hexadecimal literals, digit separators, and numeric suffixes are outside this grammar. `NaN` and `Infinity` are names, not numeric literals; computed values may be NaN or infinite. Numeric overflow produces infinity and underflow may produce zero.

### Names and calls

Each name segment starts with an ASCII letter (`a`–`z`, `A`–`Z`) or `_`, followed by zero or more ASCII letters, digits, or underscores. Names are case-sensitive. Dots join segments directly: `a.b.c` is one complete name, with no whitespace inside it.

| Form | Meaning | Examples |
| --- | --- | --- |
| `name` | Variable lookup by complete name | `width`, `screen.width` |
| `name(...)` | Function call with comma-separated expression arguments | `f()`, `min(x, 10)`, `math.pow(x, 2)` |
| `(expression)` | Explicit grouping | `(x + 1) * 2` |

Function syntax permits zero or more arguments; the supplied function binding determines the accepted count during evaluation. Every comma is followed by an argument. Variables and functions use their respective binding maps.

### Operators and whitespace

Precedence, from highest to lowest:

| Precedence | Syntax | Associativity |
| --- | --- | --- |
| 1 | Grouping `(…)`, function calls `f(…)` | Explicit grouping |
| 2 | Unary `+`, `-` | One prefix before a number, name, call, or group |
| 3 | `*`, `/`, `%` | Left |
| 4 | `+`, `-` | Left |

`2--3` means `2 - (-3)`; `--2` is invalid, while `-(-2)` uses explicit grouping. Multiplication is written with `*`, as in `2 * x`. Exponentiation uses an explicitly bound `pow` function. `%` is the remainder with truncation toward zero: `-7 % 3` is `-1`. Division or remainder by either signed zero produces an evaluation error.

ASCII space, tab (`\t`), carriage return (`\r`), and line feed (`\n`) may separate tokens, including a function name and `(`. A numeric literal or dotted name is contiguous. The grammar includes no assignment, comparisons, logical operators, strings, comments, indexing, or `^`/`**` operators.

## Built-in standards

The library provides standard constants and functions. Bind them explicitly through `variables` and `functions`; both bindings default to empty maps. Names are case-sensitive.

### Constants

`ExpressionConstants.standard` provides a `Map<String, Double>`:

| Name | Description | Value |
| --- | --- | --- |
| `PI` | Circle circumference divided by diameter | `kotlin.math.PI` ≈ 3.141592653589793 |
| `E` | Base of the natural logarithm | `kotlin.math.E` ≈ 2.718281828459045 |

### Functions

`ExpressionFunctions.standard` provides a `Map<String, ExpressionFunction>`:

| Function | Arguments | Description | Example |
| --- | --- | --- | --- |
| `min(a, …)` | One or more | Returns the smallest value | `min(3, 1, 2)` → `1` |
| `max(a, …)` | One or more | Returns the largest value | `max(3, 1, 2)` → `3` |
| `clamp(value, min, max)` | Exactly three | Restricts a value to the inclusive bounds; reversed bounds produce an error | `clamp(12, 0, 10)` → `10` |
| `pow(base, exponent)` | Exactly two | Raises the base to the exponent | `pow(2, 3)` → `8` |

### Binding example

```kotlin
import li.songe.expression.Expression
import li.songe.expression.ExpressionConstants
import li.songe.expression.ExpressionFunctions

val expression = Expression.compile("PI * pow(radius, 2)")
val variables = expression.variables // setOf("PI", "radius")
val result = expression.evaluate(
    variables = ExpressionConstants.standard + mapOf("radius" to 3.0),
    functions = ExpressionFunctions.standard,
) // 28.274333882308138
```

Names are uppercase and case-sensitive. Constants are ordinary variable bindings and appear in `expression.variables`; an absent binding produces `MISSING_VARIABLE`. `evaluateResult` accepts the same map. The read-only map is shared across accesses. Combine with custom values using `+` (right-hand values win), remove entries with `- "PI"`, or rename keys to paths such as `math.PI`.

## Path variables

Use dotted names such as `a.b.c` to organize variable bindings. Each segment follows the identifier rules above, with dots directly joining the segments. The full name is a single key in `Map<String, Double>`; evaluation looks up that key rather than traversing objects. Function names also support dotted paths, bound by their complete names in the `functions` map.

```kotlin
val expression = Expression.compile("a.b.c * 2 + screen.width")
val variables = expression.variables // setOf("a.b.c", "screen.width")
val result = expression.evaluate(
    mapOf("a.b.c" to 3.0, "screen.width" to 100.0),
) // 106.0
```

A missing binding reports `MISSING_VARIABLE` with the source range of the complete path. `compileResult` and `evaluateResult` support the same syntax and bindings.

## Custom functions

`expression.functions` lists all referenced function names, including standard functions and custom calls, deduplicated in first-appearance order. Like `variables`, it reuses the same read-only set across accesses.

Bind functions at evaluation time using `ExpressionFunction(arity) { args -> … }`. The same compiled expression can use different implementations on each call.

```kotlin
import li.songe.expression.Expression
import li.songe.expression.ExpressionFunction

val expression = Expression.compile("lerp(start, end, progress)")
val functions = mapOf(
    "lerp" to ExpressionFunction(arity = 3) { args ->
        args[0] + (args[1] - args[0]) * args[2]
    },
)
val result = expression.evaluate(
    variables = mapOf("start" to 10.0, "end" to 20.0, "progress" to 0.5),
    functions = functions,
) // 15.0
```

Use `arity = 3` for exactly three arguments, or an inclusive range such as `arity = 1..3` for a variable argument count. The `arity` property is an `IntRange`; the `Int` constructor converts `n` to `n..n`. Ranges are nonempty and nonnegative.

```kotlin
val functions = mapOf(
    "sum" to ExpressionFunction(arity = 0..Int.MAX_VALUE) { it.sum() },
    "average" to ExpressionFunction(arity = 1..3) { it.average() },
)
Expression.compile("sum(1, 2, 3)").evaluate(functions = functions) // 6.0
Expression.compile("sum()").evaluate(functions = functions) // 0.0
Expression.compile("average(2, 4)").evaluate(functions = functions) // 3.0
```

Arguments are evaluated from left to right; custom functions receive a fresh `DoubleArray`. Standard `min` and `max` accept one or more arguments, while `clamp` accepts exactly three. Combine bindings with `ExpressionFunctions.standard + customFunctions`; entries on the right override matching names. Use `ExpressionFunctions.standard - "min"` to omit a function. `standard` reuses the same read-only map across accesses. `evaluateResult()` accepts the same `functions` parameter. Counts outside the configured range produce `INVALID_ARGUMENT_COUNT`.

Dotted function names use the same segment rules as path variables. For example:

```kotlin
val expression = Expression.compile("a.b.c(3)")
val functions = expression.functions // setOf("a.b.c")
val result = expression.evaluate(
    functions = mapOf("a.b.c" to ExpressionFunction(1) { args -> args[0] * 2 }),
) // 6.0
```

Function names are case-sensitive. Missing functions and incorrect argument counts produce evaluation errors at the function name. Exceptions thrown by custom implementations or binding map lookups propagate unchanged through both evaluation APIs. For concurrent use, supply thread-safe implementations or separate bindings per call.

## Errors and reuse

`compileResult()` and `evaluateResult()` return `ExpressionResult<T, E>`. Use an exhaustive `when` to handle `Success.value` and `Failure.error`. The error type is `ExpressionParseException` for compilation and `ExpressionEvaluationException` for evaluation.

```kotlin
import li.songe.expression.Expression
import li.songe.expression.ExpressionResult

when (val compiled = Expression.compileResult("x / 2")) {
    is ExpressionResult.Success -> {
        when (val evaluated = compiled.value.evaluateResult(mapOf("x" to 10.0))) {
            is ExpressionResult.Success -> println(evaluated.value) // 5.0
            is ExpressionResult.Failure -> println(evaluated.error.code)
        }
    }
    is ExpressionResult.Failure -> println(compiled.error.code)
}
```

The original `compile()` and `evaluate()` methods remain available for exception-based handling, as shown below.

Compilation checks syntax and collects complete variable and function names in `expression.variables` and `expression.functions`. Evaluation resolves functions, checks argument counts, and reports missing variable bindings; extra bindings are ignored. Applications can validate these sets against their own allowed names before evaluation.
Compiled expressions can be reused across calls and threads, with a stable set of bindings for each evaluation.

```kotlin
import li.songe.expression.Expression
import li.songe.expression.ExpressionException
import li.songe.expression.ExpressionParseException
import li.songe.expression.ExpressionEvaluationException

try {
    Expression.compile("1 / 0").evaluate()
} catch (error: ExpressionException) {
    val code = when (error) {
        is ExpressionParseException -> error.code.name
        is ExpressionEvaluationException -> error.code.name
    }
    println("$code: [${error.startIndex}, ${error.endIndex})")
    // DIVISION_BY_ZERO: [2, 3)
}
```

`ExpressionParseException` carries an `ExpressionParseErrorCode` for syntax and complexity errors. `ExpressionEvaluationException` carries an `ExpressionEvaluationErrorCode` for binding, argument-count, and arithmetic errors. Both extend `ExpressionException`, which provides the message and original source range. Indices are zero-based UTF-16 offsets with an inclusive start and exclusive end.

Development notes are available in [docs](docs/README.md) (Chinese).

## License

[Apache License 2.0](LICENSE).
