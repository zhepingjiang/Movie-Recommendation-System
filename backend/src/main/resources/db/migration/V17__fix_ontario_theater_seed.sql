-- Corrects rows V16 seeded from OpenStreetMap. The seed CSV has the same corrections, so on a
-- fresh database these statements match nothing; they exist for databases that already ran V16.

-- FIX: tagged amenity=cinema in OSM but not movie theaters: a selfie studio and a Niagara Falls
-- 3D/4D attraction ride.
DELETE FROM theaters WHERE osm_id IN ('node/3580486880', 'node/3580513501');

-- FIX: OSM has only addr:housenumber for Stratford Cinemas, so the address was just "551". Street,
-- city and postcode confirmed with a Nominatim reverse lookup of the node's coordinates.
UPDATE theaters
SET address = '551 Huron Street', city = 'Stratford', postal_code = 'N5A 5T8'
WHERE osm_id = 'node/2649205951';
