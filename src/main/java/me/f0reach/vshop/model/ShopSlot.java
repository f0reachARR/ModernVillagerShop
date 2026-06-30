package me.f0reach.vshop.model;

import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * A trading slot. For SELL slots `amount` is unit pack size and `stockOrCapacity`
 * mirrors current SELL stock (for player shops); for BUY slots, `stockOrCapacity`
 * is the accepting capacity remaining.
 */
public final class ShopSlot {

    private final UUID id;
    private final UUID shopId;
    private int slotIndex; // (page * 27 + slot)
    private TradeSide side;
    private ItemStack itemTemplate; // size > 0; itemstack semantics for isSimilar
    private BigDecimal unitPrice;       // SELL unit price
    private BigDecimal buyUnitPrice;    // BUY unit price (used when BOTH)
    private int unitAmount;             // pack size; trade unit = unitAmount
    private int buyCapacity;            // BUY capacity (max accept count for BOTH/BUY)
    private Integer tradeLimit;         // null => unlimited per scope window
    private LimitScope limitScope;
    private Duration resetPeriod;       // null => no reset

    public ShopSlot(UUID id, UUID shopId, int slotIndex, TradeSide side, ItemStack itemTemplate,
                    BigDecimal unitPrice, BigDecimal buyUnitPrice, int unitAmount, int buyCapacity,
                    Integer tradeLimit, LimitScope limitScope, Duration resetPeriod) {
        this.id = id;
        this.shopId = shopId;
        this.slotIndex = slotIndex;
        this.side = side;
        this.itemTemplate = itemTemplate;
        this.unitPrice = unitPrice;
        this.buyUnitPrice = buyUnitPrice;
        this.unitAmount = unitAmount;
        this.buyCapacity = buyCapacity;
        this.tradeLimit = tradeLimit;
        this.limitScope = limitScope;
        this.resetPeriod = resetPeriod;
    }

    public UUID id() { return id; }
    public UUID shopId() { return shopId; }
    public int slotIndex() { return slotIndex; }
    public TradeSide side() { return side; }
    public ItemStack itemTemplate() { return itemTemplate; }
    public BigDecimal unitPrice() { return unitPrice; }
    public BigDecimal buyUnitPrice() { return buyUnitPrice; }
    public int unitAmount() { return unitAmount; }
    public int buyCapacity() { return buyCapacity; }
    public Integer tradeLimit() { return tradeLimit; }
    public LimitScope limitScope() { return limitScope; }
    public Duration resetPeriod() { return resetPeriod; }

    public boolean allowsSell() { return side == TradeSide.SELL || side == TradeSide.BOTH; }
    public boolean allowsBuy() { return side == TradeSide.BUY || side == TradeSide.BOTH; }

    /** True when {@link #buyCapacity()} is the unlimited sentinel ({@code -1}). */
    public boolean isBuyCapacityUnlimited() { return buyCapacity < 0; }

    /** True when the slot can accept {@code amount} more items into BUY. */
    public boolean hasBuyCapacityFor(int amount) {
        return isBuyCapacityUnlimited() || buyCapacity >= amount;
    }

    public void setSlotIndex(int slotIndex) { this.slotIndex = slotIndex; }
    public void setSide(TradeSide side) { this.side = side; }
    public void setItemTemplate(ItemStack itemTemplate) { this.itemTemplate = itemTemplate; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public void setBuyUnitPrice(BigDecimal buyUnitPrice) { this.buyUnitPrice = buyUnitPrice; }
    public void setUnitAmount(int unitAmount) { this.unitAmount = unitAmount; }
    public void setBuyCapacity(int buyCapacity) { this.buyCapacity = buyCapacity; }
    public void setTradeLimit(Integer tradeLimit) { this.tradeLimit = tradeLimit; }
    public void setLimitScope(LimitScope limitScope) { this.limitScope = limitScope; }
    public void setResetPeriod(Duration resetPeriod) { this.resetPeriod = resetPeriod; }
}
