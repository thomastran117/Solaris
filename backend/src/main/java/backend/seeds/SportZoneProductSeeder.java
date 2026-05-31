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
 * SportZone product catalog — performance training, outdoor sports, and sports nutrition.
 *
 * Intentional category overlap with existing companies:
 *   - Activewear (running shorts, leggings, sports bra, compression, socks) → StyleHub
 *   - Fitness equipment (resistance bands, yoga mat, foam roller) → WellnessWorld
 *   - Sports nutrition (whey, BCAAs, pre-workout, creatine) → WellnessWorld
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class SportZoneProductSeeder {

    private final ProductSeedHelper h;

    public List<Product> seed(Company co) {
        List<Product> list = new ArrayList<>();

        // ── Performance Activewear (10) ── overlaps StyleHub ─────────────────

        list.add(h.product(co, "Elite Running Shorts 5\"",
                "Lightweight 4-way stretch running shorts with 5\" inseam, built-in liner brief, and back zip pocket.",
                "SPORT-QRS-001", "44.99", "59.99", "Activewear", "SpeedLine",
                "https://placehold.co/800x800/1abc9c/ffffff?text=Elite+Run+Shorts",
                200, 20, true, false, false, null, null,
                h.images("https://placehold.co/800x800/1abc9c/ffffff?text=Elite+Run+Shorts",
                        "https://placehold.co/800x800/17a589/ffffff?text=Shorts+Liner"),
                h.attrs("Material", "92% Polyester, 8% Elastane",
                        "Inseam", "5\" with liner brief",
                        "Pockets", "2 side + 1 back zip",
                        "Features", "Reflective details, UPF 30+"),
                h.options2("Size", "XS", "S", "M", "Color", "Black", "Cobalt", "Coral"),
                p -> h.addSizeColorVariants(p, "SPORT-QRS",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Black", "Cobalt", "Coral"},
                        "44.99", 17)));

        list.add(h.product(co, "Pro 7/8 Training Leggings",
                "7/8 length training leggings with squat-proof 4-way stretch, high waist, and side pocket.",
                "SPORT-YGL-001", "54.99", "69.99", "Activewear", "SpeedLine",
                "https://placehold.co/800x800/7d3c98/ffffff?text=Pro+Leggings",
                200, 20, true, false, false, null, null,
                h.images("https://placehold.co/800x800/7d3c98/ffffff?text=Pro+Leggings",
                        "https://placehold.co/800x800/6c3483/ffffff?text=Leggings+Pocket"),
                h.attrs("Material", "78% Nylon, 22% Spandex",
                        "Length", "7/8 (below calf)",
                        "Waist", "High waist, no roll-down",
                        "Features", "Squat-proof, side key pocket"),
                h.options2("Size", "XS", "S", "M", "Color", "Black", "Cobalt", "Violet"),
                p -> h.addSizeColorVariants(p, "SPORT-YGL",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Black", "Cobalt", "Violet"},
                        "54.99", 17)));

        list.add(h.product(co, "High-Impact Sports Bra",
                "High-impact sports bra with encapsulation support, moisture-wicking straps, and adjustable back closure.",
                "SPORT-SPB-001", "39.99", "54.99", "Activewear", "SpeedLine",
                "https://placehold.co/800x800/c0392b/ffffff?text=Sports+Bra+High",
                180, 18, false, false, false, null, null,
                h.images("https://placehold.co/800x800/c0392b/ffffff?text=Sports+Bra+High",
                        "https://placehold.co/800x800/a93226/ffffff?text=Bra+Back"),
                h.attrs("Material", "80% Nylon, 20% Spandex",
                        "Support", "High impact encapsulation",
                        "Back", "Adjustable back closure",
                        "Cups", "Moulded removable cups"),
                h.options2("Size", "XS", "S", "M", "Color", "Black", "Crimson", "Teal"),
                p -> h.addSizeColorVariants(p, "SPORT-SPB",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Black", "Crimson", "Teal"},
                        "39.99", 15)));

        list.add(h.product(co, "Pro Compression Shorts 9\"",
                "9\" graduated compression shorts with flat-lock seams and sweat-transfer waistband pocket.",
                "SPORT-CMP-001", "39.99", "54.99", "Activewear", "SpeedLine",
                "https://placehold.co/800x800/1c2833/ffffff?text=Compression+9in",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Compression+9in",
                        "https://placehold.co/800x800/17202a/ffffff?text=Comp+Short+Side"),
                h.attrs("Material", "85% Nylon, 15% Spandex",
                        "Inseam", "9\"",
                        "Compression", "Graduated 20–30mmHg",
                        "Pocket", "Hidden waistband key pocket"),
                h.options2("Size", "XS", "S", "M", "Color", "Black", "Charcoal", "x"),
                p -> h.addSizeColorVariants(p, "SPORT-CMP",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Black", "Charcoal"},
                        "39.99", 25)));

        list.add(h.product(co, "Arch Support Athletic Socks 6-Pack",
                "Technical running socks with targeted cushioning, blister tabs, and Y-heel fit. 6-pair pack.",
                "SPORT-SCK-001", "24.99", "34.99", "Activewear", "SpeedLine",
                "https://placehold.co/800x800/ecf0f1/333333?text=Sport+Socks",
                250, 25, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Sport+Socks",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Sock+Cushion"),
                h.attrs("Material", "72% Merino Wool, 26% Nylon, 2% Spandex",
                        "Pack", "6 pairs",
                        "Features", "Arch support, blister tab, Y-heel, cushioned sole",
                        "Height", "Crew (mid-calf)"),
                h.options1("Size", "S/M (US 4–8)", "L/XL (US 9–13)"),
                p -> {
                    h.pv(p, "S/M (US 4–8)",   "SPORT-SCK-SM", new BigDecimal("24.99"), 100);
                    h.pv(p, "L/XL (US 9–13)", "SPORT-SCK-LX", new BigDecimal("24.99"),  90);
                }));

        list.add(h.product(co, "Mesh Workout Tank",
                "Lightweight mesh panel training tank with ventilation zones and anti-odour treatment.",
                "SPORT-WKT-001", "29.99", "39.99", "Activewear", "SpeedLine",
                "https://placehold.co/800x800/1abc9c/ffffff?text=Mesh+Tank",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1abc9c/ffffff?text=Mesh+Tank",
                        "https://placehold.co/800x800/17a589/ffffff?text=Tank+Mesh"),
                h.attrs("Material", "100% Recycled Polyester mesh",
                        "Features", "Moisture-wick, anti-odour, UPF 30+",
                        "Fit", "Relaxed athletic",
                        "Care", "Machine wash cold"),
                h.options2("Size", "XS", "S", "M", "Color", "Black", "White", "Cobalt"),
                p -> h.addSizeColorVariants(p, "SPORT-WKT",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Black", "White", "Cobalt"},
                        "29.99", 17)));

        list.add(h.product(co, "Quarter-Zip Training Top",
                "Mid-layer quarter-zip in stretch fleece. Thumbhole cuffs and reflective chest zip pull.",
                "SPORT-QZP-001", "59.99", "79.99", "Activewear", "SpeedLine",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Quarter+Zip",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Quarter+Zip",
                        "https://placehold.co/800x800/273746/ffffff?text=QZ+Cuff"),
                h.attrs("Material", "88% Polyester, 12% Elastane stretch fleece",
                        "Features", "Thumbhole cuffs, reflective zip pull",
                        "Zip", "25cm quarter zip",
                        "Care", "Machine wash cold"),
                h.options2("Size", "XS", "S", "M", "Color", "Black", "Navy", "Graphite"),
                p -> h.addSizeColorVariants(p, "SPORT-QZP",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Black", "Navy", "Graphite"},
                        "59.99", 13)));

        list.add(h.product(co, "Tapered Training Joggers",
                "Tapered fit joggers with zip ankle, elastic waistband with drawstring, and zip pockets.",
                "SPORT-JGR-001", "54.99", "69.99", "Activewear", "SpeedLine",
                "https://placehold.co/800x800/212f3c/ffffff?text=Training+Joggers",
                160, 16, false, false, false, null, null,
                h.images("https://placehold.co/800x800/212f3c/ffffff?text=Training+Joggers",
                        "https://placehold.co/800x800/1c2833/ffffff?text=Jogger+Ankle"),
                h.attrs("Material", "87% Polyester, 13% Spandex French terry",
                        "Fit", "Tapered",
                        "Features", "Zip ankle, drawstring, 2 zip hand pockets",
                        "Care", "Machine wash cold"),
                h.options2("Size", "XS", "S", "M", "Color", "Black", "Charcoal", "x"),
                p -> h.addSizeColorVariants(p, "SPORT-JGR",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Black", "Charcoal"},
                        "54.99", 20)));

        list.add(h.product(co, "Packable Wind Running Jacket",
                "Ultralight wind and water-resistant running jacket. Packs into chest pocket. Reflective 360°.",
                "SPORT-RJK-001", "79.99", "99.99", "Activewear", "SpeedLine",
                "https://placehold.co/800x800/1abc9c/ffffff?text=Run+Jacket",
                120, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1abc9c/ffffff?text=Run+Jacket",
                        "https://placehold.co/800x800/17a589/ffffff?text=Jacket+Packed"),
                h.attrs("Material", "15D ripstop nylon",
                        "DWR", "Water-resistant finish",
                        "Reflective", "360° reflective print",
                        "Packable", "Into own chest pocket"),
                h.options2("Size", "XS", "S", "M", "Color", "Neon Yellow", "Black", "Cobalt"),
                p -> h.addSizeColorVariants(p, "SPORT-RJK",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Neon Yellow", "Black", "Cobalt"},
                        "79.99", 10)));

        list.add(h.product(co, "Perforated Running Cap",
                "Perforated mesh running cap with sweat-wicking sweatband and adjustable back closure.",
                "SPORT-CAP-001", "24.99", "32.99", "Activewear", "SpeedLine",
                "https://placehold.co/800x800/1c2833/ffffff?text=Running+Cap",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Running+Cap",
                        "https://placehold.co/800x800/17202a/ffffff?text=Cap+Vent"),
                h.attrs("Material", "Perforated moisture-wicking polyester",
                        "Sweatband", "Absorbent terry sweatband",
                        "Closure", "Stretch-fit + adjustable back",
                        "UPF", "UPF 30+"),
                h.options1("Color", "Black", "White", "Navy", "Neon Yellow"),
                p -> {
                    h.pv(p, "Black",        "SPORT-CAP-BLK", new BigDecimal("24.99"), 55);
                    h.pv(p, "White",        "SPORT-CAP-WHT", new BigDecimal("24.99"), 48);
                    h.pv(p, "Navy",         "SPORT-CAP-NVY", new BigDecimal("24.99"), 40);
                    h.pv(p, "Neon Yellow",  "SPORT-CAP-NYL", new BigDecimal("24.99"), 35);
                }));

        // ── Fitness Equipment (12) ── overlaps WellnessWorld ─────────────────

        list.add(h.product(co, "Resistance Band Pro Set",
                "6-band set from 5–50lbs with premium carabiners, padded handles, and door anchor. Tube-style.",
                "SPORT-RBS-001", "34.99", "49.99", "Fitness Equipment", "IronGrip",
                "https://placehold.co/800x800/e74c3c/ffffff?text=Resistance+Pro",
                180, 18, false, false, false, null, null,
                h.images("https://placehold.co/800x800/e74c3c/ffffff?text=Resistance+Pro",
                        "https://placehold.co/800x800/cb4335/ffffff?text=Band+Handle"),
                h.attrs("Bands", "6 tube bands: 5, 10, 15, 20, 30, 50 lbs",
                        "Handles", "Foam-padded non-slip handles",
                        "Accessories", "Door anchor, ankle straps, carry bag",
                        "Material", "100% natural latex"),
                h.options1("Set", "6-Band Pro Set", "12-Band Ultimate Set"),
                p -> {
                    h.pv(p, "6-Band Pro Set",      "SPORT-RBS-6P", new BigDecimal("34.99"), 75);
                    h.pv(p, "12-Band Ultimate Set", "SPORT-RBS-12P", new BigDecimal("59.99"), 50);
                }));

        list.add(h.product(co, "Pro Yoga Mat Non-Slip 6mm",
                "6mm dual-layer non-slip yoga mat with alignment markings and a carry strap. Eco-friendly TPE.",
                "SPORT-YGM-001", "49.99", "64.99", "Fitness Equipment", "FlexZone",
                "https://placehold.co/800x800/7dcea0/ffffff?text=Pro+Yoga+Mat",
                120, 12, true, false, false, null, null,
                h.images("https://placehold.co/800x800/7dcea0/ffffff?text=Pro+Yoga+Mat",
                        "https://placehold.co/800x800/58d68d/ffffff?text=Mat+Align"),
                h.attrs("Material", "Eco-friendly TPE (no latex, no PVC)",
                        "Thickness", "6mm dual-layer",
                        "Size", "183 × 61cm",
                        "Features", "Body alignment lines, carry strap"),
                h.options1("Color", "Black/Grey", "Teal/Lime", "Purple/Pink"),
                p -> {
                    h.pv(p, "Black/Grey",   "SPORT-YGM-BLK", new BigDecimal("49.99"), 40);
                    h.pv(p, "Teal/Lime",    "SPORT-YGM-TEL", new BigDecimal("49.99"), 35);
                    h.pv(p, "Purple/Pink",  "SPORT-YGM-PRP", new BigDecimal("49.99"), 30);
                }));

        list.add(h.product(co, "High-Density Foam Roller 33cm",
                "High-density EVA foam roller with ridged surface for deep tissue myofascial release.",
                "SPORT-FRL-001", "34.99", "44.99", "Fitness Equipment", "FlexZone",
                "https://placehold.co/800x800/2e86c1/ffffff?text=HD+Foam+Roller",
                130, 13, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2e86c1/ffffff?text=HD+Foam+Roller",
                        "https://placehold.co/800x800/2874a6/ffffff?text=Roller+Ridge"),
                h.attrs("Material", "Extra-firm EVA foam",
                        "Ridges", "Multi-depth grid pattern",
                        "Diameter", "15cm × 33cm",
                        "Capacity", "150kg"),
                h.options1("Color", "Black", "Blue", "Orange"),
                p -> {
                    h.pv(p, "Black",  "SPORT-FRL-BLK", new BigDecimal("34.99"), 50);
                    h.pv(p, "Blue",   "SPORT-FRL-BLU", new BigDecimal("34.99"), 45);
                    h.pv(p, "Orange", "SPORT-FRL-ORG", new BigDecimal("34.99"), 35);
                }));

        list.add(h.product(co, "Adjustable Dumbbell Set 5–52.5lb",
                "Single dumbbell replaces 15 weights from 5 to 52.5lb. Quick-turn dial selector.",
                "SPORT-ADB-001", "249.99", "319.99", "Fitness Equipment", "IronGrip",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Adj+Dumbbells",
                30, 3, true, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Adj+Dumbbells",
                        "https://placehold.co/800x800/273746/ffffff?text=Dumbbell+Dial"),
                h.attrs("Range", "5–52.5 lbs in 2.5lb increments",
                        "Replaces", "15 sets of dumbbells",
                        "Selector", "Turn-dial in 2.5lb steps",
                        "Includes", "2 dumbbells + 2 trays"),
                h.options1("Set", "Single (1 dumbbell)", "Pair (2 dumbbells)"),
                p -> {
                    h.pv(p, "Single (1 dumbbell)", "SPORT-ADB-1X", new BigDecimal("149.99"), 15);
                    h.pv(p, "Pair (2 dumbbells)",  "SPORT-ADB-2X", new BigDecimal("249.99"), 12);
                }));

        list.add(h.productSingle(co, "Multi-Grip Pull-Up Bar",
                "Doorframe pull-up bar with 12 grip positions. No screws — pressure mount up to 120kg.",
                "SPORT-PUB-001", "39.99", "54.99", "Fitness Equipment", "IronGrip",
                "https://placehold.co/800x800/1c2833/ffffff?text=Pull+Up+Bar",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Pull+Up+Bar",
                        "https://placehold.co/800x800/17202a/ffffff?text=Bar+Grip"),
                h.attrs("Grips", "12 positions: wide, neutral, close, push-up",
                        "Capacity", "120kg",
                        "Mount", "Pressure mount (no screws)",
                        "Door Width", "Fits 62–100cm doorframes")));

        list.add(h.productSingle(co, "Battle Rope 1.5\" × 30ft",
                "1.5\" diameter poly Dacron battle rope with heat-shrink handles. Anchors to included wall mount.",
                "SPORT-BRP-001", "79.99", "99.99", "Fitness Equipment", "IronGrip",
                "https://placehold.co/800x800/1a252f/ffffff?text=Battle+Rope",
                50, 5, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a252f/ffffff?text=Battle+Rope",
                        "https://placehold.co/800x800/17202a/ffffff?text=Rope+Handle"),
                h.attrs("Diameter", "1.5\" (38mm)",
                        "Length", "30ft (9.1m)",
                        "Material", "Poly Dacron, rot-resistant",
                        "Includes", "Rope + wall anchor + chalk bag")));

        list.add(h.product(co, "Adjustable Kettlebell 12–32kg",
                "Single adjustable kettlebell with 6 weight settings from 12 to 32kg. Quick-release pin lock.",
                "SPORT-KTB-001", "179.99", "229.99", "Fitness Equipment", "IronGrip",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Adj+Kettlebell",
                35, 3, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Adj+Kettlebell",
                        "https://placehold.co/800x800/273746/ffffff?text=Kettlebell+Pin"),
                h.attrs("Range", "12, 16, 20, 24, 28, 32kg",
                        "Mechanism", "Quick-release pin",
                        "Handle", "Standard 35mm competition-style handle",
                        "Base", "Flat base for push-up use"),
                h.options1("Start Weight", "12–32kg (Standard)", "8–24kg (Compact)"),
                p -> {
                    h.pv(p, "12–32kg (Standard)", "SPORT-KTB-STD", new BigDecimal("179.99"), 18);
                    h.pv(p, "8–24kg (Compact)",   "SPORT-KTB-CMP", new BigDecimal("149.99"), 15);
                }));

        list.add(h.productSingle(co, "Speed Jump Rope with Counter",
                "Ball-bearing speed rope with 3mm PVC cable, digital counter handles, and adjustable length.",
                "SPORT-JMP-001", "24.99", "34.99", "Fitness Equipment", "FlexZone",
                "https://placehold.co/800x800/1abc9c/ffffff?text=Speed+Rope",
                180, 18, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1abc9c/ffffff?text=Speed+Rope",
                        "https://placehold.co/800x800/17a589/ffffff?text=Rope+Handle"),
                h.attrs("Cable", "3mm PVC speed cable",
                        "Bearings", "360° ball bearings per handle",
                        "Counter", "Digital jump counter in handle",
                        "Length", "Adjustable up to 3m")));

        list.add(h.productSingle(co, "Ab Wheel Roller with Knee Pad",
                "Dual-wheel ab roller with wider stability, non-slip handles, and thick foam knee pad.",
                "SPORT-ABW-001", "24.99", "34.99", "Fitness Equipment", "FlexZone",
                "https://placehold.co/800x800/1c2833/ffffff?text=Ab+Roller",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Ab+Roller",
                        "https://placehold.co/800x800/17202a/ffffff?text=Roller+Pad"),
                h.attrs("Wheel", "Dual 15cm wheels, 16cm wide stance",
                        "Handles", "Non-slip foam-wrapped handles",
                        "Knee Pad", "50mm thick foam knee pad included",
                        "Capacity", "150kg")));

        list.add(h.productSingle(co, "Wooden Gymnastic Rings 28mm",
                "28mm birch wood gymnastic rings with 4.5m adjustable straps and metal buckles.",
                "SPORT-GYR-001", "39.99", "54.99", "Fitness Equipment", "IronGrip",
                "https://placehold.co/800x800/d4a574/ffffff?text=Gym+Rings",
                80, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/d4a574/ffffff?text=Gym+Rings",
                        "https://placehold.co/800x800/c8956c/ffffff?text=Rings+Strap"),
                h.attrs("Material", "28mm sanded birch wood",
                        "Straps", "4.5m nylon, adjustable",
                        "Buckles", "Stainless steel cam-lock",
                        "Certified", "FIG-compliant dimensions")));

        list.add(h.product(co, "Slam Ball Non-Bounce",
                "Dead-bounce slam ball with textured grip shell. Won't bounce or roll off. For power throws.",
                "SPORT-SLM-001", "39.99", "54.99", "Fitness Equipment", "IronGrip",
                "https://placehold.co/800x800/1c2833/ffffff?text=Slam+Ball",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Slam+Ball",
                        "https://placehold.co/800x800/17202a/ffffff?text=Ball+Grip"),
                h.attrs("Fill", "Sand-fill, dead bounce",
                        "Shell", "Textured rubber, dual-layer",
                        "Grip", "Pebbled anti-slip surface",
                        "Sizes", "Available 10, 15, 20, 25, 30lb"),
                h.options1("Weight", "10lb", "15lb", "20lb", "25lb"),
                p -> {
                    h.pv(p, "10lb", "SPORT-SLM-10", new BigDecimal("29.99"), 28);
                    h.pv(p, "15lb", "SPORT-SLM-15", new BigDecimal("39.99"), 25);
                    h.pv(p, "20lb", "SPORT-SLM-20", new BigDecimal("49.99"), 20);
                    h.pv(p, "25lb", "SPORT-SLM-25", new BigDecimal("59.99"), 15);
                }));

        list.add(h.productSingle(co, "Agility Ladder 12-Rung",
                "12-rung flat agility ladder with adjustable rung spacing and carry bag. For speed and footwork drills.",
                "SPORT-AGL-001", "19.99", "29.99", "Fitness Equipment", "FlexZone",
                "https://placehold.co/800x800/f39c12/ffffff?text=Agility+Ladder",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f39c12/ffffff?text=Agility+Ladder",
                        "https://placehold.co/800x800/e67e22/ffffff?text=Ladder+Flat"),
                h.attrs("Rungs", "12 adjustable-spacing rungs",
                        "Length", "6m total (50cm per rung)",
                        "Material", "Durable polypropylene rungs + nylon rails",
                        "Includes", "Ladder + cones + carry bag")));

        // ── Sports Nutrition (8) ── overlaps WellnessWorld ───────────────────

        list.add(h.product(co, "Whey Isolate Protein CFM",
                "Cross-flow micro-filtered whey isolate with 30g protein per serving. Fast-absorbing, minimal lactose.",
                "SPORT-WPP-001", "59.99", "79.99", "Nutrition", "FuelPro",
                "https://placehold.co/800x800/d6eaf8/333333?text=Whey+Isolate",
                130, 13, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/d6eaf8/333333?text=Whey+Isolate",
                        "https://placehold.co/800x800/c2d9f0/333333?text=Isolate+Label"),
                h.attrs("Protein", "30g per serving (whey isolate)",
                        "Process", "CFM — cross-flow micro-filtered",
                        "Lactose", "< 1g per serving",
                        "Servings", "25 per bag"),
                h.options2("Flavour", "Double Chocolate", "Vanilla Bean", "Strawberry", "Size", "2lb", "5lb", "x"),
                p -> {
                    h.pv2(p, "Double Chocolate", "2lb", "SPORT-WPP-DC2", new BigDecimal("49.99"), 35);
                    h.pv2(p, "Double Chocolate", "5lb", "SPORT-WPP-DC5", new BigDecimal("99.99"), 25);
                    h.pv2(p, "Vanilla Bean",     "2lb", "SPORT-WPP-VB2", new BigDecimal("49.99"), 30);
                    h.pv2(p, "Vanilla Bean",     "5lb", "SPORT-WPP-VB5", new BigDecimal("99.99"), 22);
                    h.pv2(p, "Strawberry",       "2lb", "SPORT-WPP-ST2", new BigDecimal("49.99"), 25);
                    h.pv2(p, "Strawberry",       "5lb", "SPORT-WPP-ST5", new BigDecimal("99.99"), 18);
                }));

        list.add(h.product(co, "Intra-Workout BCAA + Electrolytes",
                "Intra-workout formula: 7g BCAAs (3:1:2), 1g beta-alanine, and full electrolyte complex.",
                "SPORT-BCA-001", "34.99", "44.99", "Nutrition", "FuelPro",
                "https://placehold.co/800x800/c8e6c9/333333?text=BCAA+Electro",
                130, 13, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/c8e6c9/333333?text=BCAA+Electro",
                        "https://placehold.co/800x800/a5d6a7/333333?text=BCAA+Sachet"),
                h.attrs("BCAAs", "7g (3:1:2 Leucine:Iso:Val)",
                        "Electrolytes", "Sodium, Potassium, Magnesium, Zinc",
                        "Beta-Alanine", "1g",
                        "Servings", "30 per tub"),
                h.options1("Flavour", "Mango Citrus", "Watermelon Mint", "Grape"),
                p -> {
                    h.pv(p, "Mango Citrus",    "SPORT-BCA-MGO", new BigDecimal("34.99"), 45);
                    h.pv(p, "Watermelon Mint", "SPORT-BCA-WTR", new BigDecimal("34.99"), 40);
                    h.pv(p, "Grape",           "SPORT-BCA-GRP", new BigDecimal("34.99"), 35);
                }));

        list.add(h.product(co, "Pre-Workout Stim-Free Formula",
                "Stimulant-free pre-workout: 8g citrulline, 3.2g beta-alanine, 2g betaine. No caffeine crash.",
                "SPORT-PRW-001", "44.99", "59.99", "Nutrition", "FuelPro",
                "https://placehold.co/800x800/ff7043/ffffff?text=Stim+Free+Pre",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ff7043/ffffff?text=Stim+Free+Pre",
                        "https://placehold.co/800x800/f4511e/ffffff?text=PreW+Scoop"),
                h.attrs("Citrulline", "8g L-Citrulline Malate 2:1",
                        "Beta-Alanine", "3.2g",
                        "Betaine", "2g anhydrous",
                        "Caffeine", "Zero caffeine"),
                h.options1("Flavour", "Pink Lemonade", "Sour Apple", "Peach Mango"),
                p -> {
                    h.pv(p, "Pink Lemonade", "SPORT-PRW-PNK", new BigDecimal("44.99"), 35);
                    h.pv(p, "Sour Apple",    "SPORT-PRW-APL", new BigDecimal("44.99"), 30);
                    h.pv(p, "Peach Mango",   "SPORT-PRW-PCH", new BigDecimal("44.99"), 28);
                }));

        list.add(h.product(co, "Creatine Monohydrate Micronised",
                "Unflavoured Creapure® creatine monohydrate. Mixes clear in water. 5g per serving.",
                "SPORT-CRE-001", "27.99", "37.99", "Nutrition", "FuelPro",
                "https://placehold.co/800x800/e3f2fd/333333?text=Creatine+Micro",
                150, 15, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/e3f2fd/333333?text=Creatine+Micro",
                        "https://placehold.co/800x800/bbdefb/333333?text=Creatine+Mix"),
                h.attrs("Grade", "Creapure® certified (Germany)",
                        "Per Serving", "5g",
                        "Purity", "99.9% creatine monohydrate",
                        "Flavour", "Completely unflavoured"),
                h.options1("Size", "300g (60 servings)", "500g (100 servings)"),
                p -> {
                    h.pv(p, "300g (60 servings)",  "SPORT-CRE-300", new BigDecimal("27.99"), 70);
                    h.pv(p, "500g (100 servings)", "SPORT-CRE-500", new BigDecimal("39.99"), 55);
                }));

        list.add(h.product(co, "Energy Gel 12-Pack",
                "22g fast-carb energy gels with 50mg caffeine and electrolytes. Thin consistency, easy to swallow.",
                "SPORT-EGP-001", "24.99", "34.99", "Nutrition", "FuelPro",
                "https://placehold.co/800x800/f39c12/ffffff?text=Energy+Gels",
                160, 16, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f39c12/ffffff?text=Energy+Gels",
                        "https://placehold.co/800x800/e67e22/ffffff?text=Gel+Packet"),
                h.attrs("Carbs", "22g fast carbohydrates (maltodextrin:fructose 2:1)",
                        "Caffeine", "50mg (caffeinated) or 0mg",
                        "Electrolytes", "Sodium, Potassium",
                        "Pack", "12 × 40g gels"),
                h.options1("Flavour", "Tropical (Caffeinated)", "Berry (Caffeinated)", "Orange (Caffeine-Free)"),
                p -> {
                    h.pv(p, "Tropical (Caffeinated)",   "SPORT-EGP-TRP", new BigDecimal("24.99"), 60);
                    h.pv(p, "Berry (Caffeinated)",       "SPORT-EGP-BER", new BigDecimal("24.99"), 55);
                    h.pv(p, "Orange (Caffeine-Free)",    "SPORT-EGP-ORG", new BigDecimal("24.99"), 48);
                }));

        list.add(h.productSingle(co, "Electrolyte Chews 30-Pack",
                "Portable chewable electrolyte tablets with 300mg sodium, 75mg potassium, and 25mg magnesium.",
                "SPORT-ELC-001", "19.99", "26.99", "Nutrition", "FuelPro",
                "https://placehold.co/800x800/b3e5fc/333333?text=Electro+Chews",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/b3e5fc/333333?text=Electro+Chews",
                        "https://placehold.co/800x800/81d4fa/333333?text=Chew+Roll"),
                h.attrs("Per Serving (2 chews)", "300mg Na, 75mg K, 25mg Mg",
                        "Pack", "30 chews (15 servings)",
                        "Flavour", "Citrus Salt",
                        "Use", "During long workouts or in heat")));

        list.add(h.product(co, "Protein Bar Box 12-Pack",
                "20g protein, 4g sugar, 250 cal per bar. Coated in dark chocolate with crunchy quinoa.",
                "SPORT-PBR-001", "29.99", "39.99", "Nutrition", "FuelPro",
                "https://placehold.co/800x800/4e342e/ffffff?text=Protein+Bars",
                120, 12, false, true, false, "MONTHLY:1", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/4e342e/ffffff?text=Protein+Bars",
                        "https://placehold.co/800x800/3e2723/ffffff?text=Bar+Cross"),
                h.attrs("Protein", "20g per bar (whey + milk protein)",
                        "Sugar", "4g",
                        "Calories", "250 kcal",
                        "Pack", "12 bars"),
                h.options1("Flavour", "Double Choc", "Peanut Butter Choc", "Salted Caramel"),
                p -> {
                    h.pv(p, "Double Choc",          "SPORT-PBR-DCH", new BigDecimal("29.99"), 50);
                    h.pv(p, "Peanut Butter Choc",   "SPORT-PBR-PBC", new BigDecimal("29.99"), 45);
                    h.pv(p, "Salted Caramel",        "SPORT-PBR-SLC", new BigDecimal("29.99"), 40);
                }));

        list.add(h.product(co, "Carbohydrate Sports Drink Mix",
                "30g carbs per serving with 2:1 maltodextrin:fructose ratio for sustained energy and fast hydration.",
                "SPORT-SDM-001", "24.99", "34.99", "Nutrition", "FuelPro",
                "https://placehold.co/800x800/1e8449/ffffff?text=Sports+Drink",
                130, 13, false, true, false, "MONTHLY:1,WEEKLY:4", new BigDecimal("10.00"),
                h.images("https://placehold.co/800x800/1e8449/ffffff?text=Sports+Drink",
                        "https://placehold.co/800x800/196f3d/ffffff?text=Drink+Bottle"),
                h.attrs("Carbs", "30g per serving (2:1 malto:fructose)",
                        "Electrolytes", "Sodium, Potassium, Magnesium",
                        "Servings", "30 per bag",
                        "Use", "During events 60–90+ minutes"),
                h.options1("Flavour", "Lemon Lime", "Orange", "Berry"),
                p -> {
                    h.pv(p, "Lemon Lime", "SPORT-SDM-LLM", new BigDecimal("24.99"), 50);
                    h.pv(p, "Orange",     "SPORT-SDM-ORG", new BigDecimal("24.99"), 45);
                    h.pv(p, "Berry",      "SPORT-SDM-BER", new BigDecimal("24.99"), 40);
                }));

        // ── Outdoor & Endurance (10) ──────────────────────────────────────────

        list.add(h.product(co, "Trail Running Shoes",
                "Aggressive lugged trail shoe with Vibram outsole, rock plate, and breathable mesh upper.",
                "SPORT-TRS-001", "119.99", "149.99", "Footwear", "SpeedLine",
                "https://placehold.co/800x800/5d4037/ffffff?text=Trail+Shoes",
                90, 9, true, false, false, null, null,
                h.images("https://placehold.co/800x800/5d4037/ffffff?text=Trail+Shoes",
                        "https://placehold.co/800x800/4e342e/ffffff?text=Trail+Sole"),
                h.attrs("Outsole", "Vibram Megagrip",
                        "Rock Plate", "6mm full-length TPU",
                        "Drop", "8mm heel-to-toe",
                        "Upper", "Engineered mesh with toe cap"),
                h.options2("Size", "8", "9", "10", "Color", "Black/Red", "Blue/Orange", "x"),
                p -> h.addSizeColorVariants(p, "SPORT-TRS",
                        new String[]{"8", "9", "10", "11"},
                        new String[]{"Black/Red", "Blue/Orange"},
                        "119.99", 11)));

        list.add(h.productSingle(co, "GPS Running Watch",
                "GPS sports watch with HR, SpO2, barometric altimeter, and 20-hour battery in GPS mode.",
                "SPORT-GPW-001", "179.99", "229.99", "Accessories", "SpeedLine",
                "https://placehold.co/800x800/2c3e50/ffffff?text=GPS+Watch",
                55, 5, true, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=GPS+Watch",
                        "https://placehold.co/800x800/273746/ffffff?text=Watch+Data"),
                h.attrs("GPS", "Multi-GNSS: GPS + GLONASS + Galileo",
                        "Sensors", "HR, SpO2, barometric altimeter, compass",
                        "Battery", "20h GPS / 30 days smartwatch",
                        "Waterproof", "5 ATM (50m)")));

        list.add(h.product(co, "Padded Cycling Shorts",
                "3/4 length padded cycling shorts with 4D chamois, silicone gripper, and compression fit.",
                "SPORT-CYS-001", "59.99", "79.99", "Activewear", "SpeedLine",
                "https://placehold.co/800x800/1c2833/ffffff?text=Cycle+Shorts",
                120, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Cycle+Shorts",
                        "https://placehold.co/800x800/17202a/ffffff?text=Shorts+Pad"),
                h.attrs("Length", "3/4 length (knee)",
                        "Pad", "4D multi-density chamois",
                        "Waist", "Wide elastic + silicone gripper",
                        "Material", "85D compression lycra"),
                h.options2("Size", "XS", "S", "M", "Color", "Black", "Midnight Blue", "x"),
                p -> h.addSizeColorVariants(p, "SPORT-CYS",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Black", "Midnight Blue"},
                        "59.99", 15)));

        list.add(h.product(co, "Running Hydration Vest 5L",
                "5L trail vest with 1.5L soft flask compatibility, 10 pockets, and bounce-free fit.",
                "SPORT-HVT-001", "89.99", "119.99", "Accessories", "SpeedLine",
                "https://placehold.co/800x800/1abc9c/ffffff?text=Hydration+Vest",
                50, 5, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1abc9c/ffffff?text=Hydration+Vest",
                        "https://placehold.co/800x800/17a589/ffffff?text=Vest+Pockets"),
                h.attrs("Capacity", "5L total storage",
                        "Flask", "Compatible with 500ml soft flasks (2 included)",
                        "Pockets", "10 pockets (front, back, hip)",
                        "Fit", "Adjustable sternum + waist"),
                h.options1("Size", "XS/S", "M/L"),
                p -> {
                    h.pv(p, "XS/S", "SPORT-HVT-XS", new BigDecimal("89.99"), 22);
                    h.pv(p, "M/L",  "SPORT-HVT-ML", new BigDecimal("89.99"), 25);
                }));

        list.add(h.product(co, "Compression Calf Sleeves",
                "Graduated compression calf sleeves 20–30mmHg for running recovery and shin splint prevention.",
                "SPORT-CCS-001", "24.99", "34.99", "Accessories", "SpeedLine",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Calf+Sleeves",
                160, 16, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Calf+Sleeves",
                        "https://placehold.co/800x800/273746/ffffff?text=Sleeve+Pair"),
                h.attrs("Compression", "Graduated 20–30mmHg",
                        "Material", "80% Nylon, 20% Spandex",
                        "Pack", "1 pair",
                        "Use", "Running, cycling, travel"),
                h.options1("Size", "S/M (ankle 19–23cm)", "L/XL (ankle 24–28cm)"),
                p -> {
                    h.pv(p, "S/M (ankle 19–23cm)", "SPORT-CCS-SM", new BigDecimal("24.99"), 70);
                    h.pv(p, "L/XL (ankle 24–28cm)","SPORT-CCS-LX", new BigDecimal("24.99"), 60);
                }));

        list.add(h.product(co, "Kinesiology Tape 5-Roll Pack",
                "Elastic kinesiology tape for muscle support, pain relief, and injury prevention. 5m × 5cm per roll.",
                "SPORT-KTT-001", "19.99", "26.99", "Accessories", "FlexZone",
                "https://placehold.co/800x800/e74c3c/ffffff?text=K-Tape",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/e74c3c/ffffff?text=K-Tape",
                        "https://placehold.co/800x800/cb4335/ffffff?text=Tape+Applied"),
                h.attrs("Size", "5m × 5cm per roll",
                        "Pack", "5 rolls",
                        "Material", "100% cotton + acrylic adhesive",
                        "Water Resistant", "Yes"),
                h.options1("Color", "Black", "Beige", "Blue", "Pink"),
                p -> {
                    h.pv(p, "Black", "SPORT-KTT-BLK", new BigDecimal("19.99"), 55);
                    h.pv(p, "Beige", "SPORT-KTT-BGE", new BigDecimal("19.99"), 50);
                    h.pv(p, "Blue",  "SPORT-KTT-BLU", new BigDecimal("19.99"), 45);
                    h.pv(p, "Pink",  "SPORT-KTT-PNK", new BigDecimal("19.99"), 40);
                }));

        list.add(h.product(co, "Cork Yoga Blocks (2-Pack)",
                "Non-slip cork yoga blocks in two densities. Eco-friendly, antimicrobial, and durable.",
                "SPORT-YBK-001", "29.99", "39.99", "Fitness Equipment", "FlexZone",
                "https://placehold.co/800x800/d4a574/ffffff?text=Cork+Blocks",
                140, 14, false, false, false, null, null,
                h.images("https://placehold.co/800x800/d4a574/ffffff?text=Cork+Blocks",
                        "https://placehold.co/800x800/c8956c/ffffff?text=Block+Pair"),
                h.attrs("Material", "Natural cork (antimicrobial)",
                        "Pack", "2 blocks",
                        "Dimensions", "23 × 15 × 10cm standard",
                        "Weight", "180g each"),
                h.options1("Density", "Standard Firm", "Extra Dense"),
                p -> {
                    h.pv(p, "Standard Firm", "SPORT-YBK-STD", new BigDecimal("29.99"), 60);
                    h.pv(p, "Extra Dense",   "SPORT-YBK-XDN", new BigDecimal("34.99"), 45);
                }));

        list.add(h.productSingle(co, "Medicine Ball 10lb Slam Grip",
                "10lb medicine ball with textured grip surface and dead-bounce design for wall balls and slams.",
                "SPORT-MDB-001", "34.99", "44.99", "Fitness Equipment", "IronGrip",
                "https://placehold.co/800x800/1c2833/ffffff?text=Medicine+Ball",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Medicine+Ball",
                        "https://placehold.co/800x800/17202a/ffffff?text=Med+Ball+Grip"),
                h.attrs("Weight", "10lb (4.5kg)",
                        "Diameter", "27cm",
                        "Shell", "Durable rubber, textured grip",
                        "Bounce", "Minimal bounce for slams")));

        list.add(h.productSingle(co, "Climbing Chalk Block 300g",
                "300g compressed magnesium carbonate chalk block. Low dust formula for gym and outdoor use.",
                "SPORT-CLK-001", "9.99", "14.99", "Accessories", "IronGrip",
                "https://placehold.co/800x800/ecf0f1/333333?text=Chalk+Block",
                250, 25, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Chalk+Block",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Chalk+Crumble"),
                h.attrs("Weight", "300g block",
                        "Formula", "Low-dust MgCO3",
                        "Grade", "Gym and outdoor climbing",
                        "Pack", "Single block, resealable bag")));

        list.add(h.productSingle(co, "Sport Sunscreen SPF 50 Stick",
                "SPF 50 sport sunscreen stick. Sweat-proof, water-resistant 80 minutes. No white cast.",
                "SPORT-SPF-001", "14.99", "19.99", "Accessories", "SpeedLine",
                "https://placehold.co/800x800/fff9c4/333333?text=Sport+SPF+50",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/fff9c4/333333?text=Sport+SPF+50",
                        "https://placehold.co/800x800/fff176/333333?text=SPF+Stick"),
                h.attrs("SPF", "SPF 50 broad spectrum",
                        "Form", "Solid stick (no mess, no hands)",
                        "Water Resistance", "80 minutes",
                        "Size", "50g stick")));

        // ── Gear & Accessories (10) ────────────────────────────────────────────

        list.add(h.product(co, "Gym Duffle Bag 45L",
                "45L ventilated duffle with wet/dry compartment, laptop sleeve, and roller luggage strap.",
                "SPORT-GBG-001", "59.99", "79.99", "Accessories", "SpeedLine",
                "https://placehold.co/800x800/1c2833/ffffff?text=Gym+Duffle",
                80, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Gym+Duffle",
                        "https://placehold.co/800x800/17202a/ffffff?text=Duffle+Inside"),
                h.attrs("Volume", "45L",
                        "Compartments", "Main + wet/dry + laptop (up to 15\")",
                        "Straps", "Shoulder strap + carry handles + luggage pass-through",
                        "Material", "600D ripstop nylon"),
                h.options1("Color", "Matte Black", "Navy", "Olive"),
                p -> {
                    h.pv(p, "Matte Black", "SPORT-GBG-BLK", new BigDecimal("59.99"), 30);
                    h.pv(p, "Navy",        "SPORT-GBG-NVY", new BigDecimal("59.99"), 25);
                    h.pv(p, "Olive",       "SPORT-GBG-OLV", new BigDecimal("59.99"), 20);
                }));

        list.add(h.product(co, "Insulated Sports Water Bottle 32oz",
                "Triple-wall vacuum insulated 32oz bottle. Keeps cold 24h. Wide-mouth for ice and cleaning.",
                "SPORT-WBT-001", "34.99", "44.99", "Accessories", "SpeedLine",
                "https://placehold.co/800x800/1abc9c/ffffff?text=Sport+Bottle",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1abc9c/ffffff?text=Sport+Bottle",
                        "https://placehold.co/800x800/17a589/ffffff?text=Bottle+Lid"),
                h.attrs("Capacity", "32oz (950ml)",
                        "Insulation", "Triple-wall vacuum",
                        "Cold", "24h / Hot: 12h",
                        "Lid", "Leak-proof sports lid + carry loop"),
                h.options1("Color", "Black", "Cobalt", "Forest Green", "White"),
                p -> {
                    h.pv(p, "Black",        "SPORT-WBT-BLK", new BigDecimal("34.99"), 55);
                    h.pv(p, "Cobalt",       "SPORT-WBT-CBL", new BigDecimal("34.99"), 48);
                    h.pv(p, "Forest Green", "SPORT-WBT-GRN", new BigDecimal("34.99"), 40);
                    h.pv(p, "White",        "SPORT-WBT-WHT", new BigDecimal("34.99"), 35);
                }));

        list.add(h.productSingle(co, "Running Phone Armband",
                "Universal running armband for phones up to 7\". Touch-screen window, reflective strip, key pocket.",
                "SPORT-ARB-001", "19.99", "26.99", "Accessories", "SpeedLine",
                "https://placehold.co/800x800/1c2833/ffffff?text=Phone+Armband",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Phone+Armband",
                        "https://placehold.co/800x800/17202a/ffffff?text=Armband+Key"),
                h.attrs("Fit", "Universal — phones up to 7\"",
                        "Window", "Touch-screen compatible",
                        "Pocket", "Key pocket",
                        "Reflective", "360° reflective strip")));

        list.add(h.product(co, "Waterproof Sport Earbuds IPX8",
                "IPX8 waterproof sport earbuds with bone-conduction option for situational awareness. 9-hour battery.",
                "SPORT-EAR-001", "79.99", "99.99", "Accessories", "SpeedLine",
                "https://placehold.co/800x800/1abc9c/ffffff?text=Sport+Earbuds",
                90, 9, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1abc9c/ffffff?text=Sport+Earbuds",
                        "https://placehold.co/800x800/17a589/ffffff?text=Earbuds+Bud"),
                h.attrs("Rating", "IPX8 (1m for 30min)",
                        "Battery", "9h buds + 27h case",
                        "Awareness", "Ambient transparency mode",
                        "Fit", "Sport wing tip in 3 sizes"),
                h.options1("Color", "Black", "Cobalt"),
                p -> {
                    h.pv(p, "Black",  "SPORT-EAR-BLK", new BigDecimal("79.99"), 38);
                    h.pv(p, "Cobalt", "SPORT-EAR-CBL", new BigDecimal("79.99"), 30);
                }));

        list.add(h.productSingle(co, "Leather Gym Gloves",
                "Full-palm leather training gloves with wrist wrap support and silicone grip palm.",
                "SPORT-GLV-001", "29.99", "39.99", "Accessories", "IronGrip",
                "https://placehold.co/800x800/5d4037/ffffff?text=Gym+Gloves",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/5d4037/ffffff?text=Gym+Gloves",
                        "https://placehold.co/800x800/4e342e/ffffff?text=Gloves+Palm"),
                h.attrs("Material", "Full-grain leather palm + mesh back",
                        "Wrist", "Velcro wrap wrist support",
                        "Padding", "Silicone gel palm insert",
                        "Closure", "Velcro cuff")));

        list.add(h.productSingle(co, "Velcro Powerlifting Belt",
                "4\" wide 10mm lever-free velcro powerlifting belt. Approved for powerlifting training.",
                "SPORT-LFB-001", "49.99", "64.99", "Accessories", "IronGrip",
                "https://placehold.co/800x800/1c2833/ffffff?text=Lifting+Belt",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Lifting+Belt",
                        "https://placehold.co/800x800/17202a/ffffff?text=Belt+Clasp"),
                h.attrs("Width", "4\" (10cm) uniform width",
                        "Thickness", "10mm",
                        "Closure", "Industrial velcro",
                        "Material", "Genuine leather")));

        list.add(h.productSingle(co, "Anti-Fog Swim Goggles",
                "Racing-style swim goggles with UV400, anti-fog coating, and 5 interchangeable nose bridges.",
                "SPORT-SGG-001", "19.99", "26.99", "Accessories", "SpeedLine",
                "https://placehold.co/800x800/1e8bc3/ffffff?text=Swim+Goggles",
                180, 18, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e8bc3/ffffff?text=Swim+Goggles",
                        "https://placehold.co/800x800/1a78aa/ffffff?text=Goggles+Lens"),
                h.attrs("Lens", "UV400, anti-fog coated",
                        "Gasket", "Soft silicone",
                        "Fit", "5 interchangeable nose bridges",
                        "Strap", "Dual silicone strap")));

        list.add(h.productSingle(co, "Mesh Sports Drawstring Bag",
                "Breathable mesh drawstring bag with zippered front pocket. For gym kit, shoes, or beach gear.",
                "SPORT-DBG-001", "12.99", "17.99", "Accessories", "SpeedLine",
                "https://placehold.co/800x800/1abc9c/ffffff?text=Mesh+Drawstring",
                250, 25, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1abc9c/ffffff?text=Mesh+Drawstring",
                        "https://placehold.co/800x800/17a589/ffffff?text=Bag+Pocket"),
                h.attrs("Material", "Breathable mesh polyester",
                        "Capacity", "15L",
                        "Pocket", "Zippered front organiser pocket",
                        "Strap", "Reinforced drawstring")));

        list.add(h.productSingle(co, "Lifting Wrist Straps Pair",
                "Figure-8 cotton lifting straps with neoprene wrist padding. For deadlifts, rows, and pull-downs.",
                "SPORT-WLS-001", "14.99", "19.99", "Accessories", "IronGrip",
                "https://placehold.co/800x800/1c2833/ffffff?text=Lifting+Straps",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Lifting+Straps",
                        "https://placehold.co/800x800/17202a/ffffff?text=Strap+Wrap"),
                h.attrs("Style", "Figure-8 loop",
                        "Material", "Cotton canvas + neoprene wrist pad",
                        "Length", "60cm",
                        "Use", "Deadlift, shrugs, rows, pull-downs")));

        list.add(h.product(co, "Lacrosse Ball Massage Set",
                "4-pack lacrosse balls for trigger point and myofascial release. Firm density, washable.",
                "SPORT-MBS-001", "19.99", "26.99", "Fitness Equipment", "FlexZone",
                "https://placehold.co/800x800/e74c3c/ffffff?text=Massage+Balls",
                180, 18, false, false, false, null, null,
                h.images("https://placehold.co/800x800/e74c3c/ffffff?text=Massage+Balls",
                        "https://placehold.co/800x800/cb4335/ffffff?text=Ball+Set"),
                h.attrs("Pack", "4 lacrosse balls",
                        "Density", "Firm solid rubber",
                        "Diameter", "6.4cm (standard lacrosse size)",
                        "Use", "Foot, shoulder, glute, upper back release"),
                h.options1("Color", "Black Set", "Color Mix Set"),
                p -> {
                    h.pv(p, "Black Set",     "SPORT-MBS-BLK", new BigDecimal("19.99"), 80);
                    h.pv(p, "Color Mix Set", "SPORT-MBS-MIX", new BigDecimal("19.99"), 70);
                }));

        return list;
    }
}
