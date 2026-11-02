---
id: principle-preserve-01
title: Giữ yếu tố hiện trạng khi thiết kế từ ảnh
language: vi
document_type: principle_guide
styles: []
room_types: []
topics: [photo_redesign, layout, constraints]
source_type: internal_derived
source_ids: [seed-room-prompts, app-design-prompt]
source_url: null
license_note: Nội dung mới do nhóm biên soạn từ mã nguồn dự án
author: Home AI content draft
reviewer: pending
version: 1
reviewed_at: null
status: draft
---

# Giữ yếu tố hiện trạng khi thiết kế từ ảnh

## Gợi ý có điều kiện
Khi người dùng đưa ảnh, hãy hỏi họ muốn giữ cửa sổ, cửa ra vào, trần, sàn, cột, đồ đạc hoặc góc chụp nào. Dịch vụ tạo prompt của dự án cố gắng giữ kiến trúc và hình học phòng, nhưng mô hình ảnh vẫn có thể vẽ sai. Vì vậy cần so sánh ảnh trước/sau và nêu rõ chi tiết nào bị thay đổi ngoài ý muốn.

Nếu người dùng nói “giữ cửa sổ và đổi màu tường”, mô tả yêu cầu nên tập trung vào màu/bề mặt, không diễn giải thành mở rộng cửa. Nếu ảnh kết quả làm mất cửa, không coi đó là phương án xây dựng được.

## Cần hỏi thêm
Yếu tố nào phải giữ chính xác, chỉ muốn đổi đồ rời hay cả bề mặt hoàn thiện, và ảnh có thấy rõ các vị trí cần giữ không?

## Giới hạn
Không cam kết AI giữ 100% hình học căn phòng. Mọi thay đổi kiến trúc, kết cấu hoặc hệ thống kỹ thuật phải được xác minh ngoài ứng dụng.

## Nguồn
- seed-room-prompts: các ràng buộc giữ hiện trạng trong ví dụ ảnh.
- app-design-prompt: dịch vụ biên dịch prompt cho mô hình ảnh.
