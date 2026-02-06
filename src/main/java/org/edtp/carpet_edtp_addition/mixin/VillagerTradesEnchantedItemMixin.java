package org.edtp.carpet_edtp_addition.mixin;

import net.minecraft.village.TradeOffers;
import org.edtp.carpet_edtp_addition.CarpetEdtpAdditionSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(TradeOffers.SellEnchantedToolFactory.class)
public class VillagerTradesEnchantedItemMixin {
    
    @ModifyConstant(
        method = "create",
        constant = @Constant(intValue = 15)
    )
    private int modifyEnchantmentRange(int original) {
        int level = CarpetEdtpAdditionSettings.getBoostTradeEnchantsLevel();
        if (level >= 3) {
            return 46;
        }
        if (level >= 2) {
            return 27;
        }
        return original;
    }
}
