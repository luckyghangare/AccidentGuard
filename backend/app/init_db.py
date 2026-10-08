import pymysql
from app.config import settings
from app.database import Base, engine
import app.models  # Ensures all models are registered with Base.metadata

def create_database_if_not_exists():
    """Connects to MySQL server root and creates accidentguard_db database if it doesn't exist."""
    print(f"Connecting to MySQL server at {settings.DB_HOST}:{settings.DB_PORT}...")
    try:
        connection = pymysql.connect(
            host=settings.DB_HOST,
            port=settings.DB_PORT,
            user=settings.DB_USER,
            password=settings.DB_PASSWORD,
            charset='utf8mb4'
        )
        with connection.cursor() as cursor:
            sql = f"CREATE DATABASE IF NOT EXISTS `{settings.DB_NAME}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
            cursor.execute(sql)
            print(f"Database '{settings.DB_NAME}' created or verified successfully!")
        connection.commit()
        connection.close()
    except Exception as e:
        print(f"Error creating database '{settings.DB_NAME}': {e}")
        raise e

def init_tables():
    """Creates all SQLAlchemy model tables in MySQL database."""
    print("Creating database tables...")
    Base.metadata.create_all(bind=engine)
    print("All database tables created successfully!")

if __name__ == "__main__":
    create_database_if_not_exists()
    init_tables()
