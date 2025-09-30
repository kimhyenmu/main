#!/usr/bin/env python3
"""
매우 간단한 동시성 테스트
"""

import sqlite3
import threading
import time
from database_setup import DATABASE_FILE

def test_concurrent_booking():
    print("=== 간단한 동시성 테스트 ===")
    
    # 테스트용 데이터베이스 설정
    conn = sqlite3.connect(DATABASE_FILE)
    cursor = conn.cursor()
    
    # 테스트 좌석 초기화
    cursor.execute("DELETE FROM seats WHERE seat_id = 999")
    cursor.execute("""
        INSERT INTO seats (seat_id, seat_number, is_available, reserved_by, reservation_time, version)
        VALUES (999, 'TEST', TRUE, NULL, NULL, 0)
    """)
    conn.commit()
    conn.close()
    
    results = []
    
    def book_seat(user_name):
        try:
            conn = sqlite3.connect(DATABASE_FILE, timeout=5.0)
            cursor = conn.cursor()
            
            print(f"{user_name}: 예매 시도 시작")
            
            # 좌석 상태 확인
            cursor.execute("SELECT is_available FROM seats WHERE seat_id = 999")
            available = cursor.fetchone()[0]
            
            if not available:
                results.append(f"{user_name}: 이미 예약됨")
                conn.close()
                return
            
            print(f"{user_name}: 좌석 확인 완료, 예매 처리 중...")
            time.sleep(0.1)  # 처리 시간 시뮬레이션
            
            # 좌석 예매
            cursor.execute("""
                UPDATE seats SET is_available = FALSE, reserved_by = ?
                WHERE seat_id = 999 AND is_available = TRUE
            """, (user_name,))
            
            if cursor.rowcount > 0:
                conn.commit()
                results.append(f"{user_name}: 예매 성공!")
                print(f"{user_name}: 예매 성공!")
            else:
                results.append(f"{user_name}: 다른 사용자가 먼저 예매함")
                print(f"{user_name}: 다른 사용자가 먼저 예매함")
            
            conn.close()
            
        except Exception as e:
            results.append(f"{user_name}: 오류 - {e}")
            print(f"{user_name}: 오류 - {e}")
    
    # 2명의 사용자가 동시에 예매 시도
    threads = []
    for i in range(1, 3):
        thread = threading.Thread(target=book_seat, args=(f"User{i}",))
        threads.append(thread)
    
    # 스레드 시작
    for thread in threads:
        thread.start()
    
    # 완료 대기
    for thread in threads:
        thread.join()
    
    print("\n=== 테스트 결과 ===")
    for result in results:
        print(result)
    
    # 최종 상태 확인
    conn = sqlite3.connect(DATABASE_FILE)
    cursor = conn.cursor()
    cursor.execute("SELECT reserved_by FROM seats WHERE seat_id = 999")
    final_result = cursor.fetchone()
    print(f"최종 예약자: {final_result[0] if final_result else 'None'}")
    conn.close()

if __name__ == "__main__":
    test_concurrent_booking()