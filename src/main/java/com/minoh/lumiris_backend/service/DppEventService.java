package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppEventRequest;
import com.minoh.lumiris_backend.dto.out.DppEventResponse;
import com.minoh.lumiris_backend.entity.DppEvent;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.DppEventMapper;
import com.minoh.lumiris_backend.repository.DppEventRepository;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DppEventService {

    private final DppEventRepository dppEventRepository;
    private final DppFormRepository dppFormRepository;
    private final UserRepository userRepository;
    private final DppEventMapper dppEventMapper;

    @Transactional
    public DppEventResponse create(UUID dppFormId, DppEventRequest request, String userEmail) {
        DppForm form = findOwnedForm(dppFormId, userEmail);
        DppEvent event = dppEventRepository.save(dppEventMapper.toEntity(request, form));
        return dppEventMapper.toResponse(event);
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

    private DppForm findOwnedForm(UUID dppFormId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        DppForm form = dppFormRepository.findById(dppFormId)
                .orElseThrow(() -> new ResourceNotFoundException("DPP not found"));
        if (!form.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("DPP not found");
        }
        return form;
    }
}
