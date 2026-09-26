# 多数据库适配说明

项目通过 `DB_URL`、`DB_DRIVER` 与可选的 `DB_DIALECT` 切换数据库。未设置 `DB_DIALECT` 时，系统根据 JDBC 数据库产品名称自动识别 MySQL、PostgreSQL、Oracle 和 H2。

## 驱动与部署

- MySQL、PostgreSQL、Oracle 与 H2 JDBC 驱动已由 Maven 在运行时引入。
- 达梦 JDBC 驱动由厂商分发，无法从公共 Maven 仓库稳定获取。达梦仅按 Oracle 兼容模式提供实验性适配：部署前必须确认兼容模式，将对应版本的 JDBC JAR 放入应用运行时 classpath，并显式设置 `DB_DIALECT=ORACLE`。
- 自动 DDL 使用当前连接的 schema；数据库账号需要具备建表、建索引、添加外键和写入注释的权限。
- 自动结构同步会比较列类型、默认值、注释、主键和自增属性。主键或自增属性不一致时会停止启动并要求人工迁移，避免静默破坏约束。
- Oracle 普通字符列迁移为 `CLOB` 时会通过临时列复制数据后替换原列；执行前仍建议备份数据，并确保不存在同名的 `*_LOB_TMP` 临时列。

## 推荐配置

```bash
# PostgreSQL
DB_DRIVER=org.postgresql.Driver
DB_URL=jdbc:postgresql://127.0.0.1:5432/dev

# Oracle
DB_DRIVER=oracle.jdbc.OracleDriver
DB_URL=jdbc:oracle:thin:@//127.0.0.1:1521/ORCLPDB1

# 达梦（驱动 JAR 由部署环境提供）
DB_DRIVER=dm.jdbc.driver.DmDriver
DB_URL=jdbc:dm://127.0.0.1:5236/DEV
DB_DIALECT=ORACLE
```

## 字段类型约束

- `Map`、`Collection` 等结构化字段统一映射为 `TEXT/CLOB`，通过 Jackson TypeHandler 读写 JSON 文本，避免 PostgreSQL 原生 JSON 与 JDBC 参数类型不匹配。
- 如业务必须使用 PostgreSQL `JSONB` 的索引和操作符，应为该字段单独实现基于 `PGobject` 的 PostgreSQL TypeHandler，并增加 PostgreSQL 集成测试。
