package com.minoh.lumiris_backend.service.wardrobe;

import com.minoh.lumiris_backend.entity.DppCareInstruction;
import com.minoh.lumiris_backend.entity.DppForm;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Traduit les symboles d'entretien du passeport en une consigne lisible. Le rappel saisonnier ne
// dit RIEN qui ne vienne du passeport : sans symbole saisi par l'atelier, il n'y a pas de rappel —
// inventer un conseil générique reviendrait à envoyer une relance déguisée en service.
//
// Deux conseils au maximum : au-delà, l'acheteur ne lit plus, et l'essentiel se noie.
@Service
public class CareAdviceResolver {

    private static final int MAX_TIPS = 2;

    // Ordonné : ce qui interdit prime sur ce qui recommande, et le lavage prime sur le repassage —
    // c'est le geste qui abîme une pièce artisanale quand il est mal fait.
    private static final Map<String, String> ADVICE_BY_CODE = advice();

    private static Map<String, String> advice() {
        Map<String, String> byCode = new LinkedHashMap<>();
        byCode.put("no-wash", "pas de machine : nettoyage à sec ou à l'éponge");
        byCode.put("dry-clean", "un passage au pressing");
        byCode.put("wash-30", "un lavage à 30 °C");
        byCode.put("wash-40", "un lavage à 40 °C");
        byCode.put("wash-60", "un lavage à 60 °C");
        byCode.put("no-tumble", "un séchage à plat, jamais au sèche-linge");
        byCode.put("tumble-dry", "un séchage machine à basse température");
        byCode.put("no-iron", "aucun repassage");
        byCode.put("iron-low", "un repassage à basse température");
        byCode.put("iron-med", "un repassage à température moyenne");
        byCode.put("iron-high", "un repassage à haute température");
        // unmodifiableMap et non Map.copyOf : l'ordre d'insertion PORTE la priorité des conseils,
        // et Map.copyOf ne garantit aucun ordre d'itération.
        return Collections.unmodifiableMap(byCode);
    }

    // Empty quand le passeport ne porte aucun symbole exploitable : l'appelant n'envoie alors rien.
    public String adviceFor(DppForm dppForm) {
        if (dppForm == null) {
            return null;
        }
        Set<String> codes = dppForm.getCareInstructions().stream()
                .map(DppCareInstruction::getCareCode)
                .collect(Collectors.toSet());

        List<String> tips = ADVICE_BY_CODE.entrySet().stream()
                .filter(entry -> codes.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .limit(MAX_TIPS)
                .toList();

        return tips.isEmpty() ? null : String.join(", puis ", tips);
    }
}
