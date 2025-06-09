package com.fairytale.fairytale.gallery;

import com.fairytale.fairytale.gallery.dto.GalleryImageDTO;
import com.fairytale.fairytale.gallery.dto.GalleryStatsDTO;
import com.fairytale.fairytale.story.Story;
import com.fairytale.fairytale.story.StoryRepository;
import com.fairytale.fairytale.users.Users;
import com.fairytale.fairytale.users.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GalleryService {

    private final StoryRepository storyRepository;
    private final UsersRepository usersRepository;
    private final GalleryRepository galleryRepository;

    /**
     * 사용자의 모든 갤러리 이미지 조회
     */
    public List<GalleryImageDTO> getUserGalleryImages(String username) {
        System.out.println("🔍 사용자 갤러리 이미지 조회 시작 - 사용자: " + username);

        // 1. 사용자 조회
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username));

        // 2. 사용자의 모든 스토리에서 이미지가 있는 것들만 조회
        List<Story> storiesWithImages = storyRepository.findByUserAndImageIsNotNullOrderByCreatedAtDesc(user);

        System.out.println("🔍 이미지가 있는 스토리 개수: " + storiesWithImages.size());

        // 3. Story를 GalleryImageDTO로 변환
        List<GalleryImageDTO> galleryImages = storiesWithImages.stream()
                .map(this::convertToGalleryImageDTO)
                .collect(Collectors.toList());

        // 4. 갤러리 테이블에서 추가 색칠 이미지 정보 가져와서 병합
        List<Gallery> galleries = galleryRepository.findByUserOrderByCreatedAtDesc(user);
        mergeColoringImages(galleryImages, galleries);

        System.out.println("✅ 갤러리 이미지 변환 완료 - 최종 개수: " + galleryImages.size());
        return galleryImages;
    }

    /**
     * 특정 스토리의 갤러리 이미지 조회
     */
    public GalleryImageDTO getStoryGalleryImage(Long storyId, String username) {
        System.out.println("🔍 특정 스토리 갤러리 이미지 조회 - StoryId: " + storyId);

        // 1. 사용자 조회
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username));

        // 2. 스토리 조회 (권한 확인 포함)
        Story story = storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new RuntimeException("스토리를 찾을 수 없습니다: " + storyId));

        // 3. 기본 갤러리 정보 생성
        GalleryImageDTO galleryImage = convertToGalleryImageDTO(story);

        // 4. 갤러리 테이블에서 색칠 이미지 정보 추가
        Gallery gallery = galleryRepository.findByStoryIdAndUser(storyId, user);
        if (gallery != null) {
            galleryImage.setColoringImageUrl(gallery.getColoringImageUrl());
        }

        return galleryImage;
    }

    /**
     * 색칠한 이미지 업데이트
     */
    public GalleryImageDTO updateColoringImage(Long storyId, String coloringImageUrl, String username) {
        System.out.println("🔍 색칠한 이미지 업데이트 시작 - StoryId: " + storyId);

        // 1. 사용자 조회
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username));

        // 2. 스토리 조회 (권한 확인 포함)
        Story story = storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new RuntimeException("스토리를 찾을 수 없습니다: " + storyId));

        // 3. 갤러리 엔티티 조회 또는 생성
        Gallery gallery = galleryRepository.findByStoryIdAndUser(storyId, user);
        if (gallery == null) {
            gallery = new Gallery();
            gallery.setStoryId(storyId);
            gallery.setUser(user);
            gallery.setStoryTitle(story.getTitle());
            gallery.setColorImageUrl(story.getImage());
            gallery.setCreatedAt(LocalDateTime.now());
        }

        // 4. 색칠한 이미지 URL 업데이트
        gallery.setColoringImageUrl(coloringImageUrl);
        gallery.setUpdatedAt(LocalDateTime.now());

        // 5. 저장
        Gallery savedGallery = galleryRepository.save(gallery);

        System.out.println("✅ 색칠한 이미지 업데이트 완료");

        // 6. DTO로 변환하여 반환
        return convertToGalleryImageDTO(story, savedGallery);
    }

    /**
     * 갤러리 이미지 삭제
     */
    public boolean deleteGalleryImage(Long storyId, String username) {
        System.out.println("🔍 갤러리 이미지 삭제 시작 - StoryId: " + storyId);

        // 1. 사용자 조회
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username));

        // 2. 갤러리 엔티티 조회
        Gallery gallery = galleryRepository.findByStoryIdAndUser(storyId, user);

        if (gallery != null) {
            galleryRepository.delete(gallery);
            System.out.println("✅ 갤러리 이미지 삭제 완료");
            return true;
        } else {
            System.out.println("⚠️ 삭제할 갤러리 이미지 없음");
            return false;
        }
    }

    /**
     * 갤러리 통계 조회
     */
    public GalleryStatsDTO getGalleryStats(String username) {
        System.out.println("🔍 갤러리 통계 조회 시작");

        // 1. 사용자 조회
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username));

        // 2. 통계 계산
        long totalImages = storyRepository.countByUserAndImageIsNotNull(user);
        long coloringImages = galleryRepository.countByUserAndColoringImageUrlIsNotNull(user);
        long totalStories = storyRepository.countByUser(user);

        System.out.println("✅ 갤러리 통계 조회 완료 - 총 이미지: " + totalImages + ", 색칠 이미지: " + coloringImages);

        return GalleryStatsDTO.builder()
                .totalImages(totalImages)
                .coloringImages(coloringImages)
                .totalStories(totalStories)
                .completionRate(totalImages > 0 ? (double) coloringImages / totalImages * 100 : 0.0)
                .build();
    }

    /**
     * Story를 GalleryImageDTO로 변환
     */
    private GalleryImageDTO convertToGalleryImageDTO(Story story) {
        return GalleryImageDTO.builder()
                .storyId(story.getId())
                .storyTitle(story.getTitle())
                .colorImageUrl(story.getImage())
                .coloringImageUrl(null) // 기본값, 나중에 갤러리 테이블에서 추가
                .createdAt(story.getCreatedAt())
                .build();
    }

    /**
     * Story와 Gallery를 GalleryImageDTO로 변환
     */
    private GalleryImageDTO convertToGalleryImageDTO(Story story, Gallery gallery) {
        return GalleryImageDTO.builder()
                .storyId(story.getId())
                .storyTitle(story.getTitle())
                .colorImageUrl(story.getImage())
                .coloringImageUrl(gallery != null ? gallery.getColoringImageUrl() : null)
                .createdAt(story.getCreatedAt())
                .build();
    }

    /**
     * 갤러리 테이블의 색칠 이미지 정보를 병합
     */
    private void mergeColoringImages(List<GalleryImageDTO> galleryImages, List<Gallery> galleries) {
        // Gallery 리스트를 Map으로 변환 (storyId를 키로)
        var galleryMap = galleries.stream()
                .collect(Collectors.toMap(Gallery::getStoryId, gallery -> gallery));

        // GalleryImageDTO에 색칠 이미지 정보 병합
        galleryImages.forEach(dto -> {
            Gallery gallery = galleryMap.get(dto.getStoryId());
            if (gallery != null) {
                dto.setColoringImageUrl(gallery.getColoringImageUrl());
            }
        });
    }
}