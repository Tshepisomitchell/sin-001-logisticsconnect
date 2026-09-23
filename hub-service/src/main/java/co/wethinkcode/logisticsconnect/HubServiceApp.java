package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HubServiceApp {

    private static final int PORT = 7051;

    public static void main(String[] args) {
        IngestionServiceClient client =
                new IngestionServiceClient();

        List<Hub> hubs = new ArrayList<>();

        try {
            hubs.addAll(client.fetchHubs());

            System.out.printf(
                    "Loaded %d hubs from ingestion-service%n",
                    hubs.size()
            );

        } catch (IOException exception) {
            System.err.println(
                    "Could not load hubs: " +
                            exception.getMessage()
            );
            return;

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Hub loading was interrupted");
            return;
        }

        Javalin app = Javalin.create().start(PORT);

        app.get(
                "/health",
                context -> context.result("OK")
        );

        app.get(
                "/hubs",
                context -> context.json(hubs)
        );

        app.get(
                "/hubs/{hubId}",
                context -> {
                    String requestedId =
                            context.pathParam("hubId");

                    Hub hub = findById(hubs, requestedId);

                    if (hub == null) {
                        context.status(404).json(
                                Map.of(
                                        "error",
                                        "Hub not found",
                                        "hubId",
                                        requestedId
                                )
                        );
                        return;
                    }

                    context.json(hub);
                }
        );

        app.get(
                "/provinces",
                context -> {
                    List<String> provinces = hubs.stream()
                            .map(Hub::province)
                            .distinct()
                            .sorted()
                            .toList();

                    context.json(provinces);
                }
        );

        app.get(
                "/provinces/{province}/hubs",
                context -> {
                    String requestedProvince =
                            context.pathParam("province");

                    List<Hub> matches = hubs.stream()
                            .filter(hub ->
                                    hub.province()
                                            .equalsIgnoreCase(
                                                    requestedProvince
                                            )
                            )
                            .toList();

                    if (matches.isEmpty()) {
                        context.status(404).json(
                                Map.of(
                                        "error",
                                        "Province not found",
                                        "province",
                                        requestedProvince
                                )
                        );
                        return;
                    }

                    context.json(matches);
                }
        );

        app.post(
                "/refresh",
                context -> {
                    try {
                        List<Hub> refreshedHubs =
                                client.fetchHubs();

                        hubs.clear();
                        hubs.addAll(refreshedHubs);

                        context.json(
                                Map.of(
                                        "message",
                                        "Hub data refreshed",
                                        "count",
                                        hubs.size()
                                )
                        );

                    } catch (IOException exception) {
                        context.status(502).json(
                                Map.of(
                                        "error",
                                        "Ingestion service unavailable",
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
                                        "Refresh interrupted"
                                )
                        );
                    }
                }
        );

        System.out.printf(
                "Hub service running on port %d%n",
                PORT
        );
    }

    private static Hub findById(
            List<Hub> hubs,
            String hubId
    ) {
        return hubs.stream()
                .filter(hub ->
                        hub.hubId().equalsIgnoreCase(hubId)
                )
                .findFirst()
                .orElse(null);
    }
}
