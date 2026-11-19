# Hoàn tất chuẩn bị và đối chiếu review workflow Category Management

Ngày lập: **17/11/2026**, Asia/Saigon (UTC+07:00). Người thực hiện phân tích/đối chiếu: **Codex (AI)**. Tài liệu này phục vụ sinh viên review; tên file theo yêu cầu không có nghĩa sinh viên đã tự thực hiện Human Review toàn bộ source, script hoặc evidence.

## 1. Phạm vi, nguồn và trạng thái review

Đã đọc README/AGENTS gốc, [01-environment.md](01-environment.md), [02-api-analysis.md](02-api-analysis.md), [03-category-workflow-validation.md](03-category-workflow-validation.md), [script smoke gốc](../../audit/smoke-category.cjs) và [JSON evidence gốc](../../audit/03-category-smoke.json). Năm nhận xét được đối chiếu trực tiếp với script, JSON và các đoạn báo cáo liên quan. Không chạy lại script hoặc gọi SUT để sinh bằng chứng mới.

Phân biệt ba loại thông tin:

| Loại | Bằng chứng và trạng thái |
| --- | --- |
| Quyết định sinh viên đã cung cấp | Yêu cầu hiện hành chốt Category Management, Pool A+C, JMeter 5.6.3, Login mỗi iteration, shared admin, thread-local token/ID, CSV base names, runtime uniqueness, cleanup/recovery, không reset/sửa source, commit tại mốc chính. Ghi nhận là **đã chốt trong yêu cầu**, không cần suy đoán lại. |
| Phân tích AI đã kiểm tra lại | Codex xác minh năm nhận xét và đối chiếu snapshot/status/hashes; kết quả tại mục 4. Đây là review kỹ thuật của AI, chưa tự trở thành xác nhận độc lập của sinh viên. |
| Nội dung còn cần sinh viên kiểm tra | Độ chính xác và mức ưu tiên từng nhận xét, acceptance criteria cải tiến, manifest/recovery cụ thể, workload/SLA và rubric. Không có bằng chứng sinh viên đã tự đọc từng dòng hoặc xác nhận từng finding. |

Kết luận **CONDITIONAL GO** được giữ theo yêu cầu hiện hành và bằng chứng Step 03. Không diễn giải thành GO vô điều kiện, đạt hiệu năng, hoàn thành Agent Skill/test plan hoặc đã sửa các hạn chế của smoke script.

Nhãn: **[SPEC]** đặc tả; **[SRC]** source/static analysis; **[SMOKE]** evidence lần chạy cũ; **[HYP]** rủi ro chưa thực nghiệm; **[ĐỀ XUẤT]** cải tiến cho giai đoạn sau. Kiểm tra hash/secrets của bước hiện tại là kiểm tra tài liệu, không phải smoke/performance evidence mới.

## 2. Lựa chọn workflow và kết quả review báo cáo 03

### 2.1 Các phương án

- **Loại Shopping Cart Management:** [SRC/SMOKE cũ] backend thiếu Update/Remove/Clear Cart API, frontend cart dùng React state riêng; không thể chạy đủ sáu bước HTTP rồi cleanup hợp lệ. Báo cáo 02 giữ nguyên để truy vết quyết định, không sửa thành Category Management.
- **Không chọn Admin Product Management:** quyết định do người dùng cung cấp là chọn nghiệp vụ đơn giản hơn. Product Management vẫn là phương án khác có nhiều field/phụ thuộc; **không có bằng chứng nó không khả thi hoặc đã chạy smoke thất bại**. Không tự thêm lý do kỹ thuật chưa được kiểm chứng để hợp thức hóa quyết định.
- **Chọn Category Management:** [SRC/SMOKE cũ] có đủ Login/GET/POST/DELETE/GET; Create trả actual ID và có thể DELETE đúng record sở hữu; giữ được baseline category/product. Có authentication, đọc và ghi/xóa SQLite thực tế; phù hợp phạm vi đơn giản đã chọn.

### 2.2 Đối chiếu báo cáo 03

Báo cáo 03 phân biệt rõ thiếu admin-role/name validation trong source với các probe đã chạy; ghi rằng không thử role bypass, blank/duplicate insert, referenced-category deletion hoặc lockout timing. Kết quả API happy path được JSON hỗ trợ. Các request GET xác nhận Create, negative auth và DELETE lặp là diagnostic, không tự thêm vào năm bước performance workflow.

Một số câu trong báo cáo 03 cần đọc đúng ngữ cảnh:

- Mục 4.1 liệt kê **assertions cần có**, không chứng minh script hiện tại đã triển khai đủ các assertion đó; source Login còn thiếu kiểm tra message và token đầy đủ.
- Mục 10 nói evidence được lưu sau event và đề xuất manifest độc lập; không đồng nghĩa đã có recovery đảm bảo sau forced stop.
- `executionCompleted=true` là kết quả predicate của script cũ; chưa có điều kiện explicit bảo đảm adminAfter hợp lệ. JSON thực tế vẫn cho phép kiểm tra thủ công accountAfter.
- “Dữ liệu giữ nguyên” là full records categories/products và trạng thái admin được đối chiếu, không phải database file bất biến. SQLite sequence đã tăng 3→4.
- “Duplicate name được phép” là đặc tính schema/handler; FR-14 chưa yêu cầu unique. DELETE nonexistent vẫn 200 không mặc nhiên vi phạm đặc tả có yêu cầu 404; phải phân biệt vấn đề khả năng quan sát với yêu cầu chưa được quy định.

Không sửa báo cáo 03 để viết lại lịch sử. Các điểm làm rõ nằm trong báo cáo 04 này.

## 3. Đối chiếu JSON evidence với báo cáo và script

**[SMOKE cũ]** `audit/03-category-smoke.json` ghi:

| Nội dung | Bằng chứng kiểm tra lại | Kết luận của Codex |
| --- | --- | --- |
| Thời gian | startedAt `2026-11-17T09:59:38.965Z`; finishedAt `2026-11-17T09:59:39.077Z` | Khớp 16:59:38.965–16:59:39.077 UTC+07:00 trong báo cáo 03; không phải phép đo SLA/throughput |
| Admin trước/sau | Cùng id=1, role=admin, login_attempts=0, locked_until=null | Dữ liệu lần chạy thực tế sạch; không dựa riêng vào executionCompleted để kết luận |
| View/Create/Verify Create | Baseline 3 categories; Create 200, body.id=4; event sau xác nhận đúng ID/name, count=4 | Khớp báo cáo; hai nhãn event bị overwrite nhưng payload/timestamp vẫn nhận diện được thao tác |
| Delete/Verify | DELETE owned ID=4 trả 200; Verify absent=true, count=3 | Cặp request và snapshot cuối xác nhận cleanup, không chỉ message DELETE |
| DELETE lặp | absentBeforeRequest=true; DELETE lại ID=4 vẫn 200 Category deleted | Khớp finding “success không cho biết affected rows” |
| Baseline trước/sau | categoriesUnchanged=true; productsUnchanged=true; hash từng tập records bằng nhau | Có kiểm tra nội dung full records, không chỉ counts |
| Cleanup | attempted=true, verified=true; cuối cùng ALREADY_ABSENT | Cleanup của **lần chạy bình thường này** thành công; không chứng minh forced-stop recovery |
| Metadata SQLite | categorySequenceBefore.seq=3; categorySequenceAfter.seq=4 | Tác động metadata được ghi rõ, không reset sequence |
| Source và báo cáo trước | sourceUnchanged=true; previousReportsUnchanged=true, kèm hashes | Có dữ liệu đối chiếu preservation; kiểm tra tài liệu hiện tại tiếp tục giữ nguyên các file này |
| Phạm vi kết luận | limitations ghi chưa role-bypass mutation, blank/duplicate/reference delete, lockout timing, timeout injection, load/concurrency | Không có bằng chứng runtime cho các nhánh đó; giữ nguyên nhãn [SRC]/[HYP] |

Các vị trí JSON được dẫn bằng JSONPath để ổn định hơn số dòng: `$.steps[6]`, `$.steps[7]`, `$.adminBefore`, `$.adminAfter`, `$.comparison`, `$.cleanup`, `$.executionCompleted`, `$.limitations`. Index trong `steps` bắt đầu từ 0.

## 4. Xác minh năm nhận xét của ChatGPT

### 4.1 Trường name trong event bị ghi đè

**Nhận xét ban đầu:** spread operator có thể làm tên category ghi đè event name; xuất hiện ở Create và Verify Creation.

**Vị trí:** `audit/smoke-category.cjs:26` tạo object `{timestamp:..., name, ...data}`; dòng **104** và **107** truyền thêm `name: uniqueName`. JSON `$.steps[6].name` và `$.steps[7].name` bằng tên `HW05_SMOKE_...`, thay vì `3 Create Category` / `Diagnostic Verify Creation`. Báo cáo 03 mục 6.2 cũng đã lưu ý hiện tượng này.

**Kết quả Codex [SRC + SMOKE cũ]: đúng.** Trong hai call này data.name ghi đè name do nằm sau trong object literal. Không phải mọi event đều sai, không phải spread lúc nào cũng có lỗi; chỉ xảy ra khi data có key trùng. ID, timestamp, body/status và baseline không bị biến thành dữ liệu giả vì lỗi nhãn này.

**Mức ảnh hưởng:** trung bình đối với truy vết/parse audit tự động; không làm hỏng thao tác HTTP của lần chạy đã ghi nhận. Một trình phân tích chỉ lọc event theo label dự kiến có thể bỏ sót hai thao tác.

**Hướng xử lý giai đoạn sau [ĐỀ XUẤT]:** schema tách `event` hoặc `operation` khỏi `category_name`, chặn key dữ liệu ghi đè metadata và dùng allowlist payload. Event schema cần có version. Không sửa JSON/script gốc để “sửa lịch sử”.

**Trạng thái:** Codex đã xác minh; chờ sinh viên review mức ưu tiên và schema audit tương lai. Chưa có xác nhận độc lập rằng sinh viên đã tự kiểm tra source này.

### 4.2 Assertion Login chưa đầy đủ

**Nhận xét ban đầu:** cần kiểm tra đầy đủ HTTP status, message, token hợp lệ và role admin.

**Vị trí:** script **81–84**. Dòng 82 hiện assert `status===200`, token có **kiểu string**, user.id đúng admin đọc từ DB, user.role là admin. Dòng 83 decode phần payload JWT để kiểm tra sự có mặt của exp trong log. **Không assert `message === 'Login successful'`, không explicit assert token nonempty/đúng cấu trúc đầy đủ, không verify chữ ký hoặc đối chiếu id/role decoded payload.** JWT không có exp là đúng source SUT hiện tại, không phải lỗi assertion cần tự thêm yêu cầu exp.

**Kết quả Codex [SRC]: nhận xét đúng về yêu cầu cải tiến nhưng chưa đầy đủ nếu hiểu rằng script không kiểm tra Login.** Status, user ID và role **đã được kiểm tra**. Decode payload không đồng nghĩa cryptographic verification. Chuỗi rỗng có thể fail ở bước decode sau đó, nhưng đây không phải assertion rõ nghĩa/thông báo phù hợp ngay tại Login.

**Bổ sung [SMOKE cũ]:** token của lần chạy đã được backend chấp nhận cho Create/Delete được bảo vệ. Điều đó hỗ trợ tính hợp lệ vận hành của **token lần chạy đó**, dù unit assertion Login chưa đủ. GET categories là public nên GET thành công tự nó không xác minh token. Không yêu cầu copy signing secret SUT vào JMeter để tự verify chữ ký và không kết luận token hợp lệ chỉ từ Base64 decode.

**Mức ảnh hưởng:** trung bình; lỗi contract hoặc extraction có thể được phát hiện muộn/thông báo không rõ, làm dependent request hoặc audit gây khó hiểu. Không phủ nhận kết quả happy path đã có.

**Hướng xử lý [ĐỀ XUẤT]:** tại Login assert status, message, token string không rỗng/không sentinel, cấu trúc hợp lý, expected user ID/role; nếu đọc claims thì đối chiếu đúng identity nhưng ghi rõ decode không xác thực chữ ký. Giữ token thread-local; service-side acceptance qua request bảo vệ trong workflow là bằng chứng thực thi. Fail Login thì dừng các bước phụ thuộc, không dùng token cũ. Không tự thêm một API verify token hoặc bước thứ sáu vào workflow.

**Trạng thái:** phần đã có/phần còn thiếu đã phân biệt; chờ sinh viên review acceptance criteria cải tiến. Quyết định Login mỗi iteration đã được sinh viên chốt trong yêu cầu, không còn là câu hỏi mở.

### 4.3 Reconciliation timeout hữu hạn và manifest recovery

**Nhận xét ban đầu:** reconciliation chỉ chạy trong thời gian hữu hạn, cần manifest cho recovery sau run.

**Vị trí:** script **31–33** có request timeout 10.000 ms; **37–51** reconcile dùng tối đa 4 vòng nếu uncertainCreate, 1 vòng nếu không, delay 500 ms khi chưa thấy. Dòng **97–100** đánh dấu outcome unknown khi Create timeout/network/parse error; **126** gọi reconcile trong finally. Không thấy sau khoảng reconcile thì dòng **50** đánh dấu `lateCreateOutcomeUnresolved=true`; dòng **131–132** không cho cleanup PASS.

**Kết quả Codex [SRC]: đúng về giới hạn, nhưng “chưa lưu gì để recovery” sẽ là diễn giải sai.** Script đã persist JSON bằng `fs.writeFileSync` tại **25–26**, capture name từ đầu (**12–17,67**), baseline (**69–71**), ghi dấu trước Create (**97**) và lưu ID qua event sau Create (**102–104**). Đây là partial recovery information, **chưa phải manifest độc lập/append-only, đa run/engine/iteration hoặc recovery tool hoàn chỉnh**. Dòng 10 còn từ chối ghi đè evidence khi chạy lại, là bảo vệ tốt cho lịch sử.

Không mô tả reconciliation là “chờ tổng cộng 2 giây”: riêng delay có thể tối đa 4×500 ms, nhưng mỗi GET còn có timeout 10 giây và có thể lỗi/dừng sớm; server có thể hoàn tất Create muộn hơn cửa sổ này. Polling hữu hạn không chứng minh “không có record” mãi về sau. Không cố ý kích hoạt timeout trong evidence cũ.

**Mức ảnh hưởng:** cao cho cleanup khi request outcome mơ hồ hoặc run bị gián đoạn; thấp hơn ở happy path đã thành công. Full-file overwrite chưa có atomic-write/fsync contract, nên forced stop giữa lúc ghi có thể để JSON không hoàn chỉnh [HYP], chưa được fault-injection test.

**Hướng xử lý [ĐỀ XUẤT]:** manifest riêng theo run/engine/thread/iteration, ghi intent trước POST, actual ID và outcome khi biết; lưu exact name/baseline ownership mà không token/password. Có durability strategy, outcome UNKNOWN/RECOVERY_REQUIRED và recovery độc lập sau run. Không retry POST mù quáng; không xóa theo prefix hoặc ID đoán; ambiguous name/ID cần review. Chưa triển khai manifest hay recovery mới ở bước này.

**Trạng thái:** đã xác minh cơ chế hữu hạn và thông tin persist sẵn có; chờ sinh viên review thiết kế durability, thời hạn reconciliation và recovery ownership trong giai đoạn tiếp theo. Chủ trương phải có recovery đã được chốt trong yêu cầu.

### 4.4 try/finally không bảo đảm cleanup khi forced stop

**Nhận xét ban đầu:** cần cleanup độc lập vì try/finally không bảo đảm chạy khi tiến trình bị dừng cưỡng bức.

**Vị trí:** script **121–145**, đặc biệt **124–135**; JSON `$.cleanup`, `$.steps[13]` ghi lần chạy bình thường đã thấy owned ID vắng mặt. Báo cáo 03 mục 10 đã phân biệt forced shutdown/mất điện với đường finally thông thường.

**Kết quả Codex [SRC]: đúng.** finally xử lý exception/đường thoát thông thường khi process còn chạy; không thể bảo đảm được thực thi khi kill cưỡng bức, host crash hay mất điện. Điều này **không có nghĩa cleanup lần trước thất bại**: evidence có verified=true và full baseline bằng nhau. Chưa có bằng chứng thử forced stop; không tạo thử nghiệm mới để minh họa.

**Mức ảnh hưởng:** cao đối với Stress/Spike/Endurance hoặc interruption vì có thể để category owned tồn dư và baseline drift. Đây là rủi ro cần kiểm soát, chưa phải sự cố đã quan sát.

**Hướng xử lý [ĐỀ XUẤT]:** cleanup trong iteration, quản lý thread-local ownership và recovery process độc lập sử dụng manifest. Không dựa duy nhất vào finally/tearDown. Recovery kiểm tra lại exact ID/name, baseline và product reference trước DELETE; dừng nếu ownership không rõ. Không restart hoặc reset/reseed database để dọn dữ liệu.

**Trạng thái:** đã xác minh về source/cơ chế thực thi; sinh viên đã chốt phải có recovery, còn thiết kế xử lý kill/timeout cụ thể cần review và được kiểm chứng ở giai đoạn phù hợp.

### 4.5 executionCompleted chưa gate adminAfter

**Nhận xét ban đầu:** cần kiểm tra thêm trạng thái admin sau chạy trong điều kiện executionCompleted.

**Vị trí:** script **75–77** kiểm tra adminBefore; **134** đọc adminAfter; **141** tính executionCompleted bằng fiveStepWorkflowPassed, cleanup.verified, không failure/cleanup.failure, sourceUnchanged và previousReportsUnchanged. Dòng 141 **không đọc/so sánh giá trị adminAfter**. Nếu DB query lỗi thì cleanup.failure có thể làm fail, nhưng query thành công trả account thiếu/sai trạng thái không tự fail theo predicate hiện tại.

**Kết quả Codex [SRC + SMOKE cũ]: đúng.** Có capture accountAfter không đồng nghĩa có assertion trạng thái cuối. Trong JSON lần chạy này adminBefore/adminAfter cùng id=1, role=admin, attempts=0, locked_until=null nên bằng chứng thủ công hỗ trợ account không đổi; không tự đổi executionCompleted của evidence gốc thành false.

**Mức ảnh hưởng:** trung bình đến cao đối với tín hiệu hoàn tất và admin dùng chung; workflow có thể báo hoàn tất dù account về sau không còn clean. Đây là khoảng trống static của predicate, không phải hiện tượng admin bị khóa đã xảy ra trong smoke.

**Hướng xử lý [ĐỀ XUẤT]:** thêm assertion/flag rõ rằng accountAfter tồn tại, đúng ID, role=admin, attempts=0, locked_until=null theo baseline sạch đã chốt; kết hợp flag này vào completion. Kiểm tra trạng thái sau khi các cập nhật bất đồng bộ có liên quan đã hoàn tất. Không lưu full user response hoặc credentials để kiểm tra này, không sửa trực tiếp SQLite để “đạt” assertion.

**Trạng thái:** đã xác minh; chờ sinh viên review tiêu chí accountAfter/completion ở giai đoạn xây dựng Skill/test plan. Giữ nguyên script và JSON Step 03.

## 5. Ưu điểm và giới hạn của smoke script

**Ưu điểm có căn cứ source/evidence:** scope một danh mục duy nhất; UUID/timestamp; capture baseline full categories/products; login admin hiện có qua credential đọc DB read-only; không import database.js gây reset; correlate actual Create ID; kiểm tra ID/name không thuộc baseline và product references trước DELETE; xác minh creation/deletion; try/finally và reconcile; persist evidence theo tiến trình; từ chối ghi đè JSON cũ; không ghi credential/token/full Login response; có hash source/report và baseline; exit failure khi các guard chính thất bại.

**Giới hạn cần cải tiến sau này:** năm finding ở mục 4; script hiện dành cho một lần smoke, không phải load harness; đường timeout/forced stop chưa được thực nghiệm; full JSON overwrite chưa là manifest durability; global baseline equality của một smoke không thể áp dụng nguyên xi giữa các VU cùng chạy; TOCTOU giữa guard GET và DELETE còn có thể tồn tại nếu có người khác thay đổi dữ liệu [HYP]. Không tự sửa chúng trong giai đoạn chỉ hoàn tất tài liệu.

## 6. Quyết định thiết kế và lý do

Tất cả lựa chọn dưới đây được **người dùng chốt trong yêu cầu hiện hành**; phần lý do là diễn giải kỹ thuật của Codex, không phải trích dẫn phát biểu hoặc xác nhận Human Review độc lập của sinh viên.

| Quyết định đã chốt | Lý do/ảnh hưởng kỹ thuật |
| --- | --- |
| Category Management & Verification; Pool A+C; JMeter 5.6.3 | Đủ năm bước API và cleanup qua HTTP; scope đơn giản, có Auth/read/write/verification |
| Login mỗi iteration | Mỗi vòng bao phủ Auth-heavy; tạo thêm read/UPDATE login và JWT sign, phải giữ thống nhất khi so các loại tải |
| Một admin hợp lệ dùng chung | Giảm chuẩn bị account; chấp nhận identity chung và rủi ro khóa/counter dùng chung; chưa tạo admin mới |
| JWT/category ID thread-local | Tránh dùng nhầm token/ID giữa VU; không đồng nghĩa mỗi VU có danh tính backend riêng |
| CSV base names + runtime uniqueness | Dữ liệu tái sử dụng nhưng full name không collision giữa run/engine/thread/iteration; CSV chưa được tạo |
| Cleanup mỗi iteration + recovery khi gián đoạn | Hạn chế dữ liệu tăng dần, bảo vệ baseline; cần guard ownership và manifest cụ thể ở giai đoạn sau |
| Giữ nguyên năm bước cho Load/Stress/Spike/Endurance | Cho phép so sánh cùng workflow; diagnostic GET sau Create không tự thành bước thứ sáu; không thêm Update |
| Ghi defect, không sửa source, không reset database | Giữ đúng SUT và lịch sử, tránh phá dữ liệu/account của run khác |
| Commit ở mốc chính, không commit giai đoạn chuẩn bị | Nhóm tài liệu chuẩn bị vào mốc phù hợp sau này; bước hiện tại không staging/commit/push hoặc gợi ý lệnh/message |

Workflow chính thức:

| Bước | Hoạt động | Method | Endpoint | Nhóm |
| --- | --- | --- | --- | --- |
| 1 | Admin Login | POST | `/api/login` | Auth-heavy |
| 2 | View Categories | GET | `/api/categories` | Read-heavy |
| 3 | Create Category | POST | `/api/categories` | Transactional |
| 4 | Delete Category | DELETE | `/api/categories/:id` | Transactional |
| 5 | Verify Category Deletion | GET | `/api/categories` | Verification / Read-heavy |

## 7. Rủi ro cần kiểm soát ở giai đoạn performance test

- **Shared admin:** sai password hoặc negative auth/lockout chạy cùng account có thể ảnh hưởng toàn bộ VU. Source SUT tăng counter +2, khóa 180s khác FR-02; không có bằng chứng đo lockout timing trong Step 03. Login đúng lặp vẫn tạo ghi SQLite; predicate cuối cần gate admin state.
- **Phân quyền và validation:** JWT guard không kiểm tra admin role; name không bắt buộc/unique ở backend; input phải hợp lệ, đúng ownership. Duplicate name chưa phải vi phạm explicit FR-14; vẫn cần full-name uniqueness để cleanup/reconcile không ambiguous.
- **Stale ID và false success:** không DELETE khi Login/Create fail; clear biến từng vòng; response DELETE 200 không đủ, phải assert absence đúng ID/name. Không lấy ID bất kỳ từ public category list để xóa.
- **Interruption:** timeout có outcome không rõ; finally/tearDown không bao phủ forced kill; cần manifest và recovery độc lập. Không retry POST mù quáng hoặc reset database để cleanup.
- **Dữ liệu liên quan:** không xóa seeded/unrelated categories hoặc category được products tham chiếu. Đối chiếu products/baseline; công cụ không được biến negative probe thành destructive mutation.
- **Concurrency:** bảng categories toàn cục; thread khác có thể có record tạm khi GET; không assert toàn bộ list bằng baseline giữa run. Global baseline chỉ đối chiếu sau khi workload/recovery hoàn tất.
- **SQLite/tài nguyên:** đọc list và ghi INSERT/DELETE/UPDATE login có thể tranh chấp IO/locking/queue; response list tăng khi cleanup không kịp [HYP]. Chưa có measured throughput, latency, percentile hoặc bottleneck.
- **Startup reset:** source backend tự DROP/seed khi khởi động, làm mất dữ liệu và thay baseline. Không restart/reseed/reset; nếu có biến cố bên ngoài phải ghi nhận và dừng đánh giá run bị ảnh hưởng.
- **Secrets:** không lưu Login body/response thô, Authorization/token trong JTL, audit/log hoặc CSV category; không copy signing secret vào test plan để kiểm tra JWT.

## 8. Kiểm tra thông tin nhạy cảm và gitignore

### 8.1 Phương pháp và kết quả tài liệu

Kiểm tra chỉ đọc trên các file văn bản của repository bài tập ngoài `eshop-sut/` và `.git/`: Markdown, scripts, JSON evidence/config, gitignore và runtime text logs hiện có. Đối chiếu các literal credential/signing-secret đã biết từ source SUT **trong bộ nhớ**, mẫu JWT/private key và sensitive-key literal; chỉ xuất đường dẫn/dòng/loại nếu phát hiện, không in secret. Rà soát JSON evidence cho thấy chỉ lưu ID/role, account state, product/category snapshot và cờ tokenPresent/expPresent, không full Login response.

Lượt quét trước chỉnh sửa lúc **17:24:14 UTC+07:00 ngày 17/11/2026** đã kiểm tra 18 file văn bản, không có finding hoặc sensitive-key candidate cần xử lý. Các từ `password`, `Authorization`, `${token}`, sentinel `invalid-smoke-token` và các placeholder trong ví dụ là mô tả/biến/sentinel, không phải bằng chứng có credential thật bị lưu. Các dữ liệu user minh họa dùng fixture/synthetic; chưa phát hiện thông tin cá nhân không cần thiết trong tài liệu/evidence đã rà soát.

Lượt kiểm tra sau khi tạo/cập nhật ba tài liệu, lúc **17:37:40 UTC+07:00**, quét **19 file văn bản**: không có known-secret/JWT/private-key finding hoặc sensitive-key literal candidate cần xử lý; các liên kết local và định dạng khoảng trắng của ba tài liệu đều hợp lệ. Đây là kết quả của các phép kiểm tra đã mô tả, không mở rộng thành bảo đảm phát hiện mọi loại secrets.

SUT có source/SQLite chứa credentials theo thiết kế của bài tập; local SQLite backup là binary chứa dữ liệu database. Đây là dữ liệu local bị ignore, không được đưa vào báo cáo hoặc coi là đã “làm sạch” chỉ vì Git bỏ qua. Không đọc/dump nội dung binary backup để đưa vào output. Runtime logs được quét như văn bản nhưng vẫn phải tiếp tục giữ local.

**Giới hạn:** quét pattern/known literal không bảo đảm phát hiện mọi secret hay PII bất kỳ; đây không phải chứng nhận tuyệt đối rằng không thể có dữ liệu nhạy cảm. Nếu tương lai phát hiện secret trong evidence gốc, báo vị trí/hướng xử lý và xin chỉ dẫn phù hợp; không in giá trị hoặc tự sửa bằng chứng để che lịch sử.

### 8.2 Gitignore và tracked files

Đã dùng `git check-ignore --no-index -v` với path thực tế hoặc probe **không tạo file**; kiểm tra tracked paths riêng để tránh hiểu nhầm ignore bảo vệ file đã tracked.

| Dữ liệu/path kiểm tra | Kết quả | Quy tắc hiện có |
| --- | --- | --- |
| `eshop-sut/backend/server.js`, `eshop-sut/backend/database.sqlite` | Ignored | `.gitignore:2` `/eshop-sut/` |
| `node_modules/example/index.js` | Ignored | Dòng 5 `node_modules/` |
| `data/test.sqlite` | Ignored | Dòng 46 `*.sqlite` |
| Backup SQLite thực tế trong `audit/local-backups/` | Ignored | Dòng 46 `*.sqlite`; dump/bak còn có các pattern riêng |
| `audit/local-runtime/backend.stdout.log` | Ignored | Dòng 65 `*.log` |
| `.env`, `.env.local` | Ignored | Dòng 14–15 `*.env`, `*.env.*` |
| `data/credentials/admin.json` | Ignored | Dòng 21 `credentials/` |
| `credentials.json`, `secrets/admin.json` | Ignored | Dòng 27 `credentials*.json`, dòng 19 `secrets/` |
| `data/admin-config.json` | **Không tự ignored** | Probe minh họa; file này không được tạo, chưa phải secret thật được phát hiện |

Kết quả `git ls-files` kiểm tra các protected paths thực tế không có SUT/dependencies/local backups/logs/credential paths đã được theo dõi. `.gitignore` không loại file đã tracked và không đọc nội dung để biết một JSON tùy ý chứa password. Quy định đặt credentials vào path bảo vệ đã được cập nhật README/AGENTS; **không sửa `.gitignore`** trong bước này, vì chưa phát hiện file secrets thực tế bị lọt và phạm vi chỉ có ba tài liệu đầu ra.

## 9. Bảo toàn source, evidence và phạm vi chỉnh sửa

Chụp hash SHA256 trước cập nhật cho **104 file hiện có** ngoài các thư mục `.git` và `node_modules`; trong đó có **66 file SUT**, gồm source/tài liệu/assets và database hiện có, cùng **36 file khác cần bảo toàn** ngoài README/AGENTS được phép sửa. Hash Git index cũng được giữ để kiểm tra không staging. Hash là kiểm tra toàn vẹn file, không phải bản sao nội dung nhạy cảm.

Các file lịch sử trọng yếu cần bằng hash trước/sau:

```text
audit/smoke-category.cjs
f8f0f5383ee6288bca4524e9cbd9c98a72634b058238deb7e523ab3838bea56d

audit/03-category-smoke.json
c40b4130df433a174b50dad2131978adfabdc021065ff79ca74598427cb578b3

reports/task1/03-category-workflow-validation.md
4c44a86796db33583a7057a0a20626d893e8f294555fa7b124121a547b0b2e34
```

Giữ nguyên báo cáo 01/02/03, script/JSON smoke hiện có, audit khác, `.gitignore`, source và database SUT. Không sửa event labels trong evidence; không sửa Login assertion/predicate trong smoke script; không chạy script để tạo JSON “đẹp hơn”. README/AGENTS root được cập nhật đúng phạm vi; file mới duy nhất là báo cáo 04 này. Không sửa README hoặc tài liệu trong `eshop-sut/`.

Không thực hiện request HTTP/SQL vào SUT, restart backend, reset/reseed hoặc ghi SQLite trực tiếp trong bước hoàn tất tài liệu. Không tạo CSV/JMX/Skill/Issues và không Git add/commit/push. Hash index trước/sau và inventory file giúp kiểm tra phạm vi, không tự gán các file untracked có từ trước là file mới của bước này.

**Kết quả đối chiếu lúc 17:37:40 UTC+07:00:** chỉ `README.md` và `AGENTS.md` thay đổi trong tập file có sẵn; file mới duy nhất là `reports/task1/04-workflow-human-review.md`. Không có file bảo toàn nào thay đổi: toàn bộ 66 file SUT và 36 file lịch sử/cấu hình/placeholder/local artifacts ngoài hai file được phép sửa đều có hash bằng baseline. Git index trước/sau bằng nhau, xác nhận không staging trong bước này. Bản tổng kết xác minh này chỉ bổ sung vào báo cáo 04; không chỉnh sửa các file lịch sử.

## 10. Các điểm còn cần sinh viên review

**Đã chốt, không hỏi lại:** workflow năm bước, Pool A+C, JMeter 5.6.3, Login mỗi iteration, shared admin, thread-local JWT/category ID, CSV base name/runtime uniqueness, cleanup/recovery, không sửa source/reset và commit tại mốc chính.

**Chưa có bằng chứng sinh viên xác nhận từng nội dung kỹ thuật sau:**

1. Kết quả năm finding trong mục 4 và mức ưu tiên của chúng; xác nhận cách giải thích “đã có partial evidence nhưng chưa có recovery độc lập”, “đã kiểm tra một phần Login” và “actual adminAfter sạch nhưng chưa gate completion”.
2. Event schema mới, Login/accountAfter acceptance criteria, manifest durability, timeout/recovery window và cách xử lý unknown/ambiguous outcome. Cần yêu cầu bước sau trước khi triển khai.
3. CSV EOF/recycle, run/engine naming, cách truyền credentials local và logging/JTL sanitization cụ thể.
4. Nguồn quyền sở hữu môi trường/baseline để không xung đột người dùng khác; recovery độc lập phải được review trước khi có quyền xóa dữ liệu.
5. Workload settings, SLA và resource monitoring cho Load/Stress/Spike/Endurance; chưa chọn số VU/duration/threshold trong bước này.
6. Rubric có yêu cầu số endpoint khác nhau tối thiểu hay không: năm bước hiện tại có bốn method–endpoint contracts. Đây là kiểm tra phạm vi rubric, **không phải quyền tự đổi workflow hoặc thêm Update**.

## 11. Mức độ sẵn sàng và file đầu ra

**CONDITIONAL GO** để tiếp tục **xây dựng Agent Skill và sau đó thiết kế test plan khi sinh viên yêu cầu**. Tài liệu chuẩn bị đã thống nhất lựa chọn và bảo toàn bằng chứng. Các cải tiến còn lại được ghi thành yêu cầu cho giai đoạn tiếp theo, chưa được cài đặt, chưa được fault/concurrency/performance test.

Ba file của bước hiện tại:

- `README.md` — đổi workflow/endpoints/pools, ghi quyết định thiết kế, tiến độ, hạn chế SUT, links tài liệu và Git policy.
- `AGENTS.md` — cập nhật phạm vi, năm bước chính thức, quyết định đã chốt, bảo toàn evidence và quy tắc secrets/Git; giữ quy ước cấu trúc/lưu trữ không liên quan.
- `reports/task1/04-workflow-human-review.md` — báo cáo đối chiếu này, phân biệt nhận định AI/quyết định sinh viên/nội dung chờ review; không tự xác nhận Human Review đã diễn ra.

Dừng ở tài liệu chuẩn bị. Chờ sinh viên review và yêu cầu tiếp theo; không tự động tạo Agent Skill hoặc triển khai bất kỳ test plan nào.
