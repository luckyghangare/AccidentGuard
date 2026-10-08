from app.database import SessionLocal
from app.models import User, Vehicle, GpsLocation, SensorReading, Accident, EmergencyContact, Notification
from app.utils.security import hash_password
from datetime import datetime

def seed_database():
    db = SessionLocal()
    try:
        from app.models.hospital import Hospital, HospitalEmergencyAlert
        
        # Check and seed Demo Hospital
        existing_hosp = db.query(Hospital).filter(Hospital.email == "apex.hospital@accidentguard.com").first()
        if not existing_hosp:
            demo_hosp = Hospital(
                name="Apex Trauma & Emergency Hospital",
                email="apex.hospital@accidentguard.com",
                phone_number="+91 712 2548899",
                password_hash=hash_password("Hospital@123"),
                address="Central Avenue, Wardha Road",
                city="Nagpur",
                latitude=21.1458,
                longitude=79.0882,
                total_ambulances=6,
                available_ambulances=5,
                is_active=True
            )
            db.add(demo_hosp)
            db.commit()
            db.refresh(demo_hosp)
            print(f"Created Demo Hospital ID: {demo_hosp.id}")

            # Connect existing accidents to hospital alerts
            accidents = db.query(Accident).all()
            for acc in accidents:
                alert = HospitalEmergencyAlert(
                    hospital_id=demo_hosp.id,
                    accident_id=acc.id,
                    status="PENDING" if acc.status == "emergency_sent" else ("DISPATCHED" if acc.status == "cancelled" else "ADMITTED"),
                    damage_severity="Severe Collision" if acc.impact_g >= 4.0 else "Moderate Impact",
                    notes="Crash telemetry alert."
                )
                db.add(alert)
            db.commit()
            print("Created Demo Hospital Emergency Alerts!")

        # Check if demo user already exists
        existing_user = db.query(User).filter(User.email == "demo@accidentguard.com").first()
        if existing_user:
            print("Demo user 'demo@accidentguard.com' already exists.")
            return

        print("Seeding database with demo user records...")


        # 1. Create Demo User
        demo_user = User(
            full_name="Lucky Ghangare",
            email="demo@accidentguard.com",
            phone_number="+91 9876543210",
            password_hash=hash_password("Demo@123"),
            is_active=True
        )
        db.add(demo_user)
        db.commit()
        db.refresh(demo_user)
        print(f"Created Demo User ID: {demo_user.id}")

        # 2. Create Demo Vehicle
        demo_vehicle = Vehicle(
            user_id=demo_user.id,
            name="Car 01",
            vehicle_number="MH-31-AB-1234",
            device_id="ESP32_DEV_01",
            status="connected",
            battery_level=82
        )
        db.add(demo_vehicle)
        db.commit()
        db.refresh(demo_vehicle)
        print(f"Created Demo Vehicle ID: {demo_vehicle.id}")

        # 3. Create Demo GPS Location
        demo_gps = GpsLocation(
            vehicle_id=demo_vehicle.id,
            latitude=21.1458,
            longitude=79.0882,
            speed=42.0
        )
        db.add(demo_gps)

        # 4. Create Demo Sensor Reading
        demo_sensor = SensorReading(
            vehicle_id=demo_vehicle.id,
            obstacle_distance=68.0,
            acceleration_g=1.12,
            gyroscope_rad_s=0.08,
            status="SAFE"
        )
        db.add(demo_sensor)

        # 5. Create Demo Accidents
        accident1 = Accident(
            vehicle_id=demo_vehicle.id,
            date_str="06 October 2026",
            time_str="10:32 PM",
            latitude=21.1458,
            longitude=79.0882,
            impact_g=4.8,
            status="emergency_sent"
        )
        accident2 = Accident(
            vehicle_id=demo_vehicle.id,
            date_str="28 September 2026",
            time_str="06:45 PM",
            latitude=21.1462,
            longitude=79.0911,
            impact_g=3.9,
            status="cancelled"
        )
        accident3 = Accident(
            vehicle_id=demo_vehicle.id,
            date_str="15 September 2026",
            time_str="09:12 AM",
            latitude=21.1490,
            longitude=79.0880,
            impact_g=2.1,
            status="false_alarm"
        )
        db.add_all([accident1, accident2, accident3])
        db.commit()

        # 6. Create Demo Emergency Contacts
        contact1 = EmergencyContact(
            user_id=demo_user.id,
            name="Rahul sha",
            phone_number="+91 9876543210",
            relationship_type="Brother",
            is_primary=True
        )
        contact2 = EmergencyContact(
            user_id=demo_user.id,
            name="Priya sha",
            phone_number="+91 9876543211",
            relationship_type="Mother",
            is_primary=False
        )
        contact3 = EmergencyContact(
            user_id=demo_user.id,
            name="Emergency Services",
            phone_number="112",
            relationship_type="Services",
            is_primary=False
        )
        db.add_all([contact1, contact2, contact3])
        db.commit()

        # 7. Create Demo Notification for Accident 1
        notif = Notification(
            accident_id=accident1.id,
            recipient_phone="+91 9876543210",
            message_body="ALERT: Crash detected on Car 01 at (21.1458, 79.0882). Impact: 4.8g",
            status="sent"
        )
        db.add(notif)
        db.commit()

        # 8. Create Demo Hospital
        from app.models.hospital import Hospital, HospitalEmergencyAlert
        demo_hosp = Hospital(
            name="Apex Trauma & Emergency Hospital",
            email="apex.hospital@accidentguard.com",
            phone_number="+91 712 2548899",
            password_hash=hash_password("Hospital@123"),
            address="Central Avenue, Wardha Road",
            city="Nagpur",
            latitude=21.1458,
            longitude=79.0882,
            total_ambulances=6,
            available_ambulances=5,
            is_active=True
        )
        db.add(demo_hosp)
        db.commit()
        db.refresh(demo_hosp)

        # 9. Create Hospital Emergency Alerts for demo accidents
        alert1 = HospitalEmergencyAlert(
            hospital_id=demo_hosp.id,
            accident_id=accident1.id,
            status="PENDING",
            damage_severity="Severe Collision",
            notes="Real-time crash signal from vehicle ESP32 sensor."
        )
        alert2 = HospitalEmergencyAlert(
            hospital_id=demo_hosp.id,
            accident_id=accident2.id,
            status="DISPATCHED",
            damage_severity="Moderate Impact",
            ambulance_number="MH-31-AMB-01",
            driver_name="Rajesh Sharma",
            driver_phone="+91 9876543210",
            eta_minutes=8,
            notes="Ambulance unit dispatched with paramedic team."
        )
        db.add_all([alert1, alert2])
        db.commit()

        print("Database seeded successfully with all demo records (including Hospital portal)!")
    except Exception as e:
        db.rollback()
        print(f"Error seeding database: {e}")
        raise e

    finally:
        db.close()

if __name__ == "__main__":
    seed_database()
