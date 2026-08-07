package com.splitwise.app.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "The public URL of a newly uploaded photo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoUploadResponse {

    @Schema(description = "Public URL of the uploaded file",
            example = "https://cdn.example.com/uploads/users/uuid-123/photo.jpg")
    private String url;
}
