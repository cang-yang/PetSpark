package com.petspark.file;

import java.time.Instant;

record FileObject(
        String id,
        String objectKey,
        String originalName,
        String mediaType,
        String extension,
        long sizeBytes,
        String sha256,
        Integer width,
        Integer height,
        String status,
        String ownerId,
        String businessType,
        Instant confirmedAt
) {}
