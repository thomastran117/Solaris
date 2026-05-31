package backend.seeds;

import backend.models.core.Company;
import backend.models.core.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class WellnessWorldProductSeeder {

    private final ProductSeedHelper h;

    public List<Product> seed(Company co) {
        List<Product> list = new ArrayList<>();

        list.add(h.product(co, "Whey Protein Powder",
                "Cold-processed whey isolate with 27g protein per serving. No artificial sweeteners. Informed Sport certified.",
                "WELL-WPP-001", "54.99", "69.99", "Nutrition", "NutriForge",
                "https://placehold.co/800x800/d6eaf8/333333?text=Whey+Protein",
                150, 10, true, true, false, "MONTHLY:1,WEEKLY:4", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/d6eaf8/333333?text=Whey+Protein",
                        "https://placehold.co/800x800/c2d9f0/333333?text=Protein+Label"),
                h.attrs("Protein", "27g per serving",
                        "Servings", "30 per container",
                        "Certification", "Informed Sport",
                        "Sweetener", "Stevia, no artificial sweeteners"),
                h.options2("Flavor", "Chocolate", "Vanilla", "Strawberry", "Size", "1lb", "2lb", "5lb"),
                p -> {
                    h.pv2(p, "Chocolate",  "1lb", "WELL-WPP-CH1", new BigDecimal("34.99"), 50);
                    h.pv2(p, "Chocolate",  "2lb", "WELL-WPP-CH2", new BigDecimal("54.99"), 45);
                    h.pv2(p, "Chocolate",  "5lb", "WELL-WPP-CH5", new BigDecimal("99.99"), 30);
                    h.pv2(p, "Vanilla",    "1lb", "WELL-WPP-VA1", new BigDecimal("34.99"), 40);
                    h.pv2(p, "Vanilla",    "2lb", "WELL-WPP-VA2", new BigDecimal("54.99"), 35);
                    h.pv2(p, "Vanilla",    "5lb", "WELL-WPP-VA5", new BigDecimal("99.99"), 25);
                    h.pv2(p, "Strawberry", "1lb", "WELL-WPP-ST1", new BigDecimal("34.99"), 30);
                    h.pv2(p, "Strawberry", "2lb", "WELL-WPP-ST2", new BigDecimal("54.99"), 25);
                    h.pv2(p, "Strawberry", "5lb", "WELL-WPP-ST5", new BigDecimal("99.99"), 15);
                }));

        list.add(h.product(co, "Vitamin C Brightening Serum",
                "15% L-ascorbic acid with ferulic acid and vitamin E. Brightens skin tone and fights free radicals.",
                "WELL-VCS-001", "39.99", "54.99", "Skincare", "GlowLab",
                "https://placehold.co/800x800/fef9e7/333333?text=Vit+C+Serum",
                120, 10, false, true, false, "MONTHLY:1", new BigDecimal("15.00"),
                h.images("https://placehold.co/800x800/fef9e7/333333?text=Vit+C+Serum",
                        "https://placehold.co/800x800/fdf2d0/333333?text=Serum+Drop"),
                h.attrs("Active", "15% L-Ascorbic Acid",
                        "Additional Actives", "Ferulic Acid, Vitamin E",
                        "pH", "3.0–3.5 (active range)",
                        "Packaging", "Amber glass to prevent oxidation"),
                h.options1("Size", "30ml", "60ml"),
                p -> {
                    h.pv(p, "30ml", "WELL-VCS-30", new BigDecimal("39.99"), 60);
                    h.pv(p, "60ml", "WELL-VCS-60", new BigDecimal("64.99"), 55);
                }));

        list.add(h.product(co, "HEPA Air Purifier 360°",
                "True HEPA H13 filter removes 99.97% of particles ≥0.1µm. Auto mode adjusts to real-time air quality.",
                "WELL-APR-001", "199.99", "249.99", "Home Wellness", "PureAir",
                "https://placehold.co/800x800/eaf4f4/333333?text=Air+Purifier",
                60, 5, true, false, false, null, null,
                h.images("https://placehold.co/800x800/eaf4f4/333333?text=Air+Purifier",
                        "https://placehold.co/800x800/d0ece7/333333?text=Purifier+Filter"),
                h.attrs("Filter", "True HEPA H13",
                        "Coverage", "S:300, M:600, L:900 sq ft",
                        "CADR", "S:200, M:400, L:600 CFM",
                        "Noise", "As low as 24dB on sleep mode"),
                h.options1("Room Size", "Small (up to 300 sq ft)", "Medium (up to 600 sq ft)", "Large (up to 900 sq ft)"),
                p -> {
                    h.pv(p, "Small (up to 300 sq ft)",  "WELL-APR-SM", new BigDecimal("149.99"), 25);
                    h.pv(p, "Medium (up to 600 sq ft)", "WELL-APR-MD", new BigDecimal("199.99"), 20);
                    h.pv(p, "Large (up to 900 sq ft)",  "WELL-APR-LG", new BigDecimal("249.99"), 15);
                }));

        list.add(h.productSingle(co, "Pour-Over Coffee Maker Set",
                "Borosilicate glass dripper, stainless gooseneck kettle, and bamboo server. Makes 1–4 cups.",
                "WELL-PCM-001", "54.99", "74.99", "Kitchen", "BrewCraft",
                "https://placehold.co/800x800/f9f3e3/333333?text=Pour+Over+Set",
                100, 10, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/f9f3e3/333333?text=Pour+Over+Set",
                        "https://placehold.co/800x800/f0e6d3/333333?text=Coffee+Pour"),
                h.attrs("Carafe", "600ml borosilicate glass",
                        "Kettle", "600ml gooseneck stainless",
                        "Filter", "Reusable stainless mesh",
                        "Set Includes", "Dripper, carafe, kettle, stand, 50 paper filters")));

        list.add(h.product(co, "Soy Wax Candle Collection",
                "100% soy wax with cotton wicks and essential oil fragrance blends. 55-hour burn time per candle.",
                "WELL-SWC-001", "29.99", "39.99", "Home Wellness", "WellnessWorld",
                "https://placehold.co/800x800/fdebd0/333333?text=Soy+Candle",
                200, 20, false, true, false, "MONTHLY:1,WEEKLY:4", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/fdebd0/333333?text=Soy+Candle",
                        "https://placehold.co/800x800/fad7a0/333333?text=Candle+Lit"),
                h.attrs("Wax", "100% Natural Soy",
                        "Wick", "Lead-free cotton",
                        "Burn Time", "55 hours",
                        "Volume", "8 oz jar"),
                h.options1("Scent", "Lavender & Cedar", "Citrus & Sage", "Vanilla & Sandalwood", "Eucalyptus & Mint"),
                p -> {
                    h.pv(p, "Lavender & Cedar",      "WELL-SWC-LAV", new BigDecimal("29.99"), 55);
                    h.pv(p, "Citrus & Sage",          "WELL-SWC-CIT", new BigDecimal("29.99"), 50);
                    h.pv(p, "Vanilla & Sandalwood",   "WELL-SWC-VAN", new BigDecimal("29.99"), 60);
                    h.pv(p, "Eucalyptus & Mint",      "WELL-SWC-EUC", new BigDecimal("29.99"), 45);
                }));

        list.add(h.product(co, "Yoga Mat Premium",
                "6mm natural rubber mat with microfiber suede top. Superior grip wet or dry. Alignment printed guides.",
                "WELL-YGM-001", "69.99", "89.99", "Fitness", "ZenFit",
                "https://placehold.co/800x800/7dcea0/ffffff?text=Yoga+Mat",
                100, 8, true, false, false, null, null,
                h.images("https://placehold.co/800x800/7dcea0/ffffff?text=Yoga+Mat",
                        "https://placehold.co/800x800/58d68d/ffffff?text=Mat+Texture"),
                h.attrs("Material", "Natural rubber base + microfiber suede top",
                        "Thickness", "6mm",
                        "Size", "72\" × 24\"",
                        "Weight", "2.5kg"),
                h.options1("Color", "Forest Green", "Lavender", "Terracotta", "Charcoal"),
                p -> {
                    h.pv(p, "Forest Green", "WELL-YGM-GRN", new BigDecimal("69.99"), 28);
                    h.pv(p, "Lavender",     "WELL-YGM-LAV", new BigDecimal("69.99"), 25);
                    h.pv(p, "Terracotta",   "WELL-YGM-TER", new BigDecimal("69.99"), 22);
                    h.pv(p, "Charcoal",     "WELL-YGM-CHR", new BigDecimal("69.99"), 20);
                }));

        list.add(h.product(co, "Collagen Peptides",
                "Hydrolyzed marine collagen with 10g collagen per serving. Unflavored and flavored varieties.",
                "WELL-COL-001", "49.99", "64.99", "Nutrition", "NutriForge",
                "https://placehold.co/800x800/fef5e7/333333?text=Collagen",
                120, 10, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/fef5e7/333333?text=Collagen",
                        "https://placehold.co/800x800/fdebd0/333333?text=Collagen+Label"),
                h.attrs("Source", "Hydrolyzed marine collagen",
                        "Collagen", "10g per serving",
                        "Servings", "30 per container",
                        "Features", "Paleo, gluten-free, dissolves easily"),
                h.options2("Flavor", "Unflavored", "Mixed Berry", "Lemon", "Size", "200g", "400g", "x"),
                p -> {
                    h.pv2(p, "Unflavored",   "200g", "WELL-COL-UF2", new BigDecimal("34.99"), 40);
                    h.pv2(p, "Unflavored",   "400g", "WELL-COL-UF4", new BigDecimal("49.99"), 35);
                    h.pv2(p, "Mixed Berry",  "200g", "WELL-COL-MB2", new BigDecimal("34.99"), 30);
                    h.pv2(p, "Mixed Berry",  "400g", "WELL-COL-MB4", new BigDecimal("49.99"), 25);
                    h.pv2(p, "Lemon",        "200g", "WELL-COL-LM2", new BigDecimal("34.99"), 28);
                    h.pv2(p, "Lemon",        "400g", "WELL-COL-LM4", new BigDecimal("49.99"), 22);
                }));

        list.add(h.productSingle(co, "Bamboo Cutting Board Set",
                "3-piece organic bamboo cutting board set with juice grooves and hanging holes.",
                "WELL-BCB-001", "39.99", "54.99", "Kitchen", "BrewCraft",
                "https://placehold.co/800x800/f5cba7/333333?text=Bamboo+Board",
                130, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f5cba7/333333?text=Bamboo+Board",
                        "https://placehold.co/800x800/f0b27a/333333?text=Board+Set"),
                h.attrs("Material", "Organic bamboo",
                        "Set", "3-piece (S/M/L)",
                        "Features", "Juice grooves, hanging holes, anti-slip feet",
                        "Care", "Hand wash, apply food-safe oil periodically")));

        list.add(h.product(co, "Essential Oil Diffuser",
                "400ml ultrasonic diffuser with 7-color ambient light, timer, and auto-off safety.",
                "WELL-EOD-001", "44.99", "59.99", "Home Wellness", "PureAir",
                "https://placehold.co/800x800/d5e8d4/333333?text=Oil+Diffuser",
                110, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/d5e8d4/333333?text=Oil+Diffuser",
                        "https://placehold.co/800x800/c3d9c1/333333?text=Diffuser+Mist"),
                h.attrs("Capacity", "400ml",
                        "Coverage", "Up to 30 sq meters",
                        "Run Time", "Up to 13 hours continuous",
                        "Lights", "7 ambient colors"),
                h.options1("Color", "Ivory", "Blush", "Sage"),
                p -> {
                    h.pv(p, "Ivory", "WELL-EOD-IVR", new BigDecimal("44.99"), 38);
                    h.pv(p, "Blush", "WELL-EOD-BLS", new BigDecimal("44.99"), 35);
                    h.pv(p, "Sage",  "WELL-EOD-SGE", new BigDecimal("44.99"), 30);
                }));

        list.add(h.product(co, "Resistance Band Set",
                "5-band latex resistance set from 10–50lbs with handles, door anchor, and ankle straps.",
                "WELL-RBS-001", "29.99", "39.99", "Fitness", "ZenFit",
                "https://placehold.co/800x800/e74c3c/ffffff?text=Resistance+Bands",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/e74c3c/ffffff?text=Resistance+Bands",
                        "https://placehold.co/800x800/cb4335/ffffff?text=Band+Set"),
                h.attrs("Bands", "5 bands: 10, 20, 30, 40, 50 lbs",
                        "Material", "Natural latex",
                        "Accessories", "Handles, door anchor, ankle straps, carry bag",
                        "Resistance", "10–150 lbs combined"),
                h.options1("Type", "5-Band Starter Set", "5-Band Pro Set", "10-Band Complete Set"),
                p -> {
                    h.pv(p, "5-Band Starter Set",    "WELL-RBS-STR", new BigDecimal("29.99"), 75);
                    h.pv(p, "5-Band Pro Set",         "WELL-RBS-PRO", new BigDecimal("39.99"), 55);
                    h.pv(p, "10-Band Complete Set",   "WELL-RBS-CPL", new BigDecimal("54.99"), 35);
                }));

        list.add(h.product(co, "Sleep Support Gummies",
                "3mg melatonin with L-theanine and chamomile. Vegan, non-GMO, strawberry flavor.",
                "WELL-SSG-001", "34.99", "44.99", "Supplements", "NutriForge",
                "https://placehold.co/800x800/f9ebee/333333?text=Sleep+Gummies",
                150, 15, true, true, false, "MONTHLY:1", new BigDecimal("15.00"),
                h.images("https://placehold.co/800x800/f9ebee/333333?text=Sleep+Gummies",
                        "https://placehold.co/800x800/f2d7d5/333333?text=Gummies+Label"),
                h.attrs("Melatonin", "3mg per serving",
                        "Additional", "L-Theanine 100mg, Chamomile 50mg",
                        "Flavour", "Strawberry",
                        "Diet", "Vegan, Non-GMO, Gluten-free"),
                h.options1("Count", "60 gummies (30-day)", "120 gummies (60-day)"),
                p -> {
                    h.pv(p, "60 gummies (30-day)",  "WELL-SSG-60",  new BigDecimal("34.99"), 70);
                    h.pv(p, "120 gummies (60-day)", "WELL-SSG-120", new BigDecimal("59.99"), 55);
                }));

        list.add(h.product(co, "Foam Roller Deep Tissue",
                "EVA foam roller with textured surface for trigger point release. Available in standard and compact.",
                "WELL-FRL-001", "39.99", "49.99", "Fitness", "ZenFit",
                "https://placehold.co/800x800/2e86c1/ffffff?text=Foam+Roller",
                130, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2e86c1/ffffff?text=Foam+Roller",
                        "https://placehold.co/800x800/2874a6/ffffff?text=Roller+Texture"),
                h.attrs("Material", "High-density EVA foam",
                        "Surface", "Multi-density textured zones",
                        "Weight Capacity", "136kg",
                        "Density", "Firm"),
                h.options1("Size", "Standard (33cm)", "Compact (30cm)"),
                p -> {
                    h.pv(p, "Standard (33cm)", "WELL-FRL-STD", new BigDecimal("39.99"), 65);
                    h.pv(p, "Compact (30cm)",  "WELL-FRL-CMP", new BigDecimal("34.99"), 60);
                }));

        list.add(h.product(co, "Matcha Green Tea Powder",
                "Ceremonial-grade Japanese matcha stone-ground from first-harvest tencha. 70mg caffeine per 2g serving.",
                "WELL-MGT-001", "24.99", "34.99", "Nutrition", "BrewCraft",
                "https://placehold.co/800x800/a9cce3/333333?text=Matcha+Powder",
                160, 15, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/a9cce3/333333?text=Matcha+Powder",
                        "https://placehold.co/800x800/93c6e0/333333?text=Matcha+Bowl"),
                h.attrs("Grade", "Ceremonial, first harvest",
                        "Origin", "Uji, Japan",
                        "Caffeine", "~70mg per 2g serving",
                        "Serving", "30 servings per tin"),
                h.options1("Size", "30g tin", "60g tin"),
                p -> {
                    h.pv(p, "30g tin", "WELL-MGT-30", new BigDecimal("24.99"), 80);
                    h.pv(p, "60g tin", "WELL-MGT-60", new BigDecimal("44.99"), 70);
                }));

        list.add(h.product(co, "Stainless Insulated Tumbler",
                "Triple-wall vacuum insulation keeps drinks cold 24h or hot 12h. Sweat-free exterior and leak-proof lid.",
                "WELL-SIT-001", "34.99", "44.99", "Kitchen", "BrewCraft",
                "https://placehold.co/800x800/1a5276/ffffff?text=Insulated+Tumbler",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a5276/ffffff?text=Insulated+Tumbler",
                        "https://placehold.co/800x800/154360/ffffff?text=Tumbler+Lid"),
                h.attrs("Insulation", "Triple-wall vacuum",
                        "Cold", "24 hours",
                        "Hot", "12 hours",
                        "BPA Free", "Yes, food-grade 18/8 stainless"),
                h.options2("Color", "Slate", "Rose Gold", "Sage", "Size", "20oz", "32oz", "x"),
                p -> {
                    h.pv2(p, "Slate",     "20oz", "WELL-SIT-SL20", new BigDecimal("29.99"), 40);
                    h.pv2(p, "Slate",     "32oz", "WELL-SIT-SL32", new BigDecimal("34.99"), 35);
                    h.pv2(p, "Rose Gold", "20oz", "WELL-SIT-RG20", new BigDecimal("29.99"), 38);
                    h.pv2(p, "Rose Gold", "32oz", "WELL-SIT-RG32", new BigDecimal("34.99"), 32);
                    h.pv2(p, "Sage",      "20oz", "WELL-SIT-SG20", new BigDecimal("29.99"), 35);
                    h.pv2(p, "Sage",      "32oz", "WELL-SIT-SG32", new BigDecimal("34.99"), 30);
                }));

        list.add(h.product(co, "Probiotics 60 Billion CFU",
                "20-strain probiotic blend with prebiotics for gut health and immune support. Shelf-stable, no refrigeration needed.",
                "WELL-PRB-001", "44.99", "59.99", "Supplements", "NutriForge",
                "https://placehold.co/800x800/e8f8f5/333333?text=Probiotics",
                130, 12, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/e8f8f5/333333?text=Probiotics",
                        "https://placehold.co/800x800/d5f5e3/333333?text=Probiotic+Label"),
                h.attrs("CFU", "60 Billion per serving",
                        "Strains", "20 probiotic strains",
                        "Prebiotics", "Organic inulin",
                        "Storage", "Shelf-stable, no refrigeration"),
                h.options1("Count", "30 capsules (30-day)", "60 capsules (60-day)"),
                p -> {
                    h.pv(p, "30 capsules (30-day)", "WELL-PRB-30", new BigDecimal("44.99"), 65);
                    h.pv(p, "60 capsules (60-day)", "WELL-PRB-60", new BigDecimal("79.99"), 50);
                }));

        list.add(h.productSingle(co, "LED Therapy Face Mask",
                "7-color photon LED mask targeting anti-aging, acne, and pigmentation. FDA-cleared, clinic-grade.",
                "WELL-LED-001", "89.99", "119.99", "Skincare", "GlowLab",
                "https://placehold.co/800x800/f8d7da/333333?text=LED+Face+Mask",
                55, 5, true, false, false, null, null,
                h.images("https://placehold.co/800x800/f8d7da/333333?text=LED+Face+Mask",
                        "https://placehold.co/800x800/f5c6cb/333333?text=Mask+Modes"),
                h.attrs("LEDs", "150 individual LEDs, 7 wavelengths",
                        "Colors", "Red, Blue, Green, Yellow, Cyan, Purple, White",
                        "FDA", "FDA-cleared Class II",
                        "Session Time", "10–20 minutes")));

        list.add(h.product(co, "Meditation Cushion",
                "Buckwheat hull zafu meditation cushion with organic cotton cover. Removable and washable.",
                "WELL-MED-001", "49.99", "64.99", "Fitness", "ZenFit",
                "https://placehold.co/800x800/d7bde2/333333?text=Meditation+Cushion",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/d7bde2/333333?text=Meditation+Cushion",
                        "https://placehold.co/800x800/c39bd3/333333?text=Cushion+Set"),
                h.attrs("Fill", "Organic buckwheat hulls",
                        "Cover", "Organic cotton, removable",
                        "Height", "~15cm when filled",
                        "Diameter", "33cm"),
                h.options1("Color", "Indigo", "Natural", "Terracotta", "Sage"),
                p -> {
                    h.pv(p, "Indigo",     "WELL-MED-IND", new BigDecimal("49.99"), 28);
                    h.pv(p, "Natural",    "WELL-MED-NAT", new BigDecimal("49.99"), 25);
                    h.pv(p, "Terracotta", "WELL-MED-TER", new BigDecimal("49.99"), 22);
                    h.pv(p, "Sage",       "WELL-MED-SGE", new BigDecimal("49.99"), 20);
                }));

        list.add(h.product(co, "Shea Butter Body Lotion",
                "Rich whipped shea butter lotion with jojoba and vitamin E. Absorbs quickly, no greasy residue.",
                "WELL-SBL-001", "24.99", "32.99", "Skincare", "GlowLab",
                "https://placehold.co/800x800/fef9e7/333333?text=Shea+Lotion",
                200, 20, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/fef9e7/333333?text=Shea+Lotion",
                        "https://placehold.co/800x800/fdf2d0/333333?text=Lotion+Pour"),
                h.attrs("Key Ingredients", "Shea butter, jojoba oil, vitamin E",
                        "Size", "250ml",
                        "Skin Type", "All skin types",
                        "Fragrance", "Light natural scent"),
                h.options1("Scent", "Unscented", "Lavender", "Vanilla Coconut"),
                p -> {
                    h.pv(p, "Unscented",       "WELL-SBL-UNS", new BigDecimal("24.99"), 70);
                    h.pv(p, "Lavender",        "WELL-SBL-LAV", new BigDecimal("24.99"), 65);
                    h.pv(p, "Vanilla Coconut", "WELL-SBL-VAN", new BigDecimal("24.99"), 60);
                }));

        list.add(h.product(co, "Cold Process Soap Bar Set",
                "4-pack artisan cold-process soap with plant-based oils. Palm-oil free, natural fragrance.",
                "WELL-SOP-001", "19.99", "26.99", "Skincare", "GlowLab",
                "https://placehold.co/800x800/fdebd0/333333?text=Soap+Bars",
                200, 20, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/fdebd0/333333?text=Soap+Bars",
                        "https://placehold.co/800x800/fad7a0/333333?text=Soap+Detail"),
                h.attrs("Process", "Cold process",
                        "Base Oils", "Olive, coconut, castor (palm-free)",
                        "Pack", "4 bars, 100g each",
                        "Shelf Life", "12 months"),
                h.options1("Scent", "Lavender & Oat", "Mint & Charcoal", "Rose Clay", "Citrus Burst"),
                p -> {
                    h.pv(p, "Lavender & Oat",  "WELL-SOP-LAV", new BigDecimal("19.99"), 55);
                    h.pv(p, "Mint & Charcoal", "WELL-SOP-MNT", new BigDecimal("19.99"), 50);
                    h.pv(p, "Rose Clay",       "WELL-SOP-RSE", new BigDecimal("19.99"), 48);
                    h.pv(p, "Citrus Burst",    "WELL-SOP-CIT", new BigDecimal("19.99"), 45);
                }));

        list.add(h.product(co, "Multivitamin Daily Pack",
                "Comprehensive 23-nutrient daily pack with methylated B vitamins and chelated minerals. AM/PM pouches.",
                "WELL-MVI-001", "39.99", "54.99", "Supplements", "NutriForge",
                "https://placehold.co/800x800/e8f4f8/333333?text=Multivitamin",
                160, 15, true, true, false, "MONTHLY:1", new BigDecimal("15.00"),
                h.images("https://placehold.co/800x800/e8f4f8/333333?text=Multivitamin",
                        "https://placehold.co/800x800/d6eaf8/333333?text=Vitamin+Pack"),
                h.attrs("Nutrients", "23 vitamins and minerals",
                        "Form", "AM + PM pouch system",
                        "B Vitamins", "Methylated forms (more bioavailable)",
                        "Diet", "Non-GMO, gluten-free, no artificial colors"),
                h.options1("Count", "30-day supply", "60-day supply"),
                p -> {
                    h.pv(p, "30-day supply", "WELL-MVI-30", new BigDecimal("39.99"), 80);
                    h.pv(p, "60-day supply", "WELL-MVI-60", new BigDecimal("69.99"), 65);
                }));

        // --- 30 additional products ---

        list.add(h.product(co, "Omega-3 Fish Oil 2000mg",
                "Molecularly distilled omega-3 with 1200mg EPA/DHA per serving. Lemon flavour, no fishy aftertaste.",
                "WELL-OMG-001", "29.99", "39.99", "Supplements", "NutriForge",
                "https://placehold.co/800x800/fff3cd/333333?text=Omega-3",
                160, 15, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/fff3cd/333333?text=Omega-3",
                        "https://placehold.co/800x800/ffeaa7/333333?text=Fish+Oil+Label"),
                h.attrs("EPA/DHA", "1200mg combined per 2-softgel serving",
                        "Purity", "Molecularly distilled, heavy metal tested",
                        "Flavour", "Natural lemon (no fishy burps)",
                        "Diet", "Non-GMO, gluten-free"),
                h.options1("Count", "60 softgels (30-day)", "120 softgels (60-day)"),
                p -> {
                    h.pv(p, "60 softgels (30-day)",  "WELL-OMG-60",  new BigDecimal("29.99"), 70);
                    h.pv(p, "120 softgels (60-day)", "WELL-OMG-120", new BigDecimal("49.99"), 55);
                }));

        list.add(h.product(co, "Turmeric + Black Pepper Complex",
                "500mg standardised turmeric extract (95% curcumin) with 5mg BioPerine for 20× better absorption.",
                "WELL-TUR-001", "24.99", "34.99", "Supplements", "NutriForge",
                "https://placehold.co/800x800/f39c12/ffffff?text=Turmeric",
                150, 15, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/f39c12/ffffff?text=Turmeric",
                        "https://placehold.co/800x800/e67e22/ffffff?text=Turmeric+Label"),
                h.attrs("Curcumin", "500mg extract (95% curcuminoids)",
                        "BioPerine", "5mg black pepper extract",
                        "Absorption", "20× vs standard turmeric",
                        "Diet", "Vegan, non-GMO, gluten-free"),
                h.options1("Count", "60 capsules (30-day)", "120 capsules (60-day)"),
                p -> {
                    h.pv(p, "60 capsules (30-day)",  "WELL-TUR-60",  new BigDecimal("24.99"), 75);
                    h.pv(p, "120 capsules (60-day)", "WELL-TUR-120", new BigDecimal("44.99"), 60);
                }));

        list.add(h.product(co, "Magnesium Glycinate 400mg",
                "High-absorption magnesium glycinate for sleep, muscle recovery, and stress support.",
                "WELL-MGN-001", "29.99", "39.99", "Supplements", "NutriForge",
                "https://placehold.co/800x800/e8daef/333333?text=Magnesium",
                140, 12, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/e8daef/333333?text=Magnesium",
                        "https://placehold.co/800x800/d7bde2/333333?text=Mag+Label"),
                h.attrs("Form", "Magnesium Glycinate (chelated)",
                        "Per Serving", "400mg elemental magnesium",
                        "Benefits", "Sleep, muscle recovery, stress reduction",
                        "Diet", "Vegan, non-GMO"),
                h.options1("Count", "60 capsules (30-day)", "120 capsules (60-day)"),
                p -> {
                    h.pv(p, "60 capsules (30-day)",  "WELL-MGN-60",  new BigDecimal("29.99"), 70);
                    h.pv(p, "120 capsules (60-day)", "WELL-MGN-120", new BigDecimal("49.99"), 55);
                }));

        list.add(h.product(co, "Vitamin D3 + K2 5000IU",
                "Vitamin D3 5000IU with K2 MK-7 100µg. Supports bone density, immunity, and cardiovascular health.",
                "WELL-VDK-001", "24.99", "34.99", "Supplements", "NutriForge",
                "https://placehold.co/800x800/fff9c4/333333?text=Vitamin+D3+K2",
                150, 15, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/fff9c4/333333?text=Vitamin+D3+K2",
                        "https://placehold.co/800x800/fff176/333333?text=VDK+Label"),
                h.attrs("D3", "5000 IU (125µg) cholecalciferol",
                        "K2", "100µg MK-7 (menaquinone-7)",
                        "Base", "Organic olive oil (enhanced absorption)",
                        "Diet", "Non-GMO, gluten-free"),
                h.options1("Count", "60 softgels (60-day)", "120 softgels (120-day)"),
                p -> {
                    h.pv(p, "60 softgels (60-day)",   "WELL-VDK-60",  new BigDecimal("24.99"), 80);
                    h.pv(p, "120 softgels (120-day)", "WELL-VDK-120", new BigDecimal("44.99"), 65);
                }));

        list.add(h.product(co, "Ashwagandha KSM-66 600mg",
                "Clinical-strength KSM-66 ashwagandha for cortisol reduction, stress relief, and endurance.",
                "WELL-ASH-001", "29.99", "39.99", "Supplements", "NutriForge",
                "https://placehold.co/800x800/f5cba7/333333?text=Ashwagandha",
                130, 12, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/f5cba7/333333?text=Ashwagandha",
                        "https://placehold.co/800x800/f0b27a/333333?text=Ashwa+Label"),
                h.attrs("Extract", "KSM-66 full-spectrum root extract",
                        "Withanolides", "Standardised ≥5%",
                        "Per Serving", "600mg",
                        "Studies", "21 human clinical trials"),
                h.options1("Count", "60 capsules (30-day)", "120 capsules (60-day)"),
                p -> {
                    h.pv(p, "60 capsules (30-day)",  "WELL-ASH-60",  new BigDecimal("29.99"), 70);
                    h.pv(p, "120 capsules (60-day)", "WELL-ASH-120", new BigDecimal("49.99"), 55);
                }));

        list.add(h.product(co, "Pre-Workout Energy Formula",
                "Science-backed pre-workout with 200mg caffeine, 6g citrulline, 3.2g beta-alanine, and creatine.",
                "WELL-PRW-001", "39.99", "54.99", "Nutrition", "NutriForge",
                "https://placehold.co/800x800/ff5722/ffffff?text=Pre-Workout",
                120, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ff5722/ffffff?text=Pre-Workout",
                        "https://placehold.co/800x800/e64a19/ffffff?text=PreW+Label"),
                h.attrs("Caffeine", "200mg natural caffeine",
                        "Citrulline", "6g L-Citrulline malate",
                        "Beta-Alanine", "3.2g",
                        "Servings", "30 per container"),
                h.options1("Flavour", "Tropical Punch", "Watermelon", "Blue Raspberry"),
                p -> {
                    h.pv(p, "Tropical Punch",  "WELL-PRW-TPU", new BigDecimal("39.99"), 45);
                    h.pv(p, "Watermelon",      "WELL-PRW-WTR", new BigDecimal("39.99"), 40);
                    h.pv(p, "Blue Raspberry",  "WELL-PRW-BLR", new BigDecimal("39.99"), 38);
                }));

        list.add(h.product(co, "BCAA 2:1:1 Amino Acids",
                "Vegan-fermented BCAAs in a 2:1:1 leucine/isoleucine/valine ratio for muscle recovery.",
                "WELL-BCA-001", "29.99", "39.99", "Nutrition", "NutriForge",
                "https://placehold.co/800x800/c8e6c9/333333?text=BCAA",
                130, 12, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/c8e6c9/333333?text=BCAA",
                        "https://placehold.co/800x800/a5d6a7/333333?text=BCAA+Label"),
                h.attrs("Ratio", "2:1:1 Leucine : Isoleucine : Valine",
                        "Per Serving", "7g BCAAs",
                        "Source", "Vegan fermented",
                        "Servings", "30 per bag"),
                h.options1("Flavour", "Raspberry Lemonade", "Unflavoured", "Cherry Cola"),
                p -> {
                    h.pv(p, "Raspberry Lemonade", "WELL-BCA-RAS", new BigDecimal("29.99"), 55);
                    h.pv(p, "Unflavoured",         "WELL-BCA-UNF", new BigDecimal("29.99"), 50);
                    h.pv(p, "Cherry Cola",         "WELL-BCA-CHR", new BigDecimal("29.99"), 40);
                }));

        list.add(h.product(co, "Creatine Monohydrate",
                "Micronised creatine monohydrate with Creapure® certification for maximum purity and solubility.",
                "WELL-CRE-001", "24.99", "34.99", "Nutrition", "NutriForge",
                "https://placehold.co/800x800/e3f2fd/333333?text=Creatine",
                150, 15, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/e3f2fd/333333?text=Creatine",
                        "https://placehold.co/800x800/bbdefb/333333?text=Creatine+Label"),
                h.attrs("Grade", "Creapure® certified micronised",
                        "Per Serving", "5g creatine monohydrate",
                        "Purity", "99.9%",
                        "Mixes", "Completely tasteless and colourless"),
                h.options1("Size", "300g (60 servings)", "600g (120 servings)"),
                p -> {
                    h.pv(p, "300g (60 servings)",  "WELL-CRE-300", new BigDecimal("24.99"), 80);
                    h.pv(p, "600g (120 servings)", "WELL-CRE-600", new BigDecimal("44.99"), 65);
                }));

        list.add(h.product(co, "Zinc + Selenium Complex",
                "Zinc bisglycinate 25mg with selenium methionine 200µg for immune function and thyroid support.",
                "WELL-ZNS-001", "19.99", "26.99", "Supplements", "NutriForge",
                "https://placehold.co/800x800/e8f5e9/333333?text=Zinc+Selenium",
                160, 15, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/e8f5e9/333333?text=Zinc+Selenium",
                        "https://placehold.co/800x800/c8e6c9/333333?text=ZnSe+Label"),
                h.attrs("Zinc", "25mg bisglycinate (chelated)",
                        "Selenium", "200µg L-selenomethionine",
                        "Benefits", "Immune function, thyroid health, fertility",
                        "Diet", "Vegan, non-GMO"),
                h.options1("Count", "60 capsules (60-day)", "120 capsules (120-day)"),
                p -> {
                    h.pv(p, "60 capsules (60-day)",   "WELL-ZNS-60",  new BigDecimal("19.99"), 85);
                    h.pv(p, "120 capsules (120-day)", "WELL-ZNS-120", new BigDecimal("34.99"), 70);
                }));

        list.add(h.product(co, "Green Superfood Powder",
                "30-ingredient green blend: spirulina, chlorella, wheatgrass, adaptogenic mushrooms, and digestive enzymes.",
                "WELL-GSP-001", "44.99", "59.99", "Nutrition", "NutriForge",
                "https://placehold.co/800x800/1e8449/ffffff?text=Green+Superfood",
                120, 12, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/1e8449/ffffff?text=Green+Superfood",
                        "https://placehold.co/800x800/196f3d/ffffff?text=Green+Label"),
                h.attrs("Ingredients", "30 greens, adaptogens, and probiotics",
                        "Serving", "1 scoop (10g)",
                        "Servings", "30 per bag",
                        "Diet", "Vegan, gluten-free, no added sugars"),
                h.options1("Flavour", "Original Green", "Berry", "Tropical"),
                p -> {
                    h.pv(p, "Original Green", "WELL-GSP-ORG", new BigDecimal("44.99"), 50);
                    h.pv(p, "Berry",           "WELL-GSP-BER", new BigDecimal("44.99"), 45);
                    h.pv(p, "Tropical",        "WELL-GSP-TRP", new BigDecimal("44.99"), 40);
                }));

        list.add(h.product(co, "Apple Cider Vinegar Gummies",
                "1000mg ACV with Mother per serving in gummy form. With B12, beetroot, and pomegranate.",
                "WELL-ACV-001", "24.99", "34.99", "Supplements", "NutriForge",
                "https://placehold.co/800x800/e74c3c/ffffff?text=ACV+Gummies",
                150, 15, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/e74c3c/ffffff?text=ACV+Gummies",
                        "https://placehold.co/800x800/cb4335/ffffff?text=ACV+Label"),
                h.attrs("ACV", "1000mg with Mother per serving",
                        "Added", "Vitamin B12, beetroot, pomegranate",
                        "Flavour", "Apple",
                        "Diet", "Vegan, pectin-based (no gelatin)"),
                h.options1("Count", "60 gummies (30-day)", "120 gummies (60-day)"),
                p -> {
                    h.pv(p, "60 gummies (30-day)",  "WELL-ACV-60",  new BigDecimal("24.99"), 75);
                    h.pv(p, "120 gummies (60-day)", "WELL-ACV-120", new BigDecimal("39.99"), 60);
                }));

        list.add(h.product(co, "Biotin 10000mcg",
                "High-potency biotin for hair, skin, and nail health. Rapid-dissolve soft capsule form.",
                "WELL-BIO-001", "19.99", "26.99", "Supplements", "NutriForge",
                "https://placehold.co/800x800/fce4ec/333333?text=Biotin",
                170, 17, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/fce4ec/333333?text=Biotin",
                        "https://placehold.co/800x800/f8bbd9/333333?text=Biotin+Label"),
                h.attrs("Dose", "10,000mcg per softgel",
                        "Form", "Rapid-dissolve softgel",
                        "Benefits", "Hair growth, nail strength, skin health",
                        "Diet", "Non-GMO, gluten-free"),
                h.options1("Count", "90 softgels (90-day)", "180 softgels (180-day)"),
                p -> {
                    h.pv(p, "90 softgels (90-day)",   "WELL-BIO-90",  new BigDecimal("19.99"), 90);
                    h.pv(p, "180 softgels (180-day)", "WELL-BIO-180", new BigDecimal("34.99"), 70);
                }));

        list.add(h.productSingle(co, "Collagen Firming Face Cream",
                "Day and night face cream with hydrolysed collagen, hyaluronic acid, and vitamin C. For all skin types.",
                "WELL-CFC-001", "39.99", "54.99", "Skincare", "GlowLab",
                "https://placehold.co/800x800/fce4ec/333333?text=Collagen+Cream",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/fce4ec/333333?text=Collagen+Cream",
                        "https://placehold.co/800x800/f8bbd9/333333?text=Cream+Jar"),
                h.attrs("Key Actives", "Hydrolysed collagen, hyaluronic acid, vitamin C",
                        "Size", "50ml",
                        "Use", "Morning and night",
                        "Skin Type", "All skin types")));

        list.add(h.product(co, "Hyaluronic Acid Serum 2%",
                "Multi-weight hyaluronic acid serum with niacinamide for plumping, hydrating, and barrier repair.",
                "WELL-HAS-001", "34.99", "44.99", "Skincare", "GlowLab",
                "https://placehold.co/800x800/e3f2fd/333333?text=HA+Serum",
                120, 12, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/e3f2fd/333333?text=HA+Serum",
                        "https://placehold.co/800x800/bbdefb/333333?text=HA+Drop"),
                h.attrs("Actives", "2% Hyaluronic Acid (3 molecular weights) + Niacinamide",
                        "Size", "Available in 30ml and 60ml",
                        "pH", "6.0–7.0",
                        "Skin Type", "All skin types, especially dry and dehydrated"),
                h.options1("Size", "30ml", "60ml"),
                p -> {
                    h.pv(p, "30ml", "WELL-HAS-30", new BigDecimal("34.99"), 60);
                    h.pv(p, "60ml", "WELL-HAS-60", new BigDecimal("54.99"), 50);
                }));

        list.add(h.productSingle(co, "Retinol Night Cream 0.5%",
                "0.5% time-released retinol with ceramides and peptides for anti-aging and texture refinement.",
                "WELL-RNT-001", "44.99", "59.99", "Skincare", "GlowLab",
                "https://placehold.co/800x800/fff8e1/333333?text=Retinol+Cream",
                90, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/fff8e1/333333?text=Retinol+Cream",
                        "https://placehold.co/800x800/fff3e0/333333?text=Retinol+Jar"),
                h.attrs("Retinol", "0.5% encapsulated time-release",
                        "Supports", "Ceramides, peptides, squalane",
                        "Use", "PM only",
                        "Size", "50ml")));

        list.add(h.productSingle(co, "Mineral SPF 50 Sunscreen",
                "Zinc oxide mineral sunscreen with no white cast formula. Water-resistant 80 minutes. Reef-safe.",
                "WELL-SPF-001", "29.99", "39.99", "Skincare", "GlowLab",
                "https://placehold.co/800x800/fff9c4/333333?text=SPF+50",
                130, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/fff9c4/333333?text=SPF+50",
                        "https://placehold.co/800x800/fff176/333333?text=SPF+Tube"),
                h.attrs("SPF", "SPF 50 broad spectrum UVA/UVB",
                        "Filter", "20% Zinc Oxide",
                        "Water Resistance", "80 minutes",
                        "Formula", "No white cast, reef-safe")));

        list.add(h.productSingle(co, "Kaolin Clay Face Mask",
                "Deep-pore cleansing kaolin clay mask with activated charcoal and tea tree. Suitable for oily skin.",
                "WELL-CLY-001", "19.99", "26.99", "Skincare", "GlowLab",
                "https://placehold.co/800x800/f5f5f5/333333?text=Clay+Mask",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f5f5f5/333333?text=Clay+Mask",
                        "https://placehold.co/800x800/eeeeee/333333?text=Mask+Jar"),
                h.attrs("Key Ingredients", "Kaolin clay, activated charcoal, tea tree oil",
                        "Size", "100ml",
                        "Best For", "Oily, acne-prone, and combination skin",
                        "Use", "2–3× per week")));

        list.add(h.product(co, "Glycolic Acid Toner 7%",
                "7% glycolic acid toner with aloe vera and cucumber for gentle exfoliation and brightening.",
                "WELL-GLY-001", "24.99", "34.99", "Skincare", "GlowLab",
                "https://placehold.co/800x800/f3e5f5/333333?text=Glycolic+Toner",
                120, 12, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/f3e5f5/333333?text=Glycolic+Toner",
                        "https://placehold.co/800x800/e1bee7/333333?text=Toner+Bottle"),
                h.attrs("Glycolic Acid", "7%",
                        "pH", "3.5–4.0",
                        "Calming", "Aloe vera and cucumber extract",
                        "Use", "PM, 2–3× per week"),
                h.options1("Size", "100ml", "200ml"),
                p -> {
                    h.pv(p, "100ml", "WELL-GLY-100", new BigDecimal("24.99"), 65);
                    h.pv(p, "200ml", "WELL-GLY-200", new BigDecimal("39.99"), 55);
                }));

        list.add(h.productSingle(co, "Niacinamide Serum 10%",
                "10% niacinamide with 1% zinc for pore minimising, oil control, and fading dark spots.",
                "WELL-NIA-001", "22.99", "29.99", "Skincare", "GlowLab",
                "https://placehold.co/800x800/e8f5e9/333333?text=Niacinamide",
                140, 14, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/e8f5e9/333333?text=Niacinamide",
                        "https://placehold.co/800x800/c8e6c9/333333?text=Niac+Bottle"),
                h.attrs("Niacinamide", "10%",
                        "Zinc", "1% PCA zinc",
                        "Benefits", "Pore minimising, oil control, dark spot reduction",
                        "Size", "30ml")));

        list.add(h.productSingle(co, "Under-Eye Hydrogel Patches",
                "24K gold + hyaluronic acid under-eye patches for puffiness, dark circles, and fine lines.",
                "WELL-UEP-001", "24.99", "34.99", "Skincare", "GlowLab",
                "https://placehold.co/800x800/ffd54f/333333?text=Eye+Patches",
                120, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ffd54f/333333?text=Eye+Patches",
                        "https://placehold.co/800x800/ffca28/333333?text=Patches+Detail"),
                h.attrs("Key Actives", "24K gold, hyaluronic acid, collagen",
                        "Pairs", "30 pairs per pack",
                        "Use Time", "20–30 minutes",
                        "Benefits", "Puffiness, dark circles, fine lines")));

        list.add(h.productSingle(co, "Bamboo Toothbrush Set of 4",
                "BPA-free charcoal-bristle bamboo toothbrushes. Biodegradable handle, compostable packaging.",
                "WELL-BTB-001", "12.99", "17.99", "Personal Care", "GlowLab",
                "https://placehold.co/800x800/a5d6a7/333333?text=Bamboo+Brush",
                200, 20, false, true, false, "MONTHLY:1,WEEKLY:4", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/a5d6a7/333333?text=Bamboo+Brush",
                        "https://placehold.co/800x800/81c784/333333?text=Brush+Set"),
                h.attrs("Handle", "Organically grown bamboo",
                        "Bristles", "Charcoal-infused BPA-free nylon",
                        "Pack", "4 brushes",
                        "Packaging", "Recyclable cardboard")));

        list.add(h.productSingle(co, "Copper Tongue Scraper",
                "99.9% pure copper tongue scraper. Naturally antimicrobial, reduces morning breath bacteria.",
                "WELL-CTS-001", "14.99", "19.99", "Personal Care", "GlowLab",
                "https://placehold.co/800x800/f0a500/333333?text=Tongue+Scraper",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f0a500/333333?text=Tongue+Scraper",
                        "https://placehold.co/800x800/d4880e/333333?text=Scraper+Pack"),
                h.attrs("Material", "99.9% pure copper",
                        "Pack", "2 scrapers + travel pouch",
                        "Antimicrobial", "Naturally self-sterilising",
                        "Lifespan", "Years (improve with use)")));

        list.add(h.product(co, "Sonic Electric Toothbrush",
                "40,000 strokes/min sonic toothbrush with 5 modes, 2-minute timer, and 30-day battery.",
                "WELL-ETB-001", "49.99", "69.99", "Personal Care", "GlowLab",
                "https://placehold.co/800x800/e3f2fd/333333?text=Electric+Brush",
                90, 9, false, false, false, null, null,
                h.images("https://placehold.co/800x800/e3f2fd/333333?text=Electric+Brush",
                        "https://placehold.co/800x800/bbdefb/333333?text=Brush+Heads"),
                h.attrs("Speed", "40,000 strokes/minute",
                        "Modes", "5: Clean, White, Sensitive, Gum Care, Polish",
                        "Battery", "30 days per charge (USB-C)",
                        "Includes", "Brush + 4 heads + travel case"),
                h.options1("Color", "Arctic White", "Midnight Black", "Blush"),
                p -> {
                    h.pv(p, "Arctic White",  "WELL-ETB-WHT", new BigDecimal("49.99"), 35);
                    h.pv(p, "Midnight Black","WELL-ETB-BLK", new BigDecimal("49.99"), 30);
                    h.pv(p, "Blush",         "WELL-ETB-BLS", new BigDecimal("49.99"), 22);
                }));

        list.add(h.product(co, "Herbal Sleep & Calm Tea",
                "Caffeine-free herbal blend with valerian, chamomile, passionflower, and lemon balm.",
                "WELL-SLT-001", "14.99", "19.99", "Nutrition", "BrewCraft",
                "https://placehold.co/800x800/f3e5f5/333333?text=Sleep+Tea",
                200, 20, false, true, false, "MONTHLY:1,WEEKLY:4", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/f3e5f5/333333?text=Sleep+Tea",
                        "https://placehold.co/800x800/e1bee7/333333?text=Tea+Bag"),
                h.attrs("Herbs", "Valerian, chamomile, passionflower, lemon balm",
                        "Caffeine", "Caffeine-free",
                        "Bags", "30 biodegradable pyramid bags",
                        "Flavour", "Floral, slightly earthy"),
                h.options1("Pack", "30 bags", "60 bags (twin pack)"),
                p -> {
                    h.pv(p, "30 bags",           "WELL-SLT-30", new BigDecimal("14.99"), 90);
                    h.pv(p, "60 bags (twin pack)","WELL-SLT-60", new BigDecimal("26.99"), 70);
                }));

        list.add(h.product(co, "Golden Turmeric Latte Mix",
                "Adaptogenic golden latte blend with turmeric, ginger, cinnamon, ashwagandha, and black pepper.",
                "WELL-GLT-001", "19.99", "26.99", "Nutrition", "BrewCraft",
                "https://placehold.co/800x800/f39c12/ffffff?text=Golden+Latte",
                160, 16, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/f39c12/ffffff?text=Golden+Latte",
                        "https://placehold.co/800x800/e67e22/ffffff?text=Latte+Cup"),
                h.attrs("Key Herbs", "Turmeric, ginger, ashwagandha, cinnamon",
                        "Servings", "30 per pouch",
                        "Preparation", "Mix 1 tsp in warm oat/almond milk",
                        "Diet", "Vegan, sugar-free"),
                h.options1("Size", "150g pouch (30 servings)", "300g pouch (60 servings)"),
                p -> {
                    h.pv(p, "150g pouch (30 servings)", "WELL-GLT-150", new BigDecimal("19.99"), 80);
                    h.pv(p, "300g pouch (60 servings)", "WELL-GLT-300", new BigDecimal("34.99"), 65);
                }));

        list.add(h.product(co, "Electrolyte Powder Sachets",
                "Zero-calorie electrolyte sachets with sodium, potassium, magnesium, and zinc. No sugar or sweeteners.",
                "WELL-ELP-001", "24.99", "34.99", "Nutrition", "NutriForge",
                "https://placehold.co/800x800/b3e5fc/333333?text=Electrolytes",
                150, 15, false, true, false, "MONTHLY:1,WEEKLY:4", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/b3e5fc/333333?text=Electrolytes",
                        "https://placehold.co/800x800/81d4fa/333333?text=Sachet+Pack"),
                h.attrs("Electrolytes", "Sodium 500mg, Potassium 200mg, Magnesium 60mg, Zinc 5mg",
                        "Calories", "Zero calories",
                        "Sweetener", "None",
                        "Pack", "30 sachets"),
                h.options1("Flavour", "Citrus", "Berry", "Watermelon"),
                p -> {
                    h.pv(p, "Citrus",      "WELL-ELP-CIT", new BigDecimal("24.99"), 60);
                    h.pv(p, "Berry",       "WELL-ELP-BER", new BigDecimal("24.99"), 55);
                    h.pv(p, "Watermelon",  "WELL-ELP-WTR", new BigDecimal("24.99"), 50);
                }));

        list.add(h.productSingle(co, "Cold Brew Coffee Bags",
                "Specialty single-origin cold brew bags. Steep 12–18h for smooth, low-acid cold brew concentrate.",
                "WELL-CBR-001", "19.99", "26.99", "Nutrition", "BrewCraft",
                "https://placehold.co/800x800/4e342e/ffffff?text=Cold+Brew+Bags",
                160, 15, false, true, false, "MONTHLY:1,WEEKLY:4", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/4e342e/ffffff?text=Cold+Brew+Bags",
                        "https://placehold.co/800x800/3e2723/ffffff?text=Brew+Bag"),
                h.attrs("Origin", "Single-origin Colombian",
                        "Roast", "Medium-dark",
                        "Pack", "12 brew bags",
                        "Yield", "Each bag yields 8–10 servings")));

        list.add(h.product(co, "Percussive Massage Gun",
                "2400rpm percussive massage gun with 6 attachments and 5 speed levels. 4-hour battery.",
                "WELL-MSG-001", "89.99", "119.99", "Fitness", "ZenFit",
                "https://placehold.co/800x800/1c2833/ffffff?text=Massage+Gun",
                80, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Massage+Gun",
                        "https://placehold.co/800x800/17202a/ffffff?text=Gun+Heads"),
                h.attrs("Speed", "5 levels, up to 2400 RPM",
                        "Amplitude", "12mm",
                        "Attachments", "6 heads included",
                        "Battery", "4 hours, USB-C charging"),
                h.options1("Color", "Matte Black", "Rose Gold"),
                p -> {
                    h.pv(p, "Matte Black", "WELL-MSG-BLK", new BigDecimal("89.99"), 38);
                    h.pv(p, "Rose Gold",   "WELL-MSG-RSG", new BigDecimal("89.99"), 30);
                }));

        list.add(h.productSingle(co, "Acupressure Mat + Pillow Set",
                "8,820 acupressure points on non-toxic ABS spikes. Includes pillow for neck and lower back relief.",
                "WELL-ACM-001", "44.99", "59.99", "Fitness", "ZenFit",
                "https://placehold.co/800x800/c0392b/ffffff?text=Acupressure+Mat",
                110, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/c0392b/ffffff?text=Acupressure+Mat",
                        "https://placehold.co/800x800/a93226/ffffff?text=Mat+Pillow"),
                h.attrs("Points", "8,820 ABS spikes on mat",
                        "Mat Size", "68 × 42 cm",
                        "Pillow", "Included, 33 × 14 cm",
                        "Filling", "Organic coconut fibre")));

        list.add(h.product(co, "Adaptogenic Mushroom Blend",
                "5-mushroom blend: lion's mane, reishi, cordyceps, chaga, and turkey tail. Hot-water extracted.",
                "WELL-MSH-001", "34.99", "49.99", "Supplements", "NutriForge",
                "https://placehold.co/800x800/795548/ffffff?text=Mushroom+Blend",
                130, 12, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/795548/ffffff?text=Mushroom+Blend",
                        "https://placehold.co/800x800/6d4c41/ffffff?text=Mushroom+Label"),
                h.attrs("Mushrooms", "Lion's Mane, Reishi, Cordyceps, Chaga, Turkey Tail",
                        "Extract", "Hot-water extracted, >30% beta-glucans",
                        "Per Serving", "2g (1 tsp)",
                        "Diet", "Vegan, organic certified"),
                h.options1("Size", "100g powder (50 servings)", "60 capsules (30-day)"),
                p -> {
                    h.pv(p, "100g powder (50 servings)", "WELL-MSH-PWD", new BigDecimal("34.99"), 60);
                    h.pv(p, "60 capsules (30-day)",      "WELL-MSH-CAP", new BigDecimal("34.99"), 55);
                }));

        return list;
    }
}
