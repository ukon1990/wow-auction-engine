package net.jonasmf.auctionengine.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice(basePackageClasses = [AdminController::class])
class AdminControllerAdvice {
    private val log = LoggerFactory.getLogger(AdminControllerAdvice::class.java)

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

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        exception: MethodArgumentNotValidException,
    ): ResponseEntity<ProblemDetail> {
        val fieldErrors = exception.bindingResult.fieldErrors
        val summary = validationSummary(fieldErrors)
        log.warn("Admin request validation failed (errorCount={}): {}", fieldErrors.size, summary)
        val problem =
            ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, summary).apply {
                setProperty("errorCount", fieldErrors.size)
                setProperty(
                    "errors",
                    fieldErrors.take(10).map { error ->
                        mapOf(
                            "field" to error.field,
                            "message" to (error.defaultMessage ?: "invalid"),
                        )
                    },
                )
            }
        return ResponseEntity.badRequest().body(problem)
    }

    private fun validationSummary(fieldErrors: List<FieldError>): String {
        if (fieldErrors.isEmpty()) return "Request validation failed."
        val preview =
            fieldErrors
                .take(3)
                .joinToString("; ") { error -> "${error.field}: ${error.defaultMessage}" }
        val suffix = if (fieldErrors.size > 3) " …" else ""
        return "Request validation failed (${fieldErrors.size} field errors). $preview$suffix"
    }
}
