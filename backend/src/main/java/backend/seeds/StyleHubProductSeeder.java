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
public class StyleHubProductSeeder {

    private final ProductSeedHelper h;

    public List<Product> seed(Company co) {
        List<Product> list = new ArrayList<>();

        list.add(h.product(co, "Premium Organic Cotton T-Shirt",
                "Certified GOTS organic cotton. Relaxed everyday fit with reinforced shoulder seams. Pre-shrunk.",
                "STYLE-TCO-001", "34.99", "44.99", "Clothing", "EcoThread",
                "https://placehold.co/800x800/f5cba7/333333?text=Organic+Tee",
                300, 20, true, false, false, null, null,
                h.images("https://placehold.co/800x800/f5cba7/333333?text=Organic+Tee",
                        "https://placehold.co/800x800/f0b27a/333333?text=Tee+Back",
                        "https://placehold.co/800x800/e59866/333333?text=Tee+Detail"),
                h.attrs("Material", "100% GOTS Organic Cotton",
                        "Fit", "Relaxed",
                        "Care", "Machine wash cold, tumble dry low",
                        "Certification", "GOTS certified"),
                h.options2("Size", "XS", "S", "M", "Color", "White", "Black", "Navy"),
                p -> h.addSizeColorVariants(p, "STYLE-TCO",
                        new String[]{"XS", "S", "M", "L", "XL"},
                        new String[]{"White", "Black", "Navy"},
                        "34.99", 20)));

        list.add(h.product(co, "Slim-Fit Stretch Denim Jeans",
                "2% elastane stretch denim for all-day comfort. Classic 5-pocket with YKK zipper.",
                "STYLE-DNM-001", "79.99", "99.99", "Clothing", "DenimCraft",
                "https://placehold.co/800x800/2471a3/ffffff?text=Denim+Jeans",
                200, 15, true, false, false, null, null,
                h.images("https://placehold.co/800x800/2471a3/ffffff?text=Denim+Jeans",
                        "https://placehold.co/800x800/1f618d/ffffff?text=Jeans+Back"),
                h.attrs("Material", "98% Cotton, 2% Elastane",
                        "Fit", "Slim",
                        "Rise", "Mid-rise",
                        "Care", "Machine wash cold, hang dry"),
                h.options2("Waist", "30\"", "32\"", "34\"", "Inseam", "30\"", "32\"", "x"),
                p -> h.addSizeColorVariants(p, "STYLE-DNM",
                        new String[]{"30\"x30\"", "30\"x32\"", "32\"x30\"", "32\"x32\"", "34\"x30\"", "34\"x32\""},
                        new String[]{"Dark Wash", "Medium Wash", "Light Wash"},
                        "79.99", 11)));

        list.add(h.product(co, "Heavyweight Zip Hoodie",
                "500gsm French terry fleece with a front kangaroo pocket and YKK full-zip. Brushed interior for warmth.",
                "STYLE-HZH-001", "89.99", "109.99", "Clothing", "CozyWear",
                "https://placehold.co/800x800/5d6d7e/ffffff?text=Zip+Hoodie",
                200, 15, true, false, false, null, null,
                h.images("https://placehold.co/800x800/5d6d7e/ffffff?text=Zip+Hoodie",
                        "https://placehold.co/800x800/4d5d6e/ffffff?text=Hoodie+Front"),
                h.attrs("Material", "500gsm French Terry (80% Cotton, 20% Polyester)",
                        "Interior", "Soft brushed fleece",
                        "Zipper", "YKK full-zip",
                        "Care", "Machine wash warm"),
                h.options2("Size", "S", "M", "L", "Color", "Charcoal", "Burgundy", "Navy"),
                p -> h.addSizeColorVariants(p, "STYLE-HZH",
                        new String[]{"S", "M", "L", "XL"},
                        new String[]{"Charcoal", "Burgundy", "Navy"},
                        "89.99", 17)));

        list.add(h.product(co, "Quick-Dry Running Shorts",
                "4-way stretch fabric with moisture-wicking liner and back zip pocket. Reflective details for low-light runs.",
                "STYLE-QRS-001", "39.99", "49.99", "Active", "SwiftGear",
                "https://placehold.co/800x800/1abc9c/ffffff?text=Run+Shorts",
                250, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1abc9c/ffffff?text=Run+Shorts",
                        "https://placehold.co/800x800/17a589/ffffff?text=Shorts+Back"),
                h.attrs("Material", "88% Polyester, 12% Spandex",
                        "Features", "4-way stretch, moisture-wicking, UPF 30",
                        "Inseam", "5\" or 7\" option",
                        "Pockets", "2 side + 1 back zip"),
                h.options2("Size", "XS", "S", "M", "Color", "Teal", "Black", "Coral"),
                p -> h.addSizeColorVariants(p, "STYLE-QRS",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Teal", "Black", "Coral"},
                        "39.99", 21)));

        list.add(h.product(co, "Classic Canvas Sneakers",
                "Vulcanized canvas upper with rubber cupsole. Ortholite insole for all-day cushioning.",
                "STYLE-SNK-001", "64.99", "84.99", "Footwear", "StepCraft",
                "https://placehold.co/800x800/f9ebea/333333?text=Canvas+Sneakers",
                180, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f9ebea/333333?text=Canvas+Sneakers",
                        "https://placehold.co/800x800/f2d7d5/333333?text=Sneaker+Side",
                        "https://placehold.co/800x800/ebdef0/333333?text=Sneaker+Sole"),
                h.attrs("Upper", "100% Canvas",
                        "Sole", "Vulcanized rubber cupsole",
                        "Insole", "Ortholite removable",
                        "Closure", "Lace-up"),
                h.options1("Size", "6", "7", "8", "9"),
                p -> {
                    h.pv(p, "6", "STYLE-SNK-6", new BigDecimal("64.99"), 22);
                    h.pv(p, "7", "STYLE-SNK-7", new BigDecimal("64.99"), 28);
                    h.pv(p, "8", "STYLE-SNK-8", new BigDecimal("64.99"), 35);
                    h.pv(p, "9", "STYLE-SNK-9", new BigDecimal("64.99"), 30);
                }));

        list.add(h.product(co, "Linen Button-Down Shirt",
                "Breathable European linen with a relaxed camp collar. Perfect for warm weather.",
                "STYLE-LBS-001", "54.99", "69.99", "Clothing", "EcoThread",
                "https://placehold.co/800x800/fad7a0/333333?text=Linen+Shirt",
                150, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/fad7a0/333333?text=Linen+Shirt",
                        "https://placehold.co/800x800/f8c471/333333?text=Shirt+Back"),
                h.attrs("Material", "100% European Linen",
                        "Collar", "Camp collar",
                        "Fit", "Relaxed",
                        "Care", "Machine wash cold, line dry"),
                h.options2("Size", "S", "M", "L", "Color", "Natural", "Sky Blue", "Sage"),
                p -> h.addSizeColorVariants(p, "STYLE-LBS",
                        new String[]{"S", "M", "L", "XL"},
                        new String[]{"Natural", "Sky Blue", "Sage"},
                        "54.99", 13)));

        list.add(h.product(co, "Yoga Leggings",
                "Buttery-soft 4-way stretch fabric with high waist and hidden waistband pocket. Squat-proof.",
                "STYLE-YGL-001", "49.99", "64.99", "Active", "SwiftGear",
                "https://placehold.co/800x800/7d3c98/ffffff?text=Yoga+Leggings",
                200, 15, true, false, false, null, null,
                h.images("https://placehold.co/800x800/7d3c98/ffffff?text=Yoga+Leggings",
                        "https://placehold.co/800x800/6c3483/ffffff?text=Leggings+Side"),
                h.attrs("Material", "79% Nylon, 21% Spandex",
                        "Waist", "High-waist with hidden pocket",
                        "Length", "Full length 28\" inseam",
                        "Features", "Squat-proof, moisture-wicking"),
                h.options2("Size", "XS", "S", "M", "Color", "Plum", "Midnight", "Dusty Rose"),
                p -> h.addSizeColorVariants(p, "STYLE-YGL",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Plum", "Midnight", "Dusty Rose"},
                        "49.99", 17)));

        list.add(h.product(co, "Wool Blend Coat",
                "Italian 60% wool blend with satin lining. Double-breasted silhouette with hidden button closure.",
                "STYLE-WBC-001", "189.99", "249.99", "Outerwear", "LuxLayer",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Wool+Coat",
                80, 8, true, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Wool+Coat",
                        "https://placehold.co/800x800/273746/ffffff?text=Coat+Lining"),
                h.attrs("Material", "60% Wool, 30% Polyester, 10% Other",
                        "Lining", "100% Satin",
                        "Closure", "Double-breasted, hidden buttons",
                        "Care", "Dry clean only"),
                h.options2("Size", "XS", "S", "M", "Color", "Camel", "Black", "x"),
                p -> h.addSizeColorVariants(p, "STYLE-WBC",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Camel", "Black"},
                        "189.99", 10)));

        list.add(h.product(co, "Leather Belt Classic",
                "Full-grain vegetable-tanned leather with a solid brass buckle. Develops a rich patina over time.",
                "STYLE-BLT-001", "44.99", "59.99", "Accessories", "LeatherCraft",
                "https://placehold.co/800x800/784212/ffffff?text=Leather+Belt",
                120, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/784212/ffffff?text=Leather+Belt",
                        "https://placehold.co/800x800/6e2f1a/ffffff?text=Belt+Buckle"),
                h.attrs("Material", "Full-grain vegetable-tanned leather",
                        "Width", "35mm",
                        "Buckle", "Solid brass",
                        "Sizes", "Available 30\"–42\""),
                h.options1("Size", "30\"–32\"", "33\"–35\"", "36\"–38\"", "39\"–42\""),
                p -> {
                    h.pv(p, "30\"–32\"", "STYLE-BLT-S",  new BigDecimal("44.99"), 30);
                    h.pv(p, "33\"–35\"", "STYLE-BLT-M",  new BigDecimal("44.99"), 35);
                    h.pv(p, "36\"–38\"", "STYLE-BLT-L",  new BigDecimal("44.99"), 28);
                    h.pv(p, "39\"–42\"", "STYLE-BLT-XL", new BigDecimal("44.99"), 22);
                }));

        list.add(h.product(co, "Baseball Cap",
                "Unstructured 6-panel cap in washed cotton twill. Adjustable strapback closure.",
                "STYLE-CAP-001", "29.99", "39.99", "Accessories", "StyleHub",
                "https://placehold.co/800x800/935116/ffffff?text=Baseball+Cap",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/935116/ffffff?text=Baseball+Cap",
                        "https://placehold.co/800x800/7b4510/ffffff?text=Cap+Back"),
                h.attrs("Material", "100% Washed Cotton Twill",
                        "Panel", "6-panel unstructured",
                        "Closure", "Adjustable strapback",
                        "One Size", "Fits most"),
                h.options1("Color", "Khaki", "Black", "Navy", "Sage"),
                p -> {
                    h.pv(p, "Khaki", "STYLE-CAP-KHK", new BigDecimal("29.99"), 55);
                    h.pv(p, "Black", "STYLE-CAP-BLK", new BigDecimal("29.99"), 60);
                    h.pv(p, "Navy",  "STYLE-CAP-NVY", new BigDecimal("29.99"), 48);
                    h.pv(p, "Sage",  "STYLE-CAP-SGE", new BigDecimal("29.99"), 40);
                }));

        list.add(h.product(co, "Crew Neck Sweatshirt",
                "Garment-dyed 380gsm cotton fleece with ribbed cuffs and hem. Slightly oversized fit.",
                "STYLE-CRS-001", "59.99", "79.99", "Clothing", "CozyWear",
                "https://placehold.co/800x800/717d7e/ffffff?text=Crewneck",
                180, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/717d7e/ffffff?text=Crewneck",
                        "https://placehold.co/800x800/626a6a/ffffff?text=Crew+Detail"),
                h.attrs("Material", "380gsm 100% Cotton Fleece",
                        "Dye", "Garment-dyed for vintage look",
                        "Fit", "Slightly oversized",
                        "Care", "Machine wash cold, inside out"),
                h.options2("Size", "S", "M", "L", "Color", "Vintage Black", "Washed Olive", "Faded Blue"),
                p -> h.addSizeColorVariants(p, "STYLE-CRS",
                        new String[]{"S", "M", "L", "XL"},
                        new String[]{"Vintage Black", "Washed Olive", "Faded Blue"},
                        "59.99", 15)));

        list.add(h.product(co, "Floral Sundress",
                "Lightweight viscose with adjustable spaghetti straps and a tiered midi silhouette.",
                "STYLE-FSD-001", "69.99", "89.99", "Clothing", "EcoThread",
                "https://placehold.co/800x800/f1948a/ffffff?text=Sundress",
                160, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f1948a/ffffff?text=Sundress",
                        "https://placehold.co/800x800/ec7063/ffffff?text=Dress+Detail"),
                h.attrs("Material", "100% ECOVERO Viscose",
                        "Length", "Midi (knee to calf)",
                        "Straps", "Adjustable spaghetti straps",
                        "Care", "Hand wash or delicate cycle"),
                h.options2("Size", "XS", "S", "M", "Color", "Garden Floral", "Tropical", "Vintage Bloom"),
                p -> h.addSizeColorVariants(p, "STYLE-FSD",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Garden Floral", "Tropical", "Vintage Bloom"},
                        "69.99", 14)));

        list.add(h.product(co, "Chino Trousers",
                "Stretch cotton chino with slim straight cut and YKK zip fly. Versatile from office to weekend.",
                "STYLE-CHN-001", "64.99", "84.99", "Clothing", "DenimCraft",
                "https://placehold.co/800x800/d5dbdb/333333?text=Chino+Trousers",
                170, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/d5dbdb/333333?text=Chino+Trousers",
                        "https://placehold.co/800x800/ccd1d1/333333?text=Chino+Back"),
                h.attrs("Material", "97% Cotton, 3% Elastane",
                        "Fit", "Slim straight",
                        "Rise", "Mid-rise",
                        "Care", "Machine wash cold"),
                h.options2("Waist", "30\"", "32\"", "34\"", "Color", "Khaki", "Olive", "Navy"),
                p -> h.addSizeColorVariants(p, "STYLE-CHN",
                        new String[]{"30\"", "32\"", "34\""},
                        new String[]{"Khaki", "Olive", "Navy"},
                        "64.99", 19)));

        list.add(h.product(co, "Puffer Jacket",
                "700-fill RDS-certified down with a DWR shell. Packable into its own chest pocket.",
                "STYLE-PJK-001", "119.99", "149.99", "Outerwear", "LuxLayer",
                "https://placehold.co/800x800/1a5276/ffffff?text=Puffer+Jacket",
                130, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a5276/ffffff?text=Puffer+Jacket",
                        "https://placehold.co/800x800/154360/ffffff?text=Jacket+Pack"),
                h.attrs("Fill", "700-fill RDS down",
                        "Shell", "Ripstop nylon with DWR finish",
                        "Packable", "Yes, into own chest pocket",
                        "Care", "Machine wash cold, tumble dry low"),
                h.options2("Size", "XS", "S", "M", "Color", "Navy", "Black", "Hunter Green"),
                p -> h.addSizeColorVariants(p, "STYLE-PJK",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Navy", "Black", "Hunter Green"},
                        "119.99", 11)));

        list.add(h.product(co, "Athletic Socks 6-Pack",
                "Arch-support crew socks with cushioned sole and anti-blister heel tab. Wicks moisture.",
                "STYLE-SCK-001", "19.99", "24.99", "Accessories", "SwiftGear",
                "https://placehold.co/800x800/ecf0f1/333333?text=Athletic+Socks",
                300, 30, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Athletic+Socks",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Sock+Detail"),
                h.attrs("Material", "75% Cotton, 20% Nylon, 5% Spandex",
                        "Pack", "6 pairs",
                        "Features", "Arch support, anti-blister tab, moisture-wicking",
                        "Care", "Machine wash warm"),
                h.options1("Size", "S/M (US 4–8)", "L/XL (US 9–13)", "XL (US 13+)"),
                p -> {
                    h.pv(p, "S/M (US 4–8)",   "STYLE-SCK-SM", new BigDecimal("19.99"), 120);
                    h.pv(p, "L/XL (US 9–13)", "STYLE-SCK-LX", new BigDecimal("19.99"), 100);
                    h.pv(p, "XL (US 13+)",    "STYLE-SCK-XL", new BigDecimal("19.99"),  60);
                }));

        list.add(h.product(co, "Tote Canvas Bag",
                "Heavy 12oz canvas tote with reinforced handles and an interior zipper pocket. Holds 20L.",
                "STYLE-TOT-001", "39.99", "49.99", "Accessories", "EcoThread",
                "https://placehold.co/800x800/d7ccc8/333333?text=Canvas+Tote",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/d7ccc8/333333?text=Canvas+Tote",
                        "https://placehold.co/800x800/bcaaa4/333333?text=Tote+Inside"),
                h.attrs("Material", "12oz Canvas",
                        "Capacity", "20L",
                        "Handles", "Reinforced cotton webbing",
                        "Pockets", "1 interior zip + 2 exterior slip"),
                h.options1("Color", "Natural", "Black", "Olive", "Terracotta"),
                p -> {
                    h.pv(p, "Natural",    "STYLE-TOT-NAT", new BigDecimal("39.99"), 55);
                    h.pv(p, "Black",      "STYLE-TOT-BLK", new BigDecimal("39.99"), 60);
                    h.pv(p, "Olive",      "STYLE-TOT-OLV", new BigDecimal("39.99"), 45);
                    h.pv(p, "Terracotta", "STYLE-TOT-TER", new BigDecimal("39.99"), 40);
                }));

        list.add(h.product(co, "Knit Beanie",
                "100% Merino wool ribbed beanie with a turn-up cuff. Naturally temperature-regulating.",
                "STYLE-BNE-001", "24.99", "34.99", "Accessories", "CozyWear",
                "https://placehold.co/800x800/884ea0/ffffff?text=Knit+Beanie",
                250, 25, false, false, false, null, null,
                h.images("https://placehold.co/800x800/884ea0/ffffff?text=Knit+Beanie",
                        "https://placehold.co/800x800/76448a/ffffff?text=Beanie+Detail"),
                h.attrs("Material", "100% Merino Wool",
                        "Rib", "2×2 ribbed knit",
                        "Cuff", "Turn-up cuff",
                        "One Size", "Stretchy fit"),
                h.options1("Color", "Charcoal", "Cream", "Rust", "Forest", "Navy"),
                p -> {
                    h.pv(p, "Charcoal", "STYLE-BNE-CHR", new BigDecimal("24.99"), 55);
                    h.pv(p, "Cream",    "STYLE-BNE-CRM", new BigDecimal("24.99"), 50);
                    h.pv(p, "Rust",     "STYLE-BNE-RST", new BigDecimal("24.99"), 45);
                    h.pv(p, "Forest",   "STYLE-BNE-FOR", new BigDecimal("24.99"), 48);
                    h.pv(p, "Navy",     "STYLE-BNE-NVY", new BigDecimal("24.99"), 50);
                }));

        list.add(h.product(co, "Compression Shorts",
                "8\" inseam compression shorts with 4-way stretch and flat-lock seams to prevent chafing.",
                "STYLE-CMP-001", "34.99", "44.99", "Active", "SwiftGear",
                "https://placehold.co/800x800/212f3c/ffffff?text=Compression+Short",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/212f3c/ffffff?text=Compression+Short",
                        "https://placehold.co/800x800/1c2833/ffffff?text=Comp+Detail"),
                h.attrs("Material", "82% Nylon, 18% Spandex",
                        "Inseam", "8\"",
                        "Features", "Flat-lock seams, 4-way stretch",
                        "Care", "Machine wash cold"),
                h.options2("Size", "XS", "S", "M", "Color", "Black", "Navy", "x"),
                p -> h.addSizeColorVariants(p, "STYLE-CMP",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Black", "Navy"},
                        "34.99", 25)));

        list.add(h.product(co, "Flannel Pajama Set",
                "Brushed cotton flannel shirt and pants set. Button-front top with chest pocket.",
                "STYLE-PJM-001", "54.99", "69.99", "Loungewear", "CozyWear",
                "https://placehold.co/800x800/c0392b/ffffff?text=Flannel+PJ+Set",
                180, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/c0392b/ffffff?text=Flannel+PJ+Set",
                        "https://placehold.co/800x800/a93226/ffffff?text=PJ+Detail"),
                h.attrs("Material", "100% Brushed Cotton Flannel",
                        "Set", "Long-sleeve top + full-length pants",
                        "Top", "Button-front with chest pocket",
                        "Care", "Machine wash warm"),
                h.options2("Size", "S", "M", "L", "Color", "Buffalo Plaid", "Houndstooth", "Solid Navy"),
                p -> h.addSizeColorVariants(p, "STYLE-PJM",
                        new String[]{"S", "M", "L", "XL"},
                        new String[]{"Buffalo Plaid", "Houndstooth", "Solid Navy"},
                        "54.99", 15)));

        list.add(h.product(co, "Swimwear Board Shorts",
                "Quick-dry recycled polyester boardshorts with UPF 50+ and side cargo pocket.",
                "STYLE-BSH-001", "44.99", "54.99", "Swimwear", "SwiftGear",
                "https://placehold.co/800x800/1e8bc3/ffffff?text=Board+Shorts",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e8bc3/ffffff?text=Board+Shorts",
                        "https://placehold.co/800x800/1a78aa/ffffff?text=Shorts+Detail"),
                h.attrs("Material", "100% Recycled Polyester",
                        "Length", "18\" outseam",
                        "Protection", "UPF 50+",
                        "Pockets", "2 side + 1 cargo"),
                h.options2("Size", "28\"", "30\"", "32\"", "Color", "Island Blue", "Sunset Orange", "x"),
                p -> h.addSizeColorVariants(p, "STYLE-BSH",
                        new String[]{"28\"", "30\"", "32\"", "34\""},
                        new String[]{"Island Blue", "Sunset Orange"},
                        "44.99", 25)));

        // --- 30 additional products ---

        list.add(h.product(co, "Cashmere Blend Scarf",
                "70% cashmere, 30% merino wool. Oversized 200×70cm wrap scarf with fringed ends.",
                "STYLE-CSF-001", "59.99", "79.99", "Accessories", "LuxLayer",
                "https://placehold.co/800x800/f5cba7/333333?text=Cashmere+Scarf",
                160, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f5cba7/333333?text=Cashmere+Scarf",
                        "https://placehold.co/800x800/f0b27a/333333?text=Scarf+Detail"),
                h.attrs("Material", "70% Cashmere, 30% Merino Wool",
                        "Size", "200 × 70 cm",
                        "Finish", "Fringed ends",
                        "Care", "Hand wash cold or dry clean"),
                h.options1("Color", "Camel", "Dove Grey", "Deep Navy", "Burgundy"),
                p -> {
                    h.pv(p, "Camel",      "STYLE-CSF-CAM", new BigDecimal("59.99"), 42);
                    h.pv(p, "Dove Grey",  "STYLE-CSF-GRY", new BigDecimal("59.99"), 38);
                    h.pv(p, "Deep Navy",  "STYLE-CSF-NVY", new BigDecimal("59.99"), 35);
                    h.pv(p, "Burgundy",   "STYLE-CSF-BRG", new BigDecimal("59.99"), 30);
                }));

        list.add(h.product(co, "Leather Derby Shoes",
                "Full-grain leather derby shoes with Blake-stitched construction and leather sole.",
                "STYLE-DRB-001", "149.99", "189.99", "Footwear", "StepCraft",
                "https://placehold.co/800x800/5d4037/ffffff?text=Derby+Shoes",
                80, 6, false, false, false, null, null,
                h.images("https://placehold.co/800x800/5d4037/ffffff?text=Derby+Shoes",
                        "https://placehold.co/800x800/4e342e/ffffff?text=Shoes+Sole"),
                h.attrs("Upper", "Full-grain calfskin leather",
                        "Construction", "Blake-stitched",
                        "Sole", "Leather with rubber heel tap",
                        "Last", "Classic round toe"),
                h.options1("Size", "7", "8", "9", "10"),
                p -> {
                    h.pv(p, "7",  "STYLE-DRB-7",  new BigDecimal("149.99"), 18);
                    h.pv(p, "8",  "STYLE-DRB-8",  new BigDecimal("149.99"), 22);
                    h.pv(p, "9",  "STYLE-DRB-9",  new BigDecimal("149.99"), 20);
                    h.pv(p, "10", "STYLE-DRB-10", new BigDecimal("149.99"), 16);
                }));

        list.add(h.product(co, "Chelsea Boots",
                "Suede Chelsea boots with elasticated side panels and stacked wooden heel. Year-round style.",
                "STYLE-CHB-001", "119.99", "149.99", "Footwear", "StepCraft",
                "https://placehold.co/800x800/795548/ffffff?text=Chelsea+Boots",
                90, 8, true, false, false, null, null,
                h.images("https://placehold.co/800x800/795548/ffffff?text=Chelsea+Boots",
                        "https://placehold.co/800x800/6d4c41/ffffff?text=Boot+Side"),
                h.attrs("Upper", "Genuine suede",
                        "Closure", "Elasticated side panels",
                        "Heel", "Stacked 3cm wooden heel",
                        "Sole", "Commando rubber"),
                h.options2("Size", "6", "7", "8", "Color", "Tan", "Black", "Chocolate"),
                p -> h.addSizeColorVariants(p, "STYLE-CHB",
                        new String[]{"6", "7", "8", "9"},
                        new String[]{"Tan", "Black", "Chocolate"},
                        "119.99", 8)));

        list.add(h.product(co, "Midi Wrap Skirt",
                "Fluid wrap skirt in recycled satin with an adjustable tie waist. Midi length, fully lined.",
                "STYLE-MWS-001", "54.99", "69.99", "Clothing", "EcoThread",
                "https://placehold.co/800x800/c39bd3/333333?text=Wrap+Skirt",
                140, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/c39bd3/333333?text=Wrap+Skirt",
                        "https://placehold.co/800x800/a569bd/333333?text=Skirt+Detail"),
                h.attrs("Material", "100% Recycled Satin",
                        "Lining", "Full lining",
                        "Waist", "Adjustable wrap tie",
                        "Length", "Midi (below knee)"),
                h.options2("Size", "XS", "S", "M", "Color", "Sage Green", "Dusty Rose", "Black"),
                p -> h.addSizeColorVariants(p, "STYLE-MWS",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Sage Green", "Dusty Rose", "Black"},
                        "54.99", 12)));

        list.add(h.product(co, "Silk Charmeuse Blouse",
                "100% mulberry silk charmeuse blouse with V-neck and relaxed drape. Dry clean or gentle hand wash.",
                "STYLE-SLB-001", "89.99", "119.99", "Clothing", "LuxLayer",
                "https://placehold.co/800x800/fdebd0/333333?text=Silk+Blouse",
                100, 8, true, false, false, null, null,
                h.images("https://placehold.co/800x800/fdebd0/333333?text=Silk+Blouse",
                        "https://placehold.co/800x800/fad7a0/333333?text=Blouse+Detail"),
                h.attrs("Material", "100% Grade 6A Mulberry Silk",
                        "Weight", "16 momme",
                        "Collar", "V-neck",
                        "Care", "Hand wash cold or dry clean"),
                h.options2("Size", "XS", "S", "M", "Color", "Ivory", "Blush", "Slate Blue"),
                p -> h.addSizeColorVariants(p, "STYLE-SLB",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Ivory", "Blush", "Slate Blue"},
                        "89.99", 8)));

        list.add(h.product(co, "Relaxed Cargo Pants",
                "Relaxed-fit cargo with 6 pockets, adjustable hem tabs, and durable ripstop canvas.",
                "STYLE-CGP-001", "69.99", "89.99", "Clothing", "SwiftGear",
                "https://placehold.co/800x800/707b7c/ffffff?text=Cargo+Pants",
                170, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/707b7c/ffffff?text=Cargo+Pants",
                        "https://placehold.co/800x800/616a6b/ffffff?text=Cargo+Pocket"),
                h.attrs("Material", "100% Ripstop Cotton Canvas",
                        "Fit", "Relaxed",
                        "Pockets", "6 pockets (2 cargo)",
                        "Care", "Machine wash cold"),
                h.options2("Size", "28\"", "30\"", "32\"", "Color", "Olive", "Khaki", "Black"),
                p -> h.addSizeColorVariants(p, "STYLE-CGP",
                        new String[]{"28\"", "30\"", "32\"", "34\""},
                        new String[]{"Olive", "Khaki", "Black"},
                        "69.99", 14)));

        list.add(h.product(co, "Satin Bomber Jacket",
                "Silky satin bomber with ribbed trim, welt pockets, and contrast lining. Unisex fit.",
                "STYLE-BMB-001", "99.99", "129.99", "Outerwear", "LuxLayer",
                "https://placehold.co/800x800/1a252f/ffffff?text=Bomber+Jacket",
                110, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a252f/ffffff?text=Bomber+Jacket",
                        "https://placehold.co/800x800/17202a/ffffff?text=Bomber+Lining"),
                h.attrs("Material", "100% Polyester Satin",
                        "Lining", "Contrast printed lining",
                        "Closure", "YKK zip-front",
                        "Fit", "Unisex relaxed"),
                h.options2("Size", "XS", "S", "M", "Color", "Black", "Champagne", "Forest"),
                p -> h.addSizeColorVariants(p, "STYLE-BMB",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Black", "Champagne", "Forest"},
                        "99.99", 9)));

        list.add(h.product(co, "Ribbed Lounge Set",
                "Matching ribbed knit crop top and wide-leg pants set. Ultra-stretchy and breathable.",
                "STYLE-RLS-001", "64.99", "84.99", "Loungewear", "CozyWear",
                "https://placehold.co/800x800/f9ebea/333333?text=Lounge+Set",
                150, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f9ebea/333333?text=Lounge+Set",
                        "https://placehold.co/800x800/f2d7d5/333333?text=Lounge+Detail"),
                h.attrs("Material", "92% Viscose, 8% Spandex",
                        "Set", "Crop top + wide-leg pants",
                        "Fit", "Oversized top, relaxed pants",
                        "Care", "Machine wash cold"),
                h.options2("Size", "XS", "S", "M", "Color", "Sand", "Sage", "Terracotta"),
                p -> h.addSizeColorVariants(p, "STYLE-RLS",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Sand", "Sage", "Terracotta"},
                        "64.99", 13)));

        list.add(h.product(co, "Seamless Sports Bra",
                "Medium support seamless sports bra with removable padding and racerback design.",
                "STYLE-SPB-001", "29.99", "39.99", "Active", "SwiftGear",
                "https://placehold.co/800x800/e8d5f5/333333?text=Sports+Bra",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/e8d5f5/333333?text=Sports+Bra",
                        "https://placehold.co/800x800/d7bde2/333333?text=Bra+Back"),
                h.attrs("Material", "82% Nylon, 18% Spandex seamless",
                        "Support", "Medium impact",
                        "Padding", "Removable cups",
                        "Back", "Racerback"),
                h.options2("Size", "XS", "S", "M", "Color", "Lavender", "Sage", "Midnight"),
                p -> h.addSizeColorVariants(p, "STYLE-SPB",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Lavender", "Sage", "Midnight"},
                        "29.99", 17)));

        list.add(h.product(co, "Oxford Button-Down Shirt",
                "Classic Oxford cloth button-down in GOTS organic cotton. Relaxed chest pocket and box pleat.",
                "STYLE-OXF-001", "59.99", "79.99", "Clothing", "EcoThread",
                "https://placehold.co/800x800/d6eaf8/333333?text=Oxford+Shirt",
                160, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/d6eaf8/333333?text=Oxford+Shirt",
                        "https://placehold.co/800x800/c2d9f0/333333?text=Oxford+Detail"),
                h.attrs("Material", "100% GOTS Organic Oxford Cotton",
                        "Fit", "Classic",
                        "Collar", "Button-down",
                        "Care", "Machine wash warm"),
                h.options2("Size", "S", "M", "L", "Color", "White", "Blue Oxford", "Pink"),
                p -> h.addSizeColorVariants(p, "STYLE-OXF",
                        new String[]{"S", "M", "L", "XL"},
                        new String[]{"White", "Blue Oxford", "Pink"},
                        "59.99", 14)));

        list.add(h.product(co, "Longline Open-Front Cardigan",
                "Longline open-front cardigan in brushed modal-cotton blend. Relaxed drapey fit.",
                "STYLE-LGC-001", "69.99", "89.99", "Clothing", "CozyWear",
                "https://placehold.co/800x800/f5f5f5/333333?text=Longline+Cardi",
                130, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/f5f5f5/333333?text=Longline+Cardi",
                        "https://placehold.co/800x800/eeeeee/333333?text=Cardi+Detail"),
                h.attrs("Material", "70% Modal, 30% Cotton",
                        "Length", "Longline (mid-thigh)",
                        "Closure", "Open-front, no buttons",
                        "Care", "Machine wash cold"),
                h.options2("Size", "XS", "S", "M", "Color", "Oat", "Charcoal", "Dusty Pink"),
                p -> h.addSizeColorVariants(p, "STYLE-LGC",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Oat", "Charcoal", "Dusty Pink"},
                        "69.99", 11)));

        list.add(h.product(co, "Washed Denim Jacket",
                "Relaxed-fit washed denim jacket with chest and front pockets. Pre-washed for softness.",
                "STYLE-DNJ-001", "79.99", "99.99", "Outerwear", "DenimCraft",
                "https://placehold.co/800x800/2471a3/ffffff?text=Denim+Jacket",
                150, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2471a3/ffffff?text=Denim+Jacket",
                        "https://placehold.co/800x800/1f618d/ffffff?text=Jacket+Back"),
                h.attrs("Material", "100% Cotton Denim",
                        "Wash", "Pre-washed enzyme wash",
                        "Fit", "Relaxed",
                        "Care", "Machine wash cold, inside out"),
                h.options2("Size", "XS", "S", "M", "Color", "Light Wash", "Mid Wash", "Dark Wash"),
                p -> h.addSizeColorVariants(p, "STYLE-DNJ",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Light Wash", "Mid Wash", "Dark Wash"},
                        "79.99", 13)));

        list.add(h.product(co, "Slim Leather Bifold Wallet",
                "Slim 7-card bifold wallet in pebbled full-grain leather with RFID blocking.",
                "STYLE-WLT-001", "49.99", "64.99", "Accessories", "LeatherCraft",
                "https://placehold.co/800x800/5d4037/ffffff?text=Leather+Wallet",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/5d4037/ffffff?text=Leather+Wallet",
                        "https://placehold.co/800x800/4e342e/ffffff?text=Wallet+Open"),
                h.attrs("Material", "Pebbled full-grain leather",
                        "Capacity", "7 cards + cash",
                        "Security", "RFID blocking lining",
                        "Dimensions", "11 × 9 cm folded"),
                h.options1("Color", "Cognac", "Black", "Navy"),
                p -> {
                    h.pv(p, "Cognac", "STYLE-WLT-COG", new BigDecimal("49.99"), 70);
                    h.pv(p, "Black",  "STYLE-WLT-BLK", new BigDecimal("49.99"), 80);
                    h.pv(p, "Navy",   "STYLE-WLT-NVY", new BigDecimal("49.99"), 55);
                }));

        list.add(h.product(co, "Leather Crossbody Bag",
                "Structured top-handle crossbody in genuine saffiano leather with adjustable strap.",
                "STYLE-CRB-001", "89.99", "119.99", "Accessories", "LeatherCraft",
                "https://placehold.co/800x800/8d6e63/333333?text=Crossbody+Bag",
                90, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/8d6e63/333333?text=Crossbody+Bag",
                        "https://placehold.co/800x800/795548/333333?text=Bag+Interior"),
                h.attrs("Material", "Genuine saffiano leather",
                        "Strap", "Adjustable crossbody strap (80–120cm)",
                        "Closure", "Magnetic snap + zip top",
                        "Interior", "1 main zip + 2 slip + 1 zip pocket"),
                h.options1("Color", "Caramel", "Black", "Forest Green"),
                p -> {
                    h.pv(p, "Caramel",      "STYLE-CRB-CAR", new BigDecimal("89.99"), 30);
                    h.pv(p, "Black",        "STYLE-CRB-BLK", new BigDecimal("89.99"), 35);
                    h.pv(p, "Forest Green", "STYLE-CRB-GRN", new BigDecimal("89.99"), 22);
                }));

        list.add(h.product(co, "Washed Bucket Hat",
                "Unstructured bucket hat in washed cotton twill. UV 30+ protection. Adjustable drawstring.",
                "STYLE-BKH-001", "24.99", "34.99", "Accessories", "StyleHub",
                "https://placehold.co/800x800/e9c46a/333333?text=Bucket+Hat",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/e9c46a/333333?text=Bucket+Hat",
                        "https://placehold.co/800x800/e76f51/333333?text=Hat+Detail"),
                h.attrs("Material", "100% Washed Cotton Twill",
                        "Protection", "UPF 30+",
                        "Brim", "3.5cm all-around",
                        "Fit", "One size with drawcord"),
                h.options1("Color", "Sand", "Black", "Sage", "Terracotta"),
                p -> {
                    h.pv(p, "Sand",       "STYLE-BKH-SND", new BigDecimal("24.99"), 55);
                    h.pv(p, "Black",      "STYLE-BKH-BLK", new BigDecimal("24.99"), 60);
                    h.pv(p, "Sage",       "STYLE-BKH-SGE", new BigDecimal("24.99"), 48);
                    h.pv(p, "Terracotta", "STYLE-BKH-TER", new BigDecimal("24.99"), 40);
                }));

        list.add(h.product(co, "One-Piece Swimsuit",
                "Square-neck swimsuit with adjustable straps and built-in support. 80 UPF+ protection.",
                "STYLE-OPS-001", "59.99", "74.99", "Swimwear", "SwiftGear",
                "https://placehold.co/800x800/2e86c1/ffffff?text=One+Piece",
                130, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2e86c1/ffffff?text=One+Piece",
                        "https://placehold.co/800x800/2874a6/ffffff?text=Swimsuit+Back"),
                h.attrs("Material", "80% Nylon, 20% Spandex",
                        "UPF", "UPF 50+",
                        "Support", "Underwire-free built-in shelf bra",
                        "Straps", "Adjustable"),
                h.options2("Size", "XS", "S", "M", "Color", "Ocean Blue", "Black", "Coral"),
                p -> h.addSizeColorVariants(p, "STYLE-OPS",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Ocean Blue", "Black", "Coral"},
                        "59.99", 11)));

        list.add(h.product(co, "Pleated Midi Skirt",
                "Pleated georgette midi skirt with elasticated waist and subtle metallic sheen.",
                "STYLE-PMS-001", "59.99", "79.99", "Clothing", "EcoThread",
                "https://placehold.co/800x800/d4ac0d/333333?text=Pleated+Skirt",
                130, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/d4ac0d/333333?text=Pleated+Skirt",
                        "https://placehold.co/800x800/b7950b/333333?text=Skirt+Flow"),
                h.attrs("Material", "100% Polyester Georgette",
                        "Waist", "Elasticated",
                        "Length", "Midi (below knee)",
                        "Sheen", "Subtle metallic weave"),
                h.options2("Size", "XS", "S", "M", "Color", "Gold", "Dusty Rose", "Midnight"),
                p -> h.addSizeColorVariants(p, "STYLE-PMS",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Gold", "Dusty Rose", "Midnight"},
                        "59.99", 11)));

        list.add(h.product(co, "Vintage Wash Graphic Tee",
                "100% ringspun cotton with a garment-washed vintage feel. Oversized unisex fit.",
                "STYLE-GFX-001", "29.99", "39.99", "Clothing", "StyleHub",
                "https://placehold.co/800x800/d7ccc8/333333?text=Graphic+Tee",
                250, 25, false, false, false, null, null,
                h.images("https://placehold.co/800x800/d7ccc8/333333?text=Graphic+Tee",
                        "https://placehold.co/800x800/bcaaa4/333333?text=Tee+Graphic"),
                h.attrs("Material", "100% Ringspun Cotton",
                        "Weight", "180gsm garment-washed",
                        "Fit", "Oversized unisex",
                        "Print", "Water-based eco inks"),
                h.options2("Size", "XS", "S", "M", "Color", "Vintage White", "Faded Black", "Washed Blue"),
                p -> h.addSizeColorVariants(p, "STYLE-GFX",
                        new String[]{"XS", "S", "M", "L", "XL"},
                        new String[]{"Vintage White", "Faded Black", "Washed Blue"},
                        "29.99", 17)));

        list.add(h.product(co, "Wide-Leg Tailored Trousers",
                "Wide-leg trousers in a recycled poly-viscose blend. Pressed crease and high waist.",
                "STYLE-WLT-001", "74.99", "94.99", "Clothing", "LuxLayer",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Wide+Leg+Trousers",
                130, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Wide+Leg+Trousers",
                        "https://placehold.co/800x800/273746/ffffff?text=Trousers+Detail"),
                h.attrs("Material", "60% Recycled Polyester, 40% Viscose",
                        "Waist", "High waist with waistband",
                        "Leg", "Wide leg",
                        "Care", "Dry clean recommended"),
                h.options2("Size", "XS", "S", "M", "Color", "Ivory", "Black", "Caramel"),
                p -> h.addSizeColorVariants(p, "STYLE-WLT",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Ivory", "Black", "Caramel"},
                        "74.99", 11)));

        list.add(h.product(co, "Quilted Puffer Vest",
                "Lightweight channel-quilted vest with 60g recycled fill. Packs into a small pouch.",
                "STYLE-QVT-001", "69.99", "89.99", "Outerwear", "LuxLayer",
                "https://placehold.co/800x800/1a5276/ffffff?text=Quilted+Vest",
                140, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a5276/ffffff?text=Quilted+Vest",
                        "https://placehold.co/800x800/154360/ffffff?text=Vest+Pack"),
                h.attrs("Fill", "60g recycled polyester",
                        "Shell", "20D ripstop nylon",
                        "Packable", "Into own pocket",
                        "Pockets", "2 zip hand pockets"),
                h.options2("Size", "XS", "S", "M", "Color", "Navy", "Rust", "Sage"),
                p -> h.addSizeColorVariants(p, "STYLE-QVT",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Navy", "Rust", "Sage"},
                        "69.99", 12)));

        list.add(h.product(co, "Performance Workout Tank",
                "Moisture-wicking racerback tank with anti-odour treatment. 4-way stretch for full range of motion.",
                "STYLE-WKT-001", "24.99", "32.99", "Active", "SwiftGear",
                "https://placehold.co/800x800/1abc9c/ffffff?text=Workout+Tank",
                250, 25, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1abc9c/ffffff?text=Workout+Tank",
                        "https://placehold.co/800x800/17a589/ffffff?text=Tank+Back"),
                h.attrs("Material", "94% Polyester, 6% Spandex",
                        "Features", "4-way stretch, moisture-wick, anti-odour",
                        "Back", "Racerback",
                        "Care", "Machine wash cold"),
                h.options2("Size", "XS", "S", "M", "Color", "Black", "White", "Electric Blue"),
                p -> h.addSizeColorVariants(p, "STYLE-WKT",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Black", "White", "Electric Blue"},
                        "24.99", 21)));

        list.add(h.product(co, "Suede Ankle Boots Block Heel",
                "Genuine suede ankle boots with a 5cm block heel and side zip closure.",
                "STYLE-ANB-001", "109.99", "139.99", "Footwear", "StepCraft",
                "https://placehold.co/800x800/a1887f/ffffff?text=Ankle+Boots",
                80, 6, false, false, false, null, null,
                h.images("https://placehold.co/800x800/a1887f/ffffff?text=Ankle+Boots",
                        "https://placehold.co/800x800/8d6e63/ffffff?text=Boot+Heel"),
                h.attrs("Upper", "Genuine suede",
                        "Heel", "5cm block heel",
                        "Closure", "Side zip",
                        "Sole", "Non-slip rubber"),
                h.options2("Size", "5", "6", "7", "Color", "Tan", "Black", "x"),
                p -> h.addSizeColorVariants(p, "STYLE-ANB",
                        new String[]{"5", "6", "7", "8"},
                        new String[]{"Tan", "Black"},
                        "109.99", 10)));

        list.add(h.product(co, "Leather Slip-On Loafers",
                "Classic penny loafer in smooth full-grain leather with leather-lined insole.",
                "STYLE-LOF-001", "119.99", "149.99", "Footwear", "StepCraft",
                "https://placehold.co/800x800/6d4c41/ffffff?text=Leather+Loafers",
                90, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/6d4c41/ffffff?text=Leather+Loafers",
                        "https://placehold.co/800x800/5d4037/ffffff?text=Loafer+Sole"),
                h.attrs("Upper", "Full-grain leather",
                        "Insole", "Leather-lined cushioned",
                        "Sole", "Goodyear-welted leather + rubber",
                        "Style", "Penny loafer"),
                h.options1("Size", "7", "8", "9", "10"),
                p -> {
                    h.pv(p, "7",  "STYLE-LOF-7",  new BigDecimal("119.99"), 20);
                    h.pv(p, "8",  "STYLE-LOF-8",  new BigDecimal("119.99"), 25);
                    h.pv(p, "9",  "STYLE-LOF-9",  new BigDecimal("119.99"), 22);
                    h.pv(p, "10", "STYLE-LOF-10", new BigDecimal("119.99"), 18);
                }));

        list.add(h.product(co, "Oversized Tailored Blazer",
                "Single-button oversized blazer in recycled wool blend. Unlined for a light, modern feel.",
                "STYLE-OBZ-001", "139.99", "179.99", "Outerwear", "LuxLayer",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Oversized+Blazer",
                90, 8, true, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Oversized+Blazer",
                        "https://placehold.co/800x800/273746/ffffff?text=Blazer+Back"),
                h.attrs("Material", "70% Recycled Wool, 30% Polyester",
                        "Fit", "Oversized, drop shoulder",
                        "Buttons", "Single-button front",
                        "Pockets", "2 patch + 1 chest"),
                h.options2("Size", "XS", "S", "M", "Color", "Houndstooth", "Camel", "Black"),
                p -> h.addSizeColorVariants(p, "STYLE-OBZ",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Houndstooth", "Camel", "Black"},
                        "139.99", 8)));

        list.add(h.product(co, "Ribbed Turtleneck Sweater",
                "Fine-rib merino blend turtleneck with a slim fit. Naturally temperature-regulating.",
                "STYLE-RTN-001", "79.99", "99.99", "Clothing", "CozyWear",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Turtleneck",
                140, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Turtleneck",
                        "https://placehold.co/800x800/273746/ffffff?text=Turtleneck+Rib"),
                h.attrs("Material", "80% Merino Wool, 20% Nylon",
                        "Rib", "Fine 2×2 rib",
                        "Fit", "Slim",
                        "Care", "Hand wash cold, dry flat"),
                h.options2("Size", "XS", "S", "M", "Color", "Oat", "Black", "Forest"),
                p -> h.addSizeColorVariants(p, "STYLE-RTN",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Oat", "Black", "Forest"},
                        "79.99", 12)));

        list.add(h.product(co, "High-Waist Denim Shorts",
                "High-waist denim cutoffs with a raw hem and front button fly. Vintage-inspired wash.",
                "STYLE-DSH-001", "49.99", "64.99", "Clothing", "DenimCraft",
                "https://placehold.co/800x800/2471a3/ffffff?text=Denim+Shorts",
                160, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2471a3/ffffff?text=Denim+Shorts",
                        "https://placehold.co/800x800/1f618d/ffffff?text=Shorts+Detail"),
                h.attrs("Material", "100% Cotton Denim",
                        "Waist", "High-waist with button fly",
                        "Hem", "Raw/frayed edge",
                        "Wash", "Vintage medium wash"),
                h.options2("Size", "24\"", "26\"", "28\"", "Color", "Medium Wash", "Light Wash", "x"),
                p -> h.addSizeColorVariants(p, "STYLE-DSH",
                        new String[]{"24\"", "26\"", "28\"", "30\""},
                        new String[]{"Medium Wash", "Light Wash"},
                        "49.99", 20)));

        list.add(h.product(co, "Packable Rain Jacket",
                "20D ripstop rain jacket with sealed seams, adjustable hood, and packable design. 10K waterproofing.",
                "STYLE-RJK-001", "89.99", "119.99", "Outerwear", "SwiftGear",
                "https://placehold.co/800x800/1abc9c/ffffff?text=Rain+Jacket",
                130, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1abc9c/ffffff?text=Rain+Jacket",
                        "https://placehold.co/800x800/17a589/ffffff?text=Jacket+Hood"),
                h.attrs("Material", "20D ripstop nylon",
                        "Waterproofing", "10,000mm hydrostatic head",
                        "Seams", "Fully taped sealed",
                        "Packable", "Into own chest pocket"),
                h.options2("Size", "XS", "S", "M", "Color", "Yellow", "Navy", "Black"),
                p -> h.addSizeColorVariants(p, "STYLE-RJK",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Yellow", "Navy", "Black"},
                        "89.99", 11)));

        list.add(h.product(co, "Performance Polo Shirt",
                "Moisture-wicking piqué polo with anti-wrinkle finish. UPF 30+ and machine washable.",
                "STYLE-PPL-001", "44.99", "59.99", "Active", "SwiftGear",
                "https://placehold.co/800x800/1f618d/ffffff?text=Performance+Polo",
                170, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1f618d/ffffff?text=Performance+Polo",
                        "https://placehold.co/800x800/1a5276/ffffff?text=Polo+Detail"),
                h.attrs("Material", "100% Performance Piqué Polyester",
                        "Features", "Moisture-wick, UPF 30+, anti-wrinkle",
                        "Collar", "Ribbed polo collar with 3-button placket",
                        "Care", "Machine wash cold"),
                h.options2("Size", "S", "M", "L", "Color", "Navy", "White", "Slate"),
                p -> h.addSizeColorVariants(p, "STYLE-PPL",
                        new String[]{"S", "M", "L", "XL"},
                        new String[]{"Navy", "White", "Slate"},
                        "44.99", 15)));

        list.add(h.product(co, "Silk Satin Pajama Set",
                "Luxurious satin pajama set with piped trim. Relaxed shirt and straight-leg pants.",
                "STYLE-SPJ-001", "74.99", "99.99", "Loungewear", "LuxLayer",
                "https://placehold.co/800x800/fdebd0/333333?text=Silk+Pajama",
                120, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/fdebd0/333333?text=Silk+Pajama",
                        "https://placehold.co/800x800/fad7a0/333333?text=PJ+Detail"),
                h.attrs("Material", "100% Polyester Satin (satin feel)",
                        "Set", "Long-sleeve shirt + straight-leg pants",
                        "Trim", "Contrast piped edge",
                        "Care", "Machine wash delicate, cold"),
                h.options2("Size", "XS", "S", "M", "Color", "Champagne", "Black", "Dusty Blue"),
                p -> h.addSizeColorVariants(p, "STYLE-SPJ",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Champagne", "Black", "Dusty Blue"},
                        "74.99", 10)));

        list.add(h.product(co, "Cycling Bib Shorts",
                "8-panel cycling bib shorts with 4D chamois pad and 80 denier compression knit.",
                "STYLE-CBS-001", "64.99", "84.99", "Active", "SwiftGear",
                "https://placehold.co/800x800/212f3c/ffffff?text=Cycling+Bibs",
                120, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/212f3c/ffffff?text=Cycling+Bibs",
                        "https://placehold.co/800x800/1c2833/ffffff?text=Bibs+Chamois"),
                h.attrs("Material", "80D Compression Lycra",
                        "Panels", "8-panel construction",
                        "Pad", "4D multi-density chamois",
                        "Straps", "Silicone-grip shoulder straps"),
                h.options2("Size", "XS", "S", "M", "Color", "Black", "Black/White", "x"),
                p -> h.addSizeColorVariants(p, "STYLE-CBS",
                        new String[]{"XS", "S", "M", "L"},
                        new String[]{"Black", "Black/White"},
                        "64.99", 15)));

        return list;
    }
}
