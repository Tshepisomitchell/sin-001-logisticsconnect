package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class LogisticsServiceClient {

    private static final String HUB_SERVICE_URL =
            "http://localhost:7051";

    private static final String DELAY_SERVICE_URL =
            "http://localhost:7052";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LogisticsServiceClient() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        objectMapper = new ObjectMapper();
    }

    public Hub fetchHub(String hubId)
            throws IOException, InterruptedException {

        HttpResponse<String> response = sendGet(
                HUB_SERVICE_URL + "/hubs/" + hubId
        );

        if (response.statusCode() == 404) {
            return null;
        }

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Hub Service returned HTTP " +
                            response.statusCode()
            );
        }

        return objectMapper.readValue(
                response.body(),
                Hub.class
        );
    }

    public DelayStage fetchDelayStage(String hubId)
            throws IOException, InterruptedException {

        HttpResponse<String> response = sendGet(
                DELAY_SERVICE_URL +
                        "/delay-stage/" +
                        hubId
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Delay Stage Service returned HTTP " +
                            response.statusCode()
            );
        }

        return objectMapper.readValue(
                response.body(),
                DelayStage.class
        );
    }

    private HttpResponse<String> sendGet(String url)
            throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }
}