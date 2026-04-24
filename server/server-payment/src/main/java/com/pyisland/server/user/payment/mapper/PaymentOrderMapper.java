package com.pyisland.server.user.payment.mapper;

import com.pyisland.server.user.payment.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentOrderMapper {

    int insert(PaymentOrder order);

    PaymentOrder selectByOutTradeNo(@Param("outTradeNo") String outTradeNo);

    List<PaymentOrder> selectByUsername(@Param("username") String username,
                                        @Param("limit") int limit);

    int markSuccess(@Param("outTradeNo") String outTradeNo,
                    @Param("wxTransactionId") String wxTransactionId,
                    @Param("paidAt") LocalDateTime paidAt,
                    @Param("updatedAt") LocalDateTime updatedAt);

    int markClosed(@Param("outTradeNo") String outTradeNo,
                   @Param("closedAt") LocalDateTime closedAt,
                   @Param("updatedAt") LocalDateTime updatedAt);

    List<PaymentOrder> listNeedClose(@Param("now") LocalDateTime now,
                                     @Param("limit") int limit);

    List<PaymentOrder> listPendingForQuery(@Param("now") LocalDateTime now,
                                           @Param("limit") int limit);

    List<PaymentOrder> adminList(@Param("username") String username,
                                 @Param("status") String status,
                                 @Param("limit") int limit);
}
