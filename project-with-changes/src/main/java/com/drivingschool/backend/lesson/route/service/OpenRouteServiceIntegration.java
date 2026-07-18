package com.drivingschool.backend.lesson.route.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.lesson.route.config.OpenRouteServiceConfig;
import com.drivingschool.backend.lesson.route.dto.RouteCoordinateDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenRouteServiceIntegration {

    private final OpenRouteServiceConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public Map<String, Object> generateRoute(
            Double startLat, Double startLon,
            Double destLat, Double destLon
    ) {
        try {
            String url = buildRouteUrl(startLat, startLon, destLat, destLon);

            String response = restTemplate.getForObject(url, String.class);

            return parseRouteResponse(response, startLat, startLon, destLat, destLon);
        } catch (HttpClientErrorException e) {
            String apiMessage = null;
            try {
                JsonNode body = objectMapper.readTree(e.getResponseBodyAsString());
                apiMessage = body.path("error").path("message").asText(null);
            } catch (IOException parseEx) {
                log.debug("Could not parse OpenRouteService error response body", parseEx);
            }
            String userMessage = apiMessage != null ? apiMessage
                    : "Could not generate route. Please check your coordinates and try again.";
            log.warn("OpenRouteService returned client error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BadRequestException(userMessage);
        } catch (RestClientException e) {
            log.error("Failed to generate route from OpenRouteService", e);
            throw new BadRequestException("Failed to generate route. Please try again or check your coordinates.");
        }
    }

    private String buildRouteUrl(Double startLat, Double startLon, Double destLat, Double destLon) {
        return String.format(
                "%s?api_key=%s&start=%f,%f&end=%f,%f",
                config.getDirectionsUrl(),
                config.getApiKey(),
                startLon, startLat,
                destLon, destLat
        );
    }

    /**
     * The live API responds with a GeoJSON FeatureCollection (verified against
     * a real deploy - "routes"/"summary" at the top level, the shape this
     * method previously assumed, does not appear in practice):
     * {"type":"FeatureCollection","features":[{"properties":{"summary":
     * {"distance":...,"duration":...}, ...},"geometry":{"type":"LineString",
     * "coordinates":[[lon,lat],...]}}], ...}. There is no per-route id in this
     * shape, unlike the older format this code was originally written for.
     */
    private Map<String, Object> parseRouteResponse(
            String responseString,
            Double startLat, Double startLon,
            Double destLat, Double destLon
    ) {
        try {
            JsonNode root = objectMapper.readTree(responseString);
            JsonNode features = root.path("features");

            if (!features.isArray() || features.isEmpty()) {
                // OpenRouteService can return HTTP 200 with an embedded error
                // object (invalid/missing API key, over quota, genuinely no
                // routable path, etc.) instead of a non-2xx status - logging
                // the raw body here is the only way to tell those apart,
                // since RestTemplate's getForObject() only throws for actual
                // non-2xx responses.
                log.error("OpenRouteService returned no features for this request. Raw response: {}", responseString);
                String detail = root.path("error").path("message").asText(null);
                if (detail == null) {
                    detail = root.path("error").asText(null);
                }
                throw new BadRequestException(detail != null
                        ? "Failed to generate route: " + detail
                        : "No route found for the given coordinates");
            }

            JsonNode feature = features.get(0);
            JsonNode summary = feature.path("properties").path("summary");
            long distance = summary.path("distance").asLong();
            long duration = summary.path("duration").asLong();
            List<RouteCoordinateDTO> coordinates = parseCoordinates(feature.path("geometry").path("coordinates"));

            Map<String, Object> result = new HashMap<>();
            result.put("distance", distance);
            result.put("duration", duration);
            // Stored as our own serialized coordinate list, not the raw
            // external geometry - PracticalLessonRouteService persists this
            // value verbatim as routeGeometry and later deserializes it
            // straight into List<RouteCoordinateDTO> on every subsequent
            // read, so it must already be in that shape.
            result.put("geometry", objectMapper.writeValueAsString(coordinates));
            result.put("coordinates", coordinates);
            result.put("externalRouteId", "");

            return result;
        } catch (IOException e) {
            log.error("Failed to parse OpenRouteService response", e);
            throw new BadRequestException("Failed to parse route response");
        }
    }

    private List<RouteCoordinateDTO> parseCoordinates(JsonNode coordinatesNode) {
        List<RouteCoordinateDTO> coordinates = new ArrayList<>();
        if (coordinatesNode.isArray()) {
            for (JsonNode coord : coordinatesNode) {
                if (coord.isArray() && coord.size() >= 2) {
                    Double lon = coord.get(0).asDouble();
                    Double lat = coord.get(1).asDouble();
                    coordinates.add(new RouteCoordinateDTO(lat, lon));
                }
            }
        }
        return coordinates;
    }
}