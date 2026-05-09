package com.narahim.imageprocessing.service;

import com.narahim.imageprocessing.domain.Job;
import com.narahim.imageprocessing.domain.JobRepository;
import com.narahim.imageprocessing.domain.JobStatus;
import com.narahim.imageprocessing.exception.JobNotFoundException;
import com.narahim.imageprocessing.exception.RetryableConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobService jobService;

    @Test
    void createJob_success() {
        Job savedJob = Job.create("key-1", "http://image.com/img.jpg");
        when(jobRepository.save(any(Job.class))).thenReturn(savedJob);

        Job result = jobService.createJob("key-1", "http://image.com/img.jpg");

        assertThat(result.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(result.getStatus()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    void createJob_duplicateKey_returnsExistingJob() {
        Job existingJob = Job.create("key-1", "http://image.com/img.jpg");
        when(jobRepository.save(any(Job.class))).thenThrow(new DataIntegrityViolationException("unique constraint"));
        when(jobRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existingJob));

        Job result = jobService.createJob("key-1", "http://image.com/img.jpg");

        assertThat(result).isEqualTo(existingJob);
    }

    @Test
    void createJob_duplicateKey_concurrentCase_throwsRetryableConflict() {
        when(jobRepository.save(any(Job.class))).thenThrow(new DataIntegrityViolationException("unique constraint"));
        when(jobRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.createJob("key-1", "http://image.com/img.jpg"))
                .isInstanceOf(RetryableConflictException.class);
    }

    @Test
    void getJob_found() {
        UUID jobId = UUID.randomUUID();
        Job job = Job.create("key-1", "http://image.com/img.jpg");
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        Job result = jobService.getJob(jobId);

        assertThat(result).isEqualTo(job);
    }

    @Test
    void getJob_notFound_throwsJobNotFoundException() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getJob(jobId))
                .isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void listJobs_returnsAll() {
        List<Job> jobs = List.of(
                Job.create("key-1", "http://image.com/img1.jpg"),
                Job.create("key-2", "http://image.com/img2.jpg")
        );
        Pageable pageable = PageRequest.of(0, 20);
        when(jobRepository.findAll(pageable)).thenReturn(new PageImpl<>(jobs, pageable, jobs.size()));

        Page<Job> result = jobService.listJobs(pageable);

        assertThat(result.getContent()).hasSize(2);
    }
}
