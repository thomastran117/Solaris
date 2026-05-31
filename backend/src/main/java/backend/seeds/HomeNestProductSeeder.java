package backend.seeds;

import backend.models.core.Company;
import backend.models.core.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * HomeNest Co. product catalog — home, kitchen, smart home, and living décor.
 *
 * Intentional category overlap with existing companies:
 *   - Smart home devices (smart bulbs, thermostat, doorbell, smart strip) → TechGadgets Co.
 *   - Kitchen/fragrance (pour-over, candles, diffuser, bamboo board) → WellnessWorld
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class HomeNestProductSeeder {

    private final ProductSeedHelper h;

    public List<Product> seed(Company co) {
        List<Product> list = new ArrayList<>();

        // ── Smart Home (10) ── overlaps TechGadgets ──────────────────────────

        list.add(h.product(co, "Smart LED Bulb E26 6-Pack",
                "800lm colour-tunable smart bulbs with voice and app control. Works with Alexa, Google, and HomeKit.",
                "HOME-SLB-001", "44.99", "59.99", "Smart Home", "LumIQ",
                "https://placehold.co/800x800/fef9e7/333333?text=Smart+Bulb+6pk",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/fef9e7/333333?text=Smart+Bulb+6pk",
                        "https://placehold.co/800x800/fdf2d0/333333?text=Bulb+Scene"),
                h.attrs("Pack", "6 × E26 A21 bulbs",
                        "Lumens", "800lm (60W equiv.)",
                        "Colors", "16M RGBW + tunable white 2700–6500K",
                        "Control", "App, Alexa, Google, HomeKit"),
                h.options1("Pack Size", "6-Pack", "12-Pack"),
                p -> {
                    h.pv(p, "6-Pack",  "HOME-SLB-6P", new BigDecimal("44.99"), 100);
                    h.pv(p, "12-Pack", "HOME-SLB-12P", new BigDecimal("79.99"),  75);
                }));

        list.add(h.productSingle(co, "Smart Power Strip 8-Outlet",
                "8-outlet individually controlled Wi-Fi strip with 4 USB ports, 3000J surge protection, and energy monitoring.",
                "HOME-SPS-001", "49.99", "64.99", "Smart Home", "NestIQ",
                "https://placehold.co/800x800/1e272e/ffffff?text=Smart+Strip+8",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e272e/ffffff?text=Smart+Strip+8",
                        "https://placehold.co/800x800/17202a/ffffff?text=Strip+App"),
                h.attrs("Outlets", "8 AC individually switchable",
                        "USB", "2× USB-A + 2× USB-C",
                        "Surge", "3000J protection",
                        "Control", "App, Alexa, Google")));

        list.add(h.product(co, "Smart Thermostat Touchscreen",
                "7-day programmable smart thermostat with a 3.5\" colour display and energy savings reports.",
                "HOME-STH-001", "129.99", "169.99", "Smart Home", "NestIQ",
                "https://placehold.co/800x800/ecf0f1/333333?text=Smart+Thermostat",
                60, 5, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Smart+Thermostat",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Thermostat+UI"),
                h.attrs("Display", "3.5\" full-colour touch",
                        "Programs", "7-day scheduling",
                        "Savings", "Auto-away energy savings mode",
                        "Compatibility", "Most 24V heating and cooling systems"),
                h.options1("Color", "Satin Nickel", "Matte White"),
                p -> {
                    h.pv(p, "Satin Nickel", "HOME-STH-NKL", new BigDecimal("129.99"), 25);
                    h.pv(p, "Matte White",  "HOME-STH-WHT", new BigDecimal("129.99"), 22);
                }));

        list.add(h.productSingle(co, "Smart Video Doorbell 2K Wide",
                "2K wide-angle doorbell with 180° view, package detection, and 60-day local storage.",
                "HOME-SVD-001", "119.99", "149.99", "Smart Home", "NestIQ",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Doorbell+2K",
                70, 7, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Doorbell+2K",
                        "https://placehold.co/800x800/273746/ffffff?text=Doorbell+App"),
                h.attrs("Video", "2K 1520p, 180° wide-angle",
                        "Detection", "Person, package, vehicle",
                        "Storage", "60-day local microSD",
                        "Audio", "Two-way with echo cancellation")));

        list.add(h.productSingle(co, "Smart Indoor Pan-Tilt Camera",
                "1080p indoor camera with 360° pan, 90° tilt, two-way audio, and night vision.",
                "HOME-CAM-001", "39.99", "54.99", "Smart Home", "NestIQ",
                "https://placehold.co/800x800/1e272e/ffffff?text=Indoor+Camera",
                120, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e272e/ffffff?text=Indoor+Camera",
                        "https://placehold.co/800x800/17202a/ffffff?text=Camera+App"),
                h.attrs("Resolution", "1080p @ 30fps",
                        "Pan / Tilt", "360° / 90°",
                        "Night Vision", "Up to 8m infrared",
                        "Storage", "MicroSD up to 256GB")));

        list.add(h.productSingle(co, "Smart Smoke + CO Detector",
                "Dual-sensor smoke and carbon monoxide alarm with Wi-Fi alerts, voice announcements, and 10-year battery.",
                "HOME-SMK-001", "59.99", "79.99", "Smart Home", "NestIQ",
                "https://placehold.co/800x800/ecf0f1/333333?text=Smoke+CO",
                80, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Smoke+CO",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Alarm+Detail"),
                h.attrs("Sensors", "Dual photoelectric smoke + electrochemical CO",
                        "Battery", "10-year sealed lithium",
                        "Voice", "Voice alarm with location announcement",
                        "Control", "App alerts, silence, test")));

        list.add(h.product(co, "Smart Water Leak Sensor 3-Pack",
                "Wi-Fi water leak sensors with instant app alerts. Place under sinks, near appliances, or in basements.",
                "HOME-WLS-001", "39.99", "54.99", "Smart Home", "NestIQ",
                "https://placehold.co/800x800/1abc9c/ffffff?text=Leak+Sensor",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1abc9c/ffffff?text=Leak+Sensor",
                        "https://placehold.co/800x800/17a589/ffffff?text=Sensor+App"),
                h.attrs("Sensitivity", "Detects 1mm of water",
                        "Alert", "Instant app push + siren",
                        "Battery", "2× AAA, 2-year life",
                        "Range", "Wi-Fi 2.4GHz, no hub needed"),
                h.options1("Pack", "3-Pack", "6-Pack"),
                p -> {
                    h.pv(p, "3-Pack", "HOME-WLS-3P", new BigDecimal("39.99"), 50);
                    h.pv(p, "6-Pack", "HOME-WLS-6P", new BigDecimal("69.99"), 35);
                }));

        list.add(h.product(co, "Smart Motion Sensor 4-Pack",
                "Compact PIR motion sensors trigger automations, lights, or alerts. 7m range, 120° detection.",
                "HOME-MSN-001", "34.99", "49.99", "Smart Home", "LumIQ",
                "https://placehold.co/800x800/1e272e/ffffff?text=Motion+Sensor",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e272e/ffffff?text=Motion+Sensor",
                        "https://placehold.co/800x800/17202a/ffffff?text=Sensor+Mount"),
                h.attrs("Range", "7m detection, 120° field",
                        "Battery", "CR2450, 18-month life",
                        "Latency", "< 1 second response",
                        "Protocols", "Zigbee 3.0, works with Hub"),
                h.options1("Pack", "4-Pack", "8-Pack"),
                p -> {
                    h.pv(p, "4-Pack", "HOME-MSN-4P", new BigDecimal("34.99"), 55);
                    h.pv(p, "8-Pack", "HOME-MSN-8P", new BigDecimal("59.99"), 40);
                }));

        list.add(h.productSingle(co, "Smart Light Switch Single-Pole",
                "Single-pole smart Wi-Fi switch with physical paddle. No neutral wire required. Works with Alexa & Google.",
                "HOME-SWS-001", "24.99", "34.99", "Smart Home", "LumIQ",
                "https://placehold.co/800x800/ecf0f1/333333?text=Smart+Switch",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Smart+Switch",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Switch+Paddle"),
                h.attrs("Type", "Single-pole, no neutral required",
                        "Load", "600W incandescent / 150W LED",
                        "Control", "Physical paddle, app, voice",
                        "Scheduling", "Sunrise/sunset automations")));

        list.add(h.product(co, "Smart Plug Mini 2-Pack",
                "Compact Wi-Fi smart plugs that don't block the second outlet. Energy monitoring, timer, and voice control.",
                "HOME-SPL-001", "19.99", "26.99", "Smart Home", "NestIQ",
                "https://placehold.co/800x800/1e272e/ffffff?text=Smart+Plug+Mini",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e272e/ffffff?text=Smart+Plug+Mini",
                        "https://placehold.co/800x800/17202a/ffffff?text=Plug+App"),
                h.attrs("Size", "Mini — doesn't block adjacent outlet",
                        "Max Load", "15A / 1800W",
                        "Monitoring", "Real-time energy usage",
                        "Control", "App, Alexa, Google"),
                h.options1("Pack", "2-Pack", "4-Pack"),
                p -> {
                    h.pv(p, "2-Pack", "HOME-SPL-2P", new BigDecimal("19.99"), 100);
                    h.pv(p, "4-Pack", "HOME-SPL-4P", new BigDecimal("34.99"),  80);
                }));

        // ── Kitchen & Dining (12) ── overlaps WellnessWorld ──────────────────

        list.add(h.productSingle(co, "Ceramic Pour-Over Coffee Set",
                "Handcrafted ceramic dripper, walnut server, and gooseneck kettle. Minimalist aesthetic for the home barista.",
                "HOME-PCM-001", "64.99", "84.99", "Kitchen", "BrewHome",
                "https://placehold.co/800x800/f5cba7/333333?text=Ceramic+Pour+Over",
                90, 9, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f5cba7/333333?text=Ceramic+Pour+Over",
                        "https://placehold.co/800x800/f0b27a/333333?text=Pour+Over+Pour"),
                h.attrs("Dripper", "Hand-thrown ceramic, universal fit",
                        "Server", "350ml walnut-handled glass",
                        "Kettle", "650ml gooseneck with thermometer",
                        "Includes", "Dripper, server, kettle, 40 paper filters")));

        list.add(h.product(co, "Borosilicate French Press 1L",
                "1L borosilicate glass French press with stainless steel plunger and double-wall insulation.",
                "HOME-FPR-001", "34.99", "44.99", "Kitchen", "BrewHome",
                "https://placehold.co/800x800/4e342e/ffffff?text=French+Press",
                120, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/4e342e/ffffff?text=French+Press",
                        "https://placehold.co/800x800/3e2723/ffffff?text=Press+Plunger"),
                h.attrs("Capacity", "1L (8 cups)",
                        "Glass", "Borosilicate heat-resistant",
                        "Filter", "4-layer stainless steel mesh",
                        "Care", "Dishwasher safe"),
                h.options1("Size", "350ml (3 cup)", "1L (8 cup)"),
                p -> {
                    h.pv(p, "350ml (3 cup)", "HOME-FPR-350", new BigDecimal("24.99"), 60);
                    h.pv(p, "1L (8 cup)",    "HOME-FPR-1L",  new BigDecimal("34.99"), 55);
                }));

        list.add(h.productSingle(co, "Organic Bamboo Cutting Board Set",
                "3-piece end-grain bamboo board set with juice channels, non-slip feet, and built-in handles.",
                "HOME-BCB-001", "44.99", "59.99", "Kitchen", "HomeNest Co.",
                "https://placehold.co/800x800/f5cba7/333333?text=Bamboo+Boards",
                110, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f5cba7/333333?text=Bamboo+Boards",
                        "https://placehold.co/800x800/f0b27a/333333?text=Board+Grain"),
                h.attrs("Material", "End-grain organic bamboo (harder, self-healing)",
                        "Set", "3 boards: 30×20, 38×25, 45×30cm",
                        "Features", "Juice channels, built-in handles, non-slip feet",
                        "Care", "Hand wash, oil monthly")));

        list.add(h.productSingle(co, "Cast Iron Skillet 10\" Pre-Seasoned",
                "Pre-seasoned 10\" cast iron skillet. Works on all cooktops including induction and open flame.",
                "HOME-CIS-001", "44.99", "59.99", "Kitchen", "HomeNest Co.",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Cast+Iron",
                80, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Cast+Iron",
                        "https://placehold.co/800x800/273746/ffffff?text=Skillet+Handle"),
                h.attrs("Diameter", "10\" (25cm) cooking surface",
                        "Seasoning", "Pre-seasoned with flaxseed oil",
                        "Compatible", "Gas, electric, induction, oven, campfire",
                        "Weight", "2.7kg")));

        list.add(h.productSingle(co, "Stainless Steel Mixing Bowl Set",
                "5-piece nesting mixing bowl set with lids, non-slip base, and pour spout. Dishwasher safe.",
                "HOME-MXB-001", "39.99", "54.99", "Kitchen", "HomeNest Co.",
                "https://placehold.co/800x800/bdc3c7/333333?text=Mixing+Bowls",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/bdc3c7/333333?text=Mixing+Bowls",
                        "https://placehold.co/800x800/95a5a6/333333?text=Bowl+Set"),
                h.attrs("Sizes", "1, 1.5, 2, 3, 5 quart",
                        "Material", "18/8 stainless steel",
                        "Lids", "Airtight BPA-free lids included",
                        "Base", "Non-slip silicone bottom")));

        list.add(h.productSingle(co, "Silicone Baking Set 12-Piece",
                "Comprehensive silicone baking set: spatulas, brushes, tongs, cake mold, muffin tray, and more.",
                "HOME-SBS-001", "29.99", "39.99", "Kitchen", "HomeNest Co.",
                "https://placehold.co/800x800/e74c3c/ffffff?text=Baking+Set",
                130, 13, false, false, false, null, null,
                h.images("https://placehold.co/800x800/e74c3c/ffffff?text=Baking+Set",
                        "https://placehold.co/800x800/cb4335/ffffff?text=Silicone+Tools"),
                h.attrs("Pieces", "12: spatulas, brush, tongs, whisk, cake mold, muffin tray, mat",
                        "Material", "Food-grade platinum silicone",
                        "Safe to", "230°C / 450°F",
                        "Care", "Dishwasher safe")));

        list.add(h.product(co, "German Steel Chef's Knife 8\"",
                "Full-tang 8\" chef's knife in X50CrMoV15 steel with 15° edge angle and ergonomic pakkawood handle.",
                "HOME-CHK-001", "79.99", "109.99", "Kitchen", "HomeNest Co.",
                "https://placehold.co/800x800/bdc3c7/333333?text=Chef+Knife",
                75, 7, true, false, false, null, null,
                h.images("https://placehold.co/800x800/bdc3c7/333333?text=Chef+Knife",
                        "https://placehold.co/800x800/95a5a6/333333?text=Knife+Edge"),
                h.attrs("Steel", "X50CrMoV15 German stainless",
                        "Edge", "Hand-honed 15° per side",
                        "Handle", "Triple-riveted pakkawood",
                        "Full Tang", "Yes"),
                h.options1("Blade Length", "6\"", "8\"", "10\""),
                p -> {
                    h.pv(p, "6\"",  "HOME-CHK-6",  new BigDecimal("59.99"), 30);
                    h.pv(p, "8\"",  "HOME-CHK-8",  new BigDecimal("79.99"), 35);
                    h.pv(p, "10\"", "HOME-CHK-10", new BigDecimal("99.99"), 20);
                }));

        list.add(h.productSingle(co, "Digital Kitchen Scale 5kg",
                "5kg/0.1g precision kitchen scale with tare, unit conversion, and backlit display.",
                "HOME-KSC-001", "24.99", "34.99", "Kitchen", "HomeNest Co.",
                "https://placehold.co/800x800/ecf0f1/333333?text=Kitchen+Scale",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Kitchen+Scale",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Scale+Display"),
                h.attrs("Capacity", "5kg / 11lb",
                        "Precision", "0.1g / 0.1oz",
                        "Units", "g, kg, oz, lb, ml",
                        "Power", "2× AAA batteries")));

        list.add(h.productSingle(co, "Salad Spinner 5L BPA-Free",
                "5L salad spinner with one-hand pump, non-slip base, and brake button. Bowl doubles as serving dish.",
                "HOME-SSP-001", "29.99", "39.99", "Kitchen", "HomeNest Co.",
                "https://placehold.co/800x800/27ae60/ffffff?text=Salad+Spinner",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/27ae60/ffffff?text=Salad+Spinner",
                        "https://placehold.co/800x800/229954/ffffff?text=Spinner+Bowl"),
                h.attrs("Capacity", "5L",
                        "Mechanism", "One-hand centre pump",
                        "Features", "Brake button, non-slip base",
                        "Material", "BPA-free Tritan")));

        list.add(h.productSingle(co, "Granite Mortar and Pestle",
                "Unpolished granite mortar and pestle for herbs, spices, and pastes. 500ml capacity.",
                "HOME-MPT-001", "34.99", "44.99", "Kitchen", "HomeNest Co.",
                "https://placehold.co/800x800/707b7c/ffffff?text=Mortar+Pestle",
                90, 9, false, false, false, null, null,
                h.images("https://placehold.co/800x800/707b7c/ffffff?text=Mortar+Pestle",
                        "https://placehold.co/800x800/616a6b/ffffff?text=Granite+Grind"),
                h.attrs("Material", "Unpolished granite (coarse texture for grinding)",
                        "Capacity", "500ml mortar",
                        "Diameter", "16cm mortar",
                        "Weight", "2.8kg")));

        list.add(h.product(co, "Artisan Soy Candle Trio",
                "Three hand-poured soy wax candles in amber glass jars. 50-hour burn time each.",
                "HOME-SWC-001", "39.99", "54.99", "Home Fragrance", "NestScent",
                "https://placehold.co/800x800/fdebd0/333333?text=Candle+Trio",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/fdebd0/333333?text=Candle+Trio",
                        "https://placehold.co/800x800/fad7a0/333333?text=Candle+Amber"),
                h.attrs("Wax", "100% natural soy",
                        "Pack", "3 × 8oz amber glass jars",
                        "Burn Time", "50 hours per candle",
                        "Wicks", "Unbleached cotton — no zinc core"),
                h.options1("Scent Set", "Warm Woods (Cedar/Sandalwood/Patchouli)",
                        "Fresh Home (Linen/Green Tea/Bergamot)",
                        "Floral (Rose/Jasmine/Ylang Ylang)"),
                p -> {
                    h.pv(p, "Warm Woods",  "HOME-SWC-WRM", new BigDecimal("39.99"), 50);
                    h.pv(p, "Fresh Home",  "HOME-SWC-FSH", new BigDecimal("39.99"), 45);
                    h.pv(p, "Floral",      "HOME-SWC-FLR", new BigDecimal("39.99"), 40);
                }));

        list.add(h.product(co, "Ultrasonic Aromatherapy Diffuser 500ml",
                "500ml wood-grain diffuser with 7-colour LED, 4 timer settings, and whisper-quiet operation.",
                "HOME-EOD-001", "49.99", "64.99", "Home Fragrance", "NestScent",
                "https://placehold.co/800x800/d5e8d4/333333?text=Aroma+Diffuser",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/d5e8d4/333333?text=Aroma+Diffuser",
                        "https://placehold.co/800x800/c3d9c1/333333?text=Diffuser+Mist"),
                h.attrs("Capacity", "500ml",
                        "Coverage", "Up to 40 sq meters",
                        "Run Time", "Up to 16 hours on low",
                        "Noise Level", "< 25dB"),
                h.options1("Finish", "Light Wood", "Dark Wood"),
                p -> {
                    h.pv(p, "Light Wood", "HOME-EOD-LWD", new BigDecimal("49.99"), 48);
                    h.pv(p, "Dark Wood",  "HOME-EOD-DWD", new BigDecimal("49.99"), 42);
                }));

        // ── Bedding & Bath (10) ────────────────────────────────────────────────

        list.add(h.product(co, "Organic Cotton Duvet Cover",
                "GOTS-certified 400TC percale cotton duvet cover with button closure and internal ties.",
                "HOME-DVC-001", "89.99", "119.99", "Bedding", "HomeNest Co.",
                "https://placehold.co/800x800/fdfefe/333333?text=Duvet+Cover",
                80, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/fdfefe/333333?text=Duvet+Cover",
                        "https://placehold.co/800x800/f2f3f4/333333?text=Duvet+Detail"),
                h.attrs("Material", "400TC GOTS Organic Cotton Percale",
                        "Closure", "Button with internal corner ties",
                        "Weave", "Percale (crisp, cool hand feel)",
                        "Certification", "GOTS, OEKO-TEX"),
                h.options2("Size", "Twin", "Queen", "King", "Color", "White", "Stone", "Navy"),
                p -> h.addSizeColorVariants(p, "HOME-DVC",
                        new String[]{"Twin", "Queen", "King"},
                        new String[]{"White", "Stone", "Navy"},
                        "89.99", 9)));

        list.add(h.product(co, "Bamboo Lyocell Sheet Set",
                "4-piece Tencel-bamboo sheet set: silky soft, moisture-wicking, and temperature-regulating.",
                "HOME-BSH-001", "99.99", "129.99", "Bedding", "HomeNest Co.",
                "https://placehold.co/800x800/f2f3f4/333333?text=Bamboo+Sheets",
                70, 7, true, false, false, null, null,
                h.images("https://placehold.co/800x800/f2f3f4/333333?text=Bamboo+Sheets",
                        "https://placehold.co/800x800/eaecee/333333?text=Sheet+Texture"),
                h.attrs("Material", "70% Bamboo Lyocell, 30% Organic Cotton",
                        "Set", "Flat sheet, fitted sheet, 2 pillowcases",
                        "Fitted Depth", "Up to 40cm deep pocket",
                        "Care", "Machine wash cold, tumble dry low"),
                h.options2("Size", "Twin", "Queen", "King", "Color", "Ivory", "Sage", "Blush"),
                p -> h.addSizeColorVariants(p, "HOME-BSH",
                        new String[]{"Twin", "Queen", "King"},
                        new String[]{"Ivory", "Sage", "Blush"},
                        "99.99", 8)));

        list.add(h.product(co, "Weighted Blanket 15lb",
                "15lb glass-bead weighted blanket in a breathable cotton cover. Reduces anxiety and improves sleep.",
                "HOME-WBL-001", "79.99", "99.99", "Bedding", "HomeNest Co.",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Weighted+Blanket",
                75, 7, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Weighted+Blanket",
                        "https://placehold.co/800x800/273746/ffffff?text=Blanket+Bead"),
                h.attrs("Weight", "15lb (7kg) glass microbeads",
                        "Cover", "100% cotton breathable cover",
                        "Size", "48\" × 72\" (twin/throw size)",
                        "Care", "Machine washable"),
                h.options1("Weight", "10lb", "15lb", "20lb"),
                p -> {
                    h.pv(p, "10lb", "HOME-WBL-10", new BigDecimal("69.99"), 25);
                    h.pv(p, "15lb", "HOME-WBL-15", new BigDecimal("79.99"), 30);
                    h.pv(p, "20lb", "HOME-WBL-20", new BigDecimal("89.99"), 20);
                }));

        list.add(h.product(co, "Memory Foam Pillow 2-Pack",
                "Shredded memory foam pillow with cooling bamboo cover. Fully adjustable fill.",
                "HOME-MFP-001", "59.99", "79.99", "Bedding", "HomeNest Co.",
                "https://placehold.co/800x800/ecf0f1/333333?text=Memory+Pillow",
                90, 9, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Memory+Pillow",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Pillow+Pack"),
                h.attrs("Fill", "Shredded memory foam (adjustable)",
                        "Cover", "Cooling bamboo-derived viscose",
                        "Pack", "2 queen pillows",
                        "Care", "Washable cover, spot-clean fill"),
                h.options1("Size", "Standard (2-Pack)", "King (2-Pack)"),
                p -> {
                    h.pv(p, "Standard (2-Pack)", "HOME-MFP-STD", new BigDecimal("59.99"), 40);
                    h.pv(p, "King (2-Pack)",     "HOME-MFP-KNG", new BigDecimal("69.99"), 30);
                }));

        list.add(h.product(co, "Turkish Cotton Towel Set 6-Piece",
                "500gsm Aegean cotton towel set: 2 bath, 2 hand, 2 face. Soft on first use, plushier after each wash.",
                "HOME-TCT-001", "69.99", "89.99", "Bath", "HomeNest Co.",
                "https://placehold.co/800x800/d5dbdb/333333?text=Turkish+Towels",
                80, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/d5dbdb/333333?text=Turkish+Towels",
                        "https://placehold.co/800x800/ccd1d1/333333?text=Towel+Stack"),
                h.attrs("Material", "500gsm Turkish Aegean Cotton",
                        "Set", "2 bath (140×70cm), 2 hand (100×50cm), 2 face (30×30cm)",
                        "Feature", "Gets softer with each wash",
                        "Care", "Machine wash warm"),
                h.options1("Color", "White", "Stone Grey", "Sage Green", "Navy"),
                p -> {
                    h.pv(p, "White",       "HOME-TCT-WHT", new BigDecimal("69.99"), 25);
                    h.pv(p, "Stone Grey",  "HOME-TCT-STN", new BigDecimal("69.99"), 22);
                    h.pv(p, "Sage Green",  "HOME-TCT-SGE", new BigDecimal("69.99"), 20);
                    h.pv(p, "Navy",        "HOME-TCT-NVY", new BigDecimal("69.99"), 18);
                }));

        list.add(h.product(co, "Washed Linen Shower Curtain",
                "100% French flax linen shower curtain, pre-washed for a relaxed texture. Includes 12 rust-proof rings.",
                "HOME-SHC-001", "49.99", "64.99", "Bath", "HomeNest Co.",
                "https://placehold.co/800x800/f5f5f5/333333?text=Linen+Curtain",
                80, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f5f5f5/333333?text=Linen+Curtain",
                        "https://placehold.co/800x800/eeeeee/333333?text=Curtain+Detail"),
                h.attrs("Material", "100% French Flax Linen",
                        "Size", "72\" × 72\" standard",
                        "Includes", "Curtain + 12 rust-proof rings",
                        "Care", "Machine wash cold, line dry"),
                h.options1("Color", "Natural", "White", "Charcoal", "Dusty Blue"),
                p -> {
                    h.pv(p, "Natural",    "HOME-SHC-NAT", new BigDecimal("49.99"), 30);
                    h.pv(p, "White",      "HOME-SHC-WHT", new BigDecimal("49.99"), 28);
                    h.pv(p, "Charcoal",   "HOME-SHC-CHR", new BigDecimal("49.99"), 22);
                    h.pv(p, "Dusty Blue", "HOME-SHC-BLU", new BigDecimal("49.99"), 18);
                }));

        list.add(h.product(co, "Diatomite Stone Bath Mat",
                "Super-absorbent diatomite stone bath mat. Dries instantly, anti-slip, and naturally antimicrobial.",
                "HOME-DBM-001", "39.99", "54.99", "Bath", "HomeNest Co.",
                "https://placehold.co/800x800/ecf0f1/333333?text=Diatomite+Mat",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Diatomite+Mat",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Mat+Surface"),
                h.attrs("Material", "Natural diatomite (fossilised algae)",
                        "Size", "60 × 39cm",
                        "Drying", "Absorbs water instantly, dries in minutes",
                        "Anti-slip", "Cork anti-slip strips on base"),
                h.options1("Color", "Cream", "Grey"),
                p -> {
                    h.pv(p, "Cream", "HOME-DBM-CRM", new BigDecimal("39.99"), 45);
                    h.pv(p, "Grey",  "HOME-DBM-GRY", new BigDecimal("39.99"), 40);
                }));

        list.add(h.product(co, "Ceramic Soap Dispenser Set",
                "3-piece matte ceramic soap dispenser set: hand soap, lotion, and foaming pump.",
                "HOME-SDS-001", "34.99", "44.99", "Bath", "HomeNest Co.",
                "https://placehold.co/800x800/f5f5f5/333333?text=Soap+Dispenser",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f5f5f5/333333?text=Soap+Dispenser",
                        "https://placehold.co/800x800/eeeeee/333333?text=Dispenser+Set"),
                h.attrs("Set", "3 dispensers: hand soap, lotion, foaming",
                        "Material", "Matte glaze ceramic",
                        "Pump", "Stainless steel rust-proof pump",
                        "Capacity", "300ml each"),
                h.options1("Color", "Matte White", "Sage Green", "Terracotta"),
                p -> {
                    h.pv(p, "Matte White",  "HOME-SDS-WHT", new BigDecimal("34.99"), 38);
                    h.pv(p, "Sage Green",   "HOME-SDS-SGE", new BigDecimal("34.99"), 32);
                    h.pv(p, "Terracotta",   "HOME-SDS-TER", new BigDecimal("34.99"), 28);
                }));

        list.add(h.product(co, "Electric Heated Blanket Throw",
                "10 heat settings, machine washable, auto shut-off after 10 hours. Flannel with sherpa reverse.",
                "HOME-HTB-001", "59.99", "79.99", "Bedding", "HomeNest Co.",
                "https://placehold.co/800x800/c0392b/ffffff?text=Heated+Blanket",
                70, 7, false, false, false, null, null,
                h.images("https://placehold.co/800x800/c0392b/ffffff?text=Heated+Blanket",
                        "https://placehold.co/800x800/a93226/ffffff?text=Blanket+Control"),
                h.attrs("Heat Settings", "10 adjustable levels",
                        "Material", "Flannel top + sherpa reverse",
                        "Safety", "Auto shut-off at 10 hours",
                        "Care", "Machine washable"),
                h.options1("Size", "Throw (130×180cm)", "Queen (150×200cm)"),
                p -> {
                    h.pv(p, "Throw (130×180cm)", "HOME-HTB-THR", new BigDecimal("59.99"), 35);
                    h.pv(p, "Queen (150×200cm)", "HOME-HTB-QEN", new BigDecimal("79.99"), 28);
                }));

        list.add(h.product(co, "Mulberry Silk Eye Mask",
                "22 momme mulberry silk sleep mask with adjustable elastic strap. Blocks 100% of light.",
                "HOME-EMS-001", "24.99", "34.99", "Bedding", "HomeNest Co.",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Silk+Eye+Mask",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Silk+Eye+Mask",
                        "https://placehold.co/800x800/273746/ffffff?text=Mask+Strap"),
                h.attrs("Material", "22 momme 6A mulberry silk",
                        "Light Block", "100% blackout",
                        "Strap", "Adjustable, no-pull elastic",
                        "Care", "Hand wash cold"),
                h.options1("Color", "Midnight Black", "Ivory", "Dusty Rose"),
                p -> {
                    h.pv(p, "Midnight Black", "HOME-EMS-BLK", new BigDecimal("24.99"), 55);
                    h.pv(p, "Ivory",          "HOME-EMS-IVR", new BigDecimal("24.99"), 48);
                    h.pv(p, "Dusty Rose",     "HOME-EMS-RSE", new BigDecimal("24.99"), 40);
                }));

        // ── Storage & Organisation (8) ─────────────────────────────────────────

        list.add(h.productSingle(co, "Modular Closet Organiser System",
                "12-piece modular closet system with shelves, hanging rods, and drawers. No tools required.",
                "HOME-CLO-001", "89.99", "119.99", "Storage", "HomeNest Co.",
                "https://placehold.co/800x800/ecf0f1/333333?text=Closet+System",
                40, 4, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Closet+System",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Closet+Detail"),
                h.attrs("Pieces", "12: shelves, rods, drawers, hooks",
                        "Install", "No tools — tension + pressure mount",
                        "Max Load", "30kg per shelf",
                        "Adjustable", "Fully modular layout")));

        list.add(h.product(co, "Under-Bed Storage Bags 4-Pack",
                "XXL breathable non-woven under-bed storage bags with clear window, handles, and zip closure.",
                "HOME-UBB-001", "24.99", "34.99", "Storage", "HomeNest Co.",
                "https://placehold.co/800x800/ecf0f1/333333?text=Under+Bed+Bag",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Under+Bed+Bag",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Storage+Bag"),
                h.attrs("Pack", "4 bags",
                        "Size", "58 × 46 × 16cm each",
                        "Material", "Non-woven fabric, breathable",
                        "Features", "Clear window, zip closure, carry handles"),
                h.options1("Pack", "4-Pack", "8-Pack"),
                p -> {
                    h.pv(p, "4-Pack", "HOME-UBB-4P", new BigDecimal("24.99"), 70);
                    h.pv(p, "8-Pack", "HOME-UBB-8P", new BigDecimal("44.99"), 50);
                }));

        list.add(h.productSingle(co, "Stackable Drawer Organiser Set",
                "9-piece stackable bamboo drawer organiser set for cutlery, desk accessories, or cosmetics.",
                "HOME-SDO-001", "34.99", "44.99", "Storage", "HomeNest Co.",
                "https://placehold.co/800x800/f5cba7/333333?text=Drawer+Org",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f5cba7/333333?text=Drawer+Org",
                        "https://placehold.co/800x800/f0b27a/333333?text=Org+Stack"),
                h.attrs("Pieces", "9 bamboo dividers in varied sizes",
                        "Material", "Solid bamboo",
                        "Stackable", "Yes, interlocking design",
                        "Use", "Kitchen, desk, bathroom, closet")));

        list.add(h.product(co, "Rotating Spice Rack 24-Jar",
                "360° rotating lazy susan spice rack with 24 glass jars, labels, and chalk marker included.",
                "HOME-SPR-001", "39.99", "54.99", "Storage", "HomeNest Co.",
                "https://placehold.co/800x800/ecf0f1/333333?text=Spice+Rack",
                90, 9, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Spice+Rack",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Rack+Jars"),
                h.attrs("Jars", "24 × 4oz glass spice jars",
                        "Rotation", "360° smooth lazy susan base",
                        "Labels", "120 chalkboard labels + chalk marker",
                        "Lid", "Shaker + pour options"),
                h.options1("Style", "Acrylic Base", "Bamboo Base"),
                p -> {
                    h.pv(p, "Acrylic Base", "HOME-SPR-ACR", new BigDecimal("39.99"), 45);
                    h.pv(p, "Bamboo Base",  "HOME-SPR-BMB", new BigDecimal("44.99"), 40);
                }));

        list.add(h.productSingle(co, "Airtight Pantry Containers 10-Set",
                "10-piece airtight BPA-free pantry containers with labels and chalkboard pen. For flour, pasta, grains.",
                "HOME-PCT-001", "44.99", "59.99", "Storage", "HomeNest Co.",
                "https://placehold.co/800x800/ecf0f1/333333?text=Pantry+Jars",
                80, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Pantry+Jars",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Container+Set"),
                h.attrs("Set", "10 containers (4 sizes: 0.5L, 1L, 1.5L, 2L)",
                        "Material", "BPA-free Tritan with airtight lid",
                        "Labels", "Includes 100 labels and chalk pen",
                        "Stackable", "Yes")));

        list.add(h.product(co, "Foldable Storage Baskets 3-Pack",
                "Foldable seagrass-look fabric baskets. Sturdy rim and handles. For shelves, closets, and playrooms.",
                "HOME-CSB-001", "34.99", "44.99", "Storage", "HomeNest Co.",
                "https://placehold.co/800x800/d5cba7/333333?text=Storage+Baskets",
                110, 11, false, false, false, null, null,
                h.images("https://placehold.co/800x800/d5cba7/333333?text=Storage+Baskets",
                        "https://placehold.co/800x800/c5bb97/333333?text=Basket+Handle"),
                h.attrs("Pack", "3 baskets: S/M/L",
                        "Material", "Seagrass-look woven polyester",
                        "Handles", "Cotton rope handles",
                        "Foldable", "Collapses flat for storage"),
                h.options1("Color", "Natural", "Black", "Sage"),
                p -> {
                    h.pv(p, "Natural", "HOME-CSB-NAT", new BigDecimal("34.99"), 45);
                    h.pv(p, "Black",   "HOME-CSB-BLK", new BigDecimal("34.99"), 40);
                    h.pv(p, "Sage",    "HOME-CSB-SGE", new BigDecimal("34.99"), 35);
                }));

        list.add(h.productSingle(co, "Acrylic Desktop Organiser",
                "Clear acrylic desktop organiser with 5 compartments for stationery, makeup, or remote controls.",
                "HOME-ADO-001", "24.99", "34.99", "Storage", "HomeNest Co.",
                "https://placehold.co/800x800/d6eaf8/333333?text=Acrylic+Org",
                120, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/d6eaf8/333333?text=Acrylic+Org",
                        "https://placehold.co/800x800/c2d9f0/333333?text=Acrylic+Slots"),
                h.attrs("Compartments", "5 (varied heights and widths)",
                        "Material", "3mm thick crystal-clear acrylic",
                        "Dimensions", "25 × 15 × 12cm",
                        "Use", "Desk, vanity, kitchen countertop")));

        list.add(h.productSingle(co, "Wall-Mount Pegboard Kit",
                "48×60cm MDF pegboard with 30 hooks, shelves, and baskets. Paint-ready surface.",
                "HOME-PGB-001", "39.99", "54.99", "Storage", "HomeNest Co.",
                "https://placehold.co/800x800/ecf0f1/333333?text=Pegboard+Kit",
                60, 6, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Pegboard+Kit",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Pegboard+Hooks"),
                h.attrs("Size", "48 × 60cm",
                        "Material", "MDF, paint-ready",
                        "Accessories", "30 pieces: hooks, bins, shelves, screws",
                        "Mount", "Wall-mount hardware included")));

        // ── Home Décor & Lighting (10) ─────────────────────────────────────────

        list.add(h.product(co, "Ceramic Bud Vase Set 3-Piece",
                "Minimalist matte ceramic bud vases in three heights. Perfect for single stems or dried flowers.",
                "HOME-CVS-001", "34.99", "44.99", "Décor", "HomeNest Co.",
                "https://placehold.co/800x800/f5f5f5/333333?text=Bud+Vases",
                120, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f5f5f5/333333?text=Bud+Vases",
                        "https://placehold.co/800x800/eeeeee/333333?text=Vases+Detail"),
                h.attrs("Set", "3 vases: 10cm, 16cm, 22cm",
                        "Material", "Matte glaze ceramic",
                        "Style", "Organic minimalist",
                        "Use", "Fresh stems, dried flowers, pampas"),
                h.options1("Color", "Cream Set", "Earth Tones Set", "Sage Set"),
                p -> {
                    h.pv(p, "Cream Set",       "HOME-CVS-CRM", new BigDecimal("34.99"), 40);
                    h.pv(p, "Earth Tones Set", "HOME-CVS-ETH", new BigDecimal("34.99"), 35);
                    h.pv(p, "Sage Set",        "HOME-CVS-SGE", new BigDecimal("34.99"), 30);
                }));

        list.add(h.productSingle(co, "Boho Macramé Wall Hanging",
                "Handwoven cotton macramé wall hanging on a natural driftwood branch. 60 × 90cm.",
                "HOME-MWH-001", "44.99", "59.99", "Décor", "HomeNest Co.",
                "https://placehold.co/800x800/f5cba7/333333?text=Macrame",
                70, 7, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f5cba7/333333?text=Macrame",
                        "https://placehold.co/800x800/f0b27a/333333?text=Macrame+Detail"),
                h.attrs("Size", "60cm wide × 90cm tall",
                        "Material", "Natural cotton rope on driftwood",
                        "Style", "Boho fringe",
                        "Includes", "Wall mounting nail and hook")));

        list.add(h.product(co, "Linen Throw Pillow Covers 4-Pack",
                "4 linen blend throw pillow covers in a mix of textures. Hidden zip, washable.",
                "HOME-TPC-001", "39.99", "54.99", "Décor", "HomeNest Co.",
                "https://placehold.co/800x800/f5f5f5/333333?text=Throw+Pillows",
                120, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f5f5f5/333333?text=Throw+Pillows",
                        "https://placehold.co/800x800/eeeeee/333333?text=Pillow+Texture"),
                h.attrs("Pack", "4 covers (inserts not included)",
                        "Material", "55% Linen, 45% Cotton",
                        "Closure", "Hidden zip",
                        "Size", "45 × 45cm"),
                h.options1("Palette", "Neutral Tones", "Earth Tones", "Ocean Tones"),
                p -> {
                    h.pv(p, "Neutral Tones", "HOME-TPC-NEU", new BigDecimal("39.99"), 45);
                    h.pv(p, "Earth Tones",   "HOME-TPC-ETH", new BigDecimal("39.99"), 40);
                    h.pv(p, "Ocean Tones",   "HOME-TPC-OCN", new BigDecimal("39.99"), 35);
                }));

        list.add(h.product(co, "LED Floor Lamp Dimmable Arc",
                "Modern arc floor lamp with 3-colour modes, stepless dimming, and USB-C charging port in the base.",
                "HOME-FLP-001", "89.99", "119.99", "Lighting", "LumIQ",
                "https://placehold.co/800x800/fef9e7/333333?text=Arc+Floor+Lamp",
                50, 5, true, false, false, null, null,
                h.images("https://placehold.co/800x800/fef9e7/333333?text=Arc+Floor+Lamp",
                        "https://placehold.co/800x800/fdf2d0/333333?text=Lamp+Base"),
                h.attrs("Modes", "Warm white, neutral, cool daylight",
                        "Dimming", "Stepless foot pedal dimmer",
                        "USB-C", "30W USB-C port in base",
                        "Height", "Adjustable 155–185cm"),
                h.options1("Finish", "Matte Black", "Brushed Gold"),
                p -> {
                    h.pv(p, "Matte Black",   "HOME-FLP-BLK", new BigDecimal("89.99"), 20);
                    h.pv(p, "Brushed Gold",  "HOME-FLP-GLD", new BigDecimal("99.99"), 15);
                }));

        list.add(h.productSingle(co, "Clip-On Desk Lamp USB-C Charging",
                "Architect clip lamp with 5 colour temperatures, 10 brightness levels, and 18W USB-C port.",
                "HOME-DLP-001", "44.99", "59.99", "Lighting", "LumIQ",
                "https://placehold.co/800x800/1e272e/ffffff?text=Clip+Desk+Lamp",
                90, 9, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e272e/ffffff?text=Clip+Desk+Lamp",
                        "https://placehold.co/800x800/17202a/ffffff?text=Lamp+Arm"),
                h.attrs("Color Temps", "5 (2700K–6500K)",
                        "Brightness", "10 levels",
                        "USB-C", "18W charging port on base",
                        "Arm", "3-axis flexible arm")));

        list.add(h.product(co, "Chunky Knit Throw Blanket",
                "Hand-knitted arm-knit throw in merino-style acrylic yarn. 127×152cm. Cosy centrepiece for any sofa.",
                "HOME-CKT-001", "69.99", "89.99", "Décor", "HomeNest Co.",
                "https://placehold.co/800x800/f5cba7/333333?text=Chunky+Knit",
                80, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f5cba7/333333?text=Chunky+Knit",
                        "https://placehold.co/800x800/f0b27a/333333?text=Knit+Close"),
                h.attrs("Material", "100% soft acrylic yarn",
                        "Size", "127 × 152cm",
                        "Knit", "Giant arm-knit loops",
                        "Care", "Hand wash cold, lay flat to dry"),
                h.options1("Color", "Cream", "Warm Grey", "Blush", "Caramel"),
                p -> {
                    h.pv(p, "Cream",     "HOME-CKT-CRM", new BigDecimal("69.99"), 22);
                    h.pv(p, "Warm Grey", "HOME-CKT-GRY", new BigDecimal("69.99"), 20);
                    h.pv(p, "Blush",     "HOME-CKT-BLS", new BigDecimal("69.99"), 18);
                    h.pv(p, "Caramel",   "HOME-CKT-CAR", new BigDecimal("69.99"), 15);
                }));

        list.add(h.product(co, "3-Tier Plant Stand",
                "Solid bamboo 3-tier plant stand for indoor plants. Adjustable width, no tools required.",
                "HOME-PLS-001", "49.99", "64.99", "Décor", "HomeNest Co.",
                "https://placehold.co/800x800/27ae60/ffffff?text=Plant+Stand",
                70, 7, false, false, false, null, null,
                h.images("https://placehold.co/800x800/27ae60/ffffff?text=Plant+Stand",
                        "https://placehold.co/800x800/229954/ffffff?text=Stand+Plants"),
                h.attrs("Material", "FSC certified bamboo",
                        "Tiers", "3 staggered shelves",
                        "Adjustable", "Width adjustable 50–90cm",
                        "Max Load", "5kg per shelf"),
                h.options1("Finish", "Natural Bamboo", "Dark Bamboo"),
                p -> {
                    h.pv(p, "Natural Bamboo", "HOME-PLS-NAT", new BigDecimal("49.99"), 30);
                    h.pv(p, "Dark Bamboo",    "HOME-PLS-DRK", new BigDecimal("54.99"), 25);
                }));

        list.add(h.product(co, "Blackout Curtain Panels (2-Pack)",
                "100% blackout thermal insulated curtains with noise-reducing lining. Grommet top for easy hanging.",
                "HOME-BLC-001", "59.99", "79.99", "Décor", "HomeNest Co.",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Blackout+Curtains",
                90, 9, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Blackout+Curtains",
                        "https://placehold.co/800x800/273746/ffffff?text=Curtain+Grommet"),
                h.attrs("Light Block", "100% blackout",
                        "Thermal", "Triple-layer insulating lining",
                        "Header", "Grommet top (10 grommets per panel)",
                        "Panel Size", "52\" × 84\" per panel"),
                h.options2("Size", "52\"×63\"", "52\"×84\"", "52\"×96\"", "Color", "White", "Charcoal", "Navy"),
                p -> h.addSizeColorVariants(p, "HOME-BLC",
                        new String[]{"52x63", "52x84", "52x96"},
                        new String[]{"White", "Charcoal", "Navy"},
                        "59.99", 10)));

        list.add(h.productSingle(co, "Gallery Wall Frame Set 8-Piece",
                "8-piece black metal picture frame set in matching style: 2× 4×6\", 4× 5×7\", 2× 8×10\".",
                "HOME-GFS-001", "54.99", "69.99", "Décor", "HomeNest Co.",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Gallery+Frames",
                70, 7, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Gallery+Frames",
                        "https://placehold.co/800x800/273746/ffffff?text=Frames+Wall"),
                h.attrs("Set", "8 frames: 2×(4×6\"), 4×(5×7\"), 2×(8×10\")",
                        "Material", "Metal with shatter-resistant acrylic",
                        "Hang", "Portrait or landscape, wall + tabletop",
                        "Color", "Matte black")));

        list.add(h.product(co, "Marble Effect Decorative Tray Set",
                "Set of 2 oval marble-effect resin trays. Organise candles, vases, or perfumes on any surface.",
                "HOME-MTS-001", "29.99", "39.99", "Décor", "HomeNest Co.",
                "https://placehold.co/800x800/ecf0f1/333333?text=Marble+Tray",
                110, 11, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Marble+Tray",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Tray+Set"),
                h.attrs("Set", "2 oval trays: 30×20cm and 22×15cm",
                        "Material", "Resin with marble powder veining",
                        "Finish", "High-gloss sealed surface",
                        "Use", "Vanity, coffee table, kitchen counter"),
                h.options1("Colorway", "White & Gold Veining", "Black & Gold Veining", "Sage & White"),
                p -> {
                    h.pv(p, "White & Gold Veining", "HOME-MTS-WGV", new BigDecimal("29.99"), 40);
                    h.pv(p, "Black & Gold Veining", "HOME-MTS-BGV", new BigDecimal("29.99"), 35);
                    h.pv(p, "Sage & White",         "HOME-MTS-SGW", new BigDecimal("29.99"), 28);
                }));

        return list;
    }
}
