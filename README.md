# 패션 멀티 셀러 쇼핑몰 — Backend

Spring Boot 3.5 · Java 17 · MySQL 8.0 · Flyway · Spring Security(JWT) · springdoc-openapi

무신사를 참고한 패션 전용 쇼핑몰입니다. 판매자가 브랜드와 상품을 등록하면 관리자가 승인하고, 승인된 상품만 구매자에게 보입니다.

- 요구사항: [`docs/명세서.md`](docs/명세서.md)
- API 문서: 앱 실행 후 http://localhost:8080/swagger-ui.html

목차: [작업 현황](#작업-현황-트랙별) · [1. 실행](#1-실행) · [2. 테스트](#2-테스트) · [3. 패키지 구조](#3-패키지-구조) · [4. DB 설계](#4-db-설계) · [5. 인덱스와 실행 계획](#5-인덱스와-실행-계획-explain) · [6. 변경 이력과 남은 작업](#6-변경-이력과-남은-작업) · [7. AWS 배포 가이드](#7-aws-배포-가이드-ec2--docker-compose) · [8. 계정 관리 규칙](#8-계정-관리-규칙-2026-11-17) · [9. 모의 결제](#9-모의-결제-2026-11-19) · [10. 판매자·관리자 기능 보강](#10-판매자관리자-기능-보강-2026-11-20)

---

## 작업 현황 (트랙별)

> 마지막 갱신: 2026-11-17 · 담당은 역할 분담 **안**(BE-1: 좋아요·통계·시연 데이터, BE-2: 재고·장바구니·주문)이며 팀 확정 전입니다.
> 갱신 방법: 끝난 항목의 `- [ ]`를 `- [x]`로 바꾸고, 요약 표의 진행 수와 위의 갱신 날짜를 고칩니다. 새 작업은 해당 트랙에 줄을 추가합니다.

| 트랙 | 담당 | 진행 | 상태 |
| --- | --- | --- | --- |
| [A. 공통 기반·인프라](#a-공통-기반인프라) | BE-1 | 8 / 10 | 🔶 샘플 이미지·실제 AWS 배포 남음 |
| [B. DB 설계·데이터](#b-db-설계데이터) | BE-1 | 6 / 6 | ✅ 완료 |
| [C. 회원·판매자](#c-회원판매자) | BE-1 | 6 / 6 | ✅ 완료 |
| [D. 브랜드·상품](#d-브랜드상품) | BE-1 | 6 / 6 | ✅ 완료 |
| [E. 좋아요](#e-좋아요) | BE-1 | 3 / 3 | ✅ 완료 |
| [F. 재고](#f-재고) | BE-2 | 3 / 3 | ✅ 완료 |
| [G. 장바구니](#g-장바구니) | BE-2 | 2 / 2 | ✅ 완료 |
| [H. 주문·배송](#h-주문배송) | BE-2 | 8 / 8 | ✅ 완료 |
| [I. 통계](#i-통계) | BE-1 | 5 / 5 | ✅ 완료 |
| [팀 결정](#팀-결정) | 팀 | 1 / 4 | 🔶 배포 방식 결정됨 |

순서: **F(재고 변경 서비스) → H(주문)**. I(통계)는 B의 시연 주문 데이터가 있으면 H를 기다리지 않고 시작할 수 있습니다.

### A. 공통 기반·인프라
- [x] 프로젝트 설정, Spring Security + JWT, 공통 응답·예외 처리
- [x] Swagger, CORS
- [x] 이미지 업로드 (서버 폴더 저장, `/api/seller/images`)
- [x] Docker 구성 (MySQL + 앱)
- [x] GitHub 저장소, 패키지명 `com.ssu.shop`
- [x] 회원제: 가입·로그인 외 로그인 필수
- [x] 앱 컨테이너 이미지를 최신 코드로 다시 빌드해 확인 (11-17: 빈 DB에서 V1~V5·시연 데이터 자동 적용, 시연 주문 300건, 정합성 15종 0건, Docker `shop_test`로 테스트 154개 통과)
- [ ] 샘플 이미지 22장 준비 (`src/main/resources/static/images/samples/`, 파일명은 [1.3](#13-시연-계정-local-프로필)) — 팀
- [x] 배포 방식 결정 — AWS EC2 + Docker Compose (11-17 팀 결정). 서버용 설정(`docker-compose.prod.yml`, `.env.example`, `scripts/deploy.sh`)과 [7장 가이드](#7-aws-배포-가이드-ec2--docker-compose) 준비
- [ ] 실제 AWS 배포 (7장대로) — 팀원

### B. DB 설계·데이터
- [x] 스키마 V1~V8 (테이블 17개, 제약조건, V5 = 판매자 상태별 배송 인덱스, V6 = 동시 로그인 방지 컬럼, V7 = 모의 결제·환불, V8 = 판매자 취소·브랜드 삭제), DB 제약 테스트 18개
- [x] 시연 데이터: 회원·판매자·브랜드·상품 27개와 옵션·좋아요
- [x] ERD·설계 근거 정리 (4장), 명세서 DB 확정안
- [x] 시연 주문 데이터 (90일치, `R__demo_orders.sql`) + 정합성 검사 15종 (`scripts/check-integrity.sql`)
- [x] 대량 데이터로 V4 목록 쿼리 측정 — 먼저 등록한 순 0.16ms ([5.4](#54-v4-쿼리-측정-mysql-80-상품-50000--주문-50000--판매자-20-2026-11-11))
- [x] 주문 쿼리 EXPLAIN — 내 주문·판매자 배송 목록 모두 커버링 인덱스, V5 불필요 (통계 쿼리는 트랙 I)

### C. 회원·판매자
- [x] 가입 (일반회원 / 판매자 선택, 판매자는 사업자 정보), 로그인, 내 정보
- [x] 관리자 계정 자동 생성, 가입으로 관리자 불가
- [x] 관리자 회원 목록, 정지·해제, 탈퇴 처리
- [x] 판매자 탈퇴 수정 (11-17): 계정 탈퇴가 저장되지 않던 버그, 주문과 동시에 오면 데드락이 나던 잠금 순서 (`SellerWithdrawDbTest`)
- [x] 같은 이메일 동시 가입 테스트
- [x] 계정 관리 (11-17 팀 결정): 진행 중 주문이 있으면 정지·탈퇴 불가, 정지된 판매자 상품 주문 불가, 본인 탈퇴, 마이페이지 수정·비밀번호 변경, 동시 로그인 방지 (V6, [8장](#8-계정-관리-규칙-2026-11-17), `AccountRulesDbTest`)

### D. 브랜드·상품
- [x] 브랜드 등록, 관리자 승인·거절(사유), 브랜드 페이지
- [x] 카테고리·색상·사이즈 목록 API
- [x] 상품 등록 (옵션별 재고), 수정 시 재승인, 판매중↔품절, 삭제
- [x] 관리자 상품 승인·거절·차단·해제
- [x] 검색 (상품명·브랜드명·품번), 기본 정렬 = 먼저 등록한 순, 상품 상세
- [x] 브랜드 삭제(판매자 본인·관리자, 진행 중 주문 있으면 거절, 이름 재사용), 브랜드 + 첫 상품 동시 등록, 판매자·관리자 상품 목록에 옵션 포함 (11-20, V8, [10장](#10-판매자관리자-기능-보강-2026-11-20))

### E. 좋아요
- [x] 테이블·엔티티 (`likes`, 복합 PK)
- [x] 좋아요 추가·취소·상태 API (`POST/DELETE/GET /api/products/{id}/like`, 중복 클릭 멱등)
- [x] 내 좋아요 목록 API (`GET /api/users/me/likes`, 최근 순, 보이는 상품만)

### F. 재고
- [x] 옵션 단위 재고·재고 이력 테이블, 엔티티
- [x] 재고 변경 서비스 (차감·복원·입고·조정 + 이력, 동시성 처리) — 재고는 이 서비스에서만 변경
- [x] 판매자 입고·조정 API, 재고 이력 조회 API

### G. 장바구니
- [x] 테이블·엔티티 (옵션 단위, 체크 상태 `selected`)
- [x] 장바구니 조회 (체크한 항목 합계), 담기, 수량·체크 변경, 삭제

### H. 주문·배송
- [x] 주문·판매자별 배송(`shipments`)·주문 상품 테이블, 엔티티
- [x] 주문 생성: 체크한 항목만, 판매자별 배송 묶음, 재고 차감, 장바구니 비우기
- [x] 동시 주문 테스트 (재고 10개에 동시 주문 20건 → 10건 성공)
- [x] 내 주문 목록·상세 (판매자별 배송 상태)
- [x] 판매자별 취소 (주문완료에서만, 재고 복원), 구매확정
- [x] 판매자 배송 상태 변경, 확정된 주문 목록
- [x] 모의 결제 (11-19 팀 결정): 결제수단 3종, 서버가 금액 계산·승인, 거절 시 전체 롤백, 판매자별 부분 환불 (V7, [9장](#9-모의-결제-2026-11-19), `PaymentDbTest`)
- [x] 판매자 취소 (11-20): 배송 전까지, 사유 필수, 재고 복원 + 부분 환불, 취소자·사유 기록 (V8, [10장](#10-판매자관리자-기능-보강-2026-11-20))

### I. 통계
- [x] 판매자 기간별(일/주/월) 판매량·매출 (`GET /api/seller/stats/sales`, 빈 구간은 0으로 채움) + 대시보드 요약 (`/summary`)
- [x] 상품별 판매 순위 (`GET /api/seller/stats/products/top`, 판매량·매출순)
- [x] 관리자 전체 판매 통계 (`GET /api/admin/stats/sales`)
- [x] 통계 쿼리 EXPLAIN 확인 → 처리할 배송 수 조회에 V5 인덱스 추가 ([5.5](#55-통계-쿼리-측정과-v5-인덱스-2026-11-11))
- [x] 관리자 브랜드별 판매량·매출 API (11-20, 취소 제외·주문일 기준, 브랜드 합계 = 플랫폼 합계)

### 팀 결정
- [ ] 백엔드 역할 분담 확정
- [ ] 통계에 넣을 주문 기준 — **지금 구현: 취소만 제외, 주문일 기준** (다르게 정하면 `StatsRepository` 조건만 바꾸면 됨)
- [ ] 회의 결정사항 문서의 "확인 요청" 항목 (대표 이미지 1장, 카테고리·색상·사이즈 목록 등)
- [x] 배포 방식 — AWS EC2 + Docker Compose (A 트랙과 같은 항목)

---

## 1. 실행

### 1.1 DB 준비

**방법 A — Docker (권장).** Docker Desktop만 있으면 됩니다. 호스트 포트는 **3308**입니다.

```bash
docker compose up -d                          # MySQL만 (앱은 IDE / gradlew로 실행)
docker compose --profile app up -d --build    # MySQL + 앱 같이 (시연용, http://localhost:8080)
docker compose down                           # 중지 (데이터 유지)
docker compose down -v                        # 중지 + 데이터 삭제 (DB 초기화)
```

첫 실행 때 `shop`(개발), `shop_test`(DB 테스트) 데이터베이스와 `shop / shop1234` 계정이 자동으로 만들어집니다.

**방법 B — 직접 설치한 MySQL 8.0.** root 계정으로 한 번 실행합니다.

```bash
mysql -h 127.0.0.1 -P 3307 -u root -p < scripts/init-db.sql
```

어느 방법이든 테이블·카테고리·색상·사이즈·시연 데이터는 앱이 시작할 때 Flyway가 자동으로 넣습니다.

### 1.2 앱 실행

```bash
./gradlew bootRun
```

앱 기본 설정은 `localhost:3307`(방법 B)입니다. Docker DB(3308)를 쓰려면 `DB_URL`을 지정합니다.

```powershell
# PowerShell
$env:DB_URL="jdbc:mysql://localhost:3308/shop?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false"; ./gradlew bootRun
```

> 앱 컨테이너(`--profile app`)가 떠 있으면 8080 포트가 겹칩니다. 먼저 `docker compose stop app`으로 끄세요.

| 환경변수 | 기본값 |
| --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3307/shop?...` |
| `DB_USERNAME` / `DB_PASSWORD` | `shop` / `shop1234` |
| `JWT_SECRET` | 개발용 고정값 (배포 시 반드시 변경, 32바이트 이상) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@shop.com` / `admin1234!` |
| `UPLOAD_DIR` | `./uploads` (상품·브랜드 이미지 저장 폴더) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` (프론트 주소, 여러 개면 쉼표로) |
| `SPRING_PROFILES_ACTIVE` | `local` (시연 데이터 포함). `prod`면 시연 데이터 미적용 |

서버 배포 때는 이 값들을 `.env`에 넣습니다 → [7장](#7-aws-배포-가이드-ec2--docker-compose).

### 1.3 시연 계정 (`local` 프로필)

관리자 계정은 앱 시작 시 없으면 자동 생성됩니다. 나머지는 `db/demo/R__demo_data.sql`이 넣으며 비밀번호는 모두 `demo1234!`입니다.

| 계정 | 역할 | 내용 |
| --- | --- | --- |
| `admin@shop.com` / `admin1234!` | 관리자 | 브랜드 승인, 상품 승인, 회원 관리(정지·탈퇴) |
| `store.a@demo.com` | 판매자 | 어반스타일 — 브랜드 URBAN BASIC, URBAN STEP, 상품 20개 |
| `store.b@demo.com` | 판매자 | 무드랩 — 브랜드 MOODLAB, MOODLAB ATELIER(승인 대기), 상품 7개 |
| `store.c@demo.com` | 판매자 | 새싹상점 — 브랜드 없음 (브랜드 등록·승인 시연용) |
| `buyer1@demo.com` ~ `buyer8@demo.com` | 일반 회원 | 시연 주문을 나눠 가짐. buyer1·2에는 좋아요도 있음 |

상품에는 승인 대기(PENDING) 2개, 거절(REJECTED) 1개, 판매자 수동 품절(SOLD_OUT) 1개, 모든 옵션 재고 0(품절 표시) 1개가 섞여 있습니다. 옵션 일부는 재고가 0이라 사이즈별 품절도 볼 수 있습니다.

**시연 주문** (`db/demo/R__demo_orders.sql`): 최근 90일에 걸친 주문 약 300건입니다(최근일수록 조금 더 많음). 한 주문에 판매자가 여럿인 경우도 있어 판매자별 배송을 볼 수 있습니다. 배송 상태는 주문 나이에 따라 정해지고(오래된 주문은 대부분 구매확정, 최근 주문은 배송준비·주문완료), 약 8%는 취소되어 재고가 복원돼 있습니다. 재고 이력(입고 → 주문 → 취소)도 시간순으로 맞춰 넣었습니다. 시연 주문이 이미 있으면 다시 넣지 않으며, 새로 만들려면 DB를 초기화합니다(`docker compose down -v`).

**샘플 이미지.** 시연 상품은 품목(소분류)별 샘플 이미지 `/images/samples/{품목}.jpg`를 씁니다. 파일은 `src/main/resources/static/images/samples/`에 넣습니다. 필요한 파일: `tee, longsleeve, shirt, knit, hoodie, jacket, coat, padding, cardigan, denim, slacks, training, shorts, dress, skirt, sneakers, loafers, backpack, crossbag, cap, beanie, socks` (각각 `.jpg`).

> 관리자 id는 1이라는 보장이 없습니다(시연 데이터가 먼저 들어가면 id가 뒤로 밀림). FE·스크립트에서 id를 가정하지 마세요.

---

## 2. 테스트

```bash
./gradlew test
```

- 단위·보안 테스트는 DB 없이 실행됩니다.
- **DB 테스트**는 `DB_TEST_URL`이 있을 때만 실행됩니다: DB 제약 검증 18개(`SchemaConstraintTest`), 통계 SQL 7개(`StatsQueryTest`: 취소 제외, 주·월 묶음, 자정 경계), 같은 이메일 동시 가입 10건(`ConcurrentSignupTest`).
  - BE-2 추가: 재고 변경·동시성·롤백 7개(`StockServiceDbTest`), 장바구니·판매자 재고 API 12개(`CartStockApiDbTest`), 주문 생성·동시성·장바구니 변경 충돌·판매자 상품 변경과의 경합 13개(`OrderCreateDbTest`), 주문 조회·취소·구매확정·판매자 배송 관리 7개(`OrderManageDbTest`).
  - 판매자 탈퇴 2개(`SellerWithdrawDbTest`): 탈퇴가 실제로 커밋되는지, 주문과 동시에 와도 데드락이 없는지(10라운드).
  - 계정 관리 10개(`AccountRulesDbTest`): 진행 중 주문이 있을 때 정지·탈퇴 거절(상태별), 정지된 판매자 상품 주문 거절, 본인 탈퇴(구매자·판매자), 주문과 정지가 동시에 올 때(구매자·판매자 각 10라운드), 동시 로그인·마이페이지·비밀번호 변경(HTTP).
  - 모의 결제 9개(`PaymentDbTest`): 카드·계좌이체·간편결제 승인과 기록(카드는 끝 4자리만), 카드 거절·한도 초과 시 주문·재고·장바구니 전체 롤백, 판매자별 부분 환불, 같은 묶음 동시 취소 시 환불 1건, 정합성 규칙 16~21, HTTP 400·402.
  - 판매자·관리자 기능 보강 14개(`SellerOpsDbTest`): 판매자 취소(배송준비까지, 재고·환불, 사유), 구매자·판매자 동시 취소 5라운드, 브랜드 삭제(진행 중 주문 거절·롤백, 이름 재사용, 목록 제외), 브랜드 + 첫 상품(롤백, 승인 순서), 브랜드별 매출 합계, 목록 옵션, V8 제약.
- 현재 테스트는 메서드 기준 총 **203개**(단위·보안 **103개**, DB **100개**)입니다. `ConcurrentSignupTest`는 동시 요청 10건을 검증하는 테스트 1개입니다.

```powershell
# PowerShell (Docker DB 기준. 직접 설치한 MySQL이면 3307)
$env:DB_TEST_URL="jdbc:mysql://localhost:3308/shop_test?allowPublicKeyRetrieval=true&useSSL=false"; ./gradlew test --rerun
```
```bash
# Git Bash
DB_TEST_URL="jdbc:mysql://localhost:3308/shop_test?allowPublicKeyRetrieval=true&useSSL=false" ./gradlew test --rerun
```

Gradle은 환경변수 변화를 입력값으로 보지 않아 이전 결과(DB 테스트 skip)를 재사용하므로 `--rerun`이 필요합니다.

H2 대신 실제 MySQL로 테스트하는 이유: CHECK·FK·REGEXP 동작이 MySQL과 달라, H2에서 통과해도 DB 제약을 검증한 것이 아니기 때문입니다.

**데이터 정합성 검사** — DB 제약으로 표현할 수 없는, 여러 행·테이블에 걸친 규칙 21가지(옵션 재고 = 마지막 재고 이력, 주문 총액 = 라인 합계, 배송 묶음의 판매자 = 옵션의 판매자, 결제 금액 = 주문 총액, 환불 = 취소한 묶음 금액 등)를 확인합니다. 모든 `bad`가 0이어야 정상입니다. 시연 데이터나 주문·재고 로직을 바꾼 뒤에 돌립니다.

```bash
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3308 -u shop -pshop1234 shop < scripts/check-integrity.sql
```

---

## 3. 패키지 구조

```
com.ssu.shop
├── global      config(Security, Swagger, CORS, 이미지 경로, 관리자 초기화) / security(JWT) / common / error / file(이미지 업로드)
└── domain
    ├── user      회원가입(일반회원·판매자)·로그인·내 정보, 관리자 회원 관리(정지·탈퇴)   (BE-1)
    ├── seller    판매자 정보                                                         (BE-1)
    ├── brand     브랜드 등록·승인·브랜드 페이지                                       (BE-1)
    ├── category  카테고리 트리, 색상·사이즈 목록 (고정 데이터)                         (BE-1)
    ├── product   상품·옵션 등록·수정·삭제·조회, 관리자 승인·거절·차단                  (BE-1)
    ├── like      좋아요 추가·취소·내 목록                                             (BE-1)
    ├── stats     판매 통계: 판매자 일/주/월·상품 순위·대시보드, 관리자 전체                 (BE-1)
    ├── inventory 재고 차감·복원·입고·조정과 이력, 판매자 재고 API (BE-2)
    ├── cart      장바구니 조회·담기·수량·체크 변경·삭제 (BE-2)
    └── order     주문 생성·조회·취소·구매확정, 판매자별 배송 조회·상태 변경 (BE-2)
```

```
src/main/resources
├── db/migration
│   ├── V1__init.sql                        최초 스키마 (테이블 11개 + 제약)
│   ├── V2__seed_categories.sql             최초 카테고리 (V4에서 교체)
│   ├── V3__tune_product_list_indexes.sql   상품 목록 인덱스 개선 (5장)
│   ├── V4__fashion_mall_redesign.sql       패션몰 재설계 (6장) — 현재 스키마
│   ├── V5__shipments_seller_status_index.sql  판매자 상태별 배송 인덱스 (5.5)
│   ├── V6__users_login_session.sql         동시 로그인 방지용 로그인 식별값 (8장)
│   ├── V7__payments.sql                    모의 결제·부분 환불 (9장)
│   └── V8__seller_cancel_brand_delete.sql  판매자 취소 기록·브랜드 삭제 (10장)
├── db/demo
│   ├── R__demo_data.sql                    시연 데이터: 회원·브랜드·상품·좋아요 (local 프로필만)
│   ├── R__demo_orders.sql                  시연 주문 90일치 (local 프로필만)
│   └── R__demo_payments.sql                시연 주문의 결제·환불 기록 (local 프로필만, 9장)
└── static/images/samples                   시연용 샘플 이미지
```

---

## 4. DB 설계

### 4.1 설계 원칙

1. **정규화 기본 (3NF 이상).** 하나의 사실은 한 곳에만 저장해 삽입·수정·삭제 이상을 막는다.
2. **반정규화는 "시간이 지나도 변하면 안 되는 값"에만.** 주문 당시 가격·상품명·색상·사이즈는 스냅샷으로 복사하는 것이 오히려 정확하다.
3. **무결성은 DB가 최후 방어선.** PK·FK·UNIQUE·NOT NULL·CHECK가 잘못된 데이터를 거부하게 한다. DB로 표현할 수 없는 규칙(다른 행·테이블 값에 의존)만 서비스에서 검증한다.
4. **물리 삭제 최소화.** 주문이 참조하는 회원·상품·옵션은 지우지 않고 상태값(WITHDRAWN, DELETED)으로 비활성화한다. FK는 기본 `RESTRICT`.
5. **네이밍.** 테이블은 복수형 snake_case(`users`, `orders`), PK는 `id`, FK는 `{참조 단수}_id`, 제약 이름은 `pk_/fk_/uk_/ck_/idx_` 접두어.

### 4.2 ERD

```mermaid
erDiagram
    users ||--o| sellers : "판매자 정보 (1 : 0..1)"
    users ||--o| carts : "장바구니 (1 : 0..1)"
    users ||--o{ orders : "주문"
    users ||--o{ likes : "좋아요"
    sellers ||--o{ brands : "보유"
    sellers ||--o{ shipments : "배송 담당"
    brands ||--o{ products : "소속 (필수)"
    categories ||--o{ products : "분류"
    categories |o--o{ categories : "상위-하위"
    products ||--|{ product_options : "옵션 (1..N)"
    products ||--o{ likes : ""
    colors ||--o{ product_options : ""
    sizes ||--o{ product_options : ""
    product_options ||--o{ stock_history : "재고 변동"
    product_options ||--o{ cart_items : ""
    product_options ||--o{ order_items : ""
    carts ||--o{ cart_items : "담긴 옵션"
    orders ||--|{ shipments : "판매자별 배송 (1..N)"
    shipments ||--|{ order_items : "주문 상품 줄 (1..N)"
    order_items |o--o{ stock_history : "주문/취소 원인"

    users {
        BIGINT id PK
        VARCHAR email UK
        VARCHAR role "USER/SELLER/ADMIN"
        VARCHAR status "ACTIVE/SUSPENDED/WITHDRAWN"
        DATETIME withdrawn_at
    }
    sellers {
        BIGINT id PK
        BIGINT user_id FK,UK
        CHAR business_number UK
        VARCHAR company_name
    }
    brands {
        BIGINT id PK
        BIGINT seller_id FK
        VARCHAR name UK
        VARCHAR status "PENDING/APPROVED/REJECTED"
        VARCHAR reject_reason
    }
    categories {
        BIGINT id PK
        BIGINT parent_id FK
        VARCHAR name
        VARCHAR size_group "CLOTHING/SHOES/FREE"
    }
    products {
        BIGINT id PK
        BIGINT brand_id FK
        BIGINT category_id FK
        VARCHAR product_code "품번"
        VARCHAR name
        INT price
        VARCHAR gender "MEN/WOMEN/UNISEX"
        CHAR season "24SS/24FW"
        INT ship_days
        VARCHAR image_url
        VARCHAR status
        DATETIME created_at
    }
    colors {
        BIGINT id PK
        VARCHAR name UK
        CHAR hex_code
    }
    sizes {
        BIGINT id PK
        VARCHAR size_group
        VARCHAR label
    }
    product_options {
        BIGINT id PK
        BIGINT product_id FK
        BIGINT color_id FK
        BIGINT size_id FK
        INT stock_quantity "CHECK >= 0"
    }
    stock_history {
        BIGINT id PK
        BIGINT option_id FK
        INT change_qty
        INT quantity_after
        VARCHAR type "IN/ORDER/CANCEL/ADJUST"
        BIGINT order_item_id FK
    }
    carts {
        BIGINT id PK
        BIGINT user_id FK,UK
    }
    cart_items {
        BIGINT id PK
        BIGINT cart_id FK
        BIGINT option_id FK
        INT quantity
        BOOLEAN selected
    }
    likes {
        BIGINT user_id PK,FK
        BIGINT product_id PK,FK
    }
    orders {
        BIGINT id PK
        BIGINT user_id FK
        BIGINT total_price
        VARCHAR address
        DATETIME created_at
    }
    shipments {
        BIGINT id PK
        BIGINT order_id FK
        BIGINT seller_id FK
        VARCHAR status
        DATETIME confirmed_at
        DATETIME canceled_at
    }
    order_items {
        BIGINT id PK
        BIGINT shipment_id FK
        BIGINT option_id FK
        VARCHAR product_name "스냅샷"
        VARCHAR color_name "스냅샷"
        VARCHAR size_label "스냅샷"
        INT unit_price "스냅샷"
        INT quantity
    }
```

전체 컬럼과 제약은 [`V4__fashion_mall_redesign.sql`](src/main/resources/db/migration/V4__fashion_mall_redesign.sql)(현재 구조)에 있습니다.

### 4.3 관계와 구현 방법

| 관계 | 카디널리티 | DB 구현 | 이유 |
| --- | --- | --- | --- |
| users – sellers | 1 : 0..1 | `sellers.user_id` FK + **UNIQUE** | FK만 있으면 1:N이 된다. 판매자 가입 시 같은 트랜잭션에서 생성 |
| brands – products | 1 : 0..N | `products.brand_id` FK **NOT NULL** | 모든 상품은 브랜드에서 등록한다. 상품의 판매자는 브랜드로 정해진다 |
| products – product_options | 1 : 1..N | `product_options.product_id` FK + UK(product, color, size) | 색상 × 사이즈 조합마다 재고. "최소 1개"는 등록 트랜잭션이 보장 |
| colors / sizes – product_options | 1 : 0..N | FK | 미리 정한 목록에서만 고른다. 목록에 없는 값은 FK가 거부 |
| categories – categories | 0..1 : 0..N | `parent_id` 자기참조 | 2단계 고정 분류. `size_group`이 쓸 사이즈 체계를 정한다 |
| users ↔ products | N : M | `likes` 복합 PK(user_id, product_id) | 식별 관계. PK가 곧 "한 상품에 좋아요 한 번" |
| carts – cart_items | 1 : 0..N | UK(cart_id, option_id), `selected` | 같은 옵션은 한 줄. 체크 상태를 DB에 저장 |
| orders – shipments | 1 : 1..N | UK(order_id, seller_id) | 한 번에 주문해도 배송·취소·구매확정은 판매자별 |
| shipments – order_items | 1 : 1..N | `order_items.shipment_id` | 주문·판매자는 shipment로, 상품은 option으로 도출 |

### 4.4 정규화와 의도적 반정규화

모든 테이블이 3NF(대부분 BCNF)를 만족합니다. V4에서 더 좋아진 곳:
- **`products.seller_id` 제거.** 모든 상품이 브랜드에 속하므로 `brand_id → seller_id`가 성립한다. 남겨 두면 이행 종속(3NF 위반)이라 지웠다. 판매자는 `brands.seller_id`로 찾는다.
- **`order_items`의 order_id·seller_id·product_id 제거.** 각각 shipment, option으로 도출된다.
- **상품 1:1 `inventory` 제거.** 재고는 옵션의 속성이 되어 `product_options`에 있다.

**반정규화한 컬럼**

| 컬럼 | 원본 | 이유 | 갱신 이상이 없는 이유 |
| --- | --- | --- | --- |
| `order_items.unit_price` | `products.price` | 주문 당시 가격 보존 | 생성 후 수정하지 않음 |
| `order_items.product_name / color_name / size_label` | 상품·색상·사이즈 | 구매 당시 "반팔티 블랙/M"이 그대로 보이게 | 생성 후 수정하지 않음 |
| `orders.total_price` | Σ(quantity × unit_price) | 목록마다 SUM 하지 않음 | 라인은 수정 없음 |
| `stock_history.quantity_after` | 이력 누적합 | 변동 후 재고 표시, 정합성 검사 | INSERT만 |
| `users.role = SELLER` | sellers 행 존재 | 권한 검사를 users 한 테이블로 | 가입 트랜잭션에서 함께 생성, 이후 불변 |

### 4.5 핵심 설계 결정

**① 재고는 옵션(색상 × 사이즈) 단위.** 사이즈별 품절을 표시하고, 주문 시 산 옵션의 재고만 줄이며, 다른 사이즈 주문끼리는 락을 기다리지 않는다. `CHECK (stock_quantity >= 0)`은 어떤 동시성 방식을 쓰든 음수 재고를 막는 마지막 방어선이다. 상품 정보(products)와 재고(product_options)가 다른 행이라 주문이 상품 정보 수정을 막지 않는다.

**② 색상·사이즈는 참조 테이블.** 자유 입력이면 "블랙/검정/black"이 다른 값이 된다. 목록 테이블 + FK로 정해진 값만 받는다. 사이즈는 체계(CLOTHING/SHOES/FREE)별로 나누고, 카테고리의 체계와 맞는지는 서비스에서 검증한다(두 테이블 비교라 CHECK 불가).

**③ 상품 승인 흐름.** 상태 `PENDING → ON_SALE / REJECTED`, 판매자 토글 `ON_SALE ↔ SOLD_OUT`, 관리자 `BLOCKED`, 판매자 `DELETED`. 정보를 고치면 다시 PENDING이 되어 승인 전까지 숨겨진다. 재고 변경은 승인 없이 반영한다. 승인·거절·차단은 허용되는 이전 상태를 WHERE에 넣은 조건부 UPDATE라 동시 처리에도 잘못된 전이가 일어나지 않는다.

**④ 판매자별 배송(shipments).** 장바구니에서 한 번에 주문해도 판매자마다 배송 상태가 다르다. 주문 헤더에 상태 하나를 두면 이를 표현할 수 없어, 주문 1 : 판매자별 배송 N으로 나눴다. 상태: 주문완료 → 배송준비 → 배송중 → 배송완료 → 구매확정(구매자). 취소는 판매자별로 주문완료 상태에서만. `CHECK`로 "확정 ⇔ 확정 시각", "취소 ⇔ 취소 시각"을 짝지었다.

**⑤ 탈퇴는 행 삭제가 아니라 개인정보 삭제.** 회원 행을 지우면 주문·판매 기록이 깨진다. 이메일을 `withdrawn_{id}@withdrawn.local`로 바꾸고 이름·연락처를 지운 뒤 `WITHDRAWN`으로 둔다. 같은 이메일로 다시 가입할 수 있다. 판매자가 탈퇴하면 그 판매자의 상품은 판매 종료(DELETED)된다.

**⑥ 상태값은 ENUM 대신 VARCHAR + CHECK.** MySQL `ENUM`은 값 추가 시 `ALTER TABLE`이 필요하고 내부 순서 번호로 비교·정렬된다. 이번 재설계처럼 상태가 바뀔 때 CHECK만 교체하면 된다.

### 4.6 DB 제약 목록

| 규칙 | 제약 |
| --- | --- |
| 이메일·사업자번호·브랜드명 중복 금지, 회원당 판매자 1개·장바구니 1개 | UNIQUE |
| 품번은 브랜드 안에서 유일 | UNIQUE(brand_id, product_code) |
| 같은 상품에 같은 색상·사이즈 옵션은 하나 | UNIQUE(product_id, color_id, size_id) |
| 장바구니 같은 옵션 / 주문 배송 묶음의 같은 옵션 / 주문의 같은 판매자는 하나 | UNIQUE |
| 같은 상품에 좋아요 한 번 | 복합 PK(user_id, product_id) |
| 모든 상품은 브랜드 소속 | `brand_id` NOT NULL + FK |
| 색상·사이즈는 목록 안에서만 | FK |
| 시즌 `24SS/24FW`, 성별 `MEN/WOMEN/UNISEX`, 출고 0~30일, 사업자번호 숫자 10자리 | CHECK (REGEXP 포함) |
| 재고·가격·수량 음수 불가 | CHECK |
| 재고 이력 부호, ORDER·CANCEL은 주문 라인 필수 | CHECK |
| 탈퇴 ⇔ 탈퇴 시각, 구매확정 ⇔ 확정 시각, 취소 ⇔ 취소 시각 | CHECK |

모든 제약은 `SchemaConstraintTest`에서 실제 MySQL로 위반 시 거부되는지 검증합니다.

### 4.7 데이터 타입

| 항목 | 선택 | 이유 |
| --- | --- | --- |
| PK | `BIGINT AUTO_INCREMENT` | 순차 증가라 클러스터드 인덱스 끝에만 추가(페이지 분할 적음) |
| 금액 | `INT`(단가), `BIGINT`(합계) | 원 단위 정수. `FLOAT/DOUBLE`은 오차 |
| 시즌 | `CHAR(4)` | 항상 4자리(24FW) |
| 시각 | `DATETIME(6)` | `TIMESTAMP`는 2038년 한계와 자동 타임존 변환. 타임존은 Asia/Seoul로 고정 |
| 이미지 | `VARCHAR(500)` 경로 | 파일은 서버 폴더에 저장하고 DB에는 경로만. DB가 무거워지지 않는다 |
| 문자셋 | `utf8mb4` / `utf8mb4_0900_ai_ci` | 한글·이모지 저장. 대소문자 무시라 이메일 UNIQUE를 대소문자로 우회할 수 없음 |

### 4.8 스키마 관리: Flyway + `ddl-auto=validate`

- SQL 파일로 관리하면 **설계한 DDL = 실제 DB**가 되고 변경 이력이 남습니다. JPA 자동 생성은 CHECK·제약 이름·인덱스 순서를 원하는 대로 만들지 못합니다.
- `validate`라서 엔티티와 테이블이 어긋나면 앱이 시작 단계에서 실패합니다.
- **스키마를 바꿀 때는 이미 적용된 V 파일을 고치지 말고 새 V 파일(V5 …)을 추가합니다.** Flyway가 적용된 파일의 체크섬을 검사합니다. 이번 재설계도 V1을 고치지 않고 V4로 추가했습니다.

---

## 5. 인덱스와 실행 계획 (EXPLAIN)

### 5.1 상품 목록 인덱스

| 인덱스 | 쓰는 쿼리 |
| --- | --- |
| `products(category_id, created_at)` | 소분류 목록 — 등호 조건 뒤에 정렬 컬럼 |
| `products(created_at)` | 홈(전체) 목록, 대분류 목록. 기본 정렬은 먼저 등록한 순(`created_at ASC, id ASC`) |
| `products(price)` | 가격순 |
| `products(status, created_at)` | 관리자 승인 대기 목록 — PENDING은 소수라 status의 선택도가 높다 |
| `products(brand_id, product_code)` UNIQUE | 브랜드 페이지·판매자 상품 목록, 품번 중복 검사 |
| `product_options(product_id, color_id, size_id)` UNIQUE | 상세 화면 옵션, 목록 총 재고 일괄 조회 |
| `shipments(seller_id, created_at)` | 판매자 주문 관리 목록, 판매자 통계(판매자 + 기간 범위 스캔) |
| `shipments(seller_id, status, created_at)` *(V5)* | 판매자 대시보드 "처리할 배송 수", 상태별 주문 관리 목록 |
| `likes(user_id, created_at)` | 내 좋아요 최신순 |

### 5.2 처음 설계(V1)의 문제와 개선(V3)

처음에는 `(category_id, status, created_at)`, `(status, price)`로 설계했습니다. 상품 5만 건으로 측정하니 메인 화면 쿼리가 **풀스캔 + filesort**였습니다.

1. **status는 선택도가 낮다.** 공개 상품이 약 95%라 거의 걸러지지 않는다.
2. **`status IN (2개 값)`은 범위 조건이다.** 복합 인덱스에서 범위 조건 뒤의 컬럼은 정렬 순서를 이어 쓸 수 없다.

**개선 원칙: 등호 조건 컬럼 → 정렬 컬럼. 선택도가 낮은 status는 인덱스에서 빼고 읽으면서 거른다.** InnoDB 보조 인덱스는 끝에 PK를 포함하므로 `(created_at)`은 실제로 `(created_at, id)`이고, 정렬 보조키 방향을 주 키와 맞추면 인덱스 순서만으로 정렬됩니다.

반대로 **승인 대기 목록**은 PENDING이 소수라서 status가 잘 걸러집니다. 그래서 V4에서 `(status, created_at)`을 추가했습니다. 같은 컬럼이라도 값 분포에 따라 인덱스 선두에 둘지가 달라집니다.

### 5.3 측정 결과 (MySQL 8.0, 상품 50,000건, V3 시점)

| 쿼리 | V1 | V3 | 읽은 행 | 실제 시간 |
| --- | --- | --- | --- | --- |
| 전체 최신순 | 풀스캔 + filesort | `idx_products_created` 역순 스캔 | 50,000 → **21** | 175 ms → **0.19 ms** |
| 전체 가격 낮은순 | 풀스캔 + filesort | `idx_products_price` 정순 스캔 | 50,000 → **20** | 185 ms → **0.13 ms** |
| 소분류 최신순 | range + filesort | `idx_products_category_created` 역순 | 2,500 → **27** | 28.7 ms → **0.20 ms** |

### 5.4 V4 쿼리 측정 (MySQL 8.0, 상품 50,000 · 주문 50,000 · 판매자 20, 2026-11-11)

V4 스키마에 대량 데이터를 넣고 `EXPLAIN ANALYZE`로 실제 실행을 확인했습니다(측정 후 삭제).

| 쿼리 | 실행 계획 | 읽은 행 | 실제 시간 |
| --- | --- | --- | --- |
| 홈: 공개 상품, 먼저 등록한 순 | `idx_products_created` **정방향** 스캔, 20건 찾으면 멈춤 | 20 | **0.16 ms** |
| 소분류 목록 | `idx_products_category_created` | 약 20 | **0.31 ms** |
| 대분류 목록 (소분류 5개 포함) | `idx_products_created` 정방향 + 카테고리 필터 | 52 | **0.24 ms** |
| 관리자 승인 대기 목록 | `idx_products_status_created` (PENDING 3%) | 20 | **0.36 ms** |
| 목록의 총 재고 (상품 20개) | `uk_product_options_combination` IN 조회 | 20 | **0.20 ms** |
| 내 주문 목록 | `idx_orders_user_created` 역순, **커버링** | 20 | **0.03 ms** |
| 판매자 배송 묶음 목록 | `idx_shipments_seller_created` 역순, **커버링** | 20 | **0.11 ms** |
| 판매자 내 상품 (상품 2,500개인 판매자) | 브랜드별 `uk_products_brand_code` 조회 → 정렬 | 2,500 | **22 ms** |
| 키워드 검색 `LIKE '%…%'` | `idx_products_created` 전체 스캔 + 필터 | 50,000 | **530 ms** |

- **판매자 내 상품(22ms)은 정규화의 대가입니다.** V4에서 `products.seller_id`를 지웠기 때문에(이행 종속 제거) 판매자의 상품은 브랜드를 거쳐 찾고, 여러 브랜드에 걸친 최신순은 한꺼번에 정렬합니다. 판매자당 상품 2,500개에서 22ms라 판매자 센터 화면에는 충분합니다. `seller_id`를 다시 넣으면 빨라지지만, 상품과 브랜드의 판매자가 어긋나지 않게 막는 제약(V3의 복합 FK)이 다시 필요해집니다. 그래서 넣지 않았습니다.
- **키워드 검색(530ms)은 알려진 한계입니다.** 앞에 `%`가 붙은 LIKE는 B-Tree 인덱스를 못 씁니다. 게다가 상품명·브랜드명·품번이 두 테이블에 걸쳐 있어 FULLTEXT 인덱스 하나로도 해결되지 않습니다. 시연 규모(상품 수십 개)에서는 문제없고, 커지면 검색용 컬럼과 ngram FULLTEXT를 2차 과제로 검토합니다.
- 시연 데이터(상품 29개)처럼 테이블이 아주 작으면 옵티마이저가 인덱스 대신 풀스캔을 고릅니다. 그 편이 더 싸기 때문이고 정상입니다.

### 5.5 통계 쿼리 측정과 V5 인덱스 (2026-11-11)

같은 대량 데이터(주문 5만, 주문 라인 6.7만, 판매자 20명)로 통계 쿼리를 측정했습니다. 집계 기준은 "취소만 제외, 주문일 기준"입니다(팀 확정 전 제안).

| 쿼리 | 실행 계획 | 실제 시간 |
| --- | --- | --- |
| 판매자 일별 30일 | `idx_shipments_seller_created` **범위 스캔 하나로 판매자 + 기간**을 좁힌 뒤 주문 라인 조인 | **7 ms** |
| 판매자 상품 순위 30일 | 위와 같음 → 옵션 → 상품 PK 조인, 상위 10개 | **20 ms** |
| 판매자 월별 12개월 | 위와 같음 (판매자의 1년치 2,300건) | 103 ms |
| 플랫폼 일별 30일 | `idx_orders_created` 범위 → 배송 묶음 → 라인 | 134 ms |
| 플랫폼 월별 12개월 | 위와 같음, 1년치 주문 5만 건 전부 | **2.2 s** |
| 처리할 배송 수 (V4) | 판매자의 배송 2,500건을 모두 읽고 상태로 거름 | 10.7 ms |
| 처리할 배송 수 (**V5**) | `idx_shipments_seller_status_created` **커버링** 범위 스캔, 진행 중 30건만 | **0.34 ms** |

- **판매자 통계는 `shipments.created_at`을 기준으로 씁니다.** 배송 묶음은 주문과 같은 트랜잭션에서 만들어져 시각이 같습니다. 그래서 `orders`를 조인하지 않고 `(seller_id, created_at)` 인덱스 하나로 "이 판매자의 이 기간"을 바로 찾습니다.
- **V5 `(seller_id, status, created_at)`**: 상태를 인덱스 안에서 거르게 되어, 판매자 대시보드가 진행 중인 배송만 읽습니다. BE-2가 만들 "상태별 주문 관리 목록(최신순)"도 같은 인덱스를 커버링으로 씁니다(0.44 ms).
- **플랫폼 1년치 월별(2.2초)은 실시간 집계의 한계입니다.** 매번 1년치 주문 라인을 전부 조인해서 더합니다. 시연 규모(주문 수백 건)에서는 문제없고, 데이터가 커지면 일별 집계 테이블 `daily_sales(date, seller_id, quantity, revenue)`를 두고 그 테이블을 GROUP BY 하는 것이 다음 단계입니다(2차 과제).
- 기간은 `[from 00:00, to+1일 00:00)` 반열린 구간으로 조회합니다. `BETWEEN`은 끝날 23:59:59.xxx 경계를 놓치기 쉽습니다. 이 경계는 `StatsQueryTest`에서 23:59:59.999 주문과 다음 날 0시 주문으로 확인합니다.

### 5.6 N+1 확인

`GET /api/products` 한 번에 쿼리 3개입니다: ① products + brands + categories JOIN ② count ③ `product_options WHERE product_id IN (...) GROUP BY product_id`(총 재고). 상세는 상품 1번 + 옵션(색상·사이즈 fetch join) 1번입니다. 상품 수와 상관없이 일정합니다.

---

## 6. 변경 이력과 남은 작업

### 6.1 변경 이력

| 날짜 | 변경 | 이유 |
| --- | --- | --- |
| 11-09 | 상품 목록 인덱스 재설계 (V3) | 5만 건 EXPLAIN에서 풀스캔 + filesort 발견 |
| 11-09 | Docker 구성 (`docker-compose.yml`, `Dockerfile`) | 팀원 환경 통일, 다른 프로젝트 DB와 분리 |
| 11-10 | **패션몰 재설계 (V4)** — 회의 결정 반영 | 아래 표 |
| 11-11 | 좋아요 API (트랙 E) | 회의 결정: 내가 좋아요 누른 상품 목록 |
| 11-11 | 시연 주문 90일치 `R__demo_orders.sql`, 정합성 검사 15종 `scripts/check-integrity.sql` (트랙 B) | 통계 시연, 재고·주문 정합성 확인 |
| 11-11 | 판매 통계 API + **V5** `shipments(seller_id, status, created_at)` (트랙 I) | 통계 쿼리 EXPLAIN 결과 (5.5) |
| 11-17 | AWS 배포 구성 (`docker-compose.prod.yml`, 7장) | 서버 배포 |
| 11-17 | 계정 관리 + **V6** `users.login_session_id` (트랙 C, 8장) | 팀 결정: 진행 중 주문 보호, 본인 탈퇴, 마이페이지, 동시 로그인 방지 |
| 11-19 | 모의 결제 + **V7** `payments`, `payment_refunds`, 정합성 규칙 16~21 (트랙 H, 9장) | 팀 결정: "결제 버튼만 누르면 완료"인 시나리오를 실제 쇼핑몰 흐름으로 |
| 11-20 | 판매자 취소·브랜드 삭제 **V8**, 브랜드 + 첫 상품, 브랜드별 매출, 목록 옵션 (10장) | 프론트 점검: 브라우저에만 저장하던 기능을 서버로 |

**V4 변경 내용 (2026-11-10 회의)**

| 회의 결정 | DB 변경 |
| --- | --- |
| 가입 때 일반회원/판매자 선택, 판매자 승인 없음 | `sellers.status / reject_reason / approved_at` 삭제 |
| 관리자는 브랜드 승인·상품 승인·유저 관리 | `brands.reject_reason`, `products.status`에 PENDING·REJECTED, 관리자 카테고리 관리 제거 |
| 유저 삭제 = 탈퇴 처리, 정지 유지 | `users.status`에 WITHDRAWN, `withdrawn_at` |
| 모든 상품은 브랜드에서 등록 | `products.brand_id` NOT NULL, `products.seller_id` 삭제 |
| 상품번호(품번), 성별, 시즌(24FW), 출고 예정일 | `product_code`, `gender`, `season`, `ship_days` |
| 색상·사이즈 여러 개, 미리 정한 목록, 옵션 단위 재고 | `colors`, `sizes`, `product_options` 추가, `inventory` 삭제 |
| 정보 수정 시 재승인, 삭제는 승인 불필요 | 상태 PENDING / DELETED |
| 사진 업로드 (서버 폴더) | `image_url` 경로 저장, `/api/seller/images` |
| 패션 전용, 카테고리 고정 | 카테고리 교체 (대분류 8, 소분류 28), `size_group` |
| 장바구니 체크 상태 유지 | `cart_items.selected`, 옵션 단위 |
| 좋아요 | `likes` |
| 판매자별 배송 상태·취소, 구매자 구매확정 | `shipments` 추가, `orders.status` 삭제 |
| 회원제 (로그인해야 이용) | 상품 조회도 로그인 필요 (DB 변경 없음) |
| 홈은 먼저 등록한 순 | 기본 정렬 `oldest` (DB 변경 없음) |

### 6.2 검증 결과 (2026-11-10)

| 항목 | 결과 |
| --- | --- |
| 자동 테스트 | 55개 통과 (DB 제약 18, 동시 가입 1, 보안 10, 상품 서비스 15, 회원 서비스 8, JWT 3) |
| 마이그레이션 | V3 상태의 기존 DB에 V4 적용 확인 (`shop`, `shop_test`) |
| API 시나리오 | 50개 통과 — 회원제, 판매자 가입, 브랜드 승인, 상품 등록→승인→노출, 수정→재승인, 거절·차단, 검색(상품명·브랜드명·품번), 이미지 업로드, 정지·탈퇴 |
| 데이터 정합성 | 옵션 재고 ≠ 마지막 이력 0건, 옵션 없는 상품 0건, 사이즈 체계 불일치 0건, role ≠ 판매자 행 0건 |

### 6.3 남은 작업

트랙별 진행 상황은 맨 위 [작업 현황](#작업-현황-트랙별)에서 관리합니다. 2차 과제: 상세 이미지 여러 장, 옵션별 추가 가격, ngram FULLTEXT 검색, `daily_sales` 집계 테이블.

---

## 7. AWS 배포 가이드 (EC2 + Docker Compose)

로컬에서 쓰던 Docker 구성을 EC2 서버 한 대에 그대로 올립니다. 서버에서는 `docker-compose.prod.yml`을 덧씌워 실행합니다. 이 파일은 비밀값을 `.env`에 넣지 않으면 **실행 자체를 거부**하고, DB 포트를 밖에 열지 않으며, 앱을 80번 포트로 엽니다. 서버가 재부팅되면 컨테이너도 자동으로 다시 뜹니다.

```
인터넷 ──80──▶ [EC2] shop-app (Spring Boot) ──내부 네트워크──▶ shop-mysql (MySQL 8.0, 밖에서 접속 불가)
                       │                                         │
                  shop-uploads 볼륨 (업로드 이미지)           shop-mysql-data 볼륨 (DB)
```

> 이 구성은 2026-11-17에 이 PC에서 서버와 같은 방식(prod 설정, 임시 비밀값)으로 실행해 확인했습니다. 관리자 기본 비밀번호 차단, CORS 허용 주소, 재배포 후 업로드 이미지 유지, DB 포트 비노출을 확인했습니다. 실제 AWS 콘솔 화면은 바뀔 수 있으니 메뉴 이름이 다르면 비슷한 항목을 찾으세요.

### 7.1 EC2 인스턴스 만들기 (AWS 콘솔)

| 항목 | 값 | 이유 |
| --- | --- | --- |
| 리전 | 아시아 태평양(서울) `ap-northeast-2` | 가까운 곳 |
| AMI | Ubuntu Server 24.04 LTS | 아래 명령이 Ubuntu 기준 |
| 인스턴스 유형 | **t3.small (메모리 2GB) 권장** | MySQL + Spring Boot + 서버에서 Gradle 빌드. t3.micro(1GB, 프리 티어)도 되지만 7.3의 스왑 설정이 꼭 필요하고 빌드가 느림 |
| 키 페어 | 새로 만들어 `.pem` 파일 보관 | SSH 접속용. 잃어버리면 접속 불가 |
| 스토리지 | 20GB gp3 | Docker 이미지·DB·업로드 이미지 |
| 보안 그룹 (인바운드) | **SSH 22: 내 IP만**, **HTTP 80: 0.0.0.0/0** | 3306·3308·8080은 열지 않는다 |

만든 뒤 **탄력적 IP**를 할당해 인스턴스에 연결합니다. 그래야 인스턴스를 껐다 켜도 주소가 바뀌지 않습니다. 아래에서 `<서버IP>`는 이 주소입니다.

### 7.2 접속과 Docker 설치

```bash
# 내 PC에서 (Windows는 PowerShell, .pem 파일이 있는 폴더에서)
ssh -i shop-key.pem ubuntu@<서버IP>

# 서버에서: Docker + Compose 설치 (공식 설치 스크립트)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
exit                                   # 다시 접속해야 docker 그룹이 적용된다
ssh -i shop-key.pem ubuntu@<서버IP>
docker compose version                 # 2.24 이상이어야 한다 (prod 설정의 !reset·!override 문법)
```

### 7.3 (t3.micro일 때만) 스왑 2GB 추가

메모리 1GB로는 Gradle 빌드 중 메모리가 모자라 빌드가 멈출 수 있습니다.

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab   # 재부팅 후에도 유지
free -h                                                       # Swap 2.0Gi 확인
```

### 7.4 코드 받기 (비공개 저장소 → 읽기 전용 배포 키)

저장소가 비공개라서 서버가 읽기만 할 수 있는 키를 하나 만들어 GitHub에 등록합니다. 개인 계정 비밀번호나 토큰을 서버에 두지 않아도 됩니다.

```bash
ssh-keygen -t ed25519 -C "shop-ec2-deploy" -f ~/.ssh/github_deploy -N ""
cat ~/.ssh/github_deploy.pub     # 출력된 한 줄을 복사
```

GitHub 저장소 → **Settings → Deploy keys → Add deploy key**에 붙여 넣습니다(Allow write access는 **체크하지 않음**). 저장소 admin 권한이 있어야 등록할 수 있습니다.

```bash
cat >> ~/.ssh/config <<'CFG'
Host github.com
  IdentityFile ~/.ssh/github_deploy
  IdentitiesOnly yes
CFG
git clone git@github.com:SSU-Database-Project/Backend.git
cd Backend
```

### 7.5 비밀값 설정 (.env)

```bash
cp .env.example .env
openssl rand -base64 24    # DB_PASSWORD, MYSQL_ROOT_PASSWORD, ADMIN_PASSWORD 용으로 3번 실행해 하나씩 사용
openssl rand -base64 48    # JWT_SECRET 용
nano .env                  # 값 채우기 (저장: Ctrl+O, Enter, 종료: Ctrl+X)
chmod 600 .env             # 나만 읽을 수 있게
```

| 변수 | 설명 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD` | DB 비밀번호. **DB를 처음 만들 때만 적용**된다(7.9 참고) |
| `JWT_SECRET` | 로그인 토큰 서명 키. 바꾸면 기존 로그인은 모두 풀린다 |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | 앱이 처음 뜰 때 만드는 관리자 계정. README의 개발용 비밀번호(`admin1234!`)를 쓰지 않는다 |
| `CORS_ALLOWED_ORIGINS` | API를 부를 **프론트 주소** (예: `http://<서버IP>:5173`, `https://shop.example.com`). 여러 개면 쉼표로. 주소가 한 글자라도 다르면 브라우저가 막는다 |
| `SPRING_PROFILES_ACTIVE` | `local` = 시연 데이터(판매자·상품 27개·주문 300건) 포함, `prod` = 빈 상태로 시작 |

`.env`는 `.gitignore`에 들어 있어 git에 올라가지 않습니다. 비밀값은 팀 채팅방이 아니라 서버의 이 파일에만 둡니다.

### 7.6 실행과 확인

```bash
./scripts/deploy.sh --no-pull     # 첫 실행: 이미지 빌드(몇 분) → 실행 → 응답 확인
```

- 서버에서 `[deploy] 완료`가 나오면 내 PC 브라우저에서 `http://<서버IP>/swagger-ui.html`을 엽니다.
- `.env`의 관리자 계정으로 로그인되는지 확인합니다(`POST /api/auth/login`).
- 첫 실행 때 DB 테이블과(local이면) 시연 데이터는 Flyway가 자동으로 만듭니다.

### 7.7 업데이트 (main에 새 코드가 머지된 뒤)

```bash
cd ~/Backend && ./scripts/deploy.sh
```

`git pull` → 이미지 다시 빌드 → 재시작 → 응답 확인까지 한 번에 합니다. DB 데이터와 업로드 이미지는 볼륨에 있어 그대로 남고, 새 마이그레이션(V6 …)이 있으면 앱이 뜰 때 자동으로 적용됩니다.

### 7.8 자주 쓰는 명령

```bash
C="docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile app"
$C ps                         # 상태
$C logs -f --tail 100 app     # 앱 로그 (Ctrl+C로 빠져나옴)
$C restart app                # 앱만 재시작
$C down                       # 전체 중지 (데이터는 남음)

# DB 백업 / 정합성 검사
docker exec shop-mysql sh -c 'mysqldump -ushop -p"$MYSQL_PASSWORD" shop' > backup-$(date +%F).sql
docker exec -i shop-mysql sh -c 'mysql --default-character-set=utf8mb4 -ushop -p"$MYSQL_PASSWORD" shop' < scripts/check-integrity.sql
```

### 7.9 주의할 점

- **`down -v`는 DB와 업로드 이미지를 모두 지웁니다.** 시연 직전에 처음 상태로 되돌릴 때만 씁니다.
- **DB 비밀번호를 나중에 바꿀 때**: `.env`만 바꾸면 안 됩니다. MySQL은 볼륨을 처음 만들 때만 비밀번호를 설정하므로, 앱은 새 비밀번호로 접속하는데 DB는 옛 비밀번호라 앱이 뜨지 않습니다. 데이터를 지워도 되면 `$C down -v` 후 다시 실행하고, 지키려면 DB 안에서 먼저 `ALTER USER 'shop'@'%' IDENTIFIED BY '새비밀번호';`를 실행한 뒤 `.env`를 바꿉니다.
- **프론트를 HTTPS로 배포하면**(Vercel, Amplify, CloudFront 등) 브라우저가 HTTP API 호출을 막습니다(mixed content). 그때는 백엔드도 HTTPS가 필요합니다. 도메인을 연결하고 Nginx나 Caddy로 인증서(Let's Encrypt)를 붙이거나, 프론트와 백엔드를 같은 주소 아래에 둡니다. 프론트가 HTTP면 지금 구성 그대로 됩니다.
- **비용**: 쓰지 않을 때는 인스턴스를 중지합니다. 퍼블릭 IPv4 주소에도 시간당 요금이 붙을 수 있으니, 프로젝트가 끝나면 인스턴스를 종료하고 탄력적 IP를 해제합니다.
- **보안 그룹**에 3306·3308·8080을 열지 않습니다. DB는 서버 밖에서 접속할 필요가 없습니다.

### 7.10 문제가 생기면

| 증상 | 확인할 것 |
| --- | --- |
| `required variable ... is missing a value` | `.env`에 그 변수가 비어 있음 (의도된 동작) |
| 브라우저에서 접속이 안 됨 | 보안 그룹의 HTTP 80 인바운드, `$C ps`에서 `0.0.0.0:80->8080` |
| 빌드 중 멈추거나 `Killed` | 메모리 부족 → 7.3 스왑, 또는 t3.small |
| 앱이 계속 재시작 | `$C logs app`. `Access denied`면 7.9 DB 비밀번호 |
| 프론트에서 CORS 오류 | `CORS_ALLOWED_ORIGINS`가 브라우저 주소창의 프론트 주소(스킴·포트 포함)와 정확히 같은지. 바꾼 뒤 `$C up -d` |
| `!override` / `!reset` 문법 오류 | `docker compose version`이 2.24 미만 → 7.2의 공식 스크립트로 다시 설치 |

---

## 8. 계정 관리 규칙 (2026-11-17)

팀에서 정한 내용입니다. 요구사항 원문은 [`docs/명세서.md`](docs/명세서.md)의 "팀 결정 (2026-11-17)"에 있습니다.
**진행 중인 주문**은 배송 묶음(shipments)이 주문완료·배송준비·배송중인 것입니다. 배송완료·구매확정·취소는 끝난 주문으로 봅니다.

### 8.1 규칙

| 상황 | 동작 |
| --- | --- |
| 관리자가 구매자·판매자를 정지·탈퇴 | 진행 중인 주문이 있으면 거절 → 409 `HAS_IN_PROGRESS_ORDERS` (message에 건수). 정지 해제는 항상 가능 |
| 정지된 판매자의 상품 | 목록에는 보이지만 주문은 거절 (`PRODUCT_NOT_PURCHASABLE`). 해제하면 다시 주문 가능 |
| 구매자 본인 탈퇴 | 진행 중인 주문이 있어도 확인 후 탈퇴. 주문은 남고 배송은 판매자가 계속 진행 |
| 판매자 본인 탈퇴 | 진행 중인 주문이 있으면 거절. 탈퇴하면 상품 모두 판매 종료 |
| 본인 탈퇴 확인 | 비밀번호를 다시 입력 (틀리면 400 `PASSWORD_MISMATCH`) |
| 마이페이지 수정 | 모든 회원: 이름·전화번호·비밀번호. 판매자: 상호·대표자명 (사업자번호는 고정) |
| 비밀번호 변경 | 바꾸면 지금 로그인도 끊김 → 다음 요청 401 `LOGIN_EXPIRED`, 새 비밀번호로 다시 로그인 |
| 동시 로그인 | 다른 곳에서 새로 로그인하면 먼저 로그인한 쪽의 토큰이 끊김 → 401 `LOGGED_IN_ELSEWHERE` |

### 8.2 API

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/api/users/me` | 계정 정보: email(계정명)·name·phone·role·status·createdAt, 판매자면 `seller`(상호·대표자명·사업자번호) |
| PATCH | `/api/users/me` | `{"name", "phone", "seller": {"companyName", "representativeName"}}` — seller는 판매자만, 판매자는 필수. phone을 비우면 삭제 |
| PATCH | `/api/users/me/password` | `{"currentPassword", "newPassword"}` (새 비밀번호 8~64자) |
| GET | `/api/users/me/withdrawal` | 탈퇴 버튼을 누르면 먼저 호출 → `{"canWithdraw", "inProgressOrderCount", "message"}`. message를 팝업에 그대로 표시 |
| POST | `/api/users/me/withdrawal` | `{"password"}` → 탈퇴. 성공하면 토큰을 지우고 첫 화면으로 |

### 8.3 프론트에서 처리할 것

- 모든 API 응답이 401이고 `error.code`가 `LOGGED_IN_ELSEWHERE`면 "다른 곳에서 로그인되어 로그아웃되었습니다" 팝업 → 토큰 삭제 → 로그인 화면. `LOGIN_EXPIRED`도 토큰 삭제 → 로그인 화면 (axios 응답 인터셉터 한 곳에서 처리하면 됩니다).
- 관리자 회원 관리에서 정지·탈퇴가 409 `HAS_IN_PROGRESS_ORDERS`면 `error.message`를 팝업으로.
- 탈퇴: `GET /api/users/me/withdrawal` → `canWithdraw`가 true면 message + 비밀번호 입력 칸 + 탈퇴 버튼, false면 message만.
- 주문서 우편번호 칸에 "숫자 5자리" 안내 문구 (서버는 숫자 5자리만 받습니다).

### 8.4 동시에 들어온 요청 처리

관리자가 정지를 누르는 순간 그 회원이 주문을 하면, 서로 먼저 확인한 값만 믿고 둘 다 성공할 수 있습니다. 그러면 "진행 중인 주문이 있는 정지 회원"이 생깁니다. 이를 회원 행 잠금으로 막습니다 (`AccountLifecycle`, `OrderService.lockAccounts`).

| 처리 | 잠그는 순서 |
| --- | --- |
| 주문 | 구매자·판매자 회원 행 `FOR SHARE`(id 오름차순) → 상품 `FOR SHARE` → 옵션 `FOR UPDATE` |
| 관리자 정지 | 대상 회원 행 `FOR UPDATE` → 진행 중 주문 수를 잠금 읽기(`FOR SHARE`)로 셈 |
| 판매자 탈퇴 | 판매자 회원 행 `FOR UPDATE` → 상품을 id 오름차순으로 판매 종료 → 진행 중 주문 수를 잠금 읽기로 셈 (있으면 판매 종료까지 롤백) |

- 주문이 먼저 회원 행을 잠갔으면 정지는 그 주문이 끝날 때까지 기다렸다가 그 주문까지 세어 거절합니다. 정지가 먼저면 주문이 기다렸다가 바뀐 상태를 보고 거절됩니다.
- 진행 중 주문 수를 일반 SELECT로 세면, MySQL은 트랜잭션이 처음 읽은 시점의 값을 보여 줍니다. 그러면 기다리는 사이 커밋된 주문을 못 볼 수 있어서 잠금 읽기로 셉니다.
- 모두 회원 → 상품 순서로 잠그므로 서로 기다리다 멈추지(데드락) 않습니다.
- `AccountRulesDbTest`가 주문과 정지를 같은 순간에 출발시켜 10라운드씩 확인합니다. 결과는 항상 "주문 성공 + 정지 거절" 또는 "정지 성공 + 주문 거절" 중 하나였습니다. `SellerWithdrawDbTest`는 주문과 판매자 탈퇴의 경합을 확인합니다.

### 8.5 동시 로그인 방지 (V6)

- `users.login_session_id`(CHAR(36))에 지금 유효한 로그인 하나의 UUID를 저장하고, 같은 값을 JWT의 `sid` 클레임에 넣습니다.
- JWT 필터는 원래 요청마다 회원을 PK로 한 번 읽고 있었습니다(정지 즉시 반영용). 그 행으로 `sid`도 비교하므로 **추가 쿼리는 없습니다**.
- 비밀번호 변경·탈퇴 때는 값을 NULL로 지워 모든 토큰을 끊습니다. V6 전에 발급된 토큰(`sid` 없음)은 401이 되므로 배포 후 한 번 다시 로그인하면 됩니다.

---

## 9. 모의 결제 (2026-11-19)

"결제 버튼만 누르면 완료"인 시나리오를 실제 쇼핑몰 흐름에 가깝게 바꿨습니다. 실제 돈은 오가지 않고, 서버 안의 **모의 결제사**(`MockPaymentGateway`)가 승인합니다. 실제 PG를 붙일 때는 `PaymentGateway` 구현만 바꾸면 됩니다.

### 9.1 흐름과 규칙

| 단계 | 내용 |
| --- | --- |
| 주문 + 결제 | `POST /api/orders` 한 번에 주문 생성 → 재고 차감 → 장바구니 정리 → **결제 승인**까지 한 트랜잭션 |
| 금액 | 클라이언트는 금액을 보내지 않는다. 서버가 잠금 후 다시 읽은 가격으로 계산한 주문 총액으로 승인 → 화면에서 금액 조작 불가 |
| 결제수단 | `CARD`(카드번호 16자리, 끝 4자리만 저장) · `BANK_TRANSFER` · `EASY_PAY` |
| 거절 (시연용) | 카드번호가 `0000`으로 끝나면 카드사 거절, 결제 금액이 100만 원을 넘으면 한도 초과 → 402 `PAYMENT_DECLINED`, **주문·재고·장바구니 모두 그대로** |
| 취소 = 부분 환불 | 판매자별 배송 묶음을 취소하면 그 묶음 금액만큼 환불 기록. 같은 묶음은 한 번만 환불(`uk_payment_refunds_shipment` + 조건부 UPDATE) |
| 예전 주문 | V7 이전 주문은 결제가 없다(`payment: null`). 시연 주문 300건은 `R__demo_payments.sql`이 결제·환불을 채운다 |

### 9.2 DB (V7)

| 테이블 | 관계 | 주요 컬럼 · 제약 |
| --- | --- | --- |
| `payments` | orders 1 : 1 (`uk_payments_order`) | method, amount, card_last4, approval_no(UK), paid_at · CHECK: 수단 값, 금액 ≥ 0, 카드 결제 ⇔ 끝 4자리 있음(숫자 4자리) |
| `payment_refunds` | payments 1 : N, shipments 1 : 0..1 (`uk_payment_refunds_shipment`) | amount, refunded_at · CHECK: 금액 ≥ 0 |

- 결제 상태(부분 환불·전액 환불)는 저장하지 않고 환불 합계로 계산합니다. 같은 사실을 두 곳에 두지 않기 위해서입니다(3NF).
- 정합성 규칙 16~21(`scripts/check-integrity.sql`): 결제 금액 = 주문 총액, 결제 시각 = 주문 시각, 취소된 묶음마다 환불 1건, 환불 금액 = 묶음 라인 합계, 환불은 같은 주문의 결제, 환불 합계 ≤ 결제 금액.
- 내 주문 목록은 결제·환불까지 쿼리 5번으로 끝납니다(주문 수와 무관).

### 9.3 프론트 연동 (FE 팀 전달용)

**주문 요청에 `payment`가 필수입니다.** 없으면 400입니다.

```json
POST /api/orders
{
  "receiverName": "홍길동", "receiverPhone": "010-1234-5678",
  "zipCode": "06978", "address": "서울특별시 동작구 상도로 369", "addressDetail": "101동",
  "payment": { "method": "CARD", "cardNumber": "1234-5678-9012-3456" }
}
```
- `method`: `CARD` / `BANK_TRANSFER` / `EASY_PAY`. `cardNumber`는 카드일 때만(16자리, 하이픈·공백 허용), 다른 수단이면 보내지 않습니다.
- 응답과 `GET /api/orders`, `GET /api/orders/{id}`의 각 주문에 `payment`가 붙습니다.
  ```json
  "payment": { "paymentId": 1, "method": "CARD", "amount": 97900, "cardNumberMasked": "****-****-****-3456",
               "approvalNo": "MOCK-20261119-52F5BC8C", "paidAt": "...", "refundedAmount": 45900, "balance": 52000,
               "refunds": [ { "shipmentId": 519, "amount": 45900, "refundedAt": "..." } ] }
  ```
- 화면 흐름 제안: 주문서 → 결제수단 선택(카드면 카드번호 입력) → "결제 처리 중" → 완료 화면(주문번호·승인번호·결제 금액·판매자별 배송 묶음). 주문내역에는 결제수단·결제 금액·환불 금액·남은 금액을 표시합니다.
- 402 `PAYMENT_DECLINED`면 `error.message`(예: "카드사에서 승인을 거절했습니다.")를 보여 주고 주문서에 머뭅니다. 장바구니는 그대로 남아 있습니다.
- 시연 팁: 거절은 카드번호 `…-0000`, 한도 초과는 100만 원 넘게 담기.

---

## 10. 판매자·관리자 기능 보강 (2026-11-20)

프론트 점검에서 **백엔드에 없는 기능을 브라우저 저장소(localStorage)로 흉내 낸 곳** 다섯 군데가 나왔습니다. 서버 기능으로 옮겼습니다.

| 점검 결과 | 백엔드 변경 | 프론트가 할 일 |
| --- | --- | --- |
| 판매자 취소·구매확정이 브라우저에만 저장, 취소 때 재고를 별도 입고(IN) → 이중 복원 위험 | **판매자 취소 API**: 배송 전(주문완료·배송준비)까지, 사유 필수, 재고 복원 + 부분 환불, 취소자·사유 기록 | 판매자 취소를 새 API로. **재고 입고 호출과 `seam_shipment_overrides_v1` 제거**. 구매확정 버튼은 판매자 화면에서 제거(구매자 기능) |
| 브랜드 삭제가 숨기기뿐, 관리자 상품 삭제는 오류 무시 | **브랜드 삭제 API**(판매자 본인·관리자) | 새 API로. `seam_deleted_brand_ids_v1`·`seam_deleted_product_ids_v1` 제거. 관리자 상품 "삭제"는 **차단(BLOCKED)** 사용, 오류는 화면에 표시 |
| 관리자 브랜드별 매출을 상품 수·가격 비율로 임의 배분 | **브랜드별 판매 집계 API** | 임의 배분 코드 제거, 새 API 값 그대로 표시 |
| 판매자·관리자 목록이 100건까지만, 상품마다 상세 재호출 | 판매자·관리자 상품 목록 응답에 **옵션(색상·사이즈·재고) 포함** | 목록 API의 `page`를 늘려 마지막 페이지까지 조회(`size` 최대 100). 상품마다 상세 호출 제거 |
| 승인 대기 브랜드의 첫 상품을 브라우저에 임시 저장(판매자 토큰까지 저장) | **브랜드 + 첫 상품 한 번에 등록 API** (한 트랜잭션) | 새 API로. `seam_pending_brand_products_v1`과 토큰 저장 제거 |

### 10.1 API

| Method | Endpoint | 권한 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/seller/shipments/{id}/cancel` | SELLER | 본문 `{"reason": "품절로 출고 불가"}` (필수). 주문완료·배송준비에서만, 그 뒤면 409 `INVALID_ORDER_STATUS` |
| POST | `/api/orders/{orderId}/shipments/{id}/cancel` | USER | (기존) 본문 `{"reason": "…"}` 선택 |
| DELETE | `/api/seller/brands/{id}` | SELLER | 내 브랜드 삭제. 남의 브랜드 403 `BRAND_NOT_OWNED` |
| DELETE | `/api/admin/brands/{id}` | ADMIN | 브랜드 삭제 |
| POST | `/api/seller/brands/with-product` | SELLER | `{"brand": {...}, "product": {상품 등록 본문에서 brandId만 뺀 것}}` → 둘 다 승인 대기 |
| GET | `/api/admin/stats/brands?from=&to=` | ADMIN | 브랜드별 `quantity`·`revenue`·`orderCount`·`productCount`, 합계 = 플랫폼 합계 |
| GET | `/api/seller/products`, `/api/admin/products` | | (기존) 각 상품에 `options` 추가. 구매자 목록에는 없음 |

- 배송 묶음 응답(구매자·판매자)에 `canceledBy`(`BUYER`/`SELLER`)와 `cancelReason`이 추가됐습니다. 구매자 주문내역에 "판매자 취소: 사유"를 보여 줄 수 있습니다.
- 브랜드 삭제: 소속 상품은 판매 종료(DELETED). **그 브랜드 상품의 진행 중 주문이 있으면 409 `HAS_IN_PROGRESS_ORDERS`**. 이름은 `deleted_{id}_원래이름`으로 바뀌어 같은 이름을 다시 쓸 수 있습니다. 판매자 브랜드 목록과 관리자 목록(상태 미지정)에서 빠집니다.
- 브랜드 + 첫 상품: 관리자가 **브랜드를 먼저 승인**해야 상품을 승인할 수 있습니다(먼저 하면 409 `BRAND_NOT_APPROVED`). 상품이 잘못되면 브랜드도 만들어지지 않습니다.

### 10.2 DB (V8)

- `shipments.canceled_by`(BUYER/SELLER), `cancel_reason`. CHECK: 취소 ⇔ 취소자 있음, 판매자 취소 ⇒ 사유 있음. V8 전 취소는 모두 구매자 취소라 BUYER로 채웠습니다.
- `brands.status`에 `DELETED`, `deleted_at` 추가. CHECK: DELETED ⇔ 삭제 시각 있음.
- 동시 요청: 브랜드 삭제는 브랜드 행 잠금 → 상품을 id 오름차순으로 판매 종료 → 진행 중 주문을 잠금 읽기로 셈(판매자 탈퇴와 같은 순서). 같은 브랜드에 상품 등록은 브랜드 행을 FOR SHARE로 읽어 삭제가 끝난 뒤 거절됩니다. 구매자·판매자가 동시에 취소해도 조건부 UPDATE라 한 번만 취소·환불됩니다.
