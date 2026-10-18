package br.com.chiccharmosa;

import br.com.chiccharmosa.config.SeedIds;
import br.com.chiccharmosa.dto.CategoryDto.CategoryRequest;
import br.com.chiccharmosa.dto.CategoryDto.CategoryResponse;
import br.com.chiccharmosa.dto.CustomerDto.CustomerRequest;
import br.com.chiccharmosa.dto.OrderDto.ItemRequest;
import br.com.chiccharmosa.dto.OrderDto.OrderRequest;
import br.com.chiccharmosa.dto.ProductDto.ProductRequest;
import br.com.chiccharmosa.dto.ProductDto.ProductResponse;
import br.com.chiccharmosa.dto.StockDto.EntryItem;
import br.com.chiccharmosa.dto.StockDto.StockEntryRequest;
import br.com.chiccharmosa.dto.VariantDto.VariantRequest;
import br.com.chiccharmosa.dto.VariantDto.VariantResponse;
import br.com.chiccharmosa.entity.Device;
import br.com.chiccharmosa.entity.Role;
import br.com.chiccharmosa.entity.User;
import br.com.chiccharmosa.repository.DeviceRepository;
import br.com.chiccharmosa.repository.PermissionRepository;
import br.com.chiccharmosa.repository.RoleRepository;
import br.com.chiccharmosa.repository.UserRepository;
import br.com.chiccharmosa.security.CurrentUser;
import br.com.chiccharmosa.security.JwtService;
import br.com.chiccharmosa.service.CatalogService;
import br.com.chiccharmosa.service.CustomerService;
import br.com.chiccharmosa.service.StockService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class TestData {

    public static final String PASSWORD = "Senha@123";

    private static final Instant BASE_INSTANT = Instant.parse("2001-01-01T00:00:00Z");
    private static final LocalDate BASE_DAY = LocalDate.of(2020, 1, 1);

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private StockService stockService;

    @Autowired
    private CustomerService customerService;

    public User userWith(String... permissionCodes) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("role-" + UUID.randomUUID());
        role.setPermissions(permissionRepository.findByCodeIn(Set.of(permissionCodes)));
        roleRepository.save(role);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@test.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setName("Usuário de teste");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    public User admin() {
        return userRepository.findById(SeedIds.ADMIN_USER).orElseThrow();
    }

    public CurrentUser loginAs(User user) {
        Device device = new Device();
        device.setId(UUID.randomUUID());
        device.setUser(user);
        device.setName("Aparelho de teste");
        device.setActive(true);
        deviceRepository.save(device);

        CurrentUser current = new CurrentUser(user.getId(), device.getId(), permissionRepository.findCodesByUserId(user.getId()));
        actAs(current);
        return current;
    }

    public void actAs(CurrentUser current) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(current.authentication());
        SecurityContextHolder.setContext(context);
    }

    public <T> T runAs(CurrentUser current, Callable<T> action) throws Exception {
        actAs(current);
        try {
            return action.call();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    public String tokenFor(CurrentUser current) {
        return jwtService.createAccessToken(current.userId(), current.deviceId());
    }

    public LocalDate uniqueDay() {
        return BASE_DAY.plusDays(sequence.incrementAndGet());
    }

    public Instant uniqueInstant() {
        return BASE_INSTANT.plus(sequence.incrementAndGet(), ChronoUnit.DAYS);
    }

    public VariantResponse variant(String price, String cost) {
        CategoryResponse category = catalogService.createCategory(new CategoryRequest(UUID.randomUUID(), "Sutiã"), Instant.now());
        ProductResponse product = catalogService.createProduct(new ProductRequest(UUID.randomUUID(), category.id(), "Sutiã básico", null), Instant.now());
        VariantRequest request = new VariantRequest(UUID.randomUUID(), "Preto", "M", null, new BigDecimal(price), new BigDecimal(cost));
        return catalogService.createVariant(product.id(), request, Instant.now());
    }

    public UUID variantWithStock(String price, String cost, int quantity) {
        UUID variantId = variant(price, cost).id();
        stockIn(variantId, quantity);
        return variantId;
    }

    public void stockIn(UUID variantId, int quantity) {
        stockService.registerEntry(entryRequest(variantId, quantity), Instant.now());
    }

    public StockEntryRequest entryRequest(UUID variantId, int quantity) {
        EntryItem item = new EntryItem(UUID.randomUUID(), variantId, quantity, new BigDecimal("10.00"));
        return new StockEntryRequest(UUID.randomUUID(), null, LocalDate.now(), null, List.of(item));
    }

    public UUID customer() {
        CustomerRequest request = new CustomerRequest(UUID.randomUUID(), "Maria Cliente", null, null, null);
        return customerService.create(request, Instant.now()).id();
    }

    public OrderRequest orderRequest(UUID customerId, UUID variantId, int quantity) {
        return orderRequest(customerId, variantId, quantity, null, null);
    }

    public OrderRequest orderRequest(UUID customerId, UUID variantId, int quantity, String shippingCost, String couponCode) {
        ItemRequest item = new ItemRequest(UUID.randomUUID(), variantId, quantity);
        BigDecimal shipping = shippingCost == null ? null : new BigDecimal(shippingCost);
        return new OrderRequest(UUID.randomUUID(), customerId, null, shipping, couponCode, List.of(item));
    }
}
