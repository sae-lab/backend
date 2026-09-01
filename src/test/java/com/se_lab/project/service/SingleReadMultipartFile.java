package com.se_lab.project.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/** 실제 서버처럼 입력 스트림을 한 번만 열 수 있는 multipart 테스트 대역이다. */
final class SingleReadMultipartFile implements MultipartFile {

    private final byte[] content;
    private final AtomicBoolean inputStreamOpened = new AtomicBoolean(false);

    SingleReadMultipartFile(byte[] content) {
        this.content = content;
    }

    @Override
    public String getName() {
        return "photo";
    }

    @Override
    public String getOriginalFilename() {
        return "photo.png";
    }

    @Override
    public String getContentType() {
        return "image/png";
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content.clone();
    }

    @Override
    public InputStream getInputStream() {
        if (!inputStreamOpened.compareAndSet(false, true)) {
            throw new IllegalStateException("입력 스트림은 한 번만 읽을 수 있습니다.");
        }
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File destination) throws IOException {
        Files.write(destination.toPath(), content);
    }
}
