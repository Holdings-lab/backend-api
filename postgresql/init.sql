-- PostgreSQL 초기화 스크립트
-- 이 스크립트는 Docker 컨테이너 시작 시 자동으로 실행됩니다.

-- pwa_db 데이터베이스 존재 확인 후 생성
SELECT 'CREATE DATABASE pwa_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'pwa_db')\gexec

-- 기본 테이블들은 Spring JPA (Hibernate)에서 ddl-auto: update로 자동 생성됩니다.
-- 필요시 여기에 추가 초기화 스크립트를 작성하세요.

-- 예시: 사용자 테이블 확인
-- CREATE TABLE IF NOT EXISTS users (
--     id BIGSERIAL PRIMARY KEY,
--     username VARCHAR(255) UNIQUE NOT NULL,
--     nickname VARCHAR(255),
--     password VARCHAR(255) NOT NULL,
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
-- );
