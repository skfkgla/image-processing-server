package com.narahim.imageprocessing.worker;

import com.narahim.imageprocessing.exception.PermanentWorkerException;
import com.narahim.imageprocessing.worker.dto.WorkerProcessRequest;
import com.narahim.imageprocessing.worker.dto.WorkerProcessStartResponse;
import com.narahim.imageprocessing.worker.dto.WorkerProcessStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
public class MockWorkerClient {

    private static final Retry RETRY_POLICY = Retry.backoff(3, Duration.ofSeconds(1))
            .multiplier(2.0)
            .filter(throwable -> !(throwable instanceof PermanentWorkerException));

    private final WebClient webClient;
    private final String apiKey;

    public MockWorkerClient(WebClient mockWorkerWebClient,
                            @Value("${mock-worker.api-key}") String apiKey) {
        this.webClient = mockWorkerWebClient;
        this.apiKey = apiKey;
    }

    public WorkerProcessStartResponse submit(String imageUrl) {
        return webClient.post()
                .uri("/process")
                .header("X-API-KEY", apiKey)
                .bodyValue(new WorkerProcessRequest(imageUrl))
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() && !status.equals(HttpStatus.TOO_MANY_REQUESTS),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("Client error")
                                .flatMap(body -> Mono.error(
                                        new PermanentWorkerException(response.statusCode().value(), body)))
                )
                .bodyToMono(WorkerProcessStartResponse.class)
                .retryWhen(RETRY_POLICY)
                .block();
    }

    public WorkerProcessStatusResponse getStatus(String workerJobId) {
        return webClient.get()
                .uri("/process/{jobId}", workerJobId)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() && !status.equals(HttpStatus.TOO_MANY_REQUESTS) ,
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("Client error")
                                .flatMap(body -> Mono.error(
                                        new PermanentWorkerException(response.statusCode().value(), body)))
                )
                .bodyToMono(WorkerProcessStatusResponse.class)
                .retryWhen(RETRY_POLICY)
                .block();
    }
}
