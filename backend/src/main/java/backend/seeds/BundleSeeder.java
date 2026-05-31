package backend.seeds;

import backend.models.core.BundleItem;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.models.enums.ProductStatus;
import backend.repositories.BundleRepository;
import backend.seeds.CompanySeeder.SeededCompanies;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds 5 product bundles per company (25 total).
 *
 * Each bundle groups 3–5 complementary products at a discounted bundle price.
 * compareAtPrice = sum of individual product prices, making the saving visible.
 * Variant is null on all items (any variant eligible).
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class BundleSeeder {

    public record SeededBundles(
            List<ProductBundle> tech,
            List<ProductBundle> style,
            List<ProductBundle> wellness,
            List<ProductBundle> home,
            List<ProductBundle> sport) {}

    private final BundleRepository bundleRepository;

    public SeededBundles seed(SeededCompanies co,
                     List<Product> tech, List<Product> style, List<Product> wellness,
                     List<Product> home, List<Product> sport) {
        return new SeededBundles(
                seedTech(co.tech(), tech),
                seedStyle(co.style(), style),
                seedWellness(co.wellness(), wellness),
                seedHome(co.home(), home),
                seedSport(co.sport(), sport));
    }

    // =========================================================================
    // TechGadgets Co. — 5 bundles
    //
    //  Products by index:
    //    0=Headphones  5=4K Webcam  7=Earbuds  8=Smart Hub  9=Gaming Mouse
    //    12=USB Mic  13=LED Strips  17=Smart Plug  20=Gaming Headset
    //    24=XXL Desk Mat  30=Smart Thermostat  32=Smart LED Bulbs  39=Ring Light
    //    40=Green Screen  46=Mic Arm Kit
    // =========================================================================

    private List<ProductBundle> seedTech(Company co, List<Product> p) {
        List<ProductBundle> existing = bundleRepository.findAllByCompanyId(co.getId());
        if (!existing.isEmpty()) return existing;

        List<ProductBundle> result = new ArrayList<>();
        result.add(bundle(co, "Work From Home Pro",
                "Everything for a professional home office — studio-quality video, clear audio, and an organised desk.",
                "https://placehold.co/800x800/2e4057/ffffff?text=WFH+Pro",
                new BigDecimal("219.99"), new BigDecimal("259.96"),
                items(p, new int[]{5, 12, 46, 24})));

        result.add(bundle(co, "Gaming Lair Setup",
                "The complete gaming desk package — wireless headset, precision mouse, giant desk mat, and LED strip lighting.",
                "https://placehold.co/800x800/1c2833/ffffff?text=Gaming+Lair",
                new BigDecimal("184.99"), new BigDecimal("214.96"),
                items(p, new int[]{20, 9, 24, 13})));

        result.add(bundle(co, "Smart Home Starter Kit",
                "Control, automate, and secure your home — hub, smart plugs, tunable bulbs, and climate control.",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Smart+Home+Kit",
                new BigDecimal("309.99"), new BigDecimal("363.96"),
                items(p, new int[]{8, 17, 32, 30})));

        result.add(bundle(co, "Audiophile Collection",
                "Premium audio from every angle — over-ear ANC headphones, in-ear ANC earbuds, and condenser mic.",
                "https://placehold.co/800x800/1a1a2e/ffffff?text=Audiophile",
                new BigDecimal("279.99"), new BigDecimal("319.97"),
                items(p, new int[]{0, 7, 12})));

        result.add(bundle(co, "Creator Stream Bundle",
                "Go live with confidence — professional ring light, 4K camera, boom arm, and studio backdrop.",
                "https://placehold.co/800x800/1b2631/ffffff?text=Creator+Stream",
                new BigDecimal("229.99"), new BigDecimal("264.96"),
                items(p, new int[]{39, 5, 46, 40})));

        return result;
    }

    // =========================================================================
    // StyleHub — 5 bundles
    //
    //  Products by index:
    //    0=Organic Tee  3=Running Shorts  4=Canvas Sneakers  6=Yoga Leggings
    //    8=Leather Belt  9=Baseball Cap  12=Chino Trousers  14=Athletic Socks
    //    16=Knit Beanie  18=Flannel PJ Set  20=Cashmere Scarf  28=Sports Bra
    //    29=Oxford Shirt  34=Bucket Hat  37=Graphic Tee  42=Leather Loafers
    //    44=Turtleneck  45=Denim Shorts
    // =========================================================================

    private List<ProductBundle> seedStyle(Company co, List<Product> p) {
        List<ProductBundle> existing = bundleRepository.findAllByCompanyId(co.getId());
        if (!existing.isEmpty()) return existing;

        List<ProductBundle> result = new ArrayList<>();
        result.add(bundle(co, "Weekend Casual Set",
                "The effortless weekend uniform — organic tee, tailored chinos, classic sneakers, and a washed cap.",
                "https://placehold.co/800x800/f5cba7/333333?text=Weekend+Casual",
                new BigDecimal("164.99"), new BigDecimal("194.96"),
                items(p, new int[]{0, 12, 4, 9})));

        result.add(bundle(co, "Active Life Bundle",
                "Your complete workout kit — high-waist leggings, supportive sports bra, quick-dry shorts, and arch-support socks.",
                "https://placehold.co/800x800/7d3c98/ffffff?text=Active+Life",
                new BigDecimal("109.99"), new BigDecimal("134.96"),
                items(p, new int[]{6, 28, 3, 14})));

        result.add(bundle(co, "Smart Business Look",
                "Polished from collar to sole — Oxford shirt, stretch chinos, leather belt, and slip-on loafers.",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Smart+Business",
                new BigDecimal("249.99"), new BigDecimal("289.96"),
                items(p, new int[]{29, 12, 8, 42})));

        result.add(bundle(co, "Cosy Home Set",
                "The ultimate stay-in bundle — brushed flannel pyjamas, ribbed turtleneck, merino beanie, and cashmere wrap.",
                "https://placehold.co/800x800/884ea0/ffffff?text=Cosy+Home",
                new BigDecimal("139.99"), new BigDecimal("179.96"),
                items(p, new int[]{18, 44, 16, 20})));

        result.add(bundle(co, "Festival Looks Bundle",
                "Stand out at any outdoor event — vintage graphic tee, high-waist denim shorts, bucket hat, and canvas sneakers.",
                "https://placehold.co/800x800/e9c46a/333333?text=Festival+Look",
                new BigDecimal("139.99"), new BigDecimal("169.96"),
                items(p, new int[]{37, 45, 34, 4})));

        return result;
    }

    // =========================================================================
    // WellnessWorld — 5 bundles
    //
    //  Products by index:
    //    0=Whey Protein  1=Vit C Serum  4=Soy Candle  5=Yoga Mat  8=Diffuser
    //    10=Sleep Gummies  12=Matcha  13=Insulated Tumbler  16=Meditation Cushion
    //    19=Multivitamin  22=Magnesium  25=Pre-Workout  26=BCAA  27=Creatine
    //    32=Collagen Cream  33=HA Serum  35=SPF 50  43=Herbal Sleep Tea  48=Acupressure Mat
    // =========================================================================

    private List<ProductBundle> seedWellness(Company co, List<Product> p) {
        List<ProductBundle> existing = bundleRepository.findAllByCompanyId(co.getId());
        if (!existing.isEmpty()) return existing;

        List<ProductBundle> result = new ArrayList<>();
        result.add(bundle(co, "Morning Routine Starter",
                "Kick-start every day — quality protein, essential vitamins, ceremonial matcha, and an insulated tumbler.",
                "https://placehold.co/800x800/d6eaf8/333333?text=Morning+Routine",
                new BigDecimal("124.99"), new BigDecimal("154.96"),
                items(p, new int[]{0, 19, 12, 13})));

        result.add(bundle(co, "Skincare Ritual Bundle",
                "A complete AM/PM routine — vitamin C serum, firming collagen cream, hyaluronic acid, and daily SPF.",
                "https://placehold.co/800x800/fef9e7/333333?text=Skincare+Ritual",
                new BigDecimal("119.99"), new BigDecimal("144.96"),
                items(p, new int[]{1, 32, 33, 35})));

        result.add(bundle(co, "Recovery & Rest Pack",
                "Wind down and sleep deeper — magnesium glycinate, sleep gummies, calming herbal tea, and acupressure mat.",
                "https://placehold.co/800x800/e8daef/333333?text=Recovery+Rest",
                new BigDecimal("99.99"), new BigDecimal("124.96"),
                items(p, new int[]{22, 10, 43, 48})));

        result.add(bundle(co, "Sports Performance Stack",
                "The science-backed training foundation — whey protein, creatine, pre-workout, and BCAAs.",
                "https://placehold.co/800x800/c8e6c9/333333?text=Performance+Stack",
                new BigDecimal("124.99"), new BigDecimal("149.96"),
                items(p, new int[]{0, 27, 25, 26})));

        result.add(bundle(co, "Home Wellness Sanctuary",
                "Create your restorative space — premium yoga mat, aromatherapy diffuser, meditation cushion, and soy candle.",
                "https://placehold.co/800x800/7dcea0/ffffff?text=Wellness+Sanctuary",
                new BigDecimal("164.99"), new BigDecimal("194.96"),
                items(p, new int[]{5, 8, 16, 4})));

        return result;
    }

    // =========================================================================
    // HomeNest Co. — 5 bundles
    //
    //  Products by index:
    //    3=Smart Doorbell  4=Indoor Camera  5=Smoke Detector  6=Water Leak Sensor
    //    12=Bamboo Cutting Board  13=Cast Iron Skillet  16=Chef's Knife  17=Kitchen Scale
    //    20=Artisan Soy Candle  21=Aroma Diffuser  23=Bamboo Sheets  24=Weighted Blanket
    //    25=Memory Foam Pillow  31=Silk Eye Mask  40=Ceramic Vases  42=Throw Pillow Covers
    //    43=LED Floor Lamp  45=Chunky Knit Throw  46=Plant Stand  49=Marble Tray
    // =========================================================================

    private List<ProductBundle> seedHome(Company co, List<Product> p) {
        List<ProductBundle> existing = bundleRepository.findAllByCompanyId(co.getId());
        if (!existing.isEmpty()) return existing;

        List<ProductBundle> result = new ArrayList<>();
        result.add(bundle(co, "Smart Home Security Pack",
                "Full-coverage protection — 2K doorbell, pan-tilt camera, smoke & CO alarm, and water leak sensor.",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Security+Pack",
                new BigDecimal("219.99"), new BigDecimal("259.96"),
                items(p, new int[]{3, 4, 5, 6})));

        result.add(bundle(co, "Kitchen Starter Kit",
                "Equip your kitchen properly — German steel chef's knife, bamboo cutting board, cast iron skillet, and digital scale.",
                "https://placehold.co/800x800/bdc3c7/333333?text=Kitchen+Starter",
                new BigDecimal("164.99"), new BigDecimal("194.96"),
                items(p, new int[]{16, 12, 13, 17})));

        result.add(bundle(co, "Bedroom Dream Setup",
                "Sleep like you deserve — silky bamboo sheets, memory foam pillows, weighted blanket, and mulberry silk eye mask.",
                "https://placehold.co/800x800/f2f3f4/333333?text=Bedroom+Dream",
                new BigDecimal("224.99"), new BigDecimal("264.96"),
                items(p, new int[]{23, 25, 24, 31})));

        result.add(bundle(co, "Home Fragrance Collection",
                "Scent your space beautifully — artisan soy candle trio, ultrasonic diffuser, ceramic vases, and marble tray.",
                "https://placehold.co/800x800/fdebd0/333333?text=Fragrance+Collection",
                new BigDecimal("129.99"), new BigDecimal("154.96"),
                items(p, new int[]{20, 21, 40, 49})));

        result.add(bundle(co, "Living Room Refresh",
                "Transform your living room — arc floor lamp, chunky knit throw, linen pillow covers, and bamboo plant stand.",
                "https://placehold.co/800x800/f5f5f5/333333?text=Living+Room+Refresh",
                new BigDecimal("209.99"), new BigDecimal("249.96"),
                items(p, new int[]{43, 45, 42, 46})));

        return result;
    }

    // =========================================================================
    // SportZone — 5 bundles
    //
    //  Products by index:
    //    0=Running Shorts  1=Training Leggings  4=Arch Socks  5=Workout Tank
    //    7=Training Joggers  9=Running Cap  10=Resistance Bands  11=Pro Yoga Mat
    //    12=Foam Roller  18=Ab Wheel  22=Whey Isolate  23=BCAA  25=Creatine
    //    28=Protein Bars  38=Climbing Chalk  41=Water Bottle  42=Phone Armband
    //    43=Waterproof Earbuds  44=Gym Gloves  45=Powerlifting Belt  48=Wrist Straps  49=Lacrosse Balls
    // =========================================================================

    private List<ProductBundle> seedSport(Company co, List<Product> p) {
        List<ProductBundle> existing = bundleRepository.findAllByCompanyId(co.getId());
        if (!existing.isEmpty()) return existing;

        List<ProductBundle> result = new ArrayList<>();
        result.add(bundle(co, "Runner's Complete Kit",
                "Hit the road fully equipped — elite shorts, pro leggings, perforated cap, phone armband, and arch-support socks.",
                "https://placehold.co/800x800/1abc9c/ffffff?text=Runners+Kit",
                new BigDecimal("139.99"), new BigDecimal("166.95"),
                items(p, new int[]{0, 1, 9, 42, 4})));

        result.add(bundle(co, "Home Gym Starter",
                "Build your training base at home — resistance bands, professional yoga mat, foam roller, and ab wheel.",
                "https://placehold.co/800x800/e74c3c/ffffff?text=Home+Gym+Starter",
                new BigDecimal("134.99"), new BigDecimal("159.96"),
                items(p, new int[]{10, 11, 12, 18})));

        result.add(bundle(co, "Nutrition Fundamentals",
                "The four pillars of sports nutrition — CFM whey isolate, Creapure creatine, BCAA electrolytes, and protein bars.",
                "https://placehold.co/800x800/d6eaf8/333333?text=Nutrition+Fund",
                new BigDecimal("134.99"), new BigDecimal("159.96"),
                items(p, new int[]{22, 25, 23, 28})));

        result.add(bundle(co, "Powerlifting Pack",
                "Every accessory a powerlifter needs — velcro belt, wrist straps, leather gloves, chalk, and lacrosse balls.",
                "https://placehold.co/800x800/1c2833/ffffff?text=Powerlifting+Pack",
                new BigDecimal("94.99"), new BigDecimal("117.95"),
                items(p, new int[]{45, 48, 44, 38, 49})));

        result.add(bundle(co, "Training Day Bundle",
                "Gear up for any session — mesh workout tank, tapered joggers, waterproof earbuds, and insulated water bottle.",
                "https://placehold.co/800x800/212f3c/ffffff?text=Training+Day",
                new BigDecimal("159.99"), new BigDecimal("187.96"),
                items(p, new int[]{5, 7, 43, 41})));

        return result;
    }

    // =========================================================================
    // Entity builders
    // =========================================================================

    private ProductBundle bundle(Company co, String name, String description,
                        String thumbnailUrl, BigDecimal price, BigDecimal compareAtPrice,
                        BundleItem[] bundleItems) {
        ProductBundle b = new ProductBundle();
        b.setCompany(co);
        b.setName(name);
        b.setDescription(description);
        b.setThumbnailUrl(thumbnailUrl);
        b.setPrice(price);
        b.setCompareAtPrice(compareAtPrice);
        b.setCurrency("USD");
        b.setStatus(ProductStatus.ACTIVE);
        b.setListed(true);
        for (BundleItem bi : bundleItems) {
            bi.setBundle(b);
            b.getItems().add(bi);
        }
        return bundleRepository.save(b);
    }

    private BundleItem[] items(List<Product> products, int[] indices) {
        BundleItem[] result = new BundleItem[indices.length];
        for (int i = 0; i < indices.length; i++) {
            BundleItem bi = new BundleItem();
            bi.setProduct(products.get(indices[i]));
            bi.setVariant(null);
            bi.setQuantity(1);
            bi.setDisplayOrder(i);
            result[i] = bi;
        }
        return result;
    }
}
