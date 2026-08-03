package com.sugowslt.paymentcoreapi.settlement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SettlementAmountCalculatorTest {
    private val calculator = SettlementAmountCalculator()

    @Test
    fun `calculates fee and settlement amount with basis point rate`() {
        val result = calculator.calculate(BigDecimal("1000.00"), feeRateBps = 300)

        assertEquals(BigDecimal("1000.00"), result.grossAmount)
        assertEquals(300, result.feeRateBps)
        assertEquals(BigDecimal("30.00"), result.feeAmount)
        assertEquals(BigDecimal("970.00"), result.settlementAmount)
    }

    @Test
    fun `rounds fee half up and keeps the total balanced`() {
        val result = calculator.calculate(BigDecimal("1000.01"), feeRateBps = 333)

        assertEquals(BigDecimal("33.30"), result.feeAmount)
        assertEquals(BigDecimal("966.71"), result.settlementAmount)
        assertEquals(result.grossAmount, result.feeAmount.add(result.settlementAmount))
    }

    @Test
    fun `zero fee rate leaves the full amount for settlement`() {
        val result = calculator.calculate(BigDecimal("1200"), feeRateBps = 0)

        assertEquals(BigDecimal("1200.00"), result.grossAmount)
        assertEquals(BigDecimal("0.00"), result.feeAmount)
        assertEquals(BigDecimal("1200.00"), result.settlementAmount)
    }

    @Test
    fun `one hundred percent fee leaves no settlement amount`() {
        val result = calculator.calculate(BigDecimal("1200.50"), feeRateBps = 10_000)

        assertEquals(BigDecimal("1200.50"), result.feeAmount)
        assertEquals(BigDecimal("0.00"), result.settlementAmount)
    }

    @Test
    fun `same input produces the same calculation for replay`() {
        val first = calculator.calculate(BigDecimal("777.77"), feeRateBps = 275)
        val second = calculator.calculate(BigDecimal("777.77"), feeRateBps = 275)

        assertEquals(first, second)
    }

    @Test
    fun `rejects zero or negative amount`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.calculate(BigDecimal.ZERO, feeRateBps = 300)
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculator.calculate(BigDecimal("-1.00"), feeRateBps = 300)
        }
    }

    @Test
    fun `rejects fee rate outside percentage range`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.calculate(BigDecimal("100.00"), feeRateBps = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculator.calculate(BigDecimal("100.00"), feeRateBps = 10_001)
        }
    }

    @Test
    fun `rejects amount with more than two decimal places`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.calculate(BigDecimal("100.001"), feeRateBps = 300)
        }
    }
}
