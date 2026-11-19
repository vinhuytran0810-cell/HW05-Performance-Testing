# Task 1 — Môi trường EShop

Thời điểm xác minh: **17/11/2026, khoảng 15:49 (Asia/Saigon, UTC+07:00)**.

## Kết luận

Backend API, frontend mua sắm và web admin đã khởi chạy và vượt qua kiểm tra khởi động/kết nối. SQLite mở được ở chế độ chỉ đọc, có dữ liệu mẫu và trả `PRAGMA quick_check = ok`. Các tiến trình được để chạy nền sau khi kiểm tra.

Phạm vi xác minh là smoke check tuần tự với một lần đăng nhập, các yêu cầu đọc API và tài nguyên frontend. Chưa kiểm tra giao diện bằng trình duyệt, chưa xác minh toàn bộ nghiệp vụ giỏ hàng, chưa thiết kế test plan và chưa chạy performance test. Không sửa source hoặc package lock của EShop.

## Công cụ và phiên bản thực tế

| Thành phần | Phiên bản | Cách xác minh |
| --- | --- | --- |
| Node.js | 24.11.1 | `node --version` |
| npm | 11.6.2 | `npm --version` |
| Java | OpenJDK Temurin 17.0.21+9, 64-bit | `java -version` |
| Apache JMeter | 5.6.3 | `jmeter.bat --version` |
| Express | 5.2.1 | `npm ls --depth=0` tại backend |
| sqlite3 (Node binding) | 6.0.1 | `npm ls --depth=0` tại backend |
| SQLite engine | 3.52.0 | `SELECT sqlite_version()` |
| React / React DOM | 19.2.6 | `npm ls --depth=0` tại hai frontend |
| Vite | 8.0.12 | Log khởi động và `npm ls --depth=0` |

Môi trường Windows, PowerShell; workspace: `E:\KiemThu\HW05`.

Đường dẫn JMeter:

```text
E:\Tools\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin\jmeter.bat
```

Lệnh kiểm tra phiên bản trong PowerShell:

```powershell
& 'E:\Tools\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin\jmeter.bat' --version
```

JMeter chỉ được gọi để xem phiên bản. Có cảnh báo deprecated package scanning của plugin; lệnh vẫn hoàn tất và hiển thị đúng 5.6.3.

## Hướng dẫn và cấu hình đã đọc

- `eshop-sut/setup_guide.md`: yêu cầu Node.js >= 18, hướng dẫn cài dependencies, backend và frontend.
- `eshop-sut/README.md`: tổng quan thành phần và yêu cầu nghiệp vụ.
- `eshop-sut/api_specification.md`: base URL và API phục vụ kiểm tra kết nối.
- Các `package.json`, hai `vite.config.js`, `backend/server.js`, `backend/database.js` và cấu hình URL API trong frontend.
- `eshop-sut/run_servers.sh` chứa đường dẫn macOS cố định và `killall node`; không sử dụng script này trên Windows.

## Dịch vụ và port

| Thành phần | URL / vị trí | Port | PID tại thời điểm kiểm tra | Trạng thái |
| --- | --- | --- | --- | --- |
| Backend API | `http://localhost:3000` | 3000 | 7900 | Đang lắng nghe; API trả 200 |
| Frontend Web | `http://localhost:5173/` | 5173 | 17500 | Vite sẵn sàng; HTML và module trả 200 |
| Frontend Admin | `http://localhost:5174/` | 5174 | 32644 | Vite sẵn sàng; HTML và module trả 200 |
| Database | `eshop-sut/backend/database.sqlite` | Không có | Trong tiến trình backend | SQLite nhúng, không cần database server riêng |

Backend lắng nghe trên địa chỉ `::`; hai frontend lắng nghe trên `::1` (localhost). PID chỉ áp dụng cho phiên chạy này và sẽ thay đổi khi khởi động lại. Frontend Mobile/Expo chưa khởi chạy; frontend web phục vụ workflow Shopping Cart Management đã chọn.

## Cài dependencies và khởi chạy lại

Đã chạy thành công `npm ci --no-audit --no-fund` lần lượt tại `backend`, `frontend-web` và `frontend-admin`, sử dụng package lock sẵn có. Backend cài 135 packages; mỗi frontend cài 225 packages. npm có cảnh báo deprecated `prebuild-install@7.1.3`, nhưng cài đặt thành công.

Nếu dependencies đã tồn tại từ phiên này thì có thể bỏ qua bước `npm ci`. Trước khi khởi chạy lại, xác nhận các port chưa được tiến trình hiện tại sử dụng.

### Backend — terminal riêng

```powershell
Set-Location 'E:\KiemThu\HW05\eshop-sut\backend'
npm ci --no-audit --no-fund
node server.js
```

**Lưu ý dữ liệu:** `server.js` nạp `database.js`; file này gọi `initDatabase()` vô điều kiện và thực hiện `DROP TABLE` rồi seed lại dữ liệu. Vì vậy mỗi lần khởi động backend sẽ reset các bảng được khai báo, ngay cả khi không chạy riêng `node database.js`. Không chạy lệnh seed riêng trong phiên thiết lập này.

Database cũ đã được sao lưu trước khi khởi chạy tại:

```text
audit/local-backups/database-before-start-20261117-154709.sqlite
SHA256: C63F00544180BA1FBB1427A9B9DD3F1784842698809972F33CE90482E7420BA6
```

Bản sao được kiểm tra có cùng SHA256 với database trước khi chạy; bị Git bỏ qua bởi quy tắc `*.sqlite`. Giữ bản sao cục bộ, không commit. Nếu cần giữ dữ liệu ở lần chạy tiếp theo, sao lưu database khi backend đã dừng trước khi khởi động lại. Chỉ khôi phục khi backend đã dừng; việc khởi động tiếp theo vẫn có thể reset dữ liệu theo source hiện tại.

### Frontend Web — terminal riêng

```powershell
Set-Location 'E:\KiemThu\HW05\eshop-sut\frontend-web'
npm ci --no-audit --no-fund
npm run dev -- --host localhost --port 5173 --strictPort
```

### Frontend Admin — terminal riêng

```powershell
Set-Location 'E:\KiemThu\HW05\eshop-sut\frontend-admin'
npm ci --no-audit --no-fund
npm run dev -- --host localhost --port 5174 --strictPort
```

`--strictPort` giúp phát hiện port bận thay vì tự chuyển sang port khác. URL API trong hai frontend đang trỏ tới `http://localhost:3000/api`, khớp backend thực tế.

Trong phiên thiết lập này, các dịch vụ chạy nền bằng `Start-Process -WindowStyle Hidden`: backend dùng `node server.js`; frontend dùng trực tiếp `node node_modules/vite/bin/vite.js` với các cờ host/port/strictPort như trên, tương đương script `npm run dev`. Log cục bộ nằm trong `audit/local-runtime/{backend,frontend-web,frontend-admin}.{stdout,stderr}.log`; các file `.log` bị Git bỏ qua. Log lỗi của cả ba dịch vụ rỗng tại thời điểm kiểm tra khởi động.

Để dừng phiên chạy nền này trong PowerShell, trước tiên đối chiếu PID vẫn thuộc các tiến trình EShop đã nêu, rồi dừng đúng các PID đó:

```powershell
Get-Process -Id 7900,17500,32644 | Select-Object Id,ProcessName,Path,StartTime
# Chỉ thực hiện sau khi xác nhận PID vẫn đúng:
Stop-Process -Id 7900,17500,32644
```

Nếu chạy bằng các terminal riêng, dùng Ctrl+C ở từng terminal để dừng.

## Kết quả kiểm tra

| Kiểm tra | Kết quả quan sát |
| --- | --- |
| SQLite mở read-only | Thành công |
| `PRAGMA quick_check` | `ok` |
| Bảng dữ liệu | `categories`, `coupon_usage`, `coupons`, `orders`, `products`, `users`; có bảng nội bộ `sqlite_sequence` |
| Dữ liệu mẫu sau khởi động | 2 users, 5 products, 3 categories, 4 coupons |
| `GET /api/products` | HTTP 200, JSON array gồm 5 sản phẩm |
| `GET /api/categories` | HTTP 200, JSON array gồm 3 danh mục |
| CORS với Origin `http://localhost:5173` | API trả `Access-Control-Allow-Origin: *` |
| `POST /api/login` | HTTP 200, có token; dùng một tài khoản user mẫu trong database cục bộ |
| `GET /api/users/me` có xác thực | HTTP 200, JSON hợp lệ |
| `GET /api/cart` có xác thực | HTTP 200, giỏ hàng rỗng |
| Web: `/`, `/src/main.jsx`, `/src/pages/Home.jsx` | Đều HTTP 200; HTML có root element, module JavaScript không rỗng |
| Admin: `/`, `/src/main.jsx`, `/src/App.jsx` | Đều HTTP 200; HTML có root element, module JavaScript không rỗng |

Thông tin đăng nhập và token chỉ được sử dụng trong bộ nhớ để smoke check, không ghi vào báo cáo. Không thực hiện thêm/sửa/xóa giỏ hàng hoặc checkout. Đăng nhập thành công có thể cập nhật trạng thái đăng nhập trong database theo logic backend.

## Giới hạn và trạng thái repository

Các kết quả trên xác nhận khả năng khởi động, phục vụ HTTP, biên dịch các module được yêu cầu và đọc database. Chưa chứng minh mọi trang hiển thị đúng trong trình duyệt hay toàn bộ yêu cầu nghiệp vụ được đáp ứng; chưa có kết luận về hiệu năng hoặc sức chịu tải.

`git -C eshop-sut status --short` sau kiểm tra chỉ ghi nhận database runtime thay đổi và các thư mục `node_modules/` mới. Không có thay đổi source hoặc package lock. Repository HW05 bỏ qua toàn bộ `eshop-sut/`, các bản sao SQLite và log runtime. Chưa commit hoặc push.
