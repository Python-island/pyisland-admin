package com.pyisland.server.user.controller;

import com.pyisland.server.user.service.WallpaperMarketService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 用户侧壁纸市场接口。
 */
@RestController
@RequestMapping("/v1/user/wallpapers")
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class WallpaperUserController {

    private final WallpaperMarketService wallpaperMarketService;

    public WallpaperUserController(WallpaperMarketService wallpaperMarketService) {
        this.wallpaperMarketService = wallpaperMarketService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("title") String title,
                                    @RequestParam(value = "description", required = false) String description,
                                    @RequestParam(value = "type", required = false) String type,
                                    @RequestParam(value = "tags", required = false) String tags,
                                    @RequestParam("copyrightDeclared") boolean copyrightDeclared,
                                    @RequestParam(value = "copyrightInfo", required = false) String copyrightInfo,
                                    @RequestParam(value = "width", required = false) Integer width,
                                    @RequestParam(value = "height", required = false) Integer height,
                                    @RequestParam(value = "durationMs", required = false) Long durationMs,
                                    @RequestParam(value = "frameRate", required = false) BigDecimal frameRate,
                                    @RequestParam("original") MultipartFile original,
                                    @RequestParam("thumb320") MultipartFile thumb320,
                                    @RequestParam("thumb720") MultipartFile thumb720,
                                    @RequestParam("thumb1280") MultipartFile thumb1280,
                                    Authentication authentication) {
        try {
            Long id = wallpaperMarketService.create(authentication.getName(),
                    title,
                    description,
                    type,
                    tags,
                    copyrightDeclared,
                    copyrightInfo,
                    original,
                    thumb320,
                    thumb720,
                    thumb1280,
                    width,
                    height,
                    durationMs,
                    frameRate);
            return ResponseEntity.ok(Map.of("code", 200, "message", "上传成功，等待审核", "data", Map.of("id", id)));
        } catch (IllegalArgumentException | IOException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> list(@RequestParam(value = "keyword", required = false) String keyword,
                                  @RequestParam(value = "type", required = false) String type,
                                  @RequestParam(value = "sort", required = false) String sort,
                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                  @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        var items = wallpaperMarketService.listPublished(keyword, type, sort, page, pageSize);
        long total = wallpaperMarketService.countPublished(keyword, type);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", Map.of(
                        "items", items,
                        "total", total
                )
        ));
    }

    @GetMapping("/mine")
    public ResponseEntity<?> listMine(@RequestParam(value = "keyword", required = false) String keyword,
                                      @RequestParam(value = "type", required = false) String type,
                                      @RequestParam(value = "sort", required = false) String sort,
                                      @RequestParam(value = "page", defaultValue = "1") int page,
                                      @RequestParam(value = "pageSize", defaultValue = "50") int pageSize,
                                      Authentication authentication) {
        var items = wallpaperMarketService.listOwn(authentication.getName(), keyword, type, sort, page, pageSize);
        long total = wallpaperMarketService.countOwn(authentication.getName(), keyword, type);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", Map.of(
                        "items", items,
                        "total", total
                )
        ));
    }

    @GetMapping("/detail")
    public ResponseEntity<?> detail(@RequestParam("id") Long id) {
        Map<String, Object> data = wallpaperMarketService.detail(id);
        if (data == null || data.isEmpty()) {
            return ResponseEntity.ok(Map.of("code", 404, "message", "壁纸不存在"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "success", "data", data));
    }

    @PutMapping("/metadata")
    public ResponseEntity<?> updateMetadata(@RequestBody UpdateMetadataRequest request, Authentication authentication) {
        try {
            boolean ok = wallpaperMarketService.updateOwnerMetadata(request.id(),
                    authentication.getName(),
                    request.title(),
                    request.description(),
                    request.type(),
                    request.tags(),
                    request.copyrightInfo());
            if (!ok) {
                return ResponseEntity.ok(Map.of("code", 404, "message", "壁纸不存在或无权限"));
            }
            return ResponseEntity.ok(Map.of("code", 200, "message", "更新成功，已重新进入待审核"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @PutMapping("/replace-source")
    public ResponseEntity<?> replaceSource(@RequestParam("id") Long id,
                                           @RequestParam(value = "reason", required = false) String reason,
                                           @RequestParam(value = "width", required = false) Integer width,
                                           @RequestParam(value = "height", required = false) Integer height,
                                           @RequestParam(value = "durationMs", required = false) Long durationMs,
                                           @RequestParam(value = "frameRate", required = false) BigDecimal frameRate,
                                           @RequestParam("original") MultipartFile original,
                                           @RequestParam("thumb320") MultipartFile thumb320,
                                           @RequestParam("thumb720") MultipartFile thumb720,
                                           @RequestParam("thumb1280") MultipartFile thumb1280,
                                           Authentication authentication) {
        try {
            boolean ok = wallpaperMarketService.replaceOwnerSource(id,
                    authentication.getName(),
                    original,
                    thumb320,
                    thumb720,
                    thumb1280,
                    width,
                    height,
                    durationMs,
                    frameRate,
                    reason);
            if (!ok) {
                return ResponseEntity.ok(Map.of("code", 404, "message", "壁纸不存在或无权限"));
            }
            return ResponseEntity.ok(Map.of("code", 200, "message", "替换成功，已进入待审核"));
        } catch (IllegalArgumentException | IOException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> delete(@RequestParam("id") Long id, Authentication authentication) {
        boolean ok = wallpaperMarketService.deleteOwnerWallpaper(id, authentication.getName());
        if (!ok) {
            return ResponseEntity.ok(Map.of("code", 404, "message", "壁纸不存在或无权限"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    @PostMapping("/apply")
    public ResponseEntity<?> apply(@RequestBody IdRequest request,
                                   Authentication authentication,
                                   HttpServletRequest httpServletRequest) {
        boolean ok = wallpaperMarketService.apply(request.id(),
                authentication.getName(),
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader("User-Agent"));
        if (!ok) {
            return ResponseEntity.ok(Map.of("code", 429, "message", "操作过于频繁或资源不可用"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "success"));
    }

    @PostMapping("/rate")
    public ResponseEntity<?> rate(@RequestBody RateRequest request, Authentication authentication) {
        boolean ok = wallpaperMarketService.rate(request.id(), authentication.getName(), request.score());
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "评分参数非法"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "评分成功"));
    }

    @PostMapping("/report")
    public ResponseEntity<?> report(@RequestBody ReportRequest request, Authentication authentication) {
        boolean ok = wallpaperMarketService.report(request.id(),
                authentication.getName(),
                request.reasonType(),
                request.reasonDetail());
        if (!ok) {
            return ResponseEntity.ok(Map.of("code", 429, "message", "举报过于频繁或参数无效"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "举报已提交，等待审核"));
    }

    public record IdRequest(Long id) {
    }

    public record UpdateMetadataRequest(Long id,
                                        String title,
                                        String description,
                                        String type,
                                        String tags,
                                        String copyrightInfo) {
    }

    public record RateRequest(Long id, int score) {
    }

    public record ReportRequest(Long id, String reasonType, String reasonDetail) {
    }
}
