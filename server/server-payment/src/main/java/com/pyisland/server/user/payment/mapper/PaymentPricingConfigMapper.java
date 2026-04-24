package com.pyisland.server.user.payment.mapper;

import com.pyisland.server.user.payment.entity.PaymentPricingConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface PaymentPricingConfigMapper {

    PaymentPricingConfig selectById(@Param("id") Long id);

    int insert(PaymentPricingConfig config);

    int update(@Param("id") Long id,
               @Param("proMonthAmountFen") Integer proMonthAmountFen,
               @Param("freeDesc") String freeDesc,
               @Param("freeFeaturesText") String freeFeaturesText,
               @Param("proDesc") String proDesc,
               @Param("proFeaturesText") String proFeaturesText,
               @Param("updatedAt") LocalDateTime updatedAt);
}
