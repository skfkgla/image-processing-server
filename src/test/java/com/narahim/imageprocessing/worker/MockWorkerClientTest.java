package com.narahim.imageprocessing.worker;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockWorkerClientTest {

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
    void submit_success() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-job-1\",\"status\":\"PROCESSING\"}")
                .addHeader("Content-Type", "application/json"));

        WorkerProcessStartResponse response = client.submit("http://image.com/img.jpg");

        assertThat(response.getJobId()).isEqualTo("worker-job-1");
        assertThat(response.getStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void submit_4xx_throwsPermanentWorkerException() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody("{\"detail\":\"bad request\"}").addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client.submit("http://image.com/img.jpg"))
                .isInstanceOf(PermanentWorkerException.class);
    }

    @Test
    void getStatus_completed() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-job-1\",\"status\":\"COMPLETED\",\"result\":\"ok\"}")
                .addHeader("Content-Type", "application/json"));

        WorkerProcessStatusResponse response = client.getStatus("worker-job-1");

        assertThat(response.isCompleted()).isTrue();
        assertThat(response.getResult()).isEqualTo("ok");
    }

    @Test
    void getStatus_failed() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-job-1\",\"status\":\"FAILED\",\"result\":null}")
                .addHeader("Content-Type", "application/json"));

        WorkerProcessStatusResponse response = client.getStatus("worker-job-1");

        assertThat(response.isFailed()).isTrue();
    }
}
