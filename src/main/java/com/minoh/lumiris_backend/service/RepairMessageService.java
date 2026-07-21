package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.RepairMessageRequest;
import com.minoh.lumiris_backend.dto.out.RepairMessageResponse;
import com.minoh.lumiris_backend.entity.RepairMessage;
import com.minoh.lumiris_backend.entity.RepairRequest;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.RepairMessageRepository;
import com.minoh.lumiris_backend.repository.RepairRequestRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepairMessageService {

    private final RepairMessageRepository messageRepo;
    private final RepairRequestRepository requestRepo;
    private final UserRepository userRepo;

    @Transactional(readOnly = true)
    public List<RepairMessageResponse> findByRequest(String userEmail, UUID requestId) {
        RepairRequest request = findParticipantRequest(userEmail, requestId);
        return messageRepo.findByRepairRequestOrderByCreatedAtAsc(request).stream()
                .map(m -> toResponse(m, request))
                .toList();
    }

    @Transactional
    public RepairMessageResponse send(String userEmail, UUID requestId, RepairMessageRequest body) {
        RepairRequest request = findParticipantRequest(userEmail, requestId);
        User sender = findUser(userEmail);

        RepairMessage message = new RepairMessage();
        message.setRepairRequest(request);
        message.setSender(sender);
        message.setBody(body.body());

        return toResponse(messageRepo.save(message), request);
    }

    // A user may read/write a request's thread only if they are the consumer who created it
    // or the repairer it was sent to.
    private RepairRequest findParticipantRequest(String userEmail, UUID requestId) {
        User user = findUser(userEmail);
        RepairRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable"));

        boolean isConsumer = request.getConsumerUser().getId().equals(user.getId());
        boolean isRepairer = request.getRepairerProfile().getUser() != null
                && request.getRepairerProfile().getUser().getId().equals(user.getId());
        if (!isConsumer && !isRepairer) {
            throw new ResourceNotFoundException("Demande introuvable");
        }
        return request;
    }

    private User findUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    private RepairMessageResponse toResponse(RepairMessage m, RepairRequest request) {
        boolean fromRepairer = request.getRepairerProfile().getUser() != null
                && request.getRepairerProfile().getUser().getId().equals(m.getSender().getId());
        return new RepairMessageResponse(m.getId(), m.getSender().getName(), fromRepairer, m.getBody(), m.getCreatedAt());
    }
}
