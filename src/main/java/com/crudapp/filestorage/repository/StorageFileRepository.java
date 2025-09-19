package com.crudapp.filestorage.repository;

import com.crudapp.filestorage.model.StorageFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StorageFileRepository extends JpaRepository<StorageFile, Integer> {
    List<StorageFile> findAllByOwner_Id(Integer ownerId);
    boolean existsByOwner_Id(Integer ownerId);
    Page<StorageFile> findAllByOwner_Id(Integer ownerId, Pageable pageable);
    Page<StorageFile> findAll(Pageable pageable);
}
