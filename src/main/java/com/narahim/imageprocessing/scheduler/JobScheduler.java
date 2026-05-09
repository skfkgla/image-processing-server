package com.narahim.imageprocessing.scheduler;

import com.narahim.imageprocessing.domain.Job;
import com.narahim.imageprocessing.domain.JobRepository;
import com.narahim.imageprocessing.domain.JobStatus;
import com.narahim.imageprocessing.exception.PermanentWorkerException;
import com.narahim.imageprocessing.worker.MockWorkerClient;
import com.narahim.imageprocessing.worker.dto.WorkerProcessStartResponse;
import com.narahim.imageprocessing.worker.dto.WorkerProcessStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobScheduler {

	private static final Duration POLLING_CYCLE_WARN_THRESHOLD = Duration.ofMinutes(1);

    private final JobRepository jobRepository;
    private final MockWorkerClient mockWorkerClient;


    /**
     * PENDING 상태의 Job을 Mock Worker에 제출한다.
     * 제출 성공 시 PROCESSING으로, 실패 시 FAILED로 전환한다.
     */
    @Scheduled(fixedDelayString = "${scheduler.submission-interval-ms}")
    public void submitPendingJobs() {
        List<Job> pendingJobs = jobRepository.findAllByStatus(JobStatus.PENDING);
        if (pendingJobs.isEmpty()) {
            return;
        }
        log.info("Submitting {} pending jobs", pendingJobs.size());

        for (Job job : pendingJobs) {
            try {
                WorkerProcessStartResponse response = mockWorkerClient.submit(job.getImageUrl());
                job.startProcessing(response.getJobId());
                log.info("Job {} submitted to worker as {}", job.getId(), response.getJobId());
            } catch (PermanentWorkerException e) {
                log.warn("Job {} failed permanently during submission: {}", job.getId(), e.getMessage());
                job.fail("Submission rejected by worker (status " + e.getStatusCode() + "): " + e.getMessage());
            } catch (Exception e) {
                log.warn("Job {} failed after retries during submission: {}", job.getId(), e.getMessage());
                job.fail("Failed to submit after retries: " + e.getMessage());
            }
            jobRepository.save(job);
        }
    }

    /**
     * PROCESSING 상태의 Job의 완료 여부를 Mock Worker에 폴링한다.
     * 동기(순차) 방식으로 처리하여 Mock Worker에 과도한 요청이 몰리는 것을 방지한다.
     *
     * TODO: 실제 처리시간이 1분(임의적 기준)이 넘어가면,
     *       병렬 폴링 방식으로 전환하는 것을 검토한다.
     */

    @Scheduled(fixedDelayString = "${scheduler.polling-interval-ms}")
    public void pollProcessingJobs() {
        List<Job> processingJobs = jobRepository.findAllByStatus(JobStatus.PROCESSING);
        if (processingJobs.isEmpty()) {
            return;
        }
        log.info("Polling {} processing jobs", processingJobs.size());

        Instant start = Instant.now();

        for (Job job : processingJobs) {
            try {
                WorkerProcessStatusResponse response = mockWorkerClient.getStatus(job.getWorkerJobId());
                if (response.isCompleted()) {
                    job.complete(response.getResult());
                    jobRepository.save(job);
                    log.info("Job {} completed", job.getId());
                } else if (response.isFailed()) {
                    job.fail("Worker reported failure", response.getResult());
                    jobRepository.save(job);
                    log.warn("Job {} failed by worker", job.getId());
                }
            } catch (PermanentWorkerException e) {
                log.warn("Job {} failed permanently during polling: {}", job.getId(), e.getMessage());
                job.fail("Polling rejected by worker (status " + e.getStatusCode() + "): " + e.getMessage());
                jobRepository.save(job);
            } catch (Exception e) {
                log.warn("Job {} failed after retries during polling: {}", job.getId(), e.getMessage());
                job.fail("Failed to poll after retries: " + e.getMessage());
                jobRepository.save(job);
            }
        }

        Duration elapsed = Duration.between(start, Instant.now());
        if (elapsed.compareTo(POLLING_CYCLE_WARN_THRESHOLD) > 0) {
            log.warn("Polling cycle took {}s for {} jobs — sequential throughput may be insufficient",
                    elapsed.toSeconds(), processingJobs.size());
        }
    }
}
