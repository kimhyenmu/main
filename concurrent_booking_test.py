#!/usr/bin/env python3
"""
간단한 동시 좌석 예매 테스트
"""

import sqlite3
import time
import threading
from datetime import datetime
from database_setup import get_connection, DATABASE_FILE

class ConcurrentBookingTest:
    def __init__(self):
        self.results = []
        self.lock = threading.Lock()
    
    def log_result(self, message):
        """결과 로깅"""
        timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        with self.lock:
            self.results.append(f"[{timestamp}] {message}")
            print(f"[{timestamp}] {message}")
    
    def setup_test_scenario(self):
        """테스트 시나리오 설정"""
        with get_connection() as conn:
            cursor = conn.cursor()
            
            # 데이터베이스 초기화
            cursor.execute("DELETE FROM seats")
            cursor.execute("DELETE FROM reservations")
            
            # 좌석 1개만 생성 (마지막 좌석)
            cursor.execute("""
                INSERT INTO seats (seat_id, seat_number, is_available, reserved_by, reservation_time, version)
                VALUES (1, 'LAST_SEAT', TRUE, NULL, NULL, 0)
            """)
            
            conn.commit()
            
        self.log_result("테스트 시나리오 설정: 좌석 1개만 생성됨")
    
    def book_seat_simple(self, user_name):
        """단순한 좌석 예매 (경합 조건 발생 가능)"""
        try:
            with get_connection() as conn:
                cursor = conn.cursor()
                
                # 1. 좌석 상태 확인
                cursor.execute("SELECT is_available FROM seats WHERE seat_id = 1")
                result = cursor.fetchone()
                
                if not result or not result[0]:
                    return False, "좌석이 이미 예약되었습니다."
                
                self.log_result(f"{user_name}: 좌석 확인 완료, 예매 진행 중...")
                
                # 2. 인위적인 지연 (실제 예매 처리 시간 시뮬레이션)
                time.sleep(0.1)
                
                # 3. 좌석 예매 시도
                cursor.execute("""
                    UPDATE seats 
                    SET is_available = FALSE, reserved_by = ?
                    WHERE seat_id = 1 AND is_available = TRUE
                """)
                
                if cursor.rowcount == 0:
                    return False, "다른 사용자가 먼저 예매했습니다."
                
                # 4. 예매 기록 추가
                cursor.execute("""
                    INSERT INTO reservations (seat_id, user_name, status)
                    VALUES (1, ?, 'CONFIRMED')
                """, (user_name,))
                
                conn.commit()
                return True, "예매 성공"
                
        except Exception as e:
            return False, f"오류: {e}"
    
    def book_seat_with_lock(self, user_name):
        """락을 사용한 안전한 좌석 예매"""
        try:
            with get_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("BEGIN IMMEDIATE")  # 즉시 배타적 락
                
                # 1. 좌석 상태 확인
                cursor.execute("SELECT is_available FROM seats WHERE seat_id = 1")
                result = cursor.fetchone()
                
                if not result or not result[0]:
                    conn.rollback()
                    return False, "좌석이 이미 예약되었습니다."
                
                self.log_result(f"{user_name}: 락 획득, 예매 진행 중...")
                
                # 2. 인위적인 지연
                time.sleep(0.1)
                
                # 3. 좌석 예매
                cursor.execute("""
                    UPDATE seats 
                    SET is_available = FALSE, reserved_by = ?
                    WHERE seat_id = 1
                """, (user_name,))
                
                # 4. 예매 기록 추가
                cursor.execute("""
                    INSERT INTO reservations (seat_id, user_name, status)
                    VALUES (1, ?, 'CONFIRMED')
                """, (user_name,))
                
                conn.commit()
                return True, "예매 성공"
                
        except Exception as e:
            return False, f"오류: {e}"
    
    def test_race_condition(self):
        """경합 조건 테스트 (락 없이)"""
        print("\n=== 경합 조건 테스트 (락 없이) ===")
        self.setup_test_scenario()
        
        success_count = 0
        results = {}
        
        def attempt_booking(user_id):
            nonlocal success_count
            user_name = f"User{user_id}"
            success, message = self.book_seat_simple(user_name)
            
            with self.lock:
                results[user_name] = (success, message)
                if success:
                    success_count += 1
                    self.log_result(f"{user_name}: ✅ {message}")
                else:
                    self.log_result(f"{user_name}: ❌ {message}")
        
        # 3명의 사용자가 동시에 예매 시도
        threads = []
        for i in range(1, 4):
            thread = threading.Thread(target=attempt_booking, args=(i,))
            threads.append(thread)
        
        # 거의 동시에 시작
        for thread in threads:
            thread.start()
        
        # 모든 스레드 완료 대기
        for thread in threads:
            thread.join()
        
        print(f"\n결과: {success_count}명이 예매에 성공했습니다.")
        self.verify_consistency("경합 조건 테스트")
    
    def test_with_pessimistic_lock(self):
        """비관적 락을 사용한 테스트"""
        print("\n=== 비관적 락 테스트 ===")
        self.setup_test_scenario()
        
        success_count = 0
        results = {}
        
        def attempt_booking_with_lock(user_id):
            nonlocal success_count
            user_name = f"User{user_id}"
            success, message = self.book_seat_with_lock(user_name)
            
            with self.lock:
                results[user_name] = (success, message)
                if success:
                    success_count += 1
                    self.log_result(f"{user_name}: ✅ {message}")
                else:
                    self.log_result(f"{user_name}: ❌ {message}")
        
        # 3명의 사용자가 동시에 예매 시도
        threads = []
        for i in range(1, 4):
            thread = threading.Thread(target=attempt_booking_with_lock, args=(i,))
            threads.append(thread)
        
        # 거의 동시에 시작
        for thread in threads:
            thread.start()
        
        # 모든 스레드 완료 대기
        for thread in threads:
            thread.join()
        
        print(f"\n결과: {success_count}명이 예매에 성공했습니다.")
        self.verify_consistency("비관적 락 테스트")
    
    def verify_consistency(self, test_name):
        """데이터 일관성 검증"""
        with get_connection() as conn:
            cursor = conn.cursor()
            
            # 예약된 좌석 수
            cursor.execute("SELECT COUNT(*) FROM seats WHERE is_available = FALSE")
            reserved_count = cursor.fetchone()[0]
            
            # 예매 기록 수
            cursor.execute("SELECT COUNT(*) FROM reservations WHERE status = 'CONFIRMED'")
            reservation_count = cursor.fetchone()[0]
            
            # 좌석 상태
            cursor.execute("SELECT reserved_by FROM seats WHERE seat_id = 1")
            reserved_by = cursor.fetchone()[0]
            
            print(f"\n--- {test_name} 일관성 검증 ---")
            print(f"예약된 좌석 수: {reserved_count}")
            print(f"예매 기록 수: {reservation_count}")
            print(f"좌석 예약자: {reserved_by}")
            
            if reserved_count == 1 and reservation_count == 1:
                print("✅ 데이터 일관성 유지됨")
            else:
                print("❌ 데이터 일관성 문제 발생!")
                if reservation_count > 1:
                    print("  → 중복 예매(Oversell) 발생!")
    
    def run_all_tests(self):
        """모든 테스트 실행"""
        print("=" * 50)
        print("동시 좌석 예매 테스트 시작")
        print("=" * 50)
        
        self.test_race_condition()
        time.sleep(1)
        
        self.test_with_pessimistic_lock()
        
        print("\n" + "=" * 50)
        print("모든 테스트 완료")
        print("=" * 50)

if __name__ == "__main__":
    test = ConcurrentBookingTest()
    test.run_all_tests()