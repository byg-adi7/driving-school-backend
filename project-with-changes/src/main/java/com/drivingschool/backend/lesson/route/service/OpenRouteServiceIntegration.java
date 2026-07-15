package com.drivingschool.backend.lesson.route.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.lesson.route.config.OpenRouteServiceConfig;
import com.drivingschool.backend.lesson.route.dto.RouteCoordinateDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    private Map<String, Object> parseRouteResponse(
            String responseString,
            Double startLat, Double startLon,
            Double destLat, Double destLon
    ) {
        try {
            JsonNode root = objectMapper.readTree(responseString);
            JsonNode routes = root.path("routes");

            if (!routes.isArray() || routes.size() == 0) {
                throw new BadRequestException("No route found for the given coordinates");
            }

            JsonNode route = routes.get(0);
            long distance = route.path("summary").path("distance").asLong();
            long duration = route.path("summary").path("duration").asLong();
            String geometry = route.path("geometry").asText();

            Map<String, Object> result = new HashMap<>();
            result.put("distance", distance);
            result.put("duration", duration);
            result.put("geometry", geometry);
            result.put("coordinates", parseCoordinates(geometry));
            result.put("externalRouteId", root.path("routes").get(0).path("id").asText(""));

            return result;
        } catch (IOException e) {
            log.error("Failed to parse OpenRouteService response", e);
            throw new BadRequestException("Failed to parse route response");
        }
    }

    private List<RouteCoordinateDTO> parseCoordinates(String geometry) {
        List<RouteCoordinateDTO> coordinates = new ArrayList<>();
        try {
            JsonNode geomNode = objectMapper.readTree(geometry);

            if (geomNode.isArray()) {
                for (JsonNode coord : geomNode) {
                    if (coord.isArray() && coord.size() >= 2) {
                        Double lon = coord.get(0).asDouble();
                        Double lat = coord.get(1).asDouble();
                        coordinates.add(new RouteCoordinateDTO(lat, lon));
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to parse route coordinates", e);
        }
        return coordinates;
    }
}