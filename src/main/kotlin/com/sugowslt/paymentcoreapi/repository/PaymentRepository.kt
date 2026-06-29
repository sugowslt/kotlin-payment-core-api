package com.sugowslt.paymentcoreapi.repository

import com.sugowslt.paymentcoreapi.entity.Payment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PaymentRepository : JpaRepository<Payment, Long> {

    fun existsByIdempotencyKeyAndDeletedFalse(idempotencyKey: String): Boolean

    fun findAllByDeletedFalseOrderByCreatedAtDesc(pageable: Pageable): Page<Payment>

    fun findByIdAndDeletedFalse(id: Long): Payment?

    @Query("select p from Payment p where p.deleted = false and (:cursorId is null or p.id < :cursorId) order by p.id desc")
    fun findByCursorAndDeletedFalse(@Param("cursorId") cursorId: Long?, pageable: Pageable): List<Payment>

    @Modifying
    @Query("update Payment p set p.deleted = true, p.updatedAt = current_timestamp where p.id = :id and p.deleted = false")
    fun softDelete(@Param("id") id: Long): Int
}
