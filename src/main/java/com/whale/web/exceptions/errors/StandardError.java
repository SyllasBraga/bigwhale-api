package com.whale.web.exceptions.errors;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class StandardError {

    private String timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private List<ErrorResponse> listFieldErrors;

    public StandardError(String timestamp, int status, String error, String message, String path) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public StandardError(String timestamp, int status, String error, String message, String path, List<ErrorResponse> listFieldErrors) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.listFieldErrors = listFieldErrors;
    }
}
