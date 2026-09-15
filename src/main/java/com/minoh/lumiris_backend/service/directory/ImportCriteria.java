package com.minoh.lumiris_backend.service.directory;

import java.util.List;

/**
 * Périmètre d'un import annuaire. {@code nafCodes} vide → la source utilise ses codes par défaut
 * (cordonnerie / retouche textile). {@code maxPages} borne le volume d'un déclenchement manuel.
 */
public record ImportCriteria(
        List<String> departments,
        List<String> nafCodes,
        int maxPages
) {
    public ImportCriteria {
        departments = departments == null ? List.of() : List.copyOf(departments);
        nafCodes = nafCodes == null ? List.of() : List.copyOf(nafCodes);
        if (maxPages <= 0) {
            maxPages = 4;
        }
    }
}
