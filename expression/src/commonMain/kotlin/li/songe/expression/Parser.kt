package li.songe.expression

internal class Parser(source: String) {
    val variables: MutableSet<String> = linkedSetOf()
    val functions: MutableSet<String> = linkedSetOf()
    private val lexer = Lexer(source)
    private var token = lexer.next()
    private var nesting = 0

    fun parse(): ExpressionNode {
        val result = sum()
        expect(TokenKind.EOF)
        return result
    }

    private fun advance(): Token = token.also { token = lexer.next() }

    private fun expect(kind: TokenKind) {
        if (token.kind != kind) token.fail(ExpressionParseErrorCode.UNEXPECTED_TOKEN, "Expected $kind")
        if (kind != TokenKind.EOF) advance()
    }

    private fun checked(node: ExpressionNode, location: Token): ExpressionNode {
        if (node.depth > 128) location.fail(ExpressionParseErrorCode.LIMIT_EXCEEDED, "Expression tree exceeds depth 128")
        return node
    }

    private fun sum(): ExpressionNode {
        var left = product()
        while (token.kind == TokenKind.PLUS || token.kind == TokenKind.MINUS) {
            val operator = advance()
            left = checked(ExpressionNode.Binary.create(operator, left, product()), operator)
        }
        return left
    }

    private fun product(): ExpressionNode {
        var left = unary()
        while (token.kind == TokenKind.STAR || token.kind == TokenKind.SLASH || token.kind == TokenKind.PERCENT) {
            val operator = advance()
            left = checked(ExpressionNode.Binary.create(operator, left, unary()), operator)
        }
        return left
    }

    private fun unary(): ExpressionNode {
        if (++nesting > 128) token.fail(ExpressionParseErrorCode.LIMIT_EXCEEDED, "Parser nesting exceeds 128")
        val result = if (token.kind == TokenKind.PLUS || token.kind == TokenKind.MINUS) {
            val operator = advance()
            checked(ExpressionNode.Unary.create(operator, primary()), operator)
        } else {
            primary()
        }
        nesting--
        return result
    }

    private fun primary(): ExpressionNode = when (token.kind) {
        TokenKind.NUMBER -> {
            val number = advance()
            ExpressionNode.Number(number.text.toDouble())
        }
        TokenKind.IDENTIFIER -> {
            val name = advance()
            if (token.kind == TokenKind.LEFT) {
                call(name)
            } else {
                variables.add(name.text)
                ExpressionNode.Variable(name)
            }
        }
        TokenKind.LEFT -> {
            advance()
            val result = sum()
            expect(TokenKind.RIGHT)
            result
        }
        else -> token.fail(ExpressionParseErrorCode.UNEXPECTED_TOKEN, "Expected a number, variable, function or group")
    }

    private fun call(name: Token): ExpressionNode {
        functions.add(name.text)
        advance()
        val arguments = mutableListOf<ExpressionNode>()
        if (token.kind != TokenKind.RIGHT) {
            arguments.add(sum())
            while (token.kind == TokenKind.COMMA) {
                advance()
                arguments.add(sum())
            }
        }
        expect(TokenKind.RIGHT)
        return checked(ExpressionNode.Call(name, arguments), name)
    }
}
