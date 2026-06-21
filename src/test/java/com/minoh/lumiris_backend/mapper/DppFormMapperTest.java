package com.minoh.lumiris_backend.mapper;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.entity.DppForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DppFormMapperTest {

    private DppFormMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DppFormMapper();
    }

    @Test
    void toEntity_shouldMapAllProvidedFields() {
        DppFormRequest request = new DppFormRequest("Pull Merino", "sweater", "CHE-001", BigDecimal.valueOf(180), "EUR", null);

        DppForm result = mapper.toEntity(request);

        assertThat(result.getProductName()).isEqualTo("Pull Merino");
        assertThat(result.getProductType()).isEqualTo("sweater");
        assertThat(result.getInternalReference()).isEqualTo("CHE-001");
        assertThat(result.getRetailPrice()).isEqualByComparingTo(BigDecimal.valueOf(180));
        assertThat(result.getId()).isNull();
    }

    @Test
    void applyPatch_shouldOnlyUpdateNonNullFields() {
        DppForm form = new DppForm();
        form.setProductName("Ancien nom");
        form.setProductType("shirt");

        DppFormRequest patch = new DppFormRequest(null, "sweater", "CHE-002", null, null, null);
        mapper.applyPatch(patch, form);

        assertThat(form.getProductName()).isEqualTo("Ancien nom"); // non modifié
        assertThat(form.getProductType()).isEqualTo("sweater");
        assertThat(form.getInternalReference()).isEqualTo("CHE-002");
    }

    @Test
    void toResponse_shouldMapAllFields() {
        DppForm entity = new DppForm();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setProductName("Pull Merino");
        entity.setProductType("sweater");
        entity.setInternalReference("CHE-001");
        entity.setRetailPrice(BigDecimal.valueOf(180));

        DppFormResponse result = mapper.toResponse(entity);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.productName()).isEqualTo("Pull Merino");
        assertThat(result.productType()).isEqualTo("sweater");
        assertThat(result.internalReference()).isEqualTo("CHE-001");
        assertThat(result.retailPrice()).isEqualByComparingTo(BigDecimal.valueOf(180));
        assertThat(result.createdAt()).isNull();
    }
}
