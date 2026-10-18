package br.com.chiccharmosa.service;

import br.com.chiccharmosa.dto.CategoryDto.CategoryRequest;
import br.com.chiccharmosa.dto.CategoryDto.CategoryResponse;
import br.com.chiccharmosa.dto.ProductDto.ProductRequest;
import br.com.chiccharmosa.dto.ProductDto.ProductResponse;
import br.com.chiccharmosa.entity.Category;
import br.com.chiccharmosa.dto.VariantDto.VariantRequest;
import br.com.chiccharmosa.dto.VariantDto.VariantResponse;
import br.com.chiccharmosa.entity.Product;
import br.com.chiccharmosa.entity.ProductVariant;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.CategoryRepository;
import br.com.chiccharmosa.repository.ProductRepository;
import br.com.chiccharmosa.repository.ProductVariantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
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
    public CategoryResponse getCategory(UUID id) {
        return CategoryResponse.from(findActiveCategory(id));
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> listCategories(String name, Pageable pageable) {
        if (name == null || name.isBlank()) {
            return categoryRepository.findByDeletedAtIsNull(pageable).map(CategoryResponse::from);
        }
        return categoryRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable)
                .map(CategoryResponse::from);
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
    public ProductResponse getProduct(UUID id) {
        return ProductResponse.from(findActiveProduct(id));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(String name, UUID categoryId, Pageable pageable) {
        boolean filterByName = name != null && !name.isBlank();
        boolean filterByCategory = categoryId != null;
        if (filterByName && filterByCategory) {
            return productRepository.findByDeletedAtIsNullAndCategoryIdAndNameContainingIgnoreCase(
                    categoryId, name, pageable).map(ProductResponse::from);
        }
        if (filterByName) {
            return productRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable)
                    .map(ProductResponse::from);
        }
        if (filterByCategory) {
            return productRepository.findByDeletedAtIsNullAndCategoryId(categoryId, pageable)
                    .map(ProductResponse::from);
        }
        return productRepository.findByDeletedAtIsNull(pageable).map(ProductResponse::from);
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
