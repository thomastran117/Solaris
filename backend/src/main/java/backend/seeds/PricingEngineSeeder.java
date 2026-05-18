package backend.seeds;

import backend.models.core.*;
import backend.models.enums.*;
import backend.repositories.*;
import backend.seeds.BundleSeeder.SeededBundles;
import backend.seeds.CompanySeeder.SeededCompanies;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds the pricing engine for dev:
 *   - 1 LoyaltyPolicy + 3 LoyaltyTiers per company (5 programs total)
 *   - 5 PromotionRules per company, each covering distinct products, one of
 *     every rule type: PERCENTAGE_OFF, FIXED_OFF, BOGO, TIERED_PRICE, FREE_SHIPPING
 *   - 2 Coupons per company (10 total)
 *
 * Each company's rules target exactly 10 products.
 *
 * configJson strings match the field names from the pricing engine's typed
 * config records (PercentageOffConfig, FixedOffConfig, BogoConfig,
 * TieredPriceConfig, FreeShippingConfig).
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class PricingEngineSeeder {

    private final PromotionRuleRepository promotionRuleRepository;
    private final CouponRepository couponRepository;
    private final LoyaltyPolicyRepository loyaltyPolicyRepository;
    private final LoyaltyTierRepository loyaltyTierRepository;

    public void seed(SeededCompanies co,
                     List<Product> tech, List<Product> style, List<Product> wellness,
                     List<Product> home, List<Product> sport,
                     SeededBundles bundles) {
        seedLoyaltyPrograms(co);
        seedTech(co.tech(), tech);
        seedStyle(co.style(), style);
        seedWellness(co.wellness(), wellness);
        seedHome(co.home(), home);
        seedSport(co.sport(), sport);
        seedBundleRules(co, bundles);
        seedCoupons(co);
    }

    // =========================================================================
    // Loyalty programs — 1 policy + 3 tiers per company
    // =========================================================================

    private void seedLoyaltyPrograms(SeededCompanies co) {
        loyalty(co.tech().getId(),
                "TechGadgets Rewards",
                new BigDecimal("2.0000"), 1, 100, null, 250, 0,
                BigDecimal.ZERO, LoyaltyEarnMode.POINTS,
                new Object[][]{
                    {"Bronze", 0L,    new BigDecimal("1.0000"), "#CD7F32", "{\"perks\":[\"Early access to sales\"]}",            0},
                    {"Silver", 500L,  new BigDecimal("1.2500"), "#C0C0C0", "{\"perks\":[\"Free standard shipping\",\"Priority support\"]}", 1},
                    {"Gold",   2000L, new BigDecimal("1.5000"), "#FFD700", "{\"perks\":[\"Free express shipping\",\"Exclusive member pricing\",\"Birthday double points\"]}", 2},
                });

        loyalty(co.style().getId(),
                "StyleHub Insider",
                new BigDecimal("1.5000"), 1, 100, null, 200, 0,
                BigDecimal.ZERO, LoyaltyEarnMode.POINTS,
                new Object[][]{
                    {"Bronze", 0L,    new BigDecimal("1.0000"), "#CD7F32", "{\"perks\":[\"Member-only flash sales\"]}",           0},
                    {"Silver", 750L,  new BigDecimal("1.2000"), "#C0C0C0", "{\"perks\":[\"Free returns\",\"Early collection access\"]}", 1},
                    {"Gold",   2500L, new BigDecimal("1.5000"), "#FFD700", "{\"perks\":[\"Personal stylist chat\",\"Free express shipping\",\"Exclusive drops\"]}", 2},
                });

        loyalty(co.wellness().getId(),
                "WellnessWorld Club",
                new BigDecimal("2.0000"), 1, 50, 365, 300, 0,
                new BigDecimal("0.0050"), LoyaltyEarnMode.BOTH,
                new Object[][]{
                    {"Bronze", 0L,    new BigDecimal("1.0000"), "#CD7F32", "{\"perks\":[\"0.5% cashback\",\"Recipe guides\"]}",     0},
                    {"Silver", 500L,  new BigDecimal("1.3000"), "#C0C0C0", "{\"perks\":[\"0.5% cashback\",\"Free nutrition consultation\"]}", 1},
                    {"Gold",   1500L, new BigDecimal("1.6000"), "#FFD700", "{\"perks\":[\"0.5% cashback\",\"Free product samples\",\"Annual wellness kit\"]}", 2},
                });

        loyalty(co.home().getId(),
                "HomeNest Circle",
                new BigDecimal("1.0000"), 1, 100, null, 150, 0,
                BigDecimal.ZERO, LoyaltyEarnMode.POINTS,
                new Object[][]{
                    {"Bronze", 0L,    new BigDecimal("1.0000"), "#CD7F32", "{\"perks\":[\"Member newsletter & design tips\"]}", 0},
                    {"Silver", 600L,  new BigDecimal("1.2000"), "#C0C0C0", "{\"perks\":[\"Free standard shipping\",\"Interior design guide\"]}",     1},
                    {"Gold",   2000L, new BigDecimal("1.4000"), "#FFD700", "{\"perks\":[\"Free express shipping\",\"Room design consultation\",\"VIP launch invites\"]}", 2},
                });

        loyalty(co.sport().getId(),
                "SportZone Pro",
                new BigDecimal("3.0000"), 1, 100, 180, 500, 0,
                new BigDecimal("0.0100"), LoyaltyEarnMode.BOTH,
                new Object[][]{
                    {"Bronze", 0L,    new BigDecimal("1.0000"), "#CD7F32", "{\"perks\":[\"1% cashback\",\"Training tips newsletter\"]}",   0},
                    {"Silver", 1000L, new BigDecimal("1.2500"), "#C0C0C0", "{\"perks\":[\"1% cashback\",\"Free standard shipping\",\"Athlete discount\"]}",  1},
                    {"Gold",   3000L, new BigDecimal("1.7500"), "#FFD700", "{\"perks\":[\"1% cashback\",\"Free express shipping\",\"Early product drops\",\"Free nutrition coaching\"]}", 2},
                });
    }

    // =========================================================================
    // TechGadgets Co. — 5 rules covering 10 products
    //
    //   Rule 1 PERCENTAGE_OFF 15%     → [0] Headphones, [7] Earbuds
    //   Rule 2 FIXED_OFF $20          → [1] Smart Watch, [27] Portable Monitor
    //   Rule 3 BOGO buy-2-get-1-50%   → [2] BT Speaker
    //   Rule 4 TIERED_PRICE           → [11] Portable SSD
    //   Rule 5 FREE_SHIPPING          → [5] Webcam, [8] Smart Hub, [18] Mesh WiFi, [45] VR Headset
    // =========================================================================

    private void seedTech(Company co, List<Product> p) {
        if (hasRules(co)) return;

        rule(co, "Audio Line — 15% Off",
                "15% off all headphones and earbuds.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":15.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                false, 50, null, null, null,
                Set.of(p.get(0), p.get(7)));

        rule(co, "Display Tech — $20 Off",
                "$20 off when you spend $200+ on smart watches or portable monitors.",
                PromotionRuleType.FIXED_OFF,
                "{\"amount\":20.00,\"appliesTo\":\"ORDER\"}",
                false, 60, new BigDecimal("200.00"), null, null,
                Set.of(p.get(1), p.get(27)));

        rule(co, "Speaker Bundle — Buy 2, Get 1 Half Price",
                "Add 3 Bluetooth speakers and the cheapest is 50% off.",
                PromotionRuleType.BOGO,
                "{\"triggerProductIds\":[],\"triggerQty\":2,\"rewardProductIds\":[]," +
                "\"rewardQty\":1,\"rewardPercentOff\":50.00,\"maxApplicationsPerOrder\":2}",
                true, 40, null, null, null,
                Set.of(p.get(2)));

        rule(co, "Portable SSD — Bulk Pricing",
                "Buy more, save more on the Portable SSD 1TB.",
                PromotionRuleType.TIERED_PRICE,
                "{\"breakpoints\":[" +
                "{\"minQty\":1,\"unitPrice\":109.99}," +
                "{\"minQty\":3,\"unitPrice\":94.99}," +
                "{\"minQty\":5,\"unitPrice\":84.99}]}",
                true, 30, null, null, null,
                Set.of(p.get(11)));

        rule(co, "Premium Tech — Free Shipping",
                "Free shipping on webcams, smart hubs, mesh routers, and VR headsets.",
                PromotionRuleType.FREE_SHIPPING,
                "{\"maxShippingDiscount\":null,\"requiresAllTargetProducts\":false}",
                true, 100, null, null, null,
                Set.of(p.get(5), p.get(8), p.get(18), p.get(45)));
    }

    // =========================================================================
    // StyleHub — 5 rules covering 10 products
    //
    //   Rule 1 PERCENTAGE_OFF 20%     → [0] Organic Tee, [37] Graphic Tee
    //   Rule 2 BOGO buy-2-get-1-free  → [2] Zip Hoodie
    //   Rule 3 TIERED_PRICE           → [6] Yoga Leggings
    //   Rule 4 FIXED_OFF $25          → [7] Wool Coat, [43] Oversized Blazer
    //   Rule 5 FREE_SHIPPING          → [12] Chinos, [22] Chelsea Boots, [26] Bomber, [48] Silk PJ Set
    // =========================================================================

    private void seedStyle(Company co, List<Product> p) {
        if (hasRules(co)) return;

        rule(co, "Tee Sale — 20% Off",
                "20% off the Premium Organic Tee and Vintage Wash Graphic Tee.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":20.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                false, 50, null, null, null,
                Set.of(p.get(0), p.get(37)));

        rule(co, "Hoodie Triple Deal — Buy 2 Get 1 Free",
                "Add 3 Heavyweight Zip Hoodies and the cheapest is free.",
                PromotionRuleType.BOGO,
                "{\"triggerProductIds\":[],\"triggerQty\":2,\"rewardProductIds\":[]," +
                "\"rewardQty\":1,\"rewardPercentOff\":100.00,\"maxApplicationsPerOrder\":2}",
                true, 40, null, null, null,
                Set.of(p.get(2)));

        rule(co, "Yoga Leggings — Multi-Pack Pricing",
                "The more you buy, the less you pay per pair of Yoga Leggings.",
                PromotionRuleType.TIERED_PRICE,
                "{\"breakpoints\":[" +
                "{\"minQty\":1,\"unitPrice\":49.99}," +
                "{\"minQty\":2,\"unitPrice\":44.99}," +
                "{\"minQty\":4,\"unitPrice\":39.99}]}",
                true, 30, null, null, null,
                Set.of(p.get(6)));

        rule(co, "Outerwear Saving — $25 Off",
                "$25 off the Wool Blend Coat or Oversized Blazer on orders over $150.",
                PromotionRuleType.FIXED_OFF,
                "{\"amount\":25.00,\"appliesTo\":\"ORDER\"}",
                false, 60, new BigDecimal("150.00"), null, null,
                Set.of(p.get(7), p.get(43)));

        rule(co, "Style Essentials — Free Shipping",
                "Free shipping when your cart includes Chino Trousers, Chelsea Boots, Bomber Jacket, or Silk PJ Set.",
                PromotionRuleType.FREE_SHIPPING,
                "{\"maxShippingDiscount\":null,\"requiresAllTargetProducts\":false}",
                true, 100, null, null, null,
                Set.of(p.get(12), p.get(22), p.get(26), p.get(48)));
    }

    // =========================================================================
    // WellnessWorld — 5 rules covering 10 products
    //
    //   Rule 1 PERCENTAGE_OFF 10%     → [1] Vit C Serum, [33] HA Serum
    //   Rule 2 BOGO buy-2-get-1-free  → [43] Herbal Sleep Tea
    //   Rule 3 TIERED_PRICE           → [0] Whey Protein
    //   Rule 4 FIXED_OFF $15          → [5] Yoga Mat, [22] Magnesium
    //   Rule 5 FREE_SHIPPING          → [10] Sleep Gummies, [25] Pre-Workout, [27] Creatine, [49] Mushroom Blend
    // =========================================================================

    private void seedWellness(Company co, List<Product> p) {
        if (hasRules(co)) return;

        rule(co, "Skincare Duo — 10% Off",
                "10% off the Vitamin C Brightening Serum and Hyaluronic Acid Serum.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":10.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 50, null, null, null,
                Set.of(p.get(1), p.get(33)));

        rule(co, "Sleep Tea — Buy 2 Get 1 Free",
                "Stock up on Herbal Sleep Tea and get every third box free.",
                PromotionRuleType.BOGO,
                "{\"triggerProductIds\":[],\"triggerQty\":2,\"rewardProductIds\":[]," +
                "\"rewardQty\":1,\"rewardPercentOff\":100.00,\"maxApplicationsPerOrder\":3}",
                true, 40, null, null, null,
                Set.of(p.get(43)));

        rule(co, "Whey Protein — Volume Discount",
                "Better value when you buy more: price drops per unit as quantity increases.",
                PromotionRuleType.TIERED_PRICE,
                "{\"breakpoints\":[" +
                "{\"minQty\":1,\"unitPrice\":54.99}," +
                "{\"minQty\":2,\"unitPrice\":49.99}," +
                "{\"minQty\":3,\"unitPrice\":44.99}]}",
                true, 30, null, null, null,
                Set.of(p.get(0)));

        rule(co, "Wellness Pack — $15 Off",
                "$15 off when your cart includes the Yoga Mat or Magnesium Glycinate (min $50).",
                PromotionRuleType.FIXED_OFF,
                "{\"amount\":15.00,\"appliesTo\":\"ORDER\"}",
                false, 60, new BigDecimal("50.00"), null, null,
                Set.of(p.get(5), p.get(22)));

        rule(co, "Supplement Stack — Free Shipping",
                "Free shipping on Sleep Gummies, Pre-Workout, Creatine, and Mushroom Blend.",
                PromotionRuleType.FREE_SHIPPING,
                "{\"maxShippingDiscount\":null,\"requiresAllTargetProducts\":false}",
                true, 100, null, null, null,
                Set.of(p.get(10), p.get(25), p.get(27), p.get(49)));
    }

    // =========================================================================
    // HomeNest Co. — 5 rules covering 10 products
    //
    //   Rule 1 PERCENTAGE_OFF 12%     → [0] Smart LED Bulbs, [2] Smart Thermostat
    //   Rule 2 BOGO buy-2-get-1-50%   → [20] Artisan Soy Candle Trio
    //   Rule 3 TIERED_PRICE           → [10] Ceramic Pour-Over Set
    //   Rule 4 FIXED_OFF $30          → [22] Organic Duvet, [23] Bamboo Sheets
    //   Rule 5 FREE_SHIPPING          → [16] Chef's Knife, [24] Weighted Blanket, [35] Spice Rack, [43] LED Floor Lamp
    // =========================================================================

    private void seedHome(Company co, List<Product> p) {
        if (hasRules(co)) return;

        rule(co, "Smart Home — 12% Off",
                "12% off Smart LED Bulbs and the Smart Thermostat.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":12.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                false, 50, null, null, null,
                Set.of(p.get(0), p.get(2)));

        rule(co, "Candle Bundle — Buy 2 Get 1 Half Price",
                "Buy 3 Artisan Soy Candle Trios and get the third at 50% off.",
                PromotionRuleType.BOGO,
                "{\"triggerProductIds\":[],\"triggerQty\":2,\"rewardProductIds\":[]," +
                "\"rewardQty\":1,\"rewardPercentOff\":50.00,\"maxApplicationsPerOrder\":3}",
                true, 40, null, null, null,
                Set.of(p.get(20)));

        rule(co, "Pour-Over Set — Gifting Discount",
                "Buy multiple Ceramic Pour-Over Coffee Sets (perfect for gifting) and save per unit.",
                PromotionRuleType.TIERED_PRICE,
                "{\"breakpoints\":[" +
                "{\"minQty\":1,\"unitPrice\":64.99}," +
                "{\"minQty\":2,\"unitPrice\":59.99}," +
                "{\"minQty\":3,\"unitPrice\":54.99}]}",
                true, 30, null, null, null,
                Set.of(p.get(10)));

        rule(co, "Bedroom Bundle — $30 Off",
                "$30 off when you buy the Organic Cotton Duvet Cover and Bamboo Lyocell Sheet Set together (min $150).",
                PromotionRuleType.FIXED_OFF,
                "{\"amount\":30.00,\"appliesTo\":\"ORDER\"}",
                false, 60, new BigDecimal("150.00"), null, null,
                Set.of(p.get(22), p.get(23)));

        rule(co, "Home Essentials — Free Shipping",
                "Free shipping on the Chef's Knife, Weighted Blanket, Rotating Spice Rack, and LED Floor Lamp.",
                PromotionRuleType.FREE_SHIPPING,
                "{\"maxShippingDiscount\":null,\"requiresAllTargetProducts\":false}",
                true, 100, null, null, null,
                Set.of(p.get(16), p.get(24), p.get(35), p.get(43)));
    }

    // =========================================================================
    // SportZone — 5 rules covering 10 products
    //
    //   Rule 1 PERCENTAGE_OFF 15%     → [0] Running Shorts, [1] Training Leggings
    //   Rule 2 BOGO buy-2-get-1-50%   → [22] Whey Isolate, [23] BCAA, [25] Creatine
    //   Rule 3 TIERED_PRICE           → [22] Whey Isolate (qty-based unit price)
    //   Rule 4 FIXED_OFF $50          → [13] Adjustable Dumbbells, [31] GPS Watch
    //   Rule 5 FREE_SHIPPING          → [11] Pro Yoga Mat, [40] Gym Duffle, [41] Water Bottle
    // =========================================================================

    private void seedSport(Company co, List<Product> p) {
        if (hasRules(co)) return;

        rule(co, "Kit Up — 15% Off Activewear",
                "15% off Elite Running Shorts and Pro Training Leggings.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":15.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                false, 50, null, null, null,
                Set.of(p.get(0), p.get(1)));

        rule(co, "Nutrition Stack — Buy 2 Get 1 Half Price",
                "Mix and match Whey Isolate, BCAAs, and Creatine — every third item is 50% off.",
                PromotionRuleType.BOGO,
                "{\"triggerProductIds\":[],\"triggerQty\":2,\"rewardProductIds\":[]," +
                "\"rewardQty\":1,\"rewardPercentOff\":50.00,\"maxApplicationsPerOrder\":3}",
                true, 40, null, null, null,
                Set.of(p.get(22), p.get(23), p.get(25)));

        rule(co, "Whey Isolate — Bulk Savings",
                "Unit price drops as you buy more tubs of Whey Isolate Protein CFM.",
                PromotionRuleType.TIERED_PRICE,
                "{\"breakpoints\":[" +
                "{\"minQty\":1,\"unitPrice\":59.99}," +
                "{\"minQty\":2,\"unitPrice\":54.99}," +
                "{\"minQty\":3,\"unitPrice\":49.99}]}",
                false, 25, null, null, null,
                Set.of(p.get(22)));

        rule(co, "Premium Gear — $50 Off",
                "$50 off when you spend $300+ on Adjustable Dumbbells or the GPS Running Watch.",
                PromotionRuleType.FIXED_OFF,
                "{\"amount\":50.00,\"appliesTo\":\"ORDER\"}",
                false, 60, new BigDecimal("300.00"), null, null,
                Set.of(p.get(13), p.get(31)));

        rule(co, "Training Essentials — Free Shipping",
                "Free shipping on the Pro Yoga Mat, Gym Duffle Bag, and Insulated Water Bottle.",
                PromotionRuleType.FREE_SHIPPING,
                "{\"maxShippingDiscount\":null,\"requiresAllTargetProducts\":false}",
                true, 100, null, null, null,
                Set.of(p.get(11), p.get(40), p.get(41)));
    }

    // =========================================================================
    // Bundle-scoped promotion rules — 5 per company (1 per bundle), 25 total
    //
    //  Bundle list index order matches BundleSeeder creation order:
    //
    //  TechGadgets [0] Work From Home Pro      PERCENTAGE_OFF 10%
    //              [1] Gaming Lair Setup        FIXED_OFF $15
    //              [2] Smart Home Starter Kit   PERCENTAGE_OFF 12%
    //              [3] Audiophile Collection    FREE_SHIPPING
    //              [4] Creator Stream Bundle    PERCENTAGE_OFF 8%
    //
    //  StyleHub    [0] Weekend Casual Set       PERCENTAGE_OFF 15%
    //              [1] Active Life Bundle       PERCENTAGE_OFF 10%
    //              [2] Smart Business Look      FIXED_OFF $25
    //              [3] Cosy Home Set            PERCENTAGE_OFF 10%
    //              [4] Festival Looks Bundle    FREE_SHIPPING
    //
    //  Wellness    [0] Morning Routine Starter  PERCENTAGE_OFF 10%
    //              [1] Skincare Ritual Bundle   PERCENTAGE_OFF 12%
    //              [2] Recovery & Rest Pack     FREE_SHIPPING
    //              [3] Sports Performance Stack PERCENTAGE_OFF 15%
    //              [4] Home Wellness Sanctuary  FIXED_OFF $15
    //
    //  HomeNest    [0] Smart Home Security Pack FIXED_OFF $20
    //              [1] Kitchen Starter Kit      PERCENTAGE_OFF 10%
    //              [2] Bedroom Dream Setup      FREE_SHIPPING
    //              [3] Home Fragrance Collection PERCENTAGE_OFF 8%
    //              [4] Living Room Refresh      FIXED_OFF $20
    //
    //  SportZone   [0] Runner's Complete Kit    PERCENTAGE_OFF 10%
    //              [1] Home Gym Starter         PERCENTAGE_OFF 12%
    //              [2] Nutrition Fundamentals   FIXED_OFF $15
    //              [3] Powerlifting Pack        FREE_SHIPPING
    //              [4] Training Day Bundle      PERCENTAGE_OFF 10%
    // =========================================================================

    private void seedBundleRules(SeededCompanies co, SeededBundles bundles) {
        // TechGadgets
        ruleForBundle(co.tech(), "Work From Home Pro — 10% Bundle Discount",
                "Save 10% when you buy the Work From Home Pro bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":10.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.tech().get(0));

        ruleForBundle(co.tech(), "Gaming Lair Setup — $15 Off",
                "$15 off the Gaming Lair Setup bundle.",
                PromotionRuleType.FIXED_OFF,
                "{\"amount\":15.00,\"appliesTo\":\"ORDER\"}",
                true, 20, null,
                bundles.tech().get(1));

        ruleForBundle(co.tech(), "Smart Home Starter Kit — 12% Bundle Discount",
                "Save 12% when you buy the Smart Home Starter Kit bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":12.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.tech().get(2));

        ruleForBundle(co.tech(), "Audiophile Collection — Free Shipping",
                "Free shipping on the Audiophile Collection bundle.",
                PromotionRuleType.FREE_SHIPPING,
                "{\"maxShippingDiscount\":null,\"requiresAllTargetProducts\":false}",
                true, 20, null,
                bundles.tech().get(3));

        ruleForBundle(co.tech(), "Creator Stream Bundle — 8% Bundle Discount",
                "Save 8% when you buy the Creator Stream Bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":8.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.tech().get(4));

        // StyleHub
        ruleForBundle(co.style(), "Weekend Casual Set — 15% Bundle Discount",
                "Save 15% when you buy the Weekend Casual Set bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":15.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.style().get(0));

        ruleForBundle(co.style(), "Active Life Bundle — 10% Bundle Discount",
                "Save 10% when you buy the Active Life Bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":10.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.style().get(1));

        ruleForBundle(co.style(), "Smart Business Look — $25 Off",
                "$25 off the Smart Business Look bundle.",
                PromotionRuleType.FIXED_OFF,
                "{\"amount\":25.00,\"appliesTo\":\"ORDER\"}",
                true, 20, null,
                bundles.style().get(2));

        ruleForBundle(co.style(), "Cosy Home Set — 10% Bundle Discount",
                "Save 10% when you buy the Cosy Home Set bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":10.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.style().get(3));

        ruleForBundle(co.style(), "Festival Looks Bundle — Free Shipping",
                "Free shipping on the Festival Looks Bundle.",
                PromotionRuleType.FREE_SHIPPING,
                "{\"maxShippingDiscount\":null,\"requiresAllTargetProducts\":false}",
                true, 20, null,
                bundles.style().get(4));

        // WellnessWorld
        ruleForBundle(co.wellness(), "Morning Routine Starter — 10% Bundle Discount",
                "Save 10% when you buy the Morning Routine Starter bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":10.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.wellness().get(0));

        ruleForBundle(co.wellness(), "Skincare Ritual Bundle — 12% Bundle Discount",
                "Save 12% when you buy the Skincare Ritual Bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":12.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.wellness().get(1));

        ruleForBundle(co.wellness(), "Recovery & Rest Pack — Free Shipping",
                "Free shipping on the Recovery & Rest Pack bundle.",
                PromotionRuleType.FREE_SHIPPING,
                "{\"maxShippingDiscount\":null,\"requiresAllTargetProducts\":false}",
                true, 20, null,
                bundles.wellness().get(2));

        ruleForBundle(co.wellness(), "Sports Performance Stack — 15% Bundle Discount",
                "Save 15% when you buy the Sports Performance Stack bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":15.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.wellness().get(3));

        ruleForBundle(co.wellness(), "Home Wellness Sanctuary — $15 Off",
                "$15 off the Home Wellness Sanctuary bundle.",
                PromotionRuleType.FIXED_OFF,
                "{\"amount\":15.00,\"appliesTo\":\"ORDER\"}",
                true, 20, null,
                bundles.wellness().get(4));

        // HomeNest
        ruleForBundle(co.home(), "Smart Home Security Pack — $20 Off",
                "$20 off when you buy the Smart Home Security Pack bundle.",
                PromotionRuleType.FIXED_OFF,
                "{\"amount\":20.00,\"appliesTo\":\"ORDER\"}",
                true, 20, null,
                bundles.home().get(0));

        ruleForBundle(co.home(), "Kitchen Starter Kit — 10% Bundle Discount",
                "Save 10% when you buy the Kitchen Starter Kit bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":10.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.home().get(1));

        ruleForBundle(co.home(), "Bedroom Dream Setup — Free Shipping",
                "Free shipping on the Bedroom Dream Setup bundle.",
                PromotionRuleType.FREE_SHIPPING,
                "{\"maxShippingDiscount\":null,\"requiresAllTargetProducts\":false}",
                true, 20, null,
                bundles.home().get(2));

        ruleForBundle(co.home(), "Home Fragrance Collection — 8% Bundle Discount",
                "Save 8% when you buy the Home Fragrance Collection bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":8.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.home().get(3));

        ruleForBundle(co.home(), "Living Room Refresh — $20 Off",
                "$20 off the Living Room Refresh bundle.",
                PromotionRuleType.FIXED_OFF,
                "{\"amount\":20.00,\"appliesTo\":\"ORDER\"}",
                true, 20, null,
                bundles.home().get(4));

        // SportZone
        ruleForBundle(co.sport(), "Runner's Complete Kit — 10% Bundle Discount",
                "Save 10% when you buy the Runner's Complete Kit bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":10.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.sport().get(0));

        ruleForBundle(co.sport(), "Home Gym Starter — 12% Bundle Discount",
                "Save 12% when you buy the Home Gym Starter bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":12.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.sport().get(1));

        ruleForBundle(co.sport(), "Nutrition Fundamentals — $15 Off",
                "$15 off the Nutrition Fundamentals bundle.",
                PromotionRuleType.FIXED_OFF,
                "{\"amount\":15.00,\"appliesTo\":\"ORDER\"}",
                true, 20, null,
                bundles.sport().get(2));

        ruleForBundle(co.sport(), "Powerlifting Pack — Free Shipping",
                "Free shipping on the Powerlifting Pack bundle.",
                PromotionRuleType.FREE_SHIPPING,
                "{\"maxShippingDiscount\":null,\"requiresAllTargetProducts\":false}",
                true, 20, null,
                bundles.sport().get(3));

        ruleForBundle(co.sport(), "Training Day Bundle — 10% Bundle Discount",
                "Save 10% when you buy the Training Day Bundle.",
                PromotionRuleType.PERCENTAGE_OFF,
                "{\"percent\":10.00,\"maxDiscount\":null,\"appliesTo\":\"LINE\"}",
                true, 20, null,
                bundles.sport().get(4));
    }

    // =========================================================================
    // Coupons — 2 per company
    // =========================================================================

    private void seedCoupons(SeededCompanies co) {
        // TechGadgets
        coupon(co.tech(), "TECH20",    "TechGadgets 20% Off",   DiscountType.PERCENTAGE,   new BigDecimal("20.00"), new BigDecimal("100.00"), 500,  1);
        coupon(co.tech(), "TECHSAVE",  "TechGadgets $30 Off",   DiscountType.FIXED_AMOUNT,  new BigDecimal("30.00"), new BigDecimal("200.00"), 200,  1);

        // StyleHub
        coupon(co.style(), "STYLE15",  "StyleHub 15% Off",      DiscountType.PERCENTAGE,   new BigDecimal("15.00"), new BigDecimal("50.00"),  500,  1);
        coupon(co.style(), "NEWLOOK",  "StyleHub $20 Off",      DiscountType.FIXED_AMOUNT,  new BigDecimal("20.00"), new BigDecimal("100.00"), 300,  1);

        // WellnessWorld
        coupon(co.wellness(), "WELL10",     "WellnessWorld 10% Off", DiscountType.PERCENTAGE,   new BigDecimal("10.00"), new BigDecimal("40.00"),  500,  1);
        coupon(co.wellness(), "WELLNESS20", "WellnessWorld $20 Off", DiscountType.FIXED_AMOUNT,  new BigDecimal("20.00"), new BigDecimal("80.00"),  250,  1);

        // HomeNest
        coupon(co.home(), "NEST15",    "HomeNest 15% Off",      DiscountType.PERCENTAGE,   new BigDecimal("15.00"), new BigDecimal("60.00"),  400,  1);
        coupon(co.home(), "HOMEWARM",  "HomeNest $25 Off",      DiscountType.FIXED_AMOUNT,  new BigDecimal("25.00"), new BigDecimal("120.00"), 200,  1);

        // SportZone
        coupon(co.sport(), "SPORT20",  "SportZone 20% Off",     DiscountType.PERCENTAGE,   new BigDecimal("20.00"), new BigDecimal("50.00"),  600,  1);
        coupon(co.sport(), "ATHLETE",  "SportZone $35 Off",     DiscountType.FIXED_AMOUNT,  new BigDecimal("35.00"), new BigDecimal("150.00"), 300,  1);
    }

    // =========================================================================
    // Entity builders
    // =========================================================================

    private void loyalty(UUID companyId, String name,
                         BigDecimal earnRate, int pointValueCents, int minRedemption,
                         Integer expiryDays, int birthdayBonus, int birthdayCreditCents,
                         BigDecimal cashbackRate, LoyaltyEarnMode mode,
                         Object[][] tiers) {
        if (loyaltyPolicyRepository.findFirstByCompanyIdAndActiveTrue(companyId).isPresent()) return;

        LoyaltyPolicy policy = new LoyaltyPolicy();
        policy.setCompanyId(companyId);
        policy.setName(name);
        policy.setEarnRatePerDollar(earnRate);
        policy.setPointValueCents(pointValueCents);
        policy.setMinRedemptionPoints(minRedemption);
        policy.setPointsExpiryDays(expiryDays);
        policy.setBirthdayBonusPoints(birthdayBonus);
        policy.setBirthdayBonusCreditCents(birthdayCreditCents);
        policy.setCashbackRatePercent(cashbackRate);
        policy.setEarnMode(mode);
        policy.setActive(true);
        loyaltyPolicyRepository.save(policy);

        for (Object[] t : tiers) {
            LoyaltyTier tier = new LoyaltyTier();
            tier.setCompanyId(companyId);
            tier.setName((String) t[0]);
            tier.setMinPoints((Long) t[1]);
            tier.setEarnMultiplier((BigDecimal) t[2]);
            tier.setBadgeColor((String) t[3]);
            tier.setPerksJson((String) t[4]);
            tier.setDisplayOrder((Integer) t[5]);
            loyaltyTierRepository.save(tier);
        }
    }

    private void rule(Company co, String name, String description,
                      PromotionRuleType type, String configJson,
                      boolean stackable, int priority,
                      BigDecimal minCart, Integer maxUses, Integer maxUsesPerUser,
                      Set<Product> targets) {
        PromotionRule r = new PromotionRule();
        r.setCompany(co);
        r.setName(name);
        r.setDescription(description);
        r.setRuleType(type);
        r.setConfigJson(configJson);
        r.setStatus(DiscountStatus.ACTIVE);
        r.setStackable(stackable);
        r.setPriority(priority);
        r.setMinCartAmount(minCart);
        r.setMaxUses(maxUses);
        r.setMaxUsesPerUser(maxUsesPerUser);
        r.setTargetProducts(targets);
        promotionRuleRepository.save(r);
    }

    private void ruleForBundle(Company co, String name, String description,
                               PromotionRuleType type, String configJson,
                               boolean stackable, int priority,
                               BigDecimal minCart,
                               ProductBundle targetBundle) {
        if (promotionRuleRepository.findAllByCompanyId(co.getId(), Pageable.ofSize(100))
                .stream().anyMatch(r -> r.getName().equals(name))) return;
        PromotionRule r = new PromotionRule();
        r.setCompany(co);
        r.setName(name);
        r.setDescription(description);
        r.setRuleType(type);
        r.setConfigJson(configJson);
        r.setStatus(DiscountStatus.ACTIVE);
        r.setStackable(stackable);
        r.setPriority(priority);
        r.setMinCartAmount(minCart);
        r.setTargetBundles(new java.util.HashSet<>(java.util.Set.of(targetBundle)));
        promotionRuleRepository.save(r);
    }

    private void coupon(Company co, String code, String name,
                        DiscountType type, BigDecimal value,
                        BigDecimal minOrderAmount, Integer maxUses, Integer maxUsesPerUser) {
        if (couponRepository.existsByCodeIgnoreCase(code)) return;
        Coupon c = new Coupon();
        c.setCompany(co);
        c.setCode(code.toUpperCase());
        c.setName(name);
        c.setType(type);
        c.setValue(value);
        c.setStatus(DiscountStatus.ACTIVE);
        c.setMinOrderAmount(minOrderAmount);
        c.setMaxUses(maxUses);
        c.setMaxUsesPerUser(maxUsesPerUser);
        couponRepository.save(c);
    }

    private boolean hasRules(Company co) {
        return promotionRuleRepository
                .findAllByCompanyId(co.getId(), Pageable.ofSize(1))
                .hasContent();
    }
}
