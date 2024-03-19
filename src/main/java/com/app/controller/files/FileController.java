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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

            String filename = file.getOriginalFilename();
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
            } else {
                Iterable<Result<Item>> objects = minioClient.listObjects(
                        ListObjectsArgs.builder().bucket(userBucketName).build()
                );
                for (Result<Item> result : objects) {
                    Item item = result.get();
                    if (item.objectName().equals(filename)) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File with the same name already exists");
                    }
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
}