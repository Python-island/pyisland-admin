package com.pyisland.server.user.mapper;

import com.pyisland.server.user.entity.IdentityVerification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 身份认证记录数据访问接口。
 */
@Mapper
public interface IdentityVerificationMapper {

    /**
     * 新增认证记录。
     * @param record 认证实体。
     * @return 影响行数。
     */
    int insert(IdentityVerification record);

    /**
     * 按 certifyId 查询。
     * @param certifyId 支付宝 certify_id。
     * @return 认证记录或 null。
     */
    IdentityVerification selectByCertifyId(@Param("certifyId") String certifyId);

    /**
     * 按 outerOrderNo 查询。
     * @param outerOrderNo 商户订单号。
     * @return 认证记录或 null。
     */
    IdentityVerification selectByOuterOrderNo(@Param("outerOrderNo") String outerOrderNo);

    /**
     * 查询用户最近的认证记录。
     * @param username 用户名。
     * @param limit 最多返回条数。
     * @return 认证记录列表，按创建时间倒序。
     */
    List<IdentityVerification> selectByUsername(@Param("username") String username,
                                                @Param("limit") int limit);

    /**
     * 查询用户最新一条已通过的认证记录。
     * @param username 用户名。
     * @return 认证记录或 null。
     */
    IdentityVerification selectLatestPassedByUsername(@Param("username") String username);

    /**
     * 更新认证状态。
     * @param certifyId certify_id。
     * @param status 新状态。
     * @param materialInfoUrl 人脸素材存储 URL（可为 null）。
     * @param updatedAt 更新时间。
     * @return 影响行数。
     */
    int updateStatus(@Param("certifyId") String certifyId,
                     @Param("status") String status,
                     @Param("materialInfoUrl") String materialInfoUrl,
                     @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 仅更新素材存储 URL（异步上传完成后回写）。
     * @param certifyId certify_id。
     * @param materialInfoUrl 人脸素材存储 URL。
     * @param updatedAt 更新时间。
     * @return 影响行数。
     */
    int updateMaterialUrl(@Param("certifyId") String certifyId,
                          @Param("materialInfoUrl") String materialInfoUrl,
                          @Param("updatedAt") LocalDateTime updatedAt);
}
