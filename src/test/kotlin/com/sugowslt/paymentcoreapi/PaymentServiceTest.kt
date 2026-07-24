package com.sugowslt.paymentcoreapi

import com.sugowslt.paymentcoreapi.controller.dto.CreatePaymentRequest
import com.sugowslt.paymentcoreapi.entity.Payment
import com.sugowslt.paymentcoreapi.entity.PaymentMethod
import com.sugowslt.paymentcoreapi.entity.PaymentStatus
import com.sugowslt.paymentcoreapi.exception.DuplicatePaymentException
import com.sugowslt.paymentcoreapi.exception.InvalidPaymentStatusTransitionException
import com.sugowslt.paymentcoreapi.exception.PaymentNotFoundException
import com.sugowslt.paymentcoreapi.gateway.PaymentGateway
import com.sugowslt.paymentcoreapi.gateway.PaymentGatewayApprovalResult
import com.sugowslt.paymentcoreapi.gateway.PaymentGatewayCancellationResult
import com.sugowslt.paymentcoreapi.gateway.PaymentGatewayRejectedException
import com.sugowslt.paymentcoreapi.gateway.PaymentGatewayUnavailableException
import com.sugowslt.paymentcoreapi.repository.PaymentRepository
import com.sugowslt.paymentcoreapi.repository.PaymentCancellationRepository
import com.sugowslt.paymentcoreapi.service.PaymentService
import com.sugowslt.paymentcoreapi.service.PaymentOutboxService
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

    @MockK
    lateinit var paymentGateway: PaymentGateway

    @MockK(relaxed = true)
    lateinit var paymentOutboxService: PaymentOutboxService

    @MockK(relaxed = true)
    lateinit var paymentCancellationRepository: PaymentCancellationRepository

    lateinit var paymentService: PaymentService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { paymentCancellationRepository.findByCancellationIdempotencyKey(any()) } returns null
        every { paymentCancellationRepository.save(any()) } answers { firstArg() }
        paymentService = PaymentService(
            paymentRepository,
            paymentGateway,
            paymentOutboxService,
            paymentCancellationRepository,
        )
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
        every { paymentRepository.findByIdAndDeletedFalseForUpdate(10L) } returns
            Payment(
                id = 10,
                orderId = 111,
                idempotencyKey = "approve-unit-key",
                amount = BigDecimal("1000.00"),
                method = PaymentMethod.CARD,
                status = PaymentStatus.PENDING,
                createdAt = LocalDateTime.now(),
            )

        every { paymentGateway.approve(any()) } returns PaymentGatewayApprovalResult("provider-tx-10")

        val result = paymentService.approvePayment(10L, "approve-unit-key")

        assertEquals(PaymentStatus.APPROVED, result.status)
        verify(exactly = 1) { paymentGateway.approve(any()) }
        verify(exactly = 1) { paymentRepository.findByIdAndDeletedFalseForUpdate(10L) }
    }

    @Test
    fun `approve payment marks failed when gateway rejects payment`() {
        val payment = Payment(
            id = 13,
            orderId = 114,
            idempotencyKey = "payment-key-13",
            amount = BigDecimal("1300.00"),
            method = PaymentMethod.CARD,
            status = PaymentStatus.PENDING,
            createdAt = LocalDateTime.now(),
        )
        every { paymentRepository.findByIdAndDeletedFalseForUpdate(13L) } returns payment
        every { paymentGateway.approve(any()) } throws PaymentGatewayRejectedException("card declined")

        assertThrows(PaymentGatewayRejectedException::class.java) {
            paymentService.approvePayment(13L, "rejected-key-13")
        }

        assertEquals(PaymentStatus.FAILED, payment.status)
        assertEquals("rejected-key-13", payment.approvalIdempotencyKey)
    }

    @Test
    fun `approve payment keeps pending when gateway is temporarily unavailable`() {
        val payment = Payment(
            id = 14,
            orderId = 115,
            idempotencyKey = "payment-key-14",
            amount = BigDecimal("1400.00"),
            method = PaymentMethod.CARD,
            status = PaymentStatus.PENDING,
            createdAt = LocalDateTime.now(),
        )
        every { paymentRepository.findByIdAndDeletedFalseForUpdate(14L) } returns payment
        every { paymentGateway.approve(any()) } throws PaymentGatewayUnavailableException("gateway timeout")

        assertThrows(PaymentGatewayUnavailableException::class.java) {
            paymentService.approvePayment(14L, "retryable-key-14")
        }

        assertEquals(PaymentStatus.PENDING, payment.status)
        assertEquals(null, payment.approvalIdempotencyKey)
    }

    @Test
    fun `approve payment returns the existing response when approved with the same key`() {
        every { paymentRepository.findByIdAndDeletedFalseForUpdate(11L) } returns
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
        every { paymentRepository.findByIdAndDeletedFalseForUpdate(12L) } returns
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

        every { paymentGateway.cancel(any()) } returns PaymentGatewayCancellationResult("cancel-tx-20")

        val result = paymentService.cancelPayment(20L)

        assertEquals(PaymentStatus.CANCELED, result.status)
    }

    @Test
    fun `cancel payment returns existing response when canceled with same key`() {
        every { paymentRepository.findByIdAndDeletedFalse(22L) } returns
            Payment(
                id = 22,
                orderId = 224,
                idempotencyKey = "cancel-unit-key-22",
                cancellationIdempotencyKey = "cancel-key-22",
                amount = BigDecimal("2200.00"),
                method = PaymentMethod.CARD,
                status = PaymentStatus.CANCELED,
                createdAt = LocalDateTime.now(),
            )

        val result = paymentService.cancelPayment(22L, "  cancel-key-22  ")

        assertEquals(PaymentStatus.CANCELED, result.status)
        verify(exactly = 0) { paymentGateway.cancel(any()) }
    }

    @Test
    fun `cancel payment rejects a different key after cancellation`() {
        every { paymentRepository.findByIdAndDeletedFalse(23L) } returns
            Payment(
                id = 23,
                orderId = 225,
                idempotencyKey = "cancel-unit-key-23",
                cancellationIdempotencyKey = "original-cancel-key-23",
                amount = BigDecimal("2300.00"),
                method = PaymentMethod.CARD,
                status = PaymentStatus.CANCELED,
                createdAt = LocalDateTime.now(),
            )

        assertThrows(InvalidPaymentStatusTransitionException::class.java) {
            paymentService.cancelPayment(23L, "different-cancel-key-23")
        }

        verify(exactly = 0) { paymentGateway.cancel(any()) }
    }

    @Test
    fun `cancel payment keeps approved when gateway rejects cancellation`() {
        val payment = Payment(
            id = 21,
            orderId = 223,
            idempotencyKey = "cancel-unit-key-21",
            amount = BigDecimal("2100.00"),
            method = PaymentMethod.CARD,
            status = PaymentStatus.APPROVED,
            providerTransactionId = "provider-tx-21",
            createdAt = LocalDateTime.now(),
        )
        every { paymentRepository.findByIdAndDeletedFalse(21L) } returns payment
        every { paymentGateway.cancel(any()) } throws PaymentGatewayRejectedException("not cancelable")

        assertThrows(PaymentGatewayRejectedException::class.java) {
            paymentService.cancelPayment(21L)
        }

        assertEquals(PaymentStatus.APPROVED, payment.status)
    }

    @Test
    fun `approve payment throws conflict when status is not pending`() {
        every { paymentRepository.findByIdAndDeletedFalseForUpdate(30L) } returns
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
