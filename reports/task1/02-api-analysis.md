# Task 1 — Phân tích API và workflow Shopping Cart Management

Ngày phân tích: **17/11/2026, Asia/Saigon (UTC+07:00)**. SUT: `eshop-sut/`; backend đang chạy tại `http://localhost:3000`; frontend web tại `http://localhost:5173`; công cụ hiệu năng dự kiến: Apache JMeter 5.6.3.

## 1. Kết luận và quy ước bằng chứng

**Workflow sáu bước chưa thể thực hiện đầy đủ bằng API trên SUT hiện tại.** Backend chỉ triển khai đọc và thêm giỏ hàng; không có API cập nhật/xóa item hoặc xóa giỏ. Frontend web dùng giỏ React độc lập, có thêm/xóa nhưng không có sửa số lượng. Không thể dùng JMeter HTTP để mô phỏng các thao tác chỉ thay đổi React state rồi coi đó là tải backend.

Smoke test thực tế xác nhận Login, Product Listing và các thao tác frontend Add → Remove → View hoạt động với một tài khoản demo. Update bị chặn vì thiếu chức năng. Các request PUT/DELETE giả định tới giỏ hàng trả 404. Không báo workflow PASS toàn bộ; không tạo API giả, không dùng thêm lần hai thay cho Update.

Các nhãn trong báo cáo:

- **[SPEC-API]**: nội dung `eshop-sut/api_specification.md`.
- **[SPEC-SRS]**: yêu cầu nghiệp vụ trong `eshop-sut/README.md`.
- **[SRC]**: kết luận đọc từ source hiện tại; chưa mặc nhiên được xác minh bằng request.
- **[SMOKE]**: quan sát từ lần chạy tuần tự ở mục 8 và file bằng chứng.
- **[HYP]**: giả thuyết hiệu năng/xung đột cần kiểm chứng sau này.
- **[ĐỀ XUẤT]**: chuẩn bị dữ liệu và hướng triển khai JMeter về sau, chưa được thực thi.

Source được dẫn bằng đường dẫn tương đối và số dòng tại thời điểm đọc. Hash trước/sau của các file trọng yếu nằm trong [bằng chứng smoke](../../audit/02-api-analysis-smoke.json). Script kiểm tra: [smoke-shopping-cart.cjs](../../audit/smoke-shopping-cart.cjs). Không có file `.jmx`, Agent Skill hoặc performance test được tạo/chạy trong công việc này.

## 2. Kiến trúc và lưu trữ trạng thái

### 2.1 Thành phần và cơ chế request

**[SRC]** `backend/server.js:1–14,570–572` tạo một ứng dụng Express trên Node.js, port 3000, dùng `cors()` và `bodyParser.json()`. Backend CommonJS nạp một kết nối SQLite từ `database.js`, dùng `jsonwebtoken` cho xác thực. Không thấy cluster, worker HTTP, reverse proxy, cache ngoài hoặc connection pool trong source ứng dụng.

```text
JMeter HTTP / browser
        │ HTTP JSON
        ▼
Node.js / Express :3000
 ├─ JSON parser + CORS
 ├─ JWT middleware cho route được bảo vệ
 ├─ sqlite3 callbacks ──► backend/database.sqlite
 └─ userCarts[userId] ──► RAM của tiến trình Node

React / Vite :5173
 ├─ AuthContext ──► localStorage.token + Axios Authorization
 └─ CartContext ──► React state cart[] (độc lập với userCarts)
```

**[SRC]** Mã JavaScript của handler chạy trên event loop của tiến trình Node. Truy vấn `sqlite3` dùng API callback bất đồng bộ; không đồng nghĩa toàn bộ request bị thực thi tuần tự. `db.serialize()` trong `database.js` được dùng khi khởi tạo/seed. Không có giao dịch SQL `BEGIN/COMMIT` cho chuỗi thao tác workflow. JWT được ký đồng bộ (`jwt.sign` không truyền callback); việc parse/serialize JSON, push mảng và chạy handler cũng tiêu thụ thời gian event loop. Không suy ra thông lượng hoặc độ trễ từ kiến trúc này khi chưa đo.

Phiên bản đã xác minh ở báo cáo môi trường: Node.js 24.11.1; Express 5.2.1; sqlite3 binding 6.0.1; SQLite engine 3.52.0; React 19.2.6; Vite 8.0.12. Xem [01-environment.md](01-environment.md).

### 2.2 Database và dữ liệu nghiệp vụ

| Dữ liệu | Nơi lưu và cấu trúc [SRC] | Tính chất |
| --- | --- | --- |
| Users | SQLite `users`: id, name, email, password, role, login_attempts, locked_until, reset_token, shipping_address, phone | Password plaintext; email không có UNIQUE/NOT NULL hay index riêng trong schema |
| Products | SQLite `products`: id, name, price, description, imageUrl, category_id | Không có trường stock/inventory; không có kiểm soát tồn kho trong cart handler |
| Categories | SQLite `categories` | Dữ liệu danh mục |
| Orders | SQLite `orders`: user_id, total_amount, status, shipping_address, created_at | Không có bảng order_items trong schema hiện tại |
| Coupons/usage | SQLite `coupons`, `coupon_usage` | Ngoài workflow này |
| Cart backend | `const userCarts = {}`, mỗi khóa user ID là một array JSON | RAM; không có bảng cart/cart_items trong SQLite |
| Cart frontend web | `useState([])` trong `CartContext` | RAM của phiên React; không lưu localStorage/database và không đồng bộ cart API |

Tham chiếu schema: `backend/database.js:4–11,23–82`; cart backend: `backend/server.js:14,284–295`; cart web: `frontend-web/src/context/CartContext.jsx:5–25`.

**[SRC]** Cart backend theo `req.user.id`, không theo JWT string, cookie hay TCP connection. Hai token của cùng user dùng chung mảng. `GET` lần đầu tạo mảng rỗng; `POST` chỉ `push(req.body)`. Không kiểm tra product ID, tên, giá, số lượng, trùng item, stock; không tính tổng. Không lưu SQLite, không giảm số lượng sản phẩm và không tạo order.

**[SRC]** Cart web không gắn khóa user: đổi tài khoản/đăng xuất trong cùng phiên React không tự clear cart vì `AuthContext.logout()` chỉ xóa user/token. Reload/unmount ứng dụng làm mất React cart. Thêm item bằng copy mảng, xóa theo **index**, không phải product ID. Đây là hai hệ trạng thái khác nhau; không dùng GET backend cart để xác nhận item vừa thêm bằng UI web.

**[SRC]** Frontend mobile cũng dùng React state cart (`frontend-mobile/App.js:61,128–160`), có normalize quantity và gộp cùng product ID khi add; web không gộp. Không thấy lời gọi `/api/cart` trong frontend mobile/admin khi tìm source. Mobile có API URL LAN cố định; không sử dụng mobile để smoke test lần này.

### 2.3 Reset khi backend khởi động

**[SRC]** `server.js:4` nạp `database.js`; `database.js:13–20,117` gọi `initDatabase()` vô điều kiện, DROP các bảng coupon_usage, coupons, users, products, categories, orders rồi tạo/seed lại. Chỉ chạy `node server.js` cũng reset các bảng này, không cần gọi lệnh seed riêng. Đây không phải migration giữ dữ liệu.

Khởi động lại còn mất toàn bộ `userCarts` RAM. Tài khoản thử nghiệm tạo qua API sẽ biến mất, trạng thái khóa/order thay đổi, product ID và user ID có thể được tái sử dụng. JWT cũ không bị thu hồi theo database và có thể mang ID trùng bản ghi seed mới vì signing key cố định; không tái sử dụng token qua lần khởi động khác. Thứ tự seed được serialize nhưng server không có readiness gate đợi seed hoàn tất trước `app.listen`.

**Không khởi động lại, seed hoặc reset database trong công việc này.** Những ảnh hưởng trên là [SRC], không phải hành động đã chạy.

## 3. Ánh xạ workflow sang giao diện và HTTP

| Bước đã chọn | Backend thực tế [SRC] | Frontend web thực tế [SRC] | Khả năng dùng JMeter HTTP |
| --- | --- | --- | --- |
| 1. Login — Auth-heavy | `POST /api/login` | AuthContext gọi API | Có |
| 2. Product Listing — Read-heavy | `GET /api/products`, tùy chọn `search` | Home gọi API | Có |
| 3. Add to Cart — Transactional | `POST /api/cart` | Chỉ `addToCart()` trên React state | Có endpoint backend nhưng không phải request mà UI web thực sự gửi |
| 4. Update Cart — Transactional | Không có route | Không có hàm update hoặc input +/- trong Cart | Không thể thực hiện trên bản hiện tại |
| 5. Remove from Cart — Transactional | Không có route | `removeFromCart(index)` trên React state | Không tạo HTTP request backend |
| 6. View Cart — Verification | `GET /api/cart` | Render cart React, không gọi GET cart | Có endpoint backend, nhưng xác minh trạng thái backend riêng |

**[SPEC-API]** Mục 4.1/4.2 (`api_specification.md:110–127`) chỉ mô tả GET/POST cart, không hứa có PUT/DELETE. Thiếu Update/Remove trong API là khoảng trống so với workflow đã chọn và yêu cầu nghiệp vụ, không phải một endpoint PUT/DELETE đã được đặc tả nhưng bị triển khai sai method.

**[SPEC-SRS]** FR-07 (`README.md:93–100`) yêu cầu có +/- sửa quantity, thêm trùng tăng quantity, xóa có xác nhận và nhãn “Tổng cộng”. Web hiện hiển thị quantity tĩnh, thêm trùng thành dòng mới, xóa trực tiếp, nhãn “Tổng tạm tính”. Những sai khác này phải được ghi nhận trước khi chốt phạm vi performance test.

## 4. Hợp đồng API của workflow

Các ví dụ ở mục này là **[SRC: ví dụ minh họa hợp đồng]**, không phải toàn bộ response đã capture. Placeholder `<password-local>`, `<JWT>` và `<redacted>` không phải secrets thật. Dùng base URL `http://localhost:3000`, `Accept: application/json`; request có JSON body dùng `Content-Type: application/json`. GET không cần body. Không có cookie/session bắt buộc.

### 4.1 Login — POST /api/login

- **[SPEC-API]** Mục 1.2, dòng 23–32: body email/password; thành công 200 có token/user.
- **[SRC]** Handler `backend/server.js:32–66`; frontend `context/AuthContext.jsx:26–34`, `pages/Login.jsx:12–19`.
- Header: Content-Type JSON; không cần Authorization. Không có query/path parameter.
- Tiền điều kiện: user tồn tại, password khớp chính xác, `locked_until` không nằm trong tương lai, database đọc được.

```http
POST /api/login
Content-Type: application/json

{"email":"vu001@example.invalid","password":"<password-local>"}
```

Response thành công có dạng:

```json
{
  "message": "Login successful",
  "token": "<JWT>",
  "user": {
    "id": 101,
    "name": "HW05 VU 001",
    "email": "vu001@example.invalid",
    "password": "<redacted>",
    "role": "user",
    "login_attempts": 0,
    "locked_until": null,
    "reset_token": null,
    "shipping_address": null,
    "phone": null
  }
}
```

`user` là kết quả `SELECT *`, có thể lộ password/reset token. Giá trị login_attempts/locked_until trong response là bản ghi vừa đọc **trước** câu UPDATE reset; không dùng response đó để kết luận UPDATE đã hoàn tất. Handler không đợi callback UPDATE rồi mới ký/trả token.

| Trường hợp | Status/body theo [SRC] |
| --- | --- |
| Email không tồn tại hoặc password sai, chưa bị khóa | 401 `{"error":"Invalid email or password"}` |
| locked_until còn hiệu lực | 403 `{"error":"Tài khoản đã bị khóa. Vui lòng thử lại sau."}` kể cả password đúng |
| SELECT lỗi SQLite | 500 `{"error":"<SQLite error>"}` |
| Thiếu trường/body sai kiểu | Không có validation đầy đủ; kết quả phụ thuộc parser, bind và bản ghi, không mặc định là 400 chuẩn nghiệp vụ |

**[SMOKE]** Tài khoản demo đăng nhập 200; token có id/role/iat, không có exp. Không thử đăng nhập sai hoặc đo thời gian khóa trong lần này.

### 4.2 Product Listing — GET /api/products

- **[SPEC-API]** Mục 3.1, dòng 80–83, optional `search`.
- **[SRC]** `backend/server.js:141–157`; `frontend-web/src/pages/Home.jsx:12–38`.
- Không cần Authorization. Query `search` là tùy chọn; không có path parameter/body. Không triển khai pagination/limit/sort.
- Tiền điều kiện cho vòng nghiệp vụ: database có ít nhất một product hợp lệ. API vẫn có thể trả `[]` nếu không có kết quả.

```http
GET /api/products?search=iPhone
Accept: application/json
```

Response 200 là array product, ví dụ:

```json
[
  {"id":1,"name":"iPhone 15 Pro Max","price":30000000,
   "description":"Điện thoại cao cấp của Apple",
   "imageUrl":"https://placehold.co/300x300/png?text=iPhone+15","category_id":1}
]
```

**[SRC]** Có `search` khác rỗng: ghép trực tiếp vào `LIKE '%...%'`; ký tự SQL có thể gây lỗi hoặc làm thay đổi truy vấn. Nếu query lỗi: 500, body HTML `<h1>Database Error</h1><p>...</p>`, không phải JSON. Không search/search rỗng: SELECT tất cả; callback không xử lý `err` rõ ràng nên không bảo đảm JSON error contract khi DB lỗi. Các nhánh này chưa được gây lỗi trong smoke.

**[SMOKE]** GET không search trả 200, 5 sản phẩm, IDs `[1,2,3,4,5]`. Không dùng kết quả này để khẳng định tìm kiếm hoặc pagination đã được kiểm chứng.

API phụ `GET /api/products/:id` (`server.js:159–165`, `ProductDetail.jsx:14–19`): không cần token; ID ở path; có product trả 200 object; không tìm thấy trả **200 `{}`**, không phải 404. ID chẵn chuyển price thành string. Chi tiết này không được đặc tả API mô tả; lấy giá từ listing và chuẩn hóa số trước khi tạo request/cart assertion. Chưa gọi endpoint detail trong smoke này.

### 4.3 Add to Cart — POST /api/cart

- **[SPEC-API]** Mục 4.2, dòng 117–127; body id/name/price/quantity; yêu cầu token.
- **[SRC]** `backend/server.js:290–295`; middleware dòng 100–110.
- Header: Authorization `Bearer <JWT>`, Content-Type JSON. Không có query/path parameter.
- Tiền điều kiện kỹ thuật: JWT verify thành công. Handler không truy vấn user/product database và không yêu cầu item hợp lệ. Tiền điều kiện nghiệp vụ nên áp dụng ở bộ dữ liệu: ID tồn tại, name/price đúng catalog, quantity nguyên dương.

```http
POST /api/cart
Authorization: Bearer <JWT>
Content-Type: application/json

{"id":1,"name":"iPhone 15 Pro Max","price":30000000,"quantity":1}
```

Response 200: `{"message":"Added to cart"}`. Không trả cart item ID mới, cart version, quantity đã lưu hoặc tổng tiền. `id` trong body là product ID do client gửi, không phải server-generated cart row ID. Add trùng không tăng quantity mà push một object mới. Giá trị JSON không hợp lệ về nghiệp vụ không có nhánh 400/404 riêng; không suy ra việc backend kiểm tra stock.

Lỗi xác thực: thiếu token 401 Unauthorized; token sai/verify lỗi 403 Forbidden. JSON không parse được chịu lỗi middleware; không có xử lý lỗi nghiệp vụ riêng cho item.

**[SMOKE]** Không gửi POST backend cart: source không có endpoint dọn item và người dùng cấm tự restart/reset. Việc thêm giỏ **web** đã được chạy và xác nhận 1 dòng quantity 1; đây không phải bằng chứng POST API hoạt động. Network trace web không có request `/api/cart`.

### 4.4 Update Cart — không có API hoặc chức năng web

**[SRC]** Toàn bộ `server.js` không đăng ký PUT/PATCH cart. `CartContext.jsx:5–34` không có updateQuantity; `Cart.jsx:43–57` chỉ render quantity và nút Xóa. Không có endpoint, headers/body hay response thành công được hỗ trợ cho bước này; không được tự đặt hợp đồng giả.

Để xác minh thiếu route, đã gửi hai **probe ứng viên**, không phải API được đặc tả:

```http
PUT /api/cart
Authorization: Bearer <JWT>
Content-Type: application/json

{"id":1,"quantity":2}
```

Và `PUT /api/cart/1` với `{"quantity":2}`. **[SMOKE]** Cả hai trả 404 `text/html; charset=utf-8`, response Express chứa `Cannot PUT /api/cart` hoặc `Cannot PUT /api/cart/1`. Thiếu route xảy ra trước khi có cart handler/middleware riêng, không phải 401/403 của authenticateToken.

Kết quả bước Update: **BLOCKED_MISSING_FUNCTION**. Không dùng POST thêm lại hoặc sửa giá trị trong biến JMeter để tuyên bố đã update SUT.

### 4.5 Remove from Cart — không có API backend

**[SPEC-API]** Không có mô tả DELETE cart. **[SRC]** Backend không đăng ký DELETE cart; frontend `CartContext.jsx:12–16` dùng `splice(index,1)` trên bản sao mảng; `Cart.jsx:50–55` gọi hàm đó. Không có HTTP headers/body/status thành công đối với thao tác React này.

Probe `DELETE /api/cart/1` và `DELETE /api/cart` có Authorization, không body: **[SMOKE]** trả 404 HTML Express chứa `Cannot DELETE ...`. Không suy ra `1` là một cart row ID; nó chỉ là product ID được chọn cho probe.

**[SMOKE]** Nhấn Xóa ở giao diện web đã bỏ dòng duy nhất, không có request DELETE và không có dialog xác nhận. Vì vậy Remove UI thành công trong khi Remove HTTP chưa được hỗ trợ.

### 4.6 View Cart — GET /api/cart

- **[SPEC-API]** Mục 4.1, dòng 110–116.
- **[SRC]** `backend/server.js:284–288`.
- Header Authorization Bearer; không query/path/body. JWT hợp lệ là điều kiện kỹ thuật; không kiểm tra user còn tồn tại trong DB.

```http
GET /api/cart
Authorization: Bearer <JWT>
Accept: application/json
```

Response 200 là array nguyên trạng các object đã POST; chưa có item trả `[]`. Ví dụ sau một Add đúng body ở trên có thể là:

```json
[{"id":1,"name":"iPhone 15 Pro Max","price":30000000,"quantity":1}]
```

Ví dụ này là [SRC], không phải response thực tế đã tạo trong lần smoke. GET không đọc product table, không tự tính totals hoặc sửa dữ liệu sai kiểu.

| Lỗi xác thực chung [SRC; hai trường hợp đầu đã SMOKE] | Status/body |
| --- | --- |
| Không có Authorization hoặc không lấy được phần token thứ hai | 401 `{"error":"Unauthorized"}` |
| Token malformed, sai chữ ký hoặc không verify được | 403 `{"error":"Forbidden"}` |
| Token có exp đã hết hạn nếu có ai cấp token như vậy | jwt.verify từ chối 403; Login SUT không tạo exp nên chưa có ca hết hạn tự nhiên để kiểm chứng |

**[SMOKE]** Backend GET trước và sau đều 200 `[]`, unchanged=true. Giao diện web View cuối hiển thị “Giỏ hàng của bạn đang trống” sau khi xóa React cart; hai kết quả được ghi nhận độc lập.

## 5. Authentication, khóa tài khoản và chia sẻ trạng thái

### 5.1 Correlation token và hết hạn

**[SRC]** Đọc `token` ở JSON root của Login, lưu riêng theo virtual user, gửi `Authorization: Bearer <token>` cho GET/POST cart và các API bảo vệ. JSON parser không cần cookie. Middleware tách header bằng `split(' ')[1]`; không xác minh từ đầu header có đúng chữ Bearer hay không, nhưng client vẫn phải gửi chuẩn Bearer.

JWT chứa `{id,role}` và `iat` do thư viện thêm. Không truyền `expiresIn`, không có exp do Login tạo, không có logout/revoke/refresh endpoint. Không kết luận token hết hạn sau 30 phút/1 giờ. Signing key hardcode trong source, không chép giá trị key vào báo cáo. Khóa tài khoản/password reset/deleting user không được middleware kiểm tra lại khi dùng một JWT đã cấp; không mặc định thu hồi token cũ.

**[SMOKE]** JWT demo có iat và không có exp. Thử không token trả 401; token chuỗi không hợp lệ trả 403. Không giả mạo token, không thử thay role hoặc khai thác signing key.

Frontend `AuthContext.jsx:8–24,26–41` lưu token vào localStorage, gắn Axios default Authorization, gọi `/api/users/me` để lấy user khi có token và logout nếu lời gọi đó thất bại. Logout là thao tác client, không dọn cart backend. Login response và `/api/users/me` có thể chứa trường nhạy cảm vì trả full user; không lưu response body thô vào JTL/audit.

### 5.2 Khóa tài khoản: đặc tả khác source

| Hành vi | [SPEC-SRS] FR-02 | [SRC] `server.js:32–66` |
| --- | --- | --- |
| Bộ đếm mỗi lần sai | +1 | **+2** |
| Ngưỡng | 3 lần sai liên tiếp | Điều kiện counter >=3; bắt đầu từ 0, lần sai thứ hai có thể đạt 4 |
| Thời gian khóa demo | 30 giây | **180000 ms = 180 giây** |
| Request gây vượt ngưỡng | Không ghi chi tiết mã | Vẫn trả 401; request tiếp theo khi khóa đang hiệu lực trả 403 |
| Password đúng khi đang khóa | Không nêu chi tiết | Bị chặn trước so password; không reset sớm |
| Đăng nhập đúng khi hết/không khóa | Không nêu reset chi tiết | Lập lịch UPDATE counter=0, locked_until=NULL |

Mô tả “lần sai thứ hai khóa” là suy luận cho các request **tuần tự và UPDATE trước đã hoàn tất**, không phải kết quả đo smoke hoặc bảo đảm khi request cạnh tranh. db.get → tính toán counter → db.run là read-modify-write rời, chưa có atomic increment; UPDATE không được await/callback trước response. Có thể gặp lost update khi nhiều request cùng tài khoản. Login đồng thời đúng/sai cũng có thể ghi đè trạng thái nhau [HYP].

### 5.3 Đưa account về trạng thái hợp lệ

**[ĐỀ XUẤT]** Ưu tiên tránh negative login trong workload dương. Nếu bị khóa, chờ qua locked_until theo giá trị thực tế rồi login đúng; không liên tục gửi sai vì sẽ tăng counter/tái khóa. Hết thời gian chờ không tự đặt counter về 0; một lần login đúng khi không còn khóa mới lập lịch reset. Đợi xác nhận trạng thái cập nhật xong trước đo tiếp.

Không có API unlock/reset-attempts chuyên biệt. `POST /api/reset-password` (`server.js:87–98`) chỉ sửa password và reset_token, **không clear login_attempts/locked_until**. Không dùng nó thay unlock. Cũng không restart backend để reset account vì sẽ reset dữ liệu toàn hệ thống.

Nếu sau này cần cleanup có kiểm soát: có thể dùng thao tác quản trị database được cho phép riêng, scope chính xác IDs của tài khoản synthetic và chỉ UPDATE login_attempts/locked_until, sau khi dừng workload và chụp baseline; không phải chức năng API hiện có. Chưa thực hiện thao tác này.

### 5.4 Nhiều virtual users dùng chung account

- **[SRC]** Cùng user ID → cùng `userCarts[userId]`, kể cả token khác. Add của một VU làm sai cart count/quantity của VU khác; login lại không tạo cart mới.
- **[HYP]** Thứ tự push giữa request khác nhau không xác định theo thứ tự thread JMeter. Push đồng bộ trong một tiến trình không bị xen giữa từng lệnh, nhưng chuỗi business steps không có transaction/isolation/version check.
- **[HYP]** Cùng account làm login counter và khóa bị tác động chéo, cả khi dùng thread-local token. GET rồi kiểm tra length không còn đáng tin nếu thread khác vừa add.
- **[SRC]** Dùng chung browser React session có thể còn cart khi đổi account. Không dùng thao tác UI trên cùng tab để giả lập nhiều người dùng độc lập.
- **[ĐỀ XUẤT]** Một account riêng cho mỗi VU đang hoạt động, không chia sẻ token qua global JMeter properties. Mỗi load generator có namespace dữ liệu riêng.

## 6. Chuẩn bị dữ liệu và quản lý baseline

### 6.1 Tạo nhiều user hợp lệ qua API, không sửa source

**[SRC]** `POST /api/register` (`server.js:20–30`, SPEC-API:11–21) nhận `{name,email,password}`, thực hiện INSERT parameterized, response 200 `{"message":"User registered successfully","id":...}`; SQLite lỗi trả 500 JSON. Không cần token. Không có check độ mạnh password, email format, duplicate email hoặc confirmation; schema email không UNIQUE.

**[ĐỀ XUẤT]** Sinh tài khoản synthetic theo run/engine/VU: `hw05_<run>_<engine>_<vu>@example.invalid`, tên tương ứng, password ngẫu nhiên đủ >=8 ký tự, chữ hoa/thường/số và ký tự đặc biệt để hợp lệ theo FR-01. Gọi Register tuần tự ở giai đoạn chuẩn bị ngoài khoảng đo, lưu ID được trả về, xác nhận Login đúng user và trạng thái account. Không cần tạo user trực tiếp bằng SQL. Không gọi Register trong mọi iteration đo nếu mục tiêu là Login/cart.

Backend thực tế không ngăn email trùng nên check duplicate từ manifest trước khi register/retry; nếu timeout không chắc INSERT đã xảy ra, kiểm tra trước thay vì gửi lại mù quáng. Login dùng SELECT theo email; trùng email sẽ làm mapping VU không rõ ràng.

**[SRC]** `frontend-web/src/pages/Register.jsx:15–19` dùng regex yêu cầu whitespace và chỉ cho chữ/số/whitespace, ngược với yêu cầu ký tự đặc biệt thông thường. Vì vậy đăng ký trực tiếp qua API là cách chuẩn bị phù hợp cho tài khoản hợp lệ theo SRS; không làm yếu password để vượt lỗi UI. Lần smoke này dùng tài khoản demo sẵn có, **không tạo nhiều user hoặc tạo tài khoản mới**.

### 6.2 CSV đề xuất cho JMeter

Đây là **mẫu cấu trúc**, chưa tạo CSV credentials thật:

```csv
run_id,engine_id,vu_id,user_id,email,password,quantity_initial,quantity_updated
run001,lg01,001,101,hw05_run001_lg01_001@example.invalid,<local-secret-001>,1,2
run001,lg01,002,102,hw05_run001_lg01_002@example.invalid,<local-secret-002>,1,2
```

IDs trên chỉ minh họa; điền ID thực do Register trả về. Dữ liệu credentials thật đặt trong `data/credentials/` vốn bị `.gitignore` bỏ qua. Không commit password/token; catalog không chứa secrets có thể lưu riêng trong `data/`. UTF-8, header đúng biến, không thiếu cột; bật quoted data nếu giá trị có dấu phẩy/quote. Token là dữ liệu correlation runtime, không đưa token dùng chung vào CSV.

Tài liệu Apache xác nhận CSV Data Set Config chia dòng thành biến, hỗ trợ header/quoted values và các chế độ chia sẻ; EOF behavior phụ thuộc Recycle/Stop Thread. [Apache CSV Data Set Config](https://jmeter.apache.org/usermanual/component_reference.html#CSV_Data_Set_Config).

**[ĐỀ XUẤT]** Nếu dùng một CSV cấp phát account mới theo iteration: All threads, Recycle=false, Stop thread on EOF=true, và đủ row không trùng cho toàn bộ phạm vi dự kiến. CSV đọc ở đầu iteration nên account có thể đổi mỗi vòng; không nhầm với một account cố định cho mỗi thread. Với distributed generators, phải chia file/namespace riêng, All threads không đảm bảo uniqueness giữa JVM.

Nếu muốn **cố định account theo thread** qua nhiều vòng: dùng file một dòng dành riêng cho từng engine/thread, ví dụ `data/credentials/${engine_id}/user-${__threadNum}.csv`, Sharing mode Current thread, Recycle=true để cùng thread đọc lại đúng account của nó; không cho nhiều thread trỏ cùng một file một dòng. Cách này đảm bảo identity theo thread nhưng **không giải quyết cleanup cart API đang thiếu**. Chưa cấu hình hoặc chạy các lựa chọn này.

### 6.3 Product ID và quantity

**[ĐỀ XUẤT]** Lấy danh sách product qua GET products, assert array không rỗng rồi chọn một object. Correlate id/name/price từ **cùng object**; không chọn từng field ngẫu nhiên độc lập. Không hardcode ID=1 cho mọi dataset; seed có thể khác sau thay đổi dữ liệu. Smoke hiện có IDs 1–5, đây là snapshot chứ không bảo đảm tương lai.

Price chuyển sang số hữu hạn; quantity_initial và quantity_updated là số nguyên dương, ví dụ 1 và 2, không gửi chuỗi/NaN/âm/0. JSON body phải serialize đúng để escape tên sản phẩm; không ghép trực tiếp chuỗi chưa escape. Không dùng quantity của item khác hoặc cart length làm quantity. `products` không có stock nên không thể thiết kế kiểm tra trừ tồn kho dựa trên schema hiện tại.

Nếu API detail được dùng, nhớ price ID chẵn là string; nếu không tìm thấy 200 `{}` phải fail assertion dù status 200. Không coi POST cart chấp nhận dữ liệu là chứng minh product tồn tại; handler không validate.

### 6.4 Baseline và cleanup giữa các lần chạy

| Loại trạng thái | Cách quản lý được hỗ trợ / giới hạn [SRC; đề xuất chưa thực thi] |
| --- | --- |
| Web cart | Xóa từng dòng bằng React handler, xác nhận empty; dùng browser context riêng; reload chỉ reset state client |
| Backend cart | Không có delete/clear/update API; GET/POST không đưa cart về rỗng; login/logout không clear |
| Account | Giữ account riêng, chỉ login đúng; nếu khóa thì chờ hết và login đúng; xác nhận baseline |
| Users tạo mới | Có DELETE `/api/admin/users/:id` nhưng cần scope ID và quyền quản trị phù hợp; xóa user không xóa userCarts trong RAM |
| Products | Chỉ đọc trong workflow; không cần sửa/khôi phục; lưu snapshot lựa chọn và số lượng catalog |
| Orders | Không checkout trong workflow này, tránh tạo thêm order để “dọn cart” |

**[SRC]** Checkout (`server.js:297–309`) chỉ INSERT orders, không clear backend cart. Web Checkout nhận `clearCart` nhưng không gọi (`Checkout.jsx:8,40–66`); không dùng checkout như cleanup. Delete user (`server.js:504–508`) chỉ xóa SQLite row, không xóa khóa RAM; token cũ còn verify được. Tạo account mới mỗi loop có thể cô lập kết quả nhưng để lại user/cart tăng dần, không tương đương baseline ổn định cho endurance.

Không có cách chính thức qua HTTP để lặp full sáu bước và reset cart backend trên bản hiện tại. Phải ghi hạn chế này vào đầu vào test plan, thay vì giấu bằng restart, thêm lại item, xóa account hoặc đổi user liên tục.

Nếu được người dùng cho phép sau này, có thể thống nhất một reset toàn hệ thống ở ranh giới các run, sau backup và khi không còn người dùng khác; khi đó cần tạo lại user, lấy lại catalog, login lấy token mới, xác nhận readiness/baseline và loại thời gian chuẩn bị khỏi phép đo. **Hiện chưa được phép và chưa thực hiện**. Chạy restart giữa các iteration sẽ làm sai dữ liệu và đưa chi phí khởi động/seed vào kết quả.

## 7. Đầu vào Performance Testing và khuyến nghị JMeter

### 7.1 Mức bao phủ workload

- **Auth-heavy**: Login đọc user, so password plaintext, lập lịch UPDATE trạng thái và ký JWT. Không có bcrypt/Argon2, vì vậy không gán chi phí password hashing cho SUT này.
- **Read-heavy**: Product Listing đọc SQLite rồi serialize toàn bộ catalog; tìm kiếm LIKE là một biến thể riêng. GET cart đọc RAM, không đại diện tải đọc database.
- **Transactional theo nghiệp vụ**: Add thay đổi RAM; Update/Remove backend chưa có. Không phải transaction SQL/ghi database hoặc kiểm tra stock. Label “Transactional” mô tả ý định workflow, không chứng minh tải ghi SQLite.
- **Verification**: GET cart có thể xác minh những Add gửi qua API; giao diện web xác minh React cart riêng.

**[ĐỀ XUẤT]** Muốn giữ đủ sáu bước bằng HTTP cần phiên bản SUT có hợp đồng Update/Remove/cleanup và UI mapping được xác nhận, hoặc được người dùng chấp thuận đổi workflow/phạm vi. Chưa sửa SUT và chưa tự chọn thay workflow. Chỉ có thể mô tả tạm luồng backend hỗ trợ Login → Listing → Add → GET Cart với giới hạn cleanup; không gọi đó là full Shopping Cart Management.

### 7.2 Bottleneck và xung đột tiềm năng — chưa đo

| Giả thuyết [HYP] | Căn cứ [SRC] | Cần quan sát sau khi được phép kiểm thử |
| --- | --- | --- |
| Event loop/CPU khi login và serialize JSON | Một tiến trình, JWT sign đồng bộ, JSON parser/response serialization | CPU, event-loop lag, p95/p99, queue và phân bố status |
| Hàng đợi/khóa ghi SQLite khi nhiều login | Một connection, UPDATE trạng thái cho mỗi login thành công; SQLite file chung | Thời gian query, write contention, SQLITE_BUSY/IO, CPU; chưa có bằng chứng đang gặp bottleneck |
| Chậm lookup user khi tăng số tài khoản | Email không có index riêng; SELECT theo email | Tăng kích thước dữ liệu có kiểm soát, query plan và latency; không suy từ 2 user seed |
| Listing/search tăng chi phí theo catalog | SELECT *, không pagination, LIKE có wildcard đầu, string SQL trực tiếp | Rows/response bytes, read time, serialization, heap/GC |
| Heap tăng dần qua run dài | userCarts không TTL/clear; POST append vô hạn; GET user mới tạo một mảng | RSS/heap/GC, số user/cart/items; phát hiện dữ liệu drift trước khi kết luận leak |
| GET cart ngày càng chậm | Serialize toàn bộ array tích lũy | Cart size/response bytes so với latency, không chỉ throughput |
| Lost update login/khóa chéo | SELECT rồi UPDATE counter không atomic, account dùng chung | Lịch request cạnh tranh và counter cuối; không gây lockout trên account demo chung |
| Sai/không ổn định assertion cart | Account dùng chung, add trùng không merge, không cart version | Identity theo VU, baseline count, nội dung thực tế theo từng request |
| Mất đồng bộ nếu nhiều tiến trình | Cart chỉ nằm RAM local; không shared store | Chỉ là giả thuyết kiến trúc mở rộng; hiện source khởi một process, chưa có cluster để kiểm thử |
| Máy phát tải cạnh tranh SUT | JMeter/backend/browser có thể cùng máy | CPU/RAM/IO từng tiến trình và cấu hình phần cứng; không mặc định SUT là nguyên nhân |

Catalog chỉ 5 sản phẩm và 2 user seed không đại diện dữ liệu lớn. Không đưa ra TPS tối đa, ngưỡng chịu tải, SLA hoặc kết luận bottleneck từ lần smoke vài request.

### 7.3 Correlation, parameterization và assertions

Tài liệu chính thức mô tả JSON Extractor dùng JSONPath và JSR223 Assertion cho assertion bằng script; đây là các thành phần tham chiếu cho giai đoạn sau. [Apache JSON Extractor](https://jmeter.apache.org/usermanual/component_reference.html#JSON_Extractor), [Apache JSR223 Assertion](https://jmeter.apache.org/usermanual/component_reference.html#JSR223_Assertion).

**[ĐỀ XUẤT từ source SUT]**:

| Bước | Correlation/parameterization đề xuất | Assertion cần có |
| --- | --- | --- |
| Login | email/password riêng từng VU; lấy `$.token` vào biến thread `token`, `$.user.id` vào `user_id`; default sentinel `TOKEN_NOT_FOUND` | 200, JSON object, message, token không rỗng/sentinel, user_id đúng dataset; không assert exp tồn tại |
| Listing | JSON array; chọn object rồi lấy cùng bộ id/name/price; query search qua Parameters có URL encoding | 200, JSON array không rỗng, ID hợp lệ, name không rỗng, price chuyển số được; không nhận HTML error làm dữ liệu |
| Add API (chỉ khi chấp thuận cleanup) | Body JSON từ object đã chọn + quantity; Header Manager Bearer token trong scope các sampler bảo vệ | 200 và message chính xác; tiếp theo GET xác minh nội dung, không chỉ dựa vào message |
| Update/Remove | Chưa có contract; không tạo sampler giả, không chấp nhận 404 như thành công nghiệp vụ | Đánh dấu BLOCKED trước khi chạy scenario; không thay quantity trong biến để giả update |
| GET cart | Token cùng VU; snapshot baseline và object đã thêm | 200 JSON array; kiểm tra đúng product/name/price/quantity và count tăng đúng; duplicate hiện tại là thêm dòng, không merge |
| Negative auth riêng | Tách với workload dương; không đăng nhập sai hàng loạt | Thiếu token 401 Unauthorized; token sai 403 Forbidden |

Nếu dùng JSON Extractor lấy id của phần tử đầu, biểu thức là `$[0].id`; cần lấy name/price đúng phần tử đó. Đối với chọn ngẫu nhiên, tốt hơn parse response một lần trong Groovy, chọn object và serialize body cùng object; không đặt Match No random riêng cho từng field. Dùng `vars` thread-local để giữ token/data, không dùng `props` chung cho token. Với script tái sử dụng, đọc biến qua `vars.get(...)` thay vì chèn trực tiếp secrets vào code script/log.

Không lưu đầy đủ Login/user response hoặc Authorization trong JTL/View Results Tree/log; chỉ lưu status, error category, assertion outcome và dữ liệu đã làm sạch. Assertions hiệu năng cần SLA do bài tập/người dùng chốt; chưa tự đặt duration threshold để kết luận đạt/trượt.

Nếu sau này dùng Transaction Controller, phải tách sampler hỗ trợ/baseline/setup ra khỏi số liệu business transaction, định nghĩa rõ parent/child sample, pacing/think time và login mỗi iteration hay chỉ một lần mỗi session. Hai chính sách login tạo tải auth khác nhau; cần báo cáo lựa chọn. Đây là đề xuất phân tích, chưa tạo cấu trúc test plan.

### 7.4 Một vòng lặp có thể lặp lại: điều kiện và giới hạn

**Luồng mục tiêu sau khi SUT có đủ chức năng**: lấy account riêng → Login → Listing/chọn product → baseline cart rỗng → Add quantity=1 → Update quantity=2 → Remove item → GET xác nhận cart rỗng. Giữ ID và token đúng cùng VU; reset biến correlation mỗi vòng, fail fast nếu login/listing thất bại; cleanup idempotent ở nhánh finally nếu contract hỗ trợ. Không tiếp tục request với token/ID cũ sau extractor thất bại. Mỗi vòng kết thúc cùng baseline thì mới so sánh ổn định giữa load/stress/spike/endurance.

**Trên SUT hiện tại**: luồng trên **không khả thi bằng HTTP** do thiếu Update/Remove/clear. Luồng UI Login → Listing → Add → Remove → View có thể lặp trong browser context riêng, nhưng vẫn thiếu Update và không tạo tải cart backend. Luồng API Login → Listing → Add → GET làm cart lớn dần, nên chỉ có thể sử dụng khi người dùng chấp nhận scope và mô hình tích lũy cùng kế hoạch reset riêng; không đề xuất chạy ngay cho endurance hoặc giả định dữ liệu bất biến.

## 8. Smoke test thực tế và cleanup

### 8.1 Phương pháp

**[SMOKE]** Thực hiện **17/11/2026 16:11:51–16:11:54 (UTC+07:00)**; timestamp UTC tương ứng `2026-11-17T09:11:51.729Z` đến `09:11:54.283Z` nằm trong JSON bằng chứng. Một tài khoản demo có sẵn `user_id=2`, không tạo tài khoản mới. Script đọc credential demo từ SQLite bằng connection **OPEN_READONLY**, chỉ dùng trong bộ nhớ; không require/import `backend/database.js` vì import đó sẽ seed/reset. Không in credential/token.

API smoke dùng fetch tuần tự; trình duyệt thật **Chrome headless 157.0.8103.78** với profile tạm độc lập truy cập web đang chạy. Công cụ browser tích hợp không khởi tạo được do lỗi sandbox; đã chuyển sang headless Chrome cục bộ. Script quan sát DOM, thao tác form/nút và thu network URL/method đã bỏ headers/body. Không dùng JMeter, không lặp tạo tải. Browser có thể tự gửi preflight và các request effect; React StrictMode có thể tạo request listing lặp, không đồng nghĩa đang chạy performance workload.

Trước Login, account có counter=0, locked_until=null. Nếu demo đã khóa hoặc có trạng thái đăng nhập khác baseline, script sẽ dừng thay vì reset nó. Có hai lần Login hợp lệ cho cùng tài khoản: một lần API và một lần qua form UI. Không thử password sai.

### 8.2 Kết quả theo bước

| Bước/kiểm tra | Quan sát thực tế | Đánh giá |
| --- | --- | --- |
| API Login | 200, token tồn tại; id=2, role=user, iat có, exp không có | PASS |
| API Product Listing | 200, 5 products, IDs 1–5 | PASS |
| Baseline backend cart | GET 200, count=0 | PASS |
| UI Login → Listing | Form login chuyển về home, token client tồn tại, có nút thêm sản phẩm | PASS |
| UI Add | Chọn sản phẩm đầu; cart có 1 dòng iPhone 15 Pro Max, price 30000000, quantity=1 | PASS cho React cart |
| API Add | Không gửi POST cart vì không có cleanup an toàn qua API | NOT EXECUTED; chỉ phân tích [SRC] |
| UI Update | Cart không có input number, chỉ có nút Xóa trong dòng | BLOCKED_MISSING_FUNCTION |
| API Update probe | PUT `/api/cart` và PUT `/api/cart/1` đều 404 HTML Cannot PUT | Xác nhận thiếu route; không PASS business Update |
| API Remove probe | DELETE `/api/cart/1` và DELETE `/api/cart` đều 404 HTML Cannot DELETE | Xác nhận thiếu route |
| UI Remove | Nhấn Xóa, số dòng còn 0 | PASS cho React cart |
| UI View | “Giỏ hàng của bạn đang trống”, có “Tiếp tục mua sắm” | PASS cho React cart |
| Không token / token sai | GET cart trả 401 Unauthorized / 403 Forbidden | PASS auth guard tương ứng |
| GET cart cuối | 200 `[]`, cùng nội dung baseline | Backend cart không thay đổi |

Network trace ghi nhận POST login, GET users/me, GET products và OPTIONS liên quan; **không có request `/api/cart` từ frontend**. Đây là bằng chứng thực thi hỗ trợ kết luận hai loại cart không đồng bộ, thay vì suy từ UI có tên “giỏ hàng”.

### 8.3 Dọn dẹp và bảo toàn dữ liệu

- Đã xóa dòng vừa thêm trên web, xác nhận cart rỗng, logout và xác nhận localStorage không còn token.
- Đã đóng Chrome headless/profile riêng và xóa thư mục profile tạm: `temporaryProfileRemoved=true`.
- Cart backend trước/sau đều rỗng, không POST item vào backend; GET ban đầu có thể tạo khóa RAM với mảng rỗng theo handler nhưng không tạo item.
- Account trước/sau đều `login_attempts=0`, `locked_until=null`: `accountStateUnchanged=true`. Login đúng vẫn chạy UPDATE theo source; không khẳng định byte-level database file bất biến.
- Không tạo user/order/product mới, không checkout, không seed/restart/reset database, không thao tác ghi SQLite trực tiếp.
- Hash trước/sau các source trọng yếu và package locks bằng nhau: `sourceUnchanged=true`. `git -C eshop-sut diff --stat` chỉ có database runtime đã thay đổi từ phiên trước, không có source diff.

**Không thể xác minh Update như một hành động thành công vì chức năng chưa tồn tại.** Smoke không chứng minh POST backend cart thành công, khả năng lockout theo thời gian, lỗi database, hoạt động mobile, cạnh tranh đa VU hoặc bất kỳ kết quả hiệu năng nào.

## 9. Các điểm cần chốt trước Agent Skill/test plan

1. Giữ workflow sáu bước thì cần SUT có Update/Remove/cleanup backend; không tự sửa source trong phạm vi hiện tại.
2. Quyết định mục tiêu là hành vi web thực tế hay backend cart API độc lập; hai loại tải khác nhau.
3. Chốt reset policy có phép, ownership dữ liệu và baseline giữa run; không tự restart backend.
4. Chốt identity per VU, quy mô catalog/user và chính sách Login mỗi vòng/session; kiểm tra account chưa khóa.
5. Tách bug nghiệp vụ (thiếu Update/Remove, trùng dòng, lockout sai) khỏi lỗi hiệu năng; không dùng 404 để báo throughput nghiệp vụ thành công.

Đã lưu báo cáo và bằng chứng làm đầu vào cho giai đoạn sau. Chưa tạo Agent Skill/test plan, chưa chạy performance test, chưa commit/push.
