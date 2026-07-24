package com.sugowslt.paymentcoreapi

import com.sugowslt.paymentcoreapi.controller.dto.CreatePaymentRequest
import com.sugowslt.paymentcoreapi.entity.Payment
import com.sugowslt.paymentcoreapi.entity.PaymentMethod
import com.sugowslt.paymentcoreapi.entity.PaymentStatus
import com.sugowslt.paymentcoreapi.exception.DuplicatePaymentException
import com.sugowslt.paymentcoreapi.exception.InvalidPaymentStatusTransitionException
import com.sugowslt.paymentcoreapi.exception.PaymentNotFoundException
import com.sugowslt.paymentcoreapi.repository.PaymentRepository
import com.sugowslt.paymentcoreapi.service.PaymentService
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class PaymentServiceTest {

    @MockK
    lateinit var paymentRepository: PaymentRepository

    lateinit var paymentService: PaymentService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        paymentService = PaymentService(paymentRepository)
    }

    @Test
    fun `create payment returns response when request is valid`() {
        val request =
            CreatePaymentRequest(
                orderId = 101,
                idempotencyKey = "key-101",
                amount = BigDecimal("1200.50"),
                method = PaymentMethod.CARD,
            )

        every { paymentRepository.existsByIdempotencyKeyAndDeletedFalse("key-101") } returns false
        every { paymentRepository.save(any()) } returns
            Payment(
                id = 1,
                orderId = 101,
                idempotencyKey = "key-101",
                amount = BigDecimal("1200.50"),
                method = PaymentMethod.CARD,
                status = PaymentStatus.PENDING,
                createdAt = LocalDateTime.now(),
            )

        val result = paymentService.createPayment(request)

        assertEquals(1, result.id)
        assertEquals(101, result.orderId)
        assertEquals("key-101", result.idempotencyKey)
        assertEquals(PaymentStatus.PENDING, result.status)
        verify(exactly = 1) { paymentRepository.save(any()) }
    }

    @Test
    fun `create payment throws duplicate exception when idempotency key exists`() {
        val request =
            CreatePaymentRequest(
                orderId = 202,
                idempotencyKey = "dup-key",
                amount = BigDecimal("300.00"),
                method = PaymentMethod.BANK_TRANSFER,
            )

        every { paymentRepository.existsByIdempotencyKeyAndDeletedFalse("dup-key") } returns true

        assertThrows(DuplicatePaymentException::class.java) {
            paymentService.createPayment(request)
        }

        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    @Test
    fun `create payment trims idempotency key before duplicate check and save`() {
        val request =
            CreatePaymentRequest(
                orderId = 303,
                idempotencyKey = "  trim-key  ",
                amount = BigDecimal("77.00"),
                method = PaymentMethod.ACCOUNT_BALANCE,
            )

        every { paymentRepository.existsByIdempotencyKeyAndDeletedFalse("trim-key") } returns false
        every { paymentRepository.save(any()) } answers {
            val payment = firstArg<Payment>()
            Payment(
                id = 3,
                orderId = payment.orderId,
                idempotencyKey = payment.idempotencyKey,
                amount = payment.amount,
                method = payment.method,
                status = PaymentStatus.PENDING,
                createdAt = LocalDateTime.now(),
            )
        }

        val result = paymentService.createPayment(request)

        verify(exactly = 1) { paymentRepository.existsByIdempotencyKeyAndDeletedFalse("trim-key") }
        val paymentSlot = slot<Payment>()
        verify(exactly = 1) { paymentRepository.save(capture(paymentSlot)) }
        assertEquals("trim-key", paymentSlot.captured.idempotencyKey)
        assertEquals("trim-key", result.idempotencyKey)
    }

    @Test
    fun `approve payment changes status from pending to approved`() {
        every { paymentRepository.findByIdAndDeletedFalse(10L) } returns
            Payment(
                id = 10,
                orderId = 111,
                idempotencyKey = "approve-unit-key",
                amount = BigDecimal("1000.00"),
                method = PaymentMethod.CARD,
                status = PaymentStatus.PENDING,
                createdAt = LocalDateTime.now(),
            )

        val result = paymentService.approvePayment(10L, "approve-unit-key")

        assertEquals(PaymentStatus.APPROVED, result.status)
    }

    @Test
    fun `approve payment returns the existing response when approved with the same key`() {
        every { paymentRepository.findByIdAndDeletedFalse(11L) } returns
            Payment(
                id = 11,
                orderId = 112,
                idempotencyKey = "payment-key-11",
                approvalIdempotencyKey = "retry-key-11",
                amount = BigDecimal("1100.00"),
                method = PaymentMethod.CARD,
                status = PaymentStatus.APPROVED,
                createdAt = LocalDateTime.now(),
            )

        val result = paymentService.approvePayment(11L, "  retry-key-11  ")

        assertEquals(11L, result.id)
        assertEquals(PaymentStatus.APPROVED, result.status)
    }

    @Test
    fun `approve payment rejects a different key after approval`() {
        every { paymentRepository.findByIdAndDeletedFalse(12L) } returns
            Payment(
                id = 12,
                orderId = 113,
                idempotencyKey = "payment-key-12",
                approvalIdempotencyKey = "original-key-12",
                amount = BigDecimal("1200.00"),
                method = PaymentMethod.CARD,
                status = PaymentStatus.APPROVED,
                createdAt = LocalDateTime.now(),
            )

        assertThrows(InvalidPaymentStatusTransitionException::class.java) {
            paymentService.approvePayment(12L, "different-key-12")
        }
    }

    @Test
    fun `cancel payment changes status from approved to canceled`() {
        every { paymentRepository.findByIdAndDeletedFalse(20L) } returns
            Payment(
                id = 20,
                orderId = 222,
                idempotencyKey = "cancel-unit-key",
                amount = BigDecimal("2000.00"),
                method = PaymentMethod.BANK_TRANSFER,
                status = PaymentStatus.APPROVED,
                createdAt = LocalDateTime.now(),
            )

        val result = paymentService.cancelPayment(20L)

        assertEquals(PaymentStatus.CANCELED, result.status)
    }

    @Test
    fun `approve payment throws conflict when status is not pending`() {
        every { paymentRepository.findByIdAndDeletedFalse(30L) } returns
            Payment(
                id = 30,
                orderId = 333,
                idempotencyKey = "approve-conflict-key",
                amount = BigDecimal("3000.00"),
                method = PaymentMethod.CARD,
                status = PaymentStatus.CANCELED,
                createdAt = LocalDateTime.now(),
            )

        assertThrows(InvalidPaymentStatusTransitionException::class.java) {
            paymentService.approvePayment(30L, "approve-conflict-key")
        }
    }

    @Test
    fun `cancel payment throws not found when payment does not exist`() {
        every { paymentRepository.findByIdAndDeletedFalse(999L) } returns null

        assertThrows(PaymentNotFoundException::class.java) {
            paymentService.cancelPayment(999L)
        }
    }
}
