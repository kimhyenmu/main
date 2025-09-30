#!/usr/bin/env python3
"""
트랜잭션 격리 수준과 락 실습 종합 데모
"""

import sqlite3
import threading
import time
import random
from datetime import datetime
from database_setup import get_connection, DATABASE_FILE, setup_database, show_current_state

class ComprehensiveDemo:
    def __init__(self):
        self.results = []
        self.lock = threading.Lock()
    
    def log_result(self, message):
        """결과 로깅"""
        timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        with self.lock:
            self.results.append(f"[{timestamp}] {message}")
            print(f"[{timestamp}] {message}")
    
    def demo_isolation_levels(self):
        """격리 수준 데모"""
        print("\n" + "="*60)
        print("1. 트랜잭션 격리 수준 데모")
        print("="*60)
        
        print("\n--- Non-repeatable Read 시뮬레이션 ---")
        setup_database()
        
        def reader_transaction():
            with get_connection() as conn:
                cursor = conn.cursor()
                
                # 첫 번째 읽기
                cursor.execute("SELECT seat_number, reserved_by FROM seats WHERE seat_id = 1")
                result1 = cursor.fetchone()
                self.log_result(f"Reader: 첫 번째 읽기 - {result1}")
                
                time.sleep(2)  # 다른 트랜잭션이 데이터를 변경할 시간을 줌
                
                # 두 번째 읽기
                cursor.execute("SELECT seat_number, reserved_by FROM seats WHERE seat_id = 1")
                result2 = cursor.fetchone()
                self.log_result(f"Reader: 두 번째 읽기 - {result2}")
                
                if result1 != result2:
                    self.log_result("Reader: ⚠️ Non-repeatable Read 발생!")
        
        def writer_transaction():
            time.sleep(1)  # Reader가 먼저 읽도록 대기
            with get_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("""
                    UPDATE seats SET reserved_by = 'Writer_User'
                    WHERE seat_id = 1
                """)
                conn.commit()
                self.log_result("Writer: 좌석 1번 수정 완료")
        
        # 동시 실행
        threads = [
            threading.Thread(target=reader_transaction),
            threading.Thread(target=writer_transaction)
        ]
        
        for t in threads:
            t.start()
        for t in threads:
            t.join()
    
    def demo_phantom_read(self):
        """Phantom Read 데모"""
        print("\n--- Phantom Read 시뮬레이션 ---")
        setup_database()
        
        def counting_transaction():
            with get_connection() as conn:
                cursor = conn.cursor()
                
                # 첫 번째 카운트
                cursor.execute("SELECT COUNT(*) FROM seats WHERE is_available = TRUE")
                count1 = cursor.fetchone()[0]
                self.log_result(f"Counter: 첫 번째 카운트 - {count1}개")
                
                time.sleep(2)
                
                # 두 번째 카운트
                cursor.execute("SELECT COUNT(*) FROM seats WHERE is_available = TRUE")
                count2 = cursor.fetchone()[0]
                self.log_result(f"Counter: 두 번째 카운트 - {count2}개")
                
                if count1 != count2:
                    self.log_result("Counter: ⚠️ Phantom Read 발생!")
        
        def inserter_transaction():
            time.sleep(1)
            with get_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("""
                    INSERT INTO seats (seat_number, is_available, reserved_by, reservation_time, version)
                    VALUES ('PHANTOM', TRUE, NULL, NULL, 0)
                """)
                conn.commit()
                self.log_result("Inserter: 새로운 좌석 추가 완료")
        
        threads = [
            threading.Thread(target=counting_transaction),
            threading.Thread(target=inserter_transaction)
        ]
        
        for t in threads:
            t.start()
        for t in threads:
            t.join()
    
    def demo_lock_behavior(self):
        """락 동작 데모"""
        print("\n" + "="*60)
        print("2. 락 동작 데모")
        print("="*60)
        
        setup_database()
        
        def long_transaction():
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    cursor.execute("BEGIN IMMEDIATE")  # 즉시 배타적 락
                    self.log_result("LongTx: 배타적 락 획득")
                    
                    cursor.execute("UPDATE seats SET version = version + 1 WHERE seat_id = 1")
                    self.log_result("LongTx: 데이터 수정 중...")
                    
                    time.sleep(3)  # 긴 처리 시간 시뮬레이션
                    
                    conn.commit()
                    self.log_result("LongTx: 트랜잭션 완료 및 락 해제")
            except Exception as e:
                self.log_result(f"LongTx: 오류 - {e}")
        
        def waiting_transaction():
            time.sleep(0.5)  # LongTx가 먼저 락을 획득하도록 대기
            try:
                conn = sqlite3.connect(DATABASE_FILE, timeout=5.0)
                cursor = conn.cursor()
                self.log_result("WaitingTx: 락 대기 중...")
                
                cursor.execute("UPDATE seats SET version = version + 1 WHERE seat_id = 2")
                conn.commit()
                conn.close()
                self.log_result("WaitingTx: 작업 완료")
            except Exception as e:
                self.log_result(f"WaitingTx: 타임아웃 또는 오류 - {e}")
        
        threads = [
            threading.Thread(target=long_transaction),
            threading.Thread(target=waiting_transaction)
        ]
        
        for t in threads:
            t.start()
        for t in threads:
            t.join()
    
    def demo_seat_booking_race_condition(self):
        """좌석 예매 경합 조건 데모"""
        print("\n" + "="*60)
        print("3. 좌석 예매 시스템 동시성 제어 데모")
        print("="*60)
        
        # 시나리오 1: 경합 조건 발생 (안전하지 않은 방법)
        print("\n--- 시나리오 1: 경합 조건 발생 가능한 방법 ---")
        self.setup_last_seat()
        
        def unsafe_booking(user_name):
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    
                    # 1. 좌석 확인
                    cursor.execute("SELECT is_available FROM seats WHERE seat_id = 1")
                    available = cursor.fetchone()[0]
                    
                    if not available:
                        self.log_result(f"{user_name}: 좌석이 이미 예약됨")
                        return
                    
                    self.log_result(f"{user_name}: 좌석 확인 완료, 예매 처리 중...")
                    
                    # 2. 처리 시간 시뮬레이션 (여기서 경합 발생 가능)
                    time.sleep(random.uniform(0.05, 0.15))
                    
                    # 3. 예매 시도
                    cursor.execute("""
                        UPDATE seats SET is_available = FALSE, reserved_by = ?
                        WHERE seat_id = 1 AND is_available = TRUE
                    """, (user_name,))
                    
                    if cursor.rowcount > 0:
                        conn.commit()
                        self.log_result(f"{user_name}: ✅ 예매 성공!")
                    else:
                        self.log_result(f"{user_name}: ❌ 다른 사용자가 먼저 예매함")
                        
            except Exception as e:
                self.log_result(f"{user_name}: ❌ 오류 - {e}")
        
        # 3명이 동시에 마지막 좌석 예매 시도
        threads = []
        for i in range(1, 4):
            thread = threading.Thread(target=unsafe_booking, args=(f"User{i}",))
            threads.append(thread)
        
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        
        self.verify_booking_result("안전하지 않은 방법")
        
        # 시나리오 2: 비관적 락 사용 (안전한 방법)
        print("\n--- 시나리오 2: 비관적 락을 사용한 안전한 방법 ---")
        self.setup_last_seat()
        
        def safe_booking(user_name):
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    cursor.execute("BEGIN IMMEDIATE")  # 즉시 배타적 락
                    
                    self.log_result(f"{user_name}: 락 획득, 예매 처리 시작")
                    
                    # 좌석 확인
                    cursor.execute("SELECT is_available FROM seats WHERE seat_id = 1")
                    available = cursor.fetchone()[0]
                    
                    if not available:
                        conn.rollback()
                        self.log_result(f"{user_name}: ❌ 좌석이 이미 예약됨")
                        return
                    
                    # 처리 시간 시뮬레이션
                    time.sleep(random.uniform(0.05, 0.15))
                    
                    # 예매 처리
                    cursor.execute("""
                        UPDATE seats SET is_available = FALSE, reserved_by = ?
                        WHERE seat_id = 1
                    """, (user_name,))
                    
                    conn.commit()
                    self.log_result(f"{user_name}: ✅ 예매 성공!")
                    
            except Exception as e:
                self.log_result(f"{user_name}: ❌ 오류 - {e}")
        
        # 3명이 동시에 마지막 좌석 예매 시도
        threads = []
        for i in range(1, 4):
            thread = threading.Thread(target=safe_booking, args=(f"User{i}",))
            threads.append(thread)
        
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        
        self.verify_booking_result("비관적 락 사용")
    
    def setup_last_seat(self):
        """마지막 좌석 1개만 남은 시나리오 설정"""
        with get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM seats")
            cursor.execute("DELETE FROM reservations")
            
            # 좌석 1개만 생성
            cursor.execute("""
                INSERT INTO seats (seat_id, seat_number, is_available, reserved_by, reservation_time, version)
                VALUES (1, 'LAST_SEAT', TRUE, NULL, NULL, 0)
            """)
            
            conn.commit()
        
        self.log_result("마지막 좌석 1개 설정 완료")
    
    def verify_booking_result(self, test_name):
        """예매 결과 검증"""
        with get_connection() as conn:
            cursor = conn.cursor()
            
            cursor.execute("SELECT reserved_by FROM seats WHERE seat_id = 1")
            result = cursor.fetchone()
            reserved_by = result[0] if result else None
            
            cursor.execute("SELECT COUNT(*) FROM reservations")
            reservation_count = cursor.fetchone()[0]
            
            print(f"\n--- {test_name} 결과 검증 ---")
            print(f"좌석 예약자: {reserved_by}")
            print(f"예매 기록 수: {reservation_count}")
            
            if reserved_by and reservation_count <= 1:
                print("✅ 데이터 일관성 유지됨")
            elif reservation_count > 1:
                print("❌ 중복 예매 발생!")
            else:
                print("ℹ️ 예매되지 않음")
    
    def run_comprehensive_demo(self):
        """종합 데모 실행"""
        print("="*60)
        print("트랜잭션 격리 수준과 락 실습 종합 데모")
        print("="*60)
        
        self.demo_isolation_levels()
        time.sleep(1)
        
        self.demo_phantom_read()
        time.sleep(1)
        
        self.demo_lock_behavior()
        time.sleep(1)
        
        self.demo_seat_booking_race_condition()
        
        print("\n" + "="*60)
        print("실습 완료 - 주요 학습 내용")
        print("="*60)
        print("1. 격리 수준별 동시성 문제:")
        print("   - Non-repeatable Read: 같은 트랜잭션 내에서 같은 데이터를 읽었을 때 결과가 다름")
        print("   - Phantom Read: 범위 쿼리에서 새로운 행이 나타나거나 사라짐")
        print()
        print("2. 락의 동작:")
        print("   - 배타적 락: 다른 트랜잭션의 읽기/쓰기를 모두 차단")
        print("   - 락 대기: 락을 획득할 때까지 트랜잭션이 대기")
        print("   - 타임아웃: 일정 시간 후 락 획득 실패시 오류 발생")
        print()
        print("3. 동시성 제어 전략:")
        print("   - 경합 조건: 동시 접근시 예상치 못한 결과 발생 가능")
        print("   - 비관적 락: 미리 락을 획득하여 안전하게 처리")
        print("   - 낙관적 락: 충돌을 감지하고 재시도하는 방식")
        print()
        print("4. 좌석 예매 시스템에서의 교훈:")
        print("   - 중복 예매 방지를 위해 적절한 동시성 제어 필수")
        print("   - 트랜잭션 범위를 최소화하여 성능 향상")
        print("   - 데이터 일관성과 성능 사이의 균형점 찾기")

if __name__ == "__main__":
    demo = ComprehensiveDemo()
    demo.run_comprehensive_demo()