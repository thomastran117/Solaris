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
public class TechGadgetsProductSeeder {

    private final ProductSeedHelper h;

    public List<Product> seed(Company co) {
        List<Product> list = new ArrayList<>();

        list.add(h.product(co, "Wireless Noise-Cancelling Headphones",
                "Studio-quality sound with 40-hour battery life and adaptive ANC. Premium over-ear design with memory foam cushions.",
                "TECH-WNC-HDR-001", "149.99", "199.99", "Electronics", "SoundCore",
                "https://placehold.co/800x800/1a1a2e/ffffff?text=WNC+Headphones",
                120, 10, true, false, false, null, null,
                h.images("https://placehold.co/800x800/1a1a2e/ffffff?text=WNC+Headphones",
                        "https://placehold.co/800x800/16213e/ffffff?text=WNC+Side",
                        "https://placehold.co/800x800/0f3460/ffffff?text=WNC+Case"),
                h.attrs("Driver Size", "40mm dynamic drivers",
                        "Battery Life", "40 hours ANC on / 60 hours off",
                        "Weight", "250g",
                        "Connectivity", "Bluetooth 5.3, USB-C, 3.5mm"),
                h.options1("Color", "Midnight Black", "Silver Grey"),
                p -> {
                    h.pv(p, "Midnight Black", "TECH-WNC-BLK", new BigDecimal("149.99"), 60);
                    h.pv(p, "Silver Grey",    "TECH-WNC-SLV", new BigDecimal("149.99"), 55);
                }));

        list.add(h.product(co, "Smart Watch Series X",
                "Advanced health monitoring with ECG, SpO2, GPS, and 18-day battery. Durable sapphire crystal display.",
                "TECH-SWX-001", "299.99", "349.99", "Electronics", "ChronoTech",
                "https://placehold.co/800x800/2d3561/ffffff?text=Smart+Watch",
                80, 8, true, false, false, null, null,
                h.images("https://placehold.co/800x800/2d3561/ffffff?text=Smart+Watch",
                        "https://placehold.co/800x800/1a1a2e/ffffff?text=Watch+Band",
                        "https://placehold.co/800x800/16213e/ffffff?text=Watch+Face"),
                h.attrs("Display", "1.9\" AMOLED Always-On",
                        "Battery", "18-day typical, 60-hour GPS",
                        "Water Resistance", "5 ATM",
                        "Sensors", "ECG, SpO2, accelerometer, GPS"),
                h.options2("Size", "42mm", "46mm", "Color", "Midnight", "Starlight"),
                p -> {
                    h.pv2(p, "42mm", "Midnight",    "TECH-SWX-42M", new BigDecimal("299.99"), 20);
                    h.pv2(p, "42mm", "Starlight",   "TECH-SWX-42S", new BigDecimal("299.99"), 18);
                    h.pv2(p, "42mm", "Product Red", "TECH-SWX-42R", new BigDecimal("299.99"), 10);
                    h.pv2(p, "46mm", "Midnight",    "TECH-SWX-46M", new BigDecimal("319.99"), 15);
                    h.pv2(p, "46mm", "Starlight",   "TECH-SWX-46S", new BigDecimal("319.99"), 12);
                    h.pv2(p, "46mm", "Product Red", "TECH-SWX-46R", new BigDecimal("319.99"), 8);
                }));

        list.add(h.product(co, "Portable Bluetooth Speaker",
                "360° immersive sound with IP67 waterproofing. 24-hour playtime and built-in power bank.",
                "TECH-PBS-001", "79.99", "99.99", "Electronics", "SoundCore",
                "https://placehold.co/800x800/1b4f72/ffffff?text=BT+Speaker",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1b4f72/ffffff?text=BT+Speaker",
                        "https://placehold.co/800x800/154360/ffffff?text=Speaker+Side"),
                h.attrs("Output Power", "30W",
                        "Battery", "24 hours",
                        "Water Rating", "IP67",
                        "Dimensions", "18 × 7.5 × 7.5 cm"),
                h.options1("Color", "Ocean Blue", "Charcoal", "Forest Green"),
                p -> {
                    h.pv(p, "Ocean Blue",    "TECH-PBS-BLU", new BigDecimal("79.99"), 55);
                    h.pv(p, "Charcoal",      "TECH-PBS-CHR", new BigDecimal("79.99"), 50);
                    h.pv(p, "Forest Green",  "TECH-PBS-GRN", new BigDecimal("79.99"), 45);
                }));

        list.add(h.productSingle(co, "USB-C 7-in-1 Hub",
                "Expand your laptop with 4K HDMI, 100W PD, 2× USB-A, SD, microSD, and Gigabit Ethernet.",
                "TECH-HUB-001", "49.99", "69.99", "Electronics", "NexPort",
                "https://placehold.co/800x800/212f3c/ffffff?text=USB+Hub",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/212f3c/ffffff?text=USB+Hub",
                        "https://placehold.co/800x800/1c2833/ffffff?text=Hub+Ports"),
                h.attrs("Ports", "HDMI 4K, 2× USB-A 3.0, SD, microSD, GbE, 100W PD",
                        "Max Video", "4K@60Hz",
                        "Cable Length", "20cm",
                        "Compatibility", "USB-C laptops, tablets")));

        list.add(h.product(co, "Mechanical Keyboard TKL",
                "Tenkeyless form factor with hot-swappable switches, per-key RGB, and anodized aluminum top case.",
                "TECH-MKB-001", "129.99", "159.99", "Electronics", "KeyForge",
                "https://placehold.co/800x800/1a252f/ffffff?text=Mech+Keyboard",
                90, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a252f/ffffff?text=Mech+Keyboard",
                        "https://placehold.co/800x800/17202a/ffffff?text=KB+Close"),
                h.attrs("Layout", "87-key TKL",
                        "Switches", "Hot-swappable 5-pin",
                        "Backlight", "Per-key RGB",
                        "Case", "Anodized aluminum top, ABS bottom"),
                h.options1("Switch", "Red (Linear)", "Brown (Tactile)", "Blue (Clicky)"),
                p -> {
                    h.pv(p, "Red (Linear)",    "TECH-MKB-RED", new BigDecimal("129.99"), 35);
                    h.pv(p, "Brown (Tactile)",  "TECH-MKB-BRN", new BigDecimal("129.99"), 30);
                    h.pv(p, "Blue (Clicky)",    "TECH-MKB-BLU", new BigDecimal("129.99"), 25);
                }));

        list.add(h.productSingle(co, "4K Webcam Pro",
                "Sony STARVIS sensor with 4K@30fps, auto-framing AI, dual noise-cancelling mics, and privacy shutter.",
                "TECH-WEB-001", "99.99", "129.99", "Electronics", "ClearCast",
                "https://placehold.co/800x800/2e4057/ffffff?text=4K+Webcam",
                75, 8, true, false, false, null, null,
                h.images("https://placehold.co/800x800/2e4057/ffffff?text=4K+Webcam",
                        "https://placehold.co/800x800/273746/ffffff?text=Webcam+Side"),
                h.attrs("Sensor", "Sony STARVIS 1/2.8\"",
                        "Resolution", "4K@30fps / 1080p@60fps",
                        "Field of View", "90° adjustable",
                        "Mics", "Dual omni-directional with AI noise cancellation")));

        list.add(h.product(co, "Wireless Charging Pad",
                "15W fast Qi2 wireless charger compatible with all Qi-enabled devices. Ultra-slim 5mm profile.",
                "TECH-WCP-001", "39.99", "49.99", "Electronics", "ChargeFast",
                "https://placehold.co/800x800/1e272e/ffffff?text=Wireless+Pad",
                180, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e272e/ffffff?text=Wireless+Pad",
                        "https://placehold.co/800x800/17202a/ffffff?text=Pad+Profile"),
                h.attrs("Output", "15W max (Qi2)",
                        "Thickness", "5mm",
                        "Diameter", "100mm",
                        "Cable", "USB-C, 1.5m"),
                h.options1("Color", "Matte Black", "Arctic White"),
                p -> {
                    h.pv(p, "Matte Black",  "TECH-WCP-BLK", new BigDecimal("39.99"), 90);
                    h.pv(p, "Arctic White", "TECH-WCP-WHT", new BigDecimal("39.99"), 85);
                }));

        list.add(h.product(co, "Noise-Cancelling Earbuds",
                "Active noise cancellation with 8-hour bud + 32-hour case battery. IPX5 sweat resistance.",
                "TECH-NCE-001", "89.99", "109.99", "Electronics", "SoundCore",
                "https://placehold.co/800x800/1a1a2e/ffffff?text=NC+Earbuds",
                130, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a1a2e/ffffff?text=NC+Earbuds",
                        "https://placehold.co/800x800/16213e/ffffff?text=Earbuds+Case"),
                h.attrs("Driver", "11mm dynamic",
                        "Battery", "8h buds + 32h case",
                        "ANC", "Hybrid feedforward + feedback",
                        "Water Rating", "IPX5"),
                h.options1("Color", "Jet Black", "Pearl White", "Navy Blue"),
                p -> {
                    h.pv(p, "Jet Black",   "TECH-NCE-BLK", new BigDecimal("89.99"), 45);
                    h.pv(p, "Pearl White", "TECH-NCE-WHT", new BigDecimal("89.99"), 50);
                    h.pv(p, "Navy Blue",   "TECH-NCE-NVY", new BigDecimal("89.99"), 35);
                }));

        list.add(h.productSingle(co, "Smart Home Hub",
                "Central control for 10,000+ compatible devices. Supports Matter, Zigbee, Z-Wave, Wi-Fi, and Bluetooth.",
                "TECH-SHH-001", "149.99", "179.99", "Electronics", "HomeIQ",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Smart+Hub",
                60, 6, true, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Smart+Hub",
                        "https://placehold.co/800x800/273746/ffffff?text=Hub+Ports"),
                h.attrs("Protocols", "Matter, Zigbee 3.0, Z-Wave Plus, Wi-Fi 6, BT 5.2",
                        "Compatible Devices", "10,000+",
                        "Ethernet", "Gigabit",
                        "Local Processing", "Yes, no cloud required")));

        list.add(h.product(co, "LED Gaming Mouse",
                "16,000 DPI optical sensor with 7 programmable buttons and 16.8M color RGB lighting. 1.2m braided cable.",
                "TECH-LGM-001", "59.99", "79.99", "Electronics", "PixelForce",
                "https://placehold.co/800x800/1c2833/ffffff?text=Gaming+Mouse",
                140, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Gaming+Mouse",
                        "https://placehold.co/800x800/17202a/ffffff?text=Mouse+Side"),
                h.attrs("Sensor", "Optical 16,000 DPI",
                        "Buttons", "7 programmable",
                        "Cable", "1.2m braided USB-A",
                        "Weight", "95g"),
                h.options1("Color", "Matte Black", "White"),
                p -> {
                    h.pv(p, "Matte Black", "TECH-LGM-BLK", new BigDecimal("59.99"), 70);
                    h.pv(p, "White",       "TECH-LGM-WHT", new BigDecimal("59.99"), 65);
                }));

        list.add(h.product(co, "Laptop Stand Adjustable",
                "Aluminum ergonomic stand with 6 height levels. Compatible with 10\"–17\" laptops. Foldable for travel.",
                "TECH-LSA-001", "44.99", "59.99", "Electronics", "ErgoPro",
                "https://placehold.co/800x800/707b7c/ffffff?text=Laptop+Stand",
                160, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/707b7c/ffffff?text=Laptop+Stand",
                        "https://placehold.co/800x800/616a6b/ffffff?text=Stand+Folded"),
                h.attrs("Material", "Aircraft-grade aluminum",
                        "Height Range", "6 adjustable levels (15–25cm)",
                        "Compatibility", "10\"–17\" laptops",
                        "Weight Capacity", "10kg"),
                h.options1("Color", "Space Gray", "Silver"),
                p -> {
                    h.pv(p, "Space Gray", "TECH-LSA-GRY", new BigDecimal("44.99"), 80);
                    h.pv(p, "Silver",     "TECH-LSA-SLV", new BigDecimal("44.99"), 75);
                }));

        list.add(h.product(co, "Portable SSD 1TB",
                "1TB NVMe portable SSD with USB 3.2 Gen 2 delivering 1050MB/s read. IP55 dust and water resistant.",
                "TECH-SSD-001", "109.99", "139.99", "Electronics", "FlashVault",
                "https://placehold.co/800x800/1a252f/ffffff?text=Portable+SSD",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a252f/ffffff?text=Portable+SSD",
                        "https://placehold.co/800x800/17202a/ffffff?text=SSD+Side"),
                h.attrs("Capacity", "1TB",
                        "Interface", "USB 3.2 Gen 2 (10Gbps)",
                        "Read Speed", "1,050 MB/s",
                        "Protection", "IP55 dust & water resistant"),
                h.options1("Color", "Slate Gray", "Midnight Black"),
                p -> {
                    h.pv(p, "Slate Gray",     "TECH-SSD-GRY", new BigDecimal("109.99"), 48);
                    h.pv(p, "Midnight Black", "TECH-SSD-BLK", new BigDecimal("109.99"), 45);
                }));

        list.add(h.productSingle(co, "USB Microphone Condenser",
                "Cardioid condenser mic with 24-bit/192kHz recording, zero-latency monitoring, and plug-and-play USB.",
                "TECH-MIC-001", "79.99", "99.99", "Electronics", "ClearCast",
                "https://placehold.co/800x800/1b2631/ffffff?text=USB+Mic",
                85, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1b2631/ffffff?text=USB+Mic",
                        "https://placehold.co/800x800/17202a/ffffff?text=Mic+Stand"),
                h.attrs("Pattern", "Cardioid",
                        "Sample Rate", "24-bit / 192kHz",
                        "Connection", "USB-C (cable included)",
                        "Frequency Response", "20Hz–20kHz")));

        list.add(h.product(co, "Smart LED Strip Lights",
                "16.4ft RGBIC addressable LEDs with scene modes, music sync, and app/voice control. Works with Alexa & Google.",
                "TECH-LED-001", "34.99", "44.99", "Electronics", "GlowTech",
                "https://placehold.co/800x800/1a5276/ffffff?text=LED+Strip",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a5276/ffffff?text=LED+Strip",
                        "https://placehold.co/800x800/154360/ffffff?text=Strip+Installed"),
                h.attrs("LED Type", "RGBIC addressable",
                        "Control", "App, voice (Alexa/Google)",
                        "Power", "24V DC adapter included",
                        "Compatibility", "Works with Alexa & Google Home"),
                h.options1("Length", "5m (16.4ft)", "10m (32.8ft)", "16.4ft Starter Kit"),
                p -> {
                    h.pv(p, "5m (16.4ft)",        "TECH-LED-5M",  new BigDecimal("34.99"), 80);
                    h.pv(p, "10m (32.8ft)",        "TECH-LED-10M", new BigDecimal("54.99"), 60);
                    h.pv(p, "16.4ft Starter Kit",  "TECH-LED-KIT", new BigDecimal("44.99"), 55);
                }));

        list.add(h.product(co, "Wireless Keyboard + Mouse Set",
                "Slim 2.4GHz wireless keyboard and mouse combo with 12-month battery life. Quiet scissor-switch keys.",
                "TECH-KMS-001", "69.99", "89.99", "Electronics", "KeyForge",
                "https://placehold.co/800x800/212f3c/ffffff?text=KB+Mouse+Set",
                120, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/212f3c/ffffff?text=KB+Mouse+Set",
                        "https://placehold.co/800x800/1c2833/ffffff?text=KB+MS+Close"),
                h.attrs("Connectivity", "2.4GHz USB nano receiver",
                        "Battery Life", "12 months keyboard, 18 months mouse",
                        "Keys", "Quiet scissor-switch",
                        "Mouse DPI", "1000/1600/2400 switchable"),
                h.options1("Color", "Graphite", "White"),
                p -> {
                    h.pv(p, "Graphite", "TECH-KMS-GRP", new BigDecimal("69.99"), 60);
                    h.pv(p, "White",    "TECH-KMS-WHT", new BigDecimal("69.99"), 55);
                }));

        list.add(h.productSingle(co, "Monitor Arm Dual",
                "Fully articulating dual-monitor arm supporting 2× 17\"–32\" displays up to 9kg each. VESA 75×75 & 100×100.",
                "TECH-MAD-001", "89.99", "119.99", "Electronics", "ErgoPro",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Monitor+Arm",
                55, 5, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Monitor+Arm",
                        "https://placehold.co/800x800/273746/ffffff?text=Arm+Extended"),
                h.attrs("Monitor Size", "17\"–32\"",
                        "Weight Capacity", "9kg per arm",
                        "VESA", "75×75 and 100×100mm",
                        "Adjustment", "Tilt ±45°, Swivel 360°, Rotate 90°")));

        list.add(h.product(co, "Cable Management Kit",
                "25-piece cable organizer kit including cable clips, sleeves, velcro ties, and cable boxes.",
                "TECH-CMK-001", "19.99", "24.99", "Electronics", "NexPort",
                "https://placehold.co/800x800/707b7c/ffffff?text=Cable+Kit",
                250, 25, false, false, false, null, null,
                h.images("https://placehold.co/800x800/707b7c/ffffff?text=Cable+Kit",
                        "https://placehold.co/800x800/616a6b/ffffff?text=Kit+Contents"),
                h.attrs("Pieces", "25 pcs: clips, sleeves, velcro, boxes",
                        "Material", "ABS plastic + nylon",
                        "Cable Box Size", "26 × 13 × 7 cm",
                        "Color Options", "Black or White"),
                h.options1("Color", "Black", "White"),
                p -> {
                    h.pv(p, "Black", "TECH-CMK-BLK", new BigDecimal("19.99"), 125);
                    h.pv(p, "White", "TECH-CMK-WHT", new BigDecimal("19.99"), 120);
                }));

        list.add(h.productSingle(co, "Smart Plug 4-Pack",
                "Wi-Fi smart plugs with energy monitoring, schedules, and voice control. No hub required.",
                "TECH-SPL-001", "29.99", "39.99", "Electronics", "HomeIQ",
                "https://placehold.co/800x800/1e272e/ffffff?text=Smart+Plug",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e272e/ffffff?text=Smart+Plug",
                        "https://placehold.co/800x800/17202a/ffffff?text=Plug+App"),
                h.attrs("Pack Size", "4 plugs",
                        "Max Load", "15A / 1800W",
                        "Standards", "Wi-Fi 2.4GHz, Works with Alexa/Google",
                        "Features", "Energy monitoring, schedules, timers")));

        list.add(h.product(co, "Mesh WiFi Router System",
                "Tri-band mesh system with Wi-Fi 6E support. Blanket 5,000–9,000 sq ft with seamless roaming.",
                "TECH-WFI-001", "199.99", "249.99", "Electronics", "NetBlast",
                "https://placehold.co/800x800/2e4057/ffffff?text=Mesh+WiFi",
                65, 6, true, false, false, null, null,
                h.images("https://placehold.co/800x800/2e4057/ffffff?text=Mesh+WiFi",
                        "https://placehold.co/800x800/273746/ffffff?text=WiFi+Coverage",
                        "https://placehold.co/800x800/212f3c/ffffff?text=WiFi+App"),
                h.attrs("Standard", "Wi-Fi 6E (802.11ax)",
                        "Bands", "Tri-band (2.4 + 5 + 6GHz)",
                        "Max Speed", "7.8 Gbps aggregate",
                        "Ports", "2.5GbE WAN + 2× 1GbE LAN per node"),
                h.options1("Size", "2-Node Kit (up to 5,000 sq ft)", "3-Node Kit (up to 9,000 sq ft)"),
                p -> {
                    h.pv(p, "2-Node Kit (up to 5,000 sq ft)", "TECH-WFI-2N", new BigDecimal("199.99"), 35);
                    h.pv(p, "3-Node Kit (up to 9,000 sq ft)", "TECH-WFI-3N", new BigDecimal("279.99"), 28);
                }));

        list.add(h.product(co, "Digital Photo Frame 10\"",
                "10.1\" IPS touchscreen frame with 32GB storage, Wi-Fi photo sharing, and motion-activated display.",
                "TECH-DPF-001", "79.99", "99.99", "Electronics", "MemoryBox",
                "https://placehold.co/800x800/1a1a2e/ffffff?text=Photo+Frame",
                90, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a1a2e/ffffff?text=Photo+Frame",
                        "https://placehold.co/800x800/16213e/ffffff?text=Frame+Side"),
                h.attrs("Display", "10.1\" IPS 1280×800",
                        "Storage", "32GB internal",
                        "Connectivity", "Wi-Fi 2.4/5GHz",
                        "Features", "Motion sensor, auto-brightness, touchscreen"),
                h.options1("Color", "Walnut Wood", "Matte Black"),
                p -> {
                    h.pv(p, "Walnut Wood", "TECH-DPF-WAL", new BigDecimal("79.99"), 45);
                    h.pv(p, "Matte Black", "TECH-DPF-BLK", new BigDecimal("79.99"), 40);
                }));

        // --- 30 additional products ---

        list.add(h.product(co, "Wireless Gaming Headset 7.1",
                "Virtual 7.1 surround sound with retractable noise-cancelling boom mic. 30-hour wireless battery.",
                "TECH-GHS-001", "89.99", "119.99", "Electronics", "SoundCore",
                "https://placehold.co/800x800/1c2833/ffffff?text=Gaming+Headset",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Gaming+Headset",
                        "https://placehold.co/800x800/17202a/ffffff?text=Headset+Mic"),
                h.attrs("Surround", "Virtual 7.1 surround",
                        "Battery", "30-hour wireless",
                        "Mic", "Retractable flip-to-mute",
                        "Connection", "2.4GHz USB dongle + 3.5mm"),
                h.options1("Color", "Midnight Black", "Arctic White"),
                p -> {
                    h.pv(p, "Midnight Black", "TECH-GHS-BLK", new BigDecimal("89.99"), 50);
                    h.pv(p, "Arctic White",   "TECH-GHS-WHT", new BigDecimal("89.99"), 45);
                }));

        list.add(h.productSingle(co, "Soundbar 2.1 with Subwoofer",
                "200W 2.1 soundbar with wireless subwoofer, Dolby Atmos, and HDMI eARC. Wall-mountable.",
                "TECH-SBR-001", "199.99", "249.99", "Electronics", "SoundCore",
                "https://placehold.co/800x800/1a1a2e/ffffff?text=Soundbar",
                55, 5, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a1a2e/ffffff?text=Soundbar",
                        "https://placehold.co/800x800/16213e/ffffff?text=Soundbar+Sub"),
                h.attrs("Output", "200W total (140W bar + 60W sub)",
                        "Audio", "Dolby Atmos, DTS:X",
                        "Connections", "HDMI eARC, Optical, Bluetooth 5.0",
                        "Dimensions", "90cm bar + wireless sub")));

        list.add(h.product(co, "Drawing Tablet 10\"",
                "10\" active area graphics tablet with 8192 pressure levels, tilt support, and 8 express keys.",
                "TECH-DRW-001", "79.99", "99.99", "Electronics", "PixelForce",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Drawing+Tablet",
                75, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Drawing+Tablet",
                        "https://placehold.co/800x800/273746/ffffff?text=Tablet+Pen"),
                h.attrs("Active Area", "10\" × 6.25\"",
                        "Pressure", "8192 levels",
                        "Tilt", "±60°",
                        "Express Keys", "8 customizable"),
                h.options1("Size", "10\" Standard", "13\" Large"),
                p -> {
                    h.pv(p, "10\" Standard", "TECH-DRW-10", new BigDecimal("79.99"),  40);
                    h.pv(p, "13\" Large",    "TECH-DRW-13", new BigDecimal("129.99"), 30);
                }));

        list.add(h.productSingle(co, "Ergonomic Trackball Mouse",
                "Scroll-ring trackball with 6 programmable buttons and USB-C charging. Eliminates wrist strain.",
                "TECH-TBL-001", "69.99", "89.99", "Electronics", "ErgoPro",
                "https://placehold.co/800x800/1e272e/ffffff?text=Trackball+Mouse",
                80, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e272e/ffffff?text=Trackball+Mouse",
                        "https://placehold.co/800x800/17202a/ffffff?text=Trackball+Side"),
                h.attrs("Tracking", "Optical trackball, 400–2000 DPI",
                        "Buttons", "6 programmable",
                        "Connectivity", "Bluetooth + 2.4GHz dual",
                        "Battery", "70 days per charge")));

        list.add(h.productSingle(co, "XXL Gaming Desk Mat",
                "900×400mm stitched-edge desk mat with non-slip rubber base. Smooth micro-weave surface.",
                "TECH-DMP-001", "29.99", "39.99", "Electronics", "PixelForce",
                "https://placehold.co/800x800/1a1a2e/ffffff?text=Desk+Mat",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a1a2e/ffffff?text=Desk+Mat",
                        "https://placehold.co/800x800/16213e/ffffff?text=Mat+Edge"),
                h.attrs("Size", "900 × 400 × 3mm",
                        "Surface", "Micro-weave cloth",
                        "Base", "Non-slip natural rubber",
                        "Edge", "Stitched for durability")));

        list.add(h.product(co, "GaN Charger 4-Port 140W",
                "140W USB-C GaN charger with 2× USB-C (100W + 30W) and 2× USB-A (18W). Smart power distribution.",
                "TECH-GAN-001", "59.99", "79.99", "Electronics", "ChargeFast",
                "https://placehold.co/800x800/212f3c/ffffff?text=GaN+Charger",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/212f3c/ffffff?text=GaN+Charger",
                        "https://placehold.co/800x800/1c2833/ffffff?text=Charger+Ports"),
                h.attrs("Total Output", "140W",
                        "Ports", "2× USB-C + 2× USB-A",
                        "Top Port", "100W USB-C PD 3.1",
                        "Technology", "GaN III"),
                h.options1("Color", "Matte Black", "Frost White"),
                p -> {
                    h.pv(p, "Matte Black",  "TECH-GAN-BLK", new BigDecimal("59.99"), 75);
                    h.pv(p, "Frost White",  "TECH-GAN-WHT", new BigDecimal("59.99"), 70);
                }));

        list.add(h.productSingle(co, "Laptop Power Bank 26800mAh",
                "26800mAh power bank with 100W USB-C PD output. Charges a MacBook Pro from 0–100% twice.",
                "TECH-PWB-001", "79.99", "99.99", "Electronics", "ChargeFast",
                "https://placehold.co/800x800/1b2631/ffffff?text=Power+Bank",
                90, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1b2631/ffffff?text=Power+Bank",
                        "https://placehold.co/800x800/17202a/ffffff?text=Bank+Ports"),
                h.attrs("Capacity", "26800mAh / 99Wh",
                        "USB-C Output", "100W PD",
                        "USB-A Output", "18W QC 3.0",
                        "Recharge Time", "2.5h with 100W input")));

        list.add(h.product(co, "Portable Monitor 15.6\"",
                "1080p IPS portable monitor with USB-C single-cable connectivity and kickstand case.",
                "TECH-PMN-001", "169.99", "219.99", "Electronics", "NexPort",
                "https://placehold.co/800x800/2e4057/ffffff?text=Portable+Monitor",
                55, 5, true, false, false, null, null,
                h.images("https://placehold.co/800x800/2e4057/ffffff?text=Portable+Monitor",
                        "https://placehold.co/800x800/273746/ffffff?text=Monitor+Side"),
                h.attrs("Display", "15.6\" IPS FHD 1920×1080",
                        "Brightness", "350 nits",
                        "Connectivity", "2× USB-C (full-function), Mini HDMI",
                        "Weight", "800g"),
                h.options1("Finish", "Matte Anti-Glare", "Glossy"),
                p -> {
                    h.pv(p, "Matte Anti-Glare", "TECH-PMN-MAT", new BigDecimal("169.99"), 28);
                    h.pv(p, "Glossy",           "TECH-PMN-GLS", new BigDecimal("169.99"), 22);
                }));

        list.add(h.productSingle(co, "Monitor Light Bar",
                "Asymmetric LED light bar clips to monitor. Lights desk without glare on screen. Touch-sensitive dial.",
                "TECH-MLB-001", "39.99", "54.99", "Electronics", "GlowTech",
                "https://placehold.co/800x800/1e272e/ffffff?text=Monitor+Light",
                120, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e272e/ffffff?text=Monitor+Light",
                        "https://placehold.co/800x800/17202a/ffffff?text=Light+Glow"),
                h.attrs("Light", "Asymmetric, no screen glare",
                        "Color Temp", "2700K–6500K adjustable",
                        "Brightness", "Stepless dimming",
                        "Control", "Touch dial on cable")));

        list.add(h.productSingle(co, "Smart Video Doorbell",
                "2K HDR video with color night vision, two-way audio, and local + cloud storage. Works with Alexa.",
                "TECH-SVD-001", "129.99", "159.99", "Electronics", "HomeIQ",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Smart+Doorbell",
                65, 6, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Smart+Doorbell",
                        "https://placehold.co/800x800/273746/ffffff?text=Doorbell+App"),
                h.attrs("Video", "2K HDR, 160° FOV",
                        "Night Vision", "Color, up to 5m",
                        "Audio", "Two-way with noise cancellation",
                        "Storage", "Local microSD + cloud subscription")));

        list.add(h.product(co, "Smart Thermostat Learning",
                "AI learning thermostat with remote scheduling, energy reports, and compatibility with most HVAC systems.",
                "TECH-STH-001", "149.99", "199.99", "Electronics", "HomeIQ",
                "https://placehold.co/800x800/ecf0f1/333333?text=Smart+Thermostat",
                60, 5, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Smart+Thermostat",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Thermostat+App"),
                h.attrs("Display", "3.5\" color touch",
                        "Connectivity", "Wi-Fi 2.4/5GHz + Bluetooth",
                        "Compatibility", "95% of HVAC systems",
                        "Savings", "Up to 23% on heating & cooling"),
                h.options1("Color", "Platinum", "Copper", "Black"),
                p -> {
                    h.pv(p, "Platinum", "TECH-STH-PLT", new BigDecimal("149.99"), 25);
                    h.pv(p, "Copper",   "TECH-STH-CPR", new BigDecimal("149.99"), 18);
                    h.pv(p, "Black",    "TECH-STH-BLK", new BigDecimal("149.99"), 15);
                }));

        list.add(h.productSingle(co, "Smart Door Lock Fingerprint",
                "Wi-Fi smart lock with fingerprint, PIN, card, app, and key backup. 250 fingerprint capacity.",
                "TECH-SDL-001", "179.99", "229.99", "Electronics", "HomeIQ",
                "https://placehold.co/800x800/1a252f/ffffff?text=Smart+Lock",
                45, 4, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a252f/ffffff?text=Smart+Lock",
                        "https://placehold.co/800x800/17202a/ffffff?text=Lock+Panel"),
                h.attrs("Access Methods", "Fingerprint, PIN, NFC card, app, key",
                        "Capacity", "250 fingerprints, 100 PINs",
                        "Battery", "4× AA, 1-year life",
                        "Connectivity", "Wi-Fi + Bluetooth")));

        list.add(h.product(co, "Smart LED Bulbs 4-Pack A19",
                "800lm tunable white + RGB smart bulb. Voice and app control, no hub required.",
                "TECH-SLB-001", "34.99", "44.99", "Electronics", "HomeIQ",
                "https://placehold.co/800x800/fef9e7/333333?text=Smart+Bulbs",
                200, 20, false, false, false, null, null,
                h.images("https://placehold.co/800x800/fef9e7/333333?text=Smart+Bulbs",
                        "https://placehold.co/800x800/fdf2d0/333333?text=Bulb+Colors"),
                h.attrs("Pack", "4 × A19 bulbs",
                        "Lumens", "800lm (60W equivalent)",
                        "Colors", "16M RGBW + tunable white 2700–6500K",
                        "Control", "App, Alexa, Google, Siri Shortcuts"),
                h.options1("Pack Size", "4-Pack", "8-Pack"),
                p -> {
                    h.pv(p, "4-Pack", "TECH-SLB-4P", new BigDecimal("34.99"), 100);
                    h.pv(p, "8-Pack", "TECH-SLB-8P", new BigDecimal("59.99"),  80);
                }));

        list.add(h.productSingle(co, "Smart Body Composition Scale",
                "13-metric Bluetooth scale measuring weight, BMI, body fat, muscle mass, bone density, and more.",
                "TECH-SBS-001", "34.99", "49.99", "Electronics", "HomeIQ",
                "https://placehold.co/800x800/ecf0f1/333333?text=Smart+Scale",
                150, 15, false, false, false, null, null,
                h.images("https://placehold.co/800x800/ecf0f1/333333?text=Smart+Scale",
                        "https://placehold.co/800x800/d5dbdb/333333?text=Scale+App"),
                h.attrs("Metrics", "13: weight, BMI, body fat, muscle, bone, hydration…",
                        "Capacity", "180kg",
                        "Precision", "50g increments",
                        "Users", "Unlimited via app")));

        list.add(h.productSingle(co, "USB-C Docking Station 12-in-1",
                "12-in-1 dock with dual 4K HDMI, 100W PD, 4× USB-A, 2× USB-C, SD/microSD, Ethernet, and audio.",
                "TECH-DCK-001", "129.99", "169.99", "Electronics", "NexPort",
                "https://placehold.co/800x800/212f3c/ffffff?text=Dock+Station",
                70, 7, false, false, false, null, null,
                h.images("https://placehold.co/800x800/212f3c/ffffff?text=Dock+Station",
                        "https://placehold.co/800x800/1c2833/ffffff?text=Dock+Ports"),
                h.attrs("Ports", "2× HDMI 4K, 4× USB-A, 2× USB-C, SD, microSD, GbE, audio",
                        "Power Delivery", "100W pass-through",
                        "Video", "Dual 4K@60Hz",
                        "Compatible", "Thunderbolt 3/4, USB4")));

        list.add(h.product(co, "External HDD 4TB",
                "4TB USB 3.0 portable hard drive with auto-backup software and hardware encryption.",
                "TECH-HDD-001", "89.99", "109.99", "Electronics", "FlashVault",
                "https://placehold.co/800x800/1a252f/ffffff?text=External+HDD",
                90, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a252f/ffffff?text=External+HDD",
                        "https://placehold.co/800x800/17202a/ffffff?text=HDD+Side"),
                h.attrs("Capacity", "4TB",
                        "Interface", "USB 3.0 (USB-C cable included)",
                        "Speed", "Up to 130 MB/s",
                        "Encryption", "256-bit AES hardware"),
                h.options1("Color", "Space Black", "Silver"),
                p -> {
                    h.pv(p, "Space Black", "TECH-HDD-BLK", new BigDecimal("89.99"), 45);
                    h.pv(p, "Silver",      "TECH-HDD-SLV", new BigDecimal("89.99"), 40);
                }));

        list.add(h.productSingle(co, "8-Port Gigabit Network Switch",
                "Unmanaged 8-port Gigabit switch with plug-and-play setup and fanless silent operation.",
                "TECH-NSW-001", "29.99", "39.99", "Electronics", "NetBlast",
                "https://placehold.co/800x800/1e272e/ffffff?text=Network+Switch",
                120, 12, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e272e/ffffff?text=Network+Switch",
                        "https://placehold.co/800x800/17202a/ffffff?text=Switch+Ports"),
                h.attrs("Ports", "8× Gigabit RJ45",
                        "Switching Capacity", "16 Gbps",
                        "Setup", "Plug-and-play, no software",
                        "Cooling", "Fanless, silent")));

        list.add(h.productSingle(co, "PoE IP Security Camera 4K",
                "Outdoor 4K PoE IP camera with starlight sensor, 30m IR night vision, and IP67 weatherproofing.",
                "TECH-CAM-001", "79.99", "99.99", "Electronics", "HomeIQ",
                "https://placehold.co/800x800/1c2833/ffffff?text=Security+Cam",
                80, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=Security+Cam",
                        "https://placehold.co/800x800/17202a/ffffff?text=Cam+Mount"),
                h.attrs("Resolution", "4K (8MP) @ 20fps",
                        "Night Vision", "30m IR",
                        "Protection", "IP67 weatherproof",
                        "Connection", "PoE (802.3af) or 12V DC")));

        list.add(h.productSingle(co, "HDMI Capture Card 4K",
                "4K@30fps HDMI capture card with USB 3.0. Zero-lag passthrough for streaming and recording.",
                "TECH-CAP-001", "69.99", "89.99", "Electronics", "ClearCast",
                "https://placehold.co/800x800/1a252f/ffffff?text=Capture+Card",
                85, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1a252f/ffffff?text=Capture+Card",
                        "https://placehold.co/800x800/17202a/ffffff?text=Card+Ports"),
                h.attrs("Capture", "4K@30fps or 1080p@60fps",
                        "Passthrough", "4K@60fps zero-lag",
                        "Interface", "USB 3.0",
                        "Compatible", "OBS, XSplit, Teams, Zoom")));

        list.add(h.product(co, "Ring Light 18\" Pro",
                "18\" bi-color ring light with phone holder, hot shoe, and 3200K–5600K stepless adjustment.",
                "TECH-RLT-001", "54.99", "74.99", "Electronics", "GlowTech",
                "https://placehold.co/800x800/fef9e7/333333?text=Ring+Light",
                75, 8, false, false, false, null, null,
                h.images("https://placehold.co/800x800/fef9e7/333333?text=Ring+Light",
                        "https://placehold.co/800x800/fdf2d0/333333?text=Light+Setup"),
                h.attrs("Diameter", "18\" (46cm)",
                        "Color Temp", "3200K–5600K",
                        "Brightness", "10 levels via controller",
                        "Includes", "Stand, phone holder, hot shoe, 3× color filter"),
                h.options1("Kit", "Light Only", "Light + Stand Kit"),
                p -> {
                    h.pv(p, "Light Only",       "TECH-RLT-LGT", new BigDecimal("54.99"), 38);
                    h.pv(p, "Light + Stand Kit", "TECH-RLT-KIT", new BigDecimal("79.99"), 32);
                }));

        list.add(h.productSingle(co, "Collapsible Green Screen",
                "5×7ft chromakey backdrop on spring-loaded collapsible frame. Sets up in 10 seconds.",
                "TECH-GRS-001", "59.99", "79.99", "Electronics", "ClearCast",
                "https://placehold.co/800x800/27ae60/ffffff?text=Green+Screen",
                60, 6, false, false, false, null, null,
                h.images("https://placehold.co/800x800/27ae60/ffffff?text=Green+Screen",
                        "https://placehold.co/800x800/229954/ffffff?text=Screen+Folded"),
                h.attrs("Size", "5 × 7 ft (152 × 213cm)",
                        "Material", "Wrinkle-free muslin",
                        "Frame", "Spring collapsible, no assembly",
                        "Storage", "Carry bag included")));

        list.add(h.productSingle(co, "Smart Power Strip 6-Outlet",
                "Wi-Fi smart power strip with 6 individually controlled outlets and 4 USB ports (2× USB-C).",
                "TECH-SPS-001", "44.99", "59.99", "Electronics", "HomeIQ",
                "https://placehold.co/800x800/1e272e/ffffff?text=Smart+Strip",
                110, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e272e/ffffff?text=Smart+Strip",
                        "https://placehold.co/800x800/17202a/ffffff?text=Strip+App"),
                h.attrs("Outlets", "6 individually switchable AC",
                        "USB Ports", "2× USB-A + 2× USB-C",
                        "Surge Protection", "2700J",
                        "Control", "App, Alexa, Google")));

        list.add(h.productSingle(co, "Mini PC Fanless Intel N100",
                "Fanless mini PC with Intel N100, 16GB RAM, 512GB NVMe, dual HDMI 4K, and Windows 11 Pro.",
                "TECH-MPC-001", "219.99", "279.99", "Electronics", "NetBlast",
                "https://placehold.co/800x800/1a252f/ffffff?text=Mini+PC",
                40, 4, true, false, false, null, null,
                h.images("https://placehold.co/800x800/1a252f/ffffff?text=Mini+PC",
                        "https://placehold.co/800x800/17202a/ffffff?text=Mini+PC+Back"),
                h.attrs("CPU", "Intel N100 (4-core, up to 3.4GHz)",
                        "RAM / Storage", "16GB DDR4 + 512GB NVMe",
                        "Video", "Dual HDMI 4K@60Hz",
                        "OS", "Windows 11 Pro")));

        list.add(h.product(co, "Portable Projector 1080p",
                "Native 1080p LED projector with 700 ANSI lumens, built-in Android TV, and 60W Bluetooth speaker.",
                "TECH-PRJ-001", "299.99", "379.99", "Electronics", "PixelForce",
                "https://placehold.co/800x800/2c3e50/ffffff?text=Projector",
                35, 3, true, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=Projector",
                        "https://placehold.co/800x800/273746/ffffff?text=Projector+Beam"),
                h.attrs("Resolution", "1920×1080 native",
                        "Brightness", "700 ANSI lumens",
                        "OS", "Android TV 11",
                        "Speaker", "Dual 10W Hi-Fi"),
                h.options1("Color", "Charcoal", "Arctic White"),
                p -> {
                    h.pv(p, "Charcoal",     "TECH-PRJ-CHR", new BigDecimal("299.99"), 18);
                    h.pv(p, "Arctic White", "TECH-PRJ-WHT", new BigDecimal("299.99"), 15);
                }));

        list.add(h.productSingle(co, "Raspberry Pi 5 Starter Kit",
                "Complete Raspberry Pi 5 (8GB) kit with official case, 27W power supply, SD card, and heatsink.",
                "TECH-RPI-001", "119.99", "149.99", "Electronics", "NetBlast",
                "https://placehold.co/800x800/cc0000/ffffff?text=Raspberry+Pi",
                50, 5, false, false, false, null, null,
                h.images("https://placehold.co/800x800/cc0000/ffffff?text=Raspberry+Pi",
                        "https://placehold.co/800x800/b30000/ffffff?text=Pi+Board"),
                h.attrs("Board", "Raspberry Pi 5 8GB RAM",
                        "Kit Includes", "Official case, 27W PSU, 32GB SD, heatsink, HDMI cable",
                        "Connectivity", "Wi-Fi 6, Bluetooth 5.0, 2× USB 3, 2× USB 2, 2× micro HDMI",
                        "OS", "Raspberry Pi OS (pre-installed)")));

        list.add(h.productSingle(co, "VR Headset Standalone",
                "All-in-one VR headset with 4K display, inside-out tracking, and 50+ supported games. 3-hour battery.",
                "TECH-VRS-001", "399.99", "479.99", "Electronics", "PixelForce",
                "https://placehold.co/800x800/1c2833/ffffff?text=VR+Headset",
                30, 3, true, false, false, null, null,
                h.images("https://placehold.co/800x800/1c2833/ffffff?text=VR+Headset",
                        "https://placehold.co/800x800/17202a/ffffff?text=VR+Controllers"),
                h.attrs("Display", "4K LCD (2K per eye) 90Hz",
                        "Tracking", "6DOF inside-out, no external sensors",
                        "Storage", "128GB",
                        "Battery", "3 hours gaming")));

        list.add(h.product(co, "Streaming Microphone Arm Kit",
                "Professional scissor boom arm with desk clamp, shock mount, and pop filter. Supports up to 1.5kg.",
                "TECH-MAK-001", "49.99", "69.99", "Electronics", "ClearCast",
                "https://placehold.co/800x800/1b2631/ffffff?text=Mic+Arm+Kit",
                90, 9, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1b2631/ffffff?text=Mic+Arm+Kit",
                        "https://placehold.co/800x800/17202a/ffffff?text=Arm+Extended"),
                h.attrs("Reach", "80cm max extension",
                        "Load", "Up to 1.5kg",
                        "Clamp", "Desk clamp, 2.5cm–6cm depth",
                        "Kit Includes", "Arm, shock mount, pop filter, cable clips"),
                h.options1("Color", "Matte Black", "All White"),
                p -> {
                    h.pv(p, "Matte Black", "TECH-MAK-BLK", new BigDecimal("49.99"), 45);
                    h.pv(p, "All White",   "TECH-MAK-WHT", new BigDecimal("49.99"), 40);
                }));

        list.add(h.productSingle(co, "Network Attached Storage 2-Bay",
                "2-bay NAS with Intel Celeron J4125, 4GB RAM, and diskless design. Supports RAID 0/1.",
                "TECH-NAS-001", "299.99", "379.99", "Electronics", "FlashVault",
                "https://placehold.co/800x800/2c3e50/ffffff?text=NAS+Drive",
                25, 3, false, false, false, null, null,
                h.images("https://placehold.co/800x800/2c3e50/ffffff?text=NAS+Drive",
                        "https://placehold.co/800x800/273746/ffffff?text=NAS+Bays"),
                h.attrs("CPU", "Intel Celeron J4125 quad-core",
                        "RAM", "4GB DDR4 (expandable to 8GB)",
                        "Bays", "2× 3.5\"/2.5\" SATA",
                        "Connectivity", "2× 2.5GbE, 2× USB 3.2, 1× USB-C")));

        list.add(h.product(co, "Smart Garage Door Controller",
                "Wi-Fi garage door controller works with most garage door openers built after 1993. Alexa & Google compatible.",
                "TECH-GDC-001", "29.99", "39.99", "Electronics", "HomeIQ",
                "https://placehold.co/800x800/1e272e/ffffff?text=Garage+Controller",
                100, 10, false, false, false, null, null,
                h.images("https://placehold.co/800x800/1e272e/ffffff?text=Garage+Controller",
                        "https://placehold.co/800x800/17202a/ffffff?text=Garage+App"),
                h.attrs("Compatible", "Works with 95% of openers since 1993",
                        "Features", "Remote open/close, status alerts, activity log",
                        "Connectivity", "Wi-Fi 2.4GHz",
                        "Installation", "No tools required"),
                h.options1("Pack", "1 Door", "2 Doors"),
                p -> {
                    h.pv(p, "1 Door",  "TECH-GDC-1D", new BigDecimal("29.99"), 55);
                    h.pv(p, "2 Doors", "TECH-GDC-2D", new BigDecimal("49.99"), 40);
                }));

        list.add(h.productSingle(co, "4-Port KVM Switch HDMI",
                "4-port KVM switch sharing 4K@60Hz HDMI monitor, keyboard, and mouse across 4 PCs. Hotkey switching.",
                "TECH-KVM-001", "59.99", "79.99", "Electronics", "NexPort",
                "https://placehold.co/800x800/212f3c/ffffff?text=KVM+Switch",
                55, 5, false, false, false, null, null,
                h.images("https://placehold.co/800x800/212f3c/ffffff?text=KVM+Switch",
                        "https://placehold.co/800x800/1c2833/ffffff?text=KVM+Ports"),
                h.attrs("Ports", "4× HDMI computers, 1× HDMI monitor",
                        "Resolution", "4K@60Hz",
                        "USB", "2× USB 2.0 shared",
                        "Switching", "Hotkey, button, or auto-detect")));

        return list;
    }
}
