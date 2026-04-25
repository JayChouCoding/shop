package com.suddenfix.product.domain.pojo;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDoc {

    private Long id;

    private String name;

    private Long categoryId;

    private String mainImage;

    private Long price;

    private Integer stock;

    private String description;

    private Integer status;

    private Long sales;

    private Date createTime;

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
