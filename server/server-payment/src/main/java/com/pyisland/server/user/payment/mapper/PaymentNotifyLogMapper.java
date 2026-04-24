package com.pyisland.server.user.payment.mapper;

import com.pyisland.server.user.payment.entity.PaymentNotifyLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentNotifyLogMapper {

    int insertIgnore(PaymentNotifyLog notifyLog);
}
