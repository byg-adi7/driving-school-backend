package com.drivingschool.backend.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request-shape mistakes and access denials map to the right 4xx instead of falling
 * through to the catch-all 500 (frontend report items 5-8).
 */
class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {
        @GetMapping("/probe/forbidden")
        String forbidden() {
            throw new ForbiddenException("You do not have access to this route");
        }

        @GetMapping("/probe/range")
        String range(@RequestParam String from) {
            return from;
        }

        @PostMapping("/probe/upload")
        String upload(@RequestPart("file") MultipartFile file) {
            return file.getOriginalFilename();
        }

        @PostMapping("/probe/too-large")
        String tooLarge() {
            throw new MaxUploadSizeExceededException(50L * 1024 * 1024);
        }

        @PostMapping(value = "/probe/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        String json(@RequestBody Map<String, Object> body) {
            return "ok";
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler(DataSize.ofMegabytes(50)))
                .build();
    }

    @Test
    void anAccessDenial_is403_withItsMessage() throws Exception {
        mockMvc.perform(get("/probe/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have access to this route"));
    }

    @Test
    void aMissingQueryParameter_is400() throws Exception {
        mockMvc.perform(get("/probe/range"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'from' is missing"));
    }

    @Test
    void aMissingFilePart_is400() throws Exception {
        mockMvc.perform(multipart("/probe/upload").file(new MockMultipartFile("other", "x.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required part 'file' is missing"));
    }

    @Test
    void aTooLargeUpload_is413() throws Exception {
        mockMvc.perform(post("/probe/too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("File is too large - the maximum is 50 MB"));
    }

    @Test
    void anUnsupportedMethod_is405_withAllow() throws Exception {
        mockMvc.perform(put("/probe/forbidden"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void malformedJson_is400_andTheWrongContentType_is415() throws Exception {
        mockMvc.perform(post("/probe/json").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/probe/json").contentType(MediaType.TEXT_PLAIN).content("hi"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
