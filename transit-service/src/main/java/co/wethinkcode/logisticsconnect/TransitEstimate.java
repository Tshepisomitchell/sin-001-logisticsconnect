package co.wethinkcode.logisticsconnect;

public record TransitEstimate(
        String hubId,
        String sortingCenter,
        String province,
        int delayStage,
        int estimatedMinutes,
        String earliestArrival,
        String latestArrival
) {
}
