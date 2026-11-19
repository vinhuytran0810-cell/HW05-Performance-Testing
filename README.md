# HW05 — Performance Testing

Repository bài HW05 kiểm thử hiệu năng EShop. Công cụ kiểm thử: **Apache JMeter 5.6.3**. Giai đoạn chuẩn bị đã hoàn tất về tài liệu; workflow được đánh giá **CONDITIONAL GO** để chuyển sang xây dựng Agent Skill và test plan khi sinh viên yêu cầu bước tiếp theo.

## SUT

- Source EShop nằm tại `eshop-sut/`, được quản lý riêng và không commit vào repository bài tập.
- Backend cục bộ: `http://localhost:3000`; Frontend Admin: `http://localhost:5174`.
- Đã phân tích source và thực hiện smoke test tuần tự; chưa chỉnh sửa source, tạo Agent Skill, thiết kế test plan hoặc chạy performance test.

## Workflow chính thức: Category Management & Verification

Pools: **Pool A — Authentication + Pool C — Category Management**. Giữ nguyên năm bước này cho Load, Stress, Spike và Endurance; không tự bổ sung Update Category hoặc nghiệp vụ khác.

| Thứ tự | Thao tác | Method | Endpoint | Nhóm kiểm thử |
| --- | --- | --- | --- | --- |
| 1 | Admin Login | POST | `/api/login` | Auth-heavy |
| 2 | View Categories | GET | `/api/categories` | Read-heavy |
| 3 | Create Category | POST | `/api/categories` | Transactional |
| 4 | Delete Category | DELETE | `/api/categories/:id` | Transactional |
| 5 | Verify Category Deletion | GET | `/api/categories` | Verification / Read-heavy |

Shopping Cart Management được loại vì thiếu Update/Remove/Clear Cart API và cart frontend độc lập với backend. Admin Product Management không được chọn để giữ phạm vi đơn giản hơn. Báo cáo phân tích cũ được giữ làm bằng chứng quá trình lựa chọn; năm bước hiện tại tương ứng bốn hợp đồng method–endpoint khác nhau.

## Các quyết định đã được sinh viên chốt trong yêu cầu

- Login trong **mỗi iteration**, dùng chung một tài khoản Admin hợp lệ; JWT và category ID lưu riêng theo thread.
- CSV cung cấp tên danh mục cơ sở; tên đầy đủ sinh duy nhất tại runtime theo run/engine/thread/iteration để tránh xung đột. Chưa tạo CSV.
- Cleanup trong mỗi iteration và có phương án recovery độc lập khi bị gián đoạn. Manifest/recovery và các assertion cải tiến sẽ được triển khai trong giai đoạn sau.
- Ghi nhận defect của SUT, không sửa source; không reset/reseed database, không restart backend để cleanup.
- Chỉ commit tại mốc chính của bài tập khi có yêu cầu; không commit riêng giai đoạn chuẩn bị.

Đây là các quyết định do người dùng cung cấp, không phải xác nhận rằng sinh viên đã tự kiểm tra từng dòng source hoặc toàn bộ nhận xét của AI.

## Tiến độ và bằng chứng

| Tài liệu | Trạng thái |
| --- | --- |
| [01-environment.md](reports/task1/01-environment.md) | Đã ghi nhận công cụ, port và khởi chạy môi trường. |
| [02-api-analysis.md](reports/task1/02-api-analysis.md) | Phân tích workflow cart; giữ nguyên làm lịch sử. |
| [03-category-workflow-validation.md](reports/task1/03-category-workflow-validation.md) | Workflow category smoke thành công; khuyến nghị CONDITIONAL GO. |
| [04-workflow-human-review.md](reports/task1/04-workflow-human-review.md) | Đối chiếu nhận xét AI, quyết định sinh viên đã cung cấp và nội dung còn cần review; chưa thay thế xác nhận review độc lập của sinh viên. |
| [03-category-smoke.json](audit/03-category-smoke.json) / [smoke-category.cjs](audit/smoke-category.cjs) | Bằng chứng/script gốc được bảo toàn; không sửa hoặc chạy lại trong bước hoàn tất tài liệu. |

## Hạn chế SUT cần kiểm soát

- Mutation category có JWT guard nhưng thiếu kiểm tra role admin; name không được validate và không có UNIQUE constraint. Duplicate name được phép, nhưng FR-14 chưa yêu cầu uniqueness.
- DELETE ID không tồn tại vẫn trả 200 thành công; phải xác minh absence theo đúng owned ID/name, không chỉ kiểm tra status/message.
- Xóa category không có guard bảo vệ product references; chỉ xóa record của test không có product liên quan.
- Login source tăng counter sai password +2, thời hạn khóa 180 giây, khác FR-02; không thử khóa tài khoản admin dùng chung. Token Login không có `exp`.
- Khởi động backend tự DROP/seed database; không dùng restart làm cleanup. SQLite read/write contention và độ trễ dưới tải vẫn chưa được đo.
- Script smoke gốc có hạn chế về nhãn event, assertion Login, reconciliation hữu hạn và điều kiện trạng thái admin sau chạy; chi tiết cải tiến trong báo cáo 04.

## Cấu trúc repository

```text
HW05/
├── .agents/
│   └── skills/
├── docs/
├── test-plans/
├── data/
├── scripts/
├── results/
│   ├── load/
│   ├── stress/
│   ├── spike/
│   └── endurance/
├── evidence/
│   ├── hardware/
│   ├── load/
│   ├── stress/
│   ├── spike/
│   └── endurance/
├── reports/
│   ├── task1/
│   ├── task2/
│   ├── task3/
│   └── bug-reports/
├── audit/
├── eshop-sut/                 # SUT cục bộ, bị Git bỏ qua
├── README.md
├── AGENTS.md
└── .gitignore
```

Các thư mục trống có `.gitkeep` để Git lưu cấu trúc.

| Đường dẫn | Mục đích |
| --- | --- |
| `.agents/skills/` | Dành cho Agent Skill khi được yêu cầu; hiện chưa tạo skill. |
| `docs/` | Tài liệu và ghi chú bài tập. |
| `test-plans/` | Dành cho các test plan JMeter trong giai đoạn sau. |
| `data/` | Dữ liệu kiểm thử tổng hợp, không chứa secrets hoặc dữ liệu cá nhân thật. |
| `scripts/` | Dành cho script hỗ trợ trong giai đoạn sau. |
| `results/` | Kết quả theo loại kiểm thử. |
| `evidence/` | Bằng chứng cấu hình phần cứng và từng loại kiểm thử. |
| `reports/` | Báo cáo task1, task2, task3 và báo cáo lỗi. |
| `audit/` | Nhật ký quyết định, thay đổi và thông tin truy vết. |

## Phạm vi hiện tại

Hoàn tất tài liệu chuẩn bị và thống nhất workflow; dừng sau khi cập nhật README/AGENTS và tạo báo cáo 04 để sinh viên review. Không tự chuyển sang tạo Agent Skill, CSV hoặc test plan; chờ yêu cầu tiếp theo. Không chạy lại smoke, performance test hoặc chỉnh sửa source/bằng chứng đã có trong bước này.

`.gitignore` loại trừ SUT, dependencies, database cục bộ, bản sao lưu database, file môi trường và các đường dẫn secrets phổ biến. Trước khi commit dữ liệu, kết quả hoặc bằng chứng, kiểm tra và loại bỏ mật khẩu, token, cookie phiên và thông tin nhạy cảm; Git không tự nhận diện secrets trong nội dung file.

Credentials chỉ đặt trong đường dẫn được bảo vệ như `data/credentials/`, `secrets/` hoặc file môi trường bị bỏ qua. Một file JSON tên tùy ý không tự được ignore vì có nội dung secrets. `.gitignore` không loại bỏ các file đã được Git theo dõi; cần kiểm tra tracked files ở mốc commit sau này. Không sửa bằng chứng gốc để làm sạch mà chưa được cho phép; nếu phát hiện secrets, báo vị trí và hướng xử lý, không in giá trị.

## Quy định Git

Chỉ commit tại các mốc chính của bài tập sau này khi người dùng yêu cầu. Giai đoạn chuẩn bị giữ tài liệu/bằng chứng trong workspace; không `git add`, commit hoặc push, không đề xuất lệnh staging hay commit message. Không force-add SUT, dependencies, database, backups, logs hoặc file chứa credentials.
