# AccidentGuard Backend API

Accident Avoidance System with Crash Detection and GPS Notification - Backend Service

## Technologies Used
- Python 3.10+
- FastAPI
- Uvicorn
- SQLAlchemy
- MySQL / PyMySQL
- Pydantic v2
- JWT Authentication

## Project Structure
```text
AccidentGuard/backend/
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── config.py
│   ├── database.py
│   ├── models/
│   ├── schemas/
│   ├── routers/
│   ├── services/
│   ├── repositories/
│   └── utils/
├── requirements.txt
└── README.md
```

## Setup & Running
1. Create virtual environment:
   `python -m venv venv`
2. Activate virtual environment:
   - Windows: `venv\Scripts\activate`
   - Linux/Mac: `source venv/bin/activate`
3. Install dependencies:
   `pip install -r requirements.txt`
4. Run server:
   `uvicorn app.main:app --reload`
