package com.sdui.state.expression

import kotlinx.serialization.json.JsonElement

/**
 * Abstract syntax tree for binding expressions.
 *
 * Expressions are compiled once into this tree and cached; evaluation is then a
 * plain tree walk with no string parsing, no `eval`, and no reflection — which is
 * both fast enough to run on every recomposition and safe to accept from a server.
 */
public sealed interface Expr {
    public data class Literal(val value: JsonElement) : Expr
    public data class Identifier(val name: String) : Expr
    public data class Member(val target: Expr, val name: String) : Expr
    public data class Index(val target: Expr, val index: Expr) : Expr
    public data class Call(val name: String, val args: List<Expr>) : Expr
    public data class Unary(val op: UnaryOp, val operand: Expr) : Expr
    public data class Binary(val op: BinaryOp, val left: Expr, val right: Expr) : Expr
    public data class Conditional(val condition: Expr, val ifTrue: Expr, val ifFalse: Expr) : Expr
    public data class ArrayLiteral(val items: List<Expr>) : Expr
    public data class ObjectLiteral(val entries: List<Pair<String, Expr>>) : Expr
}

public enum class UnaryOp { NOT, NEGATE }

public enum class BinaryOp {
    OR, AND, ELVIS,
    EQ, NEQ, LT, LTE, GT, GTE,
    ADD, SUB, MUL, DIV, MOD,
    IN, MATCHES,
}

public class ExpressionSyntaxException(
    public val expression: String,
    public val position: Int,
    message: String,
) : IllegalArgumentException("$message (at $position in \"$expression\")")

// ---------------------------------------------------------------------------
// Lexer
// ---------------------------------------------------------------------------

internal enum class TokenType {
    NUMBER, STRING, IDENT,
    LPAREN, RPAREN, LBRACKET, RBRACKET, LBRACE, RBRACE,
    COMMA, COLON, DOT, QUESTION, ELVIS,
    BANG, PLUS, MINUS, STAR, SLASH, PERCENT,
    EQ, NEQ, LT, LTE, GT, GTE, AND, OR,
    EOF,
}

internal data class Token(
    val type: TokenType,
    val text: String,
    val position: Int,
    val number: Double = 0.0,
)

internal class Lexer(private val source: String) {
    private var index = 0

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (true) {
            val token = next()
            tokens += token
            if (token.type == TokenType.EOF) break
        }
        return tokens
    }

    private fun next(): Token {
        while (index < source.length && source[index].isWhitespace()) index++
        if (index >= source.length) return Token(TokenType.EOF, "", index)

        val start = index
        val c = source[index]

        if (c.isDigit() || (c == '.' && index + 1 < source.length && source[index + 1].isDigit())) {
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
            val text = source.substring(start, index)
            val value = text.toDoubleOrNull()
                ?: throw ExpressionSyntaxException(source, start, "Malformed number '$text'")
            return Token(TokenType.NUMBER, text, start, value)
        }

        if (c == '\'' || c == '"') {
            index++
            val sb = StringBuilder()
            while (index < source.length && source[index] != c) {
                if (source[index] == '\\' && index + 1 < source.length) {
                    index++
                    sb.append(
                        when (source[index]) {
                            'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'
                            else -> source[index]
                        },
                    )
                } else {
                    sb.append(source[index])
                }
                index++
            }
            if (index >= source.length) {
                throw ExpressionSyntaxException(source, start, "Unterminated string literal")
            }
            index++
            return Token(TokenType.STRING, sb.toString(), start)
        }

        if (c.isLetter() || c == '_' || c == '$' || c == '@') {
            while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_' || source[index] == '$')) index++
            return Token(TokenType.IDENT, source.substring(start, index), start)
        }

        fun two(next: Char, type: TokenType, single: TokenType): Token {
            return if (index + 1 < source.length && source[index + 1] == next) {
                index += 2
                Token(type, source.substring(start, index), start)
            } else {
                index++
                Token(single, c.toString(), start)
            }
        }

        return when (c) {
            '(' -> { index++; Token(TokenType.LPAREN, "(", start) }
            ')' -> { index++; Token(TokenType.RPAREN, ")", start) }
            '[' -> { index++; Token(TokenType.LBRACKET, "[", start) }
            ']' -> { index++; Token(TokenType.RBRACKET, "]", start) }
            '{' -> { index++; Token(TokenType.LBRACE, "{", start) }
            '}' -> { index++; Token(TokenType.RBRACE, "}", start) }
            ',' -> { index++; Token(TokenType.COMMA, ",", start) }
            ':' -> { index++; Token(TokenType.COLON, ":", start) }
            '.' -> { index++; Token(TokenType.DOT, ".", start) }
            '+' -> { index++; Token(TokenType.PLUS, "+", start) }
            '-' -> { index++; Token(TokenType.MINUS, "-", start) }
            '*' -> { index++; Token(TokenType.STAR, "*", start) }
            '/' -> { index++; Token(TokenType.SLASH, "/", start) }
            '%' -> { index++; Token(TokenType.PERCENT, "%", start) }
            '?' -> when {
                index + 1 < source.length && (source[index + 1] == ':' || source[index + 1] == '?') -> {
                    index += 2; Token(TokenType.ELVIS, "?:", start)
                }
                else -> { index++; Token(TokenType.QUESTION, "?", start) }
            }
            '!' -> two('=', TokenType.NEQ, TokenType.BANG)
            '=' -> {
                // Both `==` and a lone `=` are treated as equality; authors write both.
                if (index + 1 < source.length && source[index + 1] == '=') index += 2 else index++
                Token(TokenType.EQ, "==", start)
            }
            '<' -> two('=', TokenType.LTE, TokenType.LT)
            '>' -> two('=', TokenType.GTE, TokenType.GT)
            '&' -> {
                if (index + 1 < source.length && source[index + 1] == '&') index += 2 else index++
                Token(TokenType.AND, "&&", start)
            }
            '|' -> {
                if (index + 1 < source.length && source[index + 1] == '|') index += 2 else index++
                Token(TokenType.OR, "||", start)
            }
            else -> throw ExpressionSyntaxException(source, start, "Unexpected character '$c'")
        }
    }
}

// ---------------------------------------------------------------------------
// Parser (precedence climbing)
// ---------------------------------------------------------------------------

internal class ExprParser(private val source: String) {
    private val tokens = Lexer(source).tokenize()
    private var position = 0

    fun parse(): Expr {
        val expr = parseTernary()
        expect(TokenType.EOF, "Unexpected trailing input")
        return expr
    }

    private fun peek(): Token = tokens[position]

    private fun advance(): Token = tokens[position++]

    private fun match(vararg types: TokenType): Boolean =
        if (peek().type in types) { position++; true } else false

    private fun expect(type: TokenType, message: String): Token {
        val token = peek()
        if (token.type != type) throw ExpressionSyntaxException(source, token.position, message)
        return advance()
    }

    private fun parseTernary(): Expr {
        val condition = parseElvis()
        if (match(TokenType.QUESTION)) {
            val ifTrue = parseTernary()
            expect(TokenType.COLON, "Expected ':' in conditional expression")
            val ifFalse = parseTernary()
            return Expr.Conditional(condition, ifTrue, ifFalse)
        }
        return condition
    }

    private fun parseElvis(): Expr {
        var left = parseOr()
        while (match(TokenType.ELVIS)) left = Expr.Binary(BinaryOp.ELVIS, left, parseOr())
        return left
    }

    private fun parseOr(): Expr {
        var left = parseAnd()
        while (match(TokenType.OR)) left = Expr.Binary(BinaryOp.OR, left, parseAnd())
        return left
    }

    private fun parseAnd(): Expr {
        var left = parseEquality()
        while (match(TokenType.AND)) left = Expr.Binary(BinaryOp.AND, left, parseEquality())
        return left
    }

    private fun parseEquality(): Expr {
        var left = parseComparison()
        while (true) {
            val op = when {
                match(TokenType.EQ) -> BinaryOp.EQ
                match(TokenType.NEQ) -> BinaryOp.NEQ
                else -> return left
            }
            left = Expr.Binary(op, left, parseComparison())
        }
    }

    private fun parseComparison(): Expr {
        var left = parseAdditive()
        while (true) {
            val op = when {
                match(TokenType.LT) -> BinaryOp.LT
                match(TokenType.LTE) -> BinaryOp.LTE
                match(TokenType.GT) -> BinaryOp.GT
                match(TokenType.GTE) -> BinaryOp.GTE
                peek().type == TokenType.IDENT && peek().text == "in" -> { advance(); BinaryOp.IN }
                peek().type == TokenType.IDENT && peek().text == "matches" -> { advance(); BinaryOp.MATCHES }
                else -> return left
            }
            left = Expr.Binary(op, left, parseAdditive())
        }
    }

    private fun parseAdditive(): Expr {
        var left = parseMultiplicative()
        while (true) {
            val op = when {
                match(TokenType.PLUS) -> BinaryOp.ADD
                match(TokenType.MINUS) -> BinaryOp.SUB
                else -> return left
            }
            left = Expr.Binary(op, left, parseMultiplicative())
        }
    }

    private fun parseMultiplicative(): Expr {
        var left = parseUnary()
        while (true) {
            val op = when {
                match(TokenType.STAR) -> BinaryOp.MUL
                match(TokenType.SLASH) -> BinaryOp.DIV
                match(TokenType.PERCENT) -> BinaryOp.MOD
                else -> return left
            }
            left = Expr.Binary(op, left, parseUnary())
        }
    }

    private fun parseUnary(): Expr = when {
        match(TokenType.BANG) -> Expr.Unary(UnaryOp.NOT, parseUnary())
        match(TokenType.MINUS) -> Expr.Unary(UnaryOp.NEGATE, parseUnary())
        else -> parsePostfix()
    }

    private fun parsePostfix(): Expr {
        var expr = parsePrimary()
        while (true) {
            expr = when {
                match(TokenType.DOT) -> {
                    val name = expect(TokenType.IDENT, "Expected property name after '.'")
                    Expr.Member(expr, name.text)
                }

                match(TokenType.LBRACKET) -> {
                    val index = parseTernary()
                    expect(TokenType.RBRACKET, "Expected ']'")
                    Expr.Index(expr, index)
                }

                else -> return expr
            }
        }
    }

    private fun parsePrimary(): Expr {
        val token = peek()
        return when (token.type) {
            TokenType.NUMBER -> {
                advance()
                Expr.Literal(kotlinx.serialization.json.JsonPrimitive(token.number))
            }

            TokenType.STRING -> {
                advance()
                Expr.Literal(kotlinx.serialization.json.JsonPrimitive(token.text))
            }

            TokenType.IDENT -> {
                advance()
                when (token.text) {
                    "true" -> Expr.Literal(kotlinx.serialization.json.JsonPrimitive(true))
                    "false" -> Expr.Literal(kotlinx.serialization.json.JsonPrimitive(false))
                    "null" -> Expr.Literal(kotlinx.serialization.json.JsonNull)
                    else ->
                        if (peek().type == TokenType.LPAREN) {
                            advance()
                            val args = mutableListOf<Expr>()
                            if (peek().type != TokenType.RPAREN) {
                                do { args += parseTernary() } while (match(TokenType.COMMA))
                            }
                            expect(TokenType.RPAREN, "Expected ')' to close call to '${token.text}'")
                            Expr.Call(token.text, args)
                        } else {
                            Expr.Identifier(token.text)
                        }
                }
            }

            TokenType.LPAREN -> {
                advance()
                val inner = parseTernary()
                expect(TokenType.RPAREN, "Expected ')'")
                inner
            }

            TokenType.LBRACKET -> {
                advance()
                val items = mutableListOf<Expr>()
                if (peek().type != TokenType.RBRACKET) {
                    do { items += parseTernary() } while (match(TokenType.COMMA))
                }
                expect(TokenType.RBRACKET, "Expected ']'")
                Expr.ArrayLiteral(items)
            }

            TokenType.LBRACE -> {
                advance()
                val entries = mutableListOf<Pair<String, Expr>>()
                if (peek().type != TokenType.RBRACE) {
                    do {
                        val key = advance()
                        if (key.type != TokenType.IDENT && key.type != TokenType.STRING) {
                            throw ExpressionSyntaxException(source, key.position, "Expected object key")
                        }
                        expect(TokenType.COLON, "Expected ':' after object key")
                        entries += key.text to parseTernary()
                    } while (match(TokenType.COMMA))
                }
                expect(TokenType.RBRACE, "Expected '}'")
                Expr.ObjectLiteral(entries)
            }

            else -> throw ExpressionSyntaxException(
                source, token.position, "Unexpected token '${token.text}'",
            )
        }
    }
}
