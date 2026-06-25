package com.ym.ai_story_studio_server.util;

import com.ym.ai_story_studio_server.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片拼接工具类
 * 用于将多张图片横向拼接成一张画布
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageMergeUtil {

    private static final int PADDING = 20; // 图片之间的间距
    private static final int MAX_HEIGHT = 1024; // 最大高度
    private static final Color BACKGROUND_COLOR = Color.WHITE; // 背景颜色

    private final StorageService storageService;

    /**
     * 将多个图片URL横向拼接成一张图片
     *
     * @param imageUrls 图片URL列表
     * @return 拼接后的图片字节数组
     */
    public byte[] mergeImagesHorizontally(List<String> imageUrls) throws IOException {
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new IllegalArgumentException("图片URL列表不能为空");
        }

        log.info("开始拼接图片，共 {} 张", imageUrls.size());

        // 1. 下载所有图片
        List<BufferedImage> images = new ArrayList<>();
        for (String url : imageUrls) {
            try {
                BufferedImage image = downloadImage(url);
                if (image != null) {
                    images.add(image);
                    log.debug("成功加载图片: {}", url);
                }
            } catch (Exception e) {
                log.warn("加载图片失败，跳过: {}", url, e);
            }
        }

        if (images.isEmpty()) {
            throw new IOException("没有成功加载任何图片");
        }

        // 2. 计算目标尺寸
        int targetHeight = MAX_HEIGHT;
        int totalWidth = 0;

        // 按比例缩放所有图片到相同高度
        List<BufferedImage> resizedImages = new ArrayList<>();
        for (BufferedImage img : images) {
            int newWidth = (int) ((double) targetHeight / img.getHeight() * img.getWidth());
            BufferedImage resized = resizeImage(img, newWidth, targetHeight);
            resizedImages.add(resized);
            totalWidth += newWidth;
        }

        // 加上间距
        totalWidth += PADDING * (resizedImages.size() + 1);

        // 3. 创建画布
        BufferedImage canvas = new BufferedImage(totalWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = canvas.createGraphics();

        // 设置抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // 填充背景
        g2d.setColor(BACKGROUND_COLOR);
        g2d.fillRect(0, 0, totalWidth, targetHeight);

        // 4. 将图片绘制到画布上
        int currentX = PADDING;
        for (BufferedImage img : resizedImages) {
            g2d.drawImage(img, currentX, 0, null);
            currentX += img.getWidth() + PADDING;
        }

        g2d.dispose();

        // 5. 转换为字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(canvas, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();

        log.info("图片拼接完成，总宽度: {}, 高度: {}, 大小: {} KB", 
                totalWidth, targetHeight, imageBytes.length / 1024);

        return imageBytes;
    }

    /**
     * 从URL下载图片
     */
    private BufferedImage downloadImage(String imageUrl) throws IOException {
        if (imageUrl.startsWith("/")) {
            try (InputStream in = storageService.download(imageUrl)) {
                return ImageIO.read(in);
            }
        }

        URL url = new URL(imageUrl);
        try (InputStream in = url.openStream()) {
            return ImageIO.read(in);
        }
    }

    /**
     * 等比例缩放图片
     */
    private BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        return resizedImage;
    }

    /**
     * 将字节数组转换为BufferedImage
     */
    public BufferedImage bytesToImage(byte[] imageBytes) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
            return ImageIO.read(bais);
        }
    }
}
