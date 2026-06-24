package net.jonasmf.auctionengine.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice(basePackageClasses = [AdminController::class])
class AdminControllerAdvice {
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(error: ResponseStatusException): ResponseEntity<Map<String, Any>> =
        ResponseEntity
            .status(error.statusCode)
            .body(
                mapOf(
                    "status" to error.statusCode.value(),
                    "detail" to (error.reason ?: error.statusCode.toString()),
                ),
            )
}
