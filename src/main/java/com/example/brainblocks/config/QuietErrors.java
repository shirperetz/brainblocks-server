package com.example.brainblocks.config;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 2026-11-21 (security pass): a broken request used to get the framework's own message back
 * ("Cannot deserialize value of type java.lang.Long ..."), which tells a stranger how the server is
 * built. Now: a short neutral message; the details stay in the server log. Our own Hebrew messages
 * (ResponseStatusException) are untouched.
 */
@RestControllerAdvice
public class QuietErrors {
    private static final Logger log = LoggerFactory.getLogger("brainblocks.errors");

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception e, HttpServletRequest req) {
        log.debug("bad request {}: {}", req.getRequestURI(), e.getMessage());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", OffsetDateTime.now().toString());
        m.put("status", 400);
        m.put("error", "Bad Request");
        m.put("message", "הבקשה לא תקינה");
        m.put("path", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(m);
    }
}
