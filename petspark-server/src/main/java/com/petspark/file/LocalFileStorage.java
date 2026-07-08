package com.petspark.file;

import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${petspark.file.storage-root:./data/files}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public void store(String objectKey, byte[] content) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(root);
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store file", ex);
        }
    }

    @Override
    public byte[] read(String objectKey) {
        try {
            return Files.readAllBytes(resolve(objectKey));
        } catch (java.nio.file.NoSuchFileException ex) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND_001);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read file", ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not delete file", ex);
        }
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("/") || objectKey.contains("\\")) {
            throw new BusinessException(ErrorCode.FILE_TYPE_001);
        }
        Path target = root.resolve(objectKey).normalize();
        if (!target.getParent().equals(root)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_001);
        }
        return target;
    }
}
