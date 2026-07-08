package com.petspark.file;

public interface FileStorage {
    void store(String objectKey, byte[] content);

    byte[] read(String objectKey);

    void delete(String objectKey);
}
