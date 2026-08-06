package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "saved_lists", indexes = {
        @Index(name = "idx_saved_list_user_id", columnList = "user_id"),
        @Index(name = "idx_saved_list_share_slug", columnList = "share_slug")
})
@EntityListeners(AuditingEntityListener.class)
public class SavedList {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SavedListType type = SavedListType.WISHLIST;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean isPublic = false;

    @Column(name = "share_slug", unique = true, length = 16)
    private String shareSlug;

    @OneToMany(mappedBy = "savedList", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SavedListItem> items = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
