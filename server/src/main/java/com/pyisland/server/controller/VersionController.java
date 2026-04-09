package com.pyisland.server.controller;

import com.pyisland.server.entity.AppVersion;
import com.pyisland.server.service.AppVersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/version")
public class VersionController {

    private final AppVersionService appVersionService;

    public VersionController(AppVersionService appVersionService) {
        this.appVersionService = appVersionService;
    }

    @GetMapping
    public ResponseEntity<?> getVersion(@RequestParam(defaultValue = "pyisland") String appName) {
        AppVersion version = appVersionService.getVersion(appName);
        if (version == null) {
            return ResponseEntity.ok(Map.of(
                    "code", 404,
                    "message", "版本信息不存在"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", version
        ));
    }

    @PutMapping
    public ResponseEntity<?> updateVersion(@RequestBody UpdateVersionRequest request) {
        if (request.version() == null || request.version().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "版本号不能为空"
            ));
        }
        String appName = request.appName() == null || request.appName().isBlank()
                ? "pyisland" : request.appName();
        AppVersion updated = appVersionService.updateVersion(appName, request.version(), request.description());
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "版本号更新成功",
                "data", updated
        ));
    }

    public record UpdateVersionRequest(String appName, String version, String description) {
    }
}
