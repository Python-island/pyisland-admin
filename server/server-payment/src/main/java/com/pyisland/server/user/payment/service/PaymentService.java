package com.pyisland.server.user.payment.service;

import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.payment.config.AlipayProperties;
import com.pyisland.server.user.payment.config.WechatPayProperties;
import com.pyisland.server.user.payment.entity.PaymentDlqLog;
import com.pyisland.server.user.payment.entity.PaymentNotifyLog;
import com.pyisland.server.user.payment.entity.PaymentOrder;
import com.pyisland.server.user.payment.entity.PaymentPricingConfig;
import com.pyisland.server.user.payment.entity.PaymentTransaction;
import com.pyisland.server.user.payment.mapper.PaymentDlqLogMapper;
import com.pyisland.server.user.payment.mapper.PaymentNotifyLogMapper;
import com.pyisland.server.user.payment.mapper.PaymentOrderMapper;
import com.pyisland.server.user.payment.mapper.PaymentPricingConfigMapper;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    public static final String PRODUCT_TEST_PAY = "TEST_PAY";
    public static final int DEFAULT_PRO_MONTH_AMOUNT_FEN = 1500;
    private static final String REDIS_PRO_MONTH_AMOUNT_FEN_KEY = "payment:pricing:pro-month:amount-fen";
    private static final String REDIS_FREE_DESC_KEY = "payment:pricing:free:desc";
    private static final String REDIS_FREE_FEATURES_KEY = "payment:pricing:free:features";
    private static final String REDIS_PRO_DESC_KEY = "payment:pricing:pro:desc";
    private static final String REDIS_PRO_FEATURES_KEY = "payment:pricing:pro:features";
    private static final String REDIS_ORDER_CHANNEL_KEY_PREFIX = "payment:order:channel:";
    private static final String REDIS_NOTIFY_DONE_KEY_PREFIX = "payment:notify:done:";
    private static final Long PRICING_CONFIG_SINGLETON_ID = 1L;
    private static final String DEFAULT_FREE_DESC = "基础功能可用，适合轻度日常使用。";
    private static final String DEFAULT_PRO_DESC = "完整高级能力与持续更新支持。";
    private static final List<String> DEFAULT_FREE_FEATURES = List.of(
            "基础灵动岛组件",
            "常规设置与个性化",
            "社区公开内容浏览"
    );
    private static final List<String> DEFAULT_PRO_FEATURES = List.of(
            "全部 Free 权益",
            "Pro 专属功能与扩展",
            "优先体验新功能"
    );

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentPricingConfigMapper paymentPricingConfigMapper;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final PaymentNotifyLogMapper paymentNotifyLogMapper;
    private final PaymentDlqLogMapper paymentDlqLogMapper;
    private final WechatPayClient wechatPayClient;
    private final AlipaySdkClient alipaySdkClient;
    private final WechatPayProperties wechatProperties;
    private final AlipayProperties alipayProperties;
    private final UserService userService;
    private final StringRedisTemplate paymentRedisTemplate;
    private volatile int proMonthAmountFen = DEFAULT_PRO_MONTH_AMOUNT_FEN;
    private volatile String freePlanDesc = DEFAULT_FREE_DESC;
    private volatile String proPlanDesc = DEFAULT_PRO_DESC;
    private volatile List<String> freePlanFeatures = DEFAULT_FREE_FEATURES;
    private volatile List<String> proPlanFeatures = DEFAULT_PRO_FEATURES;

    public PaymentService(PaymentOrderMapper paymentOrderMapper,
                          PaymentPricingConfigMapper paymentPricingConfigMapper,
                          PaymentTransactionMapper paymentTransactionMapper,
                          PaymentNotifyLogMapper paymentNotifyLogMapper,
                          PaymentDlqLogMapper paymentDlqLogMapper,
                          WechatPayClient wechatPayClient,
                          AlipaySdkClient alipaySdkClient,
                          WechatPayProperties wechatProperties,
                          AlipayProperties alipayProperties,
                          UserService userService,
                          @Qualifier("paymentRedisTemplate") StringRedisTemplate paymentRedisTemplate) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.paymentPricingConfigMapper = paymentPricingConfigMapper;
        this.paymentTransactionMapper = paymentTransactionMapper;
        this.paymentNotifyLogMapper = paymentNotifyLogMapper;
        this.paymentDlqLogMapper = paymentDlqLogMapper;
        this.wechatPayClient = wechatPayClient;
        this.alipaySdkClient = alipaySdkClient;
        this.wechatProperties = wechatProperties;
        this.alipayProperties = alipayProperties;
        this.userService = userService;
        this.paymentRedisTemplate = paymentRedisTemplate;
    }

    @Transactional
    public PaymentOrder createProMonthOrder(String username) throws Exception {
        return createProMonthOrder(username, PaymentChannel.WECHAT);
    }

    @Transactional
    public PaymentOrder createProMonthOrder(String username, PaymentChannel channel) throws Exception {
        PaymentChannel actualChannel = channel == null ? PaymentChannel.WECHAT : channel;
        int proAmountFen = getProMonthAmountFen();
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase();
        String lockKey = "payment:lock:create:pro-month:"
                + actualChannel.name().toLowerCase() + ":"
                + (normalizedUsername.isBlank() ? "unknown" : normalizedUsername);
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = paymentRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 15, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            throw new IllegalStateException("订单创建过于频繁，请稍后重试");
        }

        try {
            String outTradeNo = buildOutTradeNo(username, actualChannel);
            String prepayId = null;
            String codeUrl;
            if (actualChannel == PaymentChannel.ALIPAY) {
                AlipaySdkClient.PlaceOrderResult result = alipaySdkClient.createPreOrder(
                        outTradeNo,
                        "eIsland Pro 月付",
                        proAmountFen
                );
                prepayId = result.tradeNo();
                codeUrl = result.qrCode();
            } else {
                WechatPayClient.PlaceOrderResult result = wechatPayClient.createNativeOrder(
                        outTradeNo,
                        "eIsland Pro 月付",
                        proAmountFen
                );
                prepayId = result.prepayId();
                codeUrl = result.codeUrl();
            }
            LocalDateTime now = LocalDateTime.now();

            PaymentOrder order = new PaymentOrder();
            order.setOutTradeNo(outTradeNo);
            order.setUsername(username);
            order.setProductCode(PRODUCT_PRO_MONTH);
            order.setAmountFen(proAmountFen);
            order.setCurrency("CNY");
            order.setStatus(PaymentOrder.STATUS_PAYING);
            order.setWxPrepayId(prepayId);
            order.setWxCodeUrl(codeUrl);
            order.setExpireAt(now.plusMinutes(Math.max(5, getOrderExpireMinutes(actualChannel))));
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            paymentOrderMapper.insert(order);
            saveOrderChannel(outTradeNo, actualChannel);
            return order;
        } finally {
            String currentValue = paymentRedisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(currentValue)) {
                paymentRedisTemplate.delete(lockKey);
            }
        }
    }

    @Transactional
    public PaymentOrder createAdminTestOrder(String username,
                                             PaymentChannel channel,
                                             int amountFen,
                                             String subject) throws Exception {
        PaymentChannel actualChannel = channel == null ? PaymentChannel.WECHAT : channel;
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase();
        String lockKey = "payment:lock:create:test:"
                + actualChannel.name().toLowerCase() + ":"
                + (normalizedUsername.isBlank() ? "unknown" : normalizedUsername);
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = paymentRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 15, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            throw new IllegalStateException("测试单创建过于频繁，请稍后重试");
        }

        try {
            String outTradeNo = buildOutTradeNo(username, actualChannel);
            String prepayId = null;
            String codeUrl;
            String normalizedSubject = subject == null || subject.isBlank() ? "eIsland 支付测试" : subject.trim();
            if (actualChannel == PaymentChannel.ALIPAY) {
                AlipaySdkClient.PlaceOrderResult result = alipaySdkClient.createPreOrder(
                        outTradeNo,
                        normalizedSubject,
                        amountFen
                );
                prepayId = result.tradeNo();
                codeUrl = result.qrCode();
            } else {
                WechatPayClient.PlaceOrderResult result = wechatPayClient.createNativeOrder(
                        outTradeNo,
                        normalizedSubject,
                        amountFen
                );
                prepayId = result.prepayId();
                codeUrl = result.codeUrl();
            }
            LocalDateTime now = LocalDateTime.now();

            PaymentOrder order = new PaymentOrder();
            order.setOutTradeNo(outTradeNo);
            order.setUsername(username);
            order.setProductCode(PRODUCT_TEST_PAY);
            order.setAmountFen(amountFen);
            order.setCurrency("CNY");
            order.setStatus(PaymentOrder.STATUS_PAYING);
            order.setWxPrepayId(prepayId);
            order.setWxCodeUrl(codeUrl);
            order.setExpireAt(now.plusMinutes(Math.max(5, getOrderExpireMinutes(actualChannel))));
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            paymentOrderMapper.insert(order);
            saveOrderChannel(outTradeNo, actualChannel);
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
        PaymentChannel channel = resolveOrderChannel(order);
        try {
            if (channel == PaymentChannel.ALIPAY) {
                if (!alipaySdkClient.isAvailable()) {
                    return order;
                }
                AlipaySdkClient.QueryResult query = alipaySdkClient.queryOrder(order.getOutTradeNo());
                if (query.success()) {
                    completeOrderIfPending(
                            channel,
                            order.getOutTradeNo(),
                            query.tradeNo(),
                            query.successTime(),
                            query.tradeStatus(),
                            "{}"
                    );
                } else if (query.shouldClose()) {
                    paymentOrderMapper.markClosed(order.getOutTradeNo(), LocalDateTime.now(), LocalDateTime.now());
                }
            } else {
                if (!wechatPayClient.isAvailable()) {
                    return order;
                }
                WechatPayClient.QueryResult query = wechatPayClient.queryOrder(order.getOutTradeNo());
                if (query.success()) {
                    completeOrderIfPending(
                            channel,
                            order.getOutTradeNo(),
                            query.transactionId(),
                            query.successTime(),
                            query.tradeState(),
                            "{}"
                    );
                } else if (query.shouldClose()) {
                    paymentOrderMapper.markClosed(order.getOutTradeNo(), LocalDateTime.now(), LocalDateTime.now());
                }
            }
        } catch (Exception ex) {
            log.warn("refresh order by query failed channel={} outTradeNo={} err={}", channel, order.getOutTradeNo(), ex.getMessage());
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
        PaymentChannel channel = resolveOrderChannel(order);
        if (channel == PaymentChannel.ALIPAY) {
            alipaySdkClient.closeOrder(order.getOutTradeNo());
        } else {
            wechatPayClient.closeOrder(order.getOutTradeNo());
        }
        int updated = paymentOrderMapper.markClosed(order.getOutTradeNo(), LocalDateTime.now(), LocalDateTime.now());
        return updated > 0;
    }

    @Transactional
    public boolean completeOrderIfPending(PaymentChannel channel,
                                          String outTradeNo,
                                          String wxTransactionId,
                                          OffsetDateTime paidAt,
                                          String tradeState,
                                          String rawJson) {
        PaymentChannel actualChannel = channel == null ? PaymentChannel.WECHAT : channel;
        String dedupTransactionId = wxTransactionId == null || wxTransactionId.isBlank() ? outTradeNo : wxTransactionId;
        String notifyDoneKey = REDIS_NOTIFY_DONE_KEY_PREFIX + actualChannel.name().toLowerCase() + ":" + outTradeNo + ":" + dedupTransactionId;
        Boolean firstDone = paymentRedisTemplate.opsForValue().setIfAbsent(notifyDoneKey, "1", 30, TimeUnit.DAYS);
        if (!Boolean.TRUE.equals(firstDone)) {
            return false;
        }

        LocalDateTime paidTime = paidAt == null
                ? LocalDateTime.now()
                : paidAt.withOffsetSameInstant(ZoneOffset.ofHours(8)).toLocalDateTime();
        int updated = paymentOrderMapper.markSuccess(outTradeNo, wxTransactionId, paidTime, LocalDateTime.now());
        if (updated <= 0) {
            return false;
        }
        saveOrderChannel(outTradeNo, actualChannel);
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
            PaymentChannel channel = resolveOrderChannel(order);
            if (channel == PaymentChannel.ALIPAY) {
                alipaySdkClient.closeOrder(order.getOutTradeNo());
            } else {
                wechatPayClient.closeOrder(order.getOutTradeNo());
            }
            int updated = paymentOrderMapper.markClosed(order.getOutTradeNo(), LocalDateTime.now(), LocalDateTime.now());
            changed += updated;
        }
        return changed;
    }

    @Transactional
    public int reconcilePendingByQuery() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(2);
        List<PaymentOrder> list = paymentOrderMapper.listPendingForQuery(before, Math.max(10,
                Math.max(wechatProperties.getQueryPendingBatchSize(), alipayProperties.getQueryPendingBatchSize())));
        int done = 0;
        for (PaymentOrder order : list) {
            PaymentChannel channel = resolveOrderChannel(order);
            try {
                if (channel == PaymentChannel.ALIPAY) {
                    if (!alipaySdkClient.isAvailable()) {
                        continue;
                    }
                    AlipaySdkClient.QueryResult query = alipaySdkClient.queryOrder(order.getOutTradeNo());
                    if (query.success()) {
                        boolean success = completeOrderIfPending(
                                channel,
                                order.getOutTradeNo(),
                                query.tradeNo(),
                                query.successTime(),
                                query.tradeStatus(),
                                "{}"
                        );
                        if (success) {
                            done++;
                        }
                    } else if (query.shouldClose()) {
                        paymentOrderMapper.markClosed(order.getOutTradeNo(), LocalDateTime.now(), LocalDateTime.now());
                    }
                } else {
                    if (!wechatPayClient.isAvailable()) {
                        continue;
                    }
                    WechatPayClient.QueryResult query = wechatPayClient.queryOrder(order.getOutTradeNo());
                    if (query.success()) {
                        boolean success = completeOrderIfPending(
                                channel,
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
                }
            } catch (Exception ex) {
                log.warn("query order failed channel={} outTradeNo={} err={}", channel, order.getOutTradeNo(), ex.getMessage());
            }
        }
        return done;
    }

    private String buildOutTradeNo(String username, PaymentChannel channel) {
        String prefix = username == null ? "user" : username.replaceAll("[^a-zA-Z0-9]", "");
        if (prefix.isBlank()) {
            prefix = "user";
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        String channelCode = channel == PaymentChannel.ALIPAY ? "A" : "W";
        return "EI" + channelCode + prefix.substring(0, Math.min(prefix.length(), 8)).toUpperCase() + suffix;
    }

    public Map<String, Object> toOrderPayload(PaymentOrder order, User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("outTradeNo", order.getOutTradeNo());
        data.put("productCode", order.getProductCode());
        data.put("amountFen", order.getAmountFen());
        data.put("currency", order.getCurrency());
        data.put("status", order.getStatus());
        data.put("channel", resolveOrderChannel(order).name());
        data.put("qrCodeUrl", order.getWxCodeUrl());
        data.put("expireAt", order.getExpireAt() != null ? order.getExpireAt().toString() : null);
        data.put("paidAt", order.getPaidAt() != null ? order.getPaidAt().toString() : null);
        data.put("proExpireAt", user != null && user.getProExpireAt() != null ? user.getProExpireAt().toString() : null);
        return data;
    }

    public Map<String, Object> toAdminOrderPayload(PaymentOrder order) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", order.getId());
        data.put("outTradeNo", order.getOutTradeNo());
        data.put("username", order.getUsername());
        data.put("productCode", order.getProductCode());
        data.put("amountFen", order.getAmountFen());
        data.put("currency", order.getCurrency());
        data.put("status", order.getStatus());
        data.put("channel", resolveOrderChannel(order).name());
        data.put("wxPrepayId", order.getWxPrepayId());
        data.put("wxCodeUrl", order.getWxCodeUrl());
        data.put("wxTransactionId", order.getWxTransactionId());
        data.put("expireAt", order.getExpireAt() != null ? order.getExpireAt().toString() : null);
        data.put("paidAt", order.getPaidAt() != null ? order.getPaidAt().toString() : null);
        data.put("closedAt", order.getClosedAt() != null ? order.getClosedAt().toString() : null);
        data.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
        data.put("updatedAt", order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null);
        return data;
    }

    public Map<String, Object> getProMonthPricingPayload() {
        int amountFen = getProMonthAmountFen();
        String freeDesc = getFreePlanDesc();
        String proDesc = getProPlanDesc();
        List<String> freeFeatures = getFreePlanFeatures();
        List<String> proFeatures = getProPlanFeatures();
        Map<String, Object> data = new HashMap<>();
        data.put("productCode", PRODUCT_PRO_MONTH);
        data.put("amountFen", amountFen);
        data.put("currency", "CNY");
        data.put("billingCycle", "MONTH");
        data.put("amountYuan", String.format(Locale.ROOT, "%.2f", amountFen / 100.0));
        data.put("subject", "eIsland Pro 月付");
        data.put("freeDesc", freeDesc);
        data.put("proDesc", proDesc);
        data.put("freeFeatures", freeFeatures);
        data.put("proFeatures", proFeatures);
        return data;
    }

    public int getProMonthAmountFen() {
        int fallbackAmount = Math.max(1, proMonthAmountFen);
        try {
            String cachedValue = paymentRedisTemplate.opsForValue().get(REDIS_PRO_MONTH_AMOUNT_FEN_KEY);
            if (cachedValue == null || cachedValue.isBlank()) {
                PricingSnapshot snapshot = loadPricingSnapshotFromDb();
                applySnapshotToMemoryAndCache(snapshot);
                return snapshot.proMonthAmountFen;
            }
            int cachedAmount = Integer.parseInt(cachedValue.trim());
            int normalizedCachedAmount = Math.max(1, cachedAmount);
            if (normalizedCachedAmount != cachedAmount) {
                paymentRedisTemplate.opsForValue().set(REDIS_PRO_MONTH_AMOUNT_FEN_KEY, String.valueOf(normalizedCachedAmount));
            }
            proMonthAmountFen = normalizedCachedAmount;
            return normalizedCachedAmount;
        } catch (Exception ex) {
            log.warn("read pro month pricing from redis failed, fallback to memory value={} err={}", fallbackAmount, ex.getMessage());
            return fallbackAmount;
        }
    }

    public void setProMonthAmountFen(int amountFen) {
        int normalizedAmount = Math.max(1, amountFen);
        PricingSnapshot current = loadPricingSnapshotFromDb();
        PricingSnapshot next = new PricingSnapshot(
                normalizedAmount,
                current.freeDesc,
                current.freeFeatures,
                current.proDesc,
                current.proFeatures
        );
        savePricingSnapshotToDb(next);
        applySnapshotToMemoryAndCache(next);
    }

    public String getFreePlanDesc() {
        return readTextWithCache(REDIS_FREE_DESC_KEY, freePlanDesc, DEFAULT_FREE_DESC, value -> freePlanDesc = value);
    }

    public String getProPlanDesc() {
        return readTextWithCache(REDIS_PRO_DESC_KEY, proPlanDesc, DEFAULT_PRO_DESC, value -> proPlanDesc = value);
    }

    public List<String> getFreePlanFeatures() {
        return readLinesWithCache(REDIS_FREE_FEATURES_KEY, freePlanFeatures, DEFAULT_FREE_FEATURES, value -> freePlanFeatures = value);
    }

    public List<String> getProPlanFeatures() {
        return readLinesWithCache(REDIS_PRO_FEATURES_KEY, proPlanFeatures, DEFAULT_PRO_FEATURES, value -> proPlanFeatures = value);
    }

    public void setFreePlanDesc(String value) {
        String normalized = normalizeText(value, DEFAULT_FREE_DESC);
        PricingSnapshot current = loadPricingSnapshotFromDb();
        PricingSnapshot next = new PricingSnapshot(
                current.proMonthAmountFen,
                normalized,
                current.freeFeatures,
                current.proDesc,
                current.proFeatures
        );
        savePricingSnapshotToDb(next);
        applySnapshotToMemoryAndCache(next);
    }

    public void setProPlanDesc(String value) {
        String normalized = normalizeText(value, DEFAULT_PRO_DESC);
        PricingSnapshot current = loadPricingSnapshotFromDb();
        PricingSnapshot next = new PricingSnapshot(
                current.proMonthAmountFen,
                current.freeDesc,
                current.freeFeatures,
                normalized,
                current.proFeatures
        );
        savePricingSnapshotToDb(next);
        applySnapshotToMemoryAndCache(next);
    }

    public void setFreePlanFeatures(List<String> features) {
        List<String> normalized = normalizeFeatures(features, DEFAULT_FREE_FEATURES);
        PricingSnapshot current = loadPricingSnapshotFromDb();
        PricingSnapshot next = new PricingSnapshot(
                current.proMonthAmountFen,
                current.freeDesc,
                normalized,
                current.proDesc,
                current.proFeatures
        );
        savePricingSnapshotToDb(next);
        applySnapshotToMemoryAndCache(next);
    }

    public void setProPlanFeatures(List<String> features) {
        List<String> normalized = normalizeFeatures(features, DEFAULT_PRO_FEATURES);
        PricingSnapshot current = loadPricingSnapshotFromDb();
        PricingSnapshot next = new PricingSnapshot(
                current.proMonthAmountFen,
                current.freeDesc,
                current.freeFeatures,
                current.proDesc,
                normalized
        );
        savePricingSnapshotToDb(next);
        applySnapshotToMemoryAndCache(next);
    }

    private String readTextWithCache(String key,
                                     String memoryValue,
                                     String fallback,
                                     java.util.function.Consumer<String> memoryUpdater) {
        String normalizedFallback = normalizeText(memoryValue, fallback);
        try {
            String cached = paymentRedisTemplate.opsForValue().get(key);
            if (cached == null || cached.isBlank()) {
                PricingSnapshot snapshot = loadPricingSnapshotFromDb();
                applySnapshotToMemoryAndCache(snapshot);
                if (REDIS_FREE_DESC_KEY.equals(key)) {
                    return snapshot.freeDesc;
                }
                if (REDIS_PRO_DESC_KEY.equals(key)) {
                    return snapshot.proDesc;
                }
                return normalizedFallback;
            }
            String normalizedCached = normalizeText(cached, fallback);
            if (!normalizedCached.equals(cached)) {
                paymentRedisTemplate.opsForValue().set(key, normalizedCached);
            }
            memoryUpdater.accept(normalizedCached);
            return normalizedCached;
        } catch (Exception ex) {
            log.warn("read pricing text cache failed key={} err={}", key, ex.getMessage());
            return normalizedFallback;
        }
    }

    private List<String> readLinesWithCache(String key,
                                            List<String> memoryValue,
                                            List<String> fallback,
                                            java.util.function.Consumer<List<String>> memoryUpdater) {
        List<String> normalizedFallback = normalizeFeatures(memoryValue, fallback);
        try {
            String cached = paymentRedisTemplate.opsForValue().get(key);
            if (cached == null || cached.isBlank()) {
                PricingSnapshot snapshot = loadPricingSnapshotFromDb();
                applySnapshotToMemoryAndCache(snapshot);
                if (REDIS_FREE_FEATURES_KEY.equals(key)) {
                    return snapshot.freeFeatures;
                }
                if (REDIS_PRO_FEATURES_KEY.equals(key)) {
                    return snapshot.proFeatures;
                }
                return normalizedFallback;
            }
            List<String> normalizedCached = normalizeFeatures(Arrays.asList(cached.split("\\r?\\n")), fallback);
            String normalizedCachedText = String.join("\n", normalizedCached);
            if (!normalizedCachedText.equals(cached)) {
                paymentRedisTemplate.opsForValue().set(key, normalizedCachedText);
            }
            memoryUpdater.accept(normalizedCached);
            return normalizedCached;
        } catch (Exception ex) {
            log.warn("read pricing features cache failed key={} err={}", key, ex.getMessage());
            return normalizedFallback;
        }
    }

    private void writeTextCache(String key, String value) {
        try {
            paymentRedisTemplate.opsForValue().set(key, value);
        } catch (Exception ex) {
            log.warn("write pricing text cache failed key={} err={}", key, ex.getMessage());
        }
    }

    private void writeLinesCache(String key, List<String> value) {
        try {
            paymentRedisTemplate.opsForValue().set(key, String.join("\n", value));
        } catch (Exception ex) {
            log.warn("write pricing features cache failed key={} err={}", key, ex.getMessage());
        }
    }

    private synchronized PricingSnapshot loadPricingSnapshotFromDb() {
        PaymentPricingConfig dbConfig = paymentPricingConfigMapper.selectById(PRICING_CONFIG_SINGLETON_ID);
        if (dbConfig == null) {
            PricingSnapshot defaultSnapshot = new PricingSnapshot(
                    Math.max(1, proMonthAmountFen),
                    normalizeText(freePlanDesc, DEFAULT_FREE_DESC),
                    normalizeFeatures(freePlanFeatures, DEFAULT_FREE_FEATURES),
                    normalizeText(proPlanDesc, DEFAULT_PRO_DESC),
                    normalizeFeatures(proPlanFeatures, DEFAULT_PRO_FEATURES)
            );
            PaymentPricingConfig creating = new PaymentPricingConfig();
            creating.setId(PRICING_CONFIG_SINGLETON_ID);
            creating.setProMonthAmountFen(defaultSnapshot.proMonthAmountFen);
            creating.setFreeDesc(defaultSnapshot.freeDesc);
            creating.setFreeFeaturesText(String.join("\n", defaultSnapshot.freeFeatures));
            creating.setProDesc(defaultSnapshot.proDesc);
            creating.setProFeaturesText(String.join("\n", defaultSnapshot.proFeatures));
            creating.setUpdatedAt(LocalDateTime.now());
            paymentPricingConfigMapper.insert(creating);
            return defaultSnapshot;
        }

        int normalizedAmount = Math.max(1, dbConfig.getProMonthAmountFen() == null ? DEFAULT_PRO_MONTH_AMOUNT_FEN : dbConfig.getProMonthAmountFen());
        String normalizedFreeDesc = normalizeText(dbConfig.getFreeDesc(), DEFAULT_FREE_DESC);
        List<String> normalizedFreeFeatures = normalizeFeatures(parseFeatureText(dbConfig.getFreeFeaturesText()), DEFAULT_FREE_FEATURES);
        String normalizedProDesc = normalizeText(dbConfig.getProDesc(), DEFAULT_PRO_DESC);
        List<String> normalizedProFeatures = normalizeFeatures(parseFeatureText(dbConfig.getProFeaturesText()), DEFAULT_PRO_FEATURES);
        return new PricingSnapshot(
                normalizedAmount,
                normalizedFreeDesc,
                normalizedFreeFeatures,
                normalizedProDesc,
                normalizedProFeatures
        );
    }

    private synchronized void savePricingSnapshotToDb(PricingSnapshot snapshot) {
        paymentPricingConfigMapper.update(
                PRICING_CONFIG_SINGLETON_ID,
                snapshot.proMonthAmountFen,
                snapshot.freeDesc,
                String.join("\n", snapshot.freeFeatures),
                snapshot.proDesc,
                String.join("\n", snapshot.proFeatures),
                LocalDateTime.now()
        );
    }

    private void applySnapshotToMemoryAndCache(PricingSnapshot snapshot) {
        proMonthAmountFen = snapshot.proMonthAmountFen;
        freePlanDesc = snapshot.freeDesc;
        freePlanFeatures = snapshot.freeFeatures;
        proPlanDesc = snapshot.proDesc;
        proPlanFeatures = snapshot.proFeatures;
        writeTextCache(REDIS_PRO_MONTH_AMOUNT_FEN_KEY, String.valueOf(snapshot.proMonthAmountFen));
        writeTextCache(REDIS_FREE_DESC_KEY, snapshot.freeDesc);
        writeLinesCache(REDIS_FREE_FEATURES_KEY, snapshot.freeFeatures);
        writeTextCache(REDIS_PRO_DESC_KEY, snapshot.proDesc);
        writeLinesCache(REDIS_PRO_FEATURES_KEY, snapshot.proFeatures);
    }

    private List<String> parseFeatureText(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(text.split("\\r?\\n"));
    }

    private String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private List<String> normalizeFeatures(List<String> features, List<String> fallback) {
        if (features == null) {
            return fallback;
        }
        List<String> normalized = new ArrayList<>();
        for (String item : features) {
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            return fallback;
        }
        return Collections.unmodifiableList(normalized);
    }

    private static class PricingSnapshot {
        private final int proMonthAmountFen;
        private final String freeDesc;
        private final List<String> freeFeatures;
        private final String proDesc;
        private final List<String> proFeatures;

        private PricingSnapshot(int proMonthAmountFen,
                                String freeDesc,
                                List<String> freeFeatures,
                                String proDesc,
                                List<String> proFeatures) {
            this.proMonthAmountFen = proMonthAmountFen;
            this.freeDesc = freeDesc;
            this.freeFeatures = freeFeatures;
            this.proDesc = proDesc;
            this.proFeatures = proFeatures;
        }
    }

    private int getOrderExpireMinutes(PaymentChannel channel) {
        if (channel == PaymentChannel.ALIPAY) {
            return 15;
        }
        return wechatProperties.getOrderExpireMinutes();
    }

    private PaymentChannel resolveOrderChannel(PaymentOrder order) {
        if (order == null || order.getOutTradeNo() == null || order.getOutTradeNo().isBlank()) {
            return PaymentChannel.WECHAT;
        }
        String redisValue = paymentRedisTemplate.opsForValue().get(REDIS_ORDER_CHANNEL_KEY_PREFIX + order.getOutTradeNo());
        if (redisValue != null && !redisValue.isBlank()) {
            return PaymentChannel.from(redisValue);
        }
        if (order.getOutTradeNo().startsWith("EIA")) {
            return PaymentChannel.ALIPAY;
        }
        return PaymentChannel.WECHAT;
    }

    private void saveOrderChannel(String outTradeNo, PaymentChannel channel) {
        if (outTradeNo == null || outTradeNo.isBlank() || channel == null) {
            return;
        }
        paymentRedisTemplate.opsForValue().set(
                REDIS_ORDER_CHANNEL_KEY_PREFIX + outTradeNo,
                channel.name(),
                30,
                TimeUnit.DAYS
        );
    }
}
