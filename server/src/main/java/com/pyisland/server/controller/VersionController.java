package com.pyisland.server.controller;

import com.pyisland.server.entity.AppVersion;
import com.pyisland.server.service.AppVersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/version")
public class VersionController {

    private final AppVersionService appVersionService;

    public VersionController(AppVersionService appVersionService) {
        this.appVersionService = appVersionService;
    }

    @GetMapping("/list")
    public ResponseEntity<?> listVersions() {
        var list = appVersionService.listAll();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", list
        ));
    }

    @GetMapping
    public ResponseEntity<?> getVersion(@RequestParam String appName) {
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

    @DeleteMapping
    public ResponseEntity<?> deleteVersion(@RequestParam String appName) {
        boolean deleted = appVersionService.deleteVersion(appName);
        if (!deleted) {
            return ResponseEntity.ok(Map.of(
                    "code", 404,
                    "message", "版本信息不存在"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "版本信息删除成功"
        ));
    }

    @PostMapping
    public ResponseEntity<?> createVersion(@RequestBody CreateVersionRequest request) {
        if (request.appName() == null || request.appName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "应用名称不能为空"
            ));
        }
        if (request.version() == null || request.version().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "版本号不能为空"
            ));
        }
        AppVersion created = appVersionService.createVersion(request.appName(), request.version(), request.description());
        if (created == null) {
            return ResponseEntity.ok(Map.of(
                    "code", 409,
                    "message", "该应用已存在"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "应用创建成功",
                "data", created
        ));
    }

    public record UpdateVersionRequest(String appName, String version, String description) {
    }

    public record CreateVersionRequest(String appName, String version, String description) {
    }
}
