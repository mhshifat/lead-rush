package com.leadrush.common.adapter;

/** File storage adapter (default impl: Cloudinary). */
public interface FileStorageAdapter {

    UploadResult upload(UploadRequest request);

    void delete(String fileId);

    String getUrl(String fileId);

    record UploadRequest(
        byte[] data,
        String fileName,
        String folder,
        String contentType
    ) {}

    record UploadResult(
        String fileId,
        String url,
        long size,
        String contentType
    ) {}
}
