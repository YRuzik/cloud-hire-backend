package com.app.controller.files;

import com.app.config.MinioConfig;
import com.app.entity.FileMetadata;
import com.app.entity.User;
import com.app.middleware.AuthMiddleware;
import com.app.repository.FileMetadataRepository;
import com.app.util.SessionManger;
import io.minio.*;
import io.minio.messages.Item;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;

@RestController
public class FileController {
    private final MinioConfig minioConfig;
    private final FileMetadataRepository fileMetadataRepository;
    private final SessionManger sessionManger;
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public FileController(MinioConfig minioConfig,
                          FileMetadataRepository fileMetadataRepository,
                          SessionManger sessionManager,
                          RedisTemplate<String, String> redisTemplate
    ) {
        this.minioConfig = minioConfig;
        this.fileMetadataRepository = fileMetadataRepository;
        this.sessionManger = sessionManager;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping(value = "/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFile(@RequestPart("file") MultipartFile file, HttpServletRequest request) {
        try {
            if (!AuthMiddleware.sessionExists(request, redisTemplate)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User session not found");
            }

            User user = sessionManger.getUserFromSession(request);

            if (file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty");
            }

            String filename = FilenameUtils.removeExtension(file.getOriginalFilename());
            String contentType = file.getContentType();
            long size = file.getSize();

            String userBucketName = minioConfig.getBaseBucketName() + "-" + user.getUsername();

            MinioClient minioClient = MinioClient.builder()
                    .endpoint(minioConfig.getEndpoint())
                    .credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey())
                    .build();

            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(userBucketName).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(userBucketName).build());
            }
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(userBucketName).build()
            );
            for (Result<Item> result : objects) {
                Item item = result.get();
                if (item.objectName().equals(filename)) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File with the same name already exists");
                }
            }

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(userBucketName)
                            .object(filename)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );

            FileMetadata fileMetadata = new FileMetadata();
            fileMetadata.setName(filename);
            fileMetadata.setSize(size);
            fileMetadata.setMimeType(contentType);
            fileMetadata.setLocation(minioConfig.getEndpoint() + "/" + userBucketName + "/" + filename);
            fileMetadata.setUser(user);
            LocalDateTime currentTime = LocalDateTime.now();
            fileMetadata.setDateCreated(currentTime);
            fileMetadata.setDateModified(currentTime);
            fileMetadataRepository.save(fileMetadata);

            return ResponseEntity.ok("File uploaded successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File upload failed: " + e.getMessage());
        }
    }

    @DeleteMapping(value = "/delete-file/{filename}")
    @Transactional
    public ResponseEntity<String> deleteFile(@PathVariable("filename") String filename, HttpServletRequest request) {
        try {
            if (!AuthMiddleware.sessionExists(request, redisTemplate)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User session not found");
            }

            User user = sessionManger.getUserFromSession(request);
            String userBucketName = minioConfig.getBaseBucketName() + "-" + user.getUsername();

            MinioClient minioClient = MinioClient.builder()
                    .endpoint(minioConfig.getEndpoint())
                    .credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey())
                    .build();

            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(userBucketName).build());
            if (!bucketExists) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Bucket not found");
            }


            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(userBucketName)
                    .object(filename)
                    .build()
            );

            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(userBucketName).build()
            );
            for (Result<Item> result : objects) {
                Item item = result.get();
                if (!item.objectName().equals(filename)) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
                }
            }

            boolean hasFiles = objects.iterator().hasNext();

            if (!hasFiles) {
                minioClient.removeBucket(
                        RemoveBucketArgs.builder().bucket(userBucketName).build()
                );
            }

            fileMetadataRepository.deleteByNameAndUser(filename, user);

            return ResponseEntity.ok("File deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File deletion failed: " + e.getMessage());
        }
    }

    @GetMapping(value = "/download-file/{filename}")
    public ResponseEntity<Object> downloadFile(@PathVariable("filename") String filename, HttpServletRequest request) {
        try {
            if (!AuthMiddleware.sessionExists(request, redisTemplate)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User session not found");
            }

            User user = sessionManger.getUserFromSession(request);
            String userBucketName = minioConfig.getBaseBucketName() + "-" + user.getUsername();

            MinioClient minioClient = MinioClient.builder()
                    .endpoint(minioConfig.getEndpoint())
                    .credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey())
                    .build();

            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(userBucketName).build());
            if (!bucketExists) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Bucket not found");
            }

            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(userBucketName).build()
            );
            for (Result<Item> result : objects) {
                Item item = result.get();
                if (!item.objectName().equals(filename)) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
                }
            }

            byte[] fileBytes;
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(userBucketName)
                            .object(filename)
                            .build())
            ) {
                fileBytes = stream.readAllBytes();
            }

            ByteArrayResource resource = new ByteArrayResource(fileBytes);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileBytes.length)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File download failed: " + e.getMessage());
        }
    }

    @PutMapping(value = "/update-file/{filename}")
    @Transactional
    public ResponseEntity<String> updateFileMetadata(@PathVariable("filename") String filename, @RequestBody FileMetadata fileMetadata, HttpServletRequest request) {
        try {
            if (!AuthMiddleware.sessionExists(request, redisTemplate)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User session not found");
            }

            User user = sessionManger.getUserFromSession(request);
            String userBucketName = minioConfig.getBaseBucketName() + "-" + user.getUsername();

            MinioClient minioClient = MinioClient.builder()
                    .endpoint(minioConfig.getEndpoint())
                    .credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey())
                    .build();

            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(userBucketName).build());
            if (!bucketExists) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Bucket not found");
            }

            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(userBucketName).build()
            );
            for (Result<Item> result : objects) {
                Item item = result.get();
                if (!item.objectName().equals(filename)) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
                }
            }

            FileMetadata existingMetadata = fileMetadataRepository.findByNameAndUser(filename, user);

            existingMetadata.setName(fileMetadata.getName());
            existingMetadata.setDateModified(LocalDateTime.now());
            fileMetadataRepository.save(existingMetadata);

            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .source(
                                    CopySource.builder()
                                            .bucket(userBucketName)
                                            .object(filename)
                                            .build()
                            )
                            .bucket(userBucketName)
                            .object(fileMetadata.getName())
                            .build()
            );

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(userBucketName)
                            .object(filename)
                            .build()
            );

            return ResponseEntity.ok("File updated successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File update failed: " + e.getMessage());
        }
    }
}