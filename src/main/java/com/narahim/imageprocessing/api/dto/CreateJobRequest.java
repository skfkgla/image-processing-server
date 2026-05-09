package com.narahim.imageprocessing.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateJobRequest {

    @NotBlank(message = "imageUrl must not be blank")
    private String imageUrl;
}
