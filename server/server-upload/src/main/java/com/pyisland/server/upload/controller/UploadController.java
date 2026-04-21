package com.pyisland.server.upload.controller;

import com.pyisland.server.common.util.ClientIpUtil;
import com.pyisland.server.upload.service.FeedbackR2StorageService;
import com.pyisland.server.upload.service.OssService;
import com.pyisland.server.upload.service.R2StorageService;
import com.pyisland.server.upload.service.UploadRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;

/**
 * 文件上传控制器。
 */
@RestController
@RequestMapping("/v1/upload")
public class UploadController {

    private final OssService ossService;
    private final R2StorageService r2StorageService;
    private final FeedbackR2StorageService feedbackR2StorageService;
    private final UploadRateLimiter uploadRateLimiter;

    /**
     * 构造上传控制器。
     * @param ossService OSS 服务。
     * @param r2StorageService R2 服务。
     * @param uploadRateLimiter 上传限流器。
     */
    public UploadController(OssService ossService,
                            R2StorageService r2StorageService,
                            FeedbackR2StorageService feedbackR2StorageService,
                            UploadRateLimiter uploadRateLimiter) {
        this.ossService = ossService;
        this.r2StorageService = r2StorageService;
        this.feedbackR2StorageService = feedbackR2StorageService;
        this.uploadRateLimiter = uploadRateLimiter;
    }

    /**
     * 上传管理员头像文件（使用 OSS）。
     * @param file 头像文件。
     * @return 上传结果。
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin-avatar")
    public ResponseEntity<?> uploadAdminAvatar(@RequestParam("file") MultipartFile file) {
        return doUpload(file, true);
    }

    /**
     * 上传普通用户头像文件（使用 Cloudflare R2）。
     * @param file 头像文件。
     * @param authentication 当前认证信息。
     * @return 上传结果。
     */
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PostMapping("/user-avatar")
    public ResponseEntity<?> uploadUserAvatar(@RequestParam("file") MultipartFile file,
                                              Authentication authentication,
                                              HttpServletRequest request) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return ResponseEntity.status(401).body(Map.of(
                    "code", 401,
                    "message", "未登录"
            ));
        }
        String ip = ClientIpUtil.resolve(request);
        String account = authentication.getName().trim().toLowerCase();
        UploadRateLimiter.Result limiterResult = uploadRateLimiter.recordUserAvatarUploadAttempt(ip, account);
        if (limiterResult.blocked()) {
            return ResponseEntity.status(429).body(Map.of(
                    "code", 429,
                    "message", "头像上传过于频繁，请稍后再试",
                    "data", Map.of(
                            "scope", limiterResult.scope(),
                            "retryAfterSeconds", limiterResult.retryAfterSeconds(),
                            "limit", UploadRateLimiter.USER_AVATAR_HOURLY_MAX_ATTEMPTS,
                            "windowSeconds", UploadRateLimiter.USER_AVATAR_WINDOW_MS / 1000
                    )
            ));
        }
        return doUpload(file, false);
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PostMapping("/feedback-log")
    public ResponseEntity<?> uploadFeedbackLog(@RequestParam("file") MultipartFile file,
                                               Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return ResponseEntity.status(401).body(Map.of(
                    "code", 401,
                    "message", "未登录"
            ));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "文件不能为空"
            ));
        }
        if (file.getSize() > 50L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "日志文件不能超过 50MB"
            ));
        }
        String originalFilename = file.getOriginalFilename();
        String normalizedFilename = originalFilename == null ? "" : originalFilename.trim().toLowerCase(Locale.ROOT);
        if (!normalizedFilename.endsWith(".log")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "仅支持上传 .log 日志文件"
            ));
        }
        try {
            String url = feedbackR2StorageService.upload(file, "feedback-logs");
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "上传成功",
                    "data", url
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "code", 500,
                    "message", "上传失败: " + e.getMessage()
            ));
        }
    }

    private ResponseEntity<?> doUpload(MultipartFile file, boolean adminAvatar) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "文件不能为空"
            ));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "头像文件不能超过 5MB"
            ));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "只能上传图片文件"
            ));
        }
        try {
            String folder = adminAvatar ? "admin-avatars" : "user-avatars";
            String url = adminAvatar
                    ? ossService.upload(file, folder)
                    : r2StorageService.upload(file, folder);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "上传成功",
                    "data", url
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "code", 500,
                    "message", "上传失败: " + e.getMessage()
            ));
        }
    }
}
