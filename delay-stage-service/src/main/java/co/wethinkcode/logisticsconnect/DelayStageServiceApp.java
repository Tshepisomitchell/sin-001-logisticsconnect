package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

import javax.jms.JMSException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DelayStageServiceApp {

    private static final int PORT = 7052;
    private static final int MINIMUM_STAGE = 0;
    private static final int MAXIMUM_STAGE = 8;

    public static void main(String[] args) {
        Map<String, Integer> stages =
                new ConcurrentHashMap<>();

        PackageStatusPublisher publisher;

        try {
            publisher = new PackageStatusPublisher();
        } catch (JMSException exception) {
            System.err.println(
                    "Could not connect to ActiveMQ: " +
                            exception.getMessage()
            );
            return;
        }

        Runtime.getRuntime().addShutdownHook(
                new Thread(publisher::close)
        );

        Javalin app = Javalin.create().start(PORT);

        app.get(
                "/health",
                context -> context.result("OK")
        );

        app.get(
                "/delay-stage/{hubId}",
                context -> {
                    String hubId = normalizeHubId(
                            context.pathParam("hubId")
                    );

                    int stage = stages.getOrDefault(
                            hubId,
                            MINIMUM_STAGE
                    );

                    context.json(
                            new DelayStage(hubId, stage)
                    );
                }
        );

        app.post(
                "/delay-stage/{hubId}",
                context -> {
                    String hubId = normalizeHubId(
                            context.pathParam("hubId")
                    );

                    DelayStageRequest request;

                    try {
                        request = context.bodyAsClass(
                                DelayStageRequest.class
                        );
                    } catch (Exception exception) {
                        context.status(400).json(
                                Map.of(
                                        "error",
                                        "Request body must contain " +
                                                "a numeric stage"
                                )
                        );
                        return;
                    }

                    if (request.stage() == null) {
                        context.status(400).json(
                                Map.of(
                                        "error",
                                        "Stage is required"
                                )
                        );
                        return;
                    }

                    if (request.stage() < MINIMUM_STAGE ||
                            request.stage() > MAXIMUM_STAGE) {

                        context.status(400).json(
                                Map.of(
                                        "error",
                                        "Stage must be between 0 and 8",
                                        "received",
                                        request.stage()
                                )
                        );
                        return;
                    }

                    stages.put(hubId, request.stage());

                    PackageStatusEvent event =
                            new PackageStatusEvent(
                                    hubId,
                                    request.stage(),
                                    Instant.now().toString()
                            );

                    try {
                        publisher.publish(event);
                    } catch (Exception exception) {
                        context.status(502).json(
                                Map.of(
                                        "error",
                                        "Stage saved, but event " +
                                                "publishing failed",
                                        "details",
                                        exception.getMessage()
                                )
                        );
                        return;
                    }

                    context.json(
                            new DelayStage(
                                    hubId,
                                    request.stage()
                            )
                    );
                }
        );

        app.delete(
                "/delay-stage/{hubId}",
                context -> {
                    String hubId = normalizeHubId(
                            context.pathParam("hubId")
                    );

                    if (!stages.containsKey(hubId)) {
                        context.status(404).json(
                                Map.of(
                                        "error",
                                        "No delay stage recorded",
                                        "hubId",
                                        hubId
                                )
                        );
                        return;
                    }

                    stages.remove(hubId);

                    context.json(
                            Map.of(
                                    "message",
                                    "Delay stage removed",
                                    "hubId",
                                    hubId
                            )
                    );
                }
        );

        app.get(
                "/delay-stages",
                context -> context.json(stages)
        );

        System.out.printf(
                "Delay Stage Service running on port %d%n",
                PORT
        );
    }

    private static String normalizeHubId(String hubId) {
        return hubId.trim().toUpperCase();
    }
}