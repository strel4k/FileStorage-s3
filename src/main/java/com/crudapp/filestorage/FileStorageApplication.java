package com.crudapp.filestorage;

import com.crudapp.filestorage.config.JwtProps;
import com.crudapp.filestorage.config.S3Props;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties({S3Props.class, JwtProps.class})
@SpringBootApplication
public class FileStorageApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileStorageApplication.class, args);
    }
}