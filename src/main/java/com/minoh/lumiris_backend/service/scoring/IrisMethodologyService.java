package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.out.IrisMethodologyResponse;
import com.minoh.lumiris_backend.dto.out.IrisMethodologyResponse.GradeScale;
import com.minoh.lumiris_backend.dto.out.IrisMethodologyResponse.Section;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Source de vérité du texte explicatif du score Iris.
 *
 * <p>Ce texte doit décrire ce que les quatre {@code *ScoreService} calculent réellement : toute
 * évolution d'un barème doit être répercutée ici, sinon la promesse affichée à l'artisan ne
 * correspond plus à la note qu'il obtient.
 */
@Service
public class IrisMethodologyService {

    private static final IrisMethodologyResponse METHODOLOGY = build();

    public IrisMethodologyResponse getMethodology() {
        return METHODOLOGY;
    }

    private static IrisMethodologyResponse build() {
        return new IrisMethodologyResponse(
                "v3",
                "Comment est calculé le score Iris",
                "Votre passeport est noté sur 100 à partir des seules informations que vous renseignez, "
                        + "puis traduit en une lettre de A à E.",
                List.of(
                        new Section("transparency", "Transparence", 40,
                                "À quel point la composition est complète, cohérente et justifiée.",
                                List.of(
                                        "Renseigner fibre, pourcentage et pays sur chaque matière",
                                        "Faire totaliser 100 % à la composition",
                                        "Téléverser les certificats d'origine et de transaction",
                                        "Joindre le justificatif REACH, pas seulement la déclaration"
                                )),
                        new Section("craftsmanship", "Savoir-faire", 25,
                                "Ce que votre atelier apporte, et ce qui l'atteste.",
                                List.of(
                                        "Faire reconnaître vos labels (EPV, Origine France Garantie, GOTS, OEKO-TEX)",
                                        "Offrir 24 mois de garantie ou plus",
                                        "Joindre le carnet de création"
                                )),
                        new Section("impact", "Impact", 25,
                                "L'empreinte du vêtement : carbone, eau, matière recyclée, transport.",
                                List.of(
                                        "Renseigner le poids : sans lui, carbone et eau ne sont pas calculés",
                                        "Privilégier les fibres à faible impact et la matière recyclée",
                                        "Rapprocher l'origine des matières du lieu de confection"
                                )),
                        new Section("repairability", "Réparabilité", 10,
                                "La possibilité concrète de faire réparer le vêtement.",
                                List.of(
                                        "Joindre un manuel de réparation en plus de la déclaration",
                                        "Allonger la garantie au-delà de 12 mois",
                                        "Détailler les consignes de fin de vie"
                                ))
                ),
                List.of(
                        new GradeScale("A", 80, "Exemplaire — tout est tracé"),
                        new GradeScale("B", 65, "Solide — quelques justificatifs manquants"),
                        new GradeScale("C", 50, "Correct — traçabilité à approfondir"),
                        new GradeScale("D", 35, "Insuffisant — informations trop partielles"),
                        new GradeScale("E", 0, "Non conforme — passeport inexploitable")
                ),
                "Coefficients environnementaux : ADEME 2024, Higg MSI v3.5, Water Footprint Network."
        );
    }
}
