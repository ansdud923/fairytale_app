package com.fairytale.fairytale.gallery;

import com.fairytale.fairytale.coloring.ColoringWork;
import com.fairytale.fairytale.coloring.ColoringWorkRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GalleryService {

    private final StoryRepository storyRepository;
    private final UsersRepository usersRepository;
    private final GalleryRepository galleryRepository;
    private final ColoringWorkRepository coloringWorkRepository; // 🎯 추가

    /**
     * 사용자의 모든 갤러리 이미지 조회 (동화 + 색칠 완성작)
     */
    public List<GalleryImageDTO> getUserGalleryImages(String username) {
        System.out.println("🔍 사용자 갤러리 이미지 조회 시작 - 사용자: " + username);

        // 1. 사용자 조회
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username));

        List<GalleryImageDTO> allGalleryImages = new ArrayList<>();

        // 2. 기존 동화 이미지들 조회
        List<Story> storiesWithImages = storyRepository.findByUserAndImageIsNotNullOrderByCreatedAtDesc(user);
        System.out.println("🔍 이미지가 있는 스토리 개수: " + storiesWithImages.size());

        // 3. Story를 GalleryImageDTO로 변환
        List<GalleryImageDTO> storyImages = storiesWithImages.stream()
                .map(this::convertToGalleryImageDTO)
                .collect(Collectors.toList());

        // 4. 갤러리 테이블에서 추가 색칠 이미지 정보 가져와서 병합
        List<Gallery> galleries = galleryRepository.findByUserOrderByCreatedAtDesc(user);
        mergeColoringImages(storyImages, galleries);

        allGalleryImages.addAll(storyImages);

        // 🎯 5. 색칠 완성작들 조회 및 추가
        List<ColoringWork> coloringWorks = coloringWorkRepository.findByUsernameOrderByCreatedAtDesc(username);
        System.out.println("🔍 색칠 완성작 개수: " + coloringWorks.size());

        List<GalleryImageDTO> coloringImages = coloringWorks.stream()
                .map(this::convertColoringWorkToGalleryImageDTO)
                .collect(Collectors.toList());

        allGalleryImages.addAll(coloringImages);

        // 6. 생성일시 기준으로 다시 정렬
        allGalleryImages.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        System.out.println("✅ 갤러리 이미지 변환 완료 - 최종 개수: " + allGalleryImages.size());
        System.out.println("   - 동화 이미지: " + storyImages.size() + "개");
        System.out.println("   - 색칠 완성작: " + coloringImages.size() + "개");

        return allGalleryImages;
    }

    /**
     * 🎯 색칠 완성작만 조회하는 메서드 (색칠 탭용)
     */
    public List<GalleryImageDTO> getUserColoringWorks(String username) {
        System.out.println("🔍 사용자 색칠 완성작 조회 시작 - 사용자: " + username);

        List<ColoringWork> coloringWorks = coloringWorkRepository.findByUsernameOrderByCreatedAtDesc(username);

        List<GalleryImageDTO> coloringImages = coloringWorks.stream()
                .map(this::convertColoringWorkToGalleryImageDTO)
                .collect(Collectors.toList());

        System.out.println("✅ 색칠 완성작 조회 완료 - 개수: " + coloringImages.size());
        return coloringImages;
    }

    /**
     * 🎯 동화 이미지만 조회하는 메서드 (동화 탭용)
     */
    public List<GalleryImageDTO> getUserStoryImages(String username) {
        System.out.println("🔍 사용자 동화 이미지 조회 시작 - 사용자: " + username);

        // 1. 사용자 조회
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username));

        // 2. 사용자의 모든 스토리에서 이미지가 있는 것들만 조회
        List<Story> storiesWithImages = storyRepository.findByUserAndImageIsNotNullOrderByCreatedAtDesc(user);

        // 3. Story를 GalleryImageDTO로 변환
        List<GalleryImageDTO> storyImages = storiesWithImages.stream()
                .map(this::convertToGalleryImageDTO)
                .collect(Collectors.toList());

        // 4. 갤러리 테이블에서 추가 색칠 이미지 정보 가져와서 병합
        List<Gallery> galleries = galleryRepository.findByUserOrderByCreatedAtDesc(user);
        mergeColoringImages(storyImages, galleries);

        System.out.println("✅ 동화 이미지 조회 완료 - 개수: " + storyImages.size());
        return storyImages;
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
     * 🎯 색칠 완성작 삭제
     */
    public boolean deleteColoringWork(Long coloringWorkId, String username) {
        System.out.println("🔍 색칠 완성작 삭제 시작 - ColoringWorkId: " + coloringWorkId);

        ColoringWork coloringWork = coloringWorkRepository.findById(coloringWorkId)
                .orElse(null);

        if (coloringWork != null && coloringWork.getUsername().equals(username)) {
            coloringWorkRepository.delete(coloringWork);
            System.out.println("✅ 색칠 완성작 삭제 완료");
            return true;
        } else {
            System.out.println("⚠️ 삭제할 색칠 완성작 없음 또는 권한 없음");
            return false;
        }
    }

    /**
     * 갤러리 통계 조회 (색칠 완성작 포함)
     */
    public GalleryStatsDTO getGalleryStats(String username) {
        System.out.println("🔍 갤러리 통계 조회 시작");

        // 1. 사용자 조회
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username));

        // 2. 통계 계산
        long totalStoryImages = storyRepository.countByUserAndImageIsNotNull(user);
        long coloringImages = galleryRepository.countByUserAndColoringImageUrlIsNotNull(user);
        long totalStories = storyRepository.countByUser(user);

        // 🎯 색칠 완성작 통계 추가
        long coloringWorks = coloringWorkRepository.countByUsername(username);
        long totalImages = totalStoryImages + coloringWorks;

        System.out.println("✅ 갤러리 통계 조회 완료");
        System.out.println("   - 동화 이미지: " + totalStoryImages + "개");
        System.out.println("   - 색칠 완성작: " + coloringWorks + "개");
        System.out.println("   - 총 이미지: " + totalImages + "개");

        return GalleryStatsDTO.builder()
                .totalImages(totalImages)
                .coloringImages(coloringImages + coloringWorks) // 기존 + 새로운 색칠 완성작
                .totalStories(totalStories)
                .completionRate(totalImages > 0 ? (double) (coloringImages + coloringWorks) / totalImages * 100 : 0.0)
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
     * 🎯 ColoringWork를 GalleryImageDTO로 변환 (수정됨)
     */
    private GalleryImageDTO convertColoringWorkToGalleryImageDTO(ColoringWork coloringWork) {
        return GalleryImageDTO.builder()
                .storyId(coloringWork.getId()) // ColoringWork의 ID를 storyId로 사용
                .storyTitle(coloringWork.getStoryTitle())
                .colorImageUrl(coloringWork.getOriginalImageUrl()) // 원본 컬러 이미지
                .coloringImageUrl(coloringWork.getCompletedImageUrl()) // 색칠 완성작
                .createdAt(coloringWork.getCreatedAt()) // @CreationTimestamp 필드
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