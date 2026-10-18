package br.com.chiccharmosa.repository;

import br.com.chiccharmosa.entity.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    @Query("""
            select coalesce(sum(o.subtotal - o.discount), 0) from Payment p
            join Order o on o.id = p.orderId
            where p.deletedAt is null
            and o.deletedAt is null
            and o.status <> br.com.chiccharmosa.entity.OrderStatus.CANCELLED
            and p.paidAt >= :start
            and p.paidAt < :end
            """)
    BigDecimal sumRevenue(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select coalesce(sum(i.unitCost * i.quantity), 0) from Payment p
            join Order o on o.id = p.orderId
            join o.items i
            where p.deletedAt is null
            and o.deletedAt is null
            and i.deletedAt is null
            and o.status <> br.com.chiccharmosa.entity.OrderStatus.CANCELLED
            and p.paidAt >= :start
            and p.paidAt < :end
            """)
    BigDecimal sumCostOfGoods(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select p.paidAt, o.subtotal - o.discount, sum(i.unitCost * i.quantity) from Payment p
            join Order o on o.id = p.orderId
            join o.items i
            where p.deletedAt is null
            and o.deletedAt is null
            and i.deletedAt is null
            and o.status <> br.com.chiccharmosa.entity.OrderStatus.CANCELLED
            and p.paidAt >= :start
            and p.paidAt < :end
            group by o.id, p.paidAt, o.subtotal, o.discount
            """)
    List<Object[]> profitByOrder(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select pr.name, sum(i.unitPrice * i.quantity), sum(i.unitCost * i.quantity) from Payment p
            join Order o on o.id = p.orderId
            join o.items i
            join i.variant v
            join v.product pr
            where p.deletedAt is null
            and o.deletedAt is null
            and i.deletedAt is null
            and o.status <> br.com.chiccharmosa.entity.OrderStatus.CANCELLED
            and p.paidAt >= :start
            and p.paidAt < :end
            group by pr.name
            order by pr.name
            """)
    List<Object[]> profitByProduct(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select c.name, sum(i.unitPrice * i.quantity), sum(i.unitCost * i.quantity) from Payment p
            join Order o on o.id = p.orderId
            join o.items i
            join i.variant v
            join v.product pr
            join pr.category c
            where p.deletedAt is null
            and o.deletedAt is null
            and i.deletedAt is null
            and o.status <> br.com.chiccharmosa.entity.OrderStatus.CANCELLED
            and p.paidAt >= :start
            and p.paidAt < :end
            group by c.name
            order by c.name
            """)
    List<Object[]> profitByCategory(@Param("start") Instant start, @Param("end") Instant end);
}
