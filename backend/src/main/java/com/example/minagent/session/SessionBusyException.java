package com.example.minagent.session;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class SessionBusyException extends RuntimeException {

    public SessionBusyException(String message) {
        super(message);
    }
}
