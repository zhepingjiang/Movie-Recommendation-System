-- Location columns for the nearby-theaters search. theaters has had no rows since V1 (nothing
-- ever wrote to it), so the NOT NULL columns can be added without defaults.
--
-- h3_cell is the Uber H3 cell (resolution 7, see H3Cells) containing (lat, lng). It is derived,
-- never set by hand: Theater's @PrePersist/@PreUpdate hook computes it, and V16's seed computes
-- it with the same helper. The search prefilters with "h3_cell IN (cells near the user)" and
-- then applies the exact haversine distance.
ALTER TABLE theaters
    ADD COLUMN osm_id      VARCHAR(30) UNIQUE,
    ADD COLUMN brand       VARCHAR(100),
    ADD COLUMN postal_code VARCHAR(20),
    ADD COLUMN lat         DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    ADD COLUMN lng         DOUBLE PRECISION NOT NULL CHECK (lng BETWEEN -180 AND 180),
    ADD COLUMN h3_cell     BIGINT NOT NULL;

CREATE INDEX idx_theaters_h3_cell ON theaters (h3_cell);
