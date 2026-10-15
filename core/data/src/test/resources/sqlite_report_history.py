"""Execute Kotlin-exported history DDL and statements; no Android runtime or permission claim."""
import re
import sqlite3
import sys
from pathlib import Path

source_path, mode = Path(sys.argv[1]), sys.argv[2]
directory = Path(__file__).parent
source = source_path.read_text(encoding="utf-8")
on_create = source.split("override fun onCreate", 1)[1].split("override fun onUpgrade", 1)[0]
base = re.findall(r'"""(.*?)"""\.trimIndent\(\)', on_create, re.S)
assert len(base) == 2 and "db.execSQL(ReportHistorySql.CREATE_TABLE)" in on_create
photos = re.findall(r'"""(.*?)"""\.trimIndent\(\)', source.split("val PHOTO_COLUMNS = listOf(", 1)[1], re.S)
assert len(photos) == 5
version = int(re.search(r"const val VERSION = (\d+)", source).group(1))
assert version == 4
legacy_sql = re.sub(r'\$\{PHOTO_COLUMNS\.joinToString\([^}]*\)\}\s*,', "", base[0])
current_sql = re.sub(r'\$\{PHOTO_COLUMNS\.joinToString\([^}]*\)\}', ",\n".join(photos), base[0])
assert "${" not in legacy_sql and "${" not in current_sql
create, insert, head, operation, cas, first_page, older_page, activity_index = [
    (directory / f"statement{i}.sql").read_text(encoding="utf-8") for i in range(8)
]
identity = "00000000-0000-0000-0000-000000000001"
actor = "00000000-0000-0000-0000-000000000002"
new_id = "00000000-0000-0000-0000-000000000003"

def connection(name="main"):
    db = sqlite3.connect(directory / f"{name}.db", isolation_level=None)
    db.execute("PRAGMA foreign_keys = ON")
    return db

def prepare(db, old_version=None):
    old_version = version if old_version is None else old_version
    db.execute(legacy_sql if old_version == 1 else current_sql)
    db.execute(base[1])
    if old_version >= 3:
        db.execute(create)
    if old_version >= 4:
        db.execute(activity_index)
    db.execute(f"PRAGMA user_version = {old_version}")

def report(db, report_id=identity, status="REPORTED", photo=False):
    columns = "id,incident_type,priority,description,occurred_at_seconds,occurred_at_nanos,location_reference,latitude,longitude,created_at_seconds,created_at_nanos,status"
    args = [report_id, "RISK", "HIGH", "Hay un árbol caído 🌳 frente al café.", 1000, 987654321,
            "Frente al café", 19.0, -99.0, 2000, 123456789, status]
    if photo:
        columns += ",photo_id,photo_sha256,photo_byte_count,photo_width,photo_height"
        args += [actor, "a" * 64, 1024, 640, 480]
    db.execute(f"INSERT INTO reports ({columns}) VALUES ({','.join('?' for _ in args)})", args)

def event(db, sequence, start=None, end="REPORTED", kind="CREATED", report_id=identity,
          event_id=None, seconds=2000, nanos=123456789, event_actor=None, reason=""):
    event_id = event_id or f"10000000-0000-0000-0000-{sequence:012d}"
    db.execute(insert, (event_id, report_id, sequence, kind, start, end, seconds, nanos, event_actor, reason))
    return event_id

def commit_action(db, action):
    db.execute("BEGIN IMMEDIATE")
    try:
        result = action()
        db.execute("COMMIT")
        return result
    except BaseException:
        db.execute("ROLLBACK")
        raise

def reject(action):
    try:
        action()
        raise AssertionError("Expected SQLite constraint failure")
    except sqlite3.IntegrityError:
        pass

def head_row(db, report_id=identity):
    return db.execute(head, (report_id,)).fetchone()

def change(db, expected_seq, previous, target, event_id=None, seconds=2000, nanos=123456789):
    changed = db.execute(cas, (target, identity, previous, expected_seq)).rowcount
    if changed == 1:
        event(db, expected_seq + 1, previous, target, "STATUS_CHANGED", event_id=event_id,
              seconds=seconds, nanos=nanos, event_actor=actor, reason="Revisión local del incidente")
    return changed

with connection() as db:
    if mode.startswith("migration"):
        old_version = 2 if mode == "migration2" else 1
        prepare(db, old_version)
        report(db, status="ATTENDED", photo=old_version == 2)
        before = db.execute("SELECT * FROM reports").fetchall()
        def upgrade():
            if old_version == 1:
                for declaration in photos:
                    db.execute("ALTER TABLE reports ADD COLUMN " + declaration)
            if mode == "migrationRollback":
                db.execute("CREATE TABLE report_status_events (preserve TEXT)")
            db.execute(create)
            db.execute(activity_index)
            db.execute(f"PRAGMA user_version = {version}")
        if mode == "migrationRollback":
            before_schema = db.execute("SELECT * FROM sqlite_master ORDER BY name").fetchall()
            try:
                commit_action(db, upgrade)
                raise AssertionError("Expected additive migration failure")
            except sqlite3.OperationalError:
                pass
            assert db.execute("PRAGMA user_version").fetchone()[0] == 1
            assert db.execute("SELECT * FROM reports").fetchall() == before
            assert db.execute("SELECT * FROM sqlite_master ORDER BY name").fetchall() == before_schema
        else:
            commit_action(db, upgrade)
            after = db.execute("SELECT * FROM reports").fetchall()
            assert after == ([row + (None,) * 5 for row in before] if old_version == 1 else before)
            assert head_row(db) == ("ATTENDED", 0, 0)
            assert db.execute(first_page, (identity, 3)).fetchall() == []
            assert db.execute("PRAGMA user_version").fetchone()[0] == version
    else:
        prepare(db)
        if mode == "creation":
            def create_report():
                report(db)
                event(db, 1, event_id=identity)
            commit_action(db, create_report)
            assert head_row(db) == ("REPORTED", 1, 1)
            before = db.execute("SELECT * FROM report_status_events").fetchall()
            def failed_report():
                report(db, new_id)
                event(db, 1, report_id=new_id, event_id=identity)
            reject(lambda: commit_action(db, failed_report))
            assert db.execute("SELECT id FROM reports").fetchall() == [(identity,)]
            assert db.execute("SELECT * FROM report_status_events").fetchall() == before
        elif mode == "statusAtomic":
            report(db)
            event(db, 1, event_id=identity)
            reject(lambda: commit_action(db, lambda: change(db, 1, "REPORTED", "IN_REVIEW", identity)))
            assert head_row(db) == ("REPORTED", 1, 1)
            assert commit_action(db, lambda: change(db, 1, "REPORTED", "IN_REVIEW")) == 1
            assert head_row(db) == ("IN_REVIEW", 2, 1)
        elif mode == "cas":
            report(db)
            event(db, 1)
            assert commit_action(db, lambda: change(db, 1, "REPORTED", "IN_REVIEW")) == 1
            assert commit_action(db, lambda: change(db, 1, "REPORTED", "IN_REVIEW")) == 0
            assert commit_action(db, lambda: change(db, 2, "REPORTED", "IN_REVIEW")) == 0
            assert head_row(db) == ("IN_REVIEW", 2, 1)
            assert db.execute("SELECT count(*) FROM report_status_events").fetchone()[0] == 2
        elif mode == "constraints":
            report(db)
            creation_id = event(db, 1)
            reject(lambda: event(db, 1, event_id=new_id))
            reject(lambda: event(db, 2, "REPORTED", "IN_REVIEW", "STATUS_CHANGED", event_id=creation_id,
                                 event_actor=actor, reason="Revisar reporte"))
            for start, end in ((None, "IN_REVIEW"), ("REPORTED", "CLOSED"), ("CLOSED", "REPORTED")):
                reject(lambda: event(db, 2, start, end, "STATUS_CHANGED", event_actor=actor, reason="Revisar reporte"))
            reject(lambda: event(db, 2, "REPORTED", "IN_REVIEW", "STATUS_CHANGED", reason="Revisar reporte"))
            reject(lambda: event(db, 2, "REPORTED", "IN_REVIEW", "STATUS_CHANGED", event_actor=actor, reason="a"))
            reject(lambda: event(db, 2, "REPORTED", "IN_REVIEW", "STATUS_CHANGED", event_actor=actor, reason="a\0aaaa"))
            reject(lambda: event(db, 1, report_id=new_id, event_id=new_id))
            reject(lambda: event(db, 2, "REPORTED", "IN_REVIEW", "STATUS_CHANGED", event_actor=actor,
                                 reason="Revisar reporte", nanos=1000000000))
            assert head_row(db) == ("REPORTED", 1, 1)
        elif mode in ("page", "clock"):
            report(db)
            event(db, 1)
            for seq, start, end in ((1, "REPORTED", "IN_REVIEW"), (2, "IN_REVIEW", "ATTENDED"), (3, "ATTENDED", "CLOSED")):
                assert commit_action(db, lambda: change(db, seq, start, end, seconds=2000 - seq, nanos=900000000 + seq)) == 1
            before_rows = db.execute("SELECT * FROM reports").fetchall()
            before_events = db.execute("SELECT * FROM report_status_events").fetchall()
            db.execute("PRAGMA query_only = ON")
            first = db.execute(first_page, (identity, 3)).fetchall()
            older = db.execute(older_page, (identity, 3, 3)).fetchall()
            assert [row[2] for row in first] == [4, 3, 2]
            assert [row[2] for row in older] == [2, 1]
            assert [row[6:8] for row in first] == [(1997, 900000003), (1996 + 2, 900000002), (1999, 900000001)]
            assert head_row(db) == ("CLOSED", 4, 1)
            assert db.execute("SELECT * FROM reports").fetchall() == before_rows
            assert db.execute("SELECT * FROM report_status_events").fetchall() == before_events
        elif mode == "isolation":
            report(db)
            creation_id = event(db, 1)
            with connection("another-owner") as other:
                prepare(other)
                assert head_row(other) is None
                assert other.execute(operation, (creation_id,)).fetchone() is None
                assert other.execute(first_page, (identity, 3)).fetchall() == []
                report(other, status="ATTENDED")
                assert head_row(other) == ("ATTENDED", 0, 0)
                assert head_row(db) == ("REPORTED", 1, 1)
        else:
            raise AssertionError("Unknown mode")

print("history-sqlite-ok")
