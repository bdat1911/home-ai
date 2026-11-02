# Kế hoạch xây dựng kho tài liệu cho chatbot tư vấn nội thất

**Ngày lập:** 17/10/2026  
**Áp dụng cho:** Home AI / InteriorAI  
**Liên hệ:** CHATBOT_DEVELOPMENT_PLAN.md, mục 10 và 13  
**Trạng thái:** Đã biên soạn 36 bài tư vấn + 4 bài hướng dẫn ứng dụng ở trạng thái draft; backend Development có tra cứu từ khóa, nguồn theo ID đoạn và tùy chọn tạo câu trả lời bằng Gemini (tắt mặc định). Chưa có embedding, kho đã duyệt hoặc đánh giá RAG đầy đủ.

## 1. Mục tiêu và cách hiểu đúng

Kho tài liệu giúp chatbot **tra cứu khi trả lời (RAG)**, không phải dữ liệu để huấn luyện lại mô hình. Một LLM sẽ hiểu câu hỏi và diễn đạt; lịch sử hội thoại/Design Brief lưu sở thích của người dùng; kho tri thức cung cấp nội dung có nguồn để trả lời. Chỉ tạo file PDF hoặc Markdown mà chưa xây ingestion, retrieval và citation thì chatbot hiện tại vẫn không dùng được tài liệu.

Đích của đợt này:

- Trả lời câu hỏi tiếng Việt về phong cách, màu sắc, vật liệu, ánh sáng và ứng dụng theo loại phòng.
- Nêu điều kiện và giới hạn của gợi ý; hỏi thêm khi thiếu loại phòng, sắc độ màu, diện tích hoặc ánh sáng.
- Dẫn được tài liệu/đoạn đã dùng và nói “chưa đủ căn cứ” khi kho không có câu trả lời.
- Không tự ý tạo ảnh, báo giá chính xác hoặc tư vấn kết cấu/điện/nước từ tài liệu này.

Trước đợt triển khai này, ChatReplyService chỉ dò tên phong cách trong câu hỏi. Hiện bản Development/loopback đã đọc Markdown và trả đoạn liên quan kèm ID tài liệu, ví dụ câu hỏi “Tôi thích màu xanh lam, hợp phong cách nào?” có thể tìm bài màu xanh lam. Đây là **tra cứu từ khóa và ghép đoạn có nguồn thử nghiệm**, chưa phải semantic RAG: chưa có embedding, LLM, đánh giá đủ 60 câu hoặc nội dung được cố vấn duyệt. Production không phục vụ bài draft.

## 2. Phạm vi kiến thức của Home AI

Dữ liệu seed hiện có **6 phong cách**: Indochine, Minimalist Luxury, Modern Luxury, Industrial, Neoclassic, Scandinavian; và **5 loại phòng**: living_room, master_bedroom, kitchen, bathroom, study_room. RoomStylePromptSeedData chứa 30 cặp phong cách–phòng. Đây là điểm xuất phát để lập danh mục, **không phải nguồn chuyên môn đã được xác thực tự động**.

### Danh mục tài liệu mục tiêu

| Nhóm | Số tài liệu | Nội dung mỗi tài liệu |
|---|---:|---|
| Hồ sơ phong cách | 6 | Đặc trưng, bảng màu, vật liệu, ánh sáng, điều kiện phù hợp và điều nên tránh cho từng style đang có. |
| Hướng dẫn theo phòng | 5 | Mục tiêu công năng, câu hỏi cần hỏi người dùng và lưu ý thẩm mỹ của từng loại phòng đang hỗ trợ. |
| Phong cách × phòng | 15 | Chọn 3 cặp tiêu biểu mỗi loại phòng từ 30 cặp seed; giải thích cách áp dụng và giới hạn. Đảm bảo cả 6 style đều xuất hiện ít nhất 2 lần. |
| Hướng dẫn phối màu | 5 | Xanh lam; xanh lá; trung tính ấm; xám/đen; pastel. Phân biệt sắc độ và vai trò màu nền/điểm nhấn. |
| Nguyên tắc dùng chung | 5 | Chiếu sáng; vật liệu và bảo trì; không gian nhỏ/lưu trữ; bố trí và công năng; giữ các yếu tố hiện trạng khi cải tạo từ ảnh. |
| **Tổng** | **36** | Nằm trong mục tiêu 30–50 tài liệu của kế hoạch chatbot tổng. |

**Bản thử nghiệm đầu tiên chỉ cần 12 tài liệu:** 6 hồ sơ phong cách + 5 hướng dẫn phòng + 1 hướng dẫn xanh lam. Sau khi có kết quả truy hồi tốt mới mở rộng lên 36. Không tuyên bố bot tư vấn tốt cả 30 cặp phong cách–phòng khi mới biên soạn 15 cặp.

Không đưa vào corpus chung: ảnh và hội thoại riêng của người dùng, dữ liệu cá nhân, giá sản phẩm thay đổi liên tục, nội dung quảng cáo, hướng dẫn kết cấu/điện/nước hoặc quy định pháp lý chưa được người có chuyên môn kiểm tra.

## 3. Tìm nguồn và kiểm soát quyền sử dụng

Thứ tự ưu tiên:

1. **Nội dung nhóm tự biên soạn:** dùng seed hiện tại như danh sách chủ đề, viết lại bằng tiếng Việt cho người dùng, có người rà soát. Phân biệt rõ “đặc trưng ghi trong seed” với “gợi ý của nhóm”.
2. **Tài liệu được cố vấn/chuyên gia cho phép sử dụng:** ghi tác giả, ngày, phạm vi cho phép và người duyệt. Không coi tài liệu được gửi riêng là tài liệu được phép công khai.
3. **Nguồn ngoài có quyền sử dụng rõ ràng:** lưu URL gốc, tác giả/nhà xuất bản, giấy phép, ngày truy cập, phần được dùng và yêu cầu ghi công. Chỉ dùng nguồn mà nhóm đã kiểm tra điều kiện sử dụng; không sao chép hàng loạt sách/PDF/trang web chỉ vì chúng đọc được trên Internet.
4. **Dữ liệu từ người dùng thật:** chỉ dùng cho kho chung nếu có quyền và sự đồng ý phù hợp; mặc định loại trừ.

Tạo sổ nguồn đề xuất tại knowledge-base/source-register.csv, mỗi dòng gồm:

    source_id,title,author_or_publisher,source_url,license_or_permission,accessed_at,scope_of_use,reviewer,status

Trạng thái nguồn: proposed → approved → rejected/retired. Chỉ tài liệu dựa trên nguồn approved mới được publish. Nếu nguồn được sửa, gỡ hoặc hết quyền dùng, đánh dấu tài liệu liên quan để rà soát.

Đối với CC BY 4.0, cần ghi công, liên kết giấy phép và nêu thay đổi nếu có; các giấy phép khác có điều kiện khác nhau. Kiểm tra chính giấy phép của từng nguồn trước khi tái sử dụng, không mặc định mọi tài liệu “miễn phí đọc” đều được phép đưa vào ứng dụng.

## 4. Cấu trúc lưu trữ đề xuất

Chưa cần đưa PDF thô vào backend. Biên soạn bản chuẩn bằng Markdown UTF-8 để dễ review, diff và chia theo heading. PDF/website chỉ là nguồn tham khảo đã đăng ký.

~~~text
knowledge-base/
  source-register.csv
  docs/
    styles/
    rooms/
    style-room/
    colors/
    principles/
  eval/
    questions.csv
    review-notes.md
  archive/
~~~

Mỗi tài liệu tập trung vào **một chủ đề chính**, có tiêu đề và mục nhỏ rõ ràng. Tránh một file dài gom mọi phong cách hoặc lặp lại cùng lời khuyên ở nhiều file. Tên file ổn định, ví dụ colors/blue-accents.md; ID tài liệu ổn định khi sửa version.

### Mẫu tài liệu Markdown

~~~yaml
---
id: color-blue-01
title: Gợi ý sử dụng màu xanh lam trong nội thất
language: vi
document_type: color_guide
styles: [Scandinavian]
room_types: [living_room, master_bedroom]
topics: [color, lighting]
source_type: internal_derived
source_ids: [seed-design-style-scandinavian]
source_url:
license_note: Nội dung do nhóm biên soạn từ dữ liệu dự án
author: Tên người viết
reviewer: Tên người duyệt
version: 1
reviewed_at: YYYY-MM-DD
status: draft
---

# Gợi ý sử dụng màu xanh lam trong nội thất

## Cơ sở từ dữ liệu
Ghi rõ phần nào đến từ seed/tài liệu nguồn. Ví dụ: seed Scandinavian mô tả nền trắng,
gỗ sáng và điểm nhấn pastel.

## Gợi ý có điều kiện
Nêu một vài lựa chọn, điều kiện áp dụng và đánh đổi. Không khẳng định chỉ có một
phong cách đúng cho mọi sắc độ xanh lam.

## Cần hỏi người dùng
Ví dụ: xanh lam nhạt hay xanh navy; phòng nào; nhiều hay ít ánh sáng tự nhiên?

## Giới hạn
Nêu điều kho tài liệu chưa kết luận hoặc cần xác minh thêm.

## Nguồn
Liệt kê source_id và URL/đường dẫn nguồn đã duyệt.
~~~

Ví dụ trên là **mẫu biên soạn**, không phải bài tư vấn hoàn chỉnh được phép publish. Đừng điền nội dung chỉ để đủ số tài liệu; một tài liệu thiếu căn cứ nên ở trạng thái draft.

## 5. Quy trình viết và duyệt từng tài liệu

1. Chọn câu hỏi người dùng mà tài liệu phải trả lời được, ví dụ “Tôi thích xanh lam nhạt, phòng ngủ hợp phong cách nào?”.
2. Kiểm tra corpus hiện có để tránh trùng. Chọn nguồn, đăng ký và duyệt quyền sử dụng.
3. Viết tiếng Việt rõ, ngắn; phân biệt **thông tin có nguồn**, **suy luận thẩm mỹ của nhóm** và **giới hạn/điều cần hỏi thêm**.
4. Gắn metadata theo mẫu. Với lời khuyên theo phòng, dùng room code đúng với ứng dụng; với phong cách, dùng đúng tên trong seed rồi để ingestion map sang StyleId.
5. Người khác trong nhóm hoặc cố vấn rà soát: tính đúng nguồn, mâu thuẫn với seed, cách diễn đạt, khả năng áp dụng, quyền sử dụng.
6. Chuyển draft → approved. Chỉ approved mới được ingestion; lưu version và lịch sử chỉnh sửa.

Checklist duyệt một file:

- [ ] Có ID, title, tác giả, người duyệt, version, nguồn và quyền sử dụng.
- [ ] Trả lời ít nhất một câu hỏi thực tế; không chỉ chép mô tả phong cách.
- [ ] Không mâu thuẫn với bảng màu/vật liệu của style đang hỗ trợ; ngoại lệ được giải thích rõ.
- [ ] Không đưa số đo, giá, tiêu chuẩn hoặc lời khẳng định an toàn khi chưa có nguồn phù hợp.
- [ ] Có điều kiện áp dụng, câu hỏi làm rõ và giới hạn.
- [ ] Không chứa thông tin riêng của người dùng hoặc prompt instruction gửi cho LLM.
- [ ] Tiếng Việt tự nhiên, nhất quán thuật ngữ; không có đoạn sao chép không được phép.

## 6. Bàn giao cho kỹ thuật RAG

Đây là bước **sau khi có tài liệu approved**, không phải nhiệm vụ người viết tài liệu phải tự làm:

1. Script ingestion đọc Markdown, kiểm tra metadata/source/status, chuẩn hóa văn bản và bỏ phần không cần truy hồi.
2. Chia theo heading; thử cấu hình 300–600 token/chunk và overlap 50–100 token như kế hoạch chatbot tổng, rồi điều chỉnh theo bộ câu hỏi kiểm thử. Không cắt rời một điều kiện áp dụng khỏi lời khuyên của nó.
3. Tạo embedding, lưu KnowledgeDocument/KnowledgeChunk kèm document ID, chunk ID, version và content hash; tài liệu không đổi thì không tạo embedding lại.
4. Khi người dùng hỏi, kết hợp câu hỏi với room/style đã biết, lấy vài chunk liên quan, lọc chunk yếu/trùng rồi mới đưa cho LLM.
5. Server chỉ trả citation đến chunk thực sự được truy hồi; nếu không đủ bằng chứng, bot nói chưa đủ căn cứ và hỏi thêm. Nội dung tài liệu là dữ liệu, không phải instruction có quyền điều khiển chatbot.

Không cần chọn vector database riêng trước khi thử corpus nhỏ. Kế hoạch chatbot tổng đã dự tính SQL Server + cache/tính similarity trong C# cho vài trăm chunk; chỉ đổi kiến trúc khi phép đo cho thấy cần.

## 7. Bộ câu hỏi đánh giá phải làm song song

Tạo khoảng **60 câu tiếng Việt** theo kế hoạch tổng: 35 câu có đáp án trong corpus, 10 câu nhiều lượt/đổi ý, 10 câu không có nguồn hoặc ngoài phạm vi, 5 câu thử prompt injection. Với mỗi câu ghi:

    id,question,conversation_context,expected_document_ids,expected_points,should_clarify,should_abstain,forbidden_claims,reviewer

Ví dụ cần có: thích xanh lam nhưng chưa nói sắc độ/phòng; xanh lam nhạt trong phòng ngủ; đổi từ xanh nhạt sang navy ở lượt sau; hỏi giá thi công hiện tại; yêu cầu bỏ qua tài liệu và bịa nguồn. Không viết bộ đánh giá chỉ bằng cách sao chép nguyên câu trong tài liệu.

Đo riêng **retrieval** và **câu trả lời**: Hit@5 trên câu có nguồn, tính hợp lệ của citation, mức độ bám nguồn, độ hữu ích, khả năng hỏi làm rõ và từ chối đúng. So sánh bot hiện tại, LLM không RAG và LLM + RAG trên cùng câu hỏi. Các ngưỡng thử nghiệm ban đầu để nhóm tự hiệu chỉnh sau baseline: 100% tài liệu publish có metadata/quyền dùng/người duyệt; Hit@5 tối thiểu 80% trên 35 câu có nguồn; 100% citation ID trả về tồn tại trong tập chunk truy hồi; ít nhất 8/10 câu ngoài phạm vi được xử lý đúng.

## 8. Lịch làm việc gợi ý và tiêu chí hoàn thành

| Giai đoạn | Việc làm | Đầu ra |
|---|---|---|
| 1. Kiểm kê (1–2 ngày) | Chốt 6 style, 5 phòng, 30 cặp seed; chọn 15 cặp ưu tiên; lập sổ nguồn và 20 câu đánh giá đầu. | Danh mục tài liệu và nguồn approved ban đầu. |
| 2. Bản thử nghiệm (4–6 ngày) | Viết 12 tài liệu đầu; review chéo; hoàn thành đủ 60 câu đánh giá. | 12 Markdown approved + questions.csv. |
| 3. Thử RAG (3–5 ngày, song song backend) | Ingest, truy hồi, trả citation; chạy bộ câu hỏi; sửa tài liệu gây lỗi. | Báo cáo lỗi retrieval/answer và version tài liệu đã sửa. |
| 4. Mở rộng (theo kết quả đo) | Viết thêm đến 36 tài liệu, ưu tiên chủ đề người dùng hỏi nhiều và chỗ corpus thiếu. | Corpus 36 tài liệu có version, nguồn và biên bản duyệt. |

Hoàn thành phần **tài liệu** khi: có tối thiểu 12 file approved cho pilot; danh mục 36 file được phân công; mọi file publish có nguồn/quyền dùng/version/người duyệt; bộ 60 câu và đáp án/nguồn kỳ vọng đã được rà soát. Hoàn thành phần **RAG** chỉ khi backend thực sự truy hồi chunk, trả citation kiểm chứng được và đạt kết quả đánh giá; không đánh đồng hai mốc này.

## 9. Tài liệu phương pháp tham khảo

- [Microsoft: chuẩn bị nội dung cho RAG](https://learn.microsoft.com/en-us/azure/architecture/ai-ml/guide/rag/rag-preparation-phase) — kiểm kê loại nội dung, cấu trúc, quyền truy cập và dữ liệu thử nghiệm.
- [Microsoft: chia tài liệu thành chunk](https://learn.microsoft.com/en-us/azure/search/vector-search-how-to-chunk-documents) — chia nhỏ theo ý nghĩa và giới hạn đầu vào của mô hình.
- [Microsoft: đánh giá RAG](https://learn.microsoft.com/en-us/azure/foundry/concepts/evaluation-evaluators/rag-evaluators) — phân biệt chất lượng truy hồi với tính bám nguồn/độ liên quan của câu trả lời.
- [Creative Commons: CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.en) — ví dụ điều kiện ghi công khi dùng tài liệu có giấy phép này; luôn kiểm tra giấy phép cụ thể của từng nguồn.

Các nguồn phương pháp trên **không phải** tài liệu nội thất để chatbot trả lời người dùng. Chúng chỉ hướng dẫn nhóm xây và đánh giá kho tri thức.
