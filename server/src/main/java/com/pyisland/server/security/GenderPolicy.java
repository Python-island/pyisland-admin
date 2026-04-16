package com.pyisland.server.security;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * 性别与生日字段的校验与归一化。
 */
public final class GenderPolicy {

    /**
     * 默认性别标识：不愿透露。
     */
    public static final String DEFAULT = "undisclosed";

    /**
     * 允许的性别枚举。
     */
    public static final Set<String> ALLOWED = Set.of("male", "female", "custom", "undisclosed");

    private GenderPolicy() {
    }

    /**
     * 归一化性别标识。
     * @param gender 原始值。
     * @return 合法的性别标识。
     */
    public static String normalize(String gender) {
        if (gender == null || gender.isBlank()) {
            return DEFAULT;
        }
        String lower = gender.trim().toLowerCase();
        return ALLOWED.contains(lower) ? lower : DEFAULT;
    }

    /**
     * 归一化自定义性别描述：仅在 gender=custom 时保留。
     * @param gender 归一化后的性别。
     * @param genderCustom 自定义描述。
     * @return 合法的自定义描述或 null。
     */
    public static String normalizeCustom(String gender, String genderCustom) {
        if (!"custom".equals(gender)) {
            return null;
        }
        if (genderCustom == null || genderCustom.isBlank()) {
            return null;
        }
        String trimmed = genderCustom.trim();
        if (trimmed.length() > 64) {
            trimmed = trimmed.substring(0, 64);
        }
        return trimmed;
    }

    /**
     * 解析生日字符串为 LocalDate。
     * @param birthday 日期字符串（ISO 格式 yyyy-MM-dd）。
     * @return 解析结果；空或非法时返回 null。
     */
    public static LocalDate parseBirthday(String birthday) {
        if (birthday == null || birthday.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(birthday.trim());
            if (date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.of(1900, 1, 1))) {
                return null;
            }
            return date;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
