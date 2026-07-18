package com.drivingschool.backend.lesson.route.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.lesson.route.config.OpenRouteServiceConfig;
import com.drivingschool.backend.lesson.route.dto.RouteCoordinateDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a real bug found via a live deploy: the actual
 * OpenRouteService API responds with a GeoJSON FeatureCollection, not the
 * {"routes":[...]} shape this class originally assumed - every call
 * succeeded against the real API but failed to parse the response, always
 * reporting "No route found" regardless of the real outcome. This class had
 * zero test coverage before, which is exactly how that went undetected.
 */
@ExtendWith(MockitoExtension.class)
class OpenRouteServiceIntegrationTest {

    // Captured verbatim from a real OpenRouteService response during live
    // verification (Trafalgar Square -> London Eye, driving-car profile).
    private static final String REAL_GEOJSON_RESPONSE = "{\"type\":\"FeatureCollection\",\"bbox\":[-0.128009,51.50263,-0.113133,51.511332],\"features\":[{\"bbox\":[-0.128009,51.50263,-0.113133,51.511332],\"type\":\"Feature\",\"properties\":{\"segments\":[{\"distance\":2125.8,\"duration\":406.9,\"steps\":[]}],\"way_points\":[0,59],\"summary\":{\"distance\":2125.8,\"duration\":406.9}},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[-0.128009,51.507534],[-0.127827,51.507548],[-0.113133,51.504643]]}}],\"metadata\":{\"attribution\":\"openrouteservice.org\"}}";

    @Mock private RestTemplate restTemplate;

    private OpenRouteServiceIntegration integration;

    @BeforeEach
    void setUp() {
        OpenRouteServiceConfig config = new OpenRouteServiceConfig();
        config.setApiKey("test-key");
        integration = new OpenRouteServiceIntegration(config, restTemplate, new ObjectMapper());
    }

    @Test
    void generateRoute_parsesRealGeoJsonResponseShape() {
        when(restTemplate.getForObject(anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(REAL_GEOJSON_RESPONSE);

        Map<String, Object> result = integration.generateRoute(51.5080, -0.1281, 51.5033, -0.1196);

        assertThat(result.get("distance")).isEqualTo(2125L);
        assertThat(result.get("duration")).isEqualTo(406L);

        @SuppressWarnings("unchecked")
        List<RouteCoordinateDTO> coordinates = (List<RouteCoordinateDTO>) result.get("coordinates");
        assertThat(coordinates).hasSize(3);
        assertThat(coordinates.get(0).getLatitude()).isEqualTo(51.507534);
        assertThat(coordinates.get(0).getLongitude()).isEqualTo(-0.128009);
    }

    @Test
    void generateRoute_storedGeometryRoundTripsThroughRouteCoordinateDTO() throws Exception {
        when(restTemplate.getForObject(anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(REAL_GEOJSON_RESPONSE);

        Map<String, Object> result = integration.generateRoute(51.5080, -0.1281, 51.5033, -0.1196);
        String storedGeometry = (String) result.get("geometry");

        ObjectMapper mapper = new ObjectMapper();
        List<RouteCoordinateDTO> roundTripped = mapper.readValue(storedGeometry,
                mapper.getTypeFactory().constructCollectionType(List.class, RouteCoordinateDTO.class));

        assertThat(roundTripped).hasSize(3);
        assertThat(roundTripped.get(2).getLatitude()).isEqualTo(51.504643);
        assertThat(roundTripped.get(2).getLongitude()).isEqualTo(-0.113133);
    }

    @Test
    void generateRoute_emptyFeatures_throwsBadRequestWithoutCrashing() {
        when(restTemplate.getForObject(anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn("{\"type\":\"FeatureCollection\",\"features\":[]}");

        assertThatThrownBy(() -> integration.generateRoute(51.5080, -0.1281, 51.5033, -0.1196))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No route found");
    }

    @Test
    void generateRoute_embeddedErrorObject_surfacesRealMessage() {
        when(restTemplate.getForObject(anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn("{\"error\":{\"code\":2010,\"message\":\"Could not find routable point\"}}");

        assertThatThrownBy(() -> integration.generateRoute(51.5080, -0.1281, 51.5033, -0.1196))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Could not find routable point");
    }

    @Test
    void generateRoute_restClientException_wrapsAsBadRequest() {
        when(restTemplate.getForObject(anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertThatThrownBy(() -> integration.generateRoute(51.5080, -0.1281, 51.5033, -0.1196))
                .isInstanceOf(BadRequestException.class);
    }
}
