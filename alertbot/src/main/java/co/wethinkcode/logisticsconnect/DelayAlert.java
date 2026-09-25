package co.wethinkcode.logisticsconnect;

public record DelayAlert(
        String hubId,
        int stage,
        String severity,
        String message,
        String generatedAt
) {
}
