package com.minoh.lumiris_backend.marketplace.favorite.service;

import com.minoh.lumiris_backend.entity.MarketplaceFavorite;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.marketplace.catalog.repository.MarketplaceProductRepository;
import com.minoh.lumiris_backend.marketplace.catalog.service.MarketplaceItemAssembler;
import com.minoh.lumiris_backend.marketplace.favorite.repository.MarketplaceFavoriteRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Gère les pièces favorites de l'utilisateur connecté.
@Service
@RequiredArgsConstructor
public class MarketplaceFavoriteService {

    private final MarketplaceFavoriteRepository favoriteRepository;
    private final MarketplaceProductRepository productRepository;
    private final UserRepository userRepository;
    private final MarketplaceItemAssembler assembler;

    // Enregistre la pièce favorite si elle n'est pas déjà suivie.
    @Transactional
    public void add(String email, UUID productId) {
        User user = userRepository.getByEmail(email);
        if (favoriteRepository.findByUser_IdAndProduct_Id(user.getId(), productId).isPresent()) {
            return;
        }
        MarketplaceProduct product = productRepository.findById(productId)
                .filter(p -> p.getStatus() == MarketplaceProductStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));

        MarketplaceFavorite favorite = new MarketplaceFavorite();
        favorite.setUser(user);
        favorite.setProduct(product);
        favorite.setLastPriceCents(product.getPriceCents());
        favoriteRepository.save(favorite);
    }

    // Supprime le favori appartenant à l'utilisateur connecté.
    @Transactional
    public void remove(String email, UUID productId) {
        User user = userRepository.getByEmail(email);
        favoriteRepository.findByUser_IdAndProduct_Id(user.getId(), productId)
                .ifPresent(favoriteRepository::delete);
    }

    // Présente les pièces favorites de l'utilisateur connecté.
    @Transactional(readOnly = true)
    public List<MarketplaceItemResponse> list(String email) {
        User user = userRepository.getByEmail(email);
        return assembler.toResponses(assembler.fetch(favoriteRepository.findScoredByUserId(user.getId())));
    }
}
