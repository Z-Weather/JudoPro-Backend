# JudoPro 后端 (JudoPro-Backend)

## 项目简介

JudoPro-Backend 是 **JudoPro** 项目的核心服务端，基于 Spring Boot 构建。提供用户认证、文件上传与管理、AI 分析结果存储、运动员/比赛信息检索（Lucene 全文索引）、网络爬虫数据抓取等能力，同时作为中继将媒体文件转发给 Python AI 微服务进行推理分析。

## 技术栈

- **框架**：Spring Boot 2.7.5
- **语言**：Java 11
- **构建工具**：Maven
- **ORM**：Spring Data JPA + Hibernate
- **数据库**：MySQL 8.0+
- **安全**：Spring Security（Session Cookie + BCrypt 密码加密）
- **全文检索**：Apache Lucene 8.11.1 + HanLP 中文分词
- **爬虫**：WebMagic 0.7.6
- **HTTP 客户端**：Retrofit 2.9.0 + OkHttp 4.10.0（调用下游 AI 微服务）
- **包路径**：`cn.edu.bistu.cs.ir`

## 项目目录结构

```
JudoPro-Backend/
├── src/
│   ├── main/
│   │   ├── java/cn/edu/bistu/cs/ir/
│   │   │   ├── IrDemoApplication.java            # Spring Boot 启动类（@EnableAsync 开启异步支持）
│   │   │   ├── config/                           # 全局配置类
│   │   │   │   ├── Config.java                   # 通用配置属性（爬虫重试次数、User-Agent 等）
│   │   │   │   ├── FileUploadConfig.java         # 文件上传配置（路径、大小限制）
│   │   │   │   ├── SecurityConfig.java           # Spring Security 安全策略（URL 权限、登录/登出、CSRF、Session）
│   │   │   │   ├── VolcengineConfig.java         # 火山引擎（外部大模型）API 配置
│   │   │   │   └── WebMvcConfig.java             # Web MVC 扩展配置（跨域、拦截器等）
│   │   │   ├── controller/                       # RESTful API 控制器层
│   │   │   │   ├── FileUploadController.java     # 文件上传、AI 分析触发、结果查询、文件下载
│   │   │   │   ├── QueryController.java          # 运动员/比赛信息检索接口（关键词、筛选、分页）
│   │   │   │   └── UserController.java           # 用户注册、登录、获取当前用户、登出
│   │   │   ├── crawler/                          # 网络爬虫模块
│   │   │   │   ├── CrawlerService.java           # 爬虫调度服务（启动/停止/状态管理）
│   │   │   │   └── IjfCrawler.java               # 国际柔道联盟（IJF）站点爬虫实现
│   │   │   ├── dto/                              # 数据传输对象（DTO）
│   │   │   │   └── FileWithAnalysisDTO.java      # 文件与其 AI 分析结果的组合对象
│   │   │   ├── entity/                           # JPA 数据库实体类
│   │   │   │   ├── AIAnalysis.java               # AI 分析结果实体（状态、结果 URL、错误信息）
│   │   │   │   └── UserFile.java                 # 用户上传文件实体（元数据、软删除、下载统计）
│   │   │   ├── index/                            # Lucene 全文索引模块
│   │   │   │   ├── IdxService.java               # Lucene 索引读写服务（构建索引、分页查询、高亮）
│   │   │   │   └── LucenePipeline.java           # 爬虫数据接入 Lucene 索引的 Pipeline
│   │   │   ├── interceptor/                      # 拦截器
│   │   │   │   └── ApiLoggingInterceptor.java    # API 请求日志拦截器（记录请求路径、耗时、参数）
│   │   │   ├── model/                            # 领域模型与数据仓库
│   │   │   │   ├── AgeGroup.java                 # 年龄段枚举/分类
│   │   │   │   ├── Continent.java                # 大洲枚举
│   │   │   │   ├── CountryContinentMapping.java  # 国家与大洲映射关系
│   │   │   │   ├── Photo.java                    # 照片数据模型
│   │   │   │   ├── PhotoEntity.java              # 照片集合模型
│   │   │   │   ├── Player.java                   # 运动员领域模型（姓名、国家、排名、照片等）
│   │   │   │   ├── User.java                     # 用户领域模型（JPA 实体，含密码脱敏）
│   │   │   │   ├── UserRepository.java           # 用户数据访问接口
│   │   │   │   └── WeightClass.java              # 量级枚举/分类
│   │   │   ├── repository/                       # Spring Data JPA 仓库接口
│   │   │   │   ├── AIAnalysisRepository.java     # AI 分析记录数据访问
│   │   │   │   └── UserFileRepository.java       # 用户文件数据访问
│   │   │   ├── service/                          # 业务逻辑层（Service）
│   │   │   │   ├── AIAnalysisService.java        # AI 分析业务（创建记录、保存结果、状态流转）
│   │   │   │   ├── SearchCriteria.java           # 搜索条件封装（关键词、筛选字段、排序）
│   │   │   │   ├── UserFileService.java          # 用户文件业务（保存文件信息、分页查询、软删除）
│   │   │   │   └── UserService.java              # 用户业务（注册校验、密码加密、用户信息查询）
│   │   │   └── utils/                            # 工具类
│   │   │       ├── AgeUtils.java                 # 年龄段计算与格式化工具
│   │   │       ├── FileUploadUtils.java          # 文件上传工具（保存到磁盘、生成唯一文件名、URL 构建）
│   │   │       ├── FileUtils.java                # 通用文件操作工具
│   │   │       ├── HttpUtils.java                # HTTP 请求工具
│   │   │       ├── JsonUtils.java                # JSON 序列化/反序列化工具
│   │   │       ├── PageResponse.java             # 通用分页响应包装类
│   │   │       ├── QueryResponse.java            # 查询响应包装类（含高亮片段）
│   │   │       └── StringUtil.java               # 字符串处理工具
│   │   └── resources/
│   │       ├── application.properties            # 应用主配置（数据库、上传、日志、Lucene 目录）
│   │       ├── log4j.properties                  # Log4j 日志配置
│   │       ├── logback-spring.xml                # Logback 日志配置（Spring 环境）
│   │       ├── schema.sql                        # 数据库初始化脚本（建表 + 索引）
│   │       ├── school.json                       # 静态数据（学校信息）
│   │       └── META-INF/
│   │           └── MANIFEST.MF                   # 打包清单
│   └── test/
│       └── java/cn/edu/bistu/cs/ir/
│           ├── controller/
│           │   └── QueryControllerCombinedTest.java # QueryController 集成测试
│           ├── index/
│           │   └── LuceneTest.java               # Lucene 索引/查询单元测试
│           ├── model/
│           │   └── ContinentTest.java            # 大洲枚举测试
│           └── IrDemoApplicationTest.java        # 应用上下文加载测试
├── uploads/                                      # 上传文件存储目录（运行时生成）
│   └── ...                                       # 原始文件、标注后文件、文字描述文件等
├── workspace/                                    # 爬虫数据与 Lucene 索引工作目录
│   ├── idx/                                      # Lucene 索引文件存放目录
│   └── crawler/                                  # 爬虫抓取原始 JSON 数据存放目录
├── logs/                                         # 应用运行日志目录
├── pom.xml                                       # Maven 构建配置（依赖、插件、仓库镜像）
└── README.md
```

## 核心功能模块详解

### 1. 用户认证模块 (`controller/UserController`, `service/UserService`, `config/SecurityConfig`)

- **注册流程**：
  - 接口：`POST /api/user/register`
  - 校验规则：密码必须为字母和数字组合，且不少于 6 位；用户名和邮箱不可重复。
  - 密码存储：使用 `BCryptPasswordEncoder` 加密后存入 MySQL。
- **登录流程**：
  - 接口：`POST /api/user/login`
  - 认证方式：Spring Security 表单认证，登录成功后通过 `Set-Cookie` 下发 `JSESSIONID` Session Cookie。
  - 前端后续请求自动携带 Cookie，后端通过 `request.getSession().getAttribute("userId")` 识别用户。
- **登出流程**：
  - 接口：`POST /api/user/logout`
  - 由 Spring Security 处理 Session 失效，返回 JSON 成功响应。
- **权限控制**（SecurityConfig）：
  | URL 模式 | 权限 |
  |----------|------|
  | `/api/user/register`, `/api/user/login`, `/query/**` | 匿名可访问 |
  | `/uploads/**` | 匿名可访问（文件直链） |
  | `/api/user/me` | 需认证 |
  | `/api/file/upload/**`, `/api/file/delete` | 需认证 |
  | 其他 | 全部放行 |

### 2. 文件上传与 AI 分析中继模块 (`controller/FileUploadController`, `service/UserFileService`, `service/AIAnalysisService`)

#### 文件上传
- **接口**：
  - `POST /api/file/upload/single` — 单文件上传
  - `POST /api/file/upload/multiple` — 多文件上传
  - `POST /api/file/upload/image` — 图片上传
  - `POST /api/file/upload/video` — 视频上传
- **处理流程**：
  1. 接收 `MultipartFile`，校验文件类型与大小（最大 50MB）
  2. 通过 `FileUploadUtils` 生成唯一存储文件名，保存到 `uploads/` 目录
  3. 将文件元数据（原始文件名、存储文件名、URL、类型、大小、MIME 类型）写入 `user_files` 表
  4. 返回文件访问 URL

#### AI 分析触发
- **接口**：`POST /api/file/analyze`
- **处理流程**：
  1. 接收用户上传的文件，保存到本地
  2. 在 `ai_analysis` 表中创建分析记录，初始状态为 `pending`
  3. 将文件以二进制流形式转发给 Python AI 微服务 `http://{SERVER_HOST}:8000/analyze_binary`
  4. 接收 AI 微服务返回的标注文件 URL（如 `http://127.0.0.1:8000/uploads/annotated_images/xxx.jpg`）
  5. 从 AI 微服务下载标注后的文件，保存到本地 `uploads/` 目录
  6. 更新 `ai_analysis` 记录状态为 `completed`，填充 `annotated_media_url`、`annotated_filename` 等字段
  7. 可选：调用火山引擎大模型 API 生成文字描述，保存结果与文件
  8. 将最终分析结果返回给前端

#### 分析状态管理
`AIAnalysis` 实体记录完整分析生命周期：

| 字段 | 说明 |
|------|------|
| `user_file_id` | 关联的上传文件 ID |
| `user_id` | 发起分析的用户 ID |
| `media_type` | `image` 或 `video` |
| `analysis_status` | `pending` → `completed` / `failed` |
| `annotated_media_url` | 标注后文件的 HTTP 访问 URL |
| `annotated_filename` | 标注文件的存储文件名 |
| `external_model_result` | 火山引擎大模型生成的文字描述 |
| `python_service_result` | Python 微服务返回的原始 JSON |
| `error_message` | 失败时的错误信息 |
| `created_time` / `analysis_time` | 创建时间 / 完成时间 |

### 3. 信息检索模块 (`controller/QueryController`, `index/IdxService`)

- **检索引擎**：Apache Lucene 8.11.1
- **中文分词**：HanLP（portable-1.8.3）+ hanlp-lucene-plugin
- **索引内容**：运动员信息（姓名、国家、年龄、体重、排名、照片、高光时刻等）
- **核心接口**：
  - `GET /query/kw?kw=关键词&pageNo=1&pageSize=10` — 关键词全文检索，支持分页
  - `GET /query/detail?id=运动员ID` — 获取运动员详情
  - `POST /query/advanced` — 高级检索（多条件组合：年龄段、量级、大洲、性别等）
- **高亮支持**：检索结果中的匹配关键词会被 `<em>` 标签高亮。

### 4. 网络爬虫模块 (`crawler/CrawlerService`, `crawler/IjfCrawler`)

- **目标站点**：国际柔道联盟官网 `https://www.ijf.org/judoka`
- **技术框架**：WebMagic
- **调度策略**：`PriorityScheduler` 优先级队列
- **数据流水线**：
  1. `IjfCrawler` 抓取运动员页面 HTML
  2. 解析页面提取运动员结构化数据（姓名、国家、排名、照片等）
  3. `JsonFilePipeline` 将原始数据保存为 JSON 到 `workspace/crawler/`
  4. `LucenePipeline` 将解析后的数据写入 Lucene 索引到 `workspace/idx/`
- **反爬策略**：自定义 User-Agent、设置请求间隔（`sleepTime`）、失败重试（`retryTimes`）

## 数据库设计

数据库脚本位于 `src/main/resources/schema.sql`，核心表结构如下：

### users（用户表）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 用户 ID，自增 |
| `username` | VARCHAR(255) UNIQUE | 用户名 |
| `email` | VARCHAR(255) UNIQUE | 邮箱 |
| `password` | VARCHAR(255) | BCrypt 加密后的密码 |
| `avatar` | VARCHAR(500) | 头像 URL |
| `real_name` | VARCHAR(100) | 真实姓名 |
| `gender` | VARCHAR(10) | 性别 |
| `birth_date` | VARCHAR(50) | 出生日期 |
| `enabled` | BOOLEAN DEFAULT TRUE | 账户是否启用 |

### user_files（用户文件表）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 文件 ID |
| `user_id` | BIGINT FK | 上传者用户 ID |
| `original_filename` | VARCHAR(255) | 原始文件名 |
| `stored_filename` | VARCHAR(255) | 系统生成的唯一文件名 |
| `file_url` | VARCHAR(500) | 文件访问 URL |
| `file_type` | VARCHAR(20) | `image` / `video` |
| `file_extension` | VARCHAR(10) | 扩展名 |
| `file_size` | BIGINT | 文件大小（字节） |
| `mime_type` | VARCHAR(100) | MIME 类型 |
| `description` | VARCHAR(500) | 描述 |
| `download_count` | INT DEFAULT 0 | 下载次数 |
| `upload_time` | DATETIME | 上传时间 |
| `is_deleted` | BOOLEAN DEFAULT FALSE | 软删除标记 |

### ai_analysis（AI 分析结果表）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 分析结果 ID |
| `user_file_id` | BIGINT | 关联文件 ID |
| `user_id` | BIGINT | 用户 ID |
| `external_model_result` | LONGTEXT | 火山引擎大模型文字描述 |
| `external_model_result_url` | VARCHAR(500) | 文字描述文件 URL |
| `python_service_result` | LONGTEXT | Python 微服务原始 JSON |
| `annotated_media_url` | VARCHAR(500) | 标注后媒体 URL |
| `annotated_filename` | VARCHAR(255) | 标注文件名 |
| `analysis_status` | VARCHAR(50) | `pending` / `completed` / `failed` |
| `error_message` | VARCHAR(1000) | 错误信息 |
| `media_type` | VARCHAR(20) | `image` / `video` |
| `prompt` | VARCHAR(1000) | 分析提示词 |
| `created_time` / `analysis_time` | DATETIME | 创建/完成时间 |

## 配置文件详解

`src/main/resources/application.properties` 关键配置项：

```properties
# 工作目录（Lucene 索引、爬虫数据存放位置）
irdemo.dir.home = workspace
irdemo.dir.idx = ${irdemo.dir.home}/idx
irdemo.dir.crawler = ${irdemo.dir.home}/crawler

# 数据库连接（请根据实际环境修改）
spring.datasource.url=jdbc:mysql://10.199.201.199:3306/JudoPro?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=jby
spring.datasource.password=123456
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# 文件上传限制
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=100MB
file.upload.upload-path=uploads

# 日志级别
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.springframework.web=DEBUG
```

## 启动方法

### 前置条件

- JDK 11+
- Maven 3.6+
- MySQL 8.0+（已创建 `JudoPro` 数据库，并执行 `schema.sql` 初始化表结构）

### 方式一：IDE 运行（推荐）

1. 使用 IntelliJ IDEA 打开 `JudoPro-Backend` 文件夹。
2. 等待 Maven 依赖下载完成（首次可能需要较长时间，配置了阿里云镜像加速）。
3. 运行 `src/main/java/cn/edu/bistu/cs/ir/IrDemoApplication.java` 中的 `main` 方法。

### 方式二：命令行

```bash
# 进入项目目录
cd JudoPro-Backend

# 编译并运行
mvn spring-boot:run

# 或先打包再运行
mvn clean package -DskipTests
java -jar target/ir_demo-0.1.0-SNAPSHOT.jar
```

- 服务默认启动在 **8080** 端口
- API 根路径为 `/api/**`
- 检索接口路径为 `/query/**`

## 与上下游的对接关系

- **上游客户端**：接收 [JudoProFrontendv2](../JudoProFrontendv2) Android 客户端的 HTTP 请求（RESTful API + Cookie Session）。
- **下游 AI 微服务**：通过 OkHttp 将媒体文件二进制流转发给 [JudoPro-localmodel](../JudoPro-localmodel) Python FastAPI 微服务的 `/analyze_binary` 接口，接收标注后的文件 URL。
- **外部大模型**（可选）：通过 `VolcengineConfig` 配置火山引擎 API Key，调用大模型生成分析文字描述。
