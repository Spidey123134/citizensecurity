"""Read independent named partitions using the production schema and Kotlin WHERE clauses."""
import hashlib
import re
import sqlite3
import sys
from pathlib import Path

input_path, source_path, mode = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
lines = input_path.read_text(encoding="utf-8").splitlines()
names = lines[0].split("\t")
assert len(names) == 3 and len(set(names)) == 3
list_sql, list_args = lines[1], lines[2].split("\t")
map_sql, map_args = lines[3], lines[4].split("\t")
activity_index = lines[5]

# Fail if production SQL changes incompatibly; do not replace it with a parallel schema.
source = source_path.read_text(encoding="utf-8")
on_create = source.split("override fun onCreate", 1)[1].split("override fun onUpgrade", 1)[0]
create_sql = re.findall(r'"""(.*?)"""\.trimIndent\(\)', on_create, re.S)
assert len(create_sql) == 2
photo_section = source.split("val PHOTO_COLUMNS = listOf(", 1)[1]
photo_columns = re.findall(r'"""(.*?)"""\.trimIndent\(\)', photo_section, re.S)
assert len(photo_columns) == 5
table_sql = re.sub(r'\$\{PHOTO_COLUMNS\.joinToString\([^}]*\)\}', ",\n".join(photo_columns), create_sql[0])
assert "${" not in table_sql
version = int(re.search(r"const val VERSION = (\d+)", source).group(1))
assert version == 4
history_source = source_path.with_name("ReportHistorySql.kt").read_text(encoding="utf-8")
history_sql = re.search(r'val CREATE_TABLE = """(.*?)"""\.trimIndent\(\)', history_source, re.S).group(1)
assert "db.execSQL(ReportHistorySql.CREATE_TABLE)" in on_create

shared_uuid = "11111111-1111-1111-1111-111111111111"
paths = [input_path.parent / name for name in names]
owners = ["academic", "account-a", "account-b"]
unique_ids = [f"22222222-2222-2222-2222-{index + 1:012d}" for index in range(3)]
for index, path in enumerate(paths):
    assert path.parent == input_path.parent and path.name == names[index]
    with sqlite3.connect(path) as database:
        database.execute(table_sql)
        database.execute(create_sql[1])
        database.execute("PRAGMA foreign_keys = ON")
        database.execute(history_sql)
        database.execute(activity_index)
        database.execute(f"PRAGMA user_version = {version}")
        for identity, priority, created in ((shared_uuid, "HIGH", 2000), (unique_ids[index], "LOW", 1999)):
            database.execute("""
                INSERT INTO reports (id, incident_type, priority, description,
                    occurred_at_seconds, occurred_at_nanos, location_reference, latitude, longitude,
                    created_at_seconds, created_at_nanos, status)
                VALUES (?, 'RISK', ?, ?, 1000, 7, 'Referencia local', 19.0, -99.0, ?, 9, 'REPORTED')
            """, (identity, priority, f"Reporte privado de {owners[index]}", created))

before_hashes = [hashlib.sha256(path.read_bytes()).hexdigest() for path in paths]
before_rows = []
before_schema = []
for path in paths:
    with sqlite3.connect(path) as database:
        before_rows.append(database.execute("SELECT * FROM reports ORDER BY id").fetchall())
        before_schema.append(database.execute("SELECT type, name, sql FROM sqlite_master ORDER BY name").fetchall())

for index, path in enumerate(paths):
    # This is a fresh connection: no original connection or row list is reused to answer.
    with sqlite3.connect(path) as database:
        database.execute("PRAGMA query_only = ON")
        changes = database.total_changes
        if mode == "folio":
            result = database.execute("SELECT id, description FROM reports WHERE id = ? LIMIT 1", (shared_uuid,)).fetchall()
            assert result == [(shared_uuid, f"Reporte privado de {owners[index]}")]
            for other, other_id in enumerate(unique_ids):
                actual = database.execute("SELECT id FROM reports WHERE id = ? LIMIT 1", (other_id,)).fetchall()
                assert actual == ([(other_id,)] if other == index else [])
        elif mode in ("list", "map"):
            sql, arguments = (list_sql, list_args) if mode == "list" else (map_sql, map_args)
            result = database.execute(sql, arguments).fetchall()
            assert result == [(shared_uuid, f"Reporte privado de {owners[index]}")]
        elif mode == "reopen":
            assert database.execute("SELECT count(*) FROM reports").fetchone()[0] == 2
        else:
            raise AssertionError("Unknown isolation verification")
        assert database.total_changes == changes
        assert database.execute("PRAGMA user_version").fetchone()[0] == version
        assert database.execute("SELECT * FROM reports ORDER BY id").fetchall() == before_rows[index]
        assert database.execute("SELECT type, name, sql FROM sqlite_master ORDER BY name").fetchall() == before_schema[index]

assert [hashlib.sha256(path.read_bytes()).hexdigest() for path in paths] == before_hashes
print("scope-isolation-ok")
