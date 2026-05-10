package com.narahim.imageprocessing.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobTest {

    private Job pendingJob;

    @BeforeEach
    void setUp() {
        pendingJob = Job.create("idem-key-1", "http://image.com/img.jpg");
    }

    @Test
    void startProcessing_fromPending_transitionsToProcessing() {
        pendingJob.startProcessing("worker-job-1");

        assertThat(pendingJob.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(pendingJob.getWorkerJobId()).isEqualTo("worker-job-1");
    }

    @Test
    void startProcessing_fromProcessing_throwsException() {
        pendingJob.startProcessing("worker-job-1");

        assertThatThrownBy(() -> pendingJob.startProcessing("worker-job-2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startProcessing_fromCompleted_throwsException() {
        pendingJob.startProcessing("worker-job-1");
        pendingJob.complete("result");

        assertThatThrownBy(() -> pendingJob.startProcessing("worker-job-2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startProcessing_fromFailed_throwsException() {
        pendingJob.fail("error");

        assertThatThrownBy(() -> pendingJob.startProcessing("worker-job-1"))
                .isInstanceOf(IllegalStateException.class);
    }


    @Test
    void complete_fromProcessing_transitionsToCompleted() {
        pendingJob.startProcessing("worker-job-1");
        pendingJob.complete("processed result");

        assertThat(pendingJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(pendingJob.getResult()).isEqualTo("processed result");
    }

    @Test
    void complete_fromPending_throwsException() {
        assertThatThrownBy(() -> pendingJob.complete("result"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void complete_fromFailed_throwsException() {
        pendingJob.fail("error");

        assertThatThrownBy(() -> pendingJob.complete("result"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fail_fromPending_transitionsToFailed() {
        pendingJob.fail("submission error");

        assertThat(pendingJob.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(pendingJob.getErrorMessage()).isEqualTo("submission error");
    }

    @Test
    void fail_fromProcessing_transitionsToFailed() {
        pendingJob.startProcessing("worker-job-1");
        pendingJob.fail("Worker reported failure", "processing error occurred");

        assertThat(pendingJob.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(pendingJob.getErrorMessage()).isEqualTo("Worker reported failure");
        assertThat(pendingJob.getResult()).isEqualTo("processing error occurred");
    }

    @Test
    void fail_fromCompleted_throwsException() {
        pendingJob.startProcessing("worker-job-1");
        pendingJob.complete("result");

        assertThatThrownBy(() -> pendingJob.fail("error"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fail_fromFailed_throwsException() {
        pendingJob.fail("first error");

        assertThatThrownBy(() -> pendingJob.fail("second error"))
                .isInstanceOf(IllegalStateException.class);
    }
}
