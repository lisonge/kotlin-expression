package li.songe.expression

internal fun Char.isIdentifierStart(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this == '_'
internal fun Char.isIdentifierPart(): Boolean = isIdentifierStart() || this in '0'..'9'

internal enum class TokenKind {
    NUMBER, IDENTIFIER, PLUS, MINUS, STAR, SLASH, PERCENT, LEFT, RIGHT, COMMA, EOF,
}

internal class Token(val kind: TokenKind, val start: Int, val end: Int, val text: String) {
    fun fail(code: ExpressionParseErrorCode, message: String): Nothing =
        throw ExpressionParseException(code, start, end, message)

    fun evaluationFailure(code: ExpressionEvaluationErrorCode, message: String): Nothing =
        throw ExpressionEvaluationException(code, start, end, message)
}

internal class Lexer(private val source: String) {
    private var index = 0

    fun next(): Token {
        while (index < source.length && source[index] in " \t\r\n") index++
        val start = index
        if (index == source.length) return Token(TokenKind.EOF, index, index, "")
        val char = source[index++]
        val kind = when (char) {
            '+' -> TokenKind.PLUS
            '-' -> TokenKind.MINUS
            '*' -> TokenKind.STAR
            '/' -> TokenKind.SLASH
            '%' -> TokenKind.PERCENT
            '(' -> TokenKind.LEFT
            ')' -> TokenKind.RIGHT
            ',' -> TokenKind.COMMA
            in '0'..'9' -> {
                digits()
                if (index < source.length && source[index] == '.') {
                    index++
                    requireDigits(start)
                }
                if (index < source.length && source[index] in "eE") {
                    index++
                    if (index < source.length && source[index] in "+-") index++
                    requireDigits(start)
                }
                TokenKind.NUMBER
            }
            else -> when {
                char.isIdentifierStart() -> {
                    while (index < source.length && source[index].isIdentifierPart()) index++
                    while (index < source.length && source[index] == '.') {
                        val dot = index++
                        if (index == source.length || !source[index].isIdentifierStart()) {
                            throw ExpressionParseException(
                                ExpressionParseErrorCode.UNEXPECTED_TOKEN, dot, index,
                                "Expected an identifier immediately after '.'",
                            )
                        }
                        index++
                        while (index < source.length && source[index].isIdentifierPart()) index++
                    }
                    TokenKind.IDENTIFIER
                }
                char == '.' -> throw ExpressionParseException(
                    ExpressionParseErrorCode.INVALID_NUMBER, start, index, "A number must begin with a digit",
                )
                else -> throw ExpressionParseException(
                    ExpressionParseErrorCode.INVALID_CHARACTER, start, index, "Unsupported character '$char'",
                )
            }
        }
        val text = if (kind == TokenKind.NUMBER || kind == TokenKind.IDENTIFIER) source.substring(start, index) else ""
        return Token(kind, start, index, text)
    }

    private fun digits() {
        while (index < source.length && source[index] in '0'..'9') index++
    }

    private fun requireDigits(start: Int) {
        val before = index
        digits()
        if (before == index) throw ExpressionParseException(
            ExpressionParseErrorCode.INVALID_NUMBER, start, index, "Expected digits in numeric literal",
        )
    }
}
