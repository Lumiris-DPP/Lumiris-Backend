package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.ArtisanProfilePhoto;
import com.minoh.lumiris_backend.repository.ArtisanProfilePhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Résout les photos de plusieurs ateliers en deux requêtes, quel que soit le nombre d'ateliers :
// une par photo puis une par URL signée, c'est le coût que paient les listes (annuaire public,
// file de validation admin) sur chacune de leurs lignes.
@Service
@RequiredArgsConstructor
public class ArtisanPhotoUrlResolver {

    public record PhotoUrl(UUID photoId, String url) {}

    private final ArtisanProfilePhotoRepository photoRepository;
    private final StorageService storageService;

    public Map<UUID, List<PhotoUrl>> byProfileId(Collection<ArtisanProfile> profiles) {
        if (profiles.isEmpty()) {
            return Map.of();
        }
        List<ArtisanProfilePhoto> photos = photoRepository.findByArtisanProfileInOrderByPosition(profiles);
        Map<UUID, String> urlsByFileId = storageService.getPresignedUrls(
                photos.stream().map(photo -> photo.getFile().getId()).toList());

        return photos.stream()
                .filter(photo -> urlsByFileId.containsKey(photo.getFile().getId()))
                .collect(Collectors.groupingBy(
                        photo -> photo.getArtisanProfile().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(
                                photo -> new PhotoUrl(photo.getId(), urlsByFileId.get(photo.getFile().getId())),
                                Collectors.toList())));
    }

    public List<PhotoUrl> of(ArtisanProfile profile) {
        return byProfileId(List.of(profile)).getOrDefault(profile.getId(), List.of());
    }
}
