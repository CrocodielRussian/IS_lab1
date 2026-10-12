package com.mikle.lab1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DataRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String description
) { }
