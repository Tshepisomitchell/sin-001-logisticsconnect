package co.wethinkcode.logisticsconnect;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class HubDataCleaner {

    private static final String CSV_RESOURCE =
            "/hubs-global.csv";

    public List<Hub> loadAndClean()
            throws IOException, CsvException {

        InputStream inputStream =
                HubDataCleaner.class.getResourceAsStream(
                        CSV_RESOURCE
                );

        if (inputStream == null) {
            throw new IOException(
                    "Could not find " + CSV_RESOURCE
            );
        }

        try (
                Reader reader = new InputStreamReader(
                        inputStream,
                        StandardCharsets.UTF_8
                );
                CSVReader csvReader = new CSVReader(reader)
        ) {
            List<String[]> rows = csvReader.readAll();

            if (rows.isEmpty()) {
                return List.of();
            }

            List<HubCandidate> candidates =
                    parseCandidates(rows);

            fillMissingProvinces(candidates);

            return mergeDuplicates(candidates);
        }
    }

    private List<HubCandidate> parseCandidates(
            List<String[]> rows
    ) {
        List<HubCandidate> candidates =
                new ArrayList<>();

        // Start at 1 because row 0 contains the headings.
        for (int index = 1; index < rows.size(); index++) {
            String[] row = rows.get(index);
            int lineNumber = index + 1;

            if (row.length < 4) {
                reject(
                        lineNumber,
                        "Expected four columns"
                );
                continue;
            }

            String hubId = normalizeId(row[0]);
            String province = normalizeProvince(row[1]);
            String sortingCenter = normalizeName(row[2]);

            Optional<Boolean> active =
                    normalizeBoolean(row[3]);

            if (isMissing(hubId)) {
                reject(lineNumber, "Missing hub ID");
                continue;
            }

            if (isMissing(sortingCenter)) {
                reject(
                        lineNumber,
                        "Missing sorting centre"
                );
                continue;
            }

            if (active.isEmpty()) {
                reject(
                        lineNumber,
                        "Invalid active value: " + row[3]
                );
                continue;
            }

            candidates.add(
                    new HubCandidate(
                            hubId,
                            province,
                            sortingCenter,
                            active.get(),
                            lineNumber
                    )
            );
        }

        return candidates;
    }

    private void fillMissingProvinces(
            List<HubCandidate> candidates
    ) {
        Map<String, String> provinceByCentre =
                new LinkedHashMap<>();

        for (HubCandidate candidate : candidates) {
            if (!isMissing(candidate.province())) {
                provinceByCentre.putIfAbsent(
                        duplicateKey(candidate.sortingCenter()),
                        candidate.province()
                );
            }
        }

        for (int index = 0;
             index < candidates.size();
             index++) {

            HubCandidate candidate =
                    candidates.get(index);

            if (!isMissing(candidate.province())) {
                continue;
            }

            String inferredProvince =
                    provinceByCentre.get(
                            duplicateKey(
                                    candidate.sortingCenter()
                            )
                    );

            if (inferredProvince == null) {
                reject(
                        candidate.lineNumber(),
                        "Missing province and it could not " +
                                "be inferred"
                );

                candidates.set(index, null);
                continue;
            }

            System.out.printf(
                    "Line %d: inferred province %s for %s%n",
                    candidate.lineNumber(),
                    inferredProvince,
                    candidate.sortingCenter()
            );

            candidates.set(
                    index,
                    new HubCandidate(
                            candidate.hubId(),
                            inferredProvince,
                            candidate.sortingCenter(),
                            candidate.active(),
                            candidate.lineNumber()
                    )
            );
        }

        candidates.removeIf(
                candidate -> candidate == null
        );
    }

    private List<Hub> mergeDuplicates(
            List<HubCandidate> candidates
    ) {
        Map<String, Hub> uniqueHubs =
                new LinkedHashMap<>();

        for (HubCandidate candidate : candidates) {
            String key =
                    duplicateKey(candidate.province()) +
                            "|" +
                            duplicateKey(
                                    candidate.sortingCenter()
                            );

            Hub existing = uniqueHubs.get(key);

            if (existing == null) {
                uniqueHubs.put(
                        key,
                        new Hub(
                                candidate.hubId(),
                                candidate.province(),
                                candidate.sortingCenter(),
                                candidate.active()
                        )
                );
                continue;
            }

            /*
             * Duplicate policy:
             * - Keep the first canonical hub ID.
             * - Treat the hub as active if any duplicate
             *   record says that it is active.
             */
            Hub merged = new Hub(
                    existing.hubId(),
                    existing.province(),
                    existing.sortingCenter(),
                    existing.active() ||
                            candidate.active()
            );

            uniqueHubs.put(key, merged);

            System.out.printf(
                    "Line %d: merged duplicate %s into %s%n",
                    candidate.lineNumber(),
                    candidate.hubId(),
                    existing.hubId()
            );
        }

        return new ArrayList<>(uniqueHubs.values());
    }

    private String normalizeId(String value) {
        String normalized = normalizeWhitespace(value);

        if (isPlaceholder(normalized)) {
            return "";
        }

        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeProvince(String value) {
        String normalized = normalizeWhitespace(value);

        if (isPlaceholder(normalized)) {
            return "";
        }

        String comparison =
                normalized.toLowerCase(Locale.ROOT)
                        .replace("-", " ");

        comparison = comparison.replaceAll(
                "\\s+",
                " "
        );

        return switch (comparison) {
            case "gauteng" -> "Gauteng";
            case "western cape" -> "Western Cape";
            case "eastern cape" -> "Eastern Cape";
            case "free state" -> "Free State";
            case "limpopo" -> "Limpopo";
            case "north west" -> "North West";
            case "mpumalanga" -> "Mpumalanga";
            case "northern cape" -> "Northern Cape";
            case "kwazulu natal" -> "KwaZulu-Natal";
            default -> toTitleCase(normalized);
        };
    }

    private String normalizeName(String value) {
        String normalized = normalizeWhitespace(value);

        if (isPlaceholder(normalized)) {
            return "";
        }

        return toTitleCase(normalized);
    }

    private Optional<Boolean> normalizeBoolean(
            String value
    ) {
        String normalized =
                normalizeWhitespace(value)
                        .toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "y", "yes", "1", "true" ->
                    Optional.of(true);

            case "n", "no", "0", "false" ->
                    Optional.of(false);

            default -> Optional.empty();
        };
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().replaceAll("\\s+", " ");
    }

    private String toTitleCase(String value) {
        String[] words =
                value.toLowerCase(Locale.ROOT)
                        .split(" ");

        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(" ");
            }

            String[] hyphenatedParts = word.split("-");

            for (int index = 0;
                 index < hyphenatedParts.length;
                 index++) {

                if (index > 0) {
                    result.append("-");
                }

                String part = hyphenatedParts[index];

                if (!part.isEmpty()) {
                    result.append(
                            Character.toUpperCase(
                                    part.charAt(0)
                            )
                    );

                    result.append(part.substring(1));
                }
            }
        }

        return result.toString();
    }

    private boolean isPlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }

        String normalized =
                value.trim().toLowerCase(Locale.ROOT);

        return normalized.equals("n/a") ||
                normalized.equals("tbd") ||
                normalized.equals("unknown") ||
                normalized.equals("-") ||
                normalized.equals("nan");
    }

    private boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    private String duplicateKey(String value) {
        return normalizeWhitespace(value)
                .toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace(" ", "");
    }

    private void reject(
            int lineNumber,
            String reason
    ) {
        System.err.printf(
                "Rejected CSV line %d: %s%n",
                lineNumber,
                reason
        );
    }

    private record HubCandidate(
            String hubId,
            String province,
            String sortingCenter,
            boolean active,
            int lineNumber
    ) {
    }
}
