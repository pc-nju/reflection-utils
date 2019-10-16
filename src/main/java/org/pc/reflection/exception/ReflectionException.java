package org.pc.reflection.exception;

public class ReflectionException extends RuntimeException {
    public ReflectionException() {
        super();
    }
    public ReflectionException(String msg) {
        super(msg);
    }
    public ReflectionException(String msg, Throwable cause) {
        super(msg, cause);
    }
    public ReflectionException(Throwable cause) {
        super(cause);
    }
}
