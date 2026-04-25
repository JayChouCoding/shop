package com.suddenfix.product.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class AdminProductUploadRequest {

    private String name;

    private String title;

    private Long categoryId;

    private String mainImage;

    private String imageUrl;

    private Long price;

    private Integer stock;

    private Integer inventory;

    private String description;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;
}
