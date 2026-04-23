package com.pyisland.server.user.payment.service;

import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.payment.config.WechatPayProperties;
import com.pyisland.server.user.payment.entity.PaymentDlqLog;
import com.pyisland.server.user.payment.entity.PaymentNotifyLog;
import com.pyisland.server.user.payment.entity.PaymentOrder;
import com.pyisland.server.user.payment.entity.PaymentTransaction;
import com.pyisland.server.user.payment.mapper.PaymentDlqLogMapper;
import com.pyisland.server.user.payment.mapper.PaymentNotifyLogMapper;
import com.pyisland.server.user.payment.mapper.PaymentOrderMapper;
import com.pyisland.server.user.payment.mapper.PaymentTransactionMapper;
import com.pyisland.server.user.service.UserService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 支付核心服务。
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    public static final String PRODUCT_PRO_MONTH = "PRO_MONTH";
    public static final int PRO_MONTH_AMOUNT_FEN = 1500;

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final PaymentNotifyLogMapper paymentNotifyLogMapper;
    private final PaymentDlqLogMapper paymentDlqLogMapper;
    private final WechatPayClient wechatPayClient;
    private final WechatPayProperties properties;
    private final UserService userService;
    private final StringRedisTemplate paymentRedisTemplate;

    public PaymentService(PaymentOrderMapper paymentOrderMapper,
                          PaymentTransactionMapper paymentTransactionMapper,
                          PaymentNotifyLogMapper paymentNotifyLogMapper,
                          PaymentDlqLogMapper paymentDlqLogMapper,
                          WechatPayClient wechatPayClient,
                          WechatPayProperties properties,
                          UserService userService,
                          @Qualifier("paymentRedisTemplate") StringRedisTemplate paymentRedisTemplate) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.paymentTransactionMapper = paymentTransactionMapper;
        this.paymentNotifyLogMapper = paymentNotifyLogMapper;
        this.paymentDlqLogMapper = paymentDlqLogMapper;
        this.wechatPayClient = wechatPayClient;
        this.properties = properties;
        this.userService = userService;
        this.paymentRedisTemplate = paymentRedisTemplate;
    }

    @Transactional
    public PaymentOrder createProMonthOrder(String username) throws Exception {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase();
        String lockKey = "payment:lock:create:pro-month:" + (normalizedUsername.isBlank() ? "unknown" : normalizedUsername);
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = paymentRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 15, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            throw new IllegalStateException("订单创建过于频繁，请稍后重试");
        }

        try {
            String outTradeNo = buildOutTradeNo(username);
            WechatPayClient.PlaceOrderResult result = wechatPayClient.createNativeOrder(
                    outTradeNo,
                    "eIsland Pro 月付",
                    PRO_MONTH_AMOUNT_FEN
            );
            LocalDateTime now = LocalDateTime.now();

            PaymentOrder order = new PaymentOrder();
            order.setOutTradeNo(outTradeNo);
            order.setUsername(username);
            order.setProductCode(PRODUCT_PRO_MONTH);
            order.setAmountFen(PRO_MONTH_AMOUNT_FEN);
            order.setCurrency("CNY");
            order.setStatus(PaymentOrder.STATUS_PAYING);
            order.setWxPrepayId(result.prepayId());
            order.setWxCodeUrl(result.codeUrl());
            order.setExpireAt(now.plusMinutes(Math.max(5, properties.getOrderExpireMinutes())));
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            paymentOrderMapper.insert(order);
            return order;
        } finally {
            String currentValue = paymentRedisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(currentValue)) {
                paymentRedisTemplate.delete(lockKey);
            }
        }
    }

    public PaymentOrder findOrder(String outTradeNo) {
        return paymentOrderMapper.selectByOutTradeNo(outTradeNo);
    }

    public PaymentOrder refreshOrderIfNeeded(PaymentOrder order) {
        if (order == null || !PaymentOrder.STATUS_PAYING.equals(order.getStatus())) {
            return order;
        }
        if (!wechatPayClient.isAvailable()) {
            return order;
        }
        try {
            WechatPayClient.QueryResult query = wechatPayClient.queryOrder(order.getOutTradeNo());
            if (query.success()) {
                completeOrderIfPending(
                        order.getOutTradeNo(),
                        query.transactionId(),
                        query.successTime(),
                        query.tradeState(),
                        "{}"
                );
            } else if (query.shouldClose()) {
                paymentOrderMapper.markClosed(order.getOutTradeNo(), LocalDateTime.now(), LocalDateTime.now());
            }
        } catch (Exception ex) {
            log.warn("refresh order by query failed outTradeNo={} err={}", order.getOutTradeNo(), ex.getMessage());
        }
        return paymentOrderMapper.selectByOutTradeNo(order.getOutTradeNo());
    }

    public List<PaymentOrder> adminList(String username, String status, int limit) {
        return paymentOrderMapper.adminList(username, status, Math.max(1, Math.min(limit, 200)));
    }

    public PaymentOrder adminRefreshOrder(String outTradeNo) {
        if (outTradeNo == null || outTradeNo.isBlank()) {
            return null;
        }
        PaymentOrder order = paymentOrderMapper.selectByOutTradeNo(outTradeNo.trim());
        return refreshOrderIfNeeded(order);
    }

    @Transactional
    public boolean adminCloseOrder(String outTradeNo) {
        if (outTradeNo == null || outTradeNo.isBlank()) {
            return false;
        }
        PaymentOrder order = paymentOrderMapper.selectByOutTradeNo(outTradeNo.trim());
        if (order == null || !PaymentOrder.STATUS_PAYING.equals(order.getStatus())) {
            return false;
        }
        wechatPayClient.closeOrder(order.getOutTradeNo());
        int updated = paymentOrderMapper.markClosed(order.getOutTradeNo(), LocalDateTime.now(), LocalDateTime.now());
        return updated > 0;
    }

    @Transactional
    public boolean completeOrderIfPending(String outTradeNo,
                                          String wxTransactionId,
                                          OffsetDateTime paidAt,
                                          String tradeState,
                                          String rawJson) {
        LocalDateTime paidTime = paidAt == null
                ? LocalDateTime.now()
                : paidAt.withOffsetSameInstant(ZoneOffset.ofHours(8)).toLocalDateTime();
        int updated = paymentOrderMapper.markSuccess(outTradeNo, wxTransactionId, paidTime, LocalDateTime.now());
        if (updated <= 0) {
            return false;
        }
        PaymentOrder order = paymentOrderMapper.selectByOutTradeNo(outTradeNo);
        if (order == null) {
            return false;
        }

        PaymentTransaction tx = new PaymentTransaction();
        tx.setOutTradeNo(outTradeNo);
        tx.setWxTransactionId(wxTransactionId == null ? outTradeNo : wxTransactionId);
        tx.setTradeState(tradeState == null ? "SUCCESS" : tradeState);
        tx.setSuccessTime(paidTime);
        tx.setRawJson(rawJson);
        tx.setCreatedAt(LocalDateTime.now());
        paymentTransactionMapper.insertIgnore(tx);

        if (PRODUCT_PRO_MONTH.equals(order.getProductCode())) {
            userService.grantProOneMonth(order.getUsername());
        }
        return true;
    }

    @Transactional
    public void logNotify(String notifyId,
                          String outTradeNo,
                          String eventType,
                          boolean verifyOk,
                          String processStatus,
                          String rawBody) {
        PaymentNotifyLog logItem = new PaymentNotifyLog();
        logItem.setNotifyId(notifyId);
        logItem.setOutTradeNo(outTradeNo);
        logItem.setEventType(eventType);
        logItem.setVerifyOk(verifyOk);
        logItem.setProcessStatus(processStatus);
        logItem.setRawBody(rawBody);
        logItem.setCreatedAt(LocalDateTime.now());
        paymentNotifyLogMapper.insertIgnore(logItem);
    }

    @Transactional
    public void logDlqNotify(String notifyId,
                             String outTradeNo,
                             String tradeState,
                             int retryCount,
                             String errorMessage,
                             String rawBody) {
        PaymentDlqLog logItem = new PaymentDlqLog();
        logItem.setNotifyId(notifyId);
        logItem.setOutTradeNo(outTradeNo);
        logItem.setTradeState(tradeState);
        logItem.setRetryCount(Math.max(0, retryCount));
        logItem.setErrorMessage(errorMessage);
        logItem.setRawBody(rawBody);
        logItem.setCreatedAt(LocalDateTime.now());
        paymentDlqLogMapper.insert(logItem);
    }

    public List<PaymentDlqLog> adminListDlq(String notifyId, String outTradeNo, int limit) {
        String normalizedNotifyId = notifyId == null ? null : notifyId.trim();
        String normalizedOutTradeNo = outTradeNo == null ? null : outTradeNo.trim();
        return paymentDlqLogMapper.adminList(normalizedNotifyId, normalizedOutTradeNo, Math.max(1, Math.min(limit, 200)));
    }

    @Transactional
    public int closeExpiredOrders() {
        List<PaymentOrder> list = paymentOrderMapper.listNeedClose(LocalDateTime.now(), 100);
        int changed = 0;
        for (PaymentOrder order : list) {
            wechatPayClient.closeOrder(order.getOutTradeNo());
            int updated = paymentOrderMapper.markClosed(order.getOutTradeNo(), LocalDateTime.now(), LocalDateTime.now());
            changed += updated;
        }
        return changed;
    }

    @Transactional
    public int reconcilePendingByQuery() {
        if (!wechatPayClient.isAvailable()) {
            return 0;
        }
        LocalDateTime before = LocalDateTime.now().minusMinutes(2);
        List<PaymentOrder> list = paymentOrderMapper.listPendingForQuery(before, Math.max(10, properties.getQueryPendingBatchSize()));
        int done = 0;
        for (PaymentOrder order : list) {
            try {
                WechatPayClient.QueryResult query = wechatPayClient.queryOrder(order.getOutTradeNo());
                if (query.success()) {
                    boolean success = completeOrderIfPending(
                            order.getOutTradeNo(),
                            query.transactionId(),
                            query.successTime(),
                            query.tradeState(),
                            "{}"
                    );
                    if (success) {
                        done++;
                    }
                } else if (query.shouldClose()) {
                    paymentOrderMapper.markClosed(order.getOutTradeNo(), LocalDateTime.now(), LocalDateTime.now());
                }
            } catch (Exception ex) {
                log.warn("query wechat order failed outTradeNo={} err={}", order.getOutTradeNo(), ex.getMessage());
            }
        }
        return done;
    }

    private String buildOutTradeNo(String username) {
        String prefix = username == null ? "user" : username.replaceAll("[^a-zA-Z0-9]", "");
        if (prefix.isBlank()) {
            prefix = "user";
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        return "EI" + prefix.substring(0, Math.min(prefix.length(), 8)).toUpperCase() + suffix;
    }

    public Map<String, Object> toOrderPayload(PaymentOrder order, User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("outTradeNo", order.getOutTradeNo());
        data.put("productCode", order.getProductCode());
        data.put("amountFen", order.getAmountFen());
        data.put("currency", order.getCurrency());
        data.put("status", order.getStatus());
        data.put("qrCodeUrl", order.getWxCodeUrl());
        data.put("expireAt", order.getExpireAt() != null ? order.getExpireAt().toString() : null);
        data.put("paidAt", order.getPaidAt() != null ? order.getPaidAt().toString() : null);
        data.put("proExpireAt", user != null && user.getProExpireAt() != null ? user.getProExpireAt().toString() : null);
        return data;
    }
}
