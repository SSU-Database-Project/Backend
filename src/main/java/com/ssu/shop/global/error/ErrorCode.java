package com.ssu.shop.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    CATEGORY_NOT_LEAF(HttpStatus.BAD_REQUEST, "상품은 소분류 카테고리에만 등록할 수 있습니다."),
    INVALID_OPTION(HttpStatus.BAD_REQUEST, "상품 옵션(색상·사이즈)이 올바르지 않습니다."),
    INVALID_FILE(HttpStatus.BAD_REQUEST, "업로드할 수 없는 파일입니다."),
    NO_ORDER_ITEMS(HttpStatus.BAD_REQUEST, "주문할 상품이 없습니다. 장바구니에서 주문할 상품을 체크해 주세요."),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다. 토큰이 없거나 만료되었습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 정지된 계정입니다."),
    SELLER_REQUIRED(HttpStatus.FORBIDDEN, "판매자 계정만 이용할 수 있습니다."),
    BRAND_NOT_OWNED(HttpStatus.FORBIDDEN, "본인 브랜드만 사용할 수 있습니다."),
    PRODUCT_NOT_OWNED(HttpStatus.FORBIDDEN, "본인 상품만 수정할 수 있습니다."),
    CANNOT_MODIFY_ADMIN(HttpStatus.FORBIDDEN, "관리자 계정은 변경할 수 없습니다."),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),

    // 409
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    DUPLICATE_BUSINESS_NUMBER(HttpStatus.CONFLICT, "이미 등록된 사업자번호입니다."),
    DUPLICATE_BRAND_NAME(HttpStatus.CONFLICT, "이미 사용 중인 브랜드명입니다."),
    DUPLICATE_PRODUCT_CODE(HttpStatus.CONFLICT, "브랜드 안에 같은 품번의 상품이 있습니다."),
    BRAND_NOT_APPROVED(HttpStatus.CONFLICT, "승인된 브랜드에만 상품을 등록할 수 있습니다."),
    PRODUCT_NOT_EDITABLE(HttpStatus.CONFLICT, "차단되었거나 삭제된 상품은 변경할 수 없습니다."),
    PRODUCT_NOT_APPROVED(HttpStatus.CONFLICT, "승인된 상품만 판매 상태를 바꿀 수 있습니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "현재 상태에서는 변경할 수 없습니다."),
    DATA_CONFLICT(HttpStatus.CONFLICT, "데이터 제약 조건에 위배됩니다."),
    // BE-2 (Inventory / Order) 용
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "현재 주문 상태에서는 처리할 수 없습니다."),
    PRODUCT_NOT_PURCHASABLE(HttpStatus.CONFLICT, "지금 구매할 수 없는 상품이 포함되어 있습니다."),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
