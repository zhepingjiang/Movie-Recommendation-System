package com.movierec.backend.entity;

import com.movierec.backend.geo.H3Cells;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity mapping to the {@code theaters} table.
 */
@Entity
@Table(name = "theaters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Theater {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 100)
    private String brand;

    /** OpenStreetMap element ("node/123", "way/456") this row was imported from, if any. */
    @Column(name = "osm_id", length = 30, unique = true)
    private String osmId;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    /**
     * H3 cell of (lat, lng) at {@link H3Cells#RESOLUTION}. Derived: always overwritten by
     * {@link #updateH3Cell()}, never set by hand.
     */
    @Column(name = "h3_cell", nullable = false)
    private Long h3Cell;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Keeps {@code h3Cell} in step with the coordinates on every insert and update. Done in the
     * entity rather than a service so no write path (current or future) can forget it -- it
     * depends only on this row's own fields. Caveat: bulk JPQL/SQL updates of lat/lng bypass
     * lifecycle hooks and would have to recompute h3_cell themselves.
     */
    @PrePersist
    @PreUpdate
    void updateH3Cell() {
        h3Cell = H3Cells.cellFor(lat, lng);
    }
}
