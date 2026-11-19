# Thực thi và evidence
Preflight: kiểm tra SUT readiness, Java/JMeter, hardware hostname/CPU/RAM/OS, baseline, account unlocked, credential đã bảo vệ, disk space, CSV/JMX paths, quyền kiểm thử, stop conditions. Nếu account bị khóa, dừng workload, kiểm tra thời hạn thật; chỉ login đúng sau khi hết khóa, xác minh counter reset; không tự reset DB. Không cố ý khóa shared admin.

CLI ví dụ (PowerShell, thay đường dẫn bằng giá trị thật đã kiểm tra):
`& $jmeter -n -t $plan -l $jtl -j $log -e -o $html`
JTL và thư mục HTML phải mới/không ghi đè; HTML output rỗng. Kiểm tra save service CSV: timestamp, elapsed, label, success, responseCode, latency, connect, threads khi cần, không headers/body/credentials. Timestamp start/end phải biết cấu hình. Không ghi password qua tham số CLI. Chỉ chạy sau phê duyệt. Dừng khi vượt guard đã chốt; ghi thời điểm và lý do; recovery ngoài phép đo.

## Kết quả kỹ thuật
Giữ plan, CSV, lệnh, thời điểm, raw JTL đầy đủ, HTML dashboard, report view riêng và log thực tế. Đối chiếu baseline và recovery sau chạy. Thu CPU/RAM backend đúng PID và đồng bộ thời điểm đo để phục vụ Task 2; ghi nhận JMeter chạy cùng máy là yếu tố gây nhiễu. Dừng nếu thiếu điều kiện an toàn; không tạo evidence giả. Screenshot/video, Git và đóng gói nộp bài thực hiện ngoài Skill.
