package com.se_lab.project.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    default String store(MultipartFile file) {
        return store(file, "user-routes");
    }

    String store(MultipartFile file, String subDir);
}
