package com.pyisland.server.user.policy;

import java.util.regex.Pattern;

/**
 * 统一的用户名合法性校验。
 * 允许中英文、数字、下划线、点、短横线。
 */
public final class UsernamePolicy {

    /**
     * 最小长度（按字符计）。
     */
    public static final int MIN_LENGTH = 2;

    /**
     * 最大长度（按字符计）。
     */
    public static final int MAX_LENGTH = 32;

    /**
     * 允许的字符集：Unicode 字母、数字、下划线、点、短横线。
     */
    private static final Pattern CHARSET_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_.\\-]+$");

    private UsernamePolicy() {
    }

    /**
     * 校验用户名是否合法。
     * @param username 用户名。
     * @return 不合法时返回错误提示，合法时返回 null。
     */
    public static String validate(String username) {
        if (username == null || username.isBlank()) {
            return "用户名不能为空";
        }
        int length = username.codePointCount(0, username.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return "用户名长度需为 " + MIN_LENGTH + "-" + MAX_LENGTH + " 位";
        }
        if (!CHARSET_PATTERN.matcher(username).matches()) {
            return "用户名仅允许中英文、数字、下划线、点或短横线";
        }
        return null;
    }
}
