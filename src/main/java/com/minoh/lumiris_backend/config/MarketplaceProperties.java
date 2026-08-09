package com.minoh.lumiris_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Règles temporelles du cycle de vie d'une commande. `returnWindowDays` porte le droit de
// rétractation (14 jours calendaires, art. L221-18 du code de la consommation) ; les deux autres
// bornent l'attente quand ni l'acheteur ni le transporteur ne confirment rien.
@Component
@ConfigurationProperties(prefix = "app.marketplace")
public class MarketplaceProperties {

    private int returnWindowDays = 14;
    private int autoDeliverAfterShipDays = 7;
    private int autoCompleteAfterDeliveryDays = 14;
    private double commissionRate = 0.05;

    public int getReturnWindowDays() {
        return returnWindowDays;
    }

    public void setReturnWindowDays(int returnWindowDays) {
        this.returnWindowDays = returnWindowDays;
    }

    public int getAutoDeliverAfterShipDays() {
        return autoDeliverAfterShipDays;
    }

    public void setAutoDeliverAfterShipDays(int autoDeliverAfterShipDays) {
        this.autoDeliverAfterShipDays = autoDeliverAfterShipDays;
    }

    public int getAutoCompleteAfterDeliveryDays() {
        return autoCompleteAfterDeliveryDays;
    }

    public void setAutoCompleteAfterDeliveryDays(int autoCompleteAfterDeliveryDays) {
        this.autoCompleteAfterDeliveryDays = autoCompleteAfterDeliveryDays;
    }

    public double getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(double commissionRate) {
        this.commissionRate = commissionRate;
    }

    public Duration returnWindow() {
        return Duration.ofDays(returnWindowDays);
    }

    public Duration autoDeliverDelay() {
        return Duration.ofDays(autoDeliverAfterShipDays);
    }

    public Duration autoCompleteDelay() {
        return Duration.ofDays(autoCompleteAfterDeliveryDays);
    }
}
