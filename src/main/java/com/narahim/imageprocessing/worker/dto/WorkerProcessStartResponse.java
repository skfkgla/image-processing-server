package com.narahim.imageprocessing.worker.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WorkerProcessStartResponse {

    private String jobId;
    private String status;
}
