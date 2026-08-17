package com.minoh.lumiris_backend.service.shipping;

import com.minoh.lumiris_backend.dto.in.ShipFromAddressRequest;
import com.minoh.lumiris_backend.dto.out.ShipFromAddressResponse;
import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

// Adresse d'enlèvement de l'atelier. Volontairement hors de la vitrine (ArtisanVitrineService),
// qui publie : cette adresse est opérationnelle et ne sort jamais sur un chemin public.
@Service
@RequiredArgsConstructor
public class ShipFromAddressService {

    private static final String DEFAULT_COUNTRY = "FR";

    private final ArtisanProfileRepository artisanProfileRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ShipFromAddressResponse get(String userEmail) {
        return ShipFromAddressResponse.from(requireProfile(userEmail));
    }

    @Transactional
    public ShipFromAddressResponse update(String userEmail, ShipFromAddressRequest request) {
        ArtisanProfile profile = requireProfile(userEmail);
        profile.setShipFromLine1(request.line1());
        profile.setShipFromLine2(request.line2());
        profile.setShipFromPostalCode(request.postalCode());
        profile.setShipFromCity(request.city());
        profile.setShipFromCountry(request.country() == null || request.country().isBlank()
                ? DEFAULT_COUNTRY : request.country().toUpperCase(Locale.ROOT));
        profile.setShipFromPhone(request.phone());
        return ShipFromAddressResponse.from(artisanProfileRepository.save(profile));
    }

    private ArtisanProfile requireProfile(String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        return artisanProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Profil artisan introuvable"));
    }
}
