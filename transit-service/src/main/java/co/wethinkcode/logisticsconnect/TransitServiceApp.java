package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import javax.jms.JMSException;

public class TransitServiceApp {

    private static final int PORT = 7053;
    private static final int BASE_TRANSIT_MINUTES = 60;
    private static final int MINUTES_PER_DELAY_STAGE = 30;
    private static final int ARRIVAL_WINDOW_MINUTES = 30;

    public static void main(String[] args) {
        LogisticsServiceClient client =
                new LogisticsServiceClient();

        PackageStatusSubscriber subscriber;

        try {
            subscriber = new PackageStatusSubscriber();
        } catch (JMSException exception) {
            System.err.println(
                    "Could not connect to ActiveMQ: " +
                            exception.getMessage()
            );
            return;
        }

        Runtime.getRuntime().addShutdownHook(
                new Thread(subscriber::close)
        );

        Javalin app = Javalin.create().start(PORT);

        app.get(
                "/health",
                context -> context.result("OK")
        );

        app.get(
                "/transit/{hubId}/eta",
                context -> {
                    String hubId = context
                            .pathParam("hubId")
                            .trim()
                            .toUpperCase();

                    try {
                        Hub hub = client.fetchHub(hubId);

                        if (hub == null) {
                            context.status(404).json(
                                    Map.of(
                                            "error",
                                            "Unknown hub",
                                            "hubId",
                                            hubId
                                    )
                            );
                            return;
                        }

                        if (!hub.active()) {
                            context.status(409).json(
                                    Map.of(
                                            "error",
                                            "Hub is inactive",
                                            "hubId",
                                            hubId
                                    )
                            );
                            return;
                        }

                        Integer delayStage =
                                subscriber.getStage(hubId);

                        if (delayStage == null) {
                            context.status(503).json(
                                    Map.of(
                                            "error",
                                            "No delay-stage event received yet",
                                            "hubId",
                                            hubId
                                    )
                            );
                            return;
                        }

                        int estimatedMinutes =
                                BASE_TRANSIT_MINUTES +
                                        delayStage *
                                                MINUTES_PER_DELAY_STAGE;

                        Instant earliest = Instant.now()
                                .plus(
                                        Duration.ofMinutes(
                                                estimatedMinutes
                                        )
                                );

                        Instant latest = earliest.plus(
                                Duration.ofMinutes(
                                        ARRIVAL_WINDOW_MINUTES
                                )
                        );

                        TransitEstimate estimate =
                                new TransitEstimate(
                                        hub.hubId(),
                                        hub.sortingCenter(),
                                        hub.province(),
                                        delayStage,
                                        estimatedMinutes,
                                        earliest.toString(),
                                        latest.toString()
                                );

                        context.json(estimate);

                    } catch (IOException exception) {
                        context.status(502).json(
                                Map.of(
                                        "error",
                                        "A downstream service " +
                                                "is unavailable",
                                        "details",
                                        exception.getMessage()
                                )
                        );

                    } catch (
                            InterruptedException exception
                    ) {
                        Thread.currentThread().interrupt();

                        context.status(503).json(
                                Map.of(
                                        "error",
                                        "Transit request interrupted"
                                )
                        );
                    }
                }
        );

        System.out.printf(
                "Transit Service running on port %d%n",
                PORT
        );
    }
}