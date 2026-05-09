package com.narahim.imageprocessing.api.dto;

import com.narahim.imageprocessing.domain.Job;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class JobResponse {

    private final UUID id;
    private final String status;
    private final String imageUrl;
    private final String result;
    private final String errorMessage;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private JobResponse(Job job) {
        this.id = job.getId();
        this.status = job.getStatus().name();
        this.imageUrl = job.getImageUrl();
        this.result = job.getResult();
        this.errorMessage = job.getErrorMessage();
        this.createdAt = job.getCreatedAt();
        this.updatedAt = job.getUpdatedAt();
    }

    public static JobResponse from(Job job) {
        return new JobResponse(job);
    }
}
