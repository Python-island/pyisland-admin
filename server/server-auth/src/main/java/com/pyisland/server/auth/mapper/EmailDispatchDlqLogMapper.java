package com.pyisland.server.auth.mapper;

import com.pyisland.server.auth.entity.EmailDispatchDlqLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EmailDispatchDlqLogMapper {

    int insert(EmailDispatchDlqLog logItem);

    List<EmailDispatchDlqLog> adminList(@Param("traceId") String traceId,
                                        @Param("email") String email,
                                        @Param("limit") int limit);
}
