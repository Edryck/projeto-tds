package br.com.chiccharmosa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chiccharmosa.dto.CategoryDto.CategoryRequest;
import br.com.chiccharmosa.dto.CategoryDto.CategoryResponse;
import br.com.chiccharmosa.dto.ProductDto.ProductRequest;
import br.com.chiccharmosa.dto.ProductDto.ProductResponse;
import br.com.chiccharmosa.dto.VariantDto.VariantRequest;
import br.com.chiccharmosa.dto.VariantDto.VariantView;
import br.com.chiccharmosa.security.CurrentUser;
import br.com.chiccharmosa.service.CatalogService;
import br.com.chiccharmosa.service.StockService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

class VariantSearchTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private StockService stockService;

    private String adminToken;
    private String productName;
    private String uniqueColor;

    @BeforeEach
    void setUp() {
        CurrentUser admin = testData.loginAs(testData.admin());
        adminToken = testData.tokenFor(admin);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        productName = "Calcinha busca " + suffix;
        uniqueColor = "Rosa " + suffix;
        CategoryResponse category = catalogService.createCategory(
                new CategoryRequest(UUID.randomUUID(), "Calcinha " + suffix), Instant.now());
        ProductResponse product = catalogService.createProduct(
                new ProductRequest(UUID.randomUUID(), category.id(), productName, null), Instant.now());
        createVariant(product.id(), uniqueColor, "P", 0);
        createVariant(product.id(), "Preto", "M", 2);
        createVariant(product.id(), "Preto", "G", 5);
        UUID negative = createVariant(product.id(), "Preto", "GG", 0);
        stockService.withdraw(negative, 1, UUID.randomUUID(), true);
    }

    @Test
    void lowReturnsOnlyBalancesOneToTwo() {
        Page<VariantView> page = catalogService.searchVariants(productName, "LOW", PageRequest.of(0, 20));
        assertThat(page.getContent()).extracting(VariantView::size).containsExactly("M");
        assertThat(page.getContent().get(0).status()).isEqualTo("LOW");
    }

    @Test
    void soldOutIncludesZeroAndNegative() {
        Page<VariantView> page = catalogService.searchVariants(productName, "SOLD_OUT", PageRequest.of(0, 20));
        assertThat(page.getContent()).extracting(VariantView::balance).containsExactlyInAnyOrder(0, -1);
    }

    @Test
    void queryMatchesColor() {
        Page<VariantView> page = catalogService.searchVariants(uniqueColor, null, PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void invalidStatusReturns400() throws Exception {
        mockMvc.perform(get("/api/variants?status=XYZ").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    private UUID createVariant(UUID productId, String color, String size, int quantity) {
        VariantRequest request = new VariantRequest(UUID.randomUUID(), color, size, null,
                new BigDecimal("39.90"), new BigDecimal("15.00"));
        UUID variantId = catalogService.createVariant(productId, request, Instant.now()).id();
        if (quantity > 0) {
            testData.stockIn(variantId, quantity);
        }
        return variantId;
    }
}
