import sys
import os

# Add the backend directory to sys.path so we can import app
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from sqlalchemy import create_engine
from app.config import settings
from app.database import Base
# Import all models to ensure they are registered with Base
from app.models import *

def update_db():
    print(f"Connecting to database: {settings.DB_HOST}")

    # Aiven MySQL requires ssl_ca
    connect_args = {
        "ssl": {
            "ca": settings.DATABASE_SSL_CA_PATH
        }
    }

    try:
        engine = create_engine(settings.DATABASE_URL.render_as_string(hide_password=False), connect_args=connect_args)
        
        # This will create any missing tables defined in the models
        print("Creating new tables for emergency calling system...")
        Base.metadata.create_all(bind=engine)
        print("Database update complete.")
    except Exception as e:
        print(f"Error updating database: {e}")
        sys.exit(1)

if __name__ == "__main__":
    update_db()
