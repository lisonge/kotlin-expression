# 开发与发布

## 项目结构

项目参考 `kotlin-json5` 的单模块结构：

```text
.
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── expression/
│   ├── build.gradle.kts
│   ├── api/expression.api
│   └── src/
│       ├── commonMain/kotlin/li/songe/expression/
│       ├── commonTest/kotlin/li/songe/expression/
│       └── jvmTest/kotlin/li/songe/expression/
├── docs/
└── .github/workflows/release.yml
```

引擎位于 `commonMain`，运行时只依赖 Kotlin 标准库。公共契约测试位于 `commonTest`，JVM 并发测试位于 `jvmTest`。

## 构建配置

当前使用 Kotlin 2.4.10，语言和 API 级别为 Kotlin 2.2，JVM 字节码目标为 Java 11。发布工作流使用 JDK 21。

模块启用 `explicitApi()`，公开 API 显式声明类型和可见性。AST、词法分析器和解析器为内部实现。

默认配置 JVM、JS、Wasm JS/WASI、Linux x64/arm64，以及 iOS、macOS、tvOS、watchOS 的相关 arm64 目标。`-PjvmOnly` 是项目自定义参数：只要该属性存在，就只配置 JVM 目标。

## JVM 测试与 ABI 检查

在仓库根目录执行（Windows 可使用 `./gradlew.bat`）：

```shell
./gradlew :expression:jvmTest :expression:checkKotlinAbi -PjvmOnly
```

- `jvmTest` 运行公共测试与 JVM 专属测试，覆盖语法、浮点边界、错误区间、复杂度限制、重复求值、生成算例和并发调用。
- `checkKotlinAbi` 将公开 API 与 `expression/api/expression.api` 比较，存在差异时失败。
- `-PjvmOnly` 避免 ABI 检查触发其他平台编译；当前基线只覆盖 JVM。

有意修改公开 API 后，可以生成新基线并检查其差异：

```shell
./gradlew :expression:updateKotlinAbi -PjvmOnly
```

ABI 检查不能替代行为测试：运算优先级、结果和错误行为变化不一定改变 ABI。

## 本地快照

版本定义位于 `expression/build.gradle.kts`：

```kotlin
version = "0.1.0" + if (!providers.environmentVariable("CI").isPresent) "-SNAPSHOT" else ""
```

未设置 `CI` 时版本为 `0.1.0-SNAPSHOT`；只要环境变量存在，即使值为 `false`，版本也为 `0.1.0`。

发布 JVM 产物和多平台入口到本机 Maven 仓库：

```shell
./gradlew :expression:publishJvmPublicationToMavenLocal :expression:publishKotlinMultiplatformPublicationToMavenLocal
```

其他项目配置 `mavenLocal()` 后，可引用 `li.songe:expression:0.1.0-SNAPSHOT`。上述命令不发布 JS、Wasm 或 Native 平台产物。发布多平台入口时不传 `-PjvmOnly`，以保留完整的目标元数据。

## 发布工作流

当前 `release.yml` 由推送 `v*` 标签触发，在 `ubuntu-latest` 上依次执行 JVM 测试、JVM ABI 检查、签名密钥写入、Maven Central 发布和 GitHub Release 创建。Release 正文读取 `CHANGELOG.md`；发布 job 使用 `contents: write` 权限创建 Release。

标签用于触发流程，实际 Maven 版本来自构建脚本，不从标签自动提取。

工作流使用以下仓库 Secrets：

| Secret | 用途 |
|---|---|
| `OSSRH_USERNAME` | Maven Central 用户名 |
| `OSSRH_PASSWORD` | Maven Central 密码 |
| `OSSRH_GPG_SECRET_FILE_BASE64` | Base64 编码的签名私钥文件 |
| `OSSRH_GPG_SECRET_KEY_ID` | 签名密钥 ID |
| `OSSRH_GPG_SECRET_KEY_PASSWORD` | 签名密钥密码 |

私钥写入工作目录的 `secring.gpg`，签名配置引用该绝对路径；`.gitignore` 已忽略 `*.gpg`。构建脚本检测到 `signing.keyId` 后启用 Maven Central 发布和签名。

当前库的 Native 发布产物为纯 Kotlin `.klib`，没有 cinterop 或 CocoaPods 依赖，因此发布流程使用 Linux runner；这不代表在 Linux 上执行了 Apple 平台测试。

当前没有独立的 `test.yml`。发布流程中的验证在版本标签触发后执行。

## 验证范围

初始实现完成了 JVM 测试、JVM ABI 检查、本地 Maven 快照发布及独立 JVM 消费项目验证。其他目标的配置不代表已完成运行验证。

GKD 集成替换、真实订阅表达式与 Keval 的差分测试、性能基准以及 Android 设备验证不属于已完成的验证范围。
