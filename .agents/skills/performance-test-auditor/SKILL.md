---
name: performance-test-auditor
description: Thiết kế CSV, Load/Stress/Spike/Endurance bằng JMeter, audit JMX và phân tích JTL, kiểm chứng nhận định hiệu năng REST API. Dùng cho Task 1 và Task 2.
---
# Performance Test Auditor — V3

## Phạm vi
Chỉ hỗ trợ Task 1 (thiết kế, kiểm tra và thực thi performance test) và Task 2 (phân tích log, kiểm chứng nhận định AI và tối ưu hóa).

Đọc AGENTS.md, README và hợp đồng API/workflow đã phê duyệt của dự án. Dùng các báo cáo có sẵn làm đầu vào, không làm lại phân tích API hoặc smoke test chuẩn bị. Nếu thiếu hợp đồng quan trọng, báo BLOCKED; chỉ đối chiếu source có mục tiêu khi cần. Không tự đổi workflow. CSV chưa có thì bắt đầu từ CSV khi được giao; CSV đã duyệt thì dùng lại.

## Chọn đúng nhiệm vụ
| Nhiệm vụ | Đọc | Đầu ra |
|---|---|---|
| Task 1: CSV và thiết kế scenario | references/task1-test-design.md | CSV hoặc thiết kế/JMX theo yêu cầu |
| Task 1: correlation, assertions, cleanup | references/task1-correlation-cleanup.md | Guards, manifest, recovery |
| Task 1: audit và chạy | references/task1-execution.md | Audit, JTL, HTML thật |
| Task 2: metrics | references/task2-metrics.md | Thống kê và threshold có căn cứ |
| Task 2: kiểm chứng AI | references/task2-analysis-review.md | Bảng claim và optimization review |

Chỉ làm bước được giao; đọc reference liên quan. Với JMX có mutation phải đọc cả correlation-cleanup trước khi thiết kế. Dùng assets/test-plan.md và assets/analysis-review.md khi cần báo cáo kỹ thuật.

## Cổng kiểm soát
- Trước tải: người dùng duyệt workload, baseline, quyền ghi, credentials, điều kiện dừng và recovery. Không tự chạy tải hoặc cài plugin. Chỉ chạy SUT được cho phép.
- Mutation: ownership, tên/khóa duy nhất, manifest có thể recovery; không xóa theo prefix, không sửa SUT/database để vượt lỗi. Không tiếp tục khi thiếu guard an toàn.
- Phân tích: xác minh label, sample-kind, timestamp, cửa sổ đo và resource evidence. Không bịa kết quả, ngưỡng, lỗi AI hay kết luận bottleneck. Gắn [SPEC], [SRC], [SMOKE], [MEASURED], [HYP] hoặc [ĐỀ XUẤT] theo nguồn.
- Giữ nguyên raw evidence; không ghi credentials/JWT vào log; không tự commit/push. Human Review do sinh viên xác nhận, không tự đánh dấu đã duyệt.

## Công cụ
Python 3.9+ và thư viện chuẩn. Từ root dự án:
- `python .agents/skills/performance-test-auditor/scripts/validate-test-plan.py PATH.jmx --student-id 23120274 --scenario Load`: audit tĩnh; không chứng minh JMX chạy được. Thay placeholder bằng giá trị thật.
- `python .agents/skills/performance-test-auditor/scripts/analyze-jtl.py PATH.jtl --sample-kind http --output analysis.json`: chỉ dùng http khi đã xác nhận tập mẫu là HTTP. Nếu mixed, lọc label và phân tích từng loại riêng; xem `--help` để biết cửa sổ đo. Không ghi đè kết quả cũ.

## Bàn giao
Nêu file tạo/sửa, kiểm tra đã thực hiện, kết quả thật, giới hạn và quyết định cần sinh viên review. Không tự chuyển bước.
