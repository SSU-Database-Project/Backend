package com.ssu.shop.global.file;

import com.ssu.shop.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Seller - Image", description = "상품·브랜드 이미지 업로드")
@RestController
@RequiredArgsConstructor
public class ImageController {

    private final ImageStorage imageStorage;

    public record ImageUploadResponse(String url) {
    }

    @Operation(summary = "이미지 업로드",
            description = "jpg/png/webp, 최대 5MB. 돌려받은 url 을 상품 등록의 imageUrl 이나 브랜드 logoUrl 에 넣는다")
    @PostMapping(value = "/api/seller/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ImageUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(new ImageUploadResponse(imageStorage.save(file)));
    }
}
