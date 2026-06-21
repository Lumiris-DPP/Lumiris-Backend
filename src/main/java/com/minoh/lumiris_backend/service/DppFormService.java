package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.DppFormMapper;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DppFormService {

    private final DppFormRepository dppFormRepository;
    private final DppFormMapper dppFormMapper;

    public DppFormResponse create(DppFormRequest request) {
        return dppFormMapper.toResponse(
                dppFormRepository.save(dppFormMapper.toEntity(request))
        );
    }

    public DppFormResponse findById(UUID id) {
        return dppFormRepository.findById(id)
                .map(dppFormMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("DPP introuvable : " + id));
    }

    public DppFormResponse patch(UUID id, DppFormRequest request) {
        var form = dppFormRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DPP introuvable : " + id));
        dppFormMapper.applyPatch(request, form);
        return dppFormMapper.toResponse(dppFormRepository.save(form));
    }

    public List<DppFormResponse> findAll() {
        return dppFormRepository.findAll()
                .stream()
                .map(dppFormMapper::toResponse)
                .toList();
    }
}
