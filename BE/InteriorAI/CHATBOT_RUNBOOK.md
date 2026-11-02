# Vận hành chatbot Home AI

Tài liệu này mô tả chatbot đang chạy trong mã nguồn. Khi mâu thuẫn với các tài liệu kế hoạch cũ, hãy tin tài liệu này và mã nguồn.

## Luồng một lượt chat

```text
Tin nhắn → ChatSafetyGuard → câu chào / danh sách phong cách
         → HybridRetriever (embedding + từ khóa, RRF, ngưỡng cosine 0.62)
         → không có đoạn đạt ngưỡng: gợi ý theo luật hoặc nói chưa đủ căn cứ
         → GeminiChatAnswerGenerator trả { reply, usedChunkIds, briefPatch }
         → server kiểm tra rồi lưu tin nhắn, citation và brief trong một lần SaveChanges
         → Gemini lỗi: InteriorAdvicePlanner trả lời từ đoạn đã truy hồi
```

Phòng đang bàn được xác định theo thứ tự: phòng nêu trong tin nhắn mới nhất → brief → phòng người dùng nêu gần nhất trong lịch sử. Truy hồi loại tài liệu của phòng khác, còn câu trả lời chỉ nói về phòng đang bàn, trừ khi người dùng hỏi so sánh hoặc hỏi nhiều phòng.

Chỉ `POST .../brief/confirm` mới tạo job ảnh. LLM chỉ đề xuất giá trị cho brief, và server kiểm tra các giá trị này theo danh sách cho phép.

## Cài đặt lần đầu

```powershell
cd BE
dotnet ef database update --project InteriorAI
dotnet run --project InteriorAI.KnowledgeIngest
```

Cần có `Gemini:ApiKey` và chuỗi kết nối trong `appsettings*.json` cục bộ. Chạy lại `InteriorAI.KnowledgeIngest` sau mỗi lần sửa `knowledge-base/docs`; đoạn không đổi sẽ không bị tạo embedding lại. Backend đang chạy cần khởi động lại để nạp embedding mới.

## Chạy demo trên emulator

```powershell
.\run-emulator-dev.ps1
```

Emulator `Pixel_9` dùng địa chỉ `http://10.0.2.2:5207/`. Chat API chỉ mở ở Development và từ loopback cho tới khi có xác thực bằng token; kết nối từ emulator qua `10.0.2.2` được tính là loopback.

Với điện thoại USB, chạy `.\run-usb-dev.ps1` và dùng địa chỉ `http://127.0.0.1:5207/`.

## Cấu hình

| Khóa | Mặc định | Ý nghĩa |
|---|---|---|
| `Chat:GeminiEnabled` | `true` trong `launchSettings` | Bật sinh câu trả lời. Tắt thì dùng trả lời theo luật. |
| `Chat:AnswerMode` | `Hybrid` | `Hybrid`, `Lexical` hoặc `NoRag`, dùng để so sánh trong báo cáo. |
| `Gemini:Model` | `gemini-3.1-flash-lite` trong `launchSettings` | Model sinh câu trả lời. `gemini-3-flash-preview` ở free tier chỉ cho 20 lượt mỗi ngày. |
| `Knowledge:EmbeddingModel` | `gemini-embedding-2` | Đổi model thì phải chạy lại ingest. |
| `Knowledge:MinCosine` | `0.62` | Ngưỡng liên quan, chọn bằng bộ 60 câu. Nên đo lại khi kho tài liệu đổi. |
| `Knowledge:IncludeDrafts` | `true` trong `launchSettings` | Cho phép dùng bài nháp. Tắt thì chỉ dùng bài đã duyệt có nguồn đã duyệt. |

## Kiểm thử

```powershell
cd BE
dotnet test InteriorAI.Tests
dotnet run --project InteriorAI.KnowledgeSmoke

cd ..\FE\ReRoom
.\gradlew testDebugUnitTest
```

## Đánh giá

```powershell
cd BE
# Chỉ đo truy hồi, tốn ít quota
dotnet run --project InteriorAI.ChatEval -- --modes Lexical,Hybrid --retrieval-only --delay-ms 500
# Đầy đủ, gọi Gemini nhiều lần và mất khoảng 30 phút
dotnet run --project InteriorAI.ChatEval -- --modes Lexical,Hybrid,NoRag --delay-ms 6000
```

Kết quả nằm ở `knowledge-base/eval/results/` và không được commit. File CSV có cột `score_groundedness_0_2`, `score_helpfulness_0_2` để người chấm điền. Thêm `--approved-only` để chỉ dùng bài đã duyệt; số liệu đưa vào báo cáo nên chạy theo cách này.

### Kết quả tạm ngày 01/11/2026

Chạy trên 55 bài nháp với `gemini-3.1-flash-lite`; bộ câu hỏi chưa được duyệt và câu trả lời chưa được người chấm. Không dùng làm số liệu cuối trong báo cáo.

| Chế độ | Hit@5 | MRR | Từ chối đúng | Từ chối nhầm | Lượt có Gemini | Citation hợp lệ | P50 / P95 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Lexical | 93,3% | 0,911 | 12/15 | 1 | 32/60 | 53/53 | 2,3 s / 16,5 s |
| Hybrid | 97,8% | 0,938 | 15/15 | 1 | 31/60 | 57/57 | 4,1 s / 14,0 s |
| NoRag | – | – | 9/15 | 12 | 39/60 | – | 4,8 s / 21,0 s |

Cách đọc: chênh lệch Hit@5 chỉ là 2 câu, và ngưỡng được chỉnh trên chính bộ câu này. Trong đợt chạy có 34 lỗi 503 từ Gemini, 15 lần hết giờ và 7 lần embedding bị 429, nên nhiều lượt rơi về fallback. Phần lớn "từ chối nhầm" của NoRag là do lỗi nhà cung cấp, không phải do thiếu tài liệu. Cần chạy lại khi có quota ổn định.

## Giới hạn hiện tại

- `user-id` vẫn do client tự khai; chưa có xác thực bằng token.
- Job ảnh chạy bằng `Task.Run`, nên restart API có thể làm mất job đang xử lý. Việc gửi lại đã được chống tạo trùng bằng `clientRequestId`.
- Tất cả 55 bài và bộ 60 câu vẫn là bản nháp chưa được duyệt; 34 bài chưa có nguồn bên ngoài. Xem `knowledge-base/source-candidates.md`.
- Ngưỡng 0.62 được chọn trên chính bộ câu dùng để đánh giá, chưa có tập kiểm tra riêng.
- Câu ngắn thiếu ngữ cảnh có thể bị đánh giá dưới ngưỡng và được hỏi lại, ví dụ "Thảm có kết cấu dày có hợp không?".
