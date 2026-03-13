# Đánh giá mã nguồn trước khi triển khai Production

**Dự án:** User Access Management System  
**Ngày đánh giá:** 3/3/2025  
**Mục đích:** Rà soát toàn bộ mã nguồn trước khi triển khai lên môi trường production

---

## Bảng đánh giá

| Module/Package/File | Nội dung cần cải thiện | Mức độ ưu tiên | Gợi ý cải thiện | Checklist hoàn thành                                                                           |
|---------------------|------------------------|----------------|------------------|------------------------------------------------------------------------------------------------|
| **core/security/JwtUtil.java** | JWT secret key hardcode trong source code | **CRITICAL** | Chuyển secret sang biến môi trường (ví dụ `JWT_SECRET`) hoặc config server; inject qua `@Value("${jwt.secret}")` | v                                                                                              |
| **core/security/JwtUtil.java** | `SignatureAlgorithm.HS256` deprecated | Medium | Dùng `Jwts.SIG.HS256` hoặc `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))` theo chuẩn jwt mới | v                                                                                              |
| **core/security/JwtUtil.java** | Thời gian hết hạn JWT không cấu hình được | Medium | Đưa token expiration vào `application.yaml` (ví dụ `jwt.expiration-ms`) và inject qua `@Value` | v                                                                                              |
| **core/security/JwtFilter.java** | Không xử lý exception khi parse JWT sai (token hết hạn, format sai) | **CRITICAL** | Bọc `extractUsername` và `validateToken` trong try-catch; khi lỗi, return 401 và không set authentication | v                                                                                              |
| **core/security/JwtFilter.java** | Không kiểm tra token hết hạn | High | Thêm kiểm tra expiration trong `validateToken` hoặc dùng `parseClaimsJws` trước khi extract | v                                                                                              |
| **auth-service/config/SecurityConfig.java** | Endpoint `/internal/**` cho phép truy cập công khai | **CRITICAL** | Bảo vệ internal endpoint bằng mạng nội bộ (network policy), API key, hoặc service-to-service authentication | v                                                                                              |
| **user-service/config/SecurityConfig.java** | Endpoint `/internal/**` cho phép truy cập công khai | **CRITICAL** | Tương tự auth-service; chỉ cho phép request từ auth-service (IP whitelist, mTLS, hoặc shared secret) | v                                                                                              |
| **auth-service/service/AuthService.java** | Link kích hoạt hardcode `http://localhost:8081` | **CRITICAL** | Dùng config `app.activation.base-url` hoặc `APP_BASE_URL` từ biến môi trường | v                                                                                              |
| **auth-service/service/AuthService.java** | Cho phép user gửi role khi đăng ký (có thể tự đăng ký ADMIN) | **CRITICAL** | Bỏ `role` từ request; gán mặc định `ROLE_USER` cho mọi user mới đăng ký | v                                                                                              |
| **auth-service/service/AuthService.java** | Sync user sang user-service không có retry/rollback khi lỗi | High | Thêm retry logic; nếu sync fail, rollback user trong auth-service hoặc xử lý eventual consistency | v                                                                                              |
| **auth-service/service/AuthService.java** | Gửi email trước khi sync user-service | Medium | Xem xét: nếu email fail hoặc sync fail, user có thể bị inconsistent; cân nhắc thứ tự gửi email sau sync | v                                                                                              |
| **user-service/service/UserService.java** | `deleteUser`: gọi auth-service trước, nếu auth delete thành công nhưng user-service delete fail → dữ liệu không đồng bộ | High | Thêm xử lý lỗi; nếu auth delete fail, không xóa user-service; cân nhắc saga/compensation nếu cần | ch xong vde hien tai la neu xoa o auth-service thanh cong, deletion o user-service co the fail |
| **user-service/service/UserService.java** | `deleteUser`: RestTemplate không có timeout | Medium | Cấu hình `RestTemplate` với `ConnectTimeout` và `ReadTimeout` | v                                                                                              |
| **core/config/RestTemplateConfig.java** | RestTemplate không có timeout, retry | Medium | Thêm `ClientHttpRequestFactory` với timeout; cân nhắc retry cho inter-service calls | v                                                                                              |
| **auth-service/dto/RegisterRequest.java** | Thiếu `@NotBlank` cho username, password | High | Thêm `@NotBlank` cho username, password; thêm `@Size(min=8)` cho password | v                                                                                              |
| **auth-service/dto/RegisterRequest.java** | Thiếu validation email format | Medium | Thêm `@Email` cho trường email | v                                                                                              |
| **auth-service/dto/LoginRequest.java** | Thiếu validation | Medium | Thêm `@NotBlank` cho username và password | v                                                                                              |
| **auth-service/controller/AuthController.java** | `/login` không dùng `@Valid` | Medium | Thêm `@Valid @RequestBody LoginRequest` | v                                                                                              |
| **user-service/dto/UpdateUserRequest.java** | Thiếu validation | Medium | Thêm `@Email` cho email, `@Size(max=255)` cho fullName | v                                                                                              |
| **user-service/service/UserService.java** | `updateUser` không kiểm tra email trùng | Medium | Kiểm tra email unique trước khi update (trừ email của chính user đó) | v                                                                                              |
| **core/exception/GlobalExceptionHandler.java** | `handleAll(Exception)` trả về `ex.getMessage()` ra client | Medium | Không log stack trace; trả generic message cho client; log chi tiết lỗi ra server log | v                                                                                              |
| **core/exception/GlobalExceptionHandler.java** | Thiếu handler cho `BadCredentialsException` | Medium | Thêm handler trả 401 với message thống nhất | v                                                                                              |
| **core/exception/GlobalExceptionHandler.java** | Thiếu handler cho `MethodArgumentNotValidException` (validation errors) | Medium | Thêm handler trả 400 với danh sách lỗi validation | v                                                                                              |
| **application-docker.yaml** (auth & user) | `ddl-auto: update` trong production | **CRITICAL** | Đổi thành `validate` hoặc `none`; dùng Flyway/Liquibase cho migration | ☐                                                                                              |
| **application-docker.yaml** | `show-sql: true` trong production | High | Tắt `show-sql`; dùng logging level riêng cho SQL nếu cần debug | v                                                                                              |
| **docker-compose.yaml** | Mật khẩu PostgreSQL hardcode | High | Dùng `.env` file và biến môi trường; không commit mật khẩu thật | v                                                                                              |
| **docker-compose.yaml** | Thiếu health check cho services | Medium | Thêm `healthcheck` cho postgres, auth-service, user-service | v                                                                                              |
| **auth-service/Dockerfile** | Dùng JDK thay vì JRE | Low | Dùng `eclipse-temurin:17-jre-jammy` để giảm kích thước image | v                                                                                              |
| **user-service/Dockerfile** | Dùng JDK thay vì JRE | Low | Tương tự auth-service | v                                                                                              |
| **auth-service** | Thiếu cấu hình URL user-service | High | Thêm `USER_SERVICE_URL` hoặc `app.user-service.url` thay vì hardcode | v                                                                                              |
| **user-service** | Thiếu cấu hình URL auth-service | High | Thêm `AUTH_SERVICE_URL` hoặc `app.auth-service.url` thay vì hardcode | v                                                                                              |
| **auth-service/service/EmailService.java** | Không xử lý lỗi gửi email | Medium | Bắt exception, log lỗi; cân nhắc queue/async để không block registration | partially chua queue/async                                                                     |
| **user-service/controller/UserController.java** | `updateMyProfile` không validate request | Medium | Thêm `@Valid @RequestBody UpdateUserRequest` | v                                                                                              |
| **core/entity/User.java** | Thiếu `@CreationTimestamp`, `@UpdateTimestamp` | Low | Thêm audit fields (createdAt, updatedAt) nếu cần | ☐                                                                                              |
| **auth-service** | Không có rate limiting cho /login, /register | High | Thêm rate limiting (Bucket4j, Resilience4j) để chống brute force | v                                                                                              |
| **Toàn hệ thống** | Thiếu logging chuẩn | Medium | Dùng structured logging (JSON); log request ID, user, action | v                                                                                              |
| **Toàn hệ thống** | Thiếu monitoring/metrics | Medium | Thêm Actuator, Prometheus metrics; health endpoints | ☐                                                                                              |
| **Toàn hệ thống** | Thiếu API documentation cho production | Low | Bật Swagger/OpenAPI với config riêng cho production (có thể tắt trong prod) | ☐                                                                                              |

---

## Tóm tắt theo mức độ ưu tiên

### CRITICAL (phải xử lý trước khi lên production)
- JWT secret hardcode
- JwtFilter không xử lý exception khi token sai
- Internal endpoints không được bảo vệ
- Link kích hoạt hardcode localhost
- Cho phép user tự đăng ký role ADMIN
- `ddl-auto: update` trong production

### High
- JwtFilter không kiểm tra token hết hạn
- Thiếu retry/rollback khi sync giữa services
- Inconsistency khi delete user
- Thiếu validation RegisterRequest (username, password)
- Thiếu cấu hình URL giữa các service
- Thiếu rate limiting 
- `show-sql: true` trong production
- Mật khẩu DB hardcode trong docker-compose

### Medium
- JWT expiration không cấu hình được
- RestTemplate không có timeout
- Thiếu validation LoginRequest, UpdateUserRequest
- GlobalExceptionHandler lộ chi tiết lỗi
- Thiếu handler validation errors
- EmailService không xử lý lỗi
- Thiếu health check
- Thiếu structured logging

### Low
- Dockerfile dùng JDK thay vì JRE
- Thiếu audit fields
- Thiếu API documentation

---

## Review: Register → Activate Account → Login

### 1. Tổng quan flow hiện tại

```
[Register] → [Save User (enabled=false)] → [Create ActivationToken] → [Send Email] → [Sync User-Service]
     ↓
[User nhận email] → [Click link /auth/activate/{token}] → [Enable user] → [Mark token used]
     ↓
[Login] → [Validate credentials + enabled] → [Return JWT]
```

### 2. Đánh giá theo khía cạnh

#### 2.1. Code

| Vấn đề | Vị trí | Mô tả | Production-ready? |
|--------|--------|-------|-------------------|
| **Role từ client** | `AuthService.register()` L46 | `user.setRole(request.getRole())` – client có thể gửi `ROLE_ADMIN` | ❌ Không | da sua backend luon gan ROLE_USER
| **Validation thiếu** | `RegisterRequest` | Chỉ có `@NotBlank` cho email; username, password không validate | ❌ Không | da them vao dto
| **Validation thiếu** | `LoginRequest` | Không có `@Valid`, không validate | ❌ Không | da them vao authController
| **Link hardcode** | `AuthService` L59 | `http://localhost:8081` – sai trong Docker/production | ❌ Không | da dung app.activation.base-url
| **Không transactional** | `AuthService.register()` | Nhiều bước (DB, email, sync) nhưng không có transaction/compensation | ❌ Không | da them giup rollback local DB neu sync fail
| **Email không xử lý lỗi** | `EmailService.sendActivationEmail()` | `mailSender.send()` có thể throw, không try-catch | ❌ Không | da them try catch
| **Sync không xử lý lỗi** | `AuthService.register()` L70-74 | `RestTemplate.postForEntity` có thể fail, không retry/rollback | ❌ Không | da them retry, throw exception de rollback
| **Thứ tự thực thi** | `AuthService.register()` | Gửi email trước sync → user có thể nhận email nhưng user-service chưa có user | ⚠️ Cần xem xét | da doi thanh sync truoc neu syn that bai throw exception, khong send email

#### 2.2. Flow / Logic

| Vấn đề | Mô tả | Production-ready? |
|--------|-------|-------------------|
| **Race condition** | Register: save user → save token → email → sync. Nếu sync fail, user đã nhận email nhưng user-service chưa có user | ❌ Không | da them try catch cho sync user
| **Idempotency** | Register cùng username/email nhiều lần: lần đầu 409, lần sau vẫn 409 – OK | ✅ OK |
| **Token reuse** | Activate: kiểm tra `usedAt != null` – tránh dùng lại token | ✅ OK |
| **Token expiry** | Activate: kiểm tra `expiresAt` – token hết hạn sau 24h | ✅ OK |
| **Login khi chưa activate** | Login: kiểm tra `user.isEnabled()` – trả 422 | ✅ OK |
| **Thông báo lỗi** | Login: "Not found" vs "Invalid password" – có thể leak thông tin user tồn tại | ⚠️ Nên thống nhất message | da doi

#### 2.3. Technology

| Vấn đề | Mô tả | Production-ready? |
|--------|-------|-------------------|
| **Token generation** | `UUID.randomUUID()` – đủ ngẫu nhiên | ✅ OK |
| **Password** | BCrypt qua `PasswordEncoder` | ✅ OK |
| **Email** | `JavaMailSender` + SMTP – đồng bộ, có thể block | ⚠️ Nên async |
| **RestTemplate** | Không timeout, không retry | ❌ Không | da them timeout vao restTemplateConfig, retry vao authService
| **ActivationToken** | Lưu DB, có `expiresAt`, `usedAt` | ✅ OK |

### 3. Các vấn đề nghiêm trọng cần sửa

#### 3.1. Bảo mật

1. **Role từ client** – Client có thể gửi `role: "ROLE_ADMIN"`. Sửa: luôn gán `ROLE_USER` khi register, bỏ `role` khỏi `RegisterRequest`.
2. **Thông báo lỗi login** – "Not found" vs "Invalid password" cho phép đoán username tồn tại. Sửa: dùng chung message kiểu "Invalid username or password".

#### 3.2. Cấu hình

1. **Activation link** – Hardcode `http://localhost:8081`. Sửa: dùng `app.activation.base-url` hoặc `APP_BASE_URL` từ env.
2. **User-service URL** – Hardcode `http://user-service:8082`. Sửa: dùng `USER_SERVICE_URL` hoặc config tương đương.

#### 3.3. Độ tin cậy

1. **Email fail** – Nếu `mailSender.send()` lỗi → exception → user đã lưu DB nhưng không nhận email. Sửa: try-catch, log lỗi; cân nhắc queue/async và retry.
2. **Sync fail** – Nếu sync sang user-service fail → user có trong auth-service nhưng không có trong user-service. Sửa: retry sync; hoặc rollback user trong auth-service; hoặc chấp nhận eventual consistency và có job đồng bộ lại.
3. **Thứ tự thực thi** – Gửi email trước sync → user có thể kích hoạt trước khi user-service có user. Sửa: sync trước, gửi email sau; hoặc đảm bảo activate chỉ cần auth-service.

#### 3.4. Validation

1. **RegisterRequest** – Thêm `@NotBlank` cho username, password; `@Size(min=8)` cho password; `@Email` cho email.
2. **LoginRequest** – Thêm `@Valid` và validation tương tự.

### 4. Đề xuất cải thiện flow

**Flow đề xuất:**

```
[Register]
  1. Validate request
  2. Check username/email tồn tại
  3. Save user (enabled=false) + ActivationToken (trong cùng transaction)
  4. Sync sang user-service (có retry)
  5. Gửi email (async hoặc có try-catch)
  6. Nếu sync fail → rollback user + token hoặc retry
```

**Thay đổi kỹ thuật gợi ý:**

| Thành phần | Hiện tại | Đề xuất |
|------------|----------|---------|
| Email | Đồng bộ, không xử lý lỗi | Async (ví dụ `@Async` + queue) hoặc ít nhất try-catch + log |
| Sync | RestTemplate, không retry | Retry (Resilience4j) hoặc message queue |
| Transaction | Chỉ `@Transactional` ở activate | Transaction cho register (user + token); sync/email ngoài transaction |
| Config | Hardcode URL | Env/config cho base URL và service URLs |

### 5. Kết luận

| Tiêu chí | Đánh giá | Ghi chú |
|----------|----------|---------|
| **Code** | Chưa đạt | Role từ client, validation thiếu, xử lý lỗi chưa đủ |
| **Flow** | Cơ bản đúng | Thiếu xử lý lỗi và rollback/retry |
| **Tech** | Cần cải thiện | RestTemplate, email đồng bộ, config hardcode |
| **Production** | Chưa sẵn sàng | Cần sửa các mục CRITICAL trước khi deploy |

**Khuyến nghị:** Không triển khai flow này lên production cho đến khi đã xử lý: role từ client, activation link/config, xử lý lỗi email và sync, validation đầy đủ, và cấu hình qua env/config.

---

## Ghi chú

- **Mức độ ưu tiên:** CRITICAL > High > Medium > Low
- **Checklist:** Đánh dấu ☐ → ☑ khi hoàn thành từng mục
- Nên triển khai theo thứ tự: CRITICAL → High → Medium → Low
