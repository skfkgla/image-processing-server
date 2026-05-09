package com.narahim.imageprocessing.api;

import com.narahim.imageprocessing.api.dto.CreateJobRequest;
import com.narahim.imageprocessing.api.dto.JobResponse;
import com.narahim.imageprocessing.domain.Job;
import com.narahim.imageprocessing.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<JobResponse> createJob(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateJobRequest request) {
        Job job = jobService.createJob(idempotencyKey, request.getImageUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(JobResponse.from(job));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID jobId) {
        Job job = jobService.getJob(jobId);
        return ResponseEntity.ok(JobResponse.from(job));
    }

    @GetMapping
    public ResponseEntity<List<JobResponse>> listJobs() {
        List<JobResponse> jobs = jobService.listJobs().stream()
                .map(JobResponse::from)
                .toList();
        return ResponseEntity.ok(jobs);
    }
}
