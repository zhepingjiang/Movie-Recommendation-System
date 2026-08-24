from fastapi import FastAPI

from routes import recommendations

app = FastAPI(title="recommendation")
app.include_router(recommendations.router)


@app.get("/ping")
def ping():
    return {"status": "ok"}
