package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.out.IrisMethodologyResponse;
import com.minoh.lumiris_backend.dto.out.IrisMethodologyResponse.Criterion;
import com.minoh.lumiris_backend.dto.out.IrisMethodologyResponse.GradeScale;
import com.minoh.lumiris_backend.dto.out.IrisMethodologyResponse.Section;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Source de vérité du texte explicatif du score Iris V2.
 *
 * <p>Les pondérations et barèmes décrits ici reflètent l'algorithme de
 * {@code @lumiris/core} (scoring/constants.ts, grade.ts, caps.ts) : toute modification
 * d'un poids ou d'un palier côté algorithme doit être répercutée dans ce texte, sinon
 * la promesse affichée à l'artisan ne correspond plus au score qu'il obtient.
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
                """
                Le score Iris note un passeport produit sur 100, à partir des seules informations \
                que vous avez renseignées et justifiées. Il combine quatre axes pondérés, puis se \
                traduit en une lettre de A à E. Aucun élément subjectif n'entre dans le calcul : \
                à données identiques, deux passeports obtiennent exactement le même score.""",
                List.of(
                        transparency(),
                        craftsmanship(),
                        impact(),
                        repairability(),
                        regulatoryCap()
                ),
                grades(),
                """
                Les coefficients environnementaux proviennent de la Base Empreinte ADEME 2024, \
                du Higg MSI v3.5 et du Water Footprint Network. Une analyse de cycle de vie \
                externe que vous déclarez prime toujours sur notre estimation par défaut."""
        );
    }

    private static Section transparency() {
        return new Section(
                "transparency",
                "Transparence",
                40,
                """
                L'axe le plus lourd : il mesure à quel point la matière et la fabrication sont \
                traçables et justifiées par des pièces.""",
                List.of(
                        new Criterion(
                                "Complétude de la composition",
                                """
                                Chaque ligne de composition doit porter sa fibre, son fournisseur et son \
                                pays d'origine. Si la somme des pourcentages ne fait pas 100 %, ce bloc \
                                de points est divisé par deux.""",
                                40
                        ),
                        new Criterion(
                                "Factures fournisseurs",
                                "Part des matières reliées à une facture fournisseur téléversée.",
                                25
                        ),
                        new Criterion(
                                "Photos des étapes",
                                "Part des étapes de fabrication documentées par au moins une photo.",
                                20
                        ),
                        new Criterion(
                                "Certifications matière",
                                """
                                Moyenne des certifications valides couvrant vos matières. Une certification \
                                expirée perd progressivement son poids au lieu de disparaître d'un coup.""",
                                15
                        )
                )
        );
    }

    private static Section craftsmanship() {
        return new Section(
                "craftsmanship",
                "Savoir-faire",
                25,
                "Ce que vous fabriquez vous-même, et ce qui l'atteste officiellement.",
                List.of(
                        new Criterion(
                                "Part artisanale",
                                """
                                Proportion des étapes réalisées directement par vous ou votre atelier, \
                                par rapport au total des étapes déclarées.""",
                                50
                        ),
                        new Criterion(
                                "Labels et certifications maison",
                                """
                                Label EPV (Entreprise du Patrimoine Vivant) 20 points, label \
                                Origine France Garantie 10 points, certifications propres à l'atelier \
                                5 points chacune — plafonné à 30 points au total.""",
                                30
                        ),
                        new Criterion(
                                "Garantie",
                                "24 mois et plus : 20 points · 12 à 23 mois : 15 · 6 à 11 mois : 10 · moins de 6 mois : 0.",
                                20
                        )
                )
        );
    }

    private static Section impact() {
        return new Section(
                "impact",
                "Impact",
                25,
                """
                L'empreinte environnementale du vêtement, estimée à partir de sa composition \
                réelle et de ses trajets.""",
                List.of(
                        new Criterion(
                                "Empreinte carbone",
                                """
                                Calculée fibre par fibre à partir des coefficients ADEME et Higg MSI, \
                                puis comparée à un plafond de référence de 12 kg CO₂e.""",
                                25
                        ),
                        new Criterion(
                                "Consommation d'eau",
                                """
                                Même logique, sur la base du Water Footprint Network, avec un plafond \
                                de référence de 3 000 litres.""",
                                25
                        ),
                        new Criterion(
                                "Matière recyclée",
                                "Part de matière recyclée dans la composition, l'objectif plein étant fixé à 50 %.",
                                25
                        ),
                        new Criterion(
                                "Transport",
                                "Distance cumulée des étapes de fabrication, comparée à un plafond de 2 000 km.",
                                25
                        )
                )
        );
    }

    private static Section repairability() {
        return new Section(
                "repairability",
                "Réparabilité",
                10,
                "La capacité concrète à faire réparer le vêtement plutôt qu'à le remplacer.",
                List.of(
                        new Criterion(
                                "Réseau de réparateurs",
                                "Présence et proximité de retoucheurs partenaires capables d'intervenir sur ce vêtement.",
                                60
                        ),
                        new Criterion(
                                "Fibres réparables",
                                """
                                Certaines fibres se reprisent et se retouchent bien mieux que d'autres ; \
                                la note est pondérée par leur part dans la composition.""",
                                30
                        ),
                        new Criterion(
                                "Garantie",
                                "La durée de garantie engage l'atelier sur la réparation dans le temps.",
                                10
                        )
                )
        );
    }

    private static Section regulatoryCap() {
        return new Section(
                "regulatory-cap",
                "Plafond réglementaire",
                null,
                """
                Indépendamment du total obtenu, un passeport auquel il manque un champ exigé par \
                le règlement ESPR ou la loi AGEC ne peut pas dépasser la lettre D. Le total brut \
                reste affiché tel quel : seule la lettre est plafonnée, et la liste des champs \
                manquants vous est indiquée pour que vous puissiez la lever.""",
                List.of(
                        new Criterion(
                                "Champs ESPR",
                                """
                                Type et référence du produit, photo principale, fibre / pourcentage / \
                                pays d'origine de chaque matière, au moins une étape de fabrication, \
                                durée et conditions de garantie.""",
                                null
                        ),
                        new Criterion(
                                "Champs AGEC",
                                """
                                Fournisseur et pays d'origine de chaque matière, URL de vérification GS1, \
                                et consignes d'entretien pour tout passeport publié.""",
                                null
                        )
                )
        );
    }

    private static List<GradeScale> grades() {
        return List.of(
                new GradeScale("A", 80, "Exemplaire — traçabilité complète et impact maîtrisé"),
                new GradeScale("B", 65, "Solide — quelques justificatifs ou données encore manquants"),
                new GradeScale("C", 50, "Correct — la traçabilité mérite d'être approfondie"),
                new GradeScale("D", 35, "Insuffisant — informations trop partielles pour rassurer"),
                new GradeScale("E", 0, "Non conforme — le passeport n'est pas exploitable en l'état")
        );
    }
}
