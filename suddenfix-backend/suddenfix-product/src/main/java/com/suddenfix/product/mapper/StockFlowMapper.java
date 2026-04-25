package com.suddenfix.product.mapper;

import com.suddenfix.product.domain.pojo.StockFlow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockFlowMapper {
    int insert(StockFlow stockFlow);
}
