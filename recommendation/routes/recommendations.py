from fastapi import APIRouter, Query

from services.cold_start import get_cold_start_recommendations

router = APIRouter(prefix="/recommendations", tags=["recommendations"])


# FIX: this route trusts user_id as given, with no auth check of its own. That's only safe
# because the Spring Boot backend is now the sole caller (see RecommendationController), resolving
# user_id from the JWT before it ever reaches here rather than trusting client input. This service
# is not, and must not become, directly reachable from the browser.
@router.get("/{user_id}/cold-start")
def cold_start(user_id: int, limit: int = Query(default=10, ge=1, le=50)):
    return get_cold_start_recommendations(user_id, limit)
