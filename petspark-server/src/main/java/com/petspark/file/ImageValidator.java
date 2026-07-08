package com.petspark.file;

import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImageValidator {

    static final long MAX_SIZE = 5L * 1024 * 1024;

    public ImageMetadata validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_CONTENT_001);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_001);
        }
        String originalName = file.getOriginalFilename();
        String extension = extension(originalName);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (java.io.IOException ex) {
            throw new BusinessException(ErrorCode.FILE_CONTENT_001);
        }
        DetectedType detected = detect(content);
        String declared = file.getContentType();
        if (!detected.extension().equals(normalizeExtension(extension))
                || declared == null || !detected.mediaType().equals(declared.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.FILE_CONTENT_001);
        }
        Integer width = null;
        Integer height = null;
        if (!"webp".equals(detected.extension())) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
                if (image == null) {
                    throw new BusinessException(ErrorCode.FILE_CONTENT_001);
                }
                width = image.getWidth();
                height = image.getHeight();
            } catch (java.io.IOException ex) {
                throw new BusinessException(ErrorCode.FILE_CONTENT_001);
            }
        }
        return new ImageMetadata(detected.extension(), detected.mediaType(), content.length,
                sha256(content), width, height, content);
    }

    private String extension(String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")) {
            throw new BusinessException(ErrorCode.FILE_TYPE_001);
        }
        // 拒绝控制字符，防止 CRLF 注入 Content-Disposition 等响应头。
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                throw new BusinessException(ErrorCode.FILE_TYPE_001);
            }
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            throw new BusinessException(ErrorCode.FILE_TYPE_001);
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extension.equals("png") && !extension.equals("jpg")
                && !extension.equals("jpeg") && !extension.equals("webp")) {
            throw new BusinessException(ErrorCode.FILE_TYPE_001);
        }
        return extension;
    }

    private String normalizeExtension(String extension) {
        return "jpeg".equals(extension) ? "jpg" : extension;
    }

    private DetectedType detect(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return new DetectedType("png", "image/png");
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return new DetectedType("jpg", "image/jpeg");
        }
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) {
            // RIFF 容器：第 4..7 字节为小端 uint32 的 payload 长度，应不超过实际可用
            // 字节（总长减 8 字节 RIFF 头）。声明长度大于可用字节说明文件被截断或
            // 头部被伪造膨胀（如仅写 RIFF/WEBP 魔术字节的假 webp），予以拒绝；
            // 声明长度小于等于可用字节的标准 webp（含带尾部元数据的合法文件）放行。
            long payloadSize = (bytes[4] & 0xffL)
                    | ((bytes[5] & 0xffL) << 8)
                    | ((bytes[6] & 0xffL) << 16)
                    | ((bytes[7] & 0xffL) << 24);
            if (payloadSize > bytes.length - 8L) {
                throw new BusinessException(ErrorCode.FILE_CONTENT_001);
            }
            return new DetectedType("webp", "image/webp");
        }
        throw new BusinessException(ErrorCode.FILE_CONTENT_001);
    }

    private boolean ascii(byte[] bytes, int offset, String value) {
        for (int i = 0; i < value.length(); i++) {
            if (bytes[offset + i] != (byte) value.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record DetectedType(String extension, String mediaType) {}
}
