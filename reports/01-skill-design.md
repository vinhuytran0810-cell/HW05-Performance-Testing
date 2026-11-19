# Agent Skill — Thiết kế V3

## Phạm vi
Skill tập trung Task 1 và Task 2 theo yêu cầu sinh viên. Dùng workflow đã chốt làm đầu vào, không phân tích lại API. CSV chưa hoàn thành nên giữ chức năng thiết kế CSV. Không hardcode hồ sơ EShop; đọc tài liệu dự án hiện có. Task 3, AI Audit tổng hợp, video, Git và đóng gói làm riêng. Các nghĩa vụ nộp bài không bị loại bỏ.

## Thành phần
- SKILL.md: điều phối theo nhiệm vụ và cổng kiểm soát.
- references/task1-test-design.md: CSV, workload, JMX, report views.
- references/task1-correlation-cleanup.md: assertions, state, ownership, recovery.
- references/task1-execution.md: preflight, CLI, raw JTL, HTML và resource metrics.
- references/task2-metrics.md: phương pháp thống kê và threshold.
- references/task2-analysis-review.md: kiểm chứng claim AI và optimization.
- scripts/validate-test-plan.py: audit tĩnh JMX.
- scripts/analyze-jtl.py: thống kê JTL CSV.
- assets/test-plan.md và assets/analysis-review.md: template kỹ thuật.

## Kiểm tra đóng gói
PASS: cấu trúc 10 file, metadata cơ bản, reference paths, cú pháp Python, CLI --help, kiểm tra hàm percentile bằng danh sách số đơn giản trong bộ nhớ. Hai script giữ nguyên từ V2. Không có JTL giả, không chạy SUT.

## Giới hạn
Chưa xác minh Codex discovery trên Windows, chưa audit JMX thật hoặc phân tích JTL thật. Manifest/recovery mới là hướng dẫn, chưa có implementation. Audit tĩnh không chứng minh đúng nghiệp vụ; analyzer cần xác minh sample-kind và cửa sổ đo, bỏ qua bucket rỗng. Human Review: PENDING. Không tự xác nhận sinh viên đã duyệt.

## Sử dụng
Thay riêng thư mục Skill V2 bằng V3. Gọi `$performance-test-auditor` trong Codex; chọn đúng nhiệm vụ CSV, thiết kế scenario, audit, execution hoặc Task 2. Chờ sinh viên phê duyệt từng bước.
