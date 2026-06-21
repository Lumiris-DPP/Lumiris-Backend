package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.DppFormMapper;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DppFormServiceTest {

    @Mock
    private DppFormRepository dppFormRepository;

    @Mock
    private DppFormMapper dppFormMapper;

    @InjectMocks
    private DppFormService dppFormService;

    private DppFormResponse sampleResponse(UUID id) {
        return new DppFormResponse(id, "Pull Merino", "sweater", "CHE-001", BigDecimal.valueOf(180), "EUR", "Draft", null, null);
    }

    @Test
    void create_shouldSaveAndReturnResponse() {
        DppFormRequest request = new DppFormRequest("Pull Merino", "sweater", "CHE-001", BigDecimal.valueOf(180), "EUR", null);
        DppForm entity = new DppForm();
        DppForm saved = new DppForm();
        UUID id = UUID.randomUUID();
        saved.setId(id);
        DppFormResponse expected = sampleResponse(id);

        when(dppFormMapper.toEntity(request)).thenReturn(entity);
        when(dppFormRepository.save(entity)).thenReturn(saved);
        when(dppFormMapper.toResponse(saved)).thenReturn(expected);

        DppFormResponse result = dppFormService.create(request);

        assertThat(result.id()).isEqualTo(id);
        verify(dppFormMapper).toEntity(request);
        verify(dppFormRepository).save(entity);
    }

    @Test
    void findById_shouldReturnResponse_whenExists() {
        UUID id = UUID.randomUUID();
        DppForm entity = new DppForm();
        entity.setId(id);
        when(dppFormRepository.findById(id)).thenReturn(Optional.of(entity));
        when(dppFormMapper.toResponse(entity)).thenReturn(sampleResponse(id));

        DppFormResponse result = dppFormService.findById(id);

        assertThat(result.id()).isEqualTo(id);
    }

    @Test
    void findById_shouldThrow_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(dppFormRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dppFormService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void patch_shouldUpdateAndReturnResponse() {
        UUID id = UUID.randomUUID();
        DppFormRequest patch = new DppFormRequest(null, null, "CHE-002", null, null, null);
        DppForm entity = new DppForm();
        entity.setId(id);
        when(dppFormRepository.findById(id)).thenReturn(Optional.of(entity));
        when(dppFormRepository.save(entity)).thenReturn(entity);
        when(dppFormMapper.toResponse(entity)).thenReturn(sampleResponse(id));

        DppFormResponse result = dppFormService.patch(id, patch);

        assertThat(result).isNotNull();
        verify(dppFormMapper).applyPatch(patch, entity);
        verify(dppFormRepository).save(entity);
    }

    @Test
    void findAll_shouldReturnMappedResponses() {
        DppForm entity = new DppForm();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        when(dppFormRepository.findAll()).thenReturn(List.of(entity));
        when(dppFormMapper.toResponse(entity)).thenReturn(sampleResponse(id));

        List<DppFormResponse> result = dppFormService.findAll();

        assertThat(result).hasSize(1);
        verify(dppFormRepository).findAll();
    }
}
