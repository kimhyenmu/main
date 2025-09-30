#!/usr/bin/env python3
"""
좌석 예매 시스템 - 동시성 제어 구현
다양한 동시성 제어 전략을 통해 데이터 일관성을 보장합니다.
"""

import sqlite3
import time
import threading
import random
from datetime import datetime
from database_setup import get_connection, DATABASE_FILE
from enum import Enum

class BookingStrategy(Enum):
    PESSIMISTIC_LOCK = "pessimistic"
    OPTIMISTIC_LOCK = "optimistic"
    SELECT_FOR_UPDATE = "select_for_update"
    SERIALIZABLE = "serializable"

class SeatBookingSystem:
    def __init__(self):
        self.results = []
        self.lock = threading.Lock()
        self.booking_attempts = 0
        self.successful_bookings = 0
        self.failed_bookings = 0
    
    def log_result(self, user_name, result):
        """스레드 안전한 결과 로깅"""
        timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        with self.lock:
            self.results.append(f"[{timestamp}] {user_name}: {result}")
            print(f"[{timestamp}] {user_name}: {result}")
    
    def setup_single_seat_scenario(self):
        """단일 좌석 시나리오 설정 - 좌석 1개만 남김"""
        with get_connection() as conn:
            cursor = conn.cursor()
            
            # 모든 좌석을 예약됨으로 변경
            cursor.execute("""
                UPDATE seats SET is_available = FALSE, reserved_by = 'SYSTEM'
                WHERE seat_id != 1
            """)
            
            # 좌석 1번만 예약 가능하게 설정
            cursor.execute("""
                UPDATE seats SET is_available = TRUE, reserved_by = NULL, version = 0
                WHERE seat_id = 1
            """)
            
            conn.commit()
            
        self.log_result("SYSTEM", "단일 좌석 시나리오 설정 완료 - 좌석 1번만 예약 가능")
    
    def book_seat_pessimistic_lock(self, user_name, seat_id):
        """비관적 락을 사용한 좌석 예매"""
        try:
            with get_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("BEGIN IMMEDIATE")  # 즉시 배타적 락 획득
                
                self.log_result(user_name, f"비관적 락으로 좌석 {seat_id} 예매 시도")
                
                # 좌석 상태 확인
                cursor.execute("""
                    SELECT is_available, reserved_by FROM seats WHERE seat_id = ?
                """, (seat_id,))
                
                result = cursor.fetchone()
                if not result:
                    conn.rollback()
                    return False, "좌석이 존재하지 않습니다."
                
                is_available, current_user = result
                
                if not is_available:
                    conn.rollback()
                    return False, f"좌석이 이미 {current_user}에 의해 예약되었습니다."
                
                # 예매 처리 시뮬레이션 (네트워크 지연 등)
                time.sleep(random.uniform(0.1, 0.3))
                
                # 좌석 예매
                cursor.execute("""
                    UPDATE seats 
                    SET is_available = FALSE, reserved_by = ?, reservation_time = CURRENT_TIMESTAMP
                    WHERE seat_id = ?
                """, (user_name, seat_id))
                
                # 예매 기록 추가
                cursor.execute("""
                    INSERT INTO reservations (seat_id, user_name, status)
                    VALUES (?, ?, 'CONFIRMED')
                """, (seat_id, user_name))
                
                conn.commit()
                return True, "예매가 성공적으로 완료되었습니다."
                
        except Exception as e:
            return False, f"예매 중 오류 발생: {e}"
    
    def book_seat_optimistic_lock(self, user_name, seat_id):
        """낙관적 락을 사용한 좌석 예매"""
        try:
            with get_connection() as conn:
                cursor = conn.cursor()
                
                self.log_result(user_name, f"낙관적 락으로 좌석 {seat_id} 예매 시도")
                
                # 현재 버전과 상태 조회
                cursor.execute("""
                    SELECT is_available, reserved_by, version FROM seats WHERE seat_id = ?
                """, (seat_id,))
                
                result = cursor.fetchone()
                if not result:
                    return False, "좌석이 존재하지 않습니다."
                
                is_available, current_user, version = result
                
                if not is_available:
                    return False, f"좌석이 이미 {current_user}에 의해 예약되었습니다."
                
                # 예매 처리 시뮬레이션
                time.sleep(random.uniform(0.1, 0.3))
                
                # 버전을 확인하면서 업데이트 (Compare-And-Swap)
                cursor.execute("""
                    UPDATE seats 
                    SET is_available = FALSE, reserved_by = ?, reservation_time = CURRENT_TIMESTAMP, version = version + 1
                    WHERE seat_id = ? AND version = ? AND is_available = TRUE
                """, (user_name, seat_id, version))
                
                if cursor.rowcount == 0:
                    return False, "다른 사용자가 먼저 예매했습니다. (버전 충돌)"
                
                # 예매 기록 추가
                cursor.execute("""
                    INSERT INTO reservations (seat_id, user_name, status)
                    VALUES (?, ?, 'CONFIRMED')
                """, (seat_id, user_name))
                
                conn.commit()
                return True, "예매가 성공적으로 완료되었습니다."
                
        except Exception as e:
            return False, f"예매 중 오류 발생: {e}"
    
    def book_seat_serializable(self, user_name, seat_id):
        """Serializable 격리 수준을 사용한 좌석 예매"""
        try:
            conn = sqlite3.connect(DATABASE_FILE, timeout=10.0)
            conn.isolation_level = None  # autocommit 모드 해제
            cursor = conn.cursor()
            
            cursor.execute("BEGIN")
            
            self.log_result(user_name, f"Serializable로 좌석 {seat_id} 예매 시도")
            
            # 좌석 상태 확인
            cursor.execute("""
                SELECT is_available, reserved_by FROM seats WHERE seat_id = ?
            """, (seat_id,))
            
            result = cursor.fetchone()
            if not result:
                conn.rollback()
                conn.close()
                return False, "좌석이 존재하지 않습니다."
            
            is_available, current_user = result
            
            if not is_available:
                conn.rollback()
                conn.close()
                return False, f"좌석이 이미 {current_user}에 의해 예약되었습니다."
            
            # 예매 처리 시뮬레이션
            time.sleep(random.uniform(0.1, 0.3))
            
            # 좌석 예매
            cursor.execute("""
                UPDATE seats 
                SET is_available = FALSE, reserved_by = ?, reservation_time = CURRENT_TIMESTAMP
                WHERE seat_id = ?
            """, (user_name, seat_id))
            
            # 예매 기록 추가
            cursor.execute("""
                INSERT INTO reservations (seat_id, user_name, status)
                VALUES (?, ?, 'CONFIRMED')
            """, (seat_id, user_name))
            
            cursor.execute("COMMIT")
            conn.close()
            return True, "예매가 성공적으로 완료되었습니다."
            
        except Exception as e:
            try:
                conn.rollback()
                conn.close()
            except:
                pass
            return False, f"예매 중 오류 발생: {e}"
    
    def simulate_concurrent_booking(self, strategy, num_users=5):
        """동시 예매 시뮬레이션"""
        self.booking_attempts = 0
        self.successful_bookings = 0
        self.failed_bookings = 0
        
        # 단일 좌석 시나리오 설정
        self.setup_single_seat_scenario()
        
        print(f"\n=== {strategy.value.upper()} 전략으로 동시 예매 테스트 ===")
        print(f"{num_users}명의 사용자가 동시에 좌석 1번을 예매 시도합니다.")
        
        def booking_attempt(user_id):
            user_name = f"User{user_id}"
            
            with self.lock:
                self.booking_attempts += 1
            
            # 전략에 따른 예매 시도
            if strategy == BookingStrategy.PESSIMISTIC_LOCK:
                success, message = self.book_seat_pessimistic_lock(user_name, 1)
            elif strategy == BookingStrategy.OPTIMISTIC_LOCK:
                success, message = self.book_seat_optimistic_lock(user_name, 1)
            elif strategy == BookingStrategy.SERIALIZABLE:
                success, message = self.book_seat_serializable(user_name, 1)
            else:
                success, message = self.book_seat_pessimistic_lock(user_name, 1)
            
            with self.lock:
                if success:
                    self.successful_bookings += 1
                    self.log_result(user_name, f"✅ 예매 성공: {message}")
                else:
                    self.failed_bookings += 1
                    self.log_result(user_name, f"❌ 예매 실패: {message}")
        
        # 동시 실행을 위한 스레드 생성
        threads = []
        for i in range(1, num_users + 1):
            thread = threading.Thread(target=booking_attempt, args=(i,))
            threads.append(thread)
        
        # 모든 스레드 거의 동시에 시작
        start_time = time.time()
        for thread in threads:
            thread.start()
            time.sleep(0.01)  # 아주 작은 지연으로 거의 동시 실행
        
        # 모든 스레드 완료 대기
        for thread in threads:
            thread.join()
        
        end_time = time.time()
        
        # 결과 요약
        print(f"\n--- {strategy.value.upper()} 전략 결과 요약 ---")
        print(f"총 예매 시도: {self.booking_attempts}회")
        print(f"성공한 예매: {self.successful_bookings}회")
        print(f"실패한 예매: {self.failed_bookings}회")
        print(f"실행 시간: {end_time - start_time:.2f}초")
        
        # 데이터 일관성 검증
        self.verify_data_consistency()
    
    def verify_data_consistency(self):
        """데이터 일관성 검증"""
        with get_connection() as conn:
            cursor = conn.cursor()
            
            # 예약된 좌석 수 확인
            cursor.execute("SELECT COUNT(*) FROM seats WHERE is_available = FALSE AND seat_id = 1")
            reserved_seats = cursor.fetchone()[0]
            
            # 예매 기록 수 확인
            cursor.execute("SELECT COUNT(*) FROM reservations WHERE seat_id = 1 AND status = 'CONFIRMED'")
            reservation_records = cursor.fetchone()[0]
            
            # 좌석 1번의 현재 상태 확인
            cursor.execute("SELECT seat_number, is_available, reserved_by FROM seats WHERE seat_id = 1")
            seat_status = cursor.fetchone()
            
            print(f"\n--- 데이터 일관성 검증 ---")
            print(f"예약된 좌석 수: {reserved_seats}")
            print(f"예매 기록 수: {reservation_records}")
            print(f"좌석 1번 상태: {seat_status}")
            
            if reserved_seats == 1 and reservation_records == 1:
                print("✅ 데이터 일관성 검증 통과")
            else:
                print("❌ 데이터 일관성 검증 실패")
    
    def run_all_booking_tests(self):
        """모든 예매 시스템 테스트 실행"""
        print("=" * 60)
        print("좌석 예매 시스템 동시성 테스트 시작")
        print("=" * 60)
        
        # 데이터베이스 초기화
        from database_setup import setup_database
        setup_database()
        
        # 각 전략별로 테스트 실행
        strategies = [
            BookingStrategy.PESSIMISTIC_LOCK,
            BookingStrategy.OPTIMISTIC_LOCK,
            BookingStrategy.SERIALIZABLE
        ]
        
        for strategy in strategies:
            self.simulate_concurrent_booking(strategy, num_users=5)
            time.sleep(2)  # 테스트 간 간격
        
        print("\n" + "=" * 60)
        print("모든 예매 시스템 테스트 완료")
        print("=" * 60)

if __name__ == "__main__":
    booking_system = SeatBookingSystem()
    booking_system.run_all_booking_tests()