# Phân tích metrics
Dùng analyze-jtl.py với JTL thật, không chỉnh raw. Kiểm tra timestamp epoch ms và cấu hình start/end, elapsed ms, success boolean; malformed rows phải báo lỗi. Chọn label đúng và phân biệt HTTP samples với transaction parents, không dựa vào label để tự suy luận loại. Nếu mixed, phân tích hai tập riêng. Chỉ dùng cửa sổ thời gian xác định, ghi policy chọn sample theo start; boundary sample có thể kết thúc ngoài cửa sổ. Không suy rate từng stage bằng tổng run.

- N: số samples, error rate = failed/N ×100%. Bao gồm failed trong elapsed mặc định và công bố policy; nếu phân tích success-only thì ghi mẫu số riêng.
- Elapsed: tổng thời gian sample; Latency: đến response đầu tiên theo JMeter; Connect: thiết lập kết nối (có thể 0 khi reuse). Không hoán đổi.
- Median/p90/p95/p99: script dùng linear interpolation (h=(n-1)p), có thể khác JMeter dashboard; nêu phương pháp, không trung bình/cộng percentile. p95 không phải 95% requests fail.
- RPS = N / window seconds. Nếu không có cửa sổ rõ ràng, dùng span từ start sớm nhất đến end muộn nhất của tập chọn, ghi rõ. Per-label dùng cùng denominator để so sánh. Business TPS cần số workflow hoàn tất hoặc parent samples riêng, không chia cứng RPS cho số bước khi có lỗi/skip.
- Time buckets: script chia theo start time, có thể bị partial ở đầu/cuối; không coi peak bucket là stable throughput. So ramp/steady/recovery, p95/error/CPU/RAM/IO theo thời điểm tương ứng.
- Threshold: định nghĩa ổn định trước test (p95, error, resource, dữ liệu drift); pilot nhiều mức rồi giữ mức ứng viên 10–15 phút. Báo mức cao nhất ĐÃ kiểm chứng, không khẳng định giới hạn phần cứng tuyệt đối. Nếu thiếu resource evidence, chỉ nêu hypothesis. Không suy memory leak/SQLite lock từ latency đơn lẻ.
