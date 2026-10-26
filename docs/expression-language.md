# 表达式语言与实现

## 处理流程

```text
原始字符串 → Lexer → Parser → 不可变 AST → 使用本次变量及函数绑定求值
```

`Expression` 是无状态编译入口，`Expression.compile(source)` 每次创建独立解析器。数字在编译时解析为 `Double`。函数调用节点记录名称、参数节点和原始源码位置，函数绑定及参数个数在求值时检查。变量名由解析器自动收集，编译过程与变量、函数绑定分离。

`CompiledExpression.variables` 按首次出现顺序提供去重后的变量名集合，函数调用名不计入其中。同名标识符作为变量使用时仍会收集。各次访问复用同一个只读 Set。调用方可通过集合差集单独校验业务允许的变量名。

`CompiledExpression.functions` 同样按源码中首次出现顺序收集并去重，包含标准、自定义及尚未绑定的函数名；嵌套调用中外层名称先于参数内的函数名称。变量引用与函数调用分别收集，同名标识符可同时存在于两个集合。各次访问复用同一个只读 Set，求值时的函数绑定不改变该集合。

`CompiledExpression` 保存原始源码和不可变 AST，不保存变量绑定、随机状态或求值结果。每次求值使用调用者传入的 Map，没有全局编译缓存。缓存生命周期由调用方管理。

## 文法

```ebnf
expression = sum, EOF ;
sum        = product, { ("+" | "-"), product } ;
product    = unary, { ("*" | "/" | "%"), unary } ;
unary      = [ "+" | "-" ], primary ;
primary    = NUMBER | name
           | name, "(", [ arguments ], ")"
           | "(", sum, ")" ;
arguments  = sum, { ",", sum } ;
name       = IDENTIFIER, { ".", IDENTIFIER } ;
NUMBER     = DIGIT, { DIGIT }, [ ".", DIGIT, { DIGIT } ],
             [ ("e" | "E"), [ "+" | "-" ], DIGIT, { DIGIT } ] ;
```

`DIGIT` 为 ASCII 数字。标识符以 ASCII 字母或下划线开头，后续可包含 ASCII 字母、数字或下划线，区分大小写。词法分析跳过空格、制表符、回车和换行。

路径变量由紧邻的点分隔标识符组成，例如 `a.b.c`、`_a.b2.C`，整个路径是一个词法单元，内部不能插入空白。`CompiledExpression.variables` 收集完整路径，求值通过 `Map<String, Double>` 精确查找完整键，不执行对象成员访问或前缀回退。缺失绑定的错误区间覆盖完整路径。路径段为空或点后不是标识符起始字符时，报告 `UNEXPECTED_TOKEN`，区间指向该点。函数名也支持相同的点分路径，名称后跟 `(` 即识别为函数调用，名称与括号之间可有空白。`CompiledExpression.functions` 收集完整函数名，求值通过 `functions` Map 精确绑定；变量与函数可使用同一路径，分别从各自的 Map 查找。未知函数和参数数量错误的区间覆盖完整函数名。路径段数不增加 AST 深度，仍受源码总长度限制。

数字允许前导零，小数点两侧均需有数字。乘法使用显式 `*`。当前文法范围为数值表达式，未包含注释、字符串、布尔值、赋值以及自定义运算符。

变量在编译时自动识别，由调用方在求值时绑定，默认变量绑定为空。`ExpressionConstants.standard` 提供 `kotlin.math.PI` 和 `kotlin.math.E`，键分别为大写的 `PI`、`E`，各次访问共享同一个只读 `Map<String, Double>`。调用方将其显式传入 `evaluate` 或 `evaluateResult` 的 `variables`，也可合并、筛选或重命名键。常量遵循普通变量语义，出现在 `CompiledExpression.variables` 中，缺少绑定时报告 `MISSING_VARIABLE`；标准函数绑定与常量绑定相互独立。函数与变量可以同名，解析器通过后续的 `(` 识别函数调用。每次求值期间，调用方保持传入的 Map 不变，以便重复和并发求值。

优先级从高到低为括号与函数调用、一元正负号、乘除余数、加减；二元运算均左结合。连续一元前缀如 `--2` 非法，减去负数的 `2--3` 合法。幂通过显式绑定的标准函数 `pow(base, exponent)` 计算，嵌套调用决定计算顺序。旧 `^` 字符报告 `ExpressionParseErrorCode.INVALID_CHARACTER`。

标准函数为 `min`、`max`（至少一个参数，范围为 `1..Int.MAX_VALUE`）、`clamp`（固定三个参数，范围为 `3..3`）和 `pow`（固定两个参数，范围为 `2..2`）。自定义函数通过 `ExpressionFunction(arity, implementation)` 定义，`arity` 属性为闭区间 `IntRange`，回调接受 `DoubleArray` 并返回 `Double`。保留 `arity: Int` 构造重载，将固定参数个数 `n` 转换为 `n..n`。

构造时检查范围非空且起点非负，非法范围抛出 `IllegalArgumentException`。`0..Int.MAX_VALUE` 表示接受任意非负参数个数，实际输入仍受表达式长度限制；`1..3` 表示接受一到三个参数。标准函数与自定义函数统一通过参数范围检查。与之前的 `arity: Int` 属性相比，读取属性现在得到 `IntRange`，固定参数的构造写法保持兼容。

求值时仅从传入的 `functions` Map 查找精确名称，默认绑定为空。`ExpressionFunctions.standard` 提供 `min/max/clamp/pow` 的共享只读 Map，由调用方显式传入。使用 `standard + customFunctions` 时右侧同名绑定优先，也可筛选或重命名标准函数。未找到函数报告 `UNKNOWN_FUNCTION`；参数个数不在闭区间内时报告 `INVALID_ARGUMENT_COUNT`。这两项检查在求值参数之前完成，避免在无效调用中触发参数副作用。参数中的异常先于标准函数自身的数值检查报告，例如 `clamp(1,3,1/0)` 先报告除零。

标准函数实现随 `ExpressionFunction` 绑定传递，AST 不根据函数名称选择实现。标准 `clamp` 使用当前调用节点的 Token 报告错误，因此重命名后仍保留正确的源码区间。标准集合中的实现无可变求值状态。

参数从左到右求值。自定义函数接收独立数组；标准函数直接求值参数节点，避免创建参数数组，参数求值遇到异常时立即停止，后续参数不再执行；NaN 不会导致提前停止。标准实现随绑定传递，因此重命名后仍使用相同优化，覆盖为自定义函数后使用独立数组。自定义函数或绑定 Map 抛出的 `ExpressionEvaluationException` 通过内部包装区分来源，在公共 API 边界解包并原样抛出，因此 `evaluateResult()` 不会将其转换为当前表达式的 `Failure`。其他异常直接原样传播。标准函数和引擎产生的求值错误仍转换为 `Failure`。

AST 不保存函数实现。同一编译结果可以重复或并发使用不同绑定；调用方在求值期间保持绑定 Map 不变，并负责回调共享状态的线程安全。

## 数值语义

所有值均为 `Double`，操作数和函数参数从左到右求值。

- 四则运算使用 Kotlin 浮点运算；`%` 是截断除法对应的余数，例如 `-7 % 3 = -1`。
- 除法和余数的右操作数为正零或负零时，抛出 `DIVISION_BY_ZERO`。
- 幂使用 `kotlin.math.pow`。`pow(0,0) = 1`，负数的非整数幂产生 `NaN`，溢出可以产生无穷大。
- 数值字面量溢出保留为无穷大，下溢可以产生零。库不将非有限值替换为其他值。
- `min/max` 从首个参数开始依次取最小值或最大值，单参数返回该值。保留有符号零语义并传播任一位置的 `NaN`；所有参数仍按从左到右顺序求值。零参数调用在求值时报告 `INVALID_ARGUMENT_COUNT`。
- `clamp(v,lo,hi)` 先检查 `lo > hi`，反转时抛出 `INVALID_CLAMP_BOUNDS`，否则计算 `min(max(v,lo),hi)`。在未触发上下界错误时，任一参数为 `NaN` 都会传播 `NaN`。

各平台的幂计算不保证逐位相同，浮点测试使用合理容差。

## 错误定位与复杂度

`CompiledExpression.validateBindings(variables: Set<String>, functions: Map<String, ExpressionFunction>)` 提供可选的静态绑定校验，两个参数默认为空集合。遍历 AST 检查每个变量引用及每次函数调用，包括同名函数的不同参数数量；按操作数从左到右、函数自身先于参数的顺序收集所有绑定错误，返回 `List<ExpressionEvaluationException>`，空列表表示通过。同名引用在不同位置分别报告；未知函数跳过参数数量检查，未知函数和参数数量错误均不阻止参数内部校验。列表中的错误使用 `ExpressionEvaluationException` 的 `MISSING_VARIABLE`、`UNKNOWN_FUNCTION`、`INVALID_ARGUMENT_COUNT`，区间覆盖完整名称。校验不读取变量值、不执行函数或算术，不检查除零或 clamp 上下界，因此其首个错误可能不同于实际求值。集合访问异常原样传播并中断校验，不返回已收集的部分列表。每次校验使用独立的结果列表，实际求值仍在首个错误处停止。校验不存储绑定，后续求值仍使用当次传入的 Map。

`CompiledExpression.validateArithmetic()` 提供独立的静态除零校验，返回按运算符源码位置排列的 `List<ExpressionEvaluationException>`。遍历所有操作数和函数参数，包括绑定无效的调用；仅检查 `/`、`%` 的右操作数是否为编译时常量折叠得到的正零或负零，报告 `DIVISION_BY_ZERO`，区间覆盖运算符。例如 `1/(2-2)`、`x/0` 可以报告，`1/x`、`1/(x-x)` 和 `1/min(0,0)` 不会被推断。不读取变量值、不执行函数、不检查 clamp 上下界或非有限值。返回空列表仅表示未发现静态确定的除零；编译和求值行为不变，实际求值仍可能先遇到其他错误。每次校验使用独立结果列表，不保存状态。

`Expression.compileResult(source)` 返回 `ExpressionResult<CompiledExpression, ExpressionParseException>`，`evaluateResult(variables, functions)` 返回 `ExpressionResult<Double, ExpressionEvaluationException>`。sealed 接口包含 `Success<T>(value)` 与 `Failure<E>(error)` 两个分支，成功值和错误类型均为协变参数，调用方可通过 `when` 穷尽处理并直接访问具体类型的错误。

编译结果接口复用 `compile()`，两个求值接口共享 AST 求值实现。结果接口将引擎及标准函数产生的对应阶段表达式异常转换为 `Failure`，保留错误码、源码区间和消息。自定义函数及变量/函数 Map 查找抛出的异常原样传播，包括其中的表达式异常。缺失的变量绑定在求值时报告 `MISSING_VARIABLE`，保留原始变量引用区间。

此次 API 调整移除了有状态的 `ExpressionCompiler` 和 `UNKNOWN_VARIABLE` 错误码。原调用 `ExpressionCompiler(names).compile(source)` 改为 `Expression.compile(source)`，变量白名单由调用方根据 `variables` 单独检查；结果接口使用 `Expression.compileResult(source)`。

结果接口与抛异常接口使用相同的语法、复杂度限制和求值语义；`NaN` 和无穷大属于成功结果。两种求值接口均接受显式变量和函数绑定。

解析错误和计算错误分别使用 `ExpressionParseException`、`ExpressionEvaluationException`，共同继承 `ExpressionException`。基类提供消息与源码区间，两个子类的 `code` 分别为 `ExpressionParseErrorCode` 和 `ExpressionEvaluationErrorCode`，类型上区分阶段。通过基类处理时，可用穷尽的 `when` 取得具体错误码。

| 错误类型 | 错误码 |
|---|---|
| `ExpressionParseErrorCode` | `INVALID_CHARACTER`、`INVALID_NUMBER`、`UNEXPECTED_TOKEN`、`LIMIT_EXCEEDED` |
| `ExpressionEvaluationErrorCode` | `MISSING_VARIABLE`、`UNKNOWN_FUNCTION`、`INVALID_ARGUMENT_COUNT`、`DIVISION_BY_ZERO`、`INVALID_CLAMP_BOUNDS` |

`compile()` 及 `compileResult()` 的入口名称保留，解析阶段异常统一为 `ExpressionParseException`。旧的统一错误码枚举已拆分；错误码和源码区间是稳定接口，消息文本不作为兼容契约。

`ExpressionFunction` 构造时的负数或空参数范围属于 API 参数错误，仍抛出 `IllegalArgumentException`，没有表达式源码区间。调用方函数和 Map 的异常原样传播，内部用于区分来源的包装异常在公共求值边界解包。

源码始终保留原样，不进行字符串改写。区间以从 0 开始的 UTF-16 单元表示，左闭右开，EOF 区间为空。算术错误指向运算符，函数错误指向函数名，变量错误指向变量名。

源码长度最多 16,384 个 UTF-16 单元；解析递归层数和 AST 深度分别最多 128。任一超限都报告 `LIMIT_EXCEEDED`，避免无限制递归。

左结合的运算链同样增加 AST 深度：128 项相加可接受，129 项相加超限。括号主要增加解析递归层数，不单独生成 AST 节点。

## 求值性能

编译时折叠纯数字的一元和二元算术子表达式，保持原有浮点运算顺序、有符号零和非有限值语义。除零及余数除零保留到求值阶段报告；变量、常量绑定和函数调用仍在每次求值时解析。折叠节点保留原始表达式深度，复杂度限制不因优化放宽。

重复求值时可在循环外保存编译结果及合并后的绑定 Map。名称集合和标准集合直接复用只读集合，访问属性时不复制集合；`+`、`-` 等组合操作创建新的集合。求值不改变调用方 Map 的查找次数或顺序。
