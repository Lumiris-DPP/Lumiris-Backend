package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.out.IrisMethodologyResponse;
import com.minoh.lumiris_backend.dto.out.IrisMethodologyResponse.GradeScale;
import com.minoh.lumiris_backend.dto.out.IrisMethodologyResponse.Section;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Source de vérité du texte explicatif du score Iris V2.
 *
 * <p>Les poids affichés ici reflètent l'algorithme de {@code @lumiris/core}
 * (scoring/constants.ts, grade.ts, caps.ts) : changer un poids ou un palier côté
 * algorithme impose de le répercuter ici.
 */
@Service
public class IrisMethodologyService {

    private static final IrisMethodologyResponse METHODOLOGY = build();

    public IrisMethodologyResponse getMethodology() {
        return METHODOLOGY;
    }

    private static IrisMethodologyResponse build() {
        return new IrisMethodologyResponse(
                "v2",
                "Comment est calculé le score Iris",
                "Votre passeport est noté sur 100 à partir des seules informations que vous renseignez, "
                        + "puis traduit en une lettre de A à E.",
                List.of(
                        new Section("transparency", "Transparence", 40,
                                "À quel point la matière et la fabrication sont traçables et prouvées.",
                                List.of(
                                        "Renseigner fibre, fournisseur et pays pour chaque matière",
                                        "Téléverser les factures fournisseurs",
                                        "Ajouter une photo par étape de fabrication"
                                )),
                        new Section("craftsmanship", "Savoir-faire", 25,
                                "La part du vêtement que vous fabriquez vous-même.",
                                List.of(
                                        "Déclarer les étapes faites dans votre atelier",
                                        "Ajouter vos labels (EPV, Origine France Garantie)",
                                        "Offrir 24 mois de garantie ou plus"
                                )),
                        new Section("impact", "Impact", 25,
                                "L'empreinte environnementale du vêtement : carbone, eau, transport.",
                                List.of(
                                        "Privilégier les fibres à faible impact et la matière recyclée",
                                        "Rapprocher les étapes de fabrication",
                                        "Déclarer votre analyse de cycle de vie si vous en avez une"
                                )),
                        new Section("repairability", "Réparabilité", 10,
                                "La facilité à faire réparer le vêtement plutôt qu'à le remplacer.",
                                List.of(
                                        "Référencer des réparateurs partenaires",
                                        "Allonger la durée de garantie"
                                )),
                        new Section("regulatory-cap", "Plafond réglementaire", null,
                                "Un champ ESPR ou AGEC manquant bloque la lettre à D, quel que soit le total.",
                                List.of("Compléter les champs signalés comme manquants sur le passeport"))
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
