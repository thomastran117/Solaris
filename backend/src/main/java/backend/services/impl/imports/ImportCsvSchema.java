package backend.services.impl.imports;

import java.util.List;

/**
 * Canonical CSV column names for product import/export. Header parsing is
 * case-insensitive (handled by the parser) but the export writer always emits
 * the names below in this order.
 */
final class ImportCsvSchema {

    private ImportCsvSchema() {}

    static final String SKU = "sku";
    static final String NAME = "name";
    static final String DESCRIPTION = "description";
    static final String PRICE = "price";
    static final String COMPARE_AT_PRICE = "compareAtPrice";
    static final String CURRENCY = "currency";
    static final String CATEGORY = "category";
    static final String BRAND = "brand";
    static final String TAGS = "tags";
    static final String STOCK = "stock";
    static final String WEIGHT = "weight";
    static final String WEIGHT_UNIT = "weightUnit";
    static final String FEATURED = "featured";
    static final String PURCHASABLE = "purchasable";
    static final String LISTED = "listed";
    static final String STATUS = "status";
    static final String THUMBNAIL_URL = "thumbnailUrl";
    static final String IMAGE_URL_1 = "imageUrl1";
    static final String IMAGE_URL_2 = "imageUrl2";
    static final String IMAGE_URL_3 = "imageUrl3";
    static final String IMAGE_URL_4 = "imageUrl4";
    static final String IMAGE_URL_5 = "imageUrl5";

    static final List<String> EXPORT_COLUMNS = List.of(
            SKU, NAME, DESCRIPTION, PRICE, COMPARE_AT_PRICE, CURRENCY,
            CATEGORY, BRAND, TAGS, STOCK, WEIGHT, WEIGHT_UNIT,
            FEATURED, PURCHASABLE, LISTED, STATUS, THUMBNAIL_URL,
            IMAGE_URL_1, IMAGE_URL_2, IMAGE_URL_3, IMAGE_URL_4, IMAGE_URL_5
    );

    static final List<String> IMAGE_COLUMNS = List.of(
            IMAGE_URL_1, IMAGE_URL_2, IMAGE_URL_3, IMAGE_URL_4, IMAGE_URL_5
    );
}
