package co.wethinkcode.logisticsconnect;

public record PackageStatusEvent(
        String hubId,
        int stage,
        String timestamp
) {
}