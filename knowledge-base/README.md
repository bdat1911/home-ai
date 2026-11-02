# Kho tri thức chatbot Home AI — bản biên soạn thử nghiệm

Thư mục này chứa nội dung tiếng Việt do nhóm biên soạn để phục vụ tư vấn nội thất. Phạm vi bám theo 6 phong cách và 5 loại phòng trong `BE/InteriorAI/Data/Seed`. Nội dung seed là **prompt tạo ảnh**, không phải tài liệu chuyên môn độc lập; các câu suy luận thẩm mỹ trong tài liệu được ghi rõ là gợi ý, không phải quy tắc bắt buộc.

Hiện có **55 bài** trong `docs/`: 51 bài tư vấn nội thất (6 phong cách, 5 loại phòng, 30 cặp phong cách–phòng, 5 bài màu, 5 nguyên tắc) và 4 bài hướng dẫn sử dụng ứng dụng. Đây là **bản nháp cần người trong nhóm hoặc cố vấn duyệt**. Tất cả có `status: draft`, `reviewer: pending`, `reviewed_at: null`. Chỉ chuyển từng bài sang `approved` sau khi kiểm tra nội dung, nguồn, quyền sử dụng và lỗi thực tế. Không tự động dùng các bài draft trong chatbot công khai.

## Tích hợp bộ 41 hồ sơ nháp

Bản Markdown tổng hợp `HOME_AI_LOP_A_KNOWLEDGE_BASE_RAG.md` dùng làm đề cương biên tập, không nạp nguyên khối. Từ `HOME_AI_LOP_A_41_HO_SO_DRAFT.zip`, 26 chủ đề đã có bài trong repository được giữ nguyên ID và nội dung; 15 cặp phong cách–phòng còn thiếu được biên tập vào `docs/style-room/`. Các bản mới dùng `document_type: style_room_guide`, ID chỉ gồm chữ thường, số và dấu gạch nối, cùng đề mục `## Cần hỏi thêm`/`## Giới hạn` để bộ truy hồi hiện tại xử lý được. Nhãn `Design Brief gợi ý` trong bản thảo không được đưa vào nội dung tư vấn cho người dùng.

15 bài này kế thừa ý tưởng thẩm mỹ từ bản thảo do người dùng cung cấp và dữ liệu seed của dự án. `source_ids` nêu các nguồn cần kiểm tra, **không xác nhận các nguồn đã được duyệt**. Trước khi đổi trạng thái của bất kỳ bài nào sang `approved`, phải đối chiếu từng nhận định với nguồn, điều kiện áp dụng, quyền sử dụng và có người duyệt thật. Bản ZIP gốc không được sao chép vào thư mục chatbot để tránh nạp trùng chủ đề.

`source-register.csv` ghi nguồn nội bộ và nguồn công khai dùng làm căn cứ. Nội dung ngoài được diễn giải lại, không sao chép dài. `eval/smoke-questions.csv` là câu hỏi thử nhanh; `eval/questions.csv` là bộ 60 câu đánh giá.

## Cách đọc và dùng

- `source_ids` trong front matter phải khớp `source-register.csv`.
- `styles` dùng đúng tên style seed; `room_types` dùng đúng code seed (`living_room`, `master_bedroom`, `kitchen`, `bathroom`, `study_room`). Mảng rỗng nghĩa là tài liệu dùng chung, không phải thiếu dữ liệu.
- Những con số phần trăm màu trong seed là mô tả cho **mẫu ảnh**, không phải định mức thiết kế. Bài tư vấn chỉ đưa lựa chọn tham khảo và cần hỏi điều kiện thực tế.
- Các đoạn `Cần hỏi thêm` và `Giới hạn` là nội dung cho chatbot nêu điều kiện/từ chối đúng khi thiếu dữ liệu.
- Nguồn trong tài liệu là dấu vết biên tập và được kiểm tra nội bộ khi bật Gemini. Citation hiển thị cho người dùng luôn gắn với đoạn thật sự được truy hồi trong lượt đó.

## Trạng thái tích hợp

Chatbot dùng kho này qua truy hồi kết hợp embedding và từ khóa; xem `BE/InteriorAI/CHATBOT_RUNBOOK.md`. Mỗi mục trong bài là một đoạn truy hồi có ID ổn định. Mục `Giới hạn` và `Cần hỏi thêm` được gửi kèm đoạn truy hồi cho LLM, và mục `Nguồn` không được truy hồi. Câu trả lời hiển thị tên bài và nguồn của các đoạn đã dùng; nguồn nội bộ được ghi là "Tài liệu nội bộ Home AI".

Sau khi sửa bài, chạy lại `InteriorAI.KnowledgeIngest`. Ngoài môi trường có `Knowledge:IncludeDrafts=true`, chỉ bài `approved` có toàn bộ nguồn `approved`, người duyệt và ngày duyệt hợp lệ mới được dùng. Không dùng tài liệu này để quyết định kết cấu, điện, chống thấm, kích thước an toàn hoặc giá thi công.

`eval/questions.csv` là bộ 60 câu đánh giá ở trạng thái nháp, gồm 15 câu smoke cũ và 45 câu mới. `source-candidates.md` liệt kê nguồn bên ngoài cần người duyệt kiểm tra.
