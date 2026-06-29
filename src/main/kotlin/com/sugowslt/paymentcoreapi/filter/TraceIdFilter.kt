package com.sugowslt.paymentcoreapi.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class TraceIdFilter : OncePerRequestFilter() {

    companion object {
        const val TRACE_ID_HEADER = "X-Trace-Id"
        const val TRACE_ID_ATTRIBUTE = "traceId"
    }

    private val traceLogger = LoggerFactory.getLogger(TraceIdFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt = System.currentTimeMillis()
        val incomingTraceId = request.getHeader(TRACE_ID_HEADER)
        val traceId = if (StringUtils.hasText(incomingTraceId)) {
            incomingTraceId
        } else {
            UUID.randomUUID().toString().replace("-", "")
        }

        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId)
        response.setHeader(TRACE_ID_HEADER, traceId)

        traceLogger.info("request.start traceId={} method={} path={}", traceId, request.method, request.requestURI)

        try {
            filterChain.doFilter(request, response)
        } finally {
            val elapsedMs = System.currentTimeMillis() - startedAt
            traceLogger.info(
                "request.end traceId={} method={} path={} status={} elapsedMs={}",
                traceId,
                request.method,
                request.requestURI,
                response.status,
                elapsedMs,
            )
        }
    }
}
