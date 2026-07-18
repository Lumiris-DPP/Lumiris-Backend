package com.minoh.lumiris_backend.mapper;

import com.minoh.lumiris_backend.dto.in.DppEventRequest;
import com.minoh.lumiris_backend.dto.out.DppEventResponse;
import com.minoh.lumiris_backend.entity.DppEvent;
import com.minoh.lumiris_backend.entity.DppForm;
import org.springframework.stereotype.Component;

@Component
public class DppEventMapper {

    public DppEvent toEntity(DppEventRequest request, DppForm dppForm) {
        return new DppEvent(dppForm, request.occurredAt(), request.description(), request.actorType());
    }

    public DppEventResponse toResponse(DppEvent event) {
        return new DppEventResponse(
                event.getId(),
                event.getOccurredAt(),
                event.getDescription(),
                event.getActorType(),
                event.getCreatedAt()
        );
    }
}
