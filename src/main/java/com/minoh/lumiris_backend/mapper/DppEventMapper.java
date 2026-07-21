package com.minoh.lumiris_backend.mapper;

import com.minoh.lumiris_backend.dto.in.DppEventRequest;
import com.minoh.lumiris_backend.dto.out.DppEventResponse;
import com.minoh.lumiris_backend.entity.DppEvent;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.service.GeocodingService;
import org.springframework.stereotype.Component;

@Component
public class DppEventMapper {

    public DppEvent toEntity(DppEventRequest request, DppForm dppForm, GeocodingService.Coordinates coordinates) {
        Double latitude = coordinates != null ? coordinates.latitude() : null;
        Double longitude = coordinates != null ? coordinates.longitude() : null;
        return new DppEvent(
                dppForm,
                request.occurredAt(),
                request.description(),
                request.actorType(),
                request.locationCity(),
                request.locationCountry(),
                latitude,
                longitude
        );
    }

    public DppEventResponse toResponse(DppEvent event) {
        return new DppEventResponse(
                event.getId(),
                event.getOccurredAt(),
                event.getDescription(),
                event.getActorType(),
                event.getLocationCity(),
                event.getLocationCountry(),
                event.getLatitude(),
                event.getLongitude(),
                event.getCreatedAt()
        );
    }
}
