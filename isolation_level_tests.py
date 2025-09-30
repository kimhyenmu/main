#!/usr/bin/env python3
"""
트랜잭션 격리 수준 테스트
SQLite는 기본적으로 SERIALIZABLE 격리 수준을 사용하지만,
다양한 시나리오를 통해 격리 수준의 개념을 학습합니다.
"""

import sqlite3
import time
import threading
from datetime import datetime
from database_setup import get_connection, DATABASE_FILE

class IsolationLevelTester:
    def __init__(self):
        self.results = []
    
    def log_result(self, test_name, result):
        """테스트 결과 로깅"""
        timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        self.results.append(f"[{timestamp}] {test_name}: {result}")
        print(f"[{timestamp}] {test_name}: {result}")
    
    def test_read_uncommitted_simulation(self):
        """Read Uncommitted 시뮬레이션 (SQLite에서는 지원하지 않지만 개념 학습용)"""
        print("\n=== Read Uncommitted 시뮬레이션 ===")
        print("SQLite는 Read Uncommitted를 지원하지 않지만, 개념을 시뮬레이션합니다.")
        
        # 트랜잭션 1: 데이터 수정 후 커밋하지 않음
        def transaction1():
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    # 좌석 A1을 예약으로 변경 (아직 커밋하지 않음)
                    cursor.execute("""
                        UPDATE seats SET is_available = FALSE, reserved_by = 'User1'
                        WHERE seat_id = 1
                    """)
                    self.log_result("Transaction1", "좌석 A1을 User1이 예약 (커밋 전)")
                    
                    # 2초 대기 (다른 트랜잭션이 읽을 시간을 줌)
                    time.sleep(2)
                    
                    # 롤백
                    conn.rollback()
                    self.log_result("Transaction1", "롤백 완료 - 예약 취소됨")
            except Exception as e:
                self.log_result("Transaction1", f"오류: {e}")
        
        # 트랜잭션 2: 데이터 읽기
        def transaction2():
            time.sleep(1)  # Transaction1이 먼저 실행되도록 대기
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    cursor.execute("SELECT seat_number, is_available, reserved_by FROM seats WHERE seat_id = 1")
                    result = cursor.fetchone()
                    self.log_result("Transaction2", f"좌석 A1 상태: {result}")
            except Exception as e:
                self.log_result("Transaction2", f"오류: {e}")
        
        # 두 트랜잭션 동시 실행
        t1 = threading.Thread(target=transaction1)
        t2 = threading.Thread(target=transaction2)
        
        t1.start()
        t2.start()
        
        t1.join()
        t2.join()
        
        print("SQLite에서는 트랜잭션이 커밋되기 전까지 다른 트랜잭션에서 변경사항을 볼 수 없습니다.")
    
    def test_read_committed_simulation(self):
        """Read Committed 시뮬레이션"""
        print("\n=== Read Committed 시뮬레이션 ===")
        
        def transaction1():
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    # 첫 번째 읽기
                    cursor.execute("SELECT seat_number, is_available, reserved_by FROM seats WHERE seat_id = 2")
                    result1 = cursor.fetchone()
                    self.log_result("Transaction1", f"첫 번째 읽기 - 좌석 A2: {result1}")
                    
                    # 3초 대기 (다른 트랜잭션이 데이터를 변경할 시간을 줌)
                    time.sleep(3)
                    
                    # 두 번째 읽기 (같은 트랜잭션 내에서)
                    cursor.execute("SELECT seat_number, is_available, reserved_by FROM seats WHERE seat_id = 2")
                    result2 = cursor.fetchone()
                    self.log_result("Transaction1", f"두 번째 읽기 - 좌석 A2: {result2}")
                    
                    if result1 != result2:
                        self.log_result("Transaction1", "Non-repeatable Read 발생!")
                    else:
                        self.log_result("Transaction1", "데이터 일관성 유지됨")
            except Exception as e:
                self.log_result("Transaction1", f"오류: {e}")
        
        def transaction2():
            time.sleep(1.5)  # Transaction1의 첫 번째 읽기 후에 실행
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    cursor.execute("""
                        UPDATE seats SET is_available = FALSE, reserved_by = 'User2'
                        WHERE seat_id = 2
                    """)
                    conn.commit()
                    self.log_result("Transaction2", "좌석 A2를 User2가 예약하고 커밋 완료")
            except Exception as e:
                self.log_result("Transaction2", f"오류: {e}")
        
        t1 = threading.Thread(target=transaction1)
        t2 = threading.Thread(target=transaction2)
        
        t1.start()
        t2.start()
        
        t1.join()
        t2.join()
    
    def test_phantom_read_simulation(self):
        """Phantom Read 시뮬레이션"""
        print("\n=== Phantom Read 시뮬레이션 ===")
        
        def transaction1():
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    # 첫 번째 범위 읽기
                    cursor.execute("SELECT COUNT(*) FROM seats WHERE is_available = TRUE")
                    count1 = cursor.fetchone()[0]
                    self.log_result("Transaction1", f"첫 번째 카운트 - 예약 가능한 좌석: {count1}개")
                    
                    # 3초 대기
                    time.sleep(3)
                    
                    # 두 번째 범위 읽기
                    cursor.execute("SELECT COUNT(*) FROM seats WHERE is_available = TRUE")
                    count2 = cursor.fetchone()[0]
                    self.log_result("Transaction1", f"두 번째 카운트 - 예약 가능한 좌석: {count2}개")
                    
                    if count1 != count2:
                        self.log_result("Transaction1", "Phantom Read 발생!")
                    else:
                        self.log_result("Transaction1", "데이터 일관성 유지됨")
            except Exception as e:
                self.log_result("Transaction1", f"오류: {e}")
        
        def transaction2():
            time.sleep(1.5)
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    # 새로운 좌석 추가
                    cursor.execute("""
                        INSERT INTO seats (seat_number, is_available, reserved_by, reservation_time, version)
                        VALUES ('C1', TRUE, NULL, NULL, 0)
                    """)
                    conn.commit()
                    self.log_result("Transaction2", "새로운 좌석 C1 추가 및 커밋 완료")
            except Exception as e:
                self.log_result("Transaction2", f"오류: {e}")
        
        t1 = threading.Thread(target=transaction1)
        t2 = threading.Thread(target=transaction2)
        
        t1.start()
        t2.start()
        
        t1.join()
        t2.join()
    
    def test_serializable_behavior(self):
        """Serializable 격리 수준 테스트 (SQLite 기본값)"""
        print("\n=== Serializable 격리 수준 테스트 ===")
        print("SQLite는 기본적으로 Serializable 격리 수준을 사용합니다.")
        
        def transaction1():
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    cursor.execute("BEGIN IMMEDIATE")  # 즉시 배타적 락 획득
                    
                    cursor.execute("SELECT seat_number, is_available FROM seats WHERE seat_id = 3")
                    result = cursor.fetchone()
                    self.log_result("Transaction1", f"좌석 A3 상태: {result}")
                    
                    time.sleep(2)  # 락을 유지한 채로 대기
                    
                    cursor.execute("""
                        UPDATE seats SET is_available = FALSE, reserved_by = 'User1'
                        WHERE seat_id = 3
                    """)
                    conn.commit()
                    self.log_result("Transaction1", "좌석 A3 예약 완료")
            except Exception as e:
                self.log_result("Transaction1", f"오류: {e}")
        
        def transaction2():
            time.sleep(0.5)  # Transaction1이 먼저 락을 획득하도록 대기
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    self.log_result("Transaction2", "트랜잭션 시작, 락 대기 중...")
                    
                    cursor.execute("BEGIN IMMEDIATE")  # 락 대기
                    cursor.execute("""
                        UPDATE seats SET is_available = FALSE, reserved_by = 'User2'
                        WHERE seat_id = 3
                    """)
                    conn.commit()
                    self.log_result("Transaction2", "좌석 A3 예약 시도 완료")
            except Exception as e:
                self.log_result("Transaction2", f"오류 (예상됨): {e}")
        
        t1 = threading.Thread(target=transaction1)
        t2 = threading.Thread(target=transaction2)
        
        t1.start()
        t2.start()
        
        t1.join()
        t2.join()
    
    def run_all_tests(self):
        """모든 격리 수준 테스트 실행"""
        print("=" * 60)
        print("트랜잭션 격리 수준 테스트 시작")
        print("=" * 60)
        
        # 데이터베이스 초기화
        from database_setup import setup_database
        setup_database()
        
        self.test_read_uncommitted_simulation()
        time.sleep(1)
        
        self.test_read_committed_simulation()
        time.sleep(1)
        
        self.test_phantom_read_simulation()
        time.sleep(1)
        
        self.test_serializable_behavior()
        
        print("\n" + "=" * 60)
        print("테스트 완료")
        print("=" * 60)
        
        # 최종 상태 확인
        from database_setup import show_current_state
        show_current_state()

if __name__ == "__main__":
    tester = IsolationLevelTester()
    tester.run_all_tests()