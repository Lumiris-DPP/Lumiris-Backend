package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.entity.MarketplaceFavorite;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.MarketplaceFavoriteRepository;
import com.minoh.lumiris_backend.repository.MarketplaceProductRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// Liste d'envies de l'acheteur. Sur des pièces uniques à prix artisanal, la décision n'est presque
// jamais prise au premier passage : le favori est le seul moyen de revenir à une pièce vue la
// veille, et le destinataire des alertes de rupture et de baisse de prix.
@Service
@RequiredArgsConstructor
public class MarketplaceFavoriteService {

    private final MarketplaceFavoriteRepository favoriteRepository;
    private final MarketplaceProductRepository productRepository;
    private final UserRepository userRepository;
    private final MarketplaceItemAssembler assembler;

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

    @Transactional
    public void remove(String email, UUID productId) {
        User user = userRepository.getByEmail(email);
        favoriteRepository.findByUser_IdAndProduct_Id(user.getId(), productId)
                .ifPresent(favoriteRepository::delete);
    }

    // Volontairement SANS le filtre d'achetabilité appliqué au catalogue : l'acheteur a explicitement
    // demandé ces pièces, et faire disparaître une ligne de sa propre liste est pire que l'afficher
    // indisponible. La réponse porte déjà statut et stock, le front sait quoi griser.
    @Transactional(readOnly = true)
    public List<MarketplaceItemResponse> list(String email) {
        User user = userRepository.getByEmail(email);
        return assembler.toResponses(assembler.fetch(favoriteRepository.findScoredByUserId(user.getId())));
    }
}
