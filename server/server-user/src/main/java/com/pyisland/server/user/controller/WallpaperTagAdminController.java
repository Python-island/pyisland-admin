package com.pyisland.server.user.controller;

import com.pyisland.server.user.service.WallpaperTagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员侧壁纸标签管理接口。
 */
@RestController
@RequestMapping("/v1/admin/tags")
public class WallpaperTagAdminController {

    private final WallpaperTagService tagService;

    public WallpaperTagAdminController(WallpaperTagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/list")
    public ResponseEntity<?> list(@RequestParam(value = "keyword", required = false) String keyword,
                                  @RequestParam(value = "enabled", required = false) Integer enabled,
                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                  @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", Map.of(
                        "items", tagService.listAdmin(keyword, enabled, page, pageSize),
                        "total", tagService.countAdmin(keyword, enabled)
                )
        ));
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateName(@RequestBody UpdateTagRequest request) {
        if (request.id() == null || request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "参数无效"));
        }
        boolean ok = tagService.updateName(request.id(), request.name());
        if (!ok) {
            return ResponseEntity.ok(Map.of("code", 409, "message", "标签不存在或与已有标签冲突"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功"));
    }

    @PutMapping("/enable")
    public ResponseEntity<?> setEnabled(@RequestBody SetEnabledRequest request) {
        if (request.id() == null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "参数无效"));
        }
        boolean ok = tagService.setEnabled(request.id(), Boolean.TRUE.equals(request.enabled()));
        if (!ok) {
            return ResponseEntity.ok(Map.of("code", 404, "message", "标签不存在"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功"));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteTag(@RequestParam("id") Long id) {
        boolean ok = tagService.deleteTag(id);
        if (!ok) {
            return ResponseEntity.ok(Map.of("code", 404, "message", "标签不存在"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    public record UpdateTagRequest(Long id, String name) {
    }

    public record SetEnabledRequest(Long id, Boolean enabled) {
    }
}
