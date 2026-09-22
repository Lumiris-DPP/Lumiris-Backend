package com.minoh.lumiris_backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierInvoiceOcrParserTest {

    private final SupplierInvoiceOcrParser parser = new SupplierInvoiceOcrParser();

    @Test
    void parse_extractsSupplierDateTotalAndLineItems_fromARealisticInvoice() {
        String rawText = """
                Tissus Dupont SARL
                12 rue des Filatures, 69000 Lyon
                Facture n°2026-0451
                Date : 03/02/2026

                Laine mérinos premium 12,5 kg
                Coton bio écru 8 m
                Doublure polyester recyclé 20 m
                Fermeture éclair laiton 15 u

                Total HT : 1 234,56 €
                """;

        SupplierInvoiceOcrParser.ParsedInvoice result = parser.parse(rawText);

        assertThat(result.supplierName()).isEqualTo("Tissus Dupont SARL");
        assertThat(result.invoiceDate()).isEqualTo(LocalDate.of(2026, 2, 3));
        assertThat(result.totalHt()).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(result.currency()).isEqualTo("EUR");

        assertThat(result.lineItems()).hasSize(4);
        assertThat(result.lineItems().get(0).fiber()).isEqualTo("wool");
        assertThat(result.lineItems().get(0).qty()).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(result.lineItems().get(0).unit()).isEqualTo("kg");
        assertThat(result.lineItems().get(1).fiber()).isEqualTo("cotton");
        assertThat(result.lineItems().get(2).fiber()).isEqualTo("recycled-polyester");
        assertThat(result.lineItems().get(3).fiber()).isNull();
        assertThat(result.lineItems().get(3).unit()).isEqualTo("unité");
    }

    @Test
    void parse_returnsAllNulls_forBlankText() {
        SupplierInvoiceOcrParser.ParsedInvoice result = parser.parse("   \n  ");

        assertThat(result.supplierName()).isNull();
        assertThat(result.invoiceDate()).isNull();
        assertThat(result.totalHt()).isNull();
        assertThat(result.currency()).isNull();
        assertThat(result.lineItems()).isEmpty();
    }

    @Test
    void parse_toleratesTextWithNoRecognizablePattern() {
        SupplierInvoiceOcrParser.ParsedInvoice result = parser.parse("###//garbled$$$ output ??? 1 2 3");

        assertThat(result.totalHt()).isNull();
        assertThat(result.lineItems()).isEmpty();
    }

    @Test
    void parse_doesNotAssumeOtherFiber_whenLabelMatchesNoKnownKeyword() {
        SupplierInvoiceOcrParser.ParsedInvoice result = parser.parse("Boutons nacre 200 u");

        assertThat(result.lineItems()).singleElement().satisfies(item -> {
            assertThat(item.label()).isEqualTo("Boutons nacre");
            assertThat(item.fiber()).isNull();
        });
    }
}
