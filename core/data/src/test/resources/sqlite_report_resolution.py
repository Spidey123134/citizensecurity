"""Execute production result DDL/query exports with host SQLite, without granting app roles."""
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
assert "db.execSQL(ReportResolutionSql.CREATE_TABLE)" in on_create
version = int(re.search(r"const val VERSION = (\d+)", source).group(1))
assert version == 5
reports_sql = re.sub(r'\$\{PHOTO_COLUMNS\.joinToString\([^}]*\)\}', ",\n".join(photos), base[0])
assert "${" not in reports_sql
create, insert, operation, creator, latest, chain, page, older, history, index, history_insert = [
    (directory / f"statement{i}.sql").read_text(encoding="utf-8") for i in range(11)
]
identity = "00000000-0000-0000-0000-000000000001"
account = "00000000-0000-0000-0000-000000000002"
second_id = "00000000-0000-0000-0000-000000000003"


def connect(name="main"):
    db = sqlite3.connect(directory / f"{name}.db", isolation_level=None)
    db.execute("PRAGMA foreign_keys = ON")
    return db


def prepare(db, old=False):
    db.execute(reports_sql)
    db.execute(base[1])
    db.execute(history)
    db.execute(index)
    if not old:
        db.execute(create)
    db.execute(f"PRAGMA user_version = {4 if old else version}")


def report(db, report_id=identity, status="CLOSED"):
    db.execute("""INSERT INTO reports (id,incident_type,priority,description,
        occurred_at_seconds,occurred_at_nanos,location_reference,latitude,longitude,
        created_at_seconds,created_at_nanos,status,photo_id,photo_sha256,photo_byte_count,photo_width,photo_height)
        VALUES (?, 'RISK','HIGH','Hay un árbol caído frente al café 🌳',1000,123,'Frente al café',19.0,-99.0,
        2000,987654321,?,?,?,?,?,?)""", (report_id, status, account, "a" * 64, 1024, 640, 480))
    db.execute(history_insert, (report_id, report_id, 1, "CREATED", None, "REPORTED", 2000, 987654321, account, ""))


def args(sequence=1, report_id=identity, operation_id=None, previous=None, target=None):
    previous = previous if previous is not None else ("UNCONFIRMED" if sequence == 1 else "RESOLVED" if sequence % 2 == 0 else "UNRESOLVED")
    target = target if target is not None else ("RESOLVED" if sequence % 2 == 1 else "UNRESOLVED")
    return [operation_id or f"10000000-0000-0000-0000-{sequence:012d}", report_id, sequence, previous,
            target, 2000 - sequence, 123456789, account, "AUTHOR", "Ya retiraron el árbol frente al café 🌳"]


def reject(action):
    try:
        action()
        raise AssertionError("Expected a SQLite constraint rejection")
    except sqlite3.IntegrityError:
        pass


def chain_row(db):
    return db.execute(chain, (identity,)).fetchone()


def snapshots(db):
    return (db.execute("SELECT * FROM reports ORDER BY id").fetchall(),
            db.execute("SELECT * FROM report_status_events ORDER BY report_id,sequence").fetchall(),
            db.execute("SELECT type,name,sql FROM sqlite_master ORDER BY name").fetchall(),
            db.execute("PRAGMA user_version").fetchone()[0])


with connect() as db:
    prepare(db, old=mode in ("migration", "rollback"))
    report(db)
    if mode == "migration":
        rows, events, schema, old_version = snapshots(db)
        db.execute("BEGIN IMMEDIATE")
        db.execute(create)
        db.execute(f"PRAGMA user_version = {version}")
        db.execute("COMMIT")
        after = snapshots(db)
        assert after[0] == rows and after[1] == events and after[3] == 5
        assert all(item in after[2] for item in schema)
        assert db.execute(latest, (identity,)).fetchone() is None
        assert chain_row(db) == (0, None, None, 0)
        assert [row[2] for row in db.execute("PRAGMA index_info(report_status_events_activity_idx)")] == [
            "recorded_at_seconds", "recorded_at_nanos", "operation_id"]
    elif mode == "rollback":
        db.execute("CREATE TABLE report_resolution_events (note TEXT)")
        db.execute("INSERT INTO report_resolution_events VALUES ('conservar')")
        before = snapshots(db)
        db.execute("BEGIN IMMEDIATE")
        try:
            db.execute(create)
            raise AssertionError("Expected existing-table migration failure")
        except sqlite3.OperationalError:
            db.execute("ROLLBACK")
        assert snapshots(db) == before
        assert db.execute("SELECT note FROM report_resolution_events").fetchall() == [("conservar",)]
    elif mode == "empty":
        assert db.execute("SELECT status FROM reports").fetchone() == ("CLOSED",)
        assert db.execute(latest, (identity,)).fetchone() is None
        assert db.execute(page, (identity, 3)).fetchall() == []
        assert chain_row(db) == (0, None, None, 0)
        initial = db.execute(creator, (identity,)).fetchone()
        assert initial[0] == identity and initial[8] == account
    elif mode == "constraints":
        invalid = [(0, "bad"), (1, second_id), (2, 0), (2, -1), (2, 1.5),
                   (3, "RESOLVED"), (3, "OTHER"), (4, "UNCONFIRMED"), (4, "OTHER"),
                   (5, 1.5), (6, -1), (6, 1000000000), (6, 1.5), (7, "bad"), (8, "OTHER"),
                   (9, "abcd"), (9, "a" * 501), (9, "🌳" * 501), (9, "motivo\0oculto")]
        for column, value in invalid:
            values = args()
            values[column] = value
            reject(lambda: db.execute(insert, values))
        assert chain_row(db)[0] == 0
        for sequence, reason in ((1, "🌳" * 5), (2, "🌳" * 500)):
            values = args(sequence); values[9] = reason; db.execute(insert, values)
        assert chain_row(db) == (2, 1, 2, 0)
    elif mode == "uniqueness":
        report(db, second_id)
        db.execute(insert, args())
        reject(lambda: db.execute(insert, args(operation_id=second_id)))
        reject(lambda: db.execute(insert, args(report_id=second_id)))
        db.execute(insert, args(report_id=second_id, operation_id=second_id))
        assert db.execute("SELECT COUNT(*) FROM report_resolution_events").fetchone()[0] == 2
    elif mode == "pages":
        for sequence in range(1, 6):
            db.execute(insert, args(sequence))
        before = snapshots(db)
        first = db.execute(page, (identity, 3)).fetchall()
        rest = db.execute(older, (identity, 4, 3)).fetchall()
        assert [row[2] for row in first] == [5, 4, 3]
        assert [row[2] for row in rest] == [3, 2, 1]
        assert [row[5] for row in first] == [1995, 1996, 1997]
        assert chain_row(db) == (5, 1, 5, 0)
        assert db.execute(latest, (identity,)).fetchone()[2] == 5
        assert snapshots(db) == before
    elif mode == "chain":
        for sequence in range(1, 4):
            db.execute(insert, args(sequence))
        assert chain_row(db) == (3, 1, 3, 0)
        db.execute("PRAGMA ignore_check_constraints = ON")
        for damage in ("DELETE FROM report_resolution_events WHERE sequence = 2",
                       "UPDATE report_resolution_events SET from_resolution = 'UNCONFIRMED' WHERE sequence = 3",
                       "UPDATE report_resolution_events SET from_resolution = 'UNRESOLVED' WHERE sequence = 1"):
            db.execute("SAVEPOINT damage")
            db.execute(damage)
            count, first, last, broken = chain_row(db)
            assert broken > 0 or first != 1 or count != last
            db.execute("ROLLBACK TO damage"); db.execute("RELEASE damage")
    elif mode == "receipt":
        values = args(); values[8] = "LOCAL_ADMINISTRATOR"
        db.execute(insert, values)
        actual = db.execute(operation, (values[0],)).fetchone()
        assert actual == tuple(values)
        assert db.execute(operation, (second_id,)).fetchone() is None
    elif mode == "atomicity":
        before = snapshots(db)
        db.execute("BEGIN IMMEDIATE")
        db.execute(insert, args())
        db.execute("ROLLBACK")
        assert chain_row(db)[0] == 0 and snapshots(db) == before
        db.execute(insert, args())
        assert snapshots(db) == before  # Outcome append never changes report/status chronology.
        assert db.execute(latest, (identity,)).fetchone()[4] == "RESOLVED"
    elif mode == "isolation":
        values = args(); db.execute(insert, values)
        with connect("other-owner") as other:
            prepare(other); report(other)
            assert other.execute(operation, (values[0],)).fetchone() is None
            assert other.execute(page, (identity, 3)).fetchall() == []
            changed = args(target="UNRESOLVED"); other.execute(insert, changed)
            assert other.execute(latest, (identity,)).fetchone()[4] == "UNRESOLVED"
            assert db.execute(latest, (identity,)).fetchone()[4] == "RESOLVED"
    else:
        raise AssertionError("Unknown verification mode")

print("resolution-sqlite-ok")
