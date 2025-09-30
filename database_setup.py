#!/usr/bin/env python3
"""
데이터베이스 초기 설정 및 테이블 생성
"""

import sqlite3
import os
from contextlib import contextmanager

DATABASE_FILE = 'transaction_lab.db'

def setup_database():
    """데이터베이스 및 테이블 초기화"""
    # 기존 데이터베이스 파일 삭제
    if os.path.exists(DATABASE_FILE):
        os.remove(DATABASE_FILE)
    
    conn = sqlite3.connect(DATABASE_FILE)
    cursor = conn.cursor()
    
    # 좌석 테이블 생성
    cursor.execute('''
        CREATE TABLE seats (
            seat_id INTEGER PRIMARY KEY,
            seat_number VARCHAR(10) UNIQUE NOT NULL,
            is_available BOOLEAN DEFAULT TRUE,
            reserved_by VARCHAR(100),
            reservation_time TIMESTAMP,
            version INTEGER DEFAULT 0
        )
    ''')
    
    # 예매 기록 테이블 생성
    cursor.execute('''
        CREATE TABLE reservations (
            reservation_id INTEGER PRIMARY KEY AUTOINCREMENT,
            seat_id INTEGER,
            user_name VARCHAR(100),
            reservation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            status VARCHAR(20) DEFAULT 'CONFIRMED',
            FOREIGN KEY (seat_id) REFERENCES seats(seat_id)
        )
    ''')
    
    # 트랜잭션 로그 테이블 (실험 관찰용)
    cursor.execute('''
        CREATE TABLE transaction_log (
            log_id INTEGER PRIMARY KEY AUTOINCREMENT,
            transaction_name VARCHAR(100),
            operation VARCHAR(50),
            seat_id INTEGER,
            timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            details TEXT
        )
    ''')
    
    # 테스트용 좌석 데이터 삽입
    seats_data = [
        (1, 'A1', True, None, None, 0),
        (2, 'A2', True, None, None, 0),
        (3, 'A3', True, None, None, 0),
        (4, 'B1', True, None, None, 0),
        (5, 'B2', True, None, None, 0),
    ]
    
    cursor.executemany('''
        INSERT INTO seats (seat_id, seat_number, is_available, reserved_by, reservation_time, version)
        VALUES (?, ?, ?, ?, ?, ?)
    ''', seats_data)
    
    conn.commit()
    conn.close()
    
    print(f"데이터베이스 '{DATABASE_FILE}' 설정 완료")
    print("생성된 테이블: seats, reservations, transaction_log")
    print("초기 좌석 5개 생성 완료")

@contextmanager
def get_connection(isolation_level=None):
    """데이터베이스 연결 컨텍스트 매니저"""
    conn = sqlite3.connect(DATABASE_FILE, timeout=30.0)
    if isolation_level is not None:
        conn.isolation_level = isolation_level
    try:
        yield conn
    finally:
        conn.close()

def show_current_state():
    """현재 좌석 상태 출력"""
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute('''
            SELECT seat_id, seat_number, is_available, reserved_by, reservation_time
            FROM seats ORDER BY seat_id
        ''')
        
        print("\n=== 현재 좌석 상태 ===")
        print("좌석ID | 좌석번호 | 예약가능 | 예약자 | 예약시간")
        print("-" * 60)
        
        for row in cursor.fetchall():
            seat_id, seat_number, is_available, reserved_by, reservation_time = row
            available_str = "가능" if is_available else "불가능"
            reserved_by = reserved_by or "-"
            reservation_time = reservation_time or "-"
            print(f"{seat_id:6} | {seat_number:8} | {available_str:8} | {reserved_by:10} | {reservation_time}")

if __name__ == "__main__":
    setup_database()
    show_current_state()