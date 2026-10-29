
from pathlib import Path

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base

from app.config import settings


ssl_ca_path = Path(settings.DATABASE_SSL_CA_PATH)

engine_kwargs = {
    "pool_pre_ping": True,
    "pool_recycle": 3600,
    "echo": settings.DEBUG,
}

if "sqlite" not in settings.DATABASE_URL:
    engine_kwargs["connect_args"] = {
        "ssl": {
            "ca": str(ssl_ca_path),
        }
    }

engine = create_engine(
    settings.DATABASE_URL,
    **engine_kwargs
)

SessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=engine,
)

Base = declarative_base()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
