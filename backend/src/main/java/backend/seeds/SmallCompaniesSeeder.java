package backend.seeds;

import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.InventoryLocation;
import backend.models.core.Product;
import backend.models.core.ProductReview;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.ReviewStatus;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.UserRepository;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Seeds 10 additional small companies to populate Elasticsearch with
 * diverse product and review content across varied categories.
 * Each company gets 10 products and 10 reviews (1 per product).
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class SmallCompaniesSeeder {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final InventoryLocationRepository locationRepository;
    private final ProductSeedHelper h;
    private final ProductReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;

    public void seed(SeededUsers u) {
        if (userRepository.findByEmail("merchant.petcare@shopwave.dev").isPresent()) return;

        seedPetCare(u);
        seedGreenThumb(u);
        seedLittleLearners(u);
        seedGourmetGrains(u);
        seedCraftSupplies(u);
        seedBeautyBlend(u);
        seedAutoEdge(u);
        seedDeskMate(u);
        seedOutdoorPeak(u);
        seedKitchenCraft(u);
    }

    // ── 1. PetCare Plus ──────────────────────────────────────────────────────

    private void seedPetCare(SeededUsers u) {
        User owner = merchant("merchant.petcare@shopwave.dev", "Riley", "Thompson");
        Company co = company(owner, "PetCare Plus", "Pet Supplies & Accessories",
                "800 Industrial Blvd", "Portland", "US", "97201",
                "+15035550101", "hello@petcareplus.dev", "https://petcareplus.dev",
                "Premium nutrition, accessories, and care products for dogs and cats.", 2019, 12);
        invLoc(co, "Portland Warehouse", "PC-MAIN");

        var p0 = prod(co, "Premium Dry Dog Food 15lb",
                "High-protein chicken recipe with no artificial additives. Supports coat health and joint mobility in adult dogs.",
                "PC-001", "49.99", "Pet Food", "NutriPaws", 180, 25);
        var p1 = prod(co, "Clumping Cat Litter 40lb",
                "Ultra-low dust, multi-cat formula with activated charcoal odour control. Forms tight clumps for easy scooping.",
                "PC-002", "24.99", "Cat Care", "CleanPaw", 220, 30);
        var p2 = prod(co, "Dog Dental Chews 60-Pack",
                "Veterinary-grade enzymatic chews that reduce plaque and freshen breath. Natural mint and parsley formula.",
                "PC-003", "18.99", "Pet Health", "DentaFresh", 300, 40);
        var p3 = prod(co, "Retractable Dog Leash 16ft",
                "One-hand brake and lock mechanism with durable nylon tape. Comfortable ergonomic handle with LED safety light.",
                "PC-004", "21.99", "Pet Accessories", "FlexiWalk", 150, 20);
        var p4 = prod(co, "Sisal Cat Scratching Post 32in",
                "Solid base prevents tipping. Natural sisal rope wrapped around a sturdy pine post with dangling toy topper.",
                "PC-005", "39.99", "Cat Accessories", "PetNest", 90, 15);
        var p5 = prod(co, "Dog Shampoo & Conditioner Set",
                "Oatmeal and aloe vera formula for sensitive skin. Soap-free, pH-balanced, and safe for puppies over 8 weeks.",
                "PC-006", "16.99", "Pet Grooming", "CoatCare", 200, 25);
        var p6 = prod(co, "Electronic Interactive Cat Toy",
                "Automatic rotating feather wand with 3-speed settings and 30-minute auto-off. USB rechargeable.",
                "PC-007", "28.99", "Cat Toys", "PlayPaws", 120, 15);
        var p7 = prod(co, "Soft Dog Training Treats 12oz",
                "Bite-sized, low-calorie treats with real chicken as the first ingredient. Grain-free and vet-recommended.",
                "PC-008", "12.99", "Pet Food", "TreatRight", 400, 50);
        var p8 = prod(co, "Orthopedic Memory Foam Dog Bed",
                "3-inch memory foam relieves joint pressure for senior dogs. Removable, machine-washable cover in durable canvas.",
                "PC-009", "79.99", "Pet Comfort", "DreamPaws", 65, 10);
        var p9 = prod(co, "GPS Pet Tracker",
                "Real-time location tracking via app with 7-day battery. Waterproof IPX7 and compatible with any standard collar.",
                "PC-010", "69.99", "Pet Tech", "SafePet", 80, 12);

        r(p0, u.alice(), 5, "Our vet recommended it — coat looks amazing",
                "Switched three months ago and my golden retriever's coat is noticeably shinier. He also has more energy and the kibble size is perfect.");
        r(p1, u.bob(), 4, "Best clumping I've found — almost no dust",
                "The charcoal odour control genuinely works. I have two cats and the litter box doesn't smell between cleanings now. Solid clumps make scooping quick.");
        r(p2, u.carol(), 5, "My dog actually looks forward to his dental routine",
                "He used to fight tooth brushing. These chews have improved his breath noticeably and his vet confirmed less tartar at his last checkup.");
        r(p3, u.alice(), 4, "Reliable lock mechanism, very comfortable grip",
                "The one-handed lock is intuitive and responsive. The nylon tape feels more durable than cord options. LED light is a great safety feature for evening walks.");
        r(p4, u.bob(), 5, "My cat chose this over our expensive furniture",
                "The solid base means it doesn't wobble when she really digs in. The dangling toy kept her busy for hours the first day. Sisal quality is excellent.");
        r(p5, u.carol(), 5, "Sensitive skin sorted — no more scratching",
                "My spaniel had chronic dry skin. After two washes with this formula the scratching stopped. The oatmeal scent is light and pleasant, not overpowering.");
        r(p6, u.alice(), 4, "My cat is obsessed — but batteries drain faster than claimed",
                "The rotating speed settings are great — slow for older cats, fast for kittens. Auto-off is useful. Battery life is more like 4 hours continuous, not 6.");
        r(p7, u.bob(), 5, "High value treats with a clean ingredient list",
                "My trainer uses these and recommended them. They're the right size for rapid-fire reward training and my dog hasn't shown any intolerance despite daily use.");
        r(p8, u.carol(), 5, "Transformed my senior dog's sleep quality",
                "My 11-year-old lab has hip dysplasia and was restless at night. Within a week on this bed she sleeps solidly through. Cover washes and dries well too.");
        r(p9, u.alice(), 4, "Peace of mind for off-leash walks",
                "App setup was straightforward. Location updates are near real-time on LTE coverage. Battery lasts a full week with daily 1-hour walks. Waterproofing held during a river crossing.");
    }

    // ── 2. GreenThumb Gardens ─────────────────────────────────────────────────

    private void seedGreenThumb(SeededUsers u) {
        User owner = merchant("merchant.greenthumb@shopwave.dev", "Maya", "Okafor");
        Company co = company(owner, "GreenThumb Gardens", "Gardening & Plants",
                "301 Botanical Ave", "Columbus", "US", "43215",
                "+16145550202", "grow@greenthumb.dev", "https://greenthumb.dev",
                "Everything the home gardener needs — from soil to harvest.", 2020, 8);
        invLoc(co, "Columbus Distribution", "GT-MAIN");

        var p0 = prod(co, "Organic Potting Mix 2cu ft",
                "Peat-free blend of coconut coir, perlite, and worm castings. Excellent moisture retention with superior drainage.",
                "GT-001", "14.99", "Soil & Amendments", "EarthRight", 250, 30);
        var p1 = prod(co, "Raised Garden Bed Kit Cedar",
                "Untreated western red cedar resists rot naturally. Modular design — stack two kits for 12-inch depth. No tools required.",
                "GT-002", "89.99", "Garden Structures", "CedarGrow", 45, 8);
        var p2 = prod(co, "Heirloom Vegetable Seed Collection 30 Varieties",
                "Open-pollinated seeds from trusted heritage varieties. Includes tomatoes, peppers, squash, beans, and greens. 90%+ germination rate.",
                "GT-003", "34.99", "Seeds", "HeirloomHarvest", 180, 20);
        var p3 = prod(co, "Compost Tumbler Bin 37 Gallon",
                "Dual-chamber design produces finished compost in as little as 2 weeks. BPA-free recycled plastic on galvanised steel frame.",
                "GT-004", "129.99", "Composting", "CycleTurn", 30, 5);
        var p4 = prod(co, "Bypass Pruning Shears Pro",
                "High-carbon SK-5 steel blades with sap-resistant coating. Ergonomic non-slip grips with built-in wire cutter and sap groove.",
                "GT-005", "29.99", "Hand Tools", "ClipMaster", 120, 15);
        var p5 = prod(co, "Drip Irrigation Kit 50-Plant",
                "Pressure-compensating emitters deliver consistent flow regardless of slope. Includes timer, tubing, stakes, and connectors for up to 50 plants.",
                "GT-006", "44.99", "Irrigation", "AquaFlow", 75, 10);
        var p6 = prod(co, "Slow-Release Plant Fertiliser Spikes 50-Pack",
                "Balanced 10-10-10 NPK formula feeds for up to 8 weeks. Safe for edibles. Simply push into moist soil near the root zone.",
                "GT-007", "12.99", "Fertilisers", "FeedStick", 300, 40);
        var p7 = prod(co, "Garden Kneeler & Seat Convertible",
                "Thick EVA foam pad and steel frame converts between kneeling and seated position. Supports up to 330lbs. Side pockets for tools.",
                "GT-008", "39.99", "Garden Comfort", "KneeEase", 90, 12);
        var p8 = prod(co, "Organic Pest Control Spray Concentrate",
                "Neem oil and pyrethrin blend safe for vegetables, fruits, and ornamentals. Kills on contact and provides 14-day residual protection.",
                "GT-009", "19.99", "Pest Control", "BugShield", 160, 20);
        var p9 = prod(co, "Biodegradable Seed Starting Trays 10-Pack",
                "Made from pressed coconut coir — transplant tray and all directly into soil. 72-cell per tray. No transplant shock.",
                "GT-010", "9.99", "Seed Starting", "EcoStart", 350, 50);

        r(p0, u.alice(), 5, "Plants love it — noticeably better than shop-brand",
                "Repotted my entire indoor collection and everything is thriving. The perlite proportion is perfect — good drainage without drying out too fast.");
        r(p1, u.bob(), 5, "Cedar quality is excellent — built in under an hour",
                "No tools is accurate — just slot together. The cedar is fragrant and feels genuinely high quality. Three seasons in and no warping or rot.");
        r(p2, u.carol(), 4, "Germination rate is very high — two varieties didn't sprout",
                "28 of 30 varieties germinated reliably. Instructions are helpful and the heritage tomatoes in particular have been prolific and delicious.");
        r(p3, u.alice(), 5, "Finished compost in 12 days — I was skeptical",
                "Turning the drum twice a day and adding a compost accelerator I got finished compost faster than any bin I've used. Dual chambers mean continuous production.");
        r(p4, u.bob(), 5, "Cleanest cut I've ever had from pruners",
                "The SK-5 steel stayed sharp through a full season of heavy use. The sap-resistant coating actually works — a quick wipe and they're clean. Ergonomics are excellent.");
        r(p5, u.carol(), 4, "Saved me hours of watering — minor pressure variance at ends",
                "Setup took about an hour for 40 plants. Emitters near the end of the run deliver slightly less water but still adequate. Timer feature is genuinely useful.");
        r(p6, u.alice(), 4, "Convenient and effective — good for containers",
                "My potted tomatoes responded well. Easy to use and the 8-week claim holds up. Probably needs supplementing with liquid feed for heavy feeders.");
        r(p7, u.bob(), 5, "My back thanks me every weekend",
                "At 6'3\" kneeling was always uncomfortable. The convertible seat mode is perfect for longer jobs. The steel frame feels solid under my weight.");
        r(p8, u.carol(), 5, "Organic and actually works on aphids",
                "Two applications a week apart cleared a severe aphid infestation on my brassicas without harming beneficial insects. The concentrate is good value.");
        r(p9, u.alice(), 5, "No transplant shock whatsoever — brilliant concept",
                "Transplanted dozens of seedlings tray-and-all. Not a single one wilted. The coir breaks down quickly in soil. Will never go back to plastic trays.");
    }

    // ── 3. Little Learners ────────────────────────────────────────────────────

    private void seedLittleLearners(SeededUsers u) {
        User owner = merchant("merchant.learners@shopwave.dev", "Priya", "Nair");
        Company co = company(owner, "Little Learners", "Toys & Educational Products",
                "42 Innovation Way", "Minneapolis", "US", "55401",
                "+16125550303", "play@littlelearners.dev", "https://littlelearners.dev",
                "STEM toys and educational materials that make learning genuinely fun.", 2021, 6);
        invLoc(co, "Minneapolis Fulfillment", "LL-MAIN");

        var p0 = prod(co, "STEM Magnetic Building Blocks 100-Piece",
                "Strong neodymium magnets in BPA-free ABS plastic tiles. Builds STEM skills in 3D construction and spatial reasoning for ages 3+.",
                "LL-001", "54.99", "Construction Toys", "MagBuild", 130, 18);
        var p1 = prod(co, "Children's Watercolour Paint Set 36-Colour",
                "Non-toxic, AP-certified watercolours in a compact tin. Includes 2 brushes, a mixing palette, and a water brush. Vibrant pigments that blend easily.",
                "LL-002", "19.99", "Art Supplies", "ArtKids", 200, 25);
        var p2 = prod(co, "Kids' Educational Microscope 300x–1200x",
                "Dual illumination — LED and mirror. Includes 5 prepared slides, blank slides, forceps, and a full-colour activity guide for ages 8+.",
                "LL-003", "44.99", "Science Kits", "MicroExplore", 80, 10);
        var p3 = prod(co, "Family Strategy Board Game",
                "Award-winning tile-placement game for 2–4 players, ages 7+. Average play time 30–45 minutes. No reading required.",
                "LL-004", "34.99", "Board Games", "PlaySmart", 110, 15);
        var p4 = prod(co, "Alphabet & Sight Word Flash Cards",
                "Double-sided laminated cards — 52 alphabet cards plus 100 sight words from Dolch and Fry lists. Ring-bound for easy sorting.",
                "LL-005", "14.99", "Learning Resources", "ReadySet", 250, 35);
        var p5 = prod(co, "Coding Robot for Kids Ages 5+",
                "Screen-free coding via press-sequence buttons. Teaches sequences, loops, and conditionals. STEM award winner 2023. Includes activity cards.",
                "LL-006", "64.99", "STEM Toys", "CodeBot", 70, 10);
        var p6 = prod(co, "Premium Crayons 64-Colour Pack",
                "Professional-grade wax formula — vivid colour laydown, smooth blending. Includes 8 metallic shades and 8 neon colours alongside the standard palette.",
                "LL-007", "8.99", "Art Supplies", "ChromaKids", 400, 60);
        var p7 = prod(co, "Double-Sided Magnetic Drawing Board",
                "One side draws in colour with magnetic stylus pen — erase instantly with the slider. Reverse side is a traditional chalk surface. 16x12 inches.",
                "LL-008", "22.99", "Drawing Toys", "DoodleMag", 150, 20);
        var p8 = prod(co, "Junior Science Experiment Kit 50 Projects",
                "Covers chemistry, physics, and biology. All chemicals are household-safe. Includes lab coat, safety goggles, and illustrated instruction booklet for ages 8–12.",
                "LL-009", "49.99", "Science Kits", "ExploreLab", 95, 12);
        var p9 = prod(co, "Wooden Jigsaw Puzzle Set of 4",
                "Progressive difficulty — 12, 25, 50, and 100 pieces. Laser-cut basswood with vibrant water-based inks. Ages 3–8. FSC-certified materials.",
                "LL-010", "27.99", "Puzzles", "WoodWise", 120, 18);

        r(p0, u.alice(), 5, "Best toy we've bought — hours of creative play",
                "My 5-year-old has built towers, animals, and a 'spaceship'. The magnets are strong enough to stay together and she has yet to break a single piece after months of use.");
        r(p1, u.bob(), 4, "Vibrant colours, great starter set",
                "The pigments are richer than I expected for a kids' set. The compact tin is perfect for travel and the water brush is a smart addition. Paper choice matters a lot for the results.");
        r(p2, u.carol(), 5, "Sparked a genuine interest in science",
                "My 9-year-old spent an entire Saturday looking at pond water slides. The dual illumination is helpful and the prepared slides are well-made. Activity guide is thorough.");
        r(p3, u.alice(), 5, "Our most-played family game by far",
                "Fast to learn and plays in under 40 minutes so it fits a weeknight. The tile artwork is beautiful and the strategy scales well as kids get older. Genuinely replayable.");
        r(p4, u.bob(), 4, "Exactly what my kindergartner needed",
                "The ring binding is practical — we sort by category regularly. The laminating holds up to sticky fingers. My daughter went from 10 to 85 sight words in 6 weeks.");
        r(p5, u.carol(), 5, "No screen, no problem — my 6-year-old is hooked",
                "The screen-free approach is what sold me and it works brilliantly. The bot is responsive and the activity card challenges scale in difficulty nicely. Robust build quality.");
        r(p6, u.alice(), 4, "The metallic shades are a big hit",
                "Quality is noticeably better than supermarket crayons — they lay down colour without pressure and blend well. My daughter uses the metallics for everything now.");
        r(p7, u.bob(), 5, "Best toddler toy we own — clean, quiet fun",
                "No mess, no noise, and my 2-year-old is obsessed. The magnetic pen glides smoothly and the erase slider is satisfying to use. Chalk side is a bonus we haven't explored yet.");
        r(p8, u.carol(), 4, "Excellent science kit — a few experiments need adult supervision",
                "45 of 50 experiments worked well independently for my 10-year-old. The chemistry section is the highlight. Safety goggles and lab coat add to the fun.");
        r(p9, u.alice(), 5, "Gorgeous quality and great progression of difficulty",
                "The laser-cut precision is evident — pieces fit perfectly without being frustratingly tight. My daughter started with the 12-piece and is now confidently doing the 100-piece.");
    }

    // ── 4. GourmetGrains ─────────────────────────────────────────────────────

    private void seedGourmetGrains(SeededUsers u) {
        User owner = merchant("merchant.gourmet@shopwave.dev", "Luca", "Ferrara");
        Company co = company(owner, "GourmetGrains", "Specialty & Artisan Food",
                "12 Cannery Row", "San Francisco", "US", "94107",
                "+14155550404", "taste@gourmetgrains.dev", "https://gourmetgrains.dev",
                "Artisan ingredients sourced directly from small-batch producers worldwide.", 2018, 5);
        invLoc(co, "SF Cold Storage", "GG-MAIN");

        var p0 = prod(co, "Stone-Milled Sourdough Flour 5lb",
                "Heritage Red Fife wheat stone-milled on traditional millstones. Whole-grain nutrition with superior flavour complexity. Ideal for sourdough and rye loaves.",
                "GG-001", "12.99", "Baking", "MillRight", 200, 30);
        var p1 = prod(co, "Cold-Pressed Extra Virgin Olive Oil 750ml",
                "Single-estate Arbequina olives harvested and cold-pressed within 4 hours. Polyphenol count verified at 350mg/kg. Fruity with peppery finish.",
                "GG-002", "24.99", "Oils & Vinegars", "GrovePure", 120, 15);
        var p2 = prod(co, "Raw Wildflower Honey 32oz",
                "Unfiltered, unpasteurised honey sourced from a single apiary in Montana. Retains natural enzymes and pollen. Crystallises naturally in cool temperatures.",
                "GG-003", "19.99", "Honey & Sweeteners", "HiveTrue", 180, 20);
        var p3 = prod(co, "Himalayan Pink Salt Fine Grind 2lb",
                "Unrefined mineral-rich salt from ancient sea deposits at 5,000ft altitude. 84 trace minerals. Food-grade with no additives or anti-caking agents.",
                "GG-004", "9.99", "Spices & Salts", "MineralMill", 300, 40);
        var p4 = prod(co, "Ceremonial Grade Matcha 30g",
                "First-flush shade-grown tencha from Uji, Japan. Stone-ground to 5 micron particle size. Vibrant green colour with umami sweetness and no bitterness.",
                "GG-005", "34.99", "Tea & Coffee", "UjiLeaf", 100, 12);
        var p5 = prod(co, "18-Year Aged Balsamic Vinegar 250ml",
                "Traditional DOP certified from Modena. Aged in seven successive wood barrels — cherry, chestnut, oak, mulberry, ash, walnut, juniper. Syrupy consistency.",
                "GG-006", "44.99", "Oils & Vinegars", "AcetaiaRosa", 60, 8);
        var p6 = prod(co, "Single-Origin Dark Chocolate Tasting Box",
                "Six 50g bars — Ecuador, Peru, Madagascar, Vietnam, Ghana, and Papua New Guinea origins. 70–85% cacao, no emulsifiers. Tasting notes included.",
                "GG-007", "29.99", "Chocolate & Sweets", "OriginBar", 90, 12);
        var p7 = prod(co, "Cold Brew Coffee Concentrate Kit",
                "250g of coarse-ground specialty Guatemalan blend plus a 1L glass cold brew jar with stainless steel filter. Makes 8 servings. Instructions and ratio guide included.",
                "GG-008", "22.99", "Tea & Coffee", "BrewSlow", 110, 15);
        var p8 = prod(co, "Black Truffle Sea Salt 100g",
                "Fleur de sel infused with real Périgord black truffle (5% truffle content). Finishing salt — use on eggs, pasta, popcorn, or steak.",
                "GG-009", "17.99", "Spices & Salts", "TrufSalt", 140, 18);
        var p9 = prod(co, "Dried Wild Porcini Mushroom Mix 2oz",
                "Hand-harvested and sun-dried porcini (Boletus edulis) from the Italian Apennines. Intense umami — 1oz of dried equals 8oz fresh. Perfect for risotto and sauces.",
                "GG-010", "14.99", "Pantry", "ForestPick", 160, 22);

        r(p0, u.alice(), 5, "Best sourdough I've ever baked",
                "The flavour complexity from heritage wheat is unmistakable. My starter responds noticeably better to this flour and the crumb structure has improved dramatically.");
        r(p1, u.bob(), 5, "Tastes completely different from supermarket EVOO",
                "The peppery finish and fruity notes are genuine — you can taste the freshness. I've stopped cooking with it and use it exclusively as a finishing oil now.");
        r(p2, u.carol(), 4, "Beautiful honey — crystallised quickly but that's expected",
                "Flavour is complex and floral. It crystallised in two weeks but I warm the jar in water and it returns to liquid. The raw texture is different to processed honey.");
        r(p3, u.alice(), 4, "Good mineral flavour — subtle difference from table salt",
                "The pink colour is lovely and I notice a slightly less harsh saltiness than refined salt. The fine grind works well in a salt shaker. Good value for the quantity.");
        r(p4, u.bob(), 5, "The most genuine matcha I've had outside Japan",
                "The colour is an almost neon green and the umami is exactly right. I make usucha and it's smooth and slightly sweet with no bitterness whatsoever. Worth every penny.");
        r(p5, u.carol(), 5, "A proper thick balsamic — transformed my cooking",
                "Drizzled over burrata it's extraordinary. The complexity from seven wood types is real — layers of flavour that cheap balsamic simply can't replicate. Use it sparingly.");
        r(p6, u.alice(), 5, "The Madagascar bar alone is worth the purchase",
                "Each bar has genuinely distinct character. Madagascar is bright and fruity, Ecuador is earthy, Ghana has a deep roasted note. The tasting notes are accurate.");
        r(p7, u.bob(), 4, "Best cold brew I've made at home",
                "The Guatemalan blend makes a very smooth concentrate. Ratio guide is spot-on — 1:4 with oat milk is perfect. Glass jar is quality but heavy for the fridge door.");
        r(p8, u.carol(), 5, "Truffle flavour is genuine — not the synthetic oil version",
                "The truffle aroma is real and subtle, not the artificial truffle oil smell. A pinch on scrambled eggs elevates them completely. The fleur de sel base is high quality.");
        r(p9, u.alice(), 4, "Intense porcini flavour — a little goes a long way",
                "Rehydrated in warm water for my risotto and the resulting broth was incredible. The umami depth is exceptional. 2oz is pricey but you really do only need small amounts.");
    }

    // ── 5. CraftSupplies Co. ─────────────────────────────────────────────────

    private void seedCraftSupplies(SeededUsers u) {
        User owner = merchant("merchant.craft@shopwave.dev", "Sofia", "Lindqvist");
        Company co = company(owner, "CraftSupplies Co.", "Arts, Crafts & DIY",
                "77 Studio Lane", "Brooklyn", "US", "11201",
                "+17185550505", "create@craftsupplies.dev", "https://craftsupplies.dev",
                "Carefully curated supplies for fine artists, crafters, and hobbyists.", 2020, 7);
        invLoc(co, "Brooklyn Studio Warehouse", "CS-MAIN");

        var p0 = prod(co, "Professional Acrylic Paint Set 24-Colour",
                "Heavy-body artist-grade acrylics with high pigment load. Lightfast and archival. Each 60ml tube covers well and blends smoothly on canvas or board.",
                "CS-001", "39.99", "Paint", "ChromaArt", 140, 18);
        var p1 = prod(co, "Calligraphy & Hand Lettering Starter Kit",
                "Includes 2 dip nibs, oblique holder, 8 ink colours, 50 practice sheets, and a 48-page illustrated instruction guide by a professional letterer.",
                "CS-002", "29.99", "Calligraphy", "InkScript", 110, 14);
        var p2 = prod(co, "UV Resin Art Bundle with Moulds",
                "Crystal-clear low-viscosity UV resin (200ml), 15 silicone moulds, pigment powder set, UV lamp, and tools. Complete for jewellery, keychains, and coasters.",
                "CS-003", "54.99", "Resin Art", "ClearCast", 75, 10);
        var p3 = prod(co, "Embroidery Starter Set — Hoops & Threads",
                "Five bamboo hoops (4–10 inch), 50-colour DMC floss organiser, water-soluble transfer pen, and needle set. Four beginner pattern templates included.",
                "CS-004", "24.99", "Embroidery", "ThreadCraft", 160, 22);
        var p4 = prod(co, "Vegetable-Tanned Leather Crafting Kit",
                "2mm shoulder leather pre-cut to A4 sheets (4 pieces), plus saddle thread, needles, beeswax, edge beveller, and stitching chisels. Makes a wallet or card holder.",
                "CS-005", "79.99", "Leatherwork", "HideCraft", 45, 6);
        var p5 = prod(co, "Lino Printmaking Starter Set",
                "3 lino blocks, 5 carving gouges, water-based block printing ink in 6 colours, a brayer, and a 32-page project guide. Suitable for ages 12+.",
                "CS-006", "34.99", "Printmaking", "BlockPress", 90, 12);
        var p6 = prod(co, "Cold Press Watercolour Paper Pad 9×12in",
                "140lb/300gsm 100% cotton rag paper. 20 sheets per pad with gummed edges for flat working. Accepts heavy wet washes without buckling.",
                "CS-007", "16.99", "Paper", "CottonPage", 200, 28);
        var p7 = prod(co, "White Air-Dry Modelling Clay 5lb",
                "Smooth, crack-resistant air-dry clay. Self-hardening in 24 hours. Lightweight when dry. Accepts acrylic paint, sealant, and stamps. Great for sculpture and pottery simulation.",
                "CS-008", "19.99", "Clay & Sculpting", "SoftForm", 130, 18);
        var p8 = prod(co, "Natural Cotton Macramé Starter Kit",
                "100m of 3-ply natural cotton cord, 8 birch dowels, 12 brass rings, and a detailed beginner guide with 5 patterns — plant hangers, wall art, and a small shelf.",
                "CS-009", "34.99", "Macramé", "KnotCraft", 100, 14);
        var p9 = prod(co, "Beginner Crochet Bundle — Hooks & Yarn",
                "9-piece aluminium hook set (2–6mm), 400g of DK-weight cotton yarn in 5 colours, stitch markers, tapestry needles, and a step-by-step guide for 3 beginner projects.",
                "CS-010", "27.99", "Crochet & Knitting", "YarnStart", 115, 16);

        r(p0, u.alice(), 5, "Pigment density rivals paints three times the price",
                "The coverage on a single pass is impressive. Colours are vivid and the consistency is perfect for palette knife work. Lightfast ratings are what you'd expect from artist grade.");
        r(p1, u.bob(), 4, "Excellent starter kit — ink quality surprised me",
                "The oblique holder is nicely weighted and the practice sheets have the right guideline spacing. The ink range is generous. I was writing consistently within a week.");
        r(p2, u.carol(), 5, "Everything you need — excellent UV lamp inclusion",
                "The UV lamp cures the resin thoroughly in 2 minutes. Resin is crystal clear and low bubble. The mould range is versatile. Only tip: use a silicone mat under everything.");
        r(p3, u.alice(), 4, "Perfect starter set — DMC floss organiser is excellent",
                "The 50-colour selection covers everything in beginner patterns. Bamboo hoops are smooth and grip fabric well. The pattern templates are detailed and achievable for beginners.");
        r(p4, u.bob(), 5, "Made a beautiful bifold wallet on my first try",
                "The pre-cut leather is excellent quality with good temper. The saddle thread is waxed and doesn't tangle. The stitching chisels cut clean holes. Exceptional value.");
        r(p5, u.carol(), 5, "Completely new hobby unlocked — printmaking is addictive",
                "The gouges are sharp and responsive on the lino. The water-based inks clean up easily and printed cleanly even on my first attempt. The project guide is genuinely inspiring.");
        r(p6, u.alice(), 5, "Zero buckling even on very wet washes",
                "I've been using 300gsm cotton paper for years and this matches the quality of brands costing 40% more. The gummed edges keep sheets flat throughout the drying process.");
        r(p7, u.bob(), 4, "Smooth texture, takes detail well",
                "The clay is easy to work and doesn't crack on thin pieces if you keep it covered while working. Dries in 18–20 hours. Sands beautifully and takes acrylic paint perfectly.");
        r(p8, u.carol(), 5, "Kit is complete — hanging plant holder turned out perfectly",
                "The cord quality is excellent — smooth, consistent ply, and the natural colour looks beautiful. The guide photographs are clear and accurate. First project completed in 3 hours.");
        r(p9, u.alice(), 4, "Great bundle for an absolute beginner",
                "The aluminium hooks are smooth and the size labelling is clear. Cotton yarn is easy to see your stitches in, which matters a lot when learning. Completed a dishcloth on day 2.");
    }

    // ── 6. BeautyBlend ───────────────────────────────────────────────────────

    private void seedBeautyBlend(SeededUsers u) {
        User owner = merchant("merchant.beauty@shopwave.dev", "Zara", "Ahmed");
        Company co = company(owner, "BeautyBlend", "Natural Beauty & Skincare",
                "500 Melrose Ave", "Los Angeles", "US", "90046",
                "+13235550606", "glow@beautyblend.dev", "https://beautyblend.dev",
                "Clean, effective skincare and beauty tools formulated without harmful additives.", 2021, 9);
        invLoc(co, "LA Fulfilment", "BB-MAIN");

        var p0 = prod(co, "Vitamin E & Rosehip Hydrating Face Cream",
                "Rich barrier-repair moisturiser with 2% vitamin E tocopherol and rosehip seed oil. Fragrance-free. Suitable for dry, sensitive, and mature skin types.",
                "BB-001", "28.99", "Moisturisers", "DermaPure", 150, 20);
        var p1 = prod(co, "100% Pure Argan Oil 60ml",
                "Cold-pressed, first-extraction argan oil. COSMOS certified organic. Multi-use — face, hair, body, nails. Absorbs quickly without greasiness.",
                "BB-002", "24.99", "Face Oils", "ArganTrue", 120, 15);
        var p2 = prod(co, "Bulgarian Rose Water Facial Toner 200ml",
                "Steam-distilled from Rosa damascena. No alcohol, preservatives, or added fragrance. Balances pH, tones pores, and preps skin for serum. Certified organic.",
                "BB-003", "18.99", "Toners", "RoseDistil", 180, 24);
        var p3 = prod(co, "Organic Tinted Lip Balm Set of 6",
                "Sheer colour in six flattering shades. Base of shea butter, coconut oil, and beeswax. SPF 15. USDA organic certified. Moisturises for up to 8 hours.",
                "BB-004", "16.99", "Lip Care", "PetalTint", 200, 28);
        var p4 = prod(co, "Vegan Bamboo Foundation Brush Set 8-Piece",
                "Synthetic Taklon bristles with FSC-certified bamboo handles. Dense, streak-free application for powder and liquid. Hand-tied and kiln-dried for durability.",
                "BB-005", "34.99", "Makeup Tools", "BrushWise", 100, 12);
        var p5 = prod(co, "Deep Hydrating Sheet Mask Collection 10-Pack",
                "10 unique formulas — hyaluronic acid, ceramide, vitamin C, collagen, retinol, green tea, niacinamide, aloe, honey, and peptide. Biodegradable cellulose sheets.",
                "BB-006", "22.99", "Sheet Masks", "MaskBlend", 160, 22);
        var p6 = prod(co, "Caffeine Eye Cream Anti-Puffiness 30ml",
                "5% caffeine complex with peptides and niacinamide. Reduces puffiness and dark circles. Lightweight gel-cream texture absorbs without pilling under concealer.",
                "BB-007", "26.99", "Eye Care", "WakeEye", 110, 14);
        var p7 = prod(co, "Calendula Cleansing Balm 100ml",
                "Melts makeup and SPF on contact — rinse off or wipe away. Calendula flower extract calms irritation. Suitable for all skin types including rosacea-prone.",
                "BB-008", "29.99", "Cleansers", "BloomCleanse", 130, 18);
        var p8 = prod(co, "Walnut & Sugar Exfoliating Face Scrub",
                "Dual-action physical and enzymatic exfoliation. Finely milled walnut shell powder plus papain enzyme. Suitable for 2–3x weekly use. Cruelty-free and vegan.",
                "BB-009", "19.99", "Exfoliators", "SmoothGrain", 140, 20);
        var p9 = prod(co, "Crystal Nail File & Buffer Care Kit",
                "Tempered glass nail file with 120-grit edge — never dulls and dishwasher safe. Includes 4-way buffer block and cuticle pusher in a leather zip case.",
                "BB-010", "12.99", "Nail Care", "NailCrystal", 200, 28);

        r(p0, u.alice(), 5, "My skin barrier fully recovered using this",
                "I had a damaged barrier from over-exfoliating and this cream rebuilt it within two weeks. The vitamin E content is clearly high — no fragrance and beautifully absorbed.");
        r(p1, u.bob(), 4, "Works on beard dry patches — genuinely versatile",
                "I use two drops on my face and a few on beard areas after washing. Absorbs in under a minute and the organic certification matters to me. No heavy oil smell.");
        r(p2, u.carol(), 5, "The closest thing to fresh rose water I've used",
                "The scent is real rose, not synthetic — you can tell immediately. My skin tone has evened noticeably since adding this as a toner step. No alcohol means no tightness.");
        r(p3, u.alice(), 4, "The coral shade is perfect — natural and moisturising",
                "These sit beautifully on dry lips. The sheer tint looks professional and the SPF 15 is a practical bonus. I keep one in every bag. The shea base is genuinely nourishing.");
        r(p4, u.bob(), 4, "Excellent brushes — better than my department store set",
                "The bristle density is impressive and the bamboo handles are comfortable. Powder application is streak-free with the kabuki brush. Easy to clean and quick to dry.");
        r(p5, u.carol(), 5, "Ten different formulas — the hyaluronic and peptide are my favourites",
                "Each mask is genuinely differentiated, not just relabelled. The cellulose sheets fit well and stay wet throughout. 20-minute treatment and my skin visibly glows after.");
        r(p6, u.alice(), 5, "Dark circles reduced in under two weeks",
                "I was sceptical but the caffeine genuinely works on my morning puffiness. It layers perfectly under concealer without pilling. The niacinamide has brightened the area over time.");
        r(p7, u.bob(), 5, "Removes everything — even waterproof mascara",
                "One pump melts all makeup on contact. The calendula means my skin doesn't feel stripped after use. I use it as a first cleanse before my gel cleanser and it's excellent.");
        r(p8, u.carol(), 4, "Very effective scrub — start with once a week",
                "The dual action (physical + enzymatic) is gentler than straight walnut scrubs I've used. Results are smooth and glowing. I'd recommend starting with once a week to gauge sensitivity.");
        r(p9, u.alice(), 5, "The glass file is revelatory — never going back to emery",
                "The edge seals the nail as it files so no snagging or peeling. The 4-way buffer gives a genuinely mirror shine. The leather case makes it giftable and protects the glass.");
    }

    // ── 7. AutoEdge ──────────────────────────────────────────────────────────

    private void seedAutoEdge(SeededUsers u) {
        User owner = merchant("merchant.auto@shopwave.dev", "Derek", "Novak");
        Company co = company(owner, "AutoEdge", "Automotive Accessories",
                "1400 Motor Way", "Detroit", "US", "48226",
                "+13135550707", "drive@autoedge.dev", "https://autoedge.dev",
                "High-quality automotive accessories for every driver — from daily commuters to enthusiasts.", 2017, 14);
        invLoc(co, "Detroit Distribution Hub", "AE-MAIN");

        var p0 = prod(co, "Wireless Fast-Charge Car Mount",
                "15W Qi2 wireless charging with automatic clamping. Dashboard or vent mount. Compatible with all Qi-enabled devices up to 0.35 inch thick with cases.",
                "AE-001", "34.99", "Car Electronics", "MountCharge", 170, 22);
        var p1 = prod(co, "4K Dual-Channel Dash Cam",
                "Front 4K + rear 1080p recording. Sony STARVIS night vision sensor. Loop recording, G-sensor emergency lock, and 128GB card support. App-connected via WiFi.",
                "AE-002", "119.99", "Dash Cameras", "RoadEye", 80, 10);
        var p2 = prod(co, "Heavy-Duty All-Weather Car Floor Mats",
                "Custom-fit laser-measured mats in rubberised thermoplastic. 1-inch raised lip traps water, mud, and snow. Universal fit option also available.",
                "AE-003", "59.99", "Interior Accessories", "FloorShield", 110, 14);
        var p3 = prod(co, "Professional Microfibre Detailing Kit 16-Piece",
                "Includes 8 microfibre cloths (400gsm), 2 wash mitts, foam applicators, detailing brushes, and a tyre dressing sponge. Colour-coded to prevent cross-contamination.",
                "AE-004", "44.99", "Car Care", "DetailPro", 130, 18);
        var p4 = prod(co, "2000A Peak Portable Jump Starter",
                "Starts engines up to 8.0L gas and 6.0L diesel. Built-in 18000mAh power bank with dual USB-A and USB-C outputs. LED torch and safety clamps included.",
                "AE-005", "89.99", "Emergency Gear", "PowerStart", 65, 8);
        var p5 = prod(co, "Digital Tyre Pressure Gauge with Backlight",
                "0.1 PSI accuracy across 0–150 PSI range. 360° swivel head for easy access. Backlit LCD display. Auto shut-off after 30 seconds of inactivity.",
                "AE-006", "22.99", "Tools & Gauges", "PressureTech", 200, 28);
        var p6 = prod(co, "HEPA Car Air Purifier USB",
                "True HEPA H13 filter removes 99.97% of particles down to 0.3 microns. Active carbon layer absorbs VOCs and odours. Quiet at 25dB. Fits any 12V USB socket.",
                "AE-007", "39.99", "Air Quality", "CabinAir", 140, 18);
        var p7 = prod(co, "RGB LED Interior Strip Lights",
                "App-controlled 4-piece LED strip with 16M colour options and sound-reactive mode. Waterproof, adhesive-backed, and includes a USB control box. Works with CarPlay.",
                "AE-008", "19.99", "Interior Lighting", "CabinGlow", 220, 30);
        var p8 = prod(co, "Car Seat Back Organiser with Tablet Holder",
                "4 mesh pockets, foldable tray table, and a padded tablet holder up to 11 inches. Non-slip hooks won't damage headrests. 600D Oxford waterproof fabric.",
                "AE-009", "29.99", "Interior Organizers", "BackPocket", 150, 20);
        var p9 = prod(co, "Premium Emergency Road Safety Kit",
                "Includes LED road flares (3x), folding warning triangle, heavy-duty tow rope, first aid kit (35-piece), reflective vest, and window breaker/seatbelt cutter.",
                "AE-010", "49.99", "Safety", "SafeRoad", 90, 12);

        r(p0, u.alice(), 4, "Convenient charging — glass case compatible",
                "Works reliably with a 4mm thick case on my phone. Auto-clamping is satisfying and secure. Vent mount is sturdy enough for motorway driving. Good 15W charge speed.");
        r(p1, u.bob(), 5, "4K footage is extraordinary for insurance evidence",
                "The Sony sensor handles night driving without the grey noise I had on my previous dashcam. App footage transfer via WiFi is fast and reliable. Installation took 20 minutes.");
        r(p2, u.carol(), 5, "One year in — still look brand new despite winter slush",
                "The raised lip contains genuinely impressive quantities of mud and water. The laser-fit for my SUV is exact — no gaps anywhere. Smell disappears after a few days.");
        r(p3, u.alice(), 5, "Professional detailers use these for a reason",
                "The 400gsm cloths are noticeably thicker than budget microfibre. Colour coding is genuinely useful — I'll never contaminate a paint cloth with a wheel cloth again.");
        r(p4, u.bob(), 4, "Started my dead diesel instantly — and charged my phone",
                "The 2000A claim is real — my 2.0L diesel started without hesitation at -5°C. The power bank function charged my phone twice after the jump. Safety clamps are well insulated.");
        r(p5, u.carol(), 4, "Accurate and easy to use with the swivel head",
                "Checked against my garage's calibrated gauge and it was within 0.2 PSI. The backlight is bright enough for underground car parks. The 360° head is genuinely useful.");
        r(p6, u.alice(), 5, "Allergy sufferers — this is the car accessory you need",
                "My hayfever was noticeably worse in the car than outdoors. After two weeks with this purifier the difference is significant. Quiet enough to forget it's running.");
        r(p7, u.bob(), 4, "Excellent ambient effect — sound reactive mode is fun",
                "Setup via the app was straightforward and the adhesion is strong after a month. Sound reactive mode syncs well with music. The static modes are elegant for daily driving.");
        r(p8, u.carol(), 5, "Changed long-drive dynamics with kids completely",
                "The tablet holder saved our last road trip. The tray table is surprisingly sturdy and the mesh pockets hold a significant amount. Hooks are snug and haven't moved.");
        r(p9, u.alice(), 4, "Comprehensive kit — the LED flares are the standout item",
                "The LED flares are far safer than traditional flares and last for hours. First aid kit is genuinely well-stocked. Reflective vest packs into a small bag. Peace of mind kit.");
    }

    // ── 8. DeskMate ──────────────────────────────────────────────────────────

    private void seedDeskMate(SeededUsers u) {
        User owner = merchant("merchant.deskmate@shopwave.dev", "Tom", "Eriksson");
        Company co = company(owner, "DeskMate", "Office & Stationery",
                "88 Workspace Blvd", "Austin", "US", "78702",
                "+15125550808", "work@deskmate.dev", "https://deskmate.dev",
                "Ergonomic and productivity tools that make the modern desk a better place to work.", 2019, 11);
        invLoc(co, "Austin Office Hub", "DM-MAIN");

        var p0 = prod(co, "Standing Desk Riser Converter 32in",
                "Dual-tier pneumatic lift — raises to 16 inches in one motion. Holds up to 33lbs. Keyboard tray with anti-fatigue padding. Fits desks from 27 to 71 inches wide.",
                "DM-001", "149.99", "Ergonomics", "RiseDesk", 50, 6);
        var p1 = prod(co, "Dual Monitor Spring-Loaded Arm",
                "Gas-spring mechanism supports two monitors 13–32 inches, up to 17lbs each. Full articulation — pan, tilt, and rotate. Single clamp or grommet mount. Cable management channels.",
                "DM-002", "79.99", "Monitor Mounts", "ArmFlex", 70, 8);
        var p2 = prod(co, "Brass-Tipped Mechanical Pencil Set 4-Piece",
                "Matte black aluminium body with solid brass ferrule. 0.3, 0.5, 0.7, and 0.9mm lead diameters. Each includes 12 leads and a click eraser.",
                "DM-003", "22.99", "Writing Instruments", "PenCraft", 160, 22);
        var p3 = prod(co, "Full-Grain Leather Desk Pad 31×15in",
                "Vegetable-tanned full-grain leather with natural edge burnishing. Soft felt base protects the desk. Develops a patina over time. Available in slate, tan, and black.",
                "DM-004", "59.99", "Desk Accessories", "LeatherDesk", 85, 10);
        var p4 = prod(co, "Cable Management Box with Lid",
                "Hides power strips and excess cables in a vented wooden box. Dimensions: 14×5.5×5.5 inches. Pass-through slots for cable routing. Walnut, white, or black finish.",
                "DM-005", "24.99", "Cable Management", "CableBox", 130, 18);
        var p5 = prod(co, "Compact Wireless Keyboard 75%",
                "Hot-swappable switches (linear pre-installed). Tri-mode — Bluetooth (2 devices), 2.4GHz dongle, USB-C wired. 8000mAh battery lasts 3+ months wireless. PBT keycaps.",
                "DM-006", "59.99", "Keyboards", "TypeFlex", 90, 12);
        var p6 = prod(co, "Blue Light Blocking Computer Glasses",
                "CR-39 lenses with UV400 and blue light filter to 450nm. Non-prescription clear lenses in a lightweight titanium frame. Includes hardshell case and cloth.",
                "DM-007", "34.99", "Eyewear", "ClearSight", 120, 16);
        var p7 = prod(co, "A5 Undated Planner & Notebook Bundle",
                "Planner: weekly, monthly, and project spreads — 256 pages. Notebook: dot-grid, 192 pages, 100gsm Tomoe River paper. Elastic closures and ribbon markers on both.",
                "DM-008", "29.99", "Notebooks & Planners", "PageCraft", 150, 20);
        var p8 = prod(co, "Bamboo Desktop Organiser 6-Compartment",
                "Sustainably sourced bamboo with a natural lacquer finish. Holds pens, scissors, phone, tablet, and documents. Removable dividers reconfigure the layout.",
                "DM-009", "39.99", "Desk Organisation", "BambooDesk", 100, 14);
        var p9 = prod(co, "12-Port USB-C Docking Station",
                "Single Thunderbolt 4 cable delivers 100W charging, dual 4K@60Hz display, USB-A 10Gbps, SD/microSD, Ethernet, and 3.5mm audio. Compact aluminium housing.",
                "DM-010", "89.99", "Docking Stations", "DockHub", 60, 8);

        r(p0, u.alice(), 5, "Eliminated my back pain within a week",
                "I alternate sitting and standing every 45 minutes now. The pneumatic lift is smooth and the keyboard tray is at the perfect angle. Solid enough that nothing wobbles when typing.");
        r(p1, u.bob(), 5, "Best monitor arm I've used — zero drift",
                "Gas-spring is stiff enough that my monitors stay exactly where I put them. The cable management channels keep everything tidy. Installation took 10 minutes with the clamp mount.");
        r(p2, u.carol(), 5, "The best writing experience in a mechanical pencil",
                "The brass ferrule adds real weight to the balance. The 0.5mm is my daily driver and the line quality is consistent. Refilling is a non-event — just click and reload.");
        r(p3, u.alice(), 4, "Beautiful desk pad — leather quality is excellent",
                "The full-grain texture and natural edge are exactly as described. Mouse tracking is smooth on the surface. Three months in and it's already developing a lovely patina.");
        r(p4, u.bob(), 4, "Completely hides the cable chaos — looks great",
                "The power strip fits perfectly and the slots route cables neatly. The walnut finish looks far more expensive than it is. Ventilation holes seem adequate — nothing gets warm.");
        r(p5, u.carol(), 5, "My favourite keyboard at any price",
                "Tri-mode is a genuine game-changer for switching between my laptop and desktop. The linear switches are smooth and quiet. Battery life has been exceptional — now at 6 weeks.");
        r(p6, u.alice(), 4, "Reduced my evening eye strain noticeably",
                "I work long hours on a monitor and my eyes were consistently tired by evening. Two weeks of wearing these and the fatigue has reduced. Lightweight frame is comfortable all day.");
        r(p7, u.bob(), 5, "The Tomoe River paper in the notebook is exceptional",
                "100gsm Tomoe River barely ghosts with a fountain pen — remarkable for a mass-produced notebook. The planner layout is thoughtfully designed without being restrictive.");
        r(p8, u.carol(), 5, "Genuinely improved how my desk looks and works",
                "The six-compartment layout fits exactly what I need. Bamboo quality is solid and the natural finish complements my other desk accessories. Removable dividers are a smart detail.");
        r(p9, u.alice(), 5, "Replaced three separate hubs — everything works",
                "Dual 4K at 60Hz confirmed, 100W laptop charging confirmed, Ethernet passes gigabit. The Thunderbolt 4 spec is actually delivered. Compact enough to fit under my monitor arm.");
    }

    // ── 9. OutdoorPeak ───────────────────────────────────────────────────────

    private void seedOutdoorPeak(SeededUsers u) {
        User owner = merchant("merchant.outdoor@shopwave.dev", "Josh", "Walker");
        Company co = company(owner, "OutdoorPeak", "Outdoor Recreation & Camping",
                "55 Summit Trail", "Boulder", "US", "80302",
                "+13035550909", "explore@outdoorpeak.dev", "https://outdoorpeak.dev",
                "Lightweight, durable outdoor gear for hikers, climbers, and backcountry campers.", 2016, 16);
        invLoc(co, "Boulder Gear Depot", "OP-MAIN");

        var p0 = prod(co, "2-Person Ultralight Backpacking Tent",
                "Double-wall silnylon construction. 1.8kg packed weight. Free-standing with two vestibules. 3000mm waterproof rating. Pitches in 4 minutes in all conditions.",
                "OP-001", "189.99", "Shelters", "PeakShield", 40, 5);
        var p1 = prod(co, "Merino Wool Base Layer Long Sleeve",
                "195gsm 100% Zque-certified merino. Naturally odour-resistant for multi-day wear. Flatlock seams. Temperature-regulating across a wide activity range.",
                "OP-002", "79.99", "Clothing", "MerinoSkin", 90, 12);
        var p2 = prod(co, "Carbon Fibre Trekking Pole Set",
                "100% carbon fibre shaft — ultralight at 230g per pole. Ergonomic cork grips with wrist straps. 3-section telescoping from 65–135cm. Includes rubber tip covers.",
                "OP-003", "69.99", "Trekking Poles", "CarbonStride", 70, 10);
        var p3 = prod(co, "400 Lumen USB-C Rechargeable Headlamp",
                "Spot-to-flood beam with 3 brightness modes and a red night-vision mode. IPX6 waterproof. 75-hour runtime on low mode. 4-hour full charge via USB-C.",
                "OP-004", "44.99", "Lighting", "BeamHead", 110, 14);
        var p4 = prod(co, "Hollow-Fibre Squeeze Water Filter",
                "0.1 micron filter removes 99.9999% of bacteria and protozoa. No chemicals. Flow rate: 1L/min. Weighs 85g and fits in a pocket. Treats up to 100,000 litres.",
                "OP-005", "34.99", "Water Filtration", "SqueezePure", 130, 18);
        var p5 = prod(co, "7-Day Freeze-Dried Meal Kit",
                "21 meals — breakfast, lunch, and dinner for 7 days. 1800kcal/day average. 25-year shelf life. Rehydrates in 10 minutes with boiling water. No refrigeration required.",
                "OP-006", "129.99", "Food & Nutrition", "TrailFuel", 35, 5);
        var p6 = prod(co, "Ferro Rod & Magnesium Fire Starter Kit",
                "Large 12.5cm ferro rod with magnesium scraper and rope tinder. Produces 5500°F sparks. Waterproof case. Works in rain and at altitude. Strikes 12,000+ times.",
                "OP-007", "19.99", "Fire Making", "StrikeFire", 200, 28);
        var p7 = prod(co, "Synthetic-Fill 3-Season Mummy Sleeping Bag",
                "PrimaLoft Gold insulation rated to 28°F/-2°C. 800g total weight. Offset quilt stitching eliminates cold spots. Draft collar and trapezoidal footbox for warmth.",
                "OP-008", "99.99", "Sleeping Bags", "PrimaSleep", 55, 7);
        var p8 = prod(co, "550 Paracord Braided 100m Spool",
                "MIL-SPEC 550 paracord with 7-strand inner core. 550lb tensile strength. UV and rot resistant. 100m spool for camp repairs, clotheslines, and emergency use.",
                "OP-009", "14.99", "Camp Gear", "StrandStrong", 280, 38);
        var p9 = prod(co, "Ultralight Canister Camp Stove",
                "Weighs 82g. 10,000 BTU output boils 1L in 3.5 minutes. Simmer control valve for cooking versatility. Folds flat. Compatible with all Lindal valve canisters.",
                "OP-010", "49.99", "Camp Cooking", "FlameLight", 85, 10);

        r(p0, u.alice(), 5, "Surprised by how genuinely waterproof 3000mm is",
                "Camped in a Scottish downpour for 3 nights. Not a single drop inside. The two-vestibule design means gear storage doesn't compromise interior space. Easy 4-minute pitch in the dark.");
        r(p1, u.bob(), 5, "Wore this for 5 consecutive days — no odour",
                "The merino claim is real. I do not smell. Temperature regulation across morning frost and afternoon sun on a multi-day hike was exactly as described. Flatlock seams are invisible.");
        r(p2, u.carol(), 4, "Very light — some flex on steep rocky terrain",
                "At 230g per pole these are remarkably light without feeling flimsy. Cork grips are comfortable over long descents. Very slight flex on hard rock strikes but not concerning for trails.");
        r(p3, u.alice(), 5, "The red mode alone makes this worth it for camping",
                "400 lumens in spot mode is more than enough for technical night hiking. Red mode preserves night vision perfectly. USB-C charging means one cable for everything in my pack.");
        r(p4, u.bob(), 5, "Filtered a glacial stream — worked perfectly",
                "Backcountry trip in the Cascades with sketchy water sources. This filter performed flawlessly every time. The flow rate genuinely stays at 1L/min even after 50+ uses.");
        r(p5, u.carol(), 4, "Meals are surprisingly good — portions are generous",
                "The chicken and rice and chilli mac were excellent. Granola breakfast is genuinely tasty. A couple of meals were bland but the overall quality for freeze-dried is impressive.");
        r(p6, u.alice(), 4, "Produced sparks in driving rain — reliability is excellent",
                "I tested it in my backyard during a rainstorm and got ignition on the fourth strike. The ferro rod is the largest I've owned and the magnesium scraper is a useful backup.");
        r(p7, u.bob(), 5, "Warm to 28°F as claimed — no cold spots",
                "The offset quilt construction really does eliminate the cold spots I used to get at shoulder seams. PrimaLoft Gold performs when damp which matters on wet UK trips.");
        r(p8, u.carol(), 5, "A full spool of MIL-SPEC cord for this price is remarkable",
                "The weave is tight and consistent throughout and the 7-strand inner core is intact throughout the length. I've used paracord for years and this is indistinguishable from military-issue.");
        r(p9, u.alice(), 5, "82 grams to boil water in under 4 minutes — sold",
                "The simmer valve is genuinely controllable which makes real cooking possible, not just boil-in-bag. Folds completely flat into a stuff sack. The only stove I'll take backpacking.");
    }

    // ── 10. KitchenCraft Pro ─────────────────────────────────────────────────

    private void seedKitchenCraft(SeededUsers u) {
        User owner = merchant("merchant.kitchen@shopwave.dev", "Chen", "Liu");
        Company co = company(owner, "KitchenCraft Pro", "Professional Kitchen Tools",
                "900 Culinary Dr", "Chicago", "US", "60607",
                "+13125551010", "cook@kitchencraftpro.dev", "https://kitchencraftpro.dev",
                "Heirloom-quality kitchen tools designed for the serious home cook.", 2015, 20);
        invLoc(co, "Chicago Kitchen Warehouse", "KC-MAIN");

        var p0 = prod(co, "Pre-Seasoned Cast Iron Skillet 10.25in",
                "Foundry-seasoned with flaxseed oil — ready to use immediately. Compatible with all heat sources including induction. Oven-safe to 500°F. Improves with every use.",
                "KC-001", "44.99", "Cookware", "IronCore", 120, 16);
        var p1 = prod(co, "Instant-Read Meat Thermometer",
                "3-second read time with ±0.9°F accuracy. Foldable probe with auto-on/off. Waterproof to IPX7. Pre-programmed USDA temps for all proteins. Includes calibration function.",
                "KC-002", "29.99", "Thermometers", "TempSnap", 180, 24);
        var p2 = prod(co, "Adjustable Mandoline Slicer Pro",
                "430 stainless steel blades with 0–8mm thickness adjustment. Cut-resistant glove and food holder included. Folds flat for storage. Dishwasher-safe except blade.",
                "KC-003", "49.99", "Slicers & Graters", "SlicePro", 90, 12);
        var p3 = prod(co, "Enamelled Cast Iron Dutch Oven 5.5qt",
                "Heavyweight lid creates a self-basting seal. Sand-coloured interior shows fond development. Oven-safe to 500°F. Suitable for braising, soups, and no-knead bread.",
                "KC-004", "89.99", "Cookware", "IronCore", 60, 7);
        var p4 = prod(co, "Heat-Resistant Silicone Utensil Set 5-Piece",
                "BPA-free silicone heads on stainless steel cores. Rated to 480°F. Set includes spatula, spoon, slotted spoon, ladle, and tongs. Dishwasher safe.",
                "KC-005", "24.99", "Utensils", "SiliconeChef", 200, 28);
        var p5 = prod(co, "Conical Burr Spice & Coffee Grinder",
                "Stainless steel conical burr produces consistent particle size from fine espresso to coarse French press. 40g hopper. 17 grind settings. 150-watt motor.",
                "KC-006", "49.99", "Grinders", "GrindRight", 80, 10);
        var p6 = prod(co, "Stainless Digital Kitchen Scale 11lb",
                "±1g accuracy up to 5000g. Tare function. Measures in g, oz, lb, and ml. Slim profile fits in a kitchen drawer. Includes 2 x AAA batteries.",
                "KC-007", "22.99", "Scales", "WeighRight", 220, 30);
        var p7 = prod(co, "Reversible End-Grain Bamboo Cutting Board Set",
                "Two-board set — large (18×12in) and small (12×8in). End-grain construction is gentler on knife edges and self-healing against knife scars. Juice groove on one side.",
                "KC-008", "54.99", "Cutting Boards", "GrainBoard", 95, 12);
        var p8 = prod(co, "Self-Watering Indoor Herb Garden Kit 4-Pod",
                "Includes 4 grow pods, premium seed packets (basil, parsley, chives, thyme), nutrient solution, and a 40W full-spectrum grow light. Harvest in 4–6 weeks.",
                "KC-009", "34.99", "Herb Gardens", "FreshPod", 110, 14);
        var p9 = prod(co, "Pasta Maker Hand-Crank Attachment",
                "Compatible with standard 5qt stand mixer attachment port. Three dies included — fettuccine, spaghetti, and lasagne sheets. Makes 1lb of pasta in 10 minutes.",
                "KC-010", "69.99", "Pasta Tools", "PastaWorks", 55, 7);

        r(p0, u.alice(), 5, "Inherited one of these — now I own two",
                "My grandmother's cast iron is 60 years old. This one is on the same trajectory. Arrived well-seasoned and after 3 months of use it's practically non-stick. Induction performance is excellent.");
        r(p1, u.bob(), 5, "3-second read changed how I cook",
                "I used to guess doneness and over-cook meat out of fear. This thermometer has eliminated that completely. The pre-programmed USDA temps are accurate and the fold-away probe is practical.");
        r(p2, u.carol(), 4, "Very sharp and precise — cut-resistant glove is essential",
                "Produces perfectly uniform slices at every thickness setting. I use it weekly for gratin and coleslaw. The glove that comes with it is genuinely necessary — the blade is razor sharp.");
        r(p3, u.alice(), 5, "The best bread I've ever baked came from this pot",
                "No-knead sourdough in a Dutch oven is the method. The lid seal creates steam that gives commercial-bakery crust. Braises are equally transformative. A kitchen investment that lasts.");
        r(p4, u.bob(), 4, "Well-made set — the tongs are particularly good",
                "The silicone heads don't scratch my non-stick pans and handle high heat without any smell. The tong locking mechanism is spring-loaded and reliable. Easy cleanup in the dishwasher.");
        r(p5, u.carol(), 5, "Finally consistent espresso grind from a home machine",
                "The conical burr makes a genuinely uniform grind. My espresso extraction time is now consistent between shots for the first time. The 17 settings cover every brewing method.");
        r(p6, u.alice(), 4, "Accurate and slim enough for a kitchen drawer",
                "±1g is accurate — I tested it against calibration weights from my lab. The tare function resets instantly. The only minor gripe is the button placement near the liquid measuring lip.");
        r(p7, u.bob(), 5, "End-grain is genuinely different for knives",
                "My chef's knife has stayed sharper longer since switching from an edge-grain board to this. The juice groove on the large board is deep enough to contain any carving runoff.");
        r(p8, u.carol(), 5, "Fresh basil in 4 weeks — I was amazed",
                "The grow light produces the right spectrum and intensity. I harvested fresh basil at week 4 and it's still going 3 months later with regular trimming. Parsley was similarly prolific.");
        r(p9, u.alice(), 5, "10 minutes to fresh pasta — genuinely",
                "The attachment clicks in cleanly and the motor of my stand mixer handles the dough without straining. The fettuccine die produces a consistent strand width that cooks evenly.");
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private User merchant(String email, String firstName, String lastName) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setPassword(passwordEncoder.encode("Password123!"));
            u.setFirstName(firstName);
            u.setLastName(lastName);
            u.setRole(UserRole.MERCHANT);
            u.setStatus(UserStatus.ACTIVE);
            return userRepository.save(u);
        });
    }

    private Company company(User owner, String name, String industry,
                             String address, String city, String country, String postal,
                             String phone, String email, String website,
                             String description, int foundedYear, int employees) {
        return companyRepository.findByNameAndOwnerId(name, owner.getId()).orElseGet(() -> {
            Company c = new Company();
            c.setOwner(owner);
            c.setName(name);
            c.setIndustry(industry);
            c.setAddress(address);
            c.setCity(city);
            c.setCountry(country);
            c.setPostalCode(postal);
            c.setPhoneNumber(phone);
            c.setEmail(email);
            c.setWebsite(website);
            c.setDescription(description);
            c.setFoundedYear(foundedYear);
            c.setEmployeeCount(employees);
            c.setStatus(CompanyStatus.ACTIVE);
            Company saved = companyRepository.save(c);

            CompanyMembership membership = new CompanyMembership();
            membership.setCompany(saved);
            membership.setUser(owner);
            membership.setRole(CompanyRole.OWNER);
            membership.setStatus(CompanyMembershipStatus.ACTIVE);
            membership.setAcceptedAt(Instant.now());
            membershipRepository.save(membership);

            return saved;
        });
    }

    private void invLoc(Company co, String name, String code) {
        if (locationRepository.existsByCodeAndCompanyId(code, co.getId())) return;
        InventoryLocation loc = new InventoryLocation();
        loc.setCompany(co);
        loc.setName(name);
        loc.setCode(code);
        loc.setAddress(co.getAddress());
        loc.setCity(co.getCity());
        loc.setCountry(co.getCountry());
        loc.setActive(true);
        locationRepository.save(loc);
    }

    private Product prod(Company co, String name, String description, String sku,
                          String price, String category, String brand,
                          int stock, int lowStock) {
        return h.productSingle(co, name, description, sku, price, null,
                category, brand, null, stock, lowStock,
                false, false, false, null, null,
                List.of(), List.of());
    }

    private void r(Product product, User reviewer, int rating, String title, String body) {
        if (reviewRepository.existsByProductIdAndReviewerId(product.getId(), reviewer.getId())) return;
        ProductReview rv = new ProductReview();
        rv.setProduct(product);
        rv.setReviewer(reviewer);
        rv.setRating(rating);
        rv.setTitle(title);
        rv.setBody(body);
        rv.setStatus(ReviewStatus.PUBLISHED);
        reviewRepository.save(rv);
    }
}
