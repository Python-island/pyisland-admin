package com.pyisland.server.controller;

import com.pyisland.server.service.OssService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传控制器。
 */
@RestController
@RequestMapping("/v1/upload")
public class UploadController {

    private final OssService ossService;

    /**
     * 构造上传控制器。
     * @param ossService OSS 服务。
     */
    public UploadController(OssService ossService) {
        this.ossService = ossService;
    }

    /**
     * 上传头像文件。
     * @param file 头像文件。
     * @return 上传结果。
     */
    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
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
            String url = ossService.upload(file, "avatars");
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
