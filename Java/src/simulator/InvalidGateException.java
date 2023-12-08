package simulator;

public class InvalidGateException extends Exception {
    public InvalidGateException(String message) {
        super(message);
    }

    public InvalidGateException(String message, Throwable cause) {
        super(message, cause);
    }
}
