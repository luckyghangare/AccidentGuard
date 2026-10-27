
from app.database import Base, engine
import app.models  # Register all SQLAlchemy models


def init_tables():
    """Create missing SQLAlchemy tables in the configured database."""
    print(f"Database: {engine.url.database}")
    print("Checking and creating missing tables...")

    Base.metadata.create_all(bind=engine)

    print("Table initialization completed successfully.")


if __name__ == "__main__":
    init_tables()
