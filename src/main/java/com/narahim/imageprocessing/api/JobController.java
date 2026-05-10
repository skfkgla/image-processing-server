package com.narahim.imageprocessing.api;

import com.narahim.imageprocessing.api.dto.CreateJobRequest;
import com.narahim.imageprocessing.api.dto.ErrorResponse;
import com.narahim.imageprocessing.api.dto.JobResponse;
import com.narahim.imageprocessing.api.dto.PageResponse;
import com.narahim.imageprocessing.domain.Job;
import com.narahim.imageprocessing.service.JobService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.PageRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

	private final JobService jobService;

	@Operation(summary = "이미지 처리 요청")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "요청 접수 완료",
			content = @Content(schema = @Schema(implementation = JobResponse.class))),
		@ApiResponse(responseCode = "202", description = "동일 키로 동시 요청 충돌 — 잠시 후 재시도 필요",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "400", description = "Idempotency-Key 헤더 누락 또는 imageUrl 검증 실패",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping
	public ResponseEntity<JobResponse> createJob(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody CreateJobRequest request) {
		Job job = jobService.createJob(idempotencyKey, request.getImageUrl());

		return ResponseEntity.status(HttpStatus.CREATED).body(JobResponse.from(job));
	}

	@Operation(summary = "작업 단건 조회")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(schema = @Schema(implementation = JobResponse.class))),
		@ApiResponse(responseCode = "404", description = "존재하지 않는 jobId",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping("/{jobId}")
	public ResponseEntity<JobResponse> getJob(@PathVariable UUID jobId) {
		Job job = jobService.getJob(jobId);

		return ResponseEntity.ok(JobResponse.from(job));
	}

	@Operation(summary = "작업 목록 조회")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공",
			content = @Content(schema = @Schema(implementation = PageResponse.class)))
	})
	@GetMapping
	public ResponseEntity<PageResponse<JobResponse>> getJobs(
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "10") int size) {
		PageResponse<JobResponse> jobs = PageResponse.of(
			jobService.getJobs(PageRequest.of(page, size)).map(JobResponse::from));

		return ResponseEntity.ok(jobs);
	}
}
