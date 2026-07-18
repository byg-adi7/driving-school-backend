package com.drivingschool.backend.email;

import com.drivingschool.backend.config.ResendConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResendEmailClientTest {

    @Mock private RestTemplate restTemplate;

    private ResendEmailClient client;

    @BeforeEach
    void setUp() {
        ResendConfig config = new ResendConfig();
        config.setApiKey("re_test_key");
        client = new ResendEmailClient(config, restTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void send_postsCorrectPayloadAndAuthHeader() {
        when(restTemplate.postForObject(eq("https://api.resend.com/emails"), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"id\":\"abc-123\"}");

        client.send("no-reply@drivingschool.local", "student@example.com", "Reset your password", "body text");

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq("https://api.resend.com/emails"), captor.capture(), eq(String.class));

        HttpEntity<Map<String, Object>> sentEntity = captor.getValue();
        Map<String, Object> body = sentEntity.getBody();
        assertThat(body).containsEntry("from", "no-reply@drivingschool.local");
        assertThat(body).containsEntry("to", "student@example.com");
        assertThat(body).containsEntry("subject", "Reset your password");
        assertThat(body).containsEntry("text", "body text");

        HttpHeaders headers = sentEntity.getHeaders();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer re_test_key");
        assertThat(headers.getContentType()).isNotNull();
    }

    @Test
    void send_propagatesRestClientExceptionToCaller() {
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("resend unreachable"));

        assertThatThrownBy(() -> client.send("from@example.com", "to@example.com", "subject", "body"))
                .isInstanceOf(RestClientException.class);
    }
}
