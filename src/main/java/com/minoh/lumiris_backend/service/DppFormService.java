package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.DppFormMapper;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DppFormService {

    private final DppFormRepository dppFormRepository;
    private final UserRepository userRepository;
    private final DppFormMapper dppFormMapper;

    @Transactional
    public DppFormResponse create(DppFormRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DppFormRequest r = request != null ? request : emptyRequest();
        DppForm form = dppFormMapper.toEntity(r, user);
        DppForm saved = dppFormRepository.save(form);
        return dppFormMapper.toResponse(saved);
    }

    private static DppFormRequest emptyRequest() {
        return new DppFormRequest(
                null, null, null, null, null, null, null,
                null, null, null,
                null, null, null, null, null,
                null, null, null, null
        );
    }
}
