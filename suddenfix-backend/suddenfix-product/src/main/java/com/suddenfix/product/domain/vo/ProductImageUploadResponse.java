package com.suddenfix.product.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageUploadResponse {

    private String fileName;

    private String imageUrl;

    private Long size;
}
