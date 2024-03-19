package com.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "files_metadata")
@Data
@Getter @Setter
public class FileMetadata {
    @Id
    @Column(name = "id")
    @SequenceGenerator(name = "filesMetadataIdSeq", sequenceName = "files_metadata_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "filesMetadataIdSeq")
    private Integer id;

    @Column(name = "date_created")
    private LocalDateTime dateCreated;

    @Column(name = "date_modified")
    private LocalDateTime dateModified;

    @Column(name = "name", unique = true)
    private String name;

    @Column(name = "size")
    private long size;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "location")
    private String location;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public FileMetadata() {}
}
