from contextlib import asynccontextmanager

from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from grpc_server import create_server


@asynccontextmanager
async def lifespan(app: FastAPI):
    server = create_server()
    server.start()
    yield
    server.stop(grace=5).wait()


app = FastAPI(title="recommendation", lifespan=lifespan)
Instrumentator(excluded_handlers=["/metrics"]).instrument(app).expose(app)


@app.get("/ping")
def ping():
    return {"status": "ok"}
