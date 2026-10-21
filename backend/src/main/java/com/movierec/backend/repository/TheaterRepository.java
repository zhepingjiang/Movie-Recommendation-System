package com.movierec.backend.repository;

import com.movierec.backend.entity.Theater;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Theater}.
 */
public interface TheaterRepository extends JpaRepository<Theater, Long> {

    /**
     * Coarse spatial prefilter for the nearby search: every theater in any of the given H3 cells
     * (uses idx_theaters_h3_cell). Callers still filter by exact distance.
     *
     * @param h3Cells candidate cell ids, all at {@link com.movierec.backend.geo.H3Cells#RESOLUTION}
     * @return the theaters in those cells, in no particular order
     */
    List<Theater> findByH3CellIn(Collection<Long> h3Cells);
}
