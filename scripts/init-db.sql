-- 로컬 개발용 DB/계정 생성 스크립트 (MySQL root 계정으로 1회 실행)
--   mysql -h 127.0.0.1 -P 3307 -u root -p < scripts/init-db.sql
-- 테이블은 애플리케이션 시작 시 Flyway가 만든다.

CREATE DATABASE IF NOT EXISTS shop      DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS shop_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'shop'@'%' IDENTIFIED BY 'shop1234';
GRANT ALL PRIVILEGES ON shop.*      TO 'shop'@'%';
GRANT ALL PRIVILEGES ON shop_test.* TO 'shop'@'%';
FLUSH PRIVILEGES;
