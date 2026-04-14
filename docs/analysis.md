# Báo cáo phân tích hai phương án đồng bộ dữ liệu của hai database

Trong trường hợp tách hai database của auth-service và user-service riêng biệt, phức tạp nhất là làm sao để hai database của nó phải luôn đồng bộ như nhau.

## Cách 1:
Dùng phương án A để sync trong lúc register.  
Dùng phương án B để xác nhận với auth-service trước khi lazy create.  
Dùng phương án C đẻ sync trong lúc xoá tài khoản.  
Dùng phương án D để xử lý race condition khi lazy create.

## Cách 2:
Dùng phương án E - Event-driven để các service giao tiếp với nhau qua mesage queue thay gì gọi tên thẳng.

---

## Đối với cách 1: Phương án A + B + C + D

Mỗi phương án giải quyết mỗi vấn đề khác nhau và mỗi lần cần sync dữ liệu, service này sẽ gọi service khác qua internal HTTP:

- Khi user register 1 tài khoản, auth-service tạo 1 tài khoàn với role USER và gửi 1 request tới user-service (A)
- Nếu user-service không có 1 tài khoản, user-service sẽ xác nhận với auth-service trước khi tạo ra 1 tài khoản mới.
- Khi xoá 1 tài khoản, user-service sẽ gọi auth-service để xoá tài khoản ở cả 2 database.
- Khi quá nhiều request cùng muốn tạo 1 tài khoản 1 lúc, hệ thống sẽ catch database exception để hạn chế sự trùng lặp

### Ưu điểm:
- Có thể xử lý từng method một, giải quyết cái này sẽ đến cái khác. Có thể thay đổi, thêm thắt vào tuỳ ý
- Đơn giản hoá, dễ hiếu, dễ debug. Khi sai, có thể dễ dàng trace lại xem chỗ nào sai.
- Chấc chắn xoá users ở cả hai database.

### Nhược điểm:
- Hai service hoạt động phụ thuộc vào nhau. Auth-service gọi user-service để sync tài khoàn, và user-service gọi auth-service để verification và xoá tài khoản.
- Khi 1 trong 2 service down, service còn lại cũng fail.
- Hiện tại với yêu cầu ít thì đơn giản, nhưng về lâu dài, khi có nhiều yêu cầu hơn (quản lý order, shipping, reset password, reset account) sẽ rất dễ bị rối.
- Cần phải thêm, timeout, retry, internal API security (internal-secret). Nếu thiếu các cách xử lý này dễ bị lỗi khi deploy.

---

## Đối cách 2: Phương án E

Thay vì gọi HTTP, phương án E dùng Kafka để tạo ra message queue và auth-service publish events trong khi user-service đợi các events đó và update data dựa vào các event đó.

### Ưu điểm:
- Auth-service publishes events, user-service listens the events => không phụ thuộc vào nhau. Nếu user-service tạm fail, auth-service vẫn chạy và event vẫn đợi trong queue. Trong trường hợp, stop user-service và register 1 tài khoản user thì user vẫn sẽ được register trong auth-service và không bị lỗi gì.
- Thay vì nhiều phương án khác nhau, thì trong phương án này, mọi thứ diễn ra theo kiểu: event -> produce -> consume -> update.
- Khi đã biết làm thì sẽ làm nhanh.
- Khi tạo ra các services khác, các service khác có thể nghe và đợi message từ auth-service.

### Nhược điểm:
- Có thể dễ rối. Khi debug error, phải check event, queue, và consumer behavior
- Cần dùng Kafka để triển khai phương án này

---

## Nên dùng phương án nào:

- Đối với phương án A+B+C+D, chỉ nên dùng khi hệ thống nhỏ, có thể tiện điều chỉnh bất cứ lúc nào.
- Đối với trường hợp hệ thống lớn và nhiều services cần dùng đến user data thì nên dùng phương an E về lâu dài.

---

### Mô hình Kafka:
```mermaid
graph TD
    A[Client] --> B[AuthController]
    B --> C[AuthService]
    C --> D[UserEventProducer]
    D --> E[Kafka Topic]

    E --> F[UserEventConsumer]
    F --> G[UserService]
    G --> H[User Database] 