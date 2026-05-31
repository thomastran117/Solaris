package backend.models.enums;

/**
 * STATIC: members are added/removed by hand from the admin UI.
 * DYNAMIC: members are recomputed periodically by {@code DynamicCollectionWorker} from
 * a JSON rule (tag/category/brand match) stored on the Collection entity.
 */
public enum CollectionType {
    STATIC,
    DYNAMIC
}
