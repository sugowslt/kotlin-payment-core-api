package com.sugowslt.paymentcoreapi.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {

    @Bean
    fun paymentCoreOpenAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Kotlin Payment Core API")
                    .version("v1")
                    .description(
                        "결제 생성·조회·승인·취소와 웹훅 보정, 트랜잭션 아웃박스 흐름을 검증하는 결제 코어 API입니다.",
                    ),
            )
}
