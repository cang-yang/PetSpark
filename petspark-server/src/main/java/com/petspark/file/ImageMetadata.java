package com.petspark.file;

record ImageMetadata(
        String extension,
        String mediaType,
        long sizeBytes,
        String sha256,
        Integer width,
        Integer height,
        byte[] content
) {}
