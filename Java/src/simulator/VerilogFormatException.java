package simulator;

public class VerilogFormatException extends Exception {
    public VerilogFormatException(String message) {
        super(message);
    }

    public VerilogFormatException(String message, int lineNumber) {
        super(message + " Parse Line: "+lineNumber);
    }

    public VerilogFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
