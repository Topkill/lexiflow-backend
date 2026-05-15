# LexiFlow Backend

LexiFlow 后端是一个基于 Spring Boot 3 的 AI 背单词平台服务端，采用单体模块化架构，覆盖用户认证、词库管理、学习计划、今日任务、卡片学习、SM-2 复习反馈、错词收藏、AI 内容生成、AI 完形填空、学习报告和管理员后台能力。

项目定位为作品集级企业项目，重点展示完整业务闭环、权限控制、AI 接入、后台运营、Excel 导入和学习数据统计。

## 技术栈

- Java 17
- Spring Boot 3.3.x
- Spring Security + JWT
- MyBatis-Plus
- MySQL 8.x
- Redis
- RabbitMQ
- Apache POI
- Knife4j / OpenAPI

## 核心模块

- `auth`：邮箱密码注册登录、JWT 鉴权、当前用户上下文
- `user`：用户资料、学习偏好、密码修改
- `wordbook`：词库、单词、后台维护、Excel 导入
- `study`：学习计划、今日任务、学习卡片、学习反馈
- `review`：复习词、错词本、收藏词、专项复习
- `quiz`：AI 完形填空生成、提交和评分
- `ai`：公共/私有 AI 配置、OpenAI 兼容调用、缓存、配额、调用日志
- `report`：AI 学习报告生成和历史查询
- `admin`：数据看板、用户管理、AI 日志、运营管理接口
- `system`：后台系统配置

## 本地环境

请先准备：

- JDK 17，本机示例路径：`D:\jdk-17.0.12`
- Maven 3.9+
- MySQL 8.x
- Redis：`localhost:6379`，密码 `root`
- RabbitMQ：`localhost:5672`

本地配置文件：`src/main/resources/application-local.yml`。

默认数据库连接：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/lexiflow?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: root
```

数据库脚本位于项目根目录的 `docs/sql`：

- `schema.sql`：建表脚本
- `seed.sql`：内置词库、管理员账号和演示数据

## 启动方式

在 PowerShell 中进入后端仓库目录：

```powershell
cd C:\Users\delll\Desktop\java项目\ai专用\背单词项目\backend\lexiflow-backend
$env:JAVA_HOME='D:\jdk-17.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

服务默认地址：`http://localhost:8080`。

健康检查：

- `GET http://localhost:8080/api/v1/ping`
- `GET http://localhost:8080/actuator/health`

接口文档：

- `http://localhost:8080/doc.html`
- `http://localhost:8080/swagger-ui/index.html`

## 测试与构建

```powershell
$env:JAVA_HOME='D:\jdk-17.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -DskipTests compile
mvn test
```

## 演示账号

管理员：

- 邮箱：`admin@qq.com`
- 密码：`12345aaa`

普通用户：

- 邮箱：`67890@qq.com`
- 密码：`12345aaa`

也可以通过前端注册新用户，从零验证创建学习计划和背单词流程。

## AI 配置说明

系统支持 OpenAI 兼容接口。

管理员可以在后台配置公共 AI 服务：

- API Base URL
- API Key
- 模型名称
- 温度参数
- 每用户每日公共调用配额
- 启用状态

普通用户可以在用户端配置私有 AI 服务。私有配置优先生效时不消耗公共配额。API Key 会加密存储，接口不会明文回显。

当前 AI 能力包括：

- 单词讲解
- 例句生成
- 记忆法生成
- 学习组完形填空
- 每日学习报告

## 关键业务链路

1. 用户注册或登录。
2. 选择 CET4、CET6、考研英语等启用词库。
3. 创建每日新词学习计划。
4. 首页生成今日新词和到期复习任务。
5. 用户通过卡片反馈“不认识 / 模糊 / 认识”。
6. 系统按简化 SM-2 思路更新下次复习日期。
7. 一组单词完成后生成 10 空 AI 完形填空。
8. 用户提交完形填空后记录正确率和错词。
9. 用户查看错词、收藏、统计和 AI 学习报告。
10. 管理员维护词库、单词、AI 配置、导入任务和运营数据。

## 安全与权限

- 密码使用 BCrypt 加密存储。
- JWT 用于前后端分离鉴权。
- 普通用户只能访问自己的学习数据。
- 管理接口需要管理员角色。
- 禁用用户不可登录。
- AI 调用会校验鉴权、配置和公共配额。
- API Key 等敏感字段不明文返回。

## 作品集亮点

- 从词库、计划、学习、复习、AI 测验到统计报告的完整闭环。
- 后台运营能力完整，包含用户、词库、单词、Excel 导入、AI 配置和调用日志。
- AI 模块包含公共/私有 Key 策略、缓存、配额、日志和失败兜底。
- 学习反馈结合简化 SM-2 复习算法，具备真实背单词产品的核心体验。
- 后端模块边界清晰，适合继续扩展成微服务或独立 AI Service。
