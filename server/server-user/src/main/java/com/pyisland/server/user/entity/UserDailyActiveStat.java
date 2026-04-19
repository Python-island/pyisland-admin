package com.pyisland.server.user.entity;

import java.time.LocalDate;

/**
 * 用户日活统计条目。
 */
public class UserDailyActiveStat {

    private LocalDate statDate;
    private Long activeCount;

    /**
     * 统计日期。
     * @return 日期。
     */
    public LocalDate getStatDate() {
        return statDate;
    }

    /**
     * 设置统计日期。
     * @param statDate 日期。
     */
    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    /**
     * 活跃用户数。
     * @return 数量。
     */
    public Long getActiveCount() {
        return activeCount;
    }

    /**
     * 设置活跃用户数。
     * @param activeCount 数量。
     */
    public void setActiveCount(Long activeCount) {
        this.activeCount = activeCount;
    }
}
