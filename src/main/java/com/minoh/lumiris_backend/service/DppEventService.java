package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppEventRequest;
import com.minoh.lumiris_backend.dto.out.DppEventResponse;
import com.minoh.lumiris_backend.entity.DppEvent;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.RepairRequestStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.DppEventMapper;
import com.minoh.lumiris_backend.repository.DppEventRepository;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.RepairRequestRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DppEventService {

    private static final Set<RepairRequestStatus> REPAIRER_EVENT_STATUSES =
            Set.of(RepairRequestStatus.ACCEPTED, RepairRequestStatus.IN_PROGRESS, RepairRequestStatus.COMPLETED);

    private final DppEventRepository dppEventRepository;
    private final DppFormRepository dppFormRepository;
    private final UserRepository userRepository;
    private final DppEventMapper dppEventMapper;
    private final GeocodingService geocodingService;
    private final RepairRequestRepository repairRequestRepository;

    @Transactional
    public DppEventResponse create(UUID dppFormId, DppEventRequest request, String userEmail) {
        User user = findUser(userEmail);
        DppForm form = dppFormRepository.findById(dppFormId)
                .orElseThrow(() -> new ResourceNotFoundException("DPP not found"));
        if (!isOwner(form, user) && !isServicingRepairer(form, user)) {
            throw new ResourceNotFoundException("DPP not found");
        }
        GeocodingService.Coordinates coordinates = geocodingService.geocode(locationQuery(request)).orElse(null);
        DppEvent event = dppEventRepository.save(dppEventMapper.toEntity(request, form, coordinates));
        return dppEventMapper.toResponse(event);
    }

    // A repairer may log history (e.g. "repair completed") once they've been accepted on a
    // request for this DPP — not before (PENDING/DRAFT/REFUSED aren't a green light to touch it).
    private boolean isServicingRepairer(DppForm form, User user) {
        return repairRequestRepository.existsByDppFormAndRepairerProfileUserAndStatusIn(
                form, user, REPAIRER_EVENT_STATUSES);
    }

    private boolean isOwner(DppForm form, User user) {
        return form.getUser().getId().equals(user.getId());
    }

    private String locationQuery(DppEventRequest request) {
        if (request.locationCity() != null && request.locationCountry() != null) {
            return request.locationCity() + ", " + request.locationCountry();
        }
        return request.locationCity() != null ? request.locationCity() : request.locationCountry();
    }

    @Transactional(readOnly = true)
    public List<DppEventResponse> findAllByDppFormId(UUID dppFormId, String userEmail) {
        DppForm form = findOwnedForm(dppFormId, userEmail);
        return dppEventRepository.findByDppFormIdOrderByOccurredAtDesc(form.getId()).stream()
                .map(dppEventMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DppEventResponse> findAllByPublicCode(String publicCode) {
        DppForm form = dppFormRepository.findByPublicCode(publicCode)
                .orElseThrow(() -> new ResourceNotFoundException("DPP not found"));
        return dppEventRepository.findByDppFormIdOrderByOccurredAtDesc(form.getId()).stream()
                .map(dppEventMapper::toResponse)
                .toList();
    }

    // Même règle d'accès que create() : le propriétaire, ou un retoucheur en cours d'intervention
    // (ou l'ayant terminée) sur ce DPP — sinon il pourrait écrire l'historique sans jamais le relire.
    private DppForm findOwnedForm(UUID dppFormId, String userEmail) {
        User user = findUser(userEmail);
        DppForm form = dppFormRepository.findById(dppFormId)
                .orElseThrow(() -> new ResourceNotFoundException("DPP not found"));
        if (!isOwner(form, user) && !isServicingRepairer(form, user)) {
            throw new ResourceNotFoundException("DPP not found");
        }
        return form;
    }

    private User findUser(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
