package com.pyisland.server.version.controller;

import com.pyisland.server.version.entity.AppVersion;
import com.pyisland.server.version.service.AppVersionService;
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

/**
 * 应用版本控制器。
 */
@RestController
@RequestMapping("/v1/version")
public class VersionController {

    private final AppVersionService appVersionService;

    /**
     * 构造版本控制器。
     * @param appVersionService 版本服务。
     */
    public VersionController(AppVersionService appVersionService) {
        this.appVersionService = appVersionService;
    }

    /**
     * 查询全部版本。
     * @return 版本列表。
     */
    @GetMapping("/list")
    public ResponseEntity<?> listVersions() {
        var list = appVersionService.listAll();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", list
        ));
    }

    /**
     * 查询指定应用版本。
     * @param appName 应用名称。
     * @return 版本信息。
     */
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

    /**
     * 更新应用版本信息。
     * @param request 更新请求。
     * @return 更新结果。
     */
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

        AppVersion existing = appVersionService.getVersion(appName);
        if (existing == null) {
            return ResponseEntity.ok(Map.of(
                    "code", 404,
                    "message", "版本信息不存在"
            ));
        }

        String newDesc = request.description() == null ? "" : request.description();
        String oldDesc = existing.getDescription() == null ? "" : existing.getDescription();
        String newUrl = request.downloadUrl() == null ? "" : request.downloadUrl();
        String oldUrl = existing.getDownloadUrl() == null ? "" : existing.getDownloadUrl();
        if (existing.getVersion().equals(request.version()) && oldDesc.equals(newDesc) && oldUrl.equals(newUrl)) {
            return ResponseEntity.ok(Map.of(
                    "code", 400,
                    "message", "没有修改内容，无需更新"
            ));
        }

        AppVersion updated = appVersionService.updateVersion(appName, request.version(), request.description(), request.downloadUrl());
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "版本号更新成功",
                "data", updated
        ));
    }

    /**
     * 删除应用版本。
     * @param appName 应用名称。
     * @return 删除结果。
     */
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

    /**
     * 创建应用版本。
     * @param request 创建请求。
     * @return 创建结果。
     */
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
        AppVersion created = appVersionService.createVersion(request.appName(), request.version(), request.description(), request.downloadUrl());
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

    /**
     * 统计指定版本更新次数。
     * @param request 统计请求。
     * @return 统计结果。
     */
    @PostMapping("/update-count")
    public ResponseEntity<?> incrementUpdateCount(@RequestBody UpdateCountRequest request) {
        if (request.version() == null || request.version().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "版本号不能为空"
            ));
        }

        String appName = request.appName() == null || request.appName().isBlank()
                ? "pyisland" : request.appName();
        boolean updated = appVersionService.incrementUpdateCount(appName, request.version());
        if (!updated) {
            return ResponseEntity.ok(Map.of(
                    "code", 404,
                    "message", "未找到对应版本"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "更新次数统计成功"
        ));
    }

    /**
     * 更新版本请求体。
     * @param appName 应用名称。
     * @param version 版本号。
     * @param description 版本描述。
     * @param downloadUrl 下载地址。
     */
    public record UpdateVersionRequest(String appName, String version, String description, String downloadUrl) {
    }

    /**
     * 创建版本请求体。
     * @param appName 应用名称。
     * @param version 版本号。
     * @param description 版本描述。
     * @param downloadUrl 下载地址。
     */
    public record CreateVersionRequest(String appName, String version, String description, String downloadUrl) {
    }

    /**
     * 更新统计请求体。
     * @param appName 应用名称。
     * @param version 版本号。
     */
    public record UpdateCountRequest(String appName, String version) {
    }
}
