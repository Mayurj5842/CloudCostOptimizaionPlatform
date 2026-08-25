package com.cloudcostai.aws.handler;

import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.dao.DataAccessException;

@RestControllerAdvice
public class AwsExceptionHandler {

    @ExceptionHandler(AwsConnectionException.class)
    public ResponseEntity<ApiResponse<Void>> handleAwsConnectionException(
            AwsConnectionException exception
    ) {

        ApiResponse<Void> response = new ApiResponse<>(
                false,
                exception.getMessage(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

        @ExceptionHandler(MissingRequestHeaderException.class)
        public ResponseEntity<ApiResponse<Void>> handleMissingConnectionToken() {
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                                false,
                                "AWS connection is not available. Please connect an AWS account first.",
                                null
                ));
        }

        @ExceptionHandler(DataAccessException.class)
        public ResponseEntity<ApiResponse<Void>> handleDatabaseException() {
                return ResponseEntity.internalServerError().body(new ApiResponse<>(
                                false,
                                "AWS was verified, but the account details could not be saved. Please verify the database migration is applied.",
                                null
                ));
        }
}