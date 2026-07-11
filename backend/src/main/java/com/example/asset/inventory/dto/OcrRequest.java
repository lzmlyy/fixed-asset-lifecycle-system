package com.example.asset.inventory.dto;

import org.springframework.web.multipart.MultipartFile;

public class OcrRequest {
    private MultipartFile image;

    public MultipartFile getImage() {
        return image;
    }

    public void setImage(MultipartFile image) {
        this.image = image;
    }
}
