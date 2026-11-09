-- Docker MySQL 컨테이너 최초 실행 시 자동 실행된다 (docker-compose.yml 참고).
-- shop DB 와 shop 계정은 MYSQL_DATABASE / MYSQL_USER 환경변수로 이미 만들어진다.
-- 여기서는 DB 제약 테스트용 shop_test 만 추가한다.

CREATE DATABASE IF NOT EXISTS shop_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON shop_test.* TO 'shop'@'%';
FLUSH PRIVILEGES;
