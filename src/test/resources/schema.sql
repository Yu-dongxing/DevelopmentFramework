-- 测试环境 H2 内存数据库初始化脚本，用于在集成测试中自动创建测试需要的表
CREATE TABLE IF NOT EXISTS sys_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(100),
    user_role VARCHAR(255),
    ip VARCHAR(100),
    url VARCHAR(255),
    method VARCHAR(50),
    class_name VARCHAR(255),
    method_name VARCHAR(255),
    title VARCHAR(255),
    business_type VARCHAR(100),
    param TEXT,
    result TEXT,
    status INT,
    error_msg TEXT,
    take_time BIGINT,
    request_time TIMESTAMP,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);
