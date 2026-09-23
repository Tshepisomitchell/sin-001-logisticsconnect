package co.wethinkcode.logisticsconnect;

import com.opencsv.exceptions.CsvException;
import io.javalin.Javalin;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class IngestionServiceApp {

    private static final int PORT = 7050;

    public static void main(String[] args) {
        HubDataCleaner cleaner = new HubDataCleaner();

        List<Hub> hubs;

        try {
            hubs = cleaner.loadAndClean();
        } catch (IOException | CsvException exception) {
            System.err.println(
                    "Failed to load hub data: " +
                            exception.getMessage()
            );

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

                    Hub matchingHub = hubs.stream()
                            .filter(hub ->
                                    hub.hubId()
                                            .equalsIgnoreCase(
                                                    requestedId
                                            )
                            )
                            .findFirst()
                            .orElse(null);

                    if (matchingHub == null) {
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

                    context.json(matchingHub);
                }
        );

        System.out.printf(
                "Ingestion service loaded %d cleaned hubs%n",
                hubs.size()
        );
    }
}
