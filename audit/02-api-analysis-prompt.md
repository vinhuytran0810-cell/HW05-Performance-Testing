# AI Audit — Prompt phân tích API

Ngày: 17/11/2026 (Asia/Saigon). Lưu yêu cầu người dùng làm căn cứ truy vết; đây không phải Agent Skill hoặc test plan.

AGENTS.md hiện tại quy định lưu nhật ký truy vết trong `audit/` và làm sạch thông tin nhạy cảm; không có mẫu AI Audit riêng. Yêu cầu lưu prompt/output trong lượt trước của cùng tác vụ được tiếp tục áp dụng. Output đầy đủ lưu tại `02-api-analysis-output.md`; bằng chứng thực thi đã làm sạch tại `02-api-analysis-smoke.json`.

## Prompt hiện hành (nguyên văn)

Mình đang thực hiện HW05 — Performance Testing bằng Apache JMeter 5.6.3. EShop đã được khởi chạy thành công tại localhost:3000.

Workflow đã chọn là Shopping Cart Management:

1. Login — Auth-heavy.
2. Product Listing — Read-heavy.
3. Add to Cart — Transactional.
4. Update Cart — Transactional.
5. Remove from Cart — Transactional.
6. View Cart — Verification.

Hãy đọc kỹ source code trong `eshop-sut/`, đặc biệt là `server.js`, `database.js`, `api_specification.md` và các file frontend liên quan, sau đó phân tích các nội dung sau:

### 1. Kiến trúc hệ thống

- Công nghệ backend, database và cơ chế xử lý request.
- Cách lưu trữ dữ liệu người dùng, sản phẩm và giỏ hàng.
- Giỏ hàng được lưu trong database hay trong bộ nhớ.

### 2. Phân tích API của workflow

Với từng API, xác định:

- Endpoint và HTTP method chính xác.
- Headers, request body, query/path parameters.
- Điều kiện tiên quyết.
- Response body và HTTP status code.
- Ví dụ request/response.
- Các trường hợp lỗi và điều kiện phát sinh lỗi.
- Vị trí code xử lý tương ứng trong source.

Đối chiếu đặc tả API với cách triển khai thực tế. Nếu có khác biệt thì ghi rõ.

### 3. Authentication và quản lý trạng thái

- Cách lấy token từ Login API và truyền token vào các request tiếp theo.
- Cơ chế hết hạn token và khóa tài khoản sau 3 lần đăng nhập sai.
- Cách reset trạng thái tài khoản.
- Rủi ro khi nhiều virtual users sử dụng chung tài khoản hoặc giỏ hàng.

### 4. Chuẩn bị dữ liệu kiểm thử

- Cách tạo nhiều tài khoản thử nghiệm hợp lệ mà không sửa source SUT.
- Cấu trúc CSV phù hợp cho JMeter.
- Cách lấy product ID hợp lệ và xử lý số lượng sản phẩm.
- Cách đưa dữ liệu về trạng thái ban đầu giữa các lần chạy.
- Phân tích ảnh hưởng của việc backend tự reset database khi khởi động lại.

### 5. Phân tích phục vụ Performance Testing

- Xác định cách workflow bao phủ đủ Auth-heavy, Read-heavy và Transactional.
- Các nguy cơ bottleneck tiềm năng dựa trên source code.
- Khả năng xảy ra xung đột khi nhiều virtual users chạy đồng thời.
- Đề xuất cách correlation, parameterization và assertion trong JMeter.
- Đề xuất cách tổ chức một vòng lặp workflow sao cho có thể chạy nhiều lần mà không làm sai lệch dữ liệu.

### 6. Xác minh workflow

Thực hiện smoke test tuần tự với một tài khoản demo để xác minh toàn bộ workflow từ Login đến View Cart. Ghi lại kết quả thực tế và dọn dẹp dữ liệu thử nghiệm sau khi kiểm tra.

Phân biệt rõ thông tin lấy từ đặc tả, source code, kết quả smoke test và các giả thuyết chưa được kiểm chứng.

Lưu toàn bộ kết quả phân tích vào `reports/task1/02-api-analysis.md`, viết bằng tiếng Việt, trình bày rõ ràng và đủ chi tiết để sử dụng làm đầu vào xây dựng Agent Skill và test plan JMeter.

Không chỉnh sửa source EShop, không tự reset database khi chưa được cho phép, chưa tạo Agent Skill, chưa tạo test plan, chưa chạy performance test, không commit hoặc push.

## Yêu cầu audit đã có trong cùng tác vụ

> Lưu prompt và output vào AI Audit theo quy định trong AGENTS.md.
