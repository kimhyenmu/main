#!/usr/bin/env python3
"""
테이블 락 vs 행 단위 락 비교 실험
SQLite의 락 메커니즘을 통해 락의 동작 방식을 학습합니다.
"""

import sqlite3
import time
import threading
from datetime import datetime
from database_setup import get_connection, DATABASE_FILE

class LockComparison:
    def __init__(self):
        self.results = []
        self.lock = threading.Lock()
    
    def log_result(self, test_name, result):
        """스레드 안전한 결과 로깅"""
        timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        with self.lock:
            self.results.append(f"[{timestamp}] {test_name}: {result}")
            print(f"[{timestamp}] {test_name}: {result}")
    
    def test_table_level_lock(self):
        """테이블 레벨 락 테스트"""
        print("\n=== 테이블 레벨 락 테스트 ===")
        print("IMMEDIATE 트랜잭션을 사용하여 테이블 전체에 락을 걸어봅니다.")
        
        def transaction1():
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    cursor.execute("BEGIN IMMEDIATE")  # 즉시 배타적 락
                    self.log_result("Transaction1", "테이블 레벨 락 획득")
                    
                    # 좌석 1번 수정
                    cursor.execute("""
                        UPDATE seats SET reserved_by = 'TableLock_User1'
                        WHERE seat_id = 1
                    """)
                    self.log_result("Transaction1", "좌석 1번 수정 중...")
                    
                    time.sleep(3)  # 락을 유지한 채로 대기
                    
                    conn.commit()
                    self.log_result("Transaction1", "테이블 레벨 락 해제 및 커밋")
            except Exception as e:
                self.log_result("Transaction1", f"오류: {e}")
        
        def transaction2():
            time.sleep(0.5)  # Transaction1이 먼저 락을 획득하도록 대기
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    self.log_result("Transaction2", "좌석 4번 수정 시도 (다른 행)")
                    
                    # 다른 행(좌석 4번)을 수정하려고 시도
                    cursor.execute("""
                        UPDATE seats SET reserved_by = 'TableLock_User2'
                        WHERE seat_id = 4
                    """)
                    conn.commit()
                    self.log_result("Transaction2", "좌석 4번 수정 완료")
            except Exception as e:
                self.log_result("Transaction2", f"오류 (예상됨): {e}")
        
        def transaction3():
            time.sleep(1)  # Transaction1이 먼저 락을 획득하도록 대기
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    self.log_result("Transaction3", "읽기 전용 쿼리 시도")
                    
                    # 읽기 전용 쿼리
                    cursor.execute("SELECT COUNT(*) FROM seats WHERE is_available = TRUE")
                    count = cursor.fetchone()[0]
                    self.log_result("Transaction3", f"읽기 성공: 예약 가능한 좌석 {count}개")
            except Exception as e:
                self.log_result("Transaction3", f"오류: {e}")
        
        threads = [
            threading.Thread(target=transaction1),
            threading.Thread(target=transaction2),
            threading.Thread(target=transaction3)
        ]
        
        for t in threads:
            t.start()
        
        for t in threads:
            t.join()
    
    def test_row_level_lock_simulation(self):
        """행 레벨 락 시뮬레이션 (SQLite는 실제로는 테이블 락을 사용)"""
        print("\n=== 행 레벨 락 시뮬레이션 ===")
        print("SQLite는 실제로는 행 레벨 락을 지원하지 않지만, 개념을 시뮬레이션합니다.")
        
        # 데이터베이스 초기화
        from database_setup import setup_database
        setup_database()
        
        def transaction1():
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    self.log_result("Transaction1", "좌석 1번에 대한 작업 시작")
                    
                    # SELECT ... WHERE를 사용하여 특정 행만 대상으로 함
                    cursor.execute("""
                        UPDATE seats SET reserved_by = 'RowLock_User1'
                        WHERE seat_id = 1
                    """)
                    
                    time.sleep(2)  # 작업 시간 시뮬레이션
                    
                    conn.commit()
                    self.log_result("Transaction1", "좌석 1번 작업 완료")
            except Exception as e:
                self.log_result("Transaction1", f"오류: {e}")
        
        def transaction2():
            time.sleep(0.2)  # 거의 동시에 시작
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    self.log_result("Transaction2", "좌석 2번에 대한 작업 시작")
                    
                    # 다른 행에 대한 작업
                    cursor.execute("""
                        UPDATE seats SET reserved_by = 'RowLock_User2'
                        WHERE seat_id = 2
                    """)
                    
                    time.sleep(1)
                    
                    conn.commit()
                    self.log_result("Transaction2", "좌석 2번 작업 완료")
            except Exception as e:
                self.log_result("Transaction2", f"오류: {e}")
        
        def transaction3():
            time.sleep(0.3)  # 거의 동시에 시작
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    self.log_result("Transaction3", "좌석 1번에 대한 작업 시도 (충돌 예상)")
                    
                    # 같은 행에 대한 작업 (충돌)
                    cursor.execute("""
                        UPDATE seats SET reserved_by = 'RowLock_User3'
                        WHERE seat_id = 1
                    """)
                    
                    conn.commit()
                    self.log_result("Transaction3", "좌석 1번 작업 완료")
            except Exception as e:
                self.log_result("Transaction3", f"오류 (예상됨): {e}")
        
        threads = [
            threading.Thread(target=transaction1),
            threading.Thread(target=transaction2),
            threading.Thread(target=transaction3)
        ]
        
        for t in threads:
            t.start()
        
        for t in threads:
            t.join()
    
    def test_lock_timeout(self):
        """락 타임아웃 테스트"""
        print("\n=== 락 타임아웃 테스트 ===")
        
        def long_transaction():
            try:
                # 타임아웃을 5초로 설정한 연결
                conn = sqlite3.connect(DATABASE_FILE, timeout=5.0)
                cursor = conn.cursor()
                cursor.execute("BEGIN IMMEDIATE")
                self.log_result("LongTransaction", "긴 트랜잭션 시작, 락 획득")
                
                time.sleep(7)  # 7초 대기 (타임아웃보다 길게)
                
                cursor.execute("""
                    UPDATE seats SET reserved_by = 'LongTransaction'
                    WHERE seat_id = 3
                """)
                conn.commit()
                conn.close()
                self.log_result("LongTransaction", "긴 트랜잭션 완료")
            except Exception as e:
                self.log_result("LongTransaction", f"오류: {e}")
        
        def quick_transaction():
            time.sleep(1)  # 긴 트랜잭션이 먼저 시작하도록 대기
            try:
                # 타임아웃을 3초로 설정한 연결
                conn = sqlite3.connect(DATABASE_FILE, timeout=3.0)
                cursor = conn.cursor()
                self.log_result("QuickTransaction", "빠른 트랜잭션 시작, 락 대기...")
                
                cursor.execute("""
                    UPDATE seats SET reserved_by = 'QuickTransaction'
                    WHERE seat_id = 3
                """)
                conn.commit()
                conn.close()
                self.log_result("QuickTransaction", "빠른 트랜잭션 완료")
            except Exception as e:
                self.log_result("QuickTransaction", f"타임아웃 오류 (예상됨): {e}")
        
        threads = [
            threading.Thread(target=long_transaction),
            threading.Thread(target=quick_transaction)
        ]
        
        for t in threads:
            t.start()
        
        for t in threads:
            t.join()
    
    def test_deadlock_simulation(self):
        """데드락 시뮬레이션 (SQLite에서는 발생하기 어려움)"""
        print("\n=== 데드락 시뮬레이션 ===")
        print("SQLite는 단순한 락 메커니즘으로 인해 데드락이 발생하기 어렵습니다.")
        
        def transaction1():
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    cursor.execute("BEGIN IMMEDIATE")
                    self.log_result("DeadlockTest1", "트랜잭션 1 시작")
                    
                    # 리소스 A 접근
                    cursor.execute("UPDATE seats SET version = version + 1 WHERE seat_id = 1")
                    self.log_result("DeadlockTest1", "리소스 A(좌석1) 락 획득")
                    
                    time.sleep(2)
                    
                    # 리소스 B 접근 시도
                    cursor.execute("UPDATE seats SET version = version + 1 WHERE seat_id = 2")
                    self.log_result("DeadlockTest1", "리소스 B(좌석2) 락 획득")
                    
                    conn.commit()
                    self.log_result("DeadlockTest1", "트랜잭션 1 완료")
            except Exception as e:
                self.log_result("DeadlockTest1", f"오류: {e}")
        
        def transaction2():
            time.sleep(0.5)
            try:
                with get_connection() as conn:
                    cursor = conn.cursor()
                    self.log_result("DeadlockTest2", "트랜잭션 2 시작, 락 대기...")
                    
                    # SQLite에서는 이미 락이 걸려있으므로 대기하게 됨
                    cursor.execute("UPDATE seats SET version = version + 1 WHERE seat_id = 2")
                    self.log_result("DeadlockTest2", "리소스 B(좌석2) 락 획득")
                    
                    conn.commit()
                    self.log_result("DeadlockTest2", "트랜잭션 2 완료")
            except Exception as e:
                self.log_result("DeadlockTest2", f"오류: {e}")
        
        threads = [
            threading.Thread(target=transaction1),
            threading.Thread(target=transaction2)
        ]
        
        for t in threads:
            t.start()
        
        for t in threads:
            t.join()
    
    def run_all_tests(self):
        """모든 락 테스트 실행"""
        print("=" * 60)
        print("락 비교 실험 시작")
        print("=" * 60)
        
        # 데이터베이스 초기화
        from database_setup import setup_database
        setup_database()
        
        self.test_table_level_lock()
        time.sleep(1)
        
        self.test_row_level_lock_simulation()
        time.sleep(1)
        
        self.test_lock_timeout()
        time.sleep(1)
        
        self.test_deadlock_simulation()
        
        print("\n" + "=" * 60)
        print("락 테스트 완료")
        print("=" * 60)
        
        # 최종 상태 확인
        from database_setup import show_current_state
        show_current_state()

if __name__ == "__main__":
    tester = LockComparison()
    tester.run_all_tests()