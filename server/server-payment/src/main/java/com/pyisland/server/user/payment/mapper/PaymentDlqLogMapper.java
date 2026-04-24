package com.pyisland.server.user.payment.mapper;

import com.pyisland.server.user.payment.entity.PaymentDlqLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaymentDlqLogMapper {

    int insert(PaymentDlqLog logItem);

    List<PaymentDlqLog> adminList(@Param("notifyId") String notifyId,
                                  @Param("outTradeNo") String outTradeNo,
                                  @Param("limit") int limit);

    List<PaymentDlqLog> adminListByTradeState(@Param("notifyId") String notifyId,
                                              @Param("outTradeNo") String outTradeNo,
                                              @Param("tradeState") String tradeState,
                                              @Param("limit") int limit);
}
