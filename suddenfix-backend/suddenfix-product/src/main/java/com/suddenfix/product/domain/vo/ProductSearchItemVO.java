package com.suddenfix.product.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class ProductSearchItemVO {

    private Long id;

    private String name;

    private String description;

    private String mainImage;

    private Long price;

    private Integer stock;

    private Integer status;

    private Long sales;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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
