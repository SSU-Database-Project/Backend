package com.ssu.shop.global.file;

import com.ssu.shop.global.config.AppProperties;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 상품·브랜드 이미지를 서버 폴더(app.upload.dir)에 저장하고, 화면에서 쓸 경로(/images/파일명)를 돌려준다.
 * DB 에는 파일 자체가 아니라 이 경로만 저장한다(products.image_url).
 * 파일명은 UUID 로 새로 만든다 → 사용자가 보낸 파일명(../ 같은 경로 조작 포함)을 그대로 쓰지 않는다.
 */
@Component
public class ImageStorage {

    /** 허용 형식: Content-Type → 확장자 */
    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    public static final String URL_PREFIX = "/images/";

    private final Path dir;

    public ImageStorage(AppProperties properties) {
        this.dir = Path.of(properties.upload().dir()).toAbsolutePath().normalize();
    }

    public Path getDir() {
        return dir;
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "파일이 비어 있습니다.");
        }
        String ext = ALLOWED.get(file.getContentType());
        if (ext == null) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "jpg, png, webp 이미지만 올릴 수 있습니다.");
        }
        String filename = UUID.randomUUID() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(dir);
            Files.copy(in, dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "이미지를 저장하지 못했습니다.");
        }
        return URL_PREFIX + filename;
    }
}
