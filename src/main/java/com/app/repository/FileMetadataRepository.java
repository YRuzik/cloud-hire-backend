package com.app.repository;

import com.app.entity.FileMetadata;
import com.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    void deleteByNameAndUser(String name, User user);
    FileMetadata findByNameAndUser(String name, User user);
}
