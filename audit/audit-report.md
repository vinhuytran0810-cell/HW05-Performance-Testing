I use AI tools for the following tasks.

## Interaction 1

- **AI Tool:** ChatGPT — Codex
- **Date & Time:** 2026-11-18 12:47:35 +07:00 (Asia/Saigon)

### Prompt

Hãy bổ sung quy tắc AI Audit Report vào AGENTS.md theo mục 9 của đề bài HW05.

Sau mỗi lần thực hiện prompt, AI phải ghi thêm nội dung vào file `audit/audit-report.md` theo mẫu:

## Interaction [STT]

- **AI Tool:** [Tên công cụ AI]
- **Date & Time:** [Ngày giờ thực tế]

### Prompt

[Nội dung prompt nguyên văn]

### AI Output

[Nội dung phản hồi của AI]

Yêu cầu:

- Tạo file nếu chưa tồn tại.
- Thêm khai báo đầu báo cáo: "I use AI tools for the following tasks."
- Chỉ ghi thêm, không ghi đè lịch sử.
- Không tạo lịch sử giả hoặc lưu thông tin nhạy cảm.
- Ghi nhận cả tương tác hiện tại.
- Áp dụng cho mọi nhiệm vụ, kể cả khi sử dụng Agent Skill.

Không chỉnh sửa Agent Skill, không commit và không thực hiện công việc khác.

### AI Output

Đã cập nhật [AGENTS.md](E:/KiemThu/HW05/AGENTS.md) và ghi tương tác hiện tại vào [audit-report.md](E:/KiemThu/HW05/audit/audit-report.md).

Quy tắc áp dụng cho mọi nhiệm vụ, kể cả Agent Skill; chỉ ghi thêm, không tạo lịch sử giả hoặc lưu secrets. Không sửa Agent Skill, không commit.


## Interaction 2

- **AI Tool:** ChatGPT — Codex (performance-test-auditor V3)
- **Date & Time:** 2026-11-18 15:52:44 +07:00 (Asia/Saigon)

### Prompt

$performance-test-auditor

# Task 1.1 — Thiết kế CSV Test Data

MSSV của tôi: 23120274

Lưu MSSV vào AGENTS.md để sử dụng khi đặt tên các JMeter test plan.

Dựa trên workflow Category Management & Verification đã thống nhất, hãy:

1. Xác định dữ liệu cần parameterize.
2. Tạo file `data/categories.csv` chứa các tên danh mục cơ sở hợp lệ.
3. Giải thích số lượng dữ liệu được lựa chọn.
4. Đề xuất cấu hình CSV Data Set Config.
5. Đề xuất cách sinh tên danh mục duy nhất tại runtime.
6. Xác định cách cung cấp credentials Admin an toàn.

Sử dụng cùng CSV cho Load, Stress, Spike và Endurance Testing.

Không tạo báo cáo Markdown riêng cho bước này.

Không tạo JMX, không sửa SUT, không chạy test, không commit hoặc push.

Thực hiện AI Audit theo quy định trong AGENTS.md.

Trả kết quả bằng tiếng Việt để tôi review trước khi chuyển sang bước thiết kế Load Test Plan.

### AI Output

Đã tạo [categories.csv](E:/KiemThu/HW05/data/categories.csv), lưu MSSV **23120274** vào [AGENTS.md](E:/KiemThu/HW05/AGENTS.md) và ghi tương tác vào [AI Audit](E:/KiemThu/HW05/audit/audit-report.md). Đã sử dụng skill `performance-test-auditor` V3; các cấu hình dưới đây là **đề xuất chờ bạn review**.

**Dữ liệu parameterize**

| Biến | Nguồn |
|---|---|
| `category_base` | CSV, tên cơ sở không rỗng |
| `category_name` | Sinh tại runtime, dùng nguyên giá trị đó cho verification/cleanup |
| `admin_email`, `admin_password` | Cấu hình local an toàn, dùng chung tài khoản Admin |
| `token`, `category_id` | Trích từ Login/Create response, lưu riêng từng thread; không đưa vào CSV |

CSV có **20 dòng dữ liệu**, một header `category_base`, UTF-8 không BOM. Ví dụ: `Electronics`, `Books`, `Home_Living`, `Kitchen`. Chọn 20 tên để có tập dữ liệu nhỏ, dễ kiểm tra và xoay vòng; **không phải 20 VU hay giới hạn 20 iteration**. Dùng cùng file cho Load, Stress, Spike và Endurance.

**CSV Data Set Config đề xuất**

| Thuộc tính | Giá trị |
|---|---|
| Filename | `E:/KiemThu/HW05/data/categories.csv` |
| File Encoding | `UTF-8` |
| Variable Names | Để trống để lấy tên biến từ header |
| Delimiter | `,` |
| Allow quoted data | `True` |
| Recycle on EOF | `True` |
| Stop thread on EOF | `False`; không có tác dụng khi Recycle=True |
| Sharing mode | `All threads` |

Cách đọc header, chia sẻ và EOF dựa trên [tài liệu Apache JMeter](https://jmeter.apache.org/usermanual/component_reference.html#CSV_Data_Set_Config). Nếu dùng mục Ignore first line, để False vì Variable Names đang trống. Khi chạy trên máy phát tải khác, phân phối cùng CSV và cấu hình đường dẫn tương ứng.

**Tên danh mục runtime đề xuất**

```text
HW05_23120274_<run_id>_<engine_id>_T<thread_no>_I<iteration_no>_<category_base>_<UUID>
```

Tạo `run_id` một lần mỗi run, `engine_id` riêng mỗi máy phát tải; tăng iteration counter và sinh UUID mới mỗi vòng. Sinh tên một lần, lưu `category_name` trong biến thread và giữ nguyên đến khi cleanup hoàn tất. Recycle chỉ tái sử dụng tên cơ sở; namespace và UUID giảm nguy cơ trùng tên đầy đủ. Không dùng prefix đơn thuần làm căn cứ xóa. Nếu base name trống hoặc là `<EOF>`, không gửi Create.

**Credentials Admin**

Ưu tiên biến môi trường `ESHOP_ADMIN_EMAIL`, `ESHOP_ADMIN_PASSWORD`, được cấp an toàn trước khi mở JMeter; Groovy đọc bằng `System.getenv(...)` rồi giữ trong biến thread. Không đưa giá trị thật vào CSV, JMX, command line, log hoặc JTL. Phương án khác là file local trong `data/credentials/` đã được ignore, có quyền truy cập hạn chế; JMeter không tự nạp `.env` nếu chưa có cơ chế đọc. Chưa tạo credentials file trong bước này.

Login vẫn thực hiện mỗi iteration. Thiếu credentials hoặc Login thất bại phải chặn request phụ thuộc; không thử mật khẩu sai trên Admin dùng chung.

**Kiểm tra tĩnh: PASS** — đúng header, 20 dòng hợp lệ, không trống/trùng base name, không có dữ liệu dạng credentials; hash CSV:

```text
c237523a3f44a5d1471b3d4483bcb25c76495e9972e6a0f2b6bf9bec0dc1a23e
```

Chưa chạy JMeter/smoke/performance test, không sửa SUT hoặc skill, không tạo JMX/báo cáo riêng, không commit/push. CSV chưa được đánh dấu đã duyệt; dừng tại đây để bạn review.


## Interaction 3

- **AI Tool:** ChatGPT — Codex (performance-test-auditor V3)
- **Date & Time:** 2026-11-18 16:41:25 +07:00 (Asia/Saigon)

### Prompt

$performance-test-auditor

# Task 1.2 — Thiết kế Load Test Plan

Sử dụng Agent Skill để thiết kế và tạo Load Test Plan cho workflow Category Management & Verification đã thống nhất.

## Yêu cầu

1. Kiểm tra cấu hình phần cứng hiện tại để đề xuất số threads, ramp-up, duration và think time phù hợp.
2. Tạo JMeter Test Plan trong thư mục `test-plans/`, tuân thủ tên file `23120274_Load_YYYYMMDD.jmx`, sử dụng ngày tạo thực tế.
3. Thực hiện đầy đủ workflow:
   - Admin Login.
   - View Categories.
   - Create Category.
   - Delete Category.
   - Verify Category Deletion.
4. Sử dụng `data/categories.csv` thông qua CSV Data Set Config đã được phê duyệt. Ưu tiên đường dẫn có thể cấu hình bằng JMeter property thay vì hardcode đường dẫn tuyệt đối.
5. Sử dụng biến môi trường `ESHOP_ADMIN_EMAIL` và `ESHOP_ADMIN_PASSWORD` cho thông tin đăng nhập.
6. Cấu hình JSON Extractor, correlation, assertions và các điều kiện chặn request phụ thuộc khi xảy ra lỗi.
7. Áp dụng cơ chế data isolation, manifest và guarded cleanup theo Agent Skill.
8. Sử dụng Summary Report làm report view của Load Testing.
9. Sử dụng script `validate-test-plan.py` trong Skill để kiểm tra JMX, đồng thời review thủ công những nội dung script không kiểm tra được.

Cấu hình tải tham khảo:

- 20 virtual users.
- Ramp-up: 20 giây.
- Duration: 180 giây.
- Think time: 1–2 giây.

Có thể đề xuất điều chỉnh các thông số trên dựa vào phần cứng và mục tiêu Load Testing, nhưng phải giải thích lý do.

## Ràng buộc

- Không chỉnh sửa source SUT.
- Không reset database.
- Không hardcode credentials, JWT hoặc category ID.
- Không chạy Load Test trong bước này.
- Không tạo JTL hoặc bằng chứng thực thi giả.
- Không tạo báo cáo Markdown trung gian.
- Không commit hoặc push.

Thực hiện AI Audit theo AGENTS.md.

Sau khi hoàn thành, báo cáo cấu trúc JMX, thông số tải, correlation, assertions, cleanup, kết quả validation và những hạn chế còn tồn tại.

Dừng lại để tôi review trước khi thực thi.

### AI Output

Đã tạo Load Test Plan bằng skill `performance-test-auditor`:

- [23120274_Load_20261118.jmx](E:/KiemThu/HW05/test-plans/23120274_Load_20261118.jmx)
- [category-load-support.groovy](E:/KiemThu/HW05/scripts/category-load-support.groovy): preflight, assertions, manifest và cleanup.
- [recover-category-load.groovy](E:/KiemThu/HW05/scripts/recover-category-load.groovy): recovery độc lập sau gián đoạn; mặc định chỉ preview manifest, không gửi HTTP.

Đã append tương tác vào [AI Audit](E:/KiemThu/HW05/audit/audit-report.md).

**Phần cứng và tải đề xuất**

Kiểm tra lúc 16:05:55 ngày 18/11/2026: i5-11400H **6 core/12 luồng**, RAM **15,74 GiB**, còn **1,65 GiB trống**, Windows 11 64-bit; ổ E còn khoảng 55,4 GiB.

| Thông số | Mặc định trong JMX |
|---|---|
| Threads | **10 VU**, thay vì 20 VU tham khảo |
| Ramp-up | **20 giây** |
| Duration | **180 giây**, bao gồm ramp-up; không gồm preflight/recovery |
| Think time | **1–2 giây trước Login mỗi iteration** |
| Connect / response timeout | **3 / 10 giây** |

Giảm xuống 10 VU là lựa chọn khởi đầu thận trọng do RAM đang thấp và JMeter chạy cùng máy SUT, **không phải năng lực đã đo**. Cần giải phóng RAM trước khi chạy; preflight và đầu mỗi iteration chặn tải mới nếu RAM trống dưới **2 GiB**. Không đặt think time giữa Create/Delete để tránh kéo dài thời gian giữ record tạm.

**Cấu trúc JMX**

```text
Test Plan
├─ setUp: approval gates, credentials, RAM, baseline/account/hashes
├─ Load Thread Group
│  ├─ CSV Data Set Config + HTTP Defaults/Headers
│  ├─ Iteration gate: reset biến, tạo tên duy nhất
│  ├─ 01 Admin Login → JSON Extractor → Assertion
│  ├─ 02 View Categories → Assertion
│  ├─ Ghi durable Create intent
│  ├─ 03 Create Category → JSON Extractor → Assertion
│  ├─ Guard ownership trước Delete
│  ├─ 04 Delete Category → Assertion
│  └─ 05 Verify Category Deletion → Assertion
├─ tearDown: guarded recovery, kiểm tra baseline/products/admin
└─ Summary Report
```

CSV dùng `${__P(csv_file,../data/categories.csv)}`, đường dẫn tương đối theo thư mục JMX; giữ nguyên file 20 base names, UTF-8, header tự đọc, All threads, Recycle=True. Threads/ramp/duration/target/timeouts cũng cấu hình bằng properties.

Credentials chỉ đọc từ `ESHOP_ADMIN_EMAIL` và `ESHOP_ADMIN_PASSWORD`. JWT, actual category ID và ownership giữ riêng từng thread. Login mỗi iteration; tên category kết hợp MSSV/run/engine/thread/iteration/base name/UUID.

**Correlation, assertions và cleanup**

- Login trích `$.token`, `$.user.id`, `$.user.role`; kiểm tra 200, message, token không rỗng/đúng cấu trúc, identity/role admin và account state. Decode claims không được coi là xác minh chữ ký; không yêu cầu `exp` mà SUT không cấp.
- Create trích `$.id`; chỉ nhận ID nguyên dương an toàn, không thuộc baseline/reference IDs. Mỗi iteration reset biến/flags; Login/View/Create lỗi sẽ chặn request phụ thuộc.
- Delete dùng ID thật cùng intent, kiểm tra ownership nội bộ; Verify kiểm tra vắng mặt theo **ID và exact name**, không assert count cố định khi chạy đồng thời.
- Manifest JSONL riêng từng thread, append và flush/force trước khi cho phép Create; persist ID trước Delete. `stepName` tách khỏi `categoryName`. Manifest không ghi credentials/JWT.
- Lỗi contract/manifest hoặc thiếu RAM chặn iteration mới; record đang dở được giữ trong manifest để recovery. Trạng thái cleanup tách khỏi trạng thái performance.
- Recovery ngoài phần tải kiểm tra lại source/config, baseline, exact ID/name và product references; admin sau chạy phải còn đúng ID/role, attempts=0, locked_until=null. Không xóa theo prefix, không reset database. Apply độc lập cần xác nhận `--quiescent`, `--isolated` và không có run đang giữ lock.

Control/setup/teardown samples được thiết kế `setIgnore()` để Summary Report tập trung vào năm HTTP sampler; không tạo parent transaction sample. Thứ tự extractor trước assertion dựa trên [tài liệu JMeter](https://jmeter.apache.org/usermanual/test_plan.html#executionorder). Sample body/header được scrub trước listener; save-config không lưu request/response nhạy cảm.

**Validation thực tế**

- Validator của skill: **0 findings**, exit code 0; trạng thái đúng là **`REVIEW_REQUIRED`**, không phải runtime PASS.
- Hai script hỗ trợ và **10 đoạn Groovy** trong JMX compile thành công.
- JMeter **5.6.3 deserialize JMX thành công**, không khởi tạo engine.
- Review tĩnh bổ sung đạt: hashTree, thứ tự/scope năm HTTP sampler, năm assertion, hai extractor, timer, CSV/header, save-config, các nhánh guards và scan literal secrets. CSV và bằng chứng smoke gốc không đổi.

SHA256 JMX:

```text
3b63a24f3e36b469fb3eca4b983019eb69b462a9feea7e7f2ec9bc910ecca261
```

**Giới hạn cần review trước thực thi**

1. Plan mặc định chặn mutation: cần duyệt rồi mới bật `hw05.allow_mutation` và `hw05.isolated_sut`.
2. Để giữ đúng năm request, main workflow không thêm GET kiểm tra tồn tại sau Create hoặc guard GET ngay trước Delete. Vì vậy bắt buộc môi trường cô lập, không tác nhân khác tạo product references và không restart backend; guard hiện tại không phải kiểm tra DB nguyên tử. Recovery mới đọc lại ngoài phần tải.
3. Scheduler/forced stop có thể bỏ dở iteration hoặc bỏ qua tearDown. Unknown Create vẫn có thể ghi muộn; automatic cleanup không tự coi quan sát vắng mặt hữu hạn là đã giải quyết. Cần recovery độc lập khi SUT thật sự quiescent.
4. Chưa kiểm chứng pilot, concurrency, timeout/forced-stop, logging runtime hoặc tải 10 VU. Flush manifest có chi phí IO phía máy phát tải và có thể ảnh hưởng throughput; cần theo dõi tài nguyên khi được phép chạy.

Không chạy Load Test, sampler hoặc recovery; không tạo JTL/runtime manifest hay báo cáo Markdown riêng, không sửa SUT/Skill/CSV, không reset database, không commit/push. Dừng để bạn review trước thực thi.


## Interaction 4

- **AI Tool:** ChatGPT — Codex (performance-test-auditor V3)
- **Date & Time:** 2026-11-18 17:07:57 +07:00 (Asia/Saigon)

### Prompt

$performance-test-auditor

# Task 1 — Pilot Load Test

Load Test Plan đã được review tĩnh và đủ điều kiện chạy thử với tải nhỏ.

Sử dụng file `test-plans/23120274_Load_20261118.jmx` hiện có.

Thực hiện:

1. Kiểm tra backend, RAM khả dụng, credentials Admin và baseline.
2. Hướng dẫn tôi thiết lập credentials an toàn nếu chưa có.
3. Chạy thử JMeter non-GUI với:
   - 1 VU.
   - Ramp-up: 1 giây.
   - Duration: 15 giây.
   - Think time: giữ nguyên.
4. Kiểm tra cả năm HTTP sampler, correlation, assertions, manifest và cleanup.
5. Kiểm tra raw JTL không chứa thông tin nhạy cảm.
6. Xác minh danh mục, sản phẩm và trạng thái Admin sau khi chạy.
7. Kiểm tra có ít nhất một iteration hoàn chỉnh và xác minh được việc Create thực sự tạo bản ghi trong pilot. Nếu cần kiểm tra bổ sung, thực hiện riêng ngoài workload đo.
8. Báo cáo mọi lỗi thực tế và nguyên nhân nếu xác định được.

Lưu kết quả pilot riêng, không trộn vào kết quả Load Test chính thức.

Nếu thiếu RAM, credentials hoặc không bảo đảm môi trường cô lập thì dừng và hướng dẫn tôi xử lý.

Không sửa source SUT, không reset database, không tạo JMX mới, không tạo báo cáo Markdown riêng, không chạy Load Test chính thức và không commit.

Ghi AI Audit theo AGENTS.md và dừng lại để tôi review.

### AI Output

**Pilot chưa chạy — dừng tại preflight theo yêu cầu của bạn.**

Kiểm tra lúc **17:05:25 ngày 18/11/2026 (UTC+07:00)**:

| Điều kiện | Kết quả |
|---|---|
| RAM trống | **3.674 GiB (3762 MiB)**, trên ngưỡng 2 GiB tại thời điểm kiểm tra |
| Backend port 3000 | Không tìm thấy listener; chưa xác nhận backend sẵn sàng |
| Credentials | Cả `ESHOP_ADMIN_EMAIL` và `ESHOP_ADMIN_PASSWORD` đều chưa có trong môi trường tiến trình công cụ |
| Môi trường cô lập | Chưa có xác nhận không có người dùng/test khác hoặc restart trong lúc pilot |
| Baseline và trạng thái Admin | Chưa kiểm tra được vì chưa đủ điều kiện trên |

JMX giữ nguyên hash đã review. Chưa chạy sampler, chưa tạo JTL/manifest hoặc dữ liệu Category mới; vì vậy chưa có kết quả iteration, Create, assertions hay cleanup để kết luận.

Bạn có thể chuẩn bị credentials cục bộ bằng PowerShell trên **chính máy Windows và tài khoản đang dùng Codex**:

```powershell
Set-Location 'E:\KiemThu\HW05'
New-Item -ItemType Directory -Path '.\data\credentials' -Force | Out-Null
$pilotAdmin = Get-Credential -Message 'EShop Admin: nhập email và mật khẩu'
$pilotAdmin | Export-Clixml -LiteralPath '.\data\credentials\pilot-admin.clixml' -NoClobber
Remove-Variable pilotAdmin
```

Mật khẩu được bảo vệ bằng Windows DPAPI, chỉ giải mã được bởi cùng tài khoản trên cùng máy. File vẫn là credential cần giữ cục bộ trong thư mục đã được gitignore bảo vệ. [Tài liệu Microsoft](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/export-clixml#example-3-encrypt-an-exported-credential-object-on-windows).

Khi chạy lại theo yêu cầu của bạn, mình có thể nạp file này vào hai biến môi trường **chỉ trong tiến trình khởi chạy JMeter**, không in giá trị. Đặt `$env:...` trong một terminal khác sẽ không tự truyền sang phiên Codex hiện tại. Không gửi mật khẩu hoặc nội dung file credentials qua chat.

Cần bạn kiểm tra tình trạng backend và xác nhận môi trường cô lập. **Mình không tự start/restart backend**, vì source khởi động hiện tại tự DROP/seed database, trái ràng buộc không reset.

Đã ghi tình trạng dừng và hướng dẫn vào [AI Audit](E:/KiemThu/HW05/audit/audit-report.md). Không sửa SUT/JMX, không chạy Load Test chính thức và không commit.


## Interaction 5

- **AI Tool:** ChatGPT — Codex (performance-test-auditor V3)
- **Date & Time:** 2026-11-18 17:32:08 +07:00 (Asia/Saigon)

### Prompt

$performance-test-auditor

Tôi đã khởi chạy backend EShop và tạo file credentials tại `data/credentials/pilot-admin.clixml`.

Tôi xác nhận môi trường kiểm thử cô lập, không có người dùng hoặc bài test khác tác động đến SUT.

Hãy kiểm tra lại preflight, lấy baseline mới sau khi backend khởi động và nạp credentials an toàn.

Tiếp tục thực hiện Pilot Test đã thống nhất:

- 1 VU.
- Ramp-up: 1 giây.
- Duration: 15 giây.
- Giữ nguyên think time.

Kiểm tra năm HTTP sampler, assertions, correlation, manifest, cleanup và dữ liệu trước/sau khi chạy.

Không in credentials, không reset database, không chạy Load Test chính thức, không tạo báo cáo Markdown riêng và không commit.

Ghi AI Audit theo AGENTS.md và trả kết quả để tôi review.

### AI Output

**[MEASURED] Pilot đạt kiểm tra chức năng và cleanup:** đã chạy non-GUI bằng JMX hiện có, **1 VU / ramp-up 1 giây / duration 15 giây**, giữ think time 1–2 giây.

| HTTP sampler | Samples | Lỗi HTTP/assertion |
|---|---:|---:|
| Admin Login | 9 | 0 |
| View Categories | 9 | 0 |
| Create Category | 9 | 0 |
| Delete Category | 9 | 0 |
| Verify Category Deletion | 9 | 0 |
| **Tổng** | **45** | **0** |

Tất cả samples trả HTTP 200. JTL có đúng năm HTTP labels, không lẫn control/transaction samples; **9 iteration hoàn chỉnh** khớp với 9 chuỗi manifest đầy đủ. Correlation token/ID và assertions đã được thực thi; các ID Create trong workload là **4–12**, được dùng nhất quán đến Delete/Verify.

**Dữ liệu và cleanup**

- Baseline mới sau khi backend khởi động: **3 danh mục, 5 sản phẩm**. Sau pilot và kiểm tra bổ sung, full records vẫn khớp baseline.
- Admin trước/sau: ID=1, role=admin, attempts=0, locked_until=null. Backend vẫn cùng PID **28068**, không restart trong phiên kiểm tra.
- TearDown ghi **cleanup `VERIFIED`**, `performanceFailed=false`, không còn owned record hoặc unresolved intent; không cần xóa bổ sung các record workload.
- Ngoài cửa sổ đo, đã Create một record chẩn đoán **ID=13**, **GET thấy đúng ID/tên**, rồi guarded Delete và GET xác nhận không còn. Bằng chứng trực tiếp này thuộc bước xác minh bổ sung, không trộn vào 45 samples đo; không khẳng định đã GET từng record workload trước Delete.
- Raw JTL và artifact pilot đã được quét: không phát hiện credentials thật/JWT theo phép kiểm tra; JTL không chứa cột request/response body hoặc headers. Raw evidence được giữ nguyên.

RAM trống thấp nhất trong 20 mẫu giám sát là **3210 MiB**, trên guard 2048 MiB; working set JMeter cao nhất khoảng **421 MiB**.

**Kết quả lưu riêng** tại `results/pilot/pilot_20261118T171737_8e54a503/`:

- [Raw JTL](E:/KiemThu/HW05/results/pilot/pilot_20261118T171737_8e54a503/samples.jtl)
- [HTML Dashboard](E:/KiemThu/HW05/results/pilot/pilot_20261118T171737_8e54a503/html/index.html)
- [Kết quả xác minh](E:/KiemThu/HW05/results/pilot/pilot_20261118T171737_8e54a503/pilot-validation.json)
- [Bằng chứng Create tồn tại](E:/KiemThu/HW05/results/pilot/pilot_20261118T171737_8e54a503/creation-proof.jsonl)
- [Manifest workload](E:/KiemThu/HW05/audit/manifests/pilot_20261118T171737_8e54a503/pilot_local/thread-0001.jsonl)

Thư mục còn có preflight/baseline trước-sau, metadata lệnh, logs, resource CSV và phân tích JTL thực tế.

**Cảnh báo và giới hạn**

- Không ghi nhận lỗi HTTP/assertion. JMeter cảnh báo deprecated plugin scanning; WARN `Removing JMeter property: sample_variables` do chủ động truyền giá trị rỗng để không lưu biến runtime.
- Wrapper PowerShell không thu được exit code launcher (`null`); không báo giả thành 0. Một lượt đọc log gặp lỗi encoding console, đã đọc lại qua JSON escaped; raw log không đổi. Kết luận dựa trên JTL, manifest, baseline và dashboard thực tế.
- AUTOINCREMENT cuối là 13 sau các INSERT/DELETE; không reset metadata/database.
- Pilot nhỏ chưa chứng minh khả năng chịu tải chính thức, concurrency cao hoặc recovery khi timeout/forced stop.

JMX, CSV, scripts và source SUT giữ nguyên hash đã kiểm tra. Không tạo JMX mới/báo cáo Markdown riêng, không reset database, không chạy Load Test chính thức hoặc commit. Đã ghi [AI Audit](E:/KiemThu/HW05/audit/audit-report.md); dừng để bạn review.


## Interaction 6

- **AI Tool:** ChatGPT — Codex (performance-test-auditor V3)
- **Date & Time:** 2026-11-18 18:23:10 +07:00 (Asia/Saigon)

### Prompt

$performance-test-auditor

# Task 1 — Thực thi Load Testing

Pilot Test đã được review và đủ điều kiện thực hiện Load Test chính thức.

Sử dụng test plan `test-plans/23120274_Load_20261118.jmx` hiện có.

Cấu hình:

- 10 VU.
- Ramp-up: 20 giây.
- Duration: 180 giây.
- Think time: giữ nguyên.
- Report View: Summary Report.

Yêu cầu:

1. Kiểm tra preflight, credentials, RAM, backend và baseline.
2. Cung cấp lệnh chạy JMeter non-GUI chính xác để tôi có thể trực tiếp thực thi và quay video.
3. Hướng dẫn thu thập CPU/RAM của backend trong quá trình chạy.
4. Lưu raw JTL, JMeter log và HTML Dashboard vào `results/load/`, tách biệt với Pilot Test.
5. Khắc phục việc không thu được exit code launcher trong lần chạy trước.
6. Sau khi thực thi, kiểm tra assertions, manifest, cleanup và baseline trước/sau.
7. Hướng dẫn mở raw JTL bằng Summary Report.
8. Báo cáo kết quả thực tế và các lỗi phát sinh nếu có.

Không sửa source SUT, không reset database, không thay đổi cấu hình tải đã duyệt.

Không tự tạo screenshot, video hoặc bằng chứng giả.

Chưa thực hiện Task 2, chưa tạo báo cáo Markdown riêng và chưa commit.

Ghi AI Audit theo AGENTS.md.

Trước tiên, cung cấp lệnh chạy và hướng dẫn để tôi chuẩn bị quay video. Chờ tôi xác nhận trước khi thực thi Load Test.

### AI Output

Đã chuẩn bị bằng skill `performance-test-auditor`; **chưa chạy Load Test chính thức**, đúng yêu cầu chờ bạn xác nhận.

Hai file mới:

- [run-category-load.ps1](E:/KiemThu/HW05/scripts/run-category-load.ps1): preflight, chạy non-GUI, thu CPU/RAM, lưu exit code và gọi postcheck.
- [check-category-load-run.py](E:/KiemThu/HW05/scripts/check-category-load-run.py): kiểm tra credentials/baseline bằng read-only, sau chạy đối chiếu JTL, assertions, manifest, cleanup, admin và secrets. Không thực hiện Task 2.

**Preflight chuẩn bị đã đạt**: backend PID **28068**, credentials khớp đúng admin sạch qua SQLite read-only, **3 danh mục / 5 sản phẩm**, RAM trống **2816 MiB (~2,75 GiB)**. RAM gần ngưỡng chặn 2048 MiB; hãy đóng ứng dụng không cần thiết trước khi quay. Launcher kiểm tra lại lúc chạy và không tự hạ tải.

**1. Lệnh chuẩn bị — có thể chạy trước khi quay**

```powershell
Set-Location 'E:\KiemThu\HW05'
powershell.exe -NoProfile -File .\scripts\run-category-load.ps1 -PrepareOnly
```

Chỉ kiểm tra read-only; không login, mutation, chạy JMeter hoặc tạo thư mục kết quả Load.

**2. Lệnh thực thi — chỉ chạy sau khi xác nhận**

```powershell
Set-Location 'E:\KiemThu\HW05'
powershell.exe -NoProfile -File .\scripts\run-category-load.ps1 -Run -ConfirmIsolation
$LASTEXITCODE
```

Đây là lệnh trực tiếp để bạn thực thi trong video. Launcher gọi JMeter bằng `-n`, in đầy đủ lệnh non-GUI không chứa credentials trước khi chạy và lưu arguments vào metadata. Giữ cố định:

| Thông số | Giá trị |
|---|---|
| VU / ramp-up / duration | **10 / 20 giây / 180 giây** |
| Think time | **1000–2000 ms trước Login mỗi iteration** |
| JMX | `test-plans/23120274_Load_20261118.jmx` |
| CSV | `data/categories.csv`, qua property `csv_file` |
| View | **Summary Report** |

Credentials được giải mã từ CLIXML vào environment của tiến trình; không đưa vào arguments hoặc hiển thị. Không mở file credentials, Login response hay environment values trong video.

**3. CPU/RAM và exit code**

Launcher tự lấy đúng PID đang nghe port 3000 và chạy monitor nền mỗi khoảng **1 giây**. File `backend-resources.csv` lưu timestamp UTC, PID, CPU tích lũy, CPU theo một core, CPU chuẩn hóa toàn máy, working set/private bytes và RAM trống của máy. Working set không phải JavaScript heap. Nếu backend đổi tiến trình, monitor ghi cảnh báo và yêu cầu JMeter shutdown nhẹ, không restart SUT.

Đã bỏ cách lấy `.ExitCode` từ `Start-Process` của pilot. Bên trong launcher dùng:

```powershell
& $jmeter @jmeterArgs
$nativeExit = $LASTEXITCODE
```

Giá trị được lấy ngay sau native command, lưu `nativeLauncherExitCode` trong `execution.json`. Mã thoát toàn launcher có thể là **2 nếu postcheck/monitor không đạt**, dù JMeter trả 0. Không coi exit code 0 là kết luận SLA đạt. Cách mới đã kiểm tra cú pháp; chưa được kiểm chứng bằng Load chính thức vì chưa chạy.

**4. Kết quả và quay video**

Mỗi lần chạy tạo thư mục mới, không ghi đè:

```text
results/load/load_<timestamp>_<id>/
├── samples.jtl
├── jmeter.log
├── html/index.html
├── backend-resources.csv
├── preflight.json
├── execution-start.json
├── execution.json
├── post-run-baseline.json
└── postcheck.json
```

Đây là các đầu ra dự kiến khi thực thi, **chưa phải file kết quả đã tạo**. Manifest thật sẽ nằm tại `audit/manifests/<run_id>/load_local/`. Pilot giữ riêng trong `results/pilot/`.

Bắt đầu quay trước lệnh Run; để chạy hết preflight → tải → tearDown/cleanup → HTML Dashboard → postcheck. Duration 180 giây gồm ramp-up, không gồm toàn bộ thời gian chuẩn bị/cleanup/dashboard. Không bấm Ctrl+C hoặc restart backend để kết thúc thông thường. Nếu postcheck báo cần review, giữ nguyên thư mục kết quả, không chạy lại hoặc reset để che lỗi.

**5. Mở raw JTL bằng Summary Report sau chạy**

1. Mở JMeter GUI và file JMX hiện có; **không bấm Start**.
2. Chọn node **Load - Summary Report**.
3. Dùng **Browse/Filename** chọn `results/load/<run_id>/samples.jtl` của đúng lần chạy. Clear dữ liệu hiển thị cũ trước khi nạp nếu cần; không xóa raw file.
4. Quay bảng Summary Report và mở `html/index.html` để đối chiếu. Ghi lại run ID/thư mục kết quả để tránh lấy nhầm Pilot.

Launcher sẽ kiểm tra sau chạy nhưng mình chưa có kết quả Load để báo cáo. Hiện chỉ kiểm tra cú pháp, chạy PrepareOnly và xác nhận hash JMX/CSV/helper không đổi; `results/load/` vẫn chỉ có `.gitkeep`.

Đã ghi [AI Audit](E:/KiemThu/HW05/audit/audit-report.md). Không sửa SUT/JMX, reset database, tạo screenshot/video, thực hiện Task 2, tạo báo cáo Markdown riêng hoặc commit. **Chờ bạn xác nhận trước khi thực thi.**
