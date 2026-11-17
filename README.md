# HW05 — Performance Testing

Repository bài HW05 kiểm thử hiệu năng EShop. Công cụ kiểm thử dự kiến: **Apache JMeter**.

## SUT

- Source EShop nằm tại `eshop-sut/`, được quản lý riêng và không commit vào repository bài tập.
- Chưa chỉnh sửa source, thiết kế test plan hoặc chạy performance test.

## Workflow đã chọn: Shopping Cart Management

| Thứ tự | Thao tác | Đặc tính |
| --- | --- | --- |
| 1 | Login | Auth-heavy |
| 2 | Product Listing | Read-heavy |
| 3 | Add to Cart | Transactional |
| 4 | Update Cart | Transactional |
| 5 | Remove from Cart | Transactional |
| 6 | View Cart | Verification |

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

Chỉ khởi tạo cấu trúc repository và các file cấu hình ban đầu. Chưa tạo Agent Skill, chưa thiết kế test plan, chưa chạy performance test và không chỉnh sửa EShop SUT.

`.gitignore` loại trừ SUT, dependencies, database cục bộ, bản sao lưu database, file môi trường và các đường dẫn secrets phổ biến. Trước khi commit dữ liệu, kết quả hoặc bằng chứng, kiểm tra và loại bỏ mật khẩu, token, cookie phiên và thông tin nhạy cảm; Git không tự nhận diện secrets trong nội dung file.
