from contextlib import asynccontextmanager

from fastapi import FastAPI

from grpc_server import create_server


@asynccontextmanager
async def lifespan(app: FastAPI):
    server = create_server()
    server.start()
    yield
    server.stop(grace=5).wait()


app = FastAPI(title="recommendation", lifespan=lifespan)


@app.get("/ping")
def ping():
    return {"status": "ok"}
