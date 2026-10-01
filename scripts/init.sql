-- =============================================================
-- Database Initialization Script
-- Runs automatically on first MySQL container startup
-- =============================================================

-- Create database if not exists (redundant with Spring but explicit for Docker)
CREATE DATABASE IF NOT EXISTS workout_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE workout_db;

-- Grant permissions to application user
GRANT ALL PRIVILEGES ON workout_db.* TO 'workout_user'@'%';
FLUSH PRIVILEGES;
