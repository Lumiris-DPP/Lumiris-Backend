package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.DppStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Contrôle d'appartenance partagé par les services DPP. Un passeport d'un autre artisan répond
 * 404, jamais 403 : son existence même ne doit pas fuiter (pas d'énumération d'identifiants).
 */
@Component
@RequiredArgsConstructor
public class DppOwnershipGuard {

    private final UserRepository userRepository;
    private final DppFormRepository dppFormRepository;

    public User currentUser(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public DppForm loadForm(UUID id) {
        return dppFormRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DPP not found"));
    }

    public DppForm loadOwned(UUID id, String userEmail) {
        User user = currentUser(userEmail);
        DppForm form = loadForm(id);
        if (!isOwner(form, user)) {
            throw new ResourceNotFoundException("DPP not found");
        }
        return form;
    }

    public DppForm loadOwnedDraft(UUID id, String userEmail) {
        DppForm form = loadOwned(id, userEmail);
        if (form.getStatus() != DppStatus.DRAFT) {
            throw new ConflictException("Seul un DPP en brouillon peut être modifié, supprimé ou publié.");
        }
        return form;
    }

    public static boolean isOwner(DppForm form, User user) {
        return form.getUser().getId().equals(user.getId());
    }
}
