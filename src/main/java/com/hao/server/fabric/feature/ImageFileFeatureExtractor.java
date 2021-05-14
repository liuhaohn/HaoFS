package com.hao.server.fabric.feature;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Base64;

public class ImageFileFeatureExtractor implements FileFeatureExtractor {
    private final SaCoCo saCoCo = new SaCoCo();

    @Override
    public String generateFeature(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            saCoCo.extract(img);
            byte[] byteArrayRepresentation = saCoCo.getByteArrayRepresentation();
            return Base64.getEncoder().encodeToString(byteArrayRepresentation);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
}
