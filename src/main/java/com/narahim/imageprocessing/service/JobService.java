package com.narahim.imageprocessing.service;

import com.narahim.imageprocessing.domain.Job;
import com.narahim.imageprocessing.domain.JobRepository;
import com.narahim.imageprocessing.exception.JobNotFoundException;
import com.narahim.imageprocessing.exception.RetryableConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;

    /**
     * 새 Job 생성 또는 기존 Job 반환 (Idempotency-Key 기반).
     *
     * DataIntegrityViolationException 발생 시 (unique 제약 위반) 기존 Job을 조회하여 반환한다.
     * 동시 요청으로 인해 select에서도 찾지 못할 경우 RetryableConflictException을 던진다.
     *
     * @Transactional을 사용하지 않는 이유:
     * save() 실패로 DataIntegrityViolationException이 발생하면 트랜잭션이 rollback-only로 마킹된다.
     * 이후 findByIdempotencyKey() 조회 자체는 가능하지만, 메서드 종료 시 커밋을 시도하는 순간
     * UnexpectedRollbackException이 발생한다.
     * 각 repository 호출이 독립적인 트랜잭션으로 동작하도록 outer 트랜잭션을 두지 않는다.
     */
    public Job createJob(String idempotencyKey, String imageUrl) {
        try {
            return jobRepository.save(Job.create(idempotencyKey, imageUrl));
        } catch (DataIntegrityViolationException e) {
            return jobRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(RetryableConflictException::new);
        }
    }

    @Transactional(readOnly = true)
    public Job getJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }

    @Transactional(readOnly = true)
    public Page<Job> listJobs(Pageable pageable) {
        return jobRepository.findAll(pageable);
    }
}
