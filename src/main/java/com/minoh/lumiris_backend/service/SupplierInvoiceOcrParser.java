package com.minoh.lumiris_backend.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort structured extraction from a supplier invoice's raw OCR text (produced by
 * {@link OcrService}), via pattern matching — no external API, consistent with OcrService's own
 * "no cost, no key" design. This is inherently approximate: real invoices vary wildly in layout,
 * so any given field can come back {@code null} (or the line item list empty) rather than a wrong
 * guess. Treat the result as a starting point for the artisan to correct, not a certified read.
 */
@Component
public class SupplierInvoiceOcrParser {

    private static final Pattern DATE_PATTERN =
            Pattern.compile("\\b(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})\\b");

    private static final Pattern TOTAL_HT_PATTERN = Pattern.compile(
            "total\\s*h\\.?\\s*t\\.?\\s*[:\\-]?\\s*([\\d][\\d\\s]*[.,]\\d{2})", Pattern.CASE_INSENSITIVE);

    private static final Pattern LINE_ITEM_PATTERN = Pattern.compile(
            "^(.{2,80}?)\\s+(\\d+(?:[.,]\\d+)?)\\s*"
                    + "(kg|g|m|ml|cm|u|unites?|unit[eé]s?|pi[eè]ces?|pcs?)\\.?$",
            Pattern.CASE_INSENSITIVE);

    // Mots-clés (déjà normalisés : minuscules, sans accents) -> valeur de l'enum front Fiber.
    // "polyester" seul n'existe pas côté front (uniquement recycled-polyester) : mapping du mieux
    // qu'on peut, à corriger manuellement si le tissu n'est pas recyclé.
    private static final Map<String, String> FIBER_KEYWORDS = new LinkedHashMap<>();
    static {
        FIBER_KEYWORDS.put("laine", "wool");
        FIBER_KEYWORDS.put("lin", "linen");
        FIBER_KEYWORDS.put("coton", "cotton");
        FIBER_KEYWORDS.put("soie", "silk");
        FIBER_KEYWORDS.put("chanvre", "hemp");
        FIBER_KEYWORDS.put("cuir", "leather");
        FIBER_KEYWORDS.put("cachemire", "cashmere");
        FIBER_KEYWORDS.put("polyester", "recycled-polyester");
    }

    public record ParsedInvoice(
            String supplierName,
            LocalDate invoiceDate,
            BigDecimal totalHt,
            String currency,
            List<ParsedLineItem> lineItems
    ) {}

    public record ParsedLineItem(String fiber, String label, BigDecimal qty, String unit) {}

    public ParsedInvoice parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new ParsedInvoice(null, null, null, null, List.of());
        }

        List<String> lines = rawText.lines().map(String::strip).filter(l -> !l.isBlank()).toList();

        String supplierName = extractSupplierName(lines);
        LocalDate invoiceDate = extractInvoiceDate(rawText);
        BigDecimal totalHt = extractTotalHt(rawText);
        List<ParsedLineItem> lineItems = extractLineItems(lines);

        boolean foundAnything = supplierName != null || invoiceDate != null || totalHt != null || !lineItems.isEmpty();
        String currency = foundAnything ? "EUR" : null;

        return new ParsedInvoice(supplierName, invoiceDate, totalHt, currency, lineItems);
    }

    // Layout le plus courant : le nom du fournisseur est l'en-tête, donc la première ligne non
    // vide qui contient au moins une lettre (pas juste une date ou un numéro de page).
    private String extractSupplierName(List<String> lines) {
        return lines.stream()
                .filter(l -> l.chars().anyMatch(Character::isLetter))
                .findFirst()
                .map(l -> l.length() > 120 ? l.substring(0, 120) : l)
                .orElse(null);
    }

    private LocalDate extractInvoiceDate(String text) {
        Matcher m = DATE_PATTERN.matcher(text);
        if (!m.find()) return null;
        try {
            int day = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int year = Integer.parseInt(m.group(3));
            if (year < 100) year += 2000;
            return LocalDate.of(year, month, day);
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return null;
        }
    }

    private BigDecimal extractTotalHt(String text) {
        Matcher m = TOTAL_HT_PATTERN.matcher(text);
        if (!m.find()) return null;
        return parseFrenchAmount(m.group(1));
    }

    private List<ParsedLineItem> extractLineItems(List<String> lines) {
        List<ParsedLineItem> items = new ArrayList<>();
        for (String line : lines) {
            Matcher m = LINE_ITEM_PATTERN.matcher(line);
            if (!m.matches()) continue;

            String label = m.group(1).strip().replaceAll("[\\-:\\s]+$", "");
            BigDecimal qty = parseFrenchAmount(m.group(2));
            String unit = normalizeUnit(m.group(3));
            if (label.isBlank() || qty == null) continue;

            items.add(new ParsedLineItem(detectFiber(label), label, qty, unit));
        }
        return items;
    }

    private String detectFiber(String label) {
        String normalized = stripAccents(label.toLowerCase(Locale.FRENCH));
        for (Map.Entry<String, String> entry : FIBER_KEYWORDS.entrySet()) {
            if (Pattern.compile("\\b" + entry.getKey() + "\\b").matcher(normalized).find()) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String normalizeUnit(String raw) {
        String u = stripAccents(raw.toLowerCase(Locale.FRENCH));
        if (u.equals("u") || u.startsWith("unit")) return "unité";
        if (u.startsWith("piece") || u.startsWith("pc")) return "pièce";
        return u;
    }

    private BigDecimal parseFrenchAmount(String raw) {
        try {
            String normalized = raw.replaceAll("\\s", "").replace(',', '.');
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
