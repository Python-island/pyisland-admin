package com.pyisland.server.user.policy;

/**
 * 统一的密码强度策略。
 */
public final class PasswordPolicy {

    /**
     * 最小长度。
     */
    public static final int MIN_LENGTH = 8;

    /**
     * 最大长度。
     */
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /**
     * 校验密码是否满足强度要求。
     * @param password 明文密码。
     * @return 不满足时返回错误提示，满足时返回 null。
     */
    public static String validate(String password) {
        if (password == null || password.isBlank()) {
            return "密码不能为空";
        }
        if (password.length() < MIN_LENGTH) {
            return "密码长度不能少于 " + MIN_LENGTH + " 位";
        }
        if (password.length() > MAX_LENGTH) {
            return "密码长度不能超过 " + MAX_LENGTH + " 位";
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isWhitespace(c)) {
                return "密码不能包含空白字符";
            }
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            return "密码必须同时包含字母和数字";
        }
        return null;
    }
}
