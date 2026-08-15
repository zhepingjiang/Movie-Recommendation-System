package com.movierec.backend.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * {@link StorageService} backed by MinIO (self-hosted, S3-API-compatible). On startup, ensures
 * the configured bucket exists and is publicly readable, so avatar (and future) URLs can be
 * loaded directly by the browser without proxying through this backend.
 */
@Service
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final String bucket;
    private final String publicUrl;

    public MinioStorageService(
            MinioClient minioClient,
            @Value("${minio.bucket}") String bucket,
            @Value("${minio.public-url}") String publicUrl) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    @PostConstruct
    void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder().bucket(bucket).config(publicReadPolicy(bucket)).build());
        } catch (Exception e) {
            throw new StorageException("Failed to initialize MinIO bucket '" + bucket + "'", e);
        }
    }

    @Override
    public String upload(String key, InputStream data, long size, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .stream(data, size, -1)
                            .contentType(contentType)
                            .build());
            return publicUrl + "/" + bucket + "/" + key;
        } catch (Exception e) {
            throw new StorageException("Failed to upload object '" + key + "'", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("Failed to delete object '" + key + "'", e);
        }
    }

    private static String publicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """
                .formatted(bucket);
    }
}
