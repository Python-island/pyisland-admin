package com.pyisland.server.user.controller;

import com.pyisland.server.user.service.WallpaperMarketService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端壁纸市场接口。
 */
@RestController
@RequestMapping("/v1/admin/wallpapers")
public class WallpaperAdminController {

    private final WallpaperMarketService wallpaperMarketService;

    public WallpaperAdminController(WallpaperMarketService wallpaperMarketService) {
        this.wallpaperMarketService = wallpaperMarketService;
    }

    @GetMapping("/list")
    public ResponseEntity<?> list(@RequestParam(value = "keyword", required = false) String keyword,
                                  @RequestParam(value = "type", required = false) String type,
                                  @RequestParam(value = "status", required = false) String status,
                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                  @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", wallpaperMarketService.listAdmin(keyword, type, status, page, pageSize)
        ));
    }

    @PutMapping("/metadata")
    public ResponseEntity<?> metadata(@RequestBody AdminMetadataRequest request) {
        try {
            boolean ok = wallpaperMarketService.adminUpdateMetadata(request.id(),
                    request.title(),
                    request.description(),
                    request.type(),
                    request.tags(),
                    request.status());
            if (!ok) {
                return ResponseEntity.ok(Map.of("code", 404, "message", "壁纸不存在"));
            }
            return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @PutMapping("/review")
    public ResponseEntity<?> review(@RequestBody ReviewRequest request, Authentication authentication) {
        boolean ok = wallpaperMarketService.adminReview(request.id(), authentication.getName(), request.action(), request.reason());
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "审核参数非法或资源不存在"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "审核操作完成"));
    }

    @GetMapping("/reports")
    public ResponseEntity<?> reports(@RequestParam(value = "status", required = false) String status,
                                     @RequestParam(value = "page", defaultValue = "1") int page,
                                     @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", wallpaperMarketService.listReports(status, page, pageSize)
        ));
    }

    @PutMapping("/reports/resolve")
    public ResponseEntity<?> resolveReport(@RequestBody ResolveReportRequest request, Authentication authentication) {
        boolean ok = wallpaperMarketService.resolveReport(request.id(),
                authentication.getName(),
                request.status(),
                request.resolutionNote());
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "处理失败"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "处理成功"));
    }

    @GetMapping("/ratings")
    public ResponseEntity<?> ratings(@RequestParam("wallpaperId") Long wallpaperId,
                                     @RequestParam(value = "page", defaultValue = "1") int page,
                                     @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", wallpaperMarketService.listRatings(wallpaperId, page, pageSize)
        ));
    }

    @DeleteMapping("/ratings")
    public ResponseEntity<?> deleteRating(@RequestParam("id") Long id,
                                          @RequestParam("wallpaperId") Long wallpaperId) {
        boolean ok = wallpaperMarketService.adminDeleteRating(id, wallpaperId);
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "删除失败"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteWallpaper(@RequestParam("id") Long id) {
        boolean ok = wallpaperMarketService.adminDeleteWallpaper(id);
        if (!ok) {
            return ResponseEntity.ok(Map.of("code", 404, "message", "壁纸不存在或已删除"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    public record AdminMetadataRequest(Long id,
                                       String title,
                                       String description,
                                       String type,
                                       String tags,
                                       String status) {
    }

    public record ReviewRequest(Long id, String action, String reason) {
    }

    public record ResolveReportRequest(Long id, String status, String resolutionNote) {
    }
}
