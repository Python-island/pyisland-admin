package com.pyisland.server.user.payment.job;

import com.pyisland.server.user.payment.service.PaymentService;
import com.pyisland.server.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 支付补偿任务。
 */
@Component
public class PaymentJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentJob.class);

    private final PaymentService paymentService;
    private final UserService userService;

    public PaymentJob(PaymentService paymentService,
                      UserService userService) {
        this.paymentService = paymentService;
        this.userService = userService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void closeExpiredOrders() {
        int changed = paymentService.closeExpiredOrders();
        if (changed > 0) {
            log.info("payment job closed expired orders={}", changed);
        }
    }

    @Scheduled(cron = "0 */3 * * * *")
    public void reconcilePendingOrders() {
        int done = paymentService.reconcilePendingByQuery();
        if (done > 0) {
            log.info("payment job reconciled success orders={}", done);
        }
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void demoteExpiredProUsers() {
        int changed = userService.demoteExpiredProUsers(LocalDateTime.now());
        if (changed > 0) {
            log.info("payment job demoted expired pro users={}", changed);
        }
    }
}
