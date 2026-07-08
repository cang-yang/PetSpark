package com.petspark.file;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.security.AuthenticatedUser;
import com.petspark.common.security.RequirePermission;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("file:upload")
    public ApiResponse<FileUploadResponse> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam String businessType,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(fileService.uploadImage(file, businessType, user.getId()));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<FileUploadResponse> confirm(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(fileService.confirm(id, user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        StoredFile file = fileService.read(id, user == null ? null : user.getId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.originalName()).build().toString())
                .body(file.content());
    }
}
