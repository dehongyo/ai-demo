package com.example.minagent.tool.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class ExpressionParserTest {

    @Test
    void parsesSimpleAddition() {
        assertThat(new ExpressionParser("1+2").parse())
                .isEqualByComparingTo("3");
    }

    @Test
    void parsesSubtraction() {
        assertThat(new ExpressionParser("10-5").parse())
                .isEqualByComparingTo("5");
    }

    @Test
    void parsesMultiplication() {
        assertThat(new ExpressionParser("3*5").parse())
                .isEqualByComparingTo("15");
    }

    @Test
    void parsesDivision() {
        assertThat(new ExpressionParser("10/3").parse().doubleValue())
                .isCloseTo(3.333, withinPercentage(1.0));
    }

    @Test
    void respectsOperatorPrecedence() {
        assertThat(new ExpressionParser("2+3*4").parse())
                .isEqualByComparingTo("14");
    }

    @Test
    void parsesParentheses() {
        assertThat(new ExpressionParser("(2+3)*4").parse())
                .isEqualByComparingTo("20");
    }

    @Test
    void parsesNegativeNumbers() {
        assertThat(new ExpressionParser("-5").parse())
                .isEqualByComparingTo("-5");
    }

    @Test
    void parsesUnaryMinusInExpression() {
        assertThat(new ExpressionParser("5+-2").parse())
                .isEqualByComparingTo("3");
    }

    @Test
    void parsesDecimalNumbers() {
        assertThat(new ExpressionParser("(12.5+7.5)*3").parse())
                .isEqualByComparingTo("60");
    }

    @Test
    void stripsTrailingZeros() {
        assertThat(new ExpressionParser("12*5").parse())
                .isEqualByComparingTo("60");
    }

    @Test
    void divisionByZeroThrows() {
        assertThatThrownBy(() -> new ExpressionParser("10/0").parse())
                .hasMessage("DIVISION_BY_ZERO");
    }

    @Test
    void invalidExpressionThrows() {
        assertThatThrownBy(() -> new ExpressionParser("10+").parse())
                .hasMessageContaining("Expected number");
    }

    @Test
    void emptyExpressionThrows() {
        assertThatThrownBy(() -> new ExpressionParser("").parse())
                .hasMessage("Empty expression");
    }

    @Test
    void invalidCharactersThrows() {
        assertThatThrownBy(() -> new ExpressionParser("10+abc").parse())
                .isInstanceOf(ArithmeticException.class);
    }
}
