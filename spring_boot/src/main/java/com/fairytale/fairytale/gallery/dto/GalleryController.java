package com.fairytale.fairytale.gallery.dto;

import com.fairytale.fairytale.gallery.GalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gallery")
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryService galleryService;

    /**
     * 사용자의 갤러리 이미지 목록 조회
     */
    @GetMapping("/images")
    public ResponseEntity<List<GalleryImageDTO>> getUserGalleryImages(Authentication auth) {
        try {
            String username = auth.getName();
            System.out.println("🔍 갤러리 이미지 조회 요청 - 사용자: " + username);

            List<GalleryImageDTO> galleryImages = galleryService.getUserGalleryImages(username);

            System.out.println("✅ 갤러리 이미지 조회 완료 - 개수: " + galleryImages.size());
            return ResponseEntity.ok(galleryImages);

        } catch (Exception e) {
            System.err.println("❌ 갤러리 이미지 조회 실패: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 특정 스토리의 갤러리 이미지 조회
     */
    @GetMapping("/images/{storyId}")
    public ResponseEntity<GalleryImageDTO> getStoryGalleryImage(
            @PathVariable Long storyId,
            Authentication auth) {
        try {
            String username = auth.getName();
            System.out.println("🔍 특정 스토리 갤러리 조회 - StoryId: " + storyId + ", 사용자: " + username);

            GalleryImageDTO galleryImage = galleryService.getStoryGalleryImage(storyId, username);

            if (galleryImage != null) {
                System.out.println("✅ 스토리 갤러리 이미지 조회 완료");
                return ResponseEntity.ok(galleryImage);
            } else {
                System.out.println("⚠️ 해당 스토리의 갤러리 이미지 없음");
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            System.err.println("❌ 스토리 갤러리 이미지 조회 실패: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 색칠한 이미지 업로드/업데이트
     */
    @PostMapping("/coloring/{storyId}")
    public ResponseEntity<GalleryImageDTO> updateColoringImage(
            @PathVariable Long storyId,
            @RequestBody ColoringImageRequest request,
            Authentication auth) {
        try {
            String username = auth.getName();
            System.out.println("🔍 색칠한 이미지 업데이트 - StoryId: " + storyId + ", 사용자: " + username);

            GalleryImageDTO updatedImage = galleryService.updateColoringImage(storyId, request.getColoringImageUrl(), username);

            System.out.println("✅ 색칠한 이미지 업데이트 완료");
            return ResponseEntity.ok(updatedImage);

        } catch (Exception e) {
            System.err.println("❌ 색칠한 이미지 업데이트 실패: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 갤러리 이미지 삭제
     */
    @DeleteMapping("/images/{storyId}")
    public ResponseEntity<Void> deleteGalleryImage(@PathVariable Long storyId, Authentication auth) {
        try {
            String username = auth.getName();
            System.out.println("🔍 갤러리 이미지 삭제 - StoryId: " + storyId + ", 사용자: " + username);

            boolean deleted = galleryService.deleteGalleryImage(storyId, username);

            if (deleted) {
                System.out.println("✅ 갤러리 이미지 삭제 완료");
                return ResponseEntity.ok().build();
            } else {
                System.out.println("⚠️ 삭제할 갤러리 이미지 없음");
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            System.err.println("❌ 갤러리 이미지 삭제 실패: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 갤러리 통계 조회
     */
    @GetMapping("/stats")
    public ResponseEntity<GalleryStatsDTO> getGalleryStats(Authentication auth) {
        try {
            String username = auth.getName();
            System.out.println("🔍 갤러리 통계 조회 - 사용자: " + username);

            GalleryStatsDTO stats = galleryService.getGalleryStats(username);

            System.out.println("✅ 갤러리 통계 조회 완료");
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            System.err.println("❌ 갤러리 통계 조회 실패: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
}