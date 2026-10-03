package com.minoh.lumiris_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minoh.lumiris_backend.service.BlockchainService;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import com.minoh.lumiris_backend.service.DecisionLogRecorder;
import com.minoh.lumiris_backend.service.MarketplaceVariantService;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Catalogue marketplace par ses URL réelles (sécurité, validation, sérialisation), sur un vrai
 * PostgreSQL : publication, édition, archivage, suppression, rollback d'une annonce sur déclinaison
 * ou mesure invalide, recherche et tris, suggestions, piste d'audit et compteur de vues. Écrit avant
 * le découpage de MarketplaceService pour prouver que les contrats ne bougent pas.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "security.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        // Sans clé, la création du Price Stripe de l'annonce est sautée : aucun appel réseau.
        "stripe.secret-key=",
        "stripe.publishable-key=",
        "stripe.webhook-secret=whsec_catalog_test",
        "stripe.bootstrap-catalog=false",
        "stripe.products.solo=prod_dummy",
        "stripe.products.studio=prod_dummy",
        "stripe.products.maison=prod_dummy",
        "stripe.products.atelier-plus=prod_dummy",
        "stripe.products.local=prod_dummy",
        "blockchain.wallet.private-key=0x0000000000000000000000000000000000000000000000000000000000000001",
        "spring.cache.type=none",
        "lumiris.rate-limit.enabled=false",
})
class MarketplaceCatalogIntegrationTest {

    // Champs de MarketplaceItemResponse tels que sérialisés : le contrat de toutes les routes catalogue.
    private static final Set<String> ITEM_FIELDS = new TreeSet<>(List.of(
            "id", "artisanProfileId", "artisanName", "dppFormId", "name", "description", "category", "material",
            "originCountry", "priceCents", "currency", "stock", "variants", "sizeGuide", "shippingCents",
            "returnPolicy", "warrantyDescription", "preparationDays", "effectivePreparationDays",
            "atelierPausedUntil", "weightGrams", "externalOrderUrl", "photoUrl", "status", "irisTotal", "irisGrade",
            "atelierPlus", "inAppSale", "createdAt", "views", "salesCount"));
    private static final Set<String> DECISION_LOG_FIELDS = new TreeSet<>(List.of(
            "id", "context", "sortKey", "commissionConsidered", "createdAt", "ranked"));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private MinioClient minioClient;

    @MockitoBean
    private BlockchainService blockchainService;

    @MockitoSpyBean
    private DecisionLogRecorder decisionLogRecorder;

    @Autowired
    private MarketplaceVariantService variantService;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        reset(decisionLogRecorder);
    }

    // Publication : la conversion d'un passeport valide crée l'annonce, ses déclinaisons et son guide
    // des mesures, visible ensuite du public par sa fiche et par son passeport.
    @Test
    void publish_createsTheListingWithVariantsAndSizeGuide() throws Exception {
        Atelier atelier = atelier();
        UUID dpp = dpp(atelier, "Veste publication", 80.0);

        JsonNode created = call(201, post("/api/marketplace/products/from-dpp/" + dpp)
                .with(user(atelier.email()).roles("ARTISAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(convertBody("M", "Ecru")));

        assertThat(fieldNames(created)).isEqualTo(ITEM_FIELDS);
        assertThat(created.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(created.get("stock").asInt()).isEqualTo(4);
        assertThat(sizes(created)).containsExactly("S", "M");
        assertThat(created.get("sizeGuide")).hasSize(1);
        assertThat(created.get("irisTotal").asDouble()).isEqualTo(80.0);
        String id = created.get("id").asText();
        assertThat(fieldNames(call(200, get("/public/marketplace/products/" + id)))).isEqualTo(ITEM_FIELDS);
        assertThat(call(200, get("/public/marketplace/products/by-dpp/" + dpp)).get("id").asText()).isEqualTo(id);
    }

    // Édition : remplacement complet des déclinaisons (gardée, retirée, ajoutée) et du guide des mesures.
    @Test
    void edit_replacesVariantsAndSizeGuide() throws Exception {
        Atelier atelier = atelier();
        JsonNode created = publish(atelier, "Veste edition", 70.0);
        JsonNode small = variant(created, "S");

        JsonNode updated = call(200, put("/api/marketplace/products/" + created.get("id").asText())
                .with(user(atelier.email()).roles("ARTISAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Veste renommee", 9900, "PUBLISHED",
                        variantJson(small.get("id").asText(), "S", 3, small.get("version").asLong())
                                + "," + variantJson(null, "L", 2, null),
                        measureJson("L", "Poitrine", 520))));

        assertThat(fieldNames(updated)).isEqualTo(ITEM_FIELDS);
        assertThat(updated.get("name").asText()).isEqualTo("Veste renommee");
        assertThat(updated.get("priceCents").asInt()).isEqualTo(9900);
        assertThat(sizes(updated)).containsExactly("S", "L");
        assertThat(updated.get("stock").asInt()).isEqualTo(5);
        assertThat(updated.get("sizeGuide").get(0).get("sizeLabel").asText()).isEqualTo("L");
    }

    // Une déclinaison dont le stock a bougé pendant la saisie est refusée en conflit, sans rien changer.
    @Test
    void edit_withStaleVariantVersion_isRefusedAndChangesNothing() throws Exception {
        Atelier atelier = atelier();
        JsonNode created = publish(atelier, "Veste conflit", 70.0);
        JsonNode small = variant(created, "S");

        call(409, put("/api/marketplace/products/" + created.get("id").asText())
                .with(user(atelier.email()).roles("ARTISAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Veste changee", 9900, "PUBLISHED",
                        variantJson(small.get("id").asText(), "S", 9, 99L), "")));

        assertUnchanged(created);
    }

    // Une mesure pour une taille absente annule toute la mise à jour : nom, prix, déclinaisons et guide.
    @Test
    void edit_withSizeGuideForAbsentSize_rollsBackProductAndVariants() throws Exception {
        Atelier atelier = atelier();
        JsonNode created = publish(atelier, "Veste rollback", 70.0);
        JsonNode small = variant(created, "S");

        call(422, put("/api/marketplace/products/" + created.get("id").asText())
                .with(user(atelier.email()).roles("ARTISAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Veste changee", 9900, "PUBLISHED",
                        variantJson(small.get("id").asText(), "S", 9, small.get("version").asLong())
                                + "," + variantJson(null, "L", 2, null),
                        measureJson("XL", "Poitrine", 560))));

        assertUnchanged(created);
    }

    // Deux déclinaisons identiques à la publication : rien n'est créé, ni annonce ni déclinaison.
    @Test
    void publish_withDuplicateVariantCombination_createsNothing() throws Exception {
        Atelier atelier = atelier();
        UUID dpp = dpp(atelier, "Veste doublon", 70.0);

        call(422, post("/api/marketplace/products/from-dpp/" + dpp)
                .with(user(atelier.email()).roles("ARTISAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(convertBody("S", "ecru")));

        assertThat(jdbc.queryForObject("select count(*) from marketplace_products where dpp_form_id = ?",
                Integer.class, dpp)).isZero();
    }

    // Archivage : l'annonce reste dans le catalogue de l'atelier mais sort du catalogue public.
    @Test
    void archive_removesTheListingFromThePublicCatalogue() throws Exception {
        Atelier atelier = atelier();
        JsonNode created = publish(atelier, "Veste archive", 70.0);
        String id = created.get("id").asText();
        JsonNode small = variant(created, "S");
        JsonNode medium = variant(created, "M");

        call(200, put("/api/marketplace/products/" + id)
                .with(user(atelier.email()).roles("ARTISAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Veste archive", 8900, "ARCHIVED",
                        variantJson(small.get("id").asText(), "S", 3, small.get("version").asLong()) + ","
                                + variantJson(medium.get("id").asText(), "M", 1, medium.get("version").asLong()),
                        "")));

        call(404, get("/public/marketplace/products/" + id));
        assertThat(ids(call(200, get("/public/marketplace/search?category=" + atelier.category())).get("items")))
                .doesNotContain(id);
        JsonNode mine = call(200, get("/api/marketplace/products").with(user(atelier.email()).roles("ARTISAN")));
        assertThat(mine).anySatisfy(item -> {
            assertThat(item.get("id").asText()).isEqualTo(id);
            assertThat(item.get("status").asText()).isEqualTo("ARCHIVED");
        });
    }

    // Suppression : possible sans commande ; refusée en conflit dès qu'une commande existe.
    @Test
    void delete_withoutOrders_removesTheListing_andWithOrders_isRefused() throws Exception {
        Atelier atelier = atelier();
        String free = publish(atelier, "Veste libre", 70.0).get("id").asText();
        String sold = publish(atelier, "Veste vendue", 70.0).get("id").asText();
        jdbc.update("insert into marketplace_orders (product_id, amount_total_cents, commission_cents, currency, status) "
                + "values (?, 8900, 445, 'EUR', 'PAID')", UUID.fromString(sold));

        call(204, delete("/api/marketplace/products/" + free).with(user(atelier.email()).roles("ARTISAN")));
        call(404, get("/api/marketplace/products/" + free).with(user(atelier.email()).roles("ARTISAN")));
        call(409, delete("/api/marketplace/products/" + sold).with(user(atelier.email()).roles("ARTISAN")));
        call(200, get("/api/marketplace/products/" + sold).with(user(atelier.email()).roles("ARTISAN")));
    }

    // Recherche : chaque tri rend l'ordre attendu et produit une décision auditable, persistée même si
    // la recherche est en lecture seule (transaction séparée du recorder).
    @Test
    void search_sortsAndRecordsAnAuditableDecision() throws Exception {
        Atelier atelier = atelier();
        String token = uniqueWord();
        String cheap = publish(atelier, "Veste " + token + " un", 1000, 50.0).get("id").asText();
        String middle = publish(atelier, "Veste " + token + " deux", 2000, 90.0).get("id").asText();
        String dear = publish(atelier, "Veste " + token + " trois", 3000, 70.0).get("id").asText();
        String filter = "/public/marketplace/search?category=" + atelier.category();
        int logsBefore = jdbc.queryForObject("select count(*) from marketplace_decision_logs", Integer.class);

        JsonNode byPrice = call(200, get(filter + "&sort=price-asc"));
        assertThat(ids(byPrice.get("items"))).containsExactly(cheap, middle, dear);
        assertThat(fieldNames(byPrice.get("decisionLog"))).isEqualTo(DECISION_LOG_FIELDS);
        assertThat(byPrice.get("decisionLog").get("sortKey").asText()).isEqualTo("PRICE_ASC");
        assertThat(byPrice.get("decisionLog").get("commissionConsidered").asBoolean()).isFalse();
        assertThat(ids(call(200, get(filter + "&sort=price-desc")).get("items"))).containsExactly(dear, middle, cheap);
        assertThat(ids(call(200, get(filter + "&sort=iris")).get("items"))).containsExactly(middle, dear, cheap);
        JsonNode newest = call(200, get(filter));
        assertThat(ids(newest.get("items"))).containsExactly(dear, middle, cheap);
        assertThat(newest.get("decisionLog").get("sortKey").asText()).isEqualTo("NEWEST");
        JsonNode text = call(200, get("/public/marketplace/search?q=" + token));
        assertThat(ids(text.get("items"))).containsExactlyInAnyOrder(cheap, middle, dear);
        assertThat(text.get("decisionLog").get("sortKey").asText()).isEqualTo("TEXT_RANK_DESC");
        JsonNode personalized = call(200, get(filter + "&sort=price-asc&personalize=" + atelier.category()));
        assertThat(personalized.get("decisionLog").get("sortKey").asText()).isEqualTo("PRICE_ASC+PERSONALIZED");

        assertThat(jdbc.queryForObject("select count(*) from marketplace_decision_logs", Integer.class))
                .isEqualTo(logsBefore + 6);
        String logId = byPrice.get("decisionLog").get("id").asText();
        JsonNode audited = call(200, get("/api/marketplace/decision-logs/" + logId)
                .with(user(admin()).roles("ADMIN")));
        assertThat(fieldNames(audited)).isEqualTo(DECISION_LOG_FIELDS);
        assertThat(audited.get("ranked")).hasSize(3);
        assertThat(audited.get("ranked").get(0).get("productId").asText()).isEqualTo(cheap);
        call(403, get("/api/marketplace/decision-logs/" + logId).with(user(atelier.email()).roles("ARTISAN")));
    }

    // Piste d'audit en échec : la recherche répond quand même, avec un log transitoire non persisté.
    @Test
    void search_whenTheDecisionLogCannotBeWritten_stillAnswers() throws Exception {
        Atelier atelier = atelier();
        String id = publish(atelier, "Veste audit", 70.0).get("id").asText();
        doThrow(new IllegalStateException("piste d'audit indisponible (test)"))
                .when(decisionLogRecorder).persist(any(), any(), any(), any());

        JsonNode result = call(200, get("/public/marketplace/search?category=" + atelier.category()));

        assertThat(ids(result.get("items"))).containsExactly(id);
        assertThat(result.get("decisionLog").get("id").isNull()).isTrue();
    }

    // Suggestions : jusqu'à trois pièces de score au moins égal au scan, triées par score décroissant ;
    // la catégorie s'élargit quand elle n'en compte pas trois, sans jamais baisser le seuil.
    @Test
    void suggest_returnsUpToThreeAboveTheScannedScore() throws Exception {
        Atelier sameCategory = atelier();
        String a = publish(sameCategory, "Veste suggestion a", 99.5).get("id").asText();
        String b = publish(sameCategory, "Veste suggestion b", 99.7).get("id").asText();
        publish(sameCategory, "Veste suggestion basse", 60.0);
        Atelier otherCategory = atelier();
        String c = publish(otherCategory, "Veste suggestion c", 99.6).get("id").asText();

        JsonNode relaxed = call(200, post("/public/marketplace/suggest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"" + sameCategory.category() + "\",\"score\":99.4}"));

        List<String> suggested = new ArrayList<>();
        relaxed.get("suggestions").forEach(s -> suggested.add(s.get("item").get("id").asText()));
        assertThat(suggested).containsExactly(b, c, a);
        assertThat(fieldNames(relaxed.get("suggestions").get(0).get("item"))).isEqualTo(ITEM_FIELDS);
        assertThat(relaxed.get("decisionLog").get("sortKey").asText())
                .isEqualTo("IRIS_DESC_THEN_ATELIER_PLUS/RELAXED_CATEGORY");
        assertThat(relaxed.get("suggestions").get(0).get("reason").asText()).contains("catégorie élargie");

        Atelier full = atelier();
        String x = publish(full, "Veste trio x", 98.1).get("id").asText();
        String y = publish(full, "Veste trio y", 98.3).get("id").asText();
        String z = publish(full, "Veste trio z", 98.2).get("id").asText();
        JsonNode strict = call(200, post("/public/marketplace/suggest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"" + full.category() + "\",\"score\":98.0}"));
        List<String> trio = new ArrayList<>();
        strict.get("suggestions").forEach(s -> trio.add(s.get("item").get("id").asText()));
        assertThat(trio).containsExactly(y, z, x);
        assertThat(strict.get("decisionLog").get("sortKey").asText()).isEqualTo("IRIS_DESC_THEN_ATELIER_PLUS");
    }

    // Panier et vues : fiches par identifiants (les inconnues absentes), compteur de vues incrémenté.
    @Test
    void cartLookupAndViews_useThePublicCatalogue() throws Exception {
        Atelier atelier = atelier();
        String id = publish(atelier, "Veste panier", 70.0).get("id").asText();

        JsonNode cart = call(200, get("/public/marketplace/products?ids=" + id + "," + UUID.randomUUID()));
        assertThat(ids(cart)).containsExactly(id);
        call(202, post("/public/marketplace/products/" + id + "/view"));
        assertThat(jdbc.queryForObject("select views from marketplace_products where id = ?", Long.class,
                UUID.fromString(id))).isEqualTo(1L);
    }

    // Les déclinaisons ne s'écrivent que dans la transaction de leur annonce : appelées seules, refus.
    @Test
    void variantsCannotBeWrittenOutsideTheListingTransaction() {
        assertThatThrownBy(() -> variantService.seedDefaultVariant(new MarketplaceProduct(), 1))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    // ── Données et outils ───────────────────────────────────────────────────

    private record Atelier(UUID userId, String email, String category) {}

    // Atelier abonné et encaissable, avec une catégorie propre au test pour isoler ses recherches.
    private Atelier atelier() {
        UUID user = UUID.randomUUID();
        String email = "atelier-" + user + "@lumiris.test";
        jdbc.update("insert into users (id, email, password_hash, role, name) values (?, ?, '{noop}x', 'ARTISAN', 'Atelier')",
                user, email);
        jdbc.update("insert into artisan_profiles (user_id, display_name, slug) values (?, 'Atelier Catalogue', ?)",
                user, "atelier-catalogue-" + user);
        jdbc.update("insert into subscriptions (user_id, plan_tier, billing_cycle, status) "
                + "values (?, 'ATELIER_SOLO', 'MONTHLY', 'active')", user);
        jdbc.update("insert into seller_accounts (user_id, stripe_account_id, charges_enabled, payouts_enabled, "
                + "onboarding_completed) values (?, ?, true, true, true)", user, "acct_" + user);
        return new Atelier(user, email, "cat" + uniqueWord());
    }

    private String admin() {
        UUID user = UUID.randomUUID();
        String email = "admin-" + user + "@lumiris.test";
        jdbc.update("insert into users (id, email, password_hash, role, name) values (?, ?, '{noop}x', 'ADMIN', 'Admin')",
                user, email);
        return email;
    }

    // Passeport valide de l'atelier, avec son score Iris.
    private UUID dpp(Atelier atelier, String name, double irisTotal) {
        UUID dpp = UUID.randomUUID();
        jdbc.update("insert into dpp_forms (id, user_id, status, product_name, product_category, quantity) "
                + "values (?, ?, 'VALID'::dpp_status, ?, ?, 4)", dpp, atelier.userId(), name, atelier.category());
        jdbc.update("insert into dpp_iris_scores (dpp_form_id, total, grade) values (?, ?, 'B')", dpp, irisTotal);
        return dpp;
    }

    private JsonNode publish(Atelier atelier, String name, double irisTotal) throws Exception {
        return publish(atelier, name, 8900, irisTotal);
    }

    private JsonNode publish(Atelier atelier, String name, int priceCents, double irisTotal) throws Exception {
        UUID dpp = dpp(atelier, name, irisTotal);
        return call(201, post("/api/marketplace/products/from-dpp/" + dpp)
                .with(user(atelier.email()).roles("ARTISAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(convertBody("M", "Ecru").replace("\"priceCents\":8900", "\"priceCents\":" + priceCents)));
    }

    // Publication à deux déclinaisons : S Ecru (stock 3), puis la seconde (stock 1), et une mesure pour S.
    private static String convertBody(String secondSize, String secondColor) {
        return "{\"priceCents\":8900,\"currency\":\"EUR\",\"material\":\"Lin\",\"shippingCents\":690,"
                + "\"preparationDays\":2,\"weightGrams\":600,\"status\":\"PUBLISHED\",\"variants\":["
                + "{\"sizeLabel\":\"S\",\"colorLabel\":\"Ecru\",\"stock\":3,\"position\":0},"
                + "{\"sizeLabel\":\"" + secondSize + "\",\"colorLabel\":\"" + secondColor + "\",\"stock\":1,\"position\":1}],"
                + "\"sizeGuide\":[{\"sizeLabel\":\"S\",\"label\":\"Poitrine\",\"valueMm\":480,\"position\":0}]}";
    }

    private static String updateBody(String name, int priceCents, String status, String variants, String measures) {
        return "{\"name\":\"" + name + "\",\"priceCents\":" + priceCents + ",\"currency\":\"EUR\",\"material\":\"Lin\","
                + "\"shippingCents\":690,\"preparationDays\":2,\"weightGrams\":600,\"status\":\"" + status + "\","
                + "\"variants\":[" + variants + "],\"sizeGuide\":[" + measures + "]}";
    }

    private static String variantJson(String id, String size, int stock, Long version) {
        return "{" + (id != null ? "\"id\":\"" + id + "\"," : "") + "\"sizeLabel\":\"" + size + "\","
                + "\"colorLabel\":\"Ecru\",\"stock\":" + stock + ",\"position\":0"
                + (version != null ? ",\"version\":" + version : "") + "}";
    }

    private static String measureJson(String size, String label, int valueMm) {
        return "{\"sizeLabel\":\"" + size + "\",\"label\":\"" + label + "\",\"valueMm\":" + valueMm + ",\"position\":0}";
    }

    // Vérifie en base qu'une mise à jour refusée n'a rien laissé : produit, déclinaisons et guide.
    private void assertUnchanged(JsonNode created) {
        UUID id = UUID.fromString(created.get("id").asText());
        assertThat(jdbc.queryForObject("select name from marketplace_products where id = ?", String.class, id))
                .isEqualTo(created.get("name").asText());
        assertThat(jdbc.queryForObject("select price_cents from marketplace_products where id = ?", Integer.class, id))
                .isEqualTo(created.get("priceCents").asInt());
        assertThat(jdbc.queryForList("select size_label || ':' || stock from marketplace_product_variants "
                + "where product_id = ? order by position", String.class, id)).containsExactly("S:3", "M:1");
        assertThat(jdbc.queryForList("select size_label from marketplace_size_measurements where product_id = ?",
                String.class, id)).containsExactly("S");
    }

    private JsonNode call(int expectedStatus, RequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(body).isEqualTo(expectedStatus);
        return body.isBlank() ? json.nullNode() : json.readTree(body);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode variant(JsonNode item, String size) {
        for (JsonNode variant : item.get("variants")) {
            if (size.equals(variant.get("sizeLabel").asText())) {
                return variant;
            }
        }
        throw new IllegalStateException("Déclinaison " + size + " absente");
    }

    private static List<String> sizes(JsonNode item) {
        List<String> sizes = new ArrayList<>();
        item.get("variants").forEach(v -> sizes.add(v.get("sizeLabel").asText()));
        return sizes;
    }

    private static List<String> ids(JsonNode items) {
        List<String> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    // Mot alphabétique unique : isole les recherches texte et les catégories d'un test à l'autre.
    private static String uniqueWord() {
        StringBuilder word = new StringBuilder();
        for (char c : UUID.randomUUID().toString().replace("-", "").substring(0, 10).toCharArray()) {
            word.append((char) ('a' + Character.digit(c, 16)));
        }
        return word.toString();
    }
}
