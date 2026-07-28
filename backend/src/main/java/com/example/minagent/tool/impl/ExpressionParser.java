package com.example.minagent.tool.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Recursive-descent parser for arithmetic expressions.
 * Supports: +, -, *, /, parentheses, decimals, unary minus.
 * Uses BigDecimal for precise arithmetic.
 */
public class ExpressionParser {

    private final String input;
    private int pos;
    private char ch;

    public ExpressionParser(String input) {
        this.input = input != null ? input.trim() : "";
        this.pos = 0;
        this.ch = this.input.isEmpty() ? (char) -1 : this.input.charAt(0);
        if (!this.input.isEmpty() && !isValidChar(this.ch)) {
            throw new ArithmeticException("Invalid character: '" + this.ch + "'");
        }
    }

    public BigDecimal parse() {
        if (input.isEmpty()) {
            throw new ArithmeticException("Empty expression");
        }
        BigDecimal result = expression();
        if (pos < input.length()) {
            throw new ArithmeticException("Unexpected character: '" + ch + "' at position " + pos);
        }
        return result.stripTrailingZeros();
    }

    private BigDecimal expression() {
        BigDecimal left = term();
        while (ch == '+' || ch == '-') {
            char op = ch;
            nextChar();
            BigDecimal right = term();
            if (op == '+') {
                left = left.add(right);
            } else {
                left = left.subtract(right);
            }
        }
        return left;
    }

    private BigDecimal term() {
        BigDecimal left = factor();
        while (ch == '*' || ch == '/') {
            char op = ch;
            nextChar();
            BigDecimal right = factor();
            if (op == '*') {
                left = left.multiply(right);
            } else {
                if (right.compareTo(BigDecimal.ZERO) == 0) {
                    throw new ArithmeticException("DIVISION_BY_ZERO");
                }
                left = left.divide(right, 20, RoundingMode.HALF_UP);
            }
        }
        return left;
    }

    private BigDecimal factor() {
        if (ch == '+') {
            nextChar();
            return factor();
        }
        if (ch == '-') {
            nextChar();
            return factor().negate();
        }
        if (ch == '(') {
            nextChar();
            BigDecimal result = expression();
            expect(')');
            return result;
        }
        return number();
    }

    private BigDecimal number() {
        int start = pos;
        while (ch >= '0' && ch <= '9' || ch == '.') {
            nextChar();
        }
        if (start == pos) {
            throw new ArithmeticException("Expected number at position " + pos);
        }
        String numStr = input.substring(start, pos);
        // Validate not multiple dots
        long dotCount = numStr.chars().filter(c -> c == '.').count();
        if (dotCount > 1) {
            throw new ArithmeticException("Invalid number: " + numStr);
        }
        return new BigDecimal(numStr);
    }

    private void nextChar() {
        pos++;
        ch = pos < input.length() ? input.charAt(pos) : (char) -1;
    }

    private void expect(char expected) {
        if (ch == expected) {
            nextChar();
        } else {
            throw new ArithmeticException("Expected '" + expected + "' at position " + pos
                    + " but found '" + (ch == (char) -1 ? "EOF" : String.valueOf(ch)) + "'");
        }
    }

    private boolean isValidChar(char c) {
        return (c >= '0' && c <= '9') || c == '+' || c == '-' || c == '*'
                || c == '/' || c == '(' || c == ')' || c == '.' || c == ' ';
    }
}
