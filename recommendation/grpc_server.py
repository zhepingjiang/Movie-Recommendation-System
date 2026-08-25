"""gRPC server exposing cold-start recommendations to the Spring Boot backend.

FIX: this is now the only way to reach cold-start recommendations -- the old REST route
(routes/recommendations.py) trusted user_id as given, with no auth of its own, and has been
removed. The backend is the sole caller and resolves user_id from the JWT before calling here.
"""

from concurrent import futures

import grpc

from generated import recommendation_pb2, recommendation_pb2_grpc
from services.cold_start import get_cold_start_recommendations

MIN_LIMIT = 1
MAX_LIMIT = 50


class RecommendationServicer(recommendation_pb2_grpc.RecommendationServiceServicer):
    def GetColdStartRecommendations(self, request, context):
        if not MIN_LIMIT <= request.limit <= MAX_LIMIT:
            context.abort(
                grpc.StatusCode.INVALID_ARGUMENT,
                f"limit must be between {MIN_LIMIT} and {MAX_LIMIT}",
            )

        results = get_cold_start_recommendations(request.user_id, request.limit)
        return recommendation_pb2.ColdStartResponse(
            recommendations=[
                recommendation_pb2.MovieRecommendation(
                    id=movie["id"],
                    title=movie["title"],
                    poster_url=movie["poster_url"],
                    average_rating=movie["average_rating"],
                    genres=movie["genres"],
                    match_score=movie["match_score"],
                )
                for movie in results
            ]
        )


def create_server(port: int = 50051) -> grpc.Server:
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    recommendation_pb2_grpc.add_RecommendationServiceServicer_to_server(RecommendationServicer(), server)
    server.add_insecure_port(f"[::]:{port}")
    return server
