package com.crudapp.filestorage.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "files")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StorageFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 500)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FileStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", foreignKey = @ForeignKey(name = "fk_files_owner"))
    private User owner;
}
