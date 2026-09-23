package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class IngestionServiceClient {

    private static final String HUBS_URL =
            "http://localhost:7050/hubs";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public IngestionServiceClient() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        objectMapper = new ObjectMapper();
    }

    public List<Hub> fetchHubs()
            throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HUBS_URL))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Ingestion service returned HTTP " +
                            response.statusCode()
            );
        }

        return objectMapper.readValue(
                response.body(),
                new TypeReference<List<Hub>>() {
                }
        );
    }
}
