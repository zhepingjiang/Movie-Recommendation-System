from fastapi import FastAPI

app = FastAPI(title="recommendation")


@app.get("/ping")
def ping():
    return {"status": "ok"}
