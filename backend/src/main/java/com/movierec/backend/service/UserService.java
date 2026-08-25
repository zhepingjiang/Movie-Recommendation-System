package com.movierec.backend.service;

import com.movierec.backend.dto.UpdateGenrePreferencesRequest;
import com.movierec.backend.dto.UpdateProfileRequest;
import com.movierec.backend.dto.UserProfileDto;
import com.movierec.backend.entity.Genre;
import com.movierec.backend.entity.User;
import com.movierec.backend.entity.UserGenre;
import com.movierec.backend.entity.UserGenreId;
import com.movierec.backend.repository.GenreRepository;
import com.movierec.backend.repository.UserGenreRepository;
import com.movierec.backend.repository.UserRepository;
import com.movierec.backend.storage.StorageService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Profile read/update for the authenticated user, backing
 * {@link com.movierec.backend.controller.UserController}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final StorageService storageService;
    private final GenreRepository genreRepository;
    private final UserGenreRepository userGenreRepository;
    private final UserProfileMapper userProfileMapper;

    @Value("${minio.public-url}")
    private String publicUrl;

    @Value("${minio.bucket}")
    private String bucket;

    public UserProfileDto getProfile(Long userId) {
        return toProfileDto(getUserOrThrow(userId));
    }

    @Transactional
    public UserProfileDto updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUserOrThrow(userId);
        user.setDisplayName(request.displayName());
        user.setUpdatedAt(Instant.now());
        return toProfileDto(user);
    }

    @Transactional
    public UserProfileDto updateAvatar(Long userId, MultipartFile file) {
        User user = getUserOrThrow(userId);

        String previousKey = keyFromUrl(user.getAvatarUrl());
        if (previousKey != null) {
            storageService.delete(previousKey);
        }

        // No "avatars/" prefix here: the bucket itself (configured via minio.bucket) is already
        // avatar-scoped, so prefixing the key too would just duplicate that segment in the URL.
        String key = userId + "/" + UUID.randomUUID() + extensionFor(file.getContentType());
        try {
            String url = storageService.upload(key, file.getInputStream(), file.getSize(), file.getContentType());
            user.setAvatarUrl(url);
            user.setUpdatedAt(Instant.now());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded avatar", e);
        }

        return toProfileDto(user);
    }

    /**
     * Replaces the user's genre picks with the given set. Diffs against the existing rows rather
     * than a blind delete-and-recreate: a genre that stays selected keeps its original
     * {@code weight}/{@code createdAt} instead of having them reset on every profile edit.
     */
    @Transactional
    public UserProfileDto updateGenrePreferences(Long userId, UpdateGenrePreferencesRequest request) {
        User user = getUserOrThrow(userId);
        Set<Genre> desiredGenres = request.genres() == null
                ? Set.of()
                : new HashSet<>(genreRepository.findByNameInAndIsActiveTrue(request.genres()));
        Set<Long> desiredGenreIds = desiredGenres.stream().map(Genre::getId).collect(Collectors.toSet());

        List<UserGenre> existing = userGenreRepository.findByIdUserId(userId);
        List<UserGenre> toRemove =
                existing.stream().filter(ug -> !desiredGenreIds.contains(ug.getId().getGenreId())).toList();
        userGenreRepository.deleteAll(toRemove);

        Set<Long> existingGenreIds =
                existing.stream().map(ug -> ug.getId().getGenreId()).collect(Collectors.toSet());
        Instant now = Instant.now();
        List<UserGenre> toAdd = desiredGenres.stream()
                .filter(genre -> !existingGenreIds.contains(genre.getId()))
                .map(genre -> UserGenre.builder()
                        .id(new UserGenreId(userId, genre.getId()))
                        .user(user)
                        .genre(genre)
                        .weight(BigDecimal.ONE)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .toList();
        userGenreRepository.saveAll(toAdd);

        user.setUpdatedAt(now);
        return toProfileDto(user);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user " + userId + " no longer exists"));
    }

    /** Recovers the object key from a URL previously returned by {@link StorageService#upload}. */
    private String keyFromUrl(String url) {
        if (url == null) {
            return null;
        }
        String prefix = (publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl)
                + "/" + bucket + "/";
        return url.startsWith(prefix) ? url.substring(prefix.length()) : null;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    // FIX: was building UserProfileDto inline here (duplicating AuthService's copy of the same
    // logic, which is how the preferredGenres bug happened). Now delegates to the one shared
    // mapper both services use.
    private UserProfileDto toProfileDto(User user) {
        return userProfileMapper.toDto(user);
    }
}
