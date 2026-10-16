package br.com.chiccharmosa.config;

import java.util.UUID;

public final class SeedIds {

    public static final UUID ADMIN_ROLE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    public static final UUID ADMIN_USER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    public static final UUID STORE_ACCOUNT = UUID.fromString("30000000-0000-0000-0000-000000000001");
    public static final UUID PERSONAL_ACCOUNT = UUID.fromString("30000000-0000-0000-0000-000000000002");
    public static final UUID SALES_CATEGORY = UUID.fromString("40000000-0000-0000-0000-000000000001");
    public static final UUID REFUND_CATEGORY = UUID.fromString("40000000-0000-0000-0000-000000000002");
    public static final UUID SHIPPING_CATEGORY = UUID.fromString("40000000-0000-0000-0000-000000000003");
    public static final UUID OTHER_CATEGORY = UUID.fromString("40000000-0000-0000-0000-000000000004");

    private SeedIds() {
    }
}
