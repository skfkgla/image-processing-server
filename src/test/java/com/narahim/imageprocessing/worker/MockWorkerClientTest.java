package com.narahim.imageprocessing.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narahim.imageprocessing.exception.PermanentWorkerException;
import com.narahim.imageprocessing.worker.dto.WorkerProcessStartResponse;
import com.narahim.imageprocessing.worker.dto.WorkerProcessStatusResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockWorkerClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer mockWebServer;
    private MockWorkerClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        client = new MockWorkerClient(webClient, "test-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void submit_success() throws JsonProcessingException {
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", "worker-job-1");
        body.put("status", "PROCESSING");

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(body))
                .addHeader("Content-Type", "application/json"));

        WorkerProcessStartResponse response = client.submit("http://image.com/img.jpg");

        assertThat(response.getJobId()).isEqualTo("worker-job-1");
        assertThat(response.getStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void submit_4xx_throwsPermanentWorkerException() throws JsonProcessingException {
        Map<String, Object> body = new HashMap<>();
        body.put("detail", "bad request");

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody(objectMapper.writeValueAsString(body))
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client.submit("http://image.com/img.jpg"))
                .isInstanceOf(PermanentWorkerException.class);
    }

    @Test
    void getStatus_completed() throws JsonProcessingException {
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", "worker-job-1");
        body.put("status", "COMPLETED");
        body.put("result", "ok");

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(body))
                .addHeader("Content-Type", "application/json"));

        WorkerProcessStatusResponse response = client.getStatus("worker-job-1");

        assertThat(response.isCompleted()).isTrue();
        assertThat(response.getResult()).isEqualTo("ok");
    }

    @Test
    void getStatus_workerReportedFailed() throws JsonProcessingException {
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", "worker-job-1");
        body.put("status", "FAILED");
        body.put("result", "processing error occurred");

        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(body))
                .addHeader("Content-Type", "application/json"));

        WorkerProcessStatusResponse response = client.getStatus("worker-job-1");

        assertThat(response.isFailed()).isTrue();
        assertThat(response.getResult()).isEqualTo("processing error occurred");
    }

    @Test
    void getStatus_4xx_throwsPermanentWorkerException() throws JsonProcessingException {
        Map<String, Object> body = new HashMap<>();
        body.put("detail", "not found");

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody(objectMapper.writeValueAsString(body))
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client.getStatus("worker-job-1"))
                .isInstanceOf(PermanentWorkerException.class);
    }
}
