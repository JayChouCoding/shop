package com.suddenfix.product.mapper;

import com.suddenfix.common.dto.DeductionProductDTO;
import com.suddenfix.product.domain.pojo.Product;
import com.suddenfix.product.domain.pojo.ProductDoc;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMapper {
    Product selectById(@Param("id") Long id);

    List<Product> selectByIds(@Param("ids") List<Long> ids);

    List<ProductDoc> searchProducts(@Param("keyword") String keyword,
                                    @Param("minPrice") Long minPrice,
                                    @Param("maxPrice") Long maxPrice,
                                    @Param("offset") Integer offset,
                                    @Param("size") Integer size,
                                    @Param("latestFirst") boolean latestFirst);

    Long countSearchProducts(@Param("keyword") String keyword,
                             @Param("minPrice") Long minPrice,
                             @Param("maxPrice") Long maxPrice);

    List<ProductDoc> selectRecommendProducts(@Param("categoryId") Long categoryId,
                                             @Param("excludeId") Long excludeId,
                                             @Param("size") Integer size);

    void insertProduct(Product product);

    int updateProduct(Product product);

    int deduction(DeductionProductDTO deductionProduct);

    int restoreStock(@Param("productId") Long productId, @Param("quantity") Long quantity);
}
