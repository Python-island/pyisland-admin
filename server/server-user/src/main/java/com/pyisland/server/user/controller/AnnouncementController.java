package com.pyisland.server.user.controller;

import com.pyisland.server.user.entity.AnnouncementConfig;
import com.pyisland.server.user.service.AnnouncementConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公告配置与下发接口。
 */
@RestController
@RequestMapping("/v1")
public class AnnouncementController {

    private final AnnouncementConfigService announcementConfigService;

    public AnnouncementController(AnnouncementConfigService announcementConfigService) {
        this.announcementConfigService = announcementConfigService;
    }

    @GetMapping("/admin/announcement")
    public ResponseEntity<?> getAdminConfig() {
        AnnouncementConfig config = announcementConfigService.getAdminConfig();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", toAdminPayload(config)
        ));
    }

    @PutMapping("/admin/announcement")
    public ResponseEntity<?> updateAdminConfig(@RequestBody UpdateAnnouncementRequest request,
                                                Authentication authentication) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "请求体不能为空"));
        }
        try {
            String updatedBy = authentication == null ? "" : authentication.getName();
            AnnouncementConfig updated = announcementConfigService.save(
                    request.title(),
                    request.content(),
                    request.enabled(),
                    request.startAt(),
                    request.endAt(),
                    updatedBy
            );
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "公告配置已更新",
                    "data", toAdminPayload(updated)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @GetMapping("/announcement/current")
    public ResponseEntity<?> getCurrentAnnouncement() {
        Map<String, Object> data = announcementConfigService.getPublicAnnouncement();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("message", "success");
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> toAdminPayload(AnnouncementConfig config) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", config.getId());
        data.put("title", config.getTitle() == null ? "" : config.getTitle());
        data.put("content", config.getContent() == null ? "" : config.getContent());
        data.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        data.put("startAt", config.getStartAt() == null ? null : config.getStartAt().toString());
        data.put("endAt", config.getEndAt() == null ? null : config.getEndAt().toString());
        data.put("updatedBy", config.getUpdatedBy() == null ? "" : config.getUpdatedBy());
        data.put("updatedAt", config.getUpdatedAt() == null ? null : config.getUpdatedAt().toString());
        return data;
    }

    public record UpdateAnnouncementRequest(String title,
                                            String content,
                                            Boolean enabled,
                                            String startAt,
                                            String endAt) {
    }
}
