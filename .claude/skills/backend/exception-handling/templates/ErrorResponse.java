// Shared error response — re-export from libs/java-web, do not redeclare per service.

public record ErrorResponse(String code, String message, String timestamp) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now().toString());
    }
}
