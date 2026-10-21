"""
One-off fetch: pulls every cinema in Ontario from OpenStreetMap (Overpass API) and writes
src/main/resources/db/seed/ontario_theaters.csv, which the V16__seed_ontario_theaters Java
migration loads into the theaters table.

Data is (c) OpenStreetMap contributors, ODbL -- the theatres page must keep that attribution.
Not run by the app; re-run manually to refresh, then add a new migration to load the new CSV
(V16 is versioned and won't run twice).
"""

import csv
import math
import os
import re
import sys

import requests

OUTPUT_CSV = os.path.join(
    os.path.dirname(__file__), "..", "src", "main", "resources", "db", "seed", "ontario_theaters.csv"
)

OVERPASS_URL = "https://overpass-api.de/api/interpreter"
# nwr = nodes, ways and relations: many cinemas are mapped as a building outline (way) rather
# than a point. "out center" gives those a single centre coordinate.
OVERPASS_QUERY = """
[out:json][timeout:90];
area["ISO3166-2"="CA-ON"][admin_level=4]->.ontario;
nwr["amenity"="cinema"](area.ontario);
out center tags;
"""
REQUEST_TIMEOUT_SECONDS = 120
USER_AGENT = "cinemind-theater-import/1.0 (movie-rec-system dev script)"

# Two entries with the same name closer than this are the same cinema mapped twice (e.g. once as
# a point and once as its building).
DUPLICATE_DISTANCE_KM = 0.15
EARTH_RADIUS_KM = 6371.0
DRIVE_IN_NAME_PATTERN = re.compile(r"\bdrive[\s-]?in\b", re.IGNORECASE)

CSV_COLUMNS = ["osm_id", "name", "brand", "address", "city", "postal_code", "lat", "lng"]


def fetch_overpass_elements() -> list[dict]:
    """Runs the Ontario cinema query against the public Overpass API.

    Returns:
        The raw Overpass elements (nodes, ways, relations), each with its tags.
    """
    response = requests.post(
        OVERPASS_URL,
        data={"data": OVERPASS_QUERY},
        headers={"User-Agent": USER_AGENT},
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    return response.json()["elements"]


def haversine_km(first_lat: float, first_lng: float, second_lat: float, second_lng: float) -> float:
    """Great-circle distance between two coordinates.

    Args:
        first_lat: Latitude of the first point, in degrees.
        first_lng: Longitude of the first point, in degrees.
        second_lat: Latitude of the second point, in degrees.
        second_lng: Longitude of the second point, in degrees.

    Returns:
        The distance in kilometres.
    """
    lat_delta = math.radians(second_lat - first_lat)
    lng_delta = math.radians(second_lng - first_lng)
    half_chord = (
        math.sin(lat_delta / 2) ** 2
        + math.cos(math.radians(first_lat)) * math.cos(math.radians(second_lat)) * math.sin(lng_delta / 2) ** 2
    )
    return 2 * EARTH_RADIUS_KM * math.asin(math.sqrt(half_chord))


def to_theater_row(element: dict) -> dict | None:
    """Converts one Overpass element into a CSV row.

    Args:
        element: A raw Overpass element; nodes carry lat/lon directly, ways/relations carry a centre.

    Returns:
        The row as a dict keyed by CSV_COLUMNS, or None if the element should be skipped
        (no name, no coordinates, or a seasonal drive-in).
    """
    tags = element.get("tags", {})
    name = tags.get("name", "").strip()
    # Many drive-ins aren't tagged drive_in=yes in OSM, so the name is checked too.
    is_drive_in = tags.get("drive_in") == "yes" or DRIVE_IN_NAME_PATTERN.search(name)
    if not name or is_drive_in:
        return None

    coordinates = element if "lat" in element else element.get("center")
    if not coordinates:
        return None

    street_address = " ".join(
        part for part in (tags.get("addr:housenumber", ""), tags.get("addr:street", "")) if part
    )
    return {
        "osm_id": f"{element['type']}/{element['id']}",
        "name": name,
        "brand": tags.get("brand", ""),
        "address": street_address,
        "city": tags.get("addr:city", ""),
        "postal_code": tags.get("addr:postcode", ""),
        "lat": round(coordinates["lat"], 7),
        "lng": round(coordinates["lon"], 7),
    }


def drop_duplicates(theater_rows: list[dict]) -> list[dict]:
    """Removes cinemas mapped twice in OSM, keeping the entry with the most address detail.

    Args:
        theater_rows: Candidate rows, possibly containing the same cinema more than once.

    Returns:
        The rows with duplicates (same case-insensitive name within DUPLICATE_DISTANCE_KM) removed.
    """
    def address_completeness(theater_row: dict) -> int:
        return sum(1 for column in ("address", "city", "postal_code", "brand") if theater_row[column])

    kept_rows: list[dict] = []
    for candidate_row in sorted(theater_rows, key=address_completeness, reverse=True):
        is_duplicate = any(
            kept_row["name"].lower() == candidate_row["name"].lower()
            and haversine_km(kept_row["lat"], kept_row["lng"], candidate_row["lat"], candidate_row["lng"])
            < DUPLICATE_DISTANCE_KM
            for kept_row in kept_rows
        )
        if not is_duplicate:
            kept_rows.append(candidate_row)
    return kept_rows


def main() -> None:
    elements = fetch_overpass_elements()
    candidate_rows = [row for row in (to_theater_row(element) for element in elements) if row]
    theater_rows = sorted(drop_duplicates(candidate_rows), key=lambda theater_row: theater_row["osm_id"])

    os.makedirs(os.path.dirname(OUTPUT_CSV), exist_ok=True)
    with open(OUTPUT_CSV, "w", newline="", encoding="utf-8") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=CSV_COLUMNS)
        writer.writeheader()
        writer.writerows(theater_rows)

    print(
        f"{len(elements)} OSM elements -> {len(candidate_rows)} named non-drive-in "
        f"-> {len(theater_rows)} after de-duplication, written to {os.path.normpath(OUTPUT_CSV)}",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
