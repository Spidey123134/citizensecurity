"""Run Kotlin-exported activity SQL over the actual production report/history DDL."""
import hashlib
import re
import sqlite3
import sys
from pathlib import Path

source_path, mode = Path(sys.argv[1]), sys.argv[2]
directory = Path(__file__).parent
source = source_path.read_text(encoding="utf-8")
on_create = source.split("override fun onCreate", 1)[1].split("override fun onUpgrade", 1)[0]
base = re.findall(r'"""(.*?)"""\.trimIndent\(\)', on_create, re.S)
photos = re.findall(r'"""(.*?)"""\.trimIndent\(\)', source.split("val PHOTO_COLUMNS = listOf(", 1)[1], re.S)
assert len(base) == 2 and len(photos) == 5
assert "db.execSQL(ReportActivitySql.CREATE_INDEX)" in on_create
assert int(re.search(r"const val VERSION = (\d+)", source).group(1)) == 4
table = re.sub(r'\$\{PHOTO_COLUMNS\.joinToString\([^}]*\)\}', ",\n".join(photos), base[0])
assert "${" not in table
blocks = [(directory / f"statement{i}.txt").read_text(encoding="utf-8") for i in range(11)]
history, insert_event, index_sql = blocks[:3]
queries = [(blocks[i], blocks[i + 1].split("\t")) for i in range(3, 11, 2)]
assert all("OFFSET" not in sql.upper() for sql, _ in queries)

def identity(number):
    return f"b0000000-0000-0000-0000-{number:012d}"

def connect(name="activity"):
    db = sqlite3.connect(directory / f"{name}.db", isolation_level=None)
    db.execute("PRAGMA foreign_keys = ON")
    return db

def prepare(db, version=4):
    db.execute(table)
    db.execute(base[1])
    db.execute(history)
    if version == 4:
        db.execute(index_sql)
    db.execute(f"PRAGMA user_version = {version}")

def report(db, number, kind="RISK", photo=False):
    columns = "id,incident_type,priority,description,occurred_at_seconds,occurred_at_nanos,location_reference,latitude,longitude,created_at_seconds,created_at_nanos,status"
    values = [identity(number), kind, "HIGH", "Reporte local del árbol caído frente al café.", 1000, 7,
              "Frente al café", 19.0, -99.0, 2000, 7, "REPORTED"]
    if photo:
        columns += ",photo_id,photo_sha256,photo_byte_count,photo_width,photo_height"
        values += [identity(999), "a" * 64, 1024, 640, 480]
    db.execute(f"INSERT INTO reports ({columns}) VALUES ({','.join('?' for _ in values)})", values)

def event(db, operation, folio, seconds, nanos, change=False):
    db.execute(insert_event, (identity(operation), identity(folio), 2 if change else 1,
        "STATUS_CHANGED" if change else "CREATED", "REPORTED" if change else None,
        "IN_REVIEW" if change else "REPORTED", seconds, nanos, identity(888) if change else None,
        "Cambio local registrado" if change else ""))

def seed(db):
    report(db, 11, "THEFT", True)
    report(db, 12, "RISK")
    report(db, 13, "FIRE")
    event(db, 1, 11, 2000, 7)
    event(db, 2, 11, 2000, 7, True)
    event(db, 3, 12, 2000, 8)
    event(db, 4, 12, 2000, 6, True)
    event(db, 5, 13, 2001, 0)

def read(db, query=0):
    sql, args = queries[query]
    cursor = db.execute(sql, args)
    names = [column[0] for column in cursor.description]
    return [dict(zip(names, row)) for row in cursor.fetchall()]

with connect() as db:
    prepare(db, 3 if mode == "migration3" else 4)
    if mode == "legacy":
        report(db, 11, photo=True)
        before = db.execute("SELECT * FROM reports").fetchall()
        assert read(db) == []
        assert read(db, 2) == []
        assert db.execute("SELECT * FROM reports").fetchall() == before
        assert db.execute("SELECT count(*) FROM report_status_events").fetchone()[0] == 0
    else:
        seed(db)
        if mode == "order":
            rows = read(db)
            assert [row["operation_id"] for row in rows] == [identity(5), identity(3), identity(2)]
            assert [row["recorded_at_nanos"] for row in rows] == [0, 8, 7]
            assert rows[-1]["incident_type"] == "THEFT"
            plan = db.execute("EXPLAIN QUERY PLAN " + queries[0][0], queries[0][1]).fetchall()
            assert "report_status_events_activity_idx" in str(plan), plan
        elif mode == "cursor":
            assert [row["operation_id"] for row in read(db, 1)] == [identity(1), identity(4)]
        elif mode == "filter":
            for number in range(100, 200):
                report(db, number)
                event(db, number, number, 3000 + number, 7)
            rows = read(db, 2)
            assert [row["operation_id"] for row in rows] == [identity(2), identity(4)]
            assert all(row["kind"] == "STATUS_CHANGED" for row in rows)
        elif mode == "readonly":
            reports = db.execute("SELECT * FROM reports ORDER BY id").fetchall()
            events = db.execute("SELECT * FROM report_status_events ORDER BY operation_id").fetchall()
            before = hashlib.sha256((directory / "activity.db").read_bytes()).hexdigest()
            changes = db.total_changes
            db.execute("PRAGMA query_only = ON")
            for _ in range(3):
                assert read(db) == read(db)
                read(db, 1)
                read(db, 2)
            assert db.total_changes == changes
            assert db.execute("SELECT * FROM reports ORDER BY id").fetchall() == reports
            assert db.execute("SELECT * FROM report_status_events ORDER BY operation_id").fetchall() == events
            assert hashlib.sha256((directory / "activity.db").read_bytes()).hexdigest() == before
        elif mode == "partition":
            with connect("other") as other:
                prepare(other)
                report(other, 11, "OTHER")
                event(other, 1, 11, 1990, 0)
                assert len(read(other)) == 1
                assert read(other)[0]["incident_type"] == "OTHER"
                assert read(db)[-1]["incident_type"] == "THEFT"
                assert read(other, 2) == []
                assert len(read(db, 2)) == 2
        elif mode == "migration3":
            reports = db.execute("SELECT * FROM reports ORDER BY id").fetchall()
            events = db.execute("SELECT * FROM report_status_events ORDER BY operation_id").fetchall()
            assert db.execute("SELECT count(*) FROM sqlite_master WHERE name = 'report_status_events_activity_idx'").fetchone()[0] == 0
            db.execute("BEGIN IMMEDIATE")
            db.execute(index_sql)
            db.execute("PRAGMA user_version = 4")
            db.execute("COMMIT")
            assert db.execute("SELECT * FROM reports ORDER BY id").fetchall() == reports
            assert db.execute("SELECT * FROM report_status_events ORDER BY operation_id").fetchall() == events
            assert reports[0][-5:] == (identity(999), "a" * 64, 1024, 640, 480)
            assert db.execute("PRAGMA user_version").fetchone()[0] == 4
            assert len(read(db)) == 3
        elif mode == "bounded":
            for number in range(100, 300):
                report(db, number)
                event(db, number, number, 3000 + number, 7)
            rows = read(db, 3)
            assert len(rows) == 101
            assert len({row["operation_id"] for row in rows}) == 101
        else:
            raise AssertionError("Unknown activity scenario")

print("activity-sqlite-ok")
