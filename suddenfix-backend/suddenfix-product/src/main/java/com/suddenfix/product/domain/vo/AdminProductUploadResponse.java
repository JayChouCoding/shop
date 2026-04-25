package com.suddenfix.product.domain.vo;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdminProductUploadResponse {

    private Long id;

    private String name;

    private String mainImage;

    private Long price;

    private Integer stock;

    private Integer status;

    @JsonGetter("inventory")
    public Integer getInventory() {
        return stock;
    }

    @JsonGetter("title")
    public String getTitle() {
        return name;
    }

    @JsonGetter("imageUrl")
    public String getImageUrl() {
        return mainImage;
    }
}
