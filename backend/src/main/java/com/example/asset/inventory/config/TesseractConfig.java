package com.example.asset.inventory.config;

import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TesseractConfig {

    private static final Logger log = LoggerFactory.getLogger(TesseractConfig.class);

    @Value("${tesseract.datapath}")
    private String datapath;

    @Bean
    public Tesseract tesseract() {
        Tesseract tesseract = new Tesseract();
        try {
            tesseract.setDatapath(datapath);
            tesseract.setLanguage("chi_sim+eng");
            log.info("Tesseract OCR initialized with datapath: {}", datapath);
        } catch (Exception e) {
            log.warn("Tesseract OCR not available at {}: {}. OCR features will be unavailable.", datapath, e.getMessage());
        }
        return tesseract;
    }
}
