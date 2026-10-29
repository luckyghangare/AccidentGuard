
import pymysql
from app.config import settings

def update_schema():
    print(f"Connecting to {settings.DB_HOST}...")
    connection = pymysql.connect(
        host=settings.DB_HOST,
        user=settings.DB_USER,
        password=settings.DB_PASSWORD,
        database=settings.DB_NAME,
        port=settings.DB_PORT,
        ssl={'ca': settings.DATABASE_SSL_CA_PATH}
    )
    
    try:
        with connection.cursor() as cursor:
            # Check if column exists
            cursor.execute("SHOW COLUMNS FROM users LIKE 'fcm_token'")
            result = cursor.fetchone()
            
            if not result:
                print("Adding fcm_token column to users table...")
                cursor.execute("ALTER TABLE users ADD COLUMN fcm_token VARCHAR(255) NULL AFTER password_hash")
                connection.commit()
                print("Column added successfully.")
            else:
                print("Column fcm_token already exists.")
                
    finally:
        connection.close()

if __name__ == "__main__":
    update_schema()
