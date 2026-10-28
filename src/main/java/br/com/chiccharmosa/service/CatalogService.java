package br.com.chiccharmosa.service;

import br.com.chiccharmosa.dto.CategoryDto.CategoryRequest;
import br.com.chiccharmosa.dto.CategoryDto.CategoryResponse;
import br.com.chiccharmosa.dto.CategoryDto.CategoryView;
import br.com.chiccharmosa.dto.ProductDto.ProductRequest;
import br.com.chiccharmosa.dto.ProductDto.ProductResponse;
import br.com.chiccharmosa.dto.ProductDto.ProductView;
import br.com.chiccharmosa.entity.Category;
import br.com.chiccharmosa.dto.VariantDto.VariantRequest;
import br.com.chiccharmosa.dto.VariantDto.VariantResponse;
import br.com.chiccharmosa.dto.VariantDto.VariantView;
import br.com.chiccharmosa.entity.Product;
import br.com.chiccharmosa.entity.ProductVariant;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.CategoryRepository;
import br.com.chiccharmosa.repository.ProductRepository;
import br.com.chiccharmosa.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final StockService stockService;
    private final AuditService auditService;

    public CatalogService(CategoryRepository categoryRepository, ProductRepository productRepository,
            ProductVariantRepository variantRepository, StockService stockService, AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.stockService = stockService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public boolean variantExists(UUID id) {
        return variantRepository.existsById(id);
    }

    @Transactional
    public VariantResponse createVariant(UUID productId, VariantRequest request, Instant changedAt) {
        if (request.id() != null) {
            ProductVariant existing = variantRepository.findById(request.id()).orElse(null);
            if (existing != null) {
                return VariantResponse.from(existing, stockService.balance(existing.getId()));
            }
        }
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(HttpStatus.valueOf(422), "Produto inexistente"));
        ProductVariant variant = new ProductVariant();
        variant.setId(request.id() == null ? UUID.randomUUID() : request.id());
        variant.setProduct(product);
        applyRequest(variant, request);
        variant.touch(changedAt);
        variantRepository.save(variant);
        VariantResponse response = VariantResponse.from(variant, 0);
        auditService.record(AuditService.PRODUCT_VARIANT, variant.getId(), AuditService.CREATE, null, response, changedAt);
        return response;
    }

    @Transactional(readOnly = true)
    public VariantResponse findVariant(UUID id) {
        ProductVariant variant = findActiveVariant(id);
        return VariantResponse.from(variant, stockService.balance(id));
    }

    @Transactional(readOnly = true)
    public Page<VariantResponse> listVariants(UUID productId, Pageable pageable) {
        findActiveProduct(productId);
        Page<ProductVariant> page = variantRepository.findByProductIdAndDeletedAtIsNull(productId, pageable);
        List<UUID> ids = page.getContent().stream().map(ProductVariant::getId).toList();
        Map<UUID, Integer> balances = stockService.balances(ids);
        return page.map(variant -> VariantResponse.from(variant, balances.get(variant.getId())));
    }

    @Transactional(readOnly = true)
    public Page<VariantView> searchVariants(String q, String status, Pageable pageable) {
        String term = q == null ? "" : q.trim();
        Integer minBalance = null;
        Integer maxBalance = null;
        if ("SOLD_OUT".equals(status)) {
            maxBalance = 0;
        } else if ("LOW".equals(status)) {
            minBalance = 1;
            maxBalance = StockService.LOW_STOCK_LIMIT;
        } else if ("AVAILABLE".equals(status)) {
            minBalance = StockService.LOW_STOCK_LIMIT + 1;
        } else if (status != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Status inválido");
        }
        Page<Object[]> rows = variantRepository.search(term, minBalance, maxBalance, pageable);
        return rows.map(row -> VariantView.from((ProductVariant) row[0], ((Number) row[1]).intValue()));
    }

    @Transactional
    public VariantResponse updateVariant(UUID id, VariantRequest request, Instant changedAt) {
        ProductVariant variant = findActiveVariant(id);
        VariantResponse before = VariantResponse.from(variant, stockService.balance(id));
        applyRequest(variant, request);
        variant.touch(changedAt);
        VariantResponse after = VariantResponse.from(variant, before.balance());
        auditService.record(AuditService.PRODUCT_VARIANT, id, AuditService.UPDATE, before, after, changedAt);
        return after;
    }

    @Transactional
    public void deleteVariant(UUID id, Instant changedAt) {
        ProductVariant variant = findActiveVariant(id);
        VariantResponse before = VariantResponse.from(variant, stockService.balance(id));
        variant.markDeleted(changedAt);
        auditService.record(AuditService.PRODUCT_VARIANT, id, AuditService.DELETE, before, null, changedAt);
    }

    private void applyRequest(ProductVariant variant, VariantRequest request) {
        variant.setColor(request.color());
        variant.setSize(request.size());
        variant.setModel(request.model());
        variant.setPrice(request.price());
        variant.setCost(request.cost());
    }

    private ProductVariant findActiveVariant(UUID id) {
        return variantRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Variante não encontrada"));
    }

    @Transactional(readOnly = true)
    public boolean categoryExists(UUID id) {
        return categoryRepository.existsById(id);
    }

    @Transactional(readOnly = true)
    public boolean productExists(UUID id) {
        return productRepository.existsById(id);
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request, Instant changedAt) {
        if (request.id() != null) {
            Category existing = categoryRepository.findById(request.id()).orElse(null);
            if (existing != null) {
                return CategoryResponse.from(existing);
            }
        }
        Category category = new Category();
        category.setId(request.id() == null ? UUID.randomUUID() : request.id());
        category.setName(request.name());
        category.touch(changedAt);
        CategoryResponse response = CategoryResponse.from(categoryRepository.save(category));
        auditService.record(AuditService.CATEGORY, category.getId(), AuditService.CREATE, null, response, changedAt);
        return response;
    }

    @Transactional(readOnly = true)
    public CategoryView getCategoryView(UUID id) {
        Category category = findActiveCategory(id);
        return new CategoryView(category.getId(), category.getName(),
                productRepository.countByCategoryIdAndDeletedAtIsNull(id));
    }

    @Transactional(readOnly = true)
    public Page<CategoryView> listCategoryViews(String name, Pageable pageable) {
        Page<Category> page;
        if (name == null || name.isBlank()) {
            page = categoryRepository.findByDeletedAtIsNull(pageable);
        } else {
            page = categoryRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable);
        }
        Map<UUID, Long> counts = new HashMap<>();
        if (page.hasContent()) {
            List<UUID> ids = page.getContent().stream().map(Category::getId).toList();
            for (Object[] row : productRepository.countByCategoryIds(ids)) {
                counts.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }
        return page.map(category -> new CategoryView(category.getId(), category.getName(),
                counts.getOrDefault(category.getId(), 0L)));
    }

    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request, Instant changedAt) {
        Category category = findActiveCategory(id);
        CategoryResponse before = CategoryResponse.from(category);
        category.setName(request.name());
        category.touch(changedAt);
        CategoryResponse after = CategoryResponse.from(category);
        auditService.record(AuditService.CATEGORY, id, AuditService.UPDATE, before, after, changedAt);
        return after;
    }

    @Transactional
    public void deleteCategory(UUID id, Instant changedAt) {
        Category category = findActiveCategory(id);
        if (productRepository.existsByCategoryIdAndDeletedAtIsNull(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Categoria possui produtos");
        }
        CategoryResponse before = CategoryResponse.from(category);
        category.markDeleted(changedAt);
        auditService.record(AuditService.CATEGORY, id, AuditService.DELETE, before, null, changedAt);
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request, Instant changedAt) {
        if (request.id() != null) {
            Product existing = productRepository.findById(request.id()).orElse(null);
            if (existing != null) {
                return ProductResponse.from(existing);
            }
        }
        Product product = new Product();
        product.setId(request.id() == null ? UUID.randomUUID() : request.id());
        product.setCategory(findActiveCategoryForProduct(request.categoryId()));
        product.setName(request.name());
        product.setDescription(request.description());
        product.touch(changedAt);
        ProductResponse response = ProductResponse.from(productRepository.save(product));
        auditService.record(AuditService.PRODUCT, product.getId(), AuditService.CREATE, null, response, changedAt);
        return response;
    }

    @Transactional(readOnly = true)
    public ProductView getProductView(UUID id) {
        return buildProductViews(List.of(findActiveProduct(id))).get(0);
    }

    @Transactional(readOnly = true)
    public Page<ProductView> listProductViews(String name, UUID categoryId, Pageable pageable) {
        boolean filterByName = name != null && !name.isBlank();
        boolean filterByCategory = categoryId != null;
        Page<Product> page;
        if (filterByName && filterByCategory) {
            page = productRepository.findByDeletedAtIsNullAndCategoryIdAndNameContainingIgnoreCase(
                    categoryId, name, pageable);
        } else if (filterByName) {
            page = productRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable);
        } else if (filterByCategory) {
            page = productRepository.findByDeletedAtIsNullAndCategoryId(categoryId, pageable);
        } else {
            page = productRepository.findByDeletedAtIsNull(pageable);
        }
        List<ProductView> views = buildProductViews(page.getContent());
        return new PageImpl<>(views, pageable, page.getTotalElements());
    }

    private List<ProductView> buildProductViews(List<Product> products) {
        List<UUID> productIds = products.stream().map(Product::getId).toList();
        List<ProductVariant> variants = variantRepository.findByProductIdInAndDeletedAtIsNull(productIds);
        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<UUID, Integer> balances = stockService.balances(variantIds);
        Map<UUID, List<ProductVariant>> variantsByProduct = new HashMap<>();
        for (ProductVariant variant : variants) {
            variantsByProduct.computeIfAbsent(variant.getProduct().getId(), key -> new ArrayList<>()).add(variant);
        }
        List<ProductView> views = new ArrayList<>();
        for (Product product : products) {
            List<ProductVariant> own = variantsByProduct.getOrDefault(product.getId(), List.of());
            views.add(toProductView(product, own, balances));
        }
        return views;
    }

    private ProductView toProductView(Product product, List<ProductVariant> variants, Map<UUID, Integer> balances) {
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        int totalBalance = 0;
        for (ProductVariant variant : variants) {
            BigDecimal price = variant.getPrice();
            if (minPrice == null || price.compareTo(minPrice) < 0) {
                minPrice = price;
            }
            if (maxPrice == null || price.compareTo(maxPrice) > 0) {
                maxPrice = price;
            }
            totalBalance += balances.get(variant.getId());
        }
        Category category = product.getCategory();
        return new ProductView(product.getId(), category.getId(), category.getName(), product.getName(),
                product.getDescription(), variants.size(), minPrice, maxPrice, totalBalance);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request, Instant changedAt) {
        Product product = findActiveProduct(id);
        ProductResponse before = ProductResponse.from(product);
        product.setCategory(findActiveCategoryForProduct(request.categoryId()));
        product.setName(request.name());
        product.setDescription(request.description());
        product.touch(changedAt);
        ProductResponse after = ProductResponse.from(product);
        auditService.record(AuditService.PRODUCT, id, AuditService.UPDATE, before, after, changedAt);
        return after;
    }

    @Transactional
    public void deleteProduct(UUID id, Instant changedAt) {
        Product product = findActiveProduct(id);
        ProductResponse before = ProductResponse.from(product);
        product.markDeleted(changedAt);
        auditService.record(AuditService.PRODUCT, id, AuditService.DELETE, before, null, changedAt);
    }

    private Category findActiveCategory(UUID id) {
        return categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));
    }

    private Category findActiveCategoryForProduct(UUID categoryId) {
        return categoryRepository.findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new BusinessException(HttpStatus.valueOf(422), "Categoria inexistente"));
    }

    private Product findActiveProduct(UUID id) {
        return productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
    }
}
