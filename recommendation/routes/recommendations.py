from fastapi import APIRouter

from services.cold_start import get_cold_start_recommendations

router = APIRouter(prefix="/recommendations", tags=["recommendations"])


@router.get("/{user_id}/cold-start")
def cold_start(user_id: int, limit: int = 10):
    return get_cold_start_recommendations(user_id, limit)
