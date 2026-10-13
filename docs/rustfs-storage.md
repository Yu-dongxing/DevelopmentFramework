# RustFS 文件存储接入

## 接入方式

核对官方文档日期：2026-10-12。采用 AWS SDK for Java v2（BOM 2.55.14），通过标准 S3 API 接入 RustFS；使用同步 URLConnection HTTP 客户端。既有 LOCAL、MINIO、ALIYUN_OSS 保留，新增 RUSTFS 类型。

- [RustFS SDK 总览](https://docs.rustfs.com/en/developer/sdk)
- [RustFS Java 接入、签名及分片示例](https://docs.rustfs.com/en/developer/sdk/java)
- [RustFS S3 协议及兼容性边界](https://docs.rustfs.com/en/administration/protocols/s3)
- [AWS SDK v2 发布记录](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.55.14)

不要将 SDK 示例版本号视为当前最新版本，也不要把 S3 对象接口兼容推断为 MinIO 管理 API 兼容。部署固定 RustFS 版本或镜像摘要，针对该版本执行集成测试。

## 启用

预先创建业务桶，首版使用未开启版本控制、未开启对象锁的桶。应用账号至少需要目标桶内的 `s3:PutObject`、`s3:GetObject`、`s3:DeleteObject` 权限；若启用自动建桶，还需要检查桶及创建桶的权限。建议生产预建桶并关闭自动创建，应用不使用 root 凭据。

通过部署环境或秘密管理设施设置：

| 环境变量 | 说明 |
| --- | --- |
| FILE_STORAGE_ACTIVE | 设置为 RUSTFS 后，新文件上传至 RustFS |
| FILE_RUSTFS_ENABLED | 设置为 true；切换到其他上传类型后仍应保持，以处理历史文件 |
| FILE_RUSTFS_ENDPOINT | 后端 S3 地址，例如 http://rustfs:9000；不能填控制台地址 |
| FILE_RUSTFS_PUBLIC_ENDPOINT | 浏览器可访问的 S3 源站，例如 https://files.example.com；为空时复用后端地址 |
| FILE_RUSTFS_REGION | 默认 us-east-1，必须与服务端一致 |
| FILE_RUSTFS_BUCKET | 新文件默认桶，默认 springbootdev |
| FILE_RUSTFS_ACCESS_KEY / FILE_RUSTFS_SECRET_KEY | 应用专用凭据，无默认值 |
| FILE_RUSTFS_ACCESS_MODE | PRIVATE（默认）或 PUBLIC |
| FILE_RUSTFS_URL_EXPIRE_SECONDS | 默认 900，有效范围 1 至 604800 秒 |
| FILE_RUSTFS_AUTO_CREATE_BUCKET | 默认 false，显式开启也不会设置公开策略 |
| FILE_RUSTFS_PATH_STYLE | 默认 true；false 要求服务端配置虚拟主机域名及 DNS/TLS |
| FILE_RUSTFS_MAX_UPLOAD_BYTES | 默认 104857600，限制单次普通上传，不超过 5 GiB |
| FILE_MAX_FILE_SIZE / FILE_MAX_REQUEST_SIZE | Servlet 默认 100MB / 110MB，与网关和对象上传上限协调 |

网络时限由 `file.rustfs.connection-timeout`（5s）、`attempt-timeout`（60s）、`call-timeout`（180s）控制；`max-retries` 默认 2，范围 0 至 5。慢链路大文件应调整时限。SDK 保留默认传输校验，并显式发送文件 Content-MD5。

显式启用时启动校验端点、凭据、桶名、时限等；选择 RUSTFS 却未启用会启动失败。关闭时不创建客户端，配置不用于上传，也无法操作历史 RustFS 文件。配置查询接口不返回凭据。

## 访问和 API

上传仍使用 `POST /api/common/file/upload`，multipart 参数为 `file`，可选 `bizId`、`bizType`、`path`。所有响应保持 `Result<T>`，Long ID 保持项目字符串序列化约定。

私有上传需要登录，文件归属记录为当前用户。详情 `GET /api/common/file/{id}` 和删除 `DELETE /api/common/file/{id}?physical=true` 对 RustFS 执行上传者校验，管理员不自动取得他人私有对象权限。公开模式允许公开读取，但删除仍需要上传者身份。匿名公开上传无法通过这些接口删除，清理由可信后台业务或运维完成。

正常上传响应示意（其余 SysFile 字段省略）：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": "1234567890123456789",
    "storageType": "RUSTFS",
    "storageBucket": "springbootdev",
    "filePath": "default/2026/10/uuid.txt",
    "fileUrl": "https://files.example.com/springbootdev/default/2026/10/uuid.txt?X-Amz-Algorithm=AWS4-HMAC-SHA256&..."
  }
}
```

具体 code、message 以项目 `ResultCode` 定义为准。签名链接具有短期持有者访问能力，获得链接的人可在有效期内使用。逻辑删除不会撤销已经签发的链接，立即撤销需要物理删除或更严格的代理下载机制。

临时 URL 不写入 `sys_file.file_url`，避免过期快照和 varchar(500) 截断；对象位置仍由类型、历史桶和 Key 确定。上传事务提交后才组装签名响应，失败时上传仍成功、返回文件 ID 且 `fileUrl=null`，客户端应重新请求详情，不应重复上传。详情签名失败则返回错误。未增加表或列。

上传、详情和分页接口关闭操作日志的响应正文保存，防止预签名 URL 被审计表长期保存；接口调用状态和耗时仍正常审计。

`PUBLIC` 只生成固定地址，不自动开放桶。需要部署人员明确设置只读桶策略。切换 PRIVATE/PUBLIC 会改变该 RustFS 后端所有历史文件的访问模式，不能用作单文件可见性设置。

`/sys/file/page` 在查询前过滤他人的 RustFS 记录，响应时更新链接。单条和批量删除都转入文件生命周期服务。为避免通过伪造元数据绕过签名与删除授权，`/sys/file/save`、`saveBatch`、`update`、`updateBatch` 已禁止通用写入，对所有存储类型生效。业务关联的后续修改应通过具备业务授权的专用服务完成。

## 事务、重试与迁移

- 上传先将请求流有界复制到临时文件，计算一次 MD5；SDK 重试从同一文件重新读取，结束后删除临时文件。进程异常退出留下的 `rustfs-upload-*.tmp` 需按运维保留期清理，不能删除仍在上传的文件。上传并发与临时磁盘容量需配套限制。
- 对象写入异常可能是响应丢失，仍按本次唯一 Key 补偿。数据库确认回滚后执行相同补偿；清理失败复用独立事务持久化的待删除任务。事务结果未知时保留对象供核对。
- 物理删除在数据库提交后由虚拟线程监听器执行，定时任务重试失败记录。历史桶缺失直接报错，不回退新默认桶；只有明确 NoSuchKey 可以幂等忽略，NoSuchBucket、拒绝访问、超时等继续重试。
- 切换 `file.active` 不搬迁数据；保留旧端点和凭据。修改默认桶不会改变历史文件的访问和删除位置。
- 当前没有存储实例 ID，同一类型不能同时定位两个独立 RustFS 集群。更换集群需先完整迁移对象或扩展存储配置标识，不能只更改端点。
- 首版普通 PutObject 不提供前端分片直传、断点续传、版本删除或对象锁清理。开启版本控制后普通 DeleteObject 的语义不等同永久清除所有版本。

反向代理应保留签名所依赖的主机、路径、查询参数及协议语义；外部 URL 在签名前选定，不得签名后替换域名或增加路径前缀。使用受信 TLS 证书，不关闭证书验证。当前浏览器先上传到后端，不需要为上传开放桶 CORS；如浏览器以 fetch 读取签名 URL，再按实际来源配置读取 CORS。

## 验证

仓库完整测试：

```powershell
.\mvnw.cmd clean test
```

默认跳过真实存储集成测试。需要测试时设置 `RUSTFS_TEST_ENDPOINT`、`RUSTFS_TEST_ACCESS_KEY`、`RUSTFS_TEST_SECRET_KEY`，再执行：

```powershell
.\mvnw.cmd '-Dtest=RustfsStorageIntegrationTest' test
```

此测试创建并清理唯一的 `codex-rustfs-test-<UUID>` 桶，需要测试账号有建桶、对象操作和桶策略权限，只对隔离测试实例使用。覆盖真实上传/内容校验、匿名拒绝、签名下载/到期拒绝、公开读取、切换默认桶后历史删除及重复删除。不要赋予生产应用账号这些测试管理权限。

单元和 H2 测试另外覆盖配置错误、可重读请求体、上传失败补偿、私有 URL 不落库、越权签名/删除拒绝、事务回滚、失败任务持久化及后台通用入口保护。测试通过不等同生产 DNS、HTTPS、IAM 和网关联调通过。

### 本次验证记录（2026-10-12）

- AWS SDK 模块统一为 2.55.14，传输显式使用 URLConnection，排除默认 Apache5 与 Netty 客户端。
- 完整 `clean test` 共 81 项，80 项通过、1 项跳过；跳过的是需要另行启用的 `LocalDatabaseIntegrationTest`，RustFS 真实集成测试已运行。
- 真实 RustFS 使用本机已有 `1.0.0-alpha.82`，镜像摘要 `sha256:eadee1b524ec05cbc1fe08426a95e589bc70e70cd15a4591edf176cabde238a6`。隔离容器只绑定本机环回地址，验证后清理。
- 尝试拉取 `rustfs/rustfs:1.0.1` 时发生网络 EOF，本次不宣称已验证该版本。上述旧版本仅用于兼容性验证，不作为新部署版本建议；正式部署须对实际固定版本重跑集成测试。
- 本次未连接生产实例，生产域名、HTTPS、IAM 最小权限以及业务客户端仍需部署环境验收。
