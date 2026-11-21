# HW05 — Task 2, bước 1: phân tích hiệu năng ban đầu của AI

Ngày lập: 2026-11-21 11:16:03 +0700 (Asia/Saigon). MSSV: 23120274. AI Tool: OpenAI Codex; skill: performance-test-auditor V3.

**Trạng thái: INITIAL AI ANALYSIS — CHƯA HUMAN REVIEW.** Báo cáo thực hiện phần “Analyse with AI” của PDF `2026.HW05.Performance Testing_En.pdf`, trang 5–6: đọc kết quả thật, đề xuất thresholds và hướng tối ưu. Chưa thực hiện Misinterpretation Hunt, chưa phân loại các đề xuất của AI theo kết quả Human Review, chưa sửa SUT hay chạy lại test. Các nhận định dưới đây là đầu ra AI để sinh viên review ở bước tiếp theo.

Quy ước nguồn: **[MEASURED]** tính từ bằng chứng run; **[METADATA]** cấu hình/postcheck/manifest; **[SRC]** source đã đối chiếu; **[HYP]** giả thuyết chưa đo; **[ĐỀ XUẤT]** ngưỡng hoặc hướng thay đổi cần duyệt. Số thập phân dùng dấu `.`; số đếm không có dấu phân cách hàng nghìn. Response time tính bằng **ms**, bộ nhớ **MiB**.

## 1. Run được chọn và độ toàn vẹn


| Scenario | Run chính thức được chọn | Trạng thái ghi trong postcheck |
| --- | --- | --- |
| Load | [load_20261119T143412_ffe7cbc2](../results/load/load_20261119T143412_ffe7cbc2/) | FUNCTIONAL_AND_CLEANUP_VERIFIED_NOT_SLA_PASS |
| Stress | [stress_official_20261119T163536_6d710318](../results/stress/official/stress_official_20261119T163536_6d710318/) | ARTIFACTS_AND_CLEANUP_VERIFIED_NOT_SLA_PASS |
| Spike | [spike_official_20261119T183802_1133a0c3](../results/spike/official/spike_official_20261119T183802_1133a0c3/) | ARTIFACTS_AND_CLEANUP_VERIFIED_NOT_SLA_PASS |
| Endurance | [endurance_20261121T095946_618d6730](../results/endurance/endurance_20261121T095946_618d6730/) | FUNCTIONAL_AND_CLEANUP_VERIFIED_NOT_SLA_PASS |

Không gộp Pilot. Không dùng Spike `spike_official_20261119T181617_c779cbe7` vì postcheck REVIEW_REQUIRED, có safety/monitor/recovery checks chưa đạt. Không dùng Endurance `endurance_20261120T143742_32e10dc9`: metadata ghi một failed sample và run không đạt thời lượng; dùng run thành công ngày **21/11/2026** theo yêu cầu. Vì vậy “0 lỗi” trong báo cáo chỉ áp dụng bốn run đã chọn, không đại diện mọi lần thực thi trong repository.

[MEASURED/METADATA] Cả bốn raw JTL có SHA-256 khớp `postcheck.json`; số samples, label counts, complete iterations được đối chiếu lại với raw và manifest. Postcheck cho biết native launcher exit=0, backend PID/start time không đổi trong từng run, monitor Completed, cleanup VERIFIED, categories/products trở về baseline và Admin sạch. Đây là bằng chứng tính đúng và cleanup, không phải SLA PASS.

SUT `server.js` và `database.js` hiện tại khớp hash trước chạy của cả bốn run. Không giả định mọi JMX/helper working hiện nay còn byte-identical với phiên bản đã chạy; các snapshot cấu hình được lấy từ `execution.json`, manifest và log run, không viết lại lịch sử. Raw CSV không tự chứng minh sampler class: nhận diện HTTP dựa trên workflow/JMX Task 1 đã xác minh, năm label, metadata và manifest; không có parent Transaction samples hay JSR223 control samples trong tập JTL được phân tích.


| Run | Raw JTL SHA-256 |
| --- | --- |
| Load | `b614c8bb063b52d268cd83b704dfb4d4be4e5df87b8aaa2912da11123972501f` |
| Stress | `bab18b0f5bcaa3885f30f2600a0ba96e51be2c46cd5cb5756e7333081f3c41e0` |
| Spike | `16bb79b56e0162c3c1198451b3f959ce95ac19697e9c496783fce2d4032d858b` |
| Endurance | `9e9a9365b8f7630469ba14c0675907152c6c00ff093aa44322eb802a88580ef4` |

## 2. Phương pháp tính và cửa sổ thời gian

Dùng trực tiếp script read-only `.agents/skills/performance-test-auditor/scripts/analyze-jtl.py --sample-kind http --timestamp start`, không truyền `--output` để tránh tạo báo cáo trung gian; bổ sung phép nhóm phase/resource và đọc manifest trong mã tạm. Kiểm tra header, dòng malformed, epoch milliseconds, elapsed không âm, success boolean và tập label; không loại outlier hoặc failed samples khỏi phân phối. Không phát hiện dòng malformed ở bốn run.

- Average là trung bình số học elapsed; median/P90/P95/P99 dùng **linear interpolation, h=(n−1)p** như Skill. Không trung bình các percentile để ra percentile tổng.
- Error rate = số `success=false` / tổng HTTP samples. Đối chiếu riêng responseCode; bốn tập chọn đều HTTP 200 và success=true. Không suy lỗi từ `performanceFailed=false`.
- Throughput = samples / số giây của đúng cửa sổ. Mỗi label dùng cùng mẫu số thời gian của cửa sổ. Chọn sample theo **start ∈ [lo, hi)**; request có thể hoàn tất sau hi. Resource sample được nhóm theo timestamp quan trắc.
- Workflow hoàn chỉnh lấy từ manifest với chuỗi CREATE_INTENT → CREATE_CONFIRMED → DELETE_INTENT → DELETE_ACKNOWLEDGED → ABSENCE_VERIFIED; đối chiếu thêm chuỗi năm HTTP có thứ tự theo thread. Không lấy N/5 khi có cutoff/skip.
- `elapsed` khác `Latency` đến byte đầu và `Connect`; timer trước Login không nằm trong elapsed HTTP. Thread counts là snapshot JMeter gắn sample, không phải phép đo liên tục tại mọi microsecond.


| Scenario | Mốc workload +07:00 | Cửa sổ main (s) | HTTP first-start→last-end (s) | Launcher (s) | N trong main | Main HTTP/s | HTTP-span HTTP/s |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Load | 2026-11-19T14:34:28.818+07:00 | 180.000 | 178.719 | 199.311 | 5595 | 31.083 | 31.306 |
| Stress | 2026-11-19T16:35:48.668+07:00 | 400.000 | 397.849 | 478.654 | 37750 | 94.375 | 94.885 |
| Spike | 2026-11-19T18:38:08.145+07:00 | 206.608 | 204.624 | 218.360 | 24342 | 117.817 | 118.960 |
| Endurance | 2026-11-21T09:59:53.890+07:00 | 900.000 | 898.268 | 924.483 | 145377 | 161.530 | 161.849 |

Load: anchor từ log Start ThreadGroup; Stress: marker MAIN_WORKLOAD_START; Spike: marker workload và observed recovery/end; Endurance: scheduler end−900 s trong `endurance-timing.json`. Stress có **60.014 s quiet recovery, không có HTTP workload**, rồi guarded reconciliation ngoài đo. Spike có **60 s active recovery ở 10 VU trong workload**, không được loại pha này như quiet recovery. Endurance main dừng sau **900.038 s** trước cleanup; launcher còn chứa setup, cleanup và tạo dashboard. Không dùng tổng thời gian launcher làm mẫu số RPS main.

## 3. Tổng quan phân phối HTTP và số workflow


| Scenario | HTTP samples toàn raw | Workflow hoàn chỉnh | Avg ms | Median | P90 | P95 | P99 | Max | Error rate |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Load | 5595 | 1119 | 2.595 | 2.000 | 5.000 | 5.000 | 8.000 | 31 | 0.000% |
| Stress | 37750 | 7550 | 2.977 | 2.000 | 6.000 | 7.000 | 11.000 | 260 | 0.000% |
| Spike | 24342 | 4834 | 5.371 | 4.000 | 13.000 | 17.000 | 25.000 | 66 | 0.000% |
| Endurance | 145384 | 29076 | 3.319 | 3.000 | 7.000 | 9.000 | 15.000 | 528 | 0.000% |

[MEASURED] Bốn run cho latency thấp và không lỗi HTTP/assertion được lưu. Tuy nhiên Spike và Endurance không chia hết cho năm: Spike có **4920 Login + 4920 View + 4834 mỗi Create/Delete/Verify**, chênh **172 HTTP reads/auth** so với 4834×5. Endurance có **29077 mỗi Login/View/Create/Delete, 29076 Verify**, tương ứng **29077 intents, 29076 complete workflows**, một chuỗi thiếu bước Verify trong workload. Cleanup độc lập xác minh absence và baseline; cả hai run có recoveryDeletes=0. Điều này phù hợp cutoff/scheduler chặn hoặc cắt phần cuối vòng lặp, không phải bằng chứng HTTP lỗi hay còn category chưa dọn.

Endurance có **7 HTTP samples bắt đầu ngoài cửa sổ scheduler [0,900 s)**, nên raw N=145384 còn N main=145377; tất cả được giữ nguyên trong raw. Có 29074 ABSENCE_VERIFIED events trong main, trong khi tổng manifest có 29076. Các quy tắc timestamp/biên giải thích vì sao số đếm theo cửa sổ không luôn bằng tổng run.


| Scenario | Complete events trong main | Complete events/s main | Chuỗi 5 HTTP tái dựng | Avg span 5 HTTP (ms) | P95 span (ms) | Max span (ms) |
| --- | --- | --- | --- | --- | --- | --- |
| Load | 1119 | 6.217 | 1119 | 20.940 | 31.000 | 170 |
| Stress | 7550 | 18.875 | 7550 | 22.021 | 35.000 | 344 |
| Spike | 4834 | 23.397 | 4834 | 36.577 | 84.000 | 291 |
| Endurance | 29074 | 32.304 | 29076 | 24.854 | 44.000 | 673 |

“Span 5 HTTP” là Verify-response-end trừ Login-request-start trên chuỗi thành công cùng thread, gồm khoảng cách giữa HTTP; **không phải Transaction Controller sample**, không gồm think time 1–2 s trước Login hay phần assertion/journal cuối sau Verify. Số chuỗi tái dựng khớp complete manifest, nhưng không có iteration/intent ID trong JTL để join trực tiếp từng row với manifest. Không cộng percentile của năm sampler, và không dùng span vài chục ms này làm toàn bộ thời gian một virtual user hoàn thành một vòng lặp có pacing.

## 4. Kết quả theo từng HTTP sampler

Các bảng dưới dùng **toàn raw của từng run**; HTTP/s theo cửa sổ main nằm ở cột cuối, cùng denominator cho mọi sampler trong scenario. Tất cả có lỗi **0/N**; failure-code breakdown rỗng.

### Load

| HTTP sampler | N raw | Avg ms | Median | P90 | P95 | P99 | Max | Lỗi | Main HTTP/s |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01_Admin_Login | 1119 | 1.717 | 2.000 | 2.000 | 2.100 | 5.000 | 31 | 0/1119 | 6.217 |
| 02_View_Categories | 1119 | 0.886 | 1.000 | 1.000 | 2.000 | 4.000 | 7 | 0/1119 | 6.217 |
| 03_Create_Category | 1119 | 4.781 | 5.000 | 6.000 | 7.000 | 9.000 | 15 | 0/1119 | 6.217 |
| 04_Delete_Category | 1119 | 4.608 | 4.000 | 5.000 | 6.000 | 8.000 | 11 | 0/1119 | 6.217 |
| 05_Verify_Category_Deletion | 1119 | 0.986 | 1.000 | 1.000 | 2.000 | 4.000 | 8 | 0/1119 | 6.217 |

### Stress

| HTTP sampler | N raw | Avg ms | Median | P90 | P95 | P99 | Max | Lỗi | Main HTTP/s |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01_Admin_Login | 7550 | 2.278 | 2.000 | 4.000 | 6.000 | 10.000 | 244 | 0/7550 | 18.875 |
| 02_View_Categories | 7550 | 0.997 | 1.000 | 1.000 | 4.000 | 6.000 | 31 | 0/7550 | 18.875 |
| 03_Create_Category | 7550 | 5.216 | 4.000 | 8.000 | 9.000 | 13.000 | 260 | 0/7550 | 18.875 |
| 04_Delete_Category | 7550 | 4.965 | 4.000 | 7.000 | 9.000 | 12.000 | 35 | 0/7550 | 18.875 |
| 05_Verify_Category_Deletion | 7550 | 1.428 | 1.000 | 3.000 | 4.000 | 8.000 | 36 | 0/7550 | 18.875 |

### Spike

| HTTP sampler | N raw | Avg ms | Median | P90 | P95 | P99 | Max | Lỗi | Main HTTP/s |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01_Admin_Login | 4920 | 4.889 | 2.000 | 13.000 | 17.000 | 29.000 | 66 | 0/4920 | 23.813 |
| 02_View_Categories | 4920 | 2.882 | 1.000 | 9.000 | 13.000 | 22.000 | 40 | 0/4920 | 23.813 |
| 03_Create_Category | 4834 | 7.578 | 6.000 | 14.000 | 18.000 | 27.000 | 62 | 0/4834 | 23.397 |
| 04_Delete_Category | 4834 | 7.920 | 5.000 | 15.000 | 19.000 | 28.000 | 62 | 0/4834 | 23.397 |
| 05_Verify_Category_Deletion | 4834 | 3.640 | 1.000 | 10.000 | 14.000 | 20.670 | 37 | 0/4834 | 23.397 |

### Endurance

| HTTP sampler | N raw | Avg ms | Median | P90 | P95 | P99 | Max | Lỗi | Main HTTP/s |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01_Admin_Login | 29077 | 2.380 | 1.000 | 5.000 | 7.000 | 15.000 | 465 | 0/29077 | 32.308 |
| 02_View_Categories | 29077 | 1.175 | 1.000 | 3.000 | 5.000 | 10.000 | 119 | 0/29077 | 32.308 |
| 03_Create_Category | 29077 | 5.701 | 4.000 | 9.000 | 11.000 | 18.000 | 528 | 0/29077 | 32.306 |
| 04_Delete_Category | 29077 | 5.548 | 4.000 | 9.000 | 11.000 | 17.000 | 258 | 0/29077 | 32.304 |
| 05_Verify_Category_Deletion | 29076 | 1.793 | 1.000 | 4.000 | 6.000 | 11.000 | 248 | 0/29076 | 32.304 |

[MEASURED/SRC] Create/Delete thường tốn thời gian hơn hai GET; ở Endurance P95 từng mutation là 11 ms, còn Login 7 ms, View 5 ms, Verify 6 ms. Ở Spike, Delete P95=19 ms cao hơn P95 gộp=17 ms. Tên phân loại “Auth-heavy” mô tả workflow pool, không chứng minh Login là sampler chậm nhất hoặc password hashing đang chi phối CPU. Connect P99=0 ms cho cả bốn tập, phù hợp keep-alive trên localhost; không có nghĩa mọi kết nối hay mọi request không tốn thời gian. Sample 0 ms phản ánh độ phân giải ms, không khẳng định thời gian xử lý vật lý bằng không.

## 5. Load và Stress: thay đổi theo VU

[MEASURED] Load warm-up20 s: 343 samples, 17.150 HTTP/s, P95=7.000 ms. Cửa sổ 20–180 s: 5252 samples /160 s = **32.825 HTTP/s**, P95=5.000 ms, P99=8.000 ms. 5202/5252 samples gắn 10 active threads; phần cuối scheduler giảm thread, nên không gọi mọi thời điểm của160 s đều đúng10 VU.

| Hold VU dự kiến (60 s) | N | HTTP/s | Avg ms | P95 | P99 | Max | Active snapshot min–max | % samples đúng target | CPU machine avg % | WS max MiB |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 10 | 2005 | 33.417 | 2.708 | 6.000 | 8.000 | 14 | 10–10 | 100.00% | 0.357 | 68.090 |
| 20 | 3920 | 65.333 | 2.642 | 6.000 | 9.000 | 14 | 20–21 | 99.92% | 0.695 | 110.719 |
| 30 | 5918 | 98.633 | 2.796 | 7.000 | 10.000 | 233 | 30–31 | 99.95% | 1.074 | 114.281 |
| 40 | 7905 | 131.750 | 2.993 | 8.000 | 11.000 | 23 | 40–41 | 99.95% | 1.397 | 114.094 |
| 50 | 9755 | 162.583 | 3.330 | 8.000 | 13.000 | 260 | 2–50 | 96.72% | 1.702 | 113.902 |

[MEASURED] RPS tăng gần theo số VU: 33.417 → 65.333 → 98.633 → 131.750 → 162.583; P95 từ 6 ms lên 8 ms, không thấy điểm gãy throughput hoặc error ở các cửa sổ này. Các hold20/30/40 có vài snapshot thuộc level kế tiếp sát ranh giới; anchor là marker gate nên lệch vài ms so với scheduler từng nhóm. Hold50 có 9435/9755 samples gắn đúng50 threads, phần cuối giảm thread trước end. Phần trăm trong bảng là tỷ lệ **samples**, không phải tỷ lệ thời gian. Không sửa/filter các row này để làm đường tải đẹp hơn.

[AI INFERENCE] Trong dải thử10–50 VU, chưa thấy saturation rõ. Kết luận này không chứng minh server chỉ có thể đạt162.583 RPS, cũng không chứng minh còn dư chính xác bao nhiêu capacity. Think time và closed workload giới hạn tốc độ phát tải, trong khi injector, I/O và server cùng chia sẻ máy.

## 6. Spike: baseline → đỉnh100 VU → phục hồi10 VU


| Phase | Offset bắt đầu(s) | Cửa sổ(s) | N | HTTP/s | Avg ms | P95 | P99 | Max | Active snapshot |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| WARMUP | 0.000 | 20.000 | 330 | 16.500 | 2.861 | 5.000 | 7.000 | 38 | 2–10 |
| BASELINE | 20.000 | 60.000 | 1945 | 32.417 | 2.594 | 5.000 | 8.000 | 12 | 10–10 |
| SPIKE_RAMP | 80.000 | 3.000 | 378 | 126.000 | 3.521 | 9.000 | 12.230 | 20 | 16–100 |
| SPIKE_HOLD | 83.000 | 60.000 | 19429 | 323.817 | 6.088 | 18.000 | 27.000 | 66 | 100–100 |
| RECOVERY_TRANSITION | 143.000 | 2.122 | 240 | 113.101 | 1.663 | 5.000 | 9.610 | 14 | 10–100 |
| RECOVERY | 145.122 | 60.000 | 1970 | 32.833 | 2.353 | 5.000 | 8.000 | 17 | 10–10 |
| RECOVERY_TAIL | 205.122 | 1.486 | 50 | 33.647 | 2.100 | 5.000 | 5.000 | 5 | 1–10 |

[MEASURED] Baseline và Recovery đều100% samples gắn 10active threads; Spike hold100% gắn 100. Trong60 s giữ đỉnh, throughput **323.817 HTTP/s**, P95 **18 ms**, P99 **27 ms**; baseline P95=5 ms. P95 tăng3.6lần ở10lần VU; không lỗi nhưng latency nhạy với concurrency.

Recovery bắt đầu sau burststop **2.122 s**, giữ10 VU trong 60 s; còn1.486 s tail trước khi main marker kết thúc. Recovery P95=5 ms và P99=8 ms bằng baseline; RPS 32.833 so với 32.417, chênh 1.29%. CPU machine trung bình trở về 0.346% so với baseline 0.331% và peak 3.021%. Đây là bằng chứng phục hồi latency/throughput trong lần đo này, không phải chứng nhận chống quá tải mọi tình huống.

WS baseline trung bình55.763 MiB, Recovery62.729 MiB; private bytes141.763→141.673 MiB. WS không trở về mức cũ ngay không đủ kết luận memory leak; working set chịu page residency/OS. Transition/tail không gộp vào60 s peak hoặc60 s recovery. Cờ `spike_boundary_crossed` đã có trong raw; phân tích vẫn chọn theo start, giữ sample vượt biên, không dời row giữa pha.

## 7. Endurance ngày21/11: 50 VU trong 900 s cấu hình

[MEASURED] Main window900 s chứa **145377 samples**, **161.530 HTTP/s**; raw gồm cả 7tail samples là145384. Sau ramp20 s, cửa sổ 880 s có **143852 samples**, **163.468 HTTP/s**, mean3.299 ms, P95=9 ms, P99=15 ms; có 28770complete manifest events trong cửa sổ này. Đây là khoảng 14 phút 40 giây mục tiêu ở50 VU, gồm đoạn giảm thread cuối;143568/143852 samples có snapshot50 threads.


| Phút | N | HTTP/s | Avg ms | P95 | P99 | Max | Lỗi | CPU machine avg % | WS avg MiB | Private avg MiB |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 7985 | 133.083 | 6.107 | 16.000 | 46.000 | 136 | 0 | 1.948 | 79.176 | 98.805 |
| 2 | 9716 | 161.933 | 4.077 | 10.000 | 24.850 | 251 | 0 | 1.541 | 116.200 | 140.643 |
| 3 | 9919 | 165.317 | 3.122 | 9.000 | 13.000 | 117 | 0 | 1.368 | 116.853 | 141.361 |
| 4 | 9730 | 162.167 | 2.981 | 8.000 | 13.000 | 100 | 0 | 1.324 | 117.113 | 141.273 |
| 5 | 9870 | 164.500 | 3.078 | 8.000 | 13.000 | 38 | 0 | 1.287 | 117.577 | 141.625 |
| 6 | 9745 | 162.417 | 3.679 | 9.000 | 16.000 | 528 | 0 | 1.449 | 117.841 | 141.630 |
| 7 | 9785 | 163.083 | 2.924 | 8.000 | 12.000 | 100 | 0 | 1.187 | 117.848 | 141.608 |
| 8 | 9840 | 164.000 | 2.998 | 9.000 | 13.000 | 32 | 0 | 1.273 | 118.060 | 141.654 |
| 9 | 9797 | 163.283 | 2.950 | 8.000 | 13.000 | 29 | 0 | 1.231 | 118.097 | 141.644 |
| 10 | 9903 | 165.050 | 2.998 | 8.000 | 13.000 | 105 | 0 | 1.368 | 118.243 | 141.934 |
| 11 | 9843 | 164.050 | 3.147 | 9.000 | 15.000 | 37 | 0 | 1.346 | 118.183 | 141.855 |
| 12 | 9772 | 162.867 | 3.210 | 9.000 | 14.000 | 73 | 0 | 1.389 | 118.769 | 142.419 |
| 13 | 9837 | 163.950 | 3.081 | 8.000 | 14.000 | 130 | 0 | 1.314 | 118.949 | 142.402 |
| 14 | 9836 | 163.933 | 3.072 | 9.000 | 13.000 | 33 | 0 | 1.296 | 118.785 | 142.175 |
| 15 | 9799 | 163.317 | 2.897 | 8.000 | 11.000 | 39 | 0 | 1.371 | 118.862 | 142.227 |

Phút 1 bao gồm 20 s ramp: throughput thấp133.083 RPS và P95=16 ms, không so trực tiếp như một phút steady. Phút 2–15 nằm khoảng 161.933–165.317 HTTP/s, P95 từ 8–10 ms; không thấy throughput giảm dần hoặc tail percentiles tăng đơn điệu theo thời gian. Phút 6 có max528 ms nhưng P99=16 ms, P95=9 ms: một spike đuôi dài không đại diện đa số requests.

WS tăng nhanh lúc đầu:50.730 MiB ở điểm đầu main; mean phút 2=116.200 MiB, phút 15=118.862 MiB (tăng2.663 MiB giữa hai mean). Private mean phút 2=140.643 MiB, phút 15=142.227 MiB. [AI INFERENCE] Mẫu phù hợp warm-up/caching rồi tăng chậm/plateau hơn là bằng chứng leak tăng không giới hạn. [HYP] Không loại trừ leak nhỏ hoặc leak chỉ xuất hiện ở thời gian/dataset khác; chưa có JS heap, GC hoặc allocation tracing, không gọi private bytes là JavaScript heap.

Đối chiếu **90 dòng `minute-metrics.csv`**: counts, failures, throughput và P95 nearest-rank khớp phép tính lại từ raw. File này dùng nearest-rank còn Skill/report dùng linear interpolation; có 5 dòng per-sampler khác nhau do định nghĩa (ví dụ Verify phút 1:11 ms nearest-rank so với10.4 ms linear). Không sửa CSV hay gọi khác biệt phương pháp này là số liệu sai. Percentile gộp vẫn được tính trên toàn bộ sample phù hợp, không lấy trung bình15P95.

## 8. CPU/RAM: chỉ số backend, tách khỏi injector và cleanup

Máy được ghi trong `evidence/hardware/DxDiag.txt`: Intel Core i5-11400H ~2.70GHz,6cores/12logical CPUs,16384MB RAM vật lý,16122MB OS-visible; Windows11Home Single Language64-bit. JMeter5.6.3 và Java17 theo bộ thực thi. Không chép hostname/machine ID vào báo cáo. Java/Node/backend/monitor chạy cùng máy; các run khác PID và trạng thái cache/ứng dụng nền, không coi đây là benchmark phần cứng phổ quát.

Công thức đã ghi trong execution: CPU OneCore=100×ΔprocessCpuSeconds/ΔwallSeconds; CPU Machine=OneCore/12. “OneCore100%” tương đương thời gian CPU của một logical CPU, không phải telemetry riêng một core và process đa thread có thể vượt100%. Resource averages dưới đây là trung bình các điểm quan trắc, không phải bình quân có trọng số chính xác theo thời gian; chu kỳ thực tế khoảng 1.09–1.13 s, không đúng tuyệt đối1 s. Snapshot cuối kỳ có thể chứa CPU interval chạm ranh giới trước nó.


| Workload | Resource samples | OneCore avg% | OneCore max% | Machine avg% | Machine max% | WS max MiB | Private max MiB | Host free min MiB |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Load | 162 | 4.235 | 11.402 | 0.353 | 0.950 | 85.398 | 136.492 | 2567 |
| Stress | 355 | 12.165 | 37.586 | 1.014 | 3.132 | 114.523 | 142.242 | 2631 |
| Spike | 185 | 13.512 | 52.813 | 1.126 | 4.401 | 62.957 | 142.125 | 3319 |
| Endurance | 825 | 16.520 | 41.973 | 1.377 | 3.498 | 119.949 | 144.090 | 3014 |


| Scenario | CPU machine peak trong main % | Peak toàn file monitor % |
| --- | --- | --- |
| Load | 0.950 | 4.120 |
| Stress | 3.132 | 6.152 |
| Spike | 4.401 | 6.500 |
| Endurance | 3.498 | 7.298 |

Toàn monitor gồm setup, auth/baseline, quiet recovery nếu có, guarded reconciliation và dashboard tail. Không gán peak toàn file cho peak tải HTTP. Host free RAM là dung lượng trống toàn hệ thống, không phải RAM backend; giảm/tăng của nó có thể do injector hoặc ứng dụng khác. Bốn main windows đều cao hơn RAM floor2048 MiB. Không có injectorCPU/heap, disk latency, event-loop lag, SQLite busy timing nên chưa xác định bottleneck phần cứng hoặc memory ceiling tối đa.

## 9. Performance thresholds đề xuất — chưa phải SLA đã duyệt

[MEASURED] Mức sustained cao nhất được giữ dài trong bộ dữ liệu này là **50 VU**,900 s cấu hình/880 s sau ramp, khoảng 163.468 HTTP/s steady. Mức burst cao nhất quan sát là **100 VU trong 60 s**,323.817 HTTP/s. Đó là các điểm vận hành đã đo, **không phải maximum capacity**. Không có run tăng đến khi server saturation/error có kiểm soát, không có nhiều lần lặp/ngưỡng ổn định định nghĩa trước nên chưa xác định “maximum stable RPS của phần cứng” hoặc memory ceiling tuyệt đối.

[ĐỀ XUẤT] Các ngưỡng dưới là baseline hồi quy ban đầu với khoảng đệm so với số liệu, cùng dataset/5 sampler/Admin dùng chung/think1–2 s/localhost/hardware. Không tự áp dụng ngưỡng này hồi tố để đổi postcheck thành performancePASS. Sinh viên cần duyệt mục tiêu nghiệp vụ, chạy lặp khi được phép và điều chỉnh jitter trước dùng làm acceptance criteria.


| Phạm vi | Ngưỡng đề xuất | Căn cứ và giới hạn |
| --- | --- | --- |
| Latency steady≤50 VU | P95 từng HTTP sampler≤20 ms; P99≤50 ms trên cửa sổ steady | Endurance từng mutationP95=11 ms, maxP99 theo label18 ms; margin cho dao động. Không áp dụng cho span toànworkflow. |
| Latency Spike100 VU | P95 từng sampler≤30 ms; P99≤60 ms trong 60 s hold | Toànraw Spike từng sampler P95 cao nhất19 ms/P99=29 ms; holdgộp18/27 ms. Cần kiểm chứng theo label ở peak sau Human Review. |
| Throughput50 VU | ≥150 HTTP/s theo phút steady; ≥30workflow-completions/s đo độc lập | Observed steady163.468 RPS và28770/880=32.693events/s. Không đánh giá phút 1 chứa ramp hoặc tail không đủ phút. |
| Throughput100 VU burst | ≥300 HTTP/s trong 60 s hold | Observed323.817; không gọi đây là maximumRPS, và không dùng aggregate117.817 của toànSpike thay peak. |
| Error/data integrity | HTTP/assertion error=0%; baseline/products/Admin/owned-data invariants đều đạt | Cả4 run chọn đều0lỗi. Mọi lỗi ownership/cleanup vẫn là safety failure;0observed không có nghĩa xác suất lỗi tương lai bằng 0. |
| Recovery10 VU sau Spike | Trở lại10 VU trong≤5 s sau burst stop;60sRecovery P95≤max(1.5×baselineP95,baselineP95+5 ms);RPS≥90%baseline | Observed transition2.122 s,RecoveryP95=5 ms vàRPS32.833vs32.417. Ngưỡng latency tương ứng10 ms với baseline này. |
| Resource budget trong workload | BackendWS≤150 MiB;private≤180 MiB;CPUOneCore peak≤80%;hostfree≥2048 MiB | Cácmax main lần lượt119.949 MiB/144.090 MiB/52.813%; là budget đề xuất, không phải giới hạn OS/heap hoặc capacity trần. |
| Endurance drift sau warm-up | So cửa sổ 60 s đủ ở phút 2–5 với12–15:throughput không giảm>10%;P95 không tăng quá max(20%,+5 ms);private mean không tăng>20 MiB vàkhông có tăng đơn điệu kéo dài | Dữ liệu hiện thấy độ trễ/RPS ổn định,private tăng chậm; đây là policy đề xuất cho run lặp, không kết luận leak chỉ từ một slope. |

[AI INFERENCE] Với closed workload, cận lý tưởng bỏ mọi overhead từ thinktime trung bình cấu hình1.5 s là5×VU/1.5:50 VU≈166.667 HTTP/s,100 VU≈333.333 HTTP/s. RPS steady và Spike khá gần cận phát tải này. Đó là giải thích hợp lý vì sao RPS tỷ lệ với VU, **không phải phép đo công suất tối đa backend**; journal fsync, điều khiển JMeter, xử lý response và các giai đoạn cutover cũng ảnh hưởng chu kỳ.

## 10. Bất thường và kết luận AI ban đầu


| Run | DòngCSV (header=dòng1) | timeStamp | Giờ +07:00 | Sampler | Elapsed ms | HTTP | success |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Load | 2 | 1795073670084 | 2026-11-19T14:34:30.084+07:00 | 01_Admin_Login | 31 | 200 | true |
| Stress | 30205 | 1795081302146 | 2026-11-19T16:41:42.146+07:00 | 03_Create_Category | 260 | 200 | true |
| Spike | 13173 | 1795088403763 | 2026-11-19T18:40:03.763+07:00 | 01_Admin_Login | 66 | 200 | true |
| Endurance | 54800 | 1795230340354 | 2026-11-21T10:05:40.354+07:00 | 03_Create_Category | 528 | 200 | true |

Stress có Create260 ms và hai Login244/243 ms gần16:41:42; Endurance có Create528 ms và Login465/451 ms gần10:05:40. Các outlier có tính cụm nhưng vẫn200/success=true. [HYP] Có thể liên quan scheduling, GC, disk/SQLite commit hoặc cạnh tranh injector; hiện không có tracing để xác định nguyên nhân, không khẳng định SQLITE_BUSY, lock hoặc memory leak. Không xóa outlier để làm đẹp kết quả; max/P99/P95 phản ánh các khía cạnh khác nhau.

[AI INITIAL CONCLUSION] Ở môi trường cục bộ và tập dữ liệu nhỏ, workflow hoạt động đúng trong các run chọn; short burst làm tăng tail latency nhưng recovery trở về mức baseline. Endurance50 VU chưa bộc lộ sự suy giảm tăng dần rõ trong 15 phút cấu hình. Thông lượng có dấu hiệu bị pacing chi phối; chưa quan sát saturation. Những nhận định này giới hạn bởi một run thành công mỗi profile, Admin dùng chung, cardinality nhỏ và thiếu telemetry server/injector chi tiết. Các run bị loại cho thấy không thể suy rộng rằng mọi lần thử đều ổn định.

## 11. Hướng tối ưu EShop — đề xuất của AI, chưa triển khai

Những đề xuất sau nêu cơ sở, lợi ích giả định và cách kiểm chứng; đây chưa phải phần sinh viên phán xét AI “feasible/hallucinated”, chưa có so sánh trước/sau hoặc số % cải thiện thực đo.

### 11.1. Index có chọn lọc cho Login

[SRC] `server.js:35,70` tìm user bằng `WHERE email=?`; `database.js:50–61` khai báo `email TEXT`, không có UNIQUE/index tường minh trong schema khởi tạo. [ĐỀ XUẤT] Thử **non-unique index users(email)** trên môi trường thử được duyệt nếu bảng user tăng; dùng EXPLAIN QUERY PLAN xác minh và so P95/Login ở cùng cardinality. Index có thể giảm scan theo predicate. Không tự đổi thành UNIQUE vì có thể thay đổi hợp đồng và dữ liệu trùng. Với một tài khoản nóng và bảng nhỏ, lợi ích có thể rất nhỏ; index cũng tăng chi phí ghi. Nền tảng: [SQLite Query Planning](https://www.sqlite.org/queryplanner.html).

Không đề xuất thêm index trên categories.id: đây đã là INTEGER PRIMARY KEY (`database.js:23–26`). GET categories là SELECT* không WHERE (`server.js:243–246`), nên index name không tự giảm chi phí đọc toàn bộ; cũng không thêm UNIQUE(name) chỉ để tăng tốc vì làm đổi semantics. [SQLite CREATE TABLE](https://www.sqlite.org/lang_createtable.html).

### 11.2. SQLite WAL như một thí nghiệm có kiểm soát

[SRC] Source đã đọc không cấu hình PRAGMA WAL; không có snapshot journal_mode của từng run, nên **chưa xác nhận mode thực tế**. [ĐỀ XUẤT/HYP] Nếu đo được read/write contention trên nhiều connections, thử WAL để reader và writer hoạt động đồng thời hơn; theo dõi checkpoint, WAL growth, busy/error và latency. WAL vẫn chỉ có một writer tại một thời điểm; không hứa loại bỏ mọi khóa hoặc tăng throughput một tỷ lệ cố định. Cân nhắc durability/synchronous settings, không đổi để che lỗi. Cần xác minh SQLite runtime/driver và PRAGMA thực tế trước thí nghiệm. Source dùng một handle chung nên lợi ích bổ sung từ nhiều reader connections chưa được chứng minh. [SQLite WAL](https://www.sqlite.org/wal.html), [SQLite Isolation](https://www.sqlite.org/isolation.html).

### 11.3. Quản lý connection, statement và transaction

[SRC] `database.js:5` mở một sqlite3.Database được tái sử dụng; không có bằng chứng mở connection mới trên từng HTTP request. [ĐỀ XUẤT] Giữ handle dùng chung; profile chi phí prepare/execute và thử tái sử dụng prepared statements cho SQL lặp nếu driver/lifecycle cho phép. Nếu thực sự cần nhiều reader, nghiên cứu pool nhỏ/bounded và write queue, không giả định pool lớn tạo nhiều writer song song. Giữ transaction ngắn trong từng nghiệp vụ; không giữ transaction qua cả chuỗi nhiều HTTP/thinktime. Lợi ích giả định là giảm parse/lifecycle hoặc tránh queue bất thường; phải đo queue latency và driver behavior trước. Hạn chế single-writer theo [SQLite Isolation](https://www.sqlite.org/isolation.html).

### 11.4. Backend profiling trước tối ưu CPU

[ĐỀ XUẤT] Ở bước thí nghiệm sau, bổ sung event-loop delay/utilization, process CPU, SQL timing và GC/heap metrics; xác định JWT, JSON serialization, callback work hay I/O có chi phối tail không. Dời công việc CPU lớn/synchronous I/O khỏi event loop nếu tìm thấy, giới hạn logging nặng và queue hợp lý. Không khẳng định asyncsqlite3 callback hiện tại đang chặn toàn event loop, hoặc CPU tổng thấp chứng minh event loop luôn rảnh. [Node.js: Don't Block the Event Loop](https://nodejs.org/learn/asynchronous-work/dont-block-the-event-loop).

### 11.5. Payload, cache và khả năng mở rộng dataset

[ĐỀ XUẤT/HYP] Nếu số categories tăng nhiều, đánh giá payload/listing cost trước khi thiết kế pagination hoặc cache có invalidation trên Create/Delete. Cache TTL thiếu invalidation có thể làm Verify thấy dữ liệu cũ và phá correctness. Pagination sẽ đổi hợp đồng “GET trả toàn bộ categories”, cần API/test review trước; không áp dụng âm thầm cho workload hiện tại. Baseline chỉ3categories/5products nên chưa có bằng chứng listing là bottleneck. Projection categories hiện chỉid/name, không hứa giảm lớn bằng cách thay SELECT* đơn thuần.

### 11.6. Giảm nhiễu phép đo trước khi quy kết SUT

[ĐỀ XUẤT/HYP] Lần thử sau có thể tách injector khỏi backend, giữ cứng dataset/build, đo JVM/injector CPU/heap và disk, lặp cùng tải và thêm soak dài hơn khi được duyệt. Manifest force-to-storage giữa các sampler bảo vệ cleanup nhưng cũng tạo I/O trên cùng máy; không gán chi phí đó cho elapsed HTTP và không bỏ durability chỉ để tăng RPS. Mục đích là phân biệt bottleneck server với trần phát tải/host contention, không tạo tuyên bố tối ưu SUT từ latency hiện có.

## 12. Bàn giao bước AI analysis

Báo cáo là đầu ra AI ban đầu, giữ cả phát hiện thuận lợi và giới hạn dữ liệu. Các thresholds và hướng tối ưu đang ở trạng thái đề xuất; sinh viên thực hiện Human Review/Misinterpretation Hunt và đánh giá khuyến nghị ở bước kế tiếp. Không tự ghi sinh viên đã đồng ý hoặc các nhận định đã được sửa qua review.

Chỉ tạo file phân tích này và append AI Audit theo yêu cầu; không sửa raw JTL, execution/postcheck/resource/minute CSV, manifest hoặc SUT, không chạy test/HTTP mới, không commit/push. Thu thập/snapshot checksum tập772file kết quả/manifest được chọn chỉ để kiểm tra toàn vẹn; không có báo cáo trung gian trong repository. PDF trang5–6 được đọc trực tiếp; các yêu cầu video/Issues/Task3 trong PDF không mở rộng phạm vi nhiệm vụ hiện tại.

