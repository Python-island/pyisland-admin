package com.pyisland.server.user.controller;

import com.pyisland.server.user.service.WallpaperTagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户侧壁纸标签接口（搜索自动补全）。
 */
@RestController
@RequestMapping("/v1/user/tags")
public class WallpaperTagController {

    private final WallpaperTagService tagService;

    public WallpaperTagController(WallpaperTagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(value = "keyword", required = false) String keyword,
                                    @RequestParam(value = "limit", defaultValue = "15") int limit) {
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", tagService.search(keyword, limit)
        ));
    }
}
