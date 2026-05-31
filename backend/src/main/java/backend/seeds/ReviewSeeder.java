package backend.seeds;

import backend.models.core.Product;
import backend.models.core.ProductReview;
import backend.models.core.User;
import backend.models.enums.ReviewStatus;
import backend.repositories.ProductReviewRepository;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds 20 reviews per company (100 total). Products marked with multiple
 * reviewer calls intentionally have several reviews to simulate real purchase
 * history. The unique (product, reviewer) constraint is guarded idempotently.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class ReviewSeeder {

    private final ProductReviewRepository productReviewRepository;

    public void seed(List<Product> tech, List<Product> style, List<Product> wellness,
                     List<Product> home, List<Product> sport, SeededUsers u) {
        seedTech(tech, u);
        seedStyle(style, u);
        seedWellness(wellness, u);
        seedHome(home, u);
        seedSport(sport, u);
    }

    // ── TechGadgets Co. — 20 reviews ─────────────────────────────────────────

    private void seedTech(List<Product> p, SeededUsers u) {
        // [0] Wireless Noise-Cancelling Headphones — 3 reviews
        r(p, 0, u.alice(), 5, "Best headphones I've ever owned",
                "The ANC is incredible — blocks out my entire open-plan office. Sound is warm and detailed with deep bass that doesn't bleed into the mids. Battery life is exactly as advertised.");
        r(p, 0, u.bob(), 4, "Great, but the clamping force takes getting used to",
                "Sound quality is top-notch and the ANC works brilliantly on flights. Clamping force was tight at first but loosened after a week. Would still recommend wholeheartedly.");
        r(p, 0, u.carol(), 5, "My daily driver for WFH — couldn't be happier",
                "Using these 8+ hours a day and my ears don't fatigue. The USB-C connection is a bonus I didn't expect to use as much as I do. Absolutely worth the price.");

        // [1] Smart Watch Series X — 2 reviews
        r(p, 1, u.alice(), 5, "Life-changing smartwatch",
                "The health features alone are worth every penny. ECG and SpO2 have been spot-on compared to my doctor's equipment. 18-day battery is no exaggeration.");
        r(p, 1, u.carol(), 4, "Excellent watch, minor software quirks",
                "Hardware is beautiful and battery lasts two full weeks easily. A couple of minor bugs in the sleep tracking app but nothing that ruins the overall experience.");

        // [2] Portable Bluetooth Speaker — 2 reviews
        r(p, 2, u.bob(), 5, "Took it camping — survived a downpour",
                "IP67 is no joke. Dropped it in the lake by accident and it kept playing. The 360° sound is genuinely impressive for the size. Bought one for my sister too.");
        r(p, 2, u.alice(), 4, "Great sound, battery accurate, gets loud",
                "Bass is punchy for a speaker this size. Hits 24 hours easily at moderate volume. The power-bank feature saved my phone on a long hike. Only gripe is no EQ in the app.");

        // [4] Mechanical Keyboard TKL — 2 reviews
        r(p, 4, u.carol(), 5, "Best keyboard I've ever typed on",
                "Went with brown switches and the typing feel is perfect — tactile without being loud enough to annoy co-workers on calls. Build quality feels premium at every keystroke.");
        r(p, 4, u.bob(), 4, "Solid TKL, hot-swap is a game-changer",
                "Hot-swap sockets mean I can try different switches without soldering. Went through three switch types before landing on reds. RGB lighting is crisp and configurable.");

        // [5] 4K Webcam Pro — 1 review
        r(p, 5, u.alice(), 5, "Finally looks professional on video calls",
                "Before this I was using my laptop webcam and looked like a potato. The auto-framing AI keeps me centered when I move around. Dual mics are crystal clear.");

        // [6] Wireless Charging Pad — 1 review
        r(p, 6, u.carol(), 4, "Slim, fast, and reliable",
                "15W Qi2 charges my phone noticeably faster than my old 10W pad. The slim profile means it's always on my desk without being in the way. USB-C cable is a nice touch.");

        // [7] Noise-Cancelling Earbuds — 1 review
        r(p, 7, u.bob(), 5, "Small package, massive performance",
                "These punch well above their price bracket. ANC rivals earbuds twice the cost. IPX5 means I wear them running in the rain without a second thought. 32h total battery is excellent.");

        // [8] Smart Home Hub — 1 review
        r(p, 8, u.alice(), 4, "Finally unified all my smart home devices",
                "I had devices on Zigbee, Z-Wave, and Wi-Fi that wouldn't talk to each other. This hub brought everything under one roof. Local processing means automations still work if the internet goes down.");

        // [9] LED Gaming Mouse — 1 review
        r(p, 9, u.carol(), 5, "Perfect sensor, great weight",
                "16K DPI sensor tracks perfectly on any surface. The 95g weight is ideal for long sessions — no wrist fatigue. Seven buttons is enough without feeling cluttered.");

        // [10] Laptop Stand Adjustable — 1 review
        r(p, 10, u.bob(), 4, "Transformed my desk setup ergonomically",
                "Had neck pain from looking down at my laptop for years. One week with this stand and the difference was immediate. Folds flat and fits in my laptop bag for the commute.");

        // [11] Portable SSD 1TB — 1 review
        r(p, 11, u.alice(), 5, "Blazing fast, tiny footprint",
                "1050 MB/s read is real — I timed it. IP55 rating gives peace of mind on location shoots. The USB-C cable is high quality and doesn't rattle loose. Perfect travel drive.");

        // [18] Mesh WiFi Router System — 1 review
        r(p, 18, u.carol(), 5, "Eliminated every dead zone in my home",
                "3-node kit covers my 3-storey house end to end. Wi-Fi 6E is a genuine upgrade — 4K streaming and gaming simultaneously with zero buffering. Setup via the app took under 10 minutes.");

        // [20] Wireless Gaming Headset 7.1 — 1 review
        r(p, 20, u.bob(), 4, "Virtual surround is surprisingly convincing",
                "Positional audio in games is noticeably better with the 7.1 surround on. Mic is clear with the noise-cancellation preventing keyboard sound bleeding through. 30-hour battery lasts a full weekend.");

        // [27] Portable Monitor 15.6\" — 1 review
        r(p, 27, u.alice(), 4, "Great second screen for travel",
                "Single USB-C cable from my laptop powers and drives it. IPS panel has good colour for the price. The kickstand case is sturdy. At 800g it's heavier than expected but still portable.");

        // [45] VR Headset Standalone — 1 review
        r(p, 45, u.carol(), 5, "The most fun I've had with any gadget",
                "Inside-out tracking is seamless — no external sensors needed. The 4K display at 90Hz is sharp enough that I forget I'm in a headset within minutes. Three hours felt like twenty minutes.");
    }

    // ── StyleHub — 20 reviews ─────────────────────────────────────────────────

    private void seedStyle(List<Product> p, SeededUsers u) {
        // [0] Premium Organic Cotton T-Shirt — 3 reviews
        r(p, 0, u.alice(), 5, "Softest tee I've ever worn",
                "The organic cotton is incredibly soft right out of the bag. Fit is true to size, washes repeatedly without shrinking, and still looks new after months of wear.");
        r(p, 0, u.bob(), 5, "Worth every cent — bought three",
                "I ordered three different colours. The fabric quality is noticeably better than anything at this price point. The relaxed fit works for both the office and weekends. Highly recommend.");
        r(p, 0, u.carol(), 4, "Great quality, size runs slightly generous",
                "Fabric is beautifully soft and the GOTS certification matters to me. I'd go down a size if you prefer a fitted look. Washes very well and the colour hasn't faded.");

        // [1] Slim-Fit Stretch Denim Jeans — 2 reviews
        r(p, 1, u.alice(), 4, "Comfortable all day — genuinely stretchy",
                "The 2% elastane makes a real difference for someone who cycles and needs to be able to move. Cut is clean, sits mid-rise as described. Excellent quality for the price.");
        r(p, 1, u.carol(), 3, "Nice jeans, but the wash faded quickly",
                "The fit and stretch are great. However, the medium wash faded noticeably faster than expected after about fifteen washes. Would recommend washing inside out in cold water from the start.");

        // [2] Heavyweight Zip Hoodie — 2 reviews
        r(p, 2, u.bob(), 5, "My new favourite piece of clothing",
                "I've been wearing this almost every day since it arrived. The French terry is plush, the colours are rich, and it's held its shape perfectly after multiple washes. The YKK zip feels premium.");
        r(p, 2, u.alice(), 5, "Heavyweight and genuinely warm",
                "500gsm is no joke — this is a proper heavyweight hoodie that doesn't need a jacket underneath until it's really cold. The brushed interior is soft without being pill-prone. Superb quality.");

        // [3] Quick-Dry Running Shorts — 2 reviews
        r(p, 3, u.carol(), 5, "Best running shorts I've owned",
                "The built-in liner is comfortable without chafing on long runs. Back zip pocket fits my phone perfectly. Dry time after washing or sweating is impressively fast. UPF 30 is a bonus.");
        r(p, 3, u.alice(), 4, "Great for running, a little sheer in bright light",
                "Comfortable, dry quickly, and the reflective details are a safety win for early morning runs. The fabric is slightly sheer in direct sunlight — worth knowing if that bothers you.");

        // [4] Classic Canvas Sneakers — 1 review
        r(p, 4, u.bob(), 4, "Classic look, surprisingly comfortable",
                "The Ortholite insole makes a genuine difference versus cheap canvas shoes. They look great with jeans or chinos. I've been wearing mine almost daily for three months with no signs of wear.");

        // [5] Linen Button-Down Shirt — 1 review
        r(p, 5, u.carol(), 4, "Perfect summer shirt — breathable and cool",
                "The camp collar is the right call for a casual linen shirt. It drapes beautifully and wrinkles very little for linen. Wears great in warm weather without feeling clingy.");

        // [6] Yoga Leggings — 2 reviews
        r(p, 6, u.bob(), 5, "My go-to for yoga and everything else",
                "These leggings are genuinely squat-proof — tested thoroughly in class. The high waist stays put through every pose. The fabric is that buttery-soft texture that's hard to find at this price.");
        r(p, 6, u.carol(), 5, "The hidden pocket is genius",
                "Finally leggings with a waistband pocket big enough for my phone. The 4-way stretch is real and they don't go see-through. I now own four pairs and am considering a fifth.");

        // [7] Wool Blend Coat — 1 review
        r(p, 7, u.alice(), 5, "Luxury feel, surprisingly affordable",
                "The Italian wool blend drapes beautifully and the satin lining makes it easy to put on over anything. The hidden button closure looks incredibly clean. Gets compliments every time I wear it.");

        // [8] Leather Belt Classic — 1 review
        r(p, 8, u.bob(), 5, "Already developing a beautiful patina",
                "Three months in and this belt already looks better than it did on day one. The vegetable-tanned leather softens and takes on character with wear. The solid brass buckle feels indestructible.");

        // [9] Baseball Cap — 1 review
        r(p, 9, u.carol(), 4, "Lightweight and adjustable — fits perfectly",
                "The washed cotton gives it a broken-in feel from day one. The strapback fits my head exactly right with room to adjust. Low-profile and goes with almost everything in my wardrobe.");

        // [10] Crew Neck Sweatshirt — 1 review
        r(p, 10, u.alice(), 4, "Garment-dyed colour is rich and unique",
                "Each piece looks slightly different because of the garment-dye process — mine has a lovely variation in the fabric. 380gsm is genuinely heavy and cosy. The slightly oversized fit is perfect.");

        // [16] Knit Beanie — 1 review
        r(p, 16, u.bob(), 5, "Best beanie I've ever bought",
                "Merino wool is warm without being itchy — I can wear this right on my skin. The turn-up cuff sits perfectly without being too short or too tall. The Rust colour is exactly as pictured.");

        // [22] Chelsea Boots — 1 review
        r(p, 22, u.carol(), 4, "Stylish and surprisingly practical",
                "The block heel makes these wearable all day without the usual ankle-boot discomfort. Suede quality is excellent. The elastic panels are firm enough to hold the boot securely without restricting.");

        // [26] Satin Bomber Jacket — 1 review
        r(p, 26, u.alice(), 4, "Surprisingly versatile — dressed up or down",
                "Wore this to a gallery opening over a black turtleneck and got three compliments. Also works over a hoodie for a more casual look. The printed lining is a lovely detail you see when you move.");
    }

    // ── WellnessWorld — 20 reviews ────────────────────────────────────────────

    private void seedWellness(List<Product> p, SeededUsers u) {
        // [0] Whey Protein Powder — 3 reviews
        r(p, 0, u.alice(), 5, "Clean and genuinely effective",
                "Love that there are no artificial sweeteners. Mixes cleanly with just a shaker cup and sits well in my stomach even in the morning. The macros are excellent at 27g per serving.");
        r(p, 0, u.bob(), 5, "Best-tasting protein powder I've tried",
                "The chocolate flavour is genuinely delicious — tastes like a milkshake, not chalky at all. Mixes perfectly with oat milk and keeps me full until lunch. Re-ordering the 5lb.");
        r(p, 0, u.carol(), 4, "High quality, vanilla slightly sweet",
                "Protein quality is clearly excellent — I've noticed real recovery improvements. The vanilla is a touch sweeter than I'd like but still enjoyable. Love that it's Informed Sport certified.");

        // [1] Vitamin C Brightening Serum — 2 reviews
        r(p, 1, u.bob(), 5, "Visible results in three weeks",
                "I was sceptical about vitamin C serums after trying cheap ones that smelled of hot dogs. This one is different — the amber glass packaging keeps it fresh and I've seen genuine brightening.");
        r(p, 1, u.carol(), 4, "Great serum, apply at night to be safe",
                "The 15% L-ascorbic acid is potent — my skin is noticeably more even after a month. I apply at night to avoid any photosensitivity. The ferulic acid and vitamin E combo is the right formulation.");

        // [2] HEPA Air Purifier 360° — 1 review
        r(p, 2, u.bob(), 5, "Air quality visibly improved — allergy symptoms down",
                "I have dust allergies and was sceptical but within a week I stopped waking up with a stuffy nose. The auto mode intelligently ramps up when I cook and quiets at night. Excellent purchase.");

        // [3] Pour-Over Coffee Maker Set — 1 review
        r(p, 3, u.carol(), 4, "Makes exceptional coffee — steep learning curve",
                "The coffee quality once you nail the technique is miles ahead of a drip machine. The gooseneck kettle gives perfect pour control. The bamboo server keeps coffee warm for longer than expected.");

        // [4] Soy Wax Candle Collection — 2 reviews
        r(p, 4, u.alice(), 5, "Lavender & Cedar is my favourite scent ever",
                "The throw on these candles is impressive — one candle fills my entire living room. Soy burns cleaner than paraffin and the cotton wick produces no black smoke. Burn time is accurate.");
        r(p, 4, u.bob(), 4, "Great candles, Eucalyptus is very strong",
                "The Citrus & Sage is perfectly balanced. I found the Eucalyptus a bit overpowering in a small room but ideal for a bathroom or large space. Cotton wicks burn very cleanly.");

        // [5] Yoga Mat Premium — 2 reviews
        r(p, 5, u.carol(), 5, "Superior grip, zero slip — even in hot yoga",
                "I've tried many yoga mats and this is by far the best. The microfiber suede top grips perfectly wet or dry. The alignment guides are super helpful for beginners. Worth every penny.");
        r(p, 5, u.alice(), 5, "This mat transformed my practice",
                "The grip is genuinely unlike anything I've used before. The natural rubber base is dense without being too heavy to carry. After three months of daily use it shows no signs of deterioration.");

        // [10] Sleep Support Gummies — 2 reviews
        r(p, 10, u.alice(), 5, "Falling asleep in under 20 minutes now",
                "I've struggled with sleep for years. These gummies don't knock me out — they just calm my mind enough to drift off naturally. The strawberry flavour is pleasant and not artificial tasting.");
        r(p, 10, u.bob(), 4, "Genuinely helps — don't take more than 2",
                "Two gummies about 45 minutes before bed has been my routine for six weeks. Sleep quality on my tracker has improved noticeably. I find taking more than the recommended dose makes me groggy.");

        // [25] Pre-Workout Energy Formula — 2 reviews
        r(p, 25, u.carol(), 5, "Best pre-workout I've ever used",
                "200mg caffeine is the sweet spot for me — enough energy without the anxious jitters some pre-workouts give. The tropical punch flavour actually tastes good. 30-minute onset is consistent.");
        r(p, 25, u.alice(), 4, "Effective formula, mixes well",
                "The citrulline and beta-alanine combo delivers a real pump. I do get tingles from the beta-alanine for about 20 minutes which is normal but worth knowing. Performance in the gym is noticeably better.");

        // [2] Air Purifier — already done above (index 2, bob, 5★)

        // [6] Collagen Peptides — 1 review
        r(p, 6, u.bob(), 4, "Dissolves perfectly, no taste or texture",
                "The unflavoured version is genuinely tasteless — I add it to coffee or smoothies without noticing it. After two months my skin feels more elastic and my joints ache less during runs.");

        // [22] Magnesium Glycinate — 1 review
        r(p, 22, u.carol(), 5, "Better sleep and no more morning cramps",
                "I take this before bed and the difference in sleep quality was obvious within a week. Glycinate is gentler on the stomach than oxide forms — no digestive issues at all. Repurchasing.");

        // [27] Creatine Monohydrate — 1 review
        r(p, 27, u.alice(), 4, "Creapure quality is worth the premium",
                "Creapure certification matters — this mixes completely clear and has no taste whatsoever. Gym performance in the 3–8 rep range has measurably improved after six weeks of loading.");

        // [33] Hyaluronic Acid Serum — 1 review
        r(p, 33, u.bob(), 5, "Plumpest my skin has looked in years",
                "The multi-weight HA reaches different skin layers. My face looks visibly more hydrated an hour after application. The niacinamide addition is a smart pairing. Very lightweight texture.");

        // [43] Herbal Sleep Tea — 1 review
        r(p, 43, u.carol(), 4, "A calming ritual before bed",
                "I use this as part of a wind-down routine — no screens, this tea, and a book. The chamomile and passionflower combination works gently. Taste is earthy and floral without being unpleasant.");

    }

    // ── HomeNest Co. — 20 reviews ─────────────────────────────────────────────

    private void seedHome(List<Product> p, SeededUsers u) {
        // [0] Smart LED Bulb E26 6-Pack — 3 reviews
        r(p, 0, u.alice(), 5, "Replaced every bulb in my house",
                "HomeKit support is seamless. The 6-pack made the whole house smart for under £50. Colour accuracy in the warm range is excellent and they've been rock-solid stable on Wi-Fi for four months.");
        r(p, 0, u.bob(), 4, "Good brightness, setup was simple",
                "The app paired in under two minutes and the bulbs haven't dropped connection once. 800lm is genuinely equivalent to a 60W incandescent. 12-pack value is even better if you have a big home.");
        r(p, 0, u.carol(), 5, "Automations work flawlessly with Alexa",
                "Sunset trigger, morning gradual wake-up, and movie scene — all set up in minutes. The colour range from warm amber to cool daylight is exactly what I needed. No hub, no hassle.");

        // [2] Smart Thermostat Touchscreen — 2 reviews
        r(p, 2, u.carol(), 4, "Heating bills noticeably lower after one month",
                "The auto-away feature alone has saved money. The 7-day scheduling is intuitive on the touchscreen. Installation took 40 minutes following the app guide. Compatible with my older combi boiler.");
        r(p, 2, u.alice(), 4, "Beautiful hardware, solid app",
                "Satin nickel finish looks premium and fits the hallway aesthetic. The app shows clear energy usage graphs. My only note is that the geo-fencing away mode occasionally triggers too early.");

        // [10] Ceramic Pour-Over Coffee Set — 2 reviews
        r(p, 10, u.alice(), 5, "Best coffee I've ever made at home",
                "The ceramic dripper holds heat better than plastic alternatives. The gooseneck kettle with the built-in thermometer takes all the guesswork out. Makes a genuinely café-quality cup every time.");
        r(p, 10, u.bob(), 5, "A wonderful gift — for yourself",
                "I bought this on a whim and it genuinely changed my morning routine. The ritual of pour-over coffee is meditative and the result is far better than any machine I've owned. Stunning to look at too.");

        // [16] German Steel Chef's Knife — 2 reviews
        r(p, 16, u.alice(), 5, "An absolute joy to cook with",
                "The 15° edge cuts through everything effortlessly — tomatoes, meat, herbs. The pakkawood handle is perfectly balanced and comfortable for long prep sessions. It arrived razor sharp.");
        r(p, 16, u.bob(), 4, "Exceptional quality, needs regular honing",
                "The X50CrMoV15 steel holds an edge very well with regular honing. It's noticeably sharper than my previous knives out of the box. A good honing steel is essential — buy one alongside it.");

        // [20] Artisan Soy Candle Trio — 2 reviews
        r(p, 20, u.bob(), 5, "Warm Woods set is exceptional",
                "The Cedar and Patchouli combination fills the room without being overwhelming. The amber glass jars look beautiful on a coffee table. 50-hour burn time is accurate — I've been burning them for weeks.");
        r(p, 20, u.carol(), 5, "Fresh Home set — perfect for the kitchen",
                "The Green Tea and Linen combination is light and clean — exactly what I wanted for the kitchen. No black soot from the cotton wick at all. This is the best-smelling candle I've ever bought.");

        // [3] Smart Video Doorbell — 1 review
        r(p, 3, u.carol(), 5, "Package detection actually works reliably",
                "I've been burned by missed deliveries for years. The 180° wide-angle catches every angle of my porch and the package detection alert is impressively accurate. 60-day local storage is generous.");

        // [11] Borosilicate French Press — 1 review
        r(p, 11, u.alice(), 4, "Classic design, excellent coffee",
                "The 4-layer stainless filter eliminates grit completely — a common issue with cheaper presses. Borosilicate glass handles boiling water without any stress. The 1L size is perfect for two people.");

        // [22] Organic Cotton Duvet Cover — 1 review
        r(p, 22, u.bob(), 5, "Coolest I've slept in years",
                "The percale weave is crisp and cool — a complete contrast to my previous microfibre cover. The internal corner ties keep the duvet in place. GOTS certification was important to us and it delivers.");

        // [23] Bamboo Lyocell Sheet Set — 1 review
        r(p, 23, u.alice(), 5, "Silkiest sheets I've ever slept on",
                "The bamboo lyocell is noticeably cooler and softer than cotton. My partner who usually overheats at night is sleeping comfortably. The sage colour is exactly as pictured. Deep pockets fit my 35cm mattress.");

        // [24] Weighted Blanket — 1 review
        r(p, 24, u.carol(), 4, "Genuinely reduces my anxiety before sleep",
                "The 15lb is the right weight for my frame. The deep pressure stimulation effect is real — I fall asleep faster and wake up less often. The cover is breathable enough that I don't overheat.");

        // [35] Rotating Spice Rack — 1 review
        r(p, 35, u.bob(), 4, "Transformed the most chaotic kitchen drawer",
                "All 24 jars fit on the counter and the lazy susan base means everything is accessible without moving anything. The chalkboard labels look clean and professional. Very satisfying to organise.");

        // [40] Ceramic Bud Vase Set — 1 review
        r(p, 40, u.carol(), 5, "Perfect for dried pampas and eucalyptus",
                "The three heights create a beautiful layered arrangement. The matte glaze finish is exactly the neutral aesthetic I was going for. Very sturdy — no wobbling even with tall dried stems.");

        // [43] LED Floor Lamp — 1 review
        r(p, 43, u.alice(), 4, "Adds warmth to any room — and charges my phone",
                "The arc design is both stylish and practical. The USB-C port in the base keeps my phone topped up without a separate charger. Stepless dimmer means I can always find the perfect brightness.");

        // [45] Chunky Knit Throw Blanket — 1 review
        r(p, 45, u.bob(), 4, "Centrepiece of my living room now",
                "The cream colour is exactly as photographed. The chunky loops look incredible on a dark sofa. It's not as warm as wool but makes up for it in aesthetics. Hand wash is fine — it came out perfectly.");
    }

    // ── SportZone — 20 reviews ────────────────────────────────────────────────

    private void seedSport(List<Product> p, SeededUsers u) {
        // [0] Elite Running Shorts — 3 reviews
        r(p, 0, u.alice(), 5, "My marathon race-day shorts",
                "Wore these for my last three long runs and race day. Zero chafing over 26.2 miles — the liner is perfectly placed. The back zip pocket fits my phone and gels without bouncing. Highly recommend.");
        r(p, 0, u.bob(), 4, "Great, waistband is slightly snug",
                "The fabric is lightweight and dries within 20 minutes of a hard run. The reflective details are genuinely useful at 6am. Waistband could be a touch looser for my build — would size up next time.");
        r(p, 0, u.carol(), 5, "Best running shorts for hot weather",
                "Wore these during a summer half marathon in 28°C heat. The quick-dry fabric kept me comfortable throughout. UPF 30 is a bonus for exposed trail runs. I own three pairs now.");

        // [1] Pro 7/8 Training Leggings — 2 reviews
        r(p, 1, u.alice(), 5, "Squat-proof claim is legitimate",
                "I tested thoroughly before trusting these in a class setting and they passed. The high waist sits right at my natural waist without rolling. The 7/8 length flatters and is practical for any workout.");
        r(p, 1, u.carol(), 5, "Replaced every legging I own",
                "The nylon/spandex blend is more durable than polyester options I've tried. After six months and weekly washes they look brand new. The side pocket is deep enough for my large-screen phone.");

        // [2] High-Impact Sports Bra — 1 review
        r(p, 2, u.carol(), 5, "Finally — high impact support that's comfortable",
                "I have a larger chest and most sports bras offer support or comfort, not both. This one delivers both. The adjustable back closure means I can get the compression exactly right. Wore it for a 10K.");

        // [3] Pro Compression Shorts — 1 review
        r(p, 3, u.bob(), 4, "Reduced my DOMS noticeably",
                "The graduated compression is real — my quads recover faster after heavy leg days. The 9\" inseam is long enough to prevent chafing on the inner thigh during long sessions. Flat-lock seams are invisible.");

        // [4] Arch Support Athletic Socks — 1 review
        r(p, 4, u.alice(), 5, "The best socks I've ever worn for running",
                "The merino wool blend is a revelation — warm in winter, cool in summer, and odour-resistant after long sessions. The Y-heel fits perfectly and doesn't slip inside my shoes. Buying another 6-pack.");

        // [10] Resistance Band Pro Set — 1 review
        r(p, 10, u.bob(), 5, "Replaced half my gym equipment",
                "The 6-band range from 5 to 50lbs covers every exercise I need. The padded handles are far more comfortable than the rubber loops on cheaper sets. Works brilliantly for upper body and leg days at home.");

        // [11] Pro Yoga Mat — 2 reviews
        r(p, 11, u.carol(), 5, "Alignment guides changed my practice",
                "The printed alignment lines have genuinely improved my positioning — my instructor commented without me saying anything. TPE feels substantial underfoot without the rubber smell of natural mats.");
        r(p, 11, u.bob(), 4, "Great mat, colour is bolder than pictured",
                "The Teal/Lime is more vivid in person than the product photos suggest — which I actually prefer. Grip is excellent even in sweaty sessions. The carry strap is sturdy and easy to sling over a shoulder.");

        // [13] Adjustable Dumbbell Set — 1 review
        r(p, 13, u.alice(), 5, "Most space-efficient gym investment I've made",
                "I replaced 15 pairs of dumbbells with this single set and got half my garage back. The dial mechanism clicks into each weight without any wobble or play. Build quality feels commercial grade.");

        // [22] Whey Isolate Protein — 2 reviews
        r(p, 22, u.bob(), 5, "30g protein, no bloating — this is the one",
                "I'm lactose-sensitive and CFM whey isolate is the first protein that doesn't give me issues. 30g per serving with less than 1g lactose is exactly what I needed. Double Chocolate is genuinely good.");
        r(p, 22, u.alice(), 5, "Fast absorption, clean ingredients",
                "I can feel the difference in recovery time when I take this within 30 minutes post-workout. The ingredient list is short and clean — no proprietary blends or unnecessary fillers. Vanilla Bean is delicious.");

        // [25] Creatine Monohydrate Micronised — 1 review
        r(p, 25, u.carol(), 4, "Creapure purity — mixes better than any I've tried",
                "The micronised Creapure mixes completely clear even in cold water. After 8 weeks of consistent use my strength in the 3–6 rep range has increased measurably. Unflavoured is the only way to go.");

        // [28] Protein Bar Box — 1 review
        r(p, 28, u.bob(), 5, "20g protein and they actually taste good",
                "Most protein bars taste medicinal or have that weird protein aftertaste. These don't. The Dark Chocolate coating is proper chocolate and the texture is chewy without being dense. My new post-workout staple.");

        // [31] GPS Running Watch — 1 review
        r(p, 31, u.alice(), 5, "The only GPS watch you need",
                "Multi-GNSS lock takes under 10 seconds and accuracy on trails is superb. The barometric altimeter gives reliable elevation data. 20 hours of GPS battery is enough for a mountain ultra. Exceptional value.");

        // [36] Cork Yoga Blocks — 1 review
        r(p, 36, u.carol(), 5, "Cork is the only material for yoga blocks",
                "EVA foam blocks slip when my hands are sweaty. Cork doesn't. These are the right density — firm enough for support, not so hard they're uncomfortable under my hand. Worth the premium over foam.");

        // [40] Gym Duffle Bag — 2 reviews
        r(p, 40, u.alice(), 4, "Every compartment is in the right place",
                "The wet/dry compartment is the feature I didn't know I needed until I had it. The laptop sleeve fits my 15\" MacBook. The luggage strap loops onto my carry-on perfectly. Matte black looks great.");
        r(p, 40, u.carol(), 4, "Roomy, durable, functional design",
                "45L is bigger than it looks — fits all my kit plus a change of clothes and shoes with room to spare. The ripstop nylon already has a few marks from the gym floor but no tears whatsoever after 4 months.");

    }

    // ── Private helper ────────────────────────────────────────────────────────

    private void r(List<Product> products, int idx, User reviewer, int rating, String title, String body) {
        Product product = products.get(idx);
        if (productReviewRepository.existsByProductIdAndReviewerId(product.getId(), reviewer.getId())) return;
        ProductReview rv = new ProductReview();
        rv.setProduct(product);
        rv.setReviewer(reviewer);
        rv.setRating(rating);
        rv.setTitle(title);
        rv.setBody(body);
        rv.setStatus(ReviewStatus.PUBLISHED);
        productReviewRepository.save(rv);
    }
}
