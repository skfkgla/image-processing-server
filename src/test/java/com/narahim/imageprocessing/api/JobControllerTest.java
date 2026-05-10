package com.narahim.imageprocessing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.narahim.imageprocessing.api.dto.CreateJobRequest;
import com.narahim.imageprocessing.domain.Job;
import com.narahim.imageprocessing.exception.JobNotFoundException;
import com.narahim.imageprocessing.exception.RetryableConflictException;
import com.narahim.imageprocessing.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobController.class)
@ActiveProfiles("test")
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobService jobService;

    @Test
    void createJob_returns201() throws Exception {
        Job job = Job.create("idem-key-1", "http://image.com/img.jpg");
        when(jobService.createJob(eq("idem-key-1"), any())).thenReturn(job);

        CreateJobRequest request = new CreateJobRequest();

        mockMvc.perform(post("/api/jobs")
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageUrl", "http://image.com/img.jpg"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.imageUrl").value("http://image.com/img.jpg"));
    }

    @Test
    void createJob_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageUrl", "http://image.com/img.jpg"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createJob_blankImageUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageUrl", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createJob_concurrentConflict_returns202() throws Exception {
        when(jobService.createJob(any(), any())).thenThrow(new RetryableConflictException());

        mockMvc.perform(post("/api/jobs")
                        .header("Idempotency-Key", "idem-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageUrl", "http://image.com/img.jpg"))))
                .andExpect(status().isAccepted());
    }

    @Test
    void getJob_found_returns200() throws Exception {
        UUID jobId = UUID.randomUUID();
        Job job = Job.create("idem-key-1", "http://image.com/img.jpg");
        when(jobService.getJob(jobId)).thenReturn(job);

        mockMvc.perform(get("/api/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getJob_notFound_returns404() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(jobService.getJob(jobId)).thenThrow(new JobNotFoundException(jobId));

        mockMvc.perform(get("/api/jobs/{jobId}", jobId))
                .andExpect(status().isNotFound());
    }

    @Test
    void listJobs_returns200() throws Exception {
        List<Job> jobs = List.of(
                Job.create("key-1", "http://image.com/img1.jpg"),
                Job.create("key-2", "http://image.com/img2.jpg")
        );
        when(jobService.getJobs(any(Pageable.class))).thenReturn(new PageImpl<>(jobs));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }
}
