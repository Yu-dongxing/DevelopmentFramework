# Aegis-Boot (神盾 · 现代安全开发架构) - Project Context

## Project Overview
Aegis-Boot is a modern, high-performance full-stack development framework based on **Java 21** and **Spring Boot 4.x**. It is designed for extreme performance (using Virtual Threads), defensive programming, and agile development.

### Core Technologies
- **Runtime:** Java 21 (Virtual Threads enabled)
- **Framework:** Spring Boot 4.0.5
- **Security:** Sa-Token 1.45.0 (supports **Sa-Firewall**, JWT, Multi-account, Redis-backed sessions)
- **ORM:** MyBatis-Plus 3.5.16
- **Database:** MySQL 8.0+ (Automatic schema synchronization via Aegis DB Engine 2.0)
- **Caching:** Redis 6.x+
- **Logging:** Log4j2 with Lmax Disruptor for high-throughput asynchronous logging.
- **Utilities:** Fastjson2, Hutool 5.8.40.

### Key Architectural Components
1. **Aegis DB Engine 2.0 (Component-Based Code-First):** Automatically synchronizes database schema based on Java entity annotations using specialized components (`EntityScanner`, `TableMetadataParser`, `SqlGenerator`, `SchemaExecutor`).
2. **Aegis File Hub (Event-Driven):** Strategy-based file storage supporting `LOCAL` and `ALIYUN_OSS`. Decoupled via `FileUploadedEvent` and `FileUploadedListener`. Supports generic `metadata` (JSON).
3. **Jackson Precision Engine:** Automatically converts `Long` to `String` to prevent precision loss in JavaScript (controllable via `jackson.long-to-string`).
4. **Unified Architecture:**
    - `BaseEntity`: Standardizes `id` (Snowflake), `create_time`, and `update_time`.
    - `Result<T>`: Standardized API response wrapper.
    - `GlobalExceptionHandler`: Centralized error handling for Business, Security, and Firewall exceptions.

## Project Structure (Module-First)
- `top.yuxs.springbootdev.core`: Core infrastructure packages.
    - `.common`: Common base classes (`BaseEntity`, `Result`, `ResultCode`).
    - `.config`: Configuration for Sa-Token, WebMvc, Redis, MyBatis-Plus, and Jackson.
    - `.db`: Aegis DB Engine implementation (Components, Metadata, Config).
    - `.enums`: Business enums.
    - `.exception`: Global exception handling logic.
    - `.utils`: Helper tools (IpUtils, ProjectRenameTool).
- `top.yuxs.springbootdev.modules`: Functional business modules.
    - `.file`: File management module (Entity, Mapper, Service, Event, Listener).
    - `.system`: System-level features (Auth, Ip, etc.).

## Building and Running

### Prerequisites
- **JDK 21+**
- **MySQL 8.0+**
- **Redis 6.x+**

### Commands
- **Run Application:** `./mvnw spring-boot:run`
- **Build Package:** `./mvnw clean package -DskipTests`
- **Run Tests:** `./mvnw test`

## Development Conventions (系统开发与重构规范)

为了保证 Aegis-Boot 架构的极简度、高内聚性以及在大型团队下的可维护性，所有开发工作和代码重构必须严格遵循以下约定：

### 1. Coding Style & Naming (代码编写风格)
- **Entities (实体类)**：必须继承 `BaseEntity`，使用 `@TableName`、`@TableComment` 以及 Aegis DB 专属字段注解。统一放置于 `top.yuxs.springbootdev.modules.[module].entity`。
- **Mappers (持久层)**：统一放置于 `top.yuxs.springbootdev.modules.[module].mapper`。
- **Services (服务层)**：接口放置于 `top.yuxs.springbootdev.modules.[module].service`，实现类放置于 `.impl` 子包下。
- **Controllers (控制层)**：统一放置于 `top.yuxs.springbootdev.modules.[module].controller`。
- **ID Management (主键设计)**：统一使用 `Long` 声明 Snowflake 唯一 ID；系统底层自动通过 Jackson 序列化处理器（Precision Engine）将其转为 String 格式输出给 REST 客户端，防止前端 JavaScript 发生高位精度丢失。
- **Security (安全策略)**：利用 Sa-Token Firewall 实施入站请求的安全阻断与拦截，保护系统免受恶意请求 and RCE。
- **API Responses (API 返回规范)**：所有 Controller 接口必须使用统一返回实体 `Result<T>` 进行包装。
- **Generated Code Requirements (生成/编写代码规范)**：
  - 所有生成的代码必须符合当前项目的所有规范（包括通用类型的代码，非必要不允许在已经有了一个相同作用的方法时再次重新写一个方法）。
  - 代码必须有详细的中文注释（包括方法的 Javadoc 注释以及代码内部的行内注释），代码格式必须规范（严禁将完整方法、多条语句或复杂查询压缩写在单行）。
  - 代码中的日志输出必须以中文输出，禁止使用纯英文或其他语言（除了专有名词或特定单词可以保留英文，完整句子必须是中文）。
- **Dependency Import (依赖引入规范)**：
  - 严禁将外部依赖类名以全限定名（Fully Qualified Name）的形式直接写在执行代码中。
  - 所有外部依赖类（如 `LambdaQueryWrapper`）必须在 Java 文件头部的 `import` 区域中明确引入，代码中仅使用简写类名。
- **Entity and Automatic Schema Rules (实体类与自动建表规范)**：
  - 所有实体类必须继承通用实体类 `BaseEntity`（它提供了 `id`, `create_time`, `update_time` 等通用字段）。
  - 实体类必须包含自动建表系统的注解，且属性和元数据必须完整（必须使用如 `@TableName` 的表名，`@TableComment` 的表注释，`@TableField` 声明字段，`@ColumnComment` 字段注释，`@ColumnType` 字段类型等注解，所有实体类必须继承通用实体类），且所有这些相关的建表注解必须在文件头部正确引入。

### 2. Modularity & Package Separation (模块自治与配置隔离)
- **业务专属配置隔离**：所有业务模块专属的配置类（Properties/Configuration）必须内聚在其所在模块的 `config` 文件夹下（例如 `top.yuxs.springbootdev.modules.[module].config`），严禁将特定业务属性写入全局 `core/config`，确保 `core` 仅承载通用的纯底层技术中间件。
- **专属业务枚举归位**：仅服务于特定业务模块底层的枚举定义，必须存放在对应模块的 `enums` 目录下（如 `modules/[module]/enums/`），禁止随意放置在 `core/enums` 包中，保障通用枚举包的纯粹度。
- **事件/监听器对称设计**：各业务模块利用 Spring 异步事件架构解耦时，需严格执行“对称拆包”结构：
  - 事件定义类（Event）放置于 `modules/[module]/event`；
  - 事件监听处理器（Listener）放置于 `modules/[module]/listener`（并使用虚拟线程池 `taskExecutor` 标注 `@Async` 异步执行）。
  - 严禁将事件与处理器随意混合堆放在单个包内。

### 3. Component Self-Containment (高内聚技术组件设计)
- **技术组件自闭环原则**：系统内具备独立完备功能的自定义底层中间件技术组件（如自定义 DDL 自动建表引擎 `core/db`），必须遵循严格的**自包含与去污染**原则：
  - 属于该技术组件专属的配置类（如 `AegisDbProperties`）与启动加载引导器（如 `DatabaseInitConfig`）必须统一收拢在组件自身的包路径下（如 `core/db/config`）；
  - 该组件运转专属的核心参数及状态枚举（如 `ForeignKeyAction`、`IndexType`）必须在组件专用包（如 `core/db/enums`）内声明。
  - 坚决杜绝组件私有逻辑泄漏、污染、甚至四散在全局通用配置文件夹（`core/config`）或全局枚举文件夹（`core/enums`）中，确保底层组件具备极高的独立可拔插性与低心智负担。

### 4. Testing Conventions (单元测试 1:1 标准映射)
- **测试路径规范**：所有的单元测试类（位于 `src/test/java` 下）必须与 `src/main/java` 的主类路径保持严格的 **1:1 镜像包路径映射**（例如对 `modules/system` 下服务的业务测试，测试类必须位于 `top.yuxs.springbootdev.modules.system` 包路径下）。
- **根目录去污染**：单元测试根目录下仅允许保留容器启动骨架基类 `SpringbootDevApplicationTests.java`，严禁随意将具体模块的功能测试类堆置在测试根目录下，保障测试代码与生产代码具有对称的可维护性。
- **测试保障**：每次提交代构、配置或代码变更前，必须在项目根目录下通过 PowerShell 运行 Maven Wrapper 进行全量本地验证并取得 `BUILD SUCCESS`：
  ```powershell
  .\mvnw clean test
  ```

### 5. Commit & Pull Request Guidelines (提交与合并规范)
- **提交与推送规范**：历史记录通常遵循 Conventional Commit 主题。使用 `feat`、`fix`、`refactor`、`test` 或 `docs`，并带有可选的 scope。提交 GitHub 时，默认直接提交并推送到 `master`，不创建分支或 Pull Request；除非用户明确要求，否则不得改变该流程。提交内容说明必须使用中文，例如：`fix(file): “完善文件删除一致性与审计”`。汇报时使用格式：`提交：9a8ed73 fix(file): “中文说明”`。
- **Pull Request 规范**：如用户明确要求创建 Pull Request，PR 应说明变更、列出验证命令、关联 issue，并标注 schema、配置、API 或安全影响；接口变更还应包含请求/响应示例。
