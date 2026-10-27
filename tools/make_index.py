#!/usr/bin/env python3
"""Собирает app/src/main/assets/baza/index.json из файлов разделов.

Запуск после добавления или изменения раздела: python3 tools/make_index.py
"""
import json
import pathlib
import re

BAZA = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/baza"


def sort_key(code):
    # А.1 < Б.1 < Б.2 < … < Б.12 < В < Г.1: буква, затем номер числом.
    letter, _, num = code.partition(".")
    return ("АБВГ".find(letter[0]) if letter else 9, int(num) if num.isdigit() else 0)


def main():
    index = []
    for f in BAZA.glob("*.json"):
        if f.name == "index.json":
            continue
        data = json.loads(f.read_text(encoding="utf-8"))
        qs = [q for q in data["questions"] if len(q.get("options", [])) >= 2 and q.get("correct")]
        index.append({
            "code": data["code"],
            "title": data["title"],
            "count": len(qs),
            "order": data.get("order", ""),
            "file": f.name,
        })
    index.sort(key=lambda s: sort_key(s["code"]))
    (BAZA / "index.json").write_text(json.dumps(index, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    for s in index:
        print(f'{s["code"]:6} {s["count"]:5}  {s["title"]}')
    print("всего:", sum(s["count"] for s in index))


if __name__ == "__main__":
    main()
