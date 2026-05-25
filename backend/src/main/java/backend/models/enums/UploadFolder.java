package backend.models.enums;

public enum UploadFolder {
    COMPANY_LOGO("company-logos"),
    PRODUCT_THUMBNAIL("product-thumbnails"),
    PRODUCT_IMAGE("product-images"),
    RETURN_EVIDENCE("return-evidence"),
    REVIEW_MEDIA("review-media"),
    IMPORT_CSV("imports/csv"),
    IMPORT_ERROR_REPORT("imports/errors"),
    REPORT_SCREENSHOT("report-screenshots");

    private final String path;

    UploadFolder(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
