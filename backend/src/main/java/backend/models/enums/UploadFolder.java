package backend.models.enums;

public enum UploadFolder {
    COMPANY_LOGO("company-logos"),
    PRODUCT_THUMBNAIL("product-thumbnails");

    private final String path;

    UploadFolder(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
